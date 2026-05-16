package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ServerEntityCombatEvent {
    @JvmField val onAfterKilledOtherEntity = createUnit<Any>()
    @JvmField val onShieldBlock = createUnit<Any>()
    @JvmField val onCriticalHit = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onDeath(e: EntityDeathEvent) {
                val ld = e.entity.lastDamageCause
                if (ld is EntityDamageByEntityEvent && ld.damager != null) {
                    onAfterKilledOtherEntity(PaperNmsBridge.toNmsEntity(ld.damager))
                }
            }
        }, plugin)
    }
}




