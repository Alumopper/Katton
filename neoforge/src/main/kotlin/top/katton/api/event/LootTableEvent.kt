package top.katton.api.event

import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Loot table modification events (not directly available in NeoForge, placeholder for fabric compatibility).
 */
object LootTableEvent {

    @JvmField
    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    @JvmField
    val onLootTableModify = createUnit<LootTableModifyArg>()

    @JvmField
    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    @JvmField
    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}
