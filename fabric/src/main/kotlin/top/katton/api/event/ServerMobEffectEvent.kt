package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Mob effect events for Fabric platform.
 *
 * This object provides events related to mob effects (potions) including
 * adding, removing, and modifying effects on entities.
 */
object ServerMobEffectEvent {

    /**
     * Initializes the mob effect events by registering Fabric event handlers.
     * This method should be called during mod initialization.
     */
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

    /**
     * Event triggered to allow or deny a mob effect being added to an entity.
     *
     * @return true to allow the effect, false to cancel it.
     */
    val onAllowAdd = createAll<MobEffectAllowAddArg>()

    /**
     * Event triggered before a mob effect is added to an entity.
     * Use this for pre-processing or modification.
     */
    val onBeforeAdd = createUnit<MobEffectAddArg>()

    /**
     * Event triggered after a mob effect has been added to an entity.
     */
    val onAfterAdd = createUnit<MobEffectAddArg>()

    /**
     * Event triggered to allow or deny early removal of a mob effect.
     *
     * @return true to allow removal, false to cancel it.
     */
    val onAllowEarlyRemove = createAll<MobEffectAllowEarlyRemoveArg>()

    /**
     * Event triggered before a mob effect is removed from an entity.
     */
    val onBeforeRemove = createUnit<MobEffectBeforeRemoveArg>()

    /**
     * Event triggered after a mob effect has been removed from an entity.
     */
    val onAfterRemove = createUnit<MobEffectAfterRemoveArg>()

    /**
     * Argument class for mob effect allow-add events.
     *
     * @property entity The entity receiving the effect
     * @property effectInstance The effect instance being added
     * @property context The context of the effect event
     */
    data class MobEffectAllowAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * Argument class for mob effect add events.
     *
     * @property entity The entity receiving the effect
     * @property effectInstance The effect instance being added
     * @property context The context of the effect event
     */
    data class MobEffectAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * Argument class for mob effect allow-early-remove events.
     *
     * @property entity The entity losing the effect
     * @property effectInstance The effect instance being removed
     * @property context The context of the effect event
     */
    data class MobEffectAllowEarlyRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * Argument class for mob effect before-remove events.
     *
     * @property entity The entity losing the effect
     * @property effectInstance The effect instance being removed
     * @property context The context of the effect event
     */
    data class MobEffectBeforeRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * Argument class for mob effect after-remove events.
     *
     * @property entity The entity that lost the effect
     * @property effectInstance The effect instance that was removed
     * @property context The context of the effect event
     */
    data class MobEffectAfterRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)
}
