package top.katton.datapack

import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.trading.TradeCost
import net.minecraft.world.item.trading.TradeSet
import net.minecraft.world.item.trading.VillagerTrade
import net.minecraft.world.level.storage.loot.functions.LootItemFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import org.slf4j.LoggerFactory
import top.katton.registry.unregister
import top.katton.registry.withUnfrozenRegistry
import top.katton.util.ReflectUtil
import java.util.Optional

/**
 * Stages and applies user-script mutations to villager / wandering trader
 * trade sets on Minecraft 26.1.2.
 *
 * 26.1.2 deleted the legacy `VillagerTrades.TRADES` static map and now
 * stores trades exclusively in two reloadable registries:
 * [Registries.VILLAGER_TRADE] holds individual [VillagerTrade] instances
 * and [Registries.TRADE_SET] holds [TradeSet] entries that aggregate them
 * via [HolderSet]. Vanilla profession objects reference trade sets by
 * `ResourceKey<TradeSet>` per level.
 *
 * Strategy (the same shape `applyLootTables` uses for loot tables):
 *
 * 1. Scripts call [stageAddTrade] to push a [PendingTrade] into the
 *    pending list.
 * 2. On [apply]:
 *    - On first apply, snapshot every [TradeSet] that is going to be
 *      modified so subsequent reloads can restore vanilla state.
 *    - Restore previously-touched trade sets from the snapshot, drop the
 *      previously-injected `VillagerTrade` entries from
 *      `Registry<VillagerTrade>`.
 *    - Build a `VillagerTrade` from each [PendingTrade] using the public
 *      `(TradeCost, ItemStackTemplate, int maxUses, int xp,
 *      float priceMultiplier, Optional, List)` constructor.
 *    - Register the new trades into `Registry<VillagerTrade>` under
 *      `katton:trade/<auto-id>`.
 *    - Replace each affected `TradeSet` entry with a new instance whose
 *      `HolderSet<VillagerTrade>` is the original holder list plus the
 *      newly-registered trades.
 *
 * Wandering trader pools live in the same `Registry<TradeSet>` (under
 * `minecraft:wandering_trader/buying`, `/common`, `/uncommon`); the same
 * code path therefore covers wandering trader modifications.
 */
internal object VillagerTradeManager {

    private val LOGGER = LoggerFactory.getLogger("top.katton.datapack.VillagerTradeManager")

    private const val GENERATED_NAMESPACE = "katton"
    private const val GENERATED_PATH_PREFIX = "trade/"

    /**
     * Description of a trade scripts asked to add into a particular
     * trade set. Resolved against the live registry on [apply].
     */
    internal data class PendingTrade(
        /** Target `Registry<TradeSet>` entry to extend, e.g. `minecraft:farmer/level_1`. */
        val tradeSet: ResourceKey<TradeSet>,
        /** Item the merchant wants from the player. */
        val wants: ItemRef,
        /** Optional secondary cost. */
        val additionalWants: ItemRef?,
        /** Item the merchant sells back. */
        val gives: ItemRef,
        /** Maximum trade uses before lock-out. */
        val maxUses: Int,
        /** Villager XP awarded per trade. */
        val xp: Int,
        /** Price multiplier (vanilla farmer = 0.05f). */
        val priceMultiplier: Float,
    )

    /**
     * A namespaced item id paired with a stack count. Resolved against
     * `BuiltInRegistries.ITEM` at apply time.
     */
    internal data class ItemRef(val id: Identifier, val count: Int) {
        init { require(count > 0) { "trade item count must be > 0, got $count" } }
    }

    private val pendingTrades = mutableListOf<PendingTrade>()

    /** TradeSet → original `HolderSet<VillagerTrade>` snapshot. */
    private val tradeSetSnapshots = mutableMapOf<ResourceKey<TradeSet>, HolderSet<VillagerTrade>>()
    /** TradeSet → original `TradeSet` snapshot (other fields preserved). */
    private val tradeSetOriginals = mutableMapOf<ResourceKey<TradeSet>, TradeSet>()
    /** Identifiers of trades injected during the previous apply pass. */
    private val previouslyRegisteredTrades = mutableListOf<ResourceKey<VillagerTrade>>()

    fun beginReload() {
        pendingTrades.clear()
    }

    fun stageAddTrade(trade: PendingTrade) {
        pendingTrades += trade
    }

    /**
     * Number of pending mutations staged since the last [beginReload]. Used
     * by hot-reload status reporting.
     */
    fun pendingCount(): Int = pendingTrades.size

    fun apply(server: MinecraftServer): Boolean {
        if (pendingTrades.isEmpty() && previouslyRegisteredTrades.isEmpty()) {
            return false
        }

        val registryAccess = server.reloadableRegistries().lookup() as? RegistryAccess ?: run {
            LOGGER.warn("reloadableRegistries().lookup() is not a RegistryAccess; skipping {} pending trade mutations", pendingTrades.size)
            pendingTrades.clear()
            return false
        }
        val tradeRegistry = mappedRegistry(registryAccess, Registries.VILLAGER_TRADE)
        val tradeSetRegistry = mappedRegistry(registryAccess, Registries.TRADE_SET)

        if (tradeRegistry == null || tradeSetRegistry == null) {
            LOGGER.warn(
                "VILLAGER_TRADE / TRADE_SET registries not available; skipping {} pending trade mutations",
                pendingTrades.size
            )
            pendingTrades.clear()
            return false
        }

        // Step 1: drop the trades we registered in the previous apply.
        if (previouslyRegisteredTrades.isNotEmpty()) {
            withUnfrozenRegistry(tradeRegistry) {
                previouslyRegisteredTrades.forEach { key ->
                    unregister(tradeRegistry, key)
                }
            }
            previouslyRegisteredTrades.clear()
        }

        // Step 2: restore previously-modified trade sets from snapshot.
        if (tradeSetOriginals.isNotEmpty()) {
            withUnfrozenRegistry(tradeSetRegistry) {
                tradeSetOriginals.forEach { (key, original) ->
                    replaceTradeSet(tradeSetRegistry, key, original)
                }
            }
        }

        if (pendingTrades.isEmpty()) {
            return true // nothing else to do; cleanup of previous round was the work.
        }

        // Step 3: snapshot every TradeSet that is about to be mutated.
        val byTradeSet = pendingTrades.groupBy { it.tradeSet }
        for (key in byTradeSet.keys) {
            val existing = tradeSetRegistry.get(key).orElse(null)?.value() ?: continue
            tradeSetSnapshots.putIfAbsent(key, existing.trades)
            tradeSetOriginals.putIfAbsent(key, existing)
        }

        // Step 4: register new VillagerTrade entries.
        val newHoldersByTradeSet = mutableMapOf<ResourceKey<TradeSet>, MutableList<Holder<VillagerTrade>>>()
        var skipped = 0
        withUnfrozenRegistry(tradeRegistry) {
            for ((index, pending) in pendingTrades.withIndex()) {
                val trade = buildVillagerTrade(pending) ?: run { skipped++; continue }
                val tradeId = Identifier.fromNamespaceAndPath(
                    GENERATED_NAMESPACE,
                    "${GENERATED_PATH_PREFIX}${pending.tradeSet.identifier().path}/$index"
                )
                val key = ResourceKey.create(Registries.VILLAGER_TRADE, tradeId)
                val holder = Registry.registerForHolder(tradeRegistry, key, trade)
                previouslyRegisteredTrades += key
                newHoldersByTradeSet
                    .computeIfAbsent(pending.tradeSet) { mutableListOf() }
                    .add(holder)
            }
        }

        // Step 5: replace each affected TradeSet with an extended HolderSet.
        if (newHoldersByTradeSet.isNotEmpty()) {
            withUnfrozenRegistry(tradeSetRegistry) {
                newHoldersByTradeSet.forEach { (key, addedHolders) ->
                    val baseline = tradeSetOriginals[key] ?: return@forEach
                    val originalHolders = tradeSetSnapshots[key] ?: baseline.trades
                    val combined = HolderSet.direct(buildList {
                        originalHolders.forEach { add(it) }
                        addAll(addedHolders)
                    })
                    val replacement = TradeSet(
                        combined,
                        baseline.calculateAmount(),
                        baseline.allowDuplicates(),
                        baseline.randomSequence()
                    )
                    replaceTradeSet(tradeSetRegistry, key, replacement)
                }
            }
        }

        LOGGER.info(
            "Applied {} villager trade additions across {} trade sets ({} skipped)",
            pendingTrades.size - skipped,
            newHoldersByTradeSet.size,
            skipped
        )
        return true
    }

    private fun buildVillagerTrade(pending: PendingTrade): VillagerTrade? {
        val wantsItem = resolveItem(pending.wants.id) ?: run {
            LOGGER.warn("villager trade: wants item {} is not registered", pending.wants.id); return null
        }
        val givesItem = resolveItem(pending.gives.id) ?: run {
            LOGGER.warn("villager trade: gives item {} is not registered", pending.gives.id); return null
        }
        val additionalWants = pending.additionalWants?.let { extra ->
            val item = resolveItem(extra.id) ?: run {
                LOGGER.warn("villager trade: additional wants item {} is not registered", extra.id); return null
            }
            Optional.of(TradeCost(item, extra.count))
        } ?: Optional.empty()

        return VillagerTrade(
            TradeCost(wantsItem, pending.wants.count),
            additionalWants,
            ItemStackTemplate(givesItem, pending.gives.count),
            pending.maxUses,
            pending.xp,
            pending.priceMultiplier,
            Optional.empty<LootItemCondition>(),
            emptyList<LootItemFunction>(),
        )
    }

    private fun resolveItem(id: Identifier): Item? {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null)
    }

    private fun replaceTradeSet(
        registry: MappedRegistry<TradeSet>,
        key: ResourceKey<TradeSet>,
        value: TradeSet,
    ) {
        unregister(registry, key)
        Registry.register(registry, key, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> mappedRegistry(
        access: RegistryAccess,
        key: ResourceKey<out Registry<T>>,
    ): MappedRegistry<T>? {
        val raw = access.registries()
            .filter { it.key() == key }
            .findFirst()
            .orElse(null)
            ?.value() ?: return null
        return raw as? MappedRegistry<T>
    }

    /**
     * Read-only accessor for the original number provider of a baseline
     * `TradeSet` so we can preserve `amount` when rebuilding the set.
     */
    private fun TradeSet.calculateAmount(): net.minecraft.world.level.storage.loot.providers.number.NumberProvider {
        val raw = ReflectUtil.get(this, "amount").getOrNull() as?
            net.minecraft.world.level.storage.loot.providers.number.NumberProvider
        return raw ?: net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(2.0f)
    }
}
