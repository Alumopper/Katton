package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createUnit

/**
 * Item component events for Paper (Bukkit) platform.
 *
 * This object provides events related to item enchantment preparation
 * and execution.
 */
@Suppress("unused")
object ItemComponentEvent {

//    @JvmField val onModifyComponent = createUnit<Any>()

    @JvmField
    val onAllowEnchanting = createUnit<PrepareItemEnchantEvent>() // TODO: type as AllowEnchantingArg

    @JvmField
    val onModifyEnchantment = createUnit<EnchantItemEvent>() //TODO: type as ModifyEnchantmentArg

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPrepareEnchant(event: PrepareItemEnchantEvent) {
                onAllowEnchanting(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onEnchant(event: EnchantItemEvent) {
                onModifyEnchantment(event)
            }
        }, plugin)
    }
}
