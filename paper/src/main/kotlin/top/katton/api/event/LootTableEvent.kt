package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.util.createUnit

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
