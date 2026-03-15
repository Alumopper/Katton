package top.katton.api.event

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 */
@Suppress("unused")
object LootTableEvent {

    fun initialize() {
        LootTableEvents.REPLACE.register { a, b, _, d ->
            onLootTableReplace(LootTableReplaceArg(a, b, d)).getOrNull()
        }

        LootTableEvents.MODIFY.register { a, b, _, d ->
            onLootTableModify(LootTableModifyArg(a, b, d))
        }

        LootTableEvents.ALL_LOADED.register { a, b ->
            onLootTableAllLoad(LootTableAllLoadArg(a, b))
        }

        LootTableEvents.MODIFY_DROPS.register { a, b, c ->
            onLootTableModifyDrops(LootTableModifyDropsArg(a, b, c))
        }
    }

    /**
     * Event triggered to replace a loot table entirely.
     *
     * @return The replacement LootTable, or null to keep the original.
     */
    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    /**
     * Event triggered to modify a loot table's contents.
     * Use this to add or remove loot pool entries.
     */
    val onLootTableModify = createUnit<LootTableModifyArg>()

    /**
     * Event triggered when all loot tables have been loaded.
     * Use this for post-processing after all tables are available.
     */
    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    /**
     * Event triggered to modify the drops from a loot table.
     * Use this to customize what items are actually dropped.
     */
    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}
