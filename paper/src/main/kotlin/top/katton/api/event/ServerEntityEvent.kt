package top.katton.api.event

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit

object ServerEntityEvent {
    @JvmField val onAfterEntityLoad = createUnit<EntityLoadArg>()
    @JvmField val onEntityUnload = createUnit<EntityUnloadArg>()
    @JvmField val onEquipmentChange = createUnit<Any>()
    @JvmField val onAfterEntityChangeLevel = createUnit<Any>()
    @JvmField val onAfterPlayerChangeLevel = createUnit<Any>()
    @JvmField val onEndermanAnger = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onSpawn(e: CreatureSpawnEvent) {
                val ent: Entity = PaperNmsBridge.toNmsEntity(e.entity)
                val w: ServerLevel = PaperNmsBridge.toNmsLevel(e.entity.world)
                onAfterEntityLoad(EntityLoadArg(ent, w))
            }
            @EventHandler fun onUnload(e: EntityDeathEvent) {
                val ent: Entity = PaperNmsBridge.toNmsEntity(e.entity)
                val w: ServerLevel = PaperNmsBridge.toNmsLevel(e.entity.world)
                onEntityUnload(EntityUnloadArg(ent, w))
            }
        }, plugin)
    }
}




