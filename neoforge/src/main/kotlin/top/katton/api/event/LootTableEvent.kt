package top.katton.api.event

import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Loot table events for NeoForge platform.
 *
 */
object LootTableEvent {

    /**
     * Event triggered to replace a loot table entirely.
     *
     * @return The replacement LootTable, or null to keep the original.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    /**
     * Event triggered to modify a loot table's contents.
     * Use this to add or remove loot pool entries.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onLootTableModify = createUnit<LootTableModifyArg>()

    /**
     * Event triggered when all loot tables have been loaded.
     * Use this for post-processing after all tables are available.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    /**
     * Event triggered to modify the drops from a loot table.
     * Use this to customize what items are actually dropped.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}
