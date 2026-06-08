package top.katton.api.event

import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * %en
 * Loot table events for NeoForge platform.
 *
 * %zh
 * NeoForge 平台的战利品表事件。
 */
object LootTableEvent {

    /**
     * %en
     * Event triggered to replace a loot table entirely.
     *
     * %zh
     * 当需要整体替换战利品表时触发。
     * @return
     * %en replacement LootTable, or null to keep the original.
     * %zh 返回替换用的 LootTable，或返回 null 以保留原表。
     * %en
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    /**
     * %en
     * Event triggered to modify a loot table's contents.
     * Use this to add or remove loot pool entries.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当需要修改战利品表内容时触发。
     * 可用于添加或移除战利品池条目。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onLootTableModify = createUnit<LootTableModifyArg>()

    /**
     * %en
     * Event triggered when all loot tables have been loaded.
     * Use this for post-processing after all tables are available.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当所有战利品表加载完成后触发。
     * 可用于在全部表可用后进行后处理。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    /**
     * %en
     * Event triggered to modify the drops from a loot table.
     * Use this to customize what items are actually dropped.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当需要修改战利品表的掉落内容时触发。
     * 可用于自定义实际掉落的物品。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}
