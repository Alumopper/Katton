package top.katton.api.event

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Loot table related events (replace/modify/all loaded/modify drops).
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

    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    val onLootTableModify = createUnit<LootTableModifyArg>()

    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}