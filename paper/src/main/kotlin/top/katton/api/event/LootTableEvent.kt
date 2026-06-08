package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.util.createUnit

/**
 * %en
 * Loot table events for Paper (Bukkit) platform.
 *
 * This object provides events related to loot generation and drop
 * modification.
 *
 * TODO: raw Bukkit event - LootTable replacement and modification hooks
 * are not yet bridged for the Paper platform.
 *
 * %zh
 * Paper (Bukkit) 平台的战利品表事件。
 *
 * 此对象提供与战利品生成和掉落修改相关的事件。
 *
 * TODO: 目前尚未为 Paper 平台桥接原生 Bukkit 事件中的 LootTable 替换和修改钩子。
 */
@Suppress("unused")
object LootTableEvent {

//    @JvmField val onLootTableReplace = createUnit<Any>()
//    @JvmField val onLootTableModify = createUnit<Any>()
//    @JvmField val onLootTableAllLoad = createUnit<Any>()

    @JvmField
    val onLootTableModifyDrops = createUnit<LootGenerateEvent>() // TODO: type as LootTableModifyDropsArg

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onLootGen(event: LootGenerateEvent) {
                if (event.isPlugin) return // skip plugin-triggered loot
                onLootTableModifyDrops(event)
            }
        }, plugin)
    }
}
