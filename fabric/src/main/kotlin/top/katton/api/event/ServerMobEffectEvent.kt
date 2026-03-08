package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Mob effect events on the server (allow add / before/after add / allow early remove / before/after remove).
 */
object ServerMobEffectEvent {

    fun initialize() {
        ServerMobEffectEvents.ALLOW_ADD.register { a, b, c ->
            onAllowAdd(MobEffectAllowAddArg(b, a, c)).getOrElse { true }
        }

        ServerMobEffectEvents.BEFORE_ADD.register { a, b, c ->
            onBeforeAdd(MobEffectAddArg(b, a, c))
        }

        ServerMobEffectEvents.AFTER_ADD.register { a, b, c ->
            onAfterAdd(MobEffectAddArg(b, a, c))
        }

        ServerMobEffectEvents.ALLOW_EARLY_REMOVE.register { a, b, c ->
            onAllowEarlyRemove(MobEffectAllowEarlyRemoveArg(b, a, c)).getOrElse { true }
        }

        ServerMobEffectEvents.BEFORE_REMOVE.register { a, b, c ->
            onBeforeRemove(MobEffectBeforeRemoveArg(b, a, c))
        }

        ServerMobEffectEvents.AFTER_REMOVE.register { a, b, c ->
            onAfterRemove(MobEffectAfterRemoveArg(b, a, c))
        }
    }

    val onAllowAdd = createAll<MobEffectAllowAddArg>()

    val onBeforeAdd = createUnit<MobEffectAddArg>()

    val onAfterAdd = createUnit<MobEffectAddArg>()

    val onAllowEarlyRemove = createAll<MobEffectAllowEarlyRemoveArg>()

    val onBeforeRemove = createUnit<MobEffectBeforeRemoveArg>()

    val onAfterRemove = createUnit<MobEffectAfterRemoveArg>()

    data class MobEffectAllowAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    data class MobEffectAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    data class MobEffectAllowEarlyRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    data class MobEffectBeforeRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    data class MobEffectAfterRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)
}