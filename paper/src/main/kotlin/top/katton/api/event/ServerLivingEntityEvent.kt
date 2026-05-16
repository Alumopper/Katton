package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ServerLivingEntityEvent {
    @JvmField val onLivingHurt = createUnit<Any>()
    @JvmField val onAllowDamage = createUnit<Any>()
    @JvmField val onAfterDamage = createUnit<Any>()
    @JvmField val onAllowDeath = createUnit<Any>()
    @JvmField val onAfterDeath = createUnit<Any>()
    @JvmField val onLivingFall = createUnit<Any>()
    @JvmField val onMobConversion = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onDamage(e: EntityDamageEvent) { onLivingHurt(PaperNmsBridge.toNmsLivingEntity(e.entity as org.bukkit.entity.LivingEntity)) }
            @EventHandler fun onDeath(e: EntityDeathEvent) { onAfterDeath(PaperNmsBridge.toNmsLivingEntity(e.entity)) }
        }, plugin)
    }
}




