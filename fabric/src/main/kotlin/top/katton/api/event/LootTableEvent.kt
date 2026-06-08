package top.katton.api.event

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.world.level.storage.loot.LootTable
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * %en
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 *
 * %zh
 * Fabric 平台的战利品表事件。
 *
 * 此对象提供与战利品表操作相关的事件，包括替换、修改以及在战利品表加载时响应。
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
 * %en
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 *
 * %zh
 * Fabric 平台的战利品表事件。
 *
 * 此对象提供与战利品表操作相关的事件，包括替换、修改以及在战利品表加载时响应。
 */
    val onLootTableReplace = createFirstNotNullOfOrNull<LootTableReplaceArg, LootTable?>()

    /**
 * %en
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 *
 * %zh
 * Fabric 平台的战利品表事件。
 *
 * 此对象提供与战利品表操作相关的事件，包括替换、修改以及在战利品表加载时响应。
 */
    val onLootTableModify = createUnit<LootTableModifyArg>()

    /**
 * %en
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 *
 * %zh
 * Fabric 平台的战利品表事件。
 *
 * 此对象提供与战利品表操作相关的事件，包括替换、修改以及在战利品表加载时响应。
 */
    val onLootTableAllLoad = createUnit<LootTableAllLoadArg>()

    /**
 * %en
 * Loot table events for Fabric platform.
 *
 * This object provides events related to loot table manipulation including
 * replacing, modifying, and reacting to loot table loading.
 *
 * %zh
 * Fabric 平台的战利品表事件。
 *
 * 此对象提供与战利品表操作相关的事件，包括替换、修改以及在战利品表加载时响应。
 */
    val onLootTableModifyDrops = createUnit<LootTableModifyDropsArg>()
}
