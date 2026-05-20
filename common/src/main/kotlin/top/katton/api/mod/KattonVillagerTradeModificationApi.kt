@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.trading.TradeSet
import org.jetbrains.annotations.ApiStatus
import org.slf4j.LoggerFactory
import top.katton.api.requireServer
import top.katton.datapack.VillagerTradeManager
import top.katton.registry.id

private val LOGGER = LoggerFactory.getLogger("top.katton.api.mod.KattonVillagerTradeModificationApi")

/**
 * Configuration for a single trade to be appended to an existing
 * [TradeSet] via [addVillagerTrade].
 *
 * Fields map directly to the public `VillagerTrade(TradeCost wants,
 * Optional<TradeCost> additionalWants, ItemStackTemplate gives, int
 * maxUses, int xp, float priceMultiplier, ...)` constructor on MC
 * 26.1.2 — every value here is plain enough to keep stable across patch
 * releases.
 */
class VillagerTradeAdditionConfig internal constructor(
    val tradeSet: ResourceKey<TradeSet>,
) {
    /** Item the merchant wants from the player. */
    var costItemId: Identifier? = null
    var costItemCount: Int = 1

    /** Optional secondary cost. */
    var costBItemId: Identifier? = null
    var costBItemCount: Int = 1

    /** Item the merchant gives back. */
    var resultItemId: Identifier? = null
    var resultItemCount: Int = 1

    /** Maximum trade uses (vanilla farmer level 1 emerald-bread = 16). */
    var maxUses: Int = 12

    /** Villager XP awarded per trade. */
    var xp: Int = 2

    /** Vanilla price multiplier (0.05 default; matches farmer baselines). */
    var priceMultiplier: Float = 0.05f

    fun cost(itemId: String, count: Int = 1) {
        costItemId = id(itemId); costItemCount = count
    }

    fun cost(itemId: Identifier, count: Int = 1) {
        costItemId = itemId; costItemCount = count
    }

    fun additionalCost(itemId: String, count: Int = 1) {
        costBItemId = id(itemId); costBItemCount = count
    }

    fun additionalCost(itemId: Identifier, count: Int = 1) {
        costBItemId = itemId; costBItemCount = count
    }

    fun result(itemId: String, count: Int = 1) {
        resultItemId = id(itemId); resultItemCount = count
    }

    fun result(itemId: Identifier, count: Int = 1) {
        resultItemId = itemId; resultItemCount = count
    }

    internal fun build(): VillagerTradeManager.PendingTrade? {
        val want = costItemId ?: run {
            LOGGER.warn("addVillagerTrade({}): cost item id is missing", tradeSet.identifier())
            return null
        }
        val give = resultItemId ?: run {
            LOGGER.warn("addVillagerTrade({}): result item id is missing", tradeSet.identifier())
            return null
        }
        require(maxUses > 0) { "maxUses must be > 0 (got $maxUses)" }
        require(xp >= 0) { "xp must be >= 0 (got $xp)" }
        require(priceMultiplier in 0.0f..1.0f) {
            "priceMultiplier must be within 0.0..1.0 (got $priceMultiplier)"
        }
        return VillagerTradeManager.PendingTrade(
            tradeSet = tradeSet,
            wants = VillagerTradeManager.ItemRef(want, costItemCount),
            additionalWants = costBItemId?.let { VillagerTradeManager.ItemRef(it, costBItemCount) },
            gives = VillagerTradeManager.ItemRef(give, resultItemCount),
            maxUses = maxUses,
            xp = xp,
            priceMultiplier = priceMultiplier,
        )
    }
}

/**
 * Appends a new trade entry to an existing villager / wandering trader
 * [TradeSet].
 *
 * `tradeSetKey` is a registry id from `minecraft:trade_set`, e.g.
 * `"minecraft:farmer/level_1"` or `"minecraft:wandering_trader/buying"`.
 *
 * The mutation is staged through [VillagerTradeManager] and flushed
 * during the standard reload cycle (after script execution, on the
 * server thread). Calling this outside `/katton reload` is safe — the
 * change is not visible until the server applies pending datapack
 * mutations.
 *
 * Returns `false` and logs a warning when:
 * - the server is offline,
 * - the trade-set id cannot be parsed,
 * - the configuration is missing required fields (`cost` / `result`).
 *
 * @example
 * ```kotlin
 * addVillagerTrade("minecraft:farmer/level_1") {
 *     cost("minecraft:emerald", count = 1)
 *     result("minecraft:apple", count = 5)
 *     maxUses = 12
 *     xp = 2
 *     priceMultiplier = 0.05f
 * }
 * ```
 */
@ApiStatus.Experimental
fun addVillagerTrade(
    tradeSetKey: String,
    configure: VillagerTradeAdditionConfig.() -> Unit,
): Boolean {
    val tradeSetId = runCatching { id(tradeSetKey) }.getOrNull() ?: run {
        LOGGER.warn("addVillagerTrade: invalid trade set id '{}'", tradeSetKey)
        return false
    }
    return addVillagerTrade(tradeSetId, configure)
}

/** Identifier overload of [addVillagerTrade]. */
@ApiStatus.Experimental
fun addVillagerTrade(
    tradeSetId: Identifier,
    configure: VillagerTradeAdditionConfig.() -> Unit,
): Boolean {
    val server = requireServer()
    val tradeSetKey = ResourceKey.create(Registries.TRADE_SET, tradeSetId)

    // Validate the trade set exists. We do this against the live
    // registry rather than waiting for apply so script authors get
    // immediate, addressable feedback during reload.
    val registries = server.reloadableRegistries().lookup() as? RegistryAccess
    if (registries == null) {
        LOGGER.warn("addVillagerTrade: reloadable registry access is not available")
        return false
    }
    val tradeSetRegistry = registries.registries()
        .filter { it.key() == Registries.TRADE_SET }
        .findFirst()
        .orElse(null)
        ?.value()
    if (tradeSetRegistry == null) {
        LOGGER.warn("addVillagerTrade: TRADE_SET registry not available")
        return false
    }
    @Suppress("UNCHECKED_CAST")
    val typedTradeSetRegistry = tradeSetRegistry as Registry<TradeSet>
    if (typedTradeSetRegistry.get(tradeSetKey).orElse(null) == null) {
        LOGGER.warn("addVillagerTrade: trade set {} is not registered", tradeSetId)
        return false
    }

    val pending = VillagerTradeAdditionConfig(tradeSetKey).apply(configure).build() ?: return false
    VillagerTradeManager.stageAddTrade(pending)
    return true
}
