package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * %en
 * Mob effect events for Fabric platform.
 *
 * This object provides events related to mob effects (potions) including
 * adding, removing, and modifying effects on entities.
 *
 * %zh
 * Fabric 平台的状态效果事件。
 * 此对象提供与状态效果（药水）相关的事件，包括添加、移除和修改实体效果。
 */
object ServerMobEffectEvent {

    /**
     * %en
     * Initializes the mob effect events by registering Fabric event handlers.
     * This method should be called during mod initialization.
     *
     * %zh
     * 通过注册 Fabric 事件处理器初始化状态效果事件。
     * 该方法应在模组初始化期间调用。
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
     * %en
     * Event triggered to allow or deny a mob effect being added to an entity.
     *
     * %zh
     * 当需要决定是否允许向实体添加状态效果时触发。
     * @return
     * %en to allow the effect, false to cancel it.
     * %zh 返回值允许效果生效，false 表示取消。
     */
    val onAllowAdd = createAll<MobEffectAllowAddArg>()

    /**
     * %en
     * Event triggered before a mob effect is added to an entity.
     * Use this for pre-processing or modification.
     *
     * %zh
     * 当状态效果添加到实体之前触发。
     * 可用于预处理或修改。
     */
    val onBeforeAdd = createUnit<MobEffectAddArg>()

    /**
     * %en
     * Event triggered after a mob effect has been added to an entity.
     *
     * %zh
     * 当状态效果已添加到实体之后触发。
     */
    val onAfterAdd = createUnit<MobEffectAddArg>()

    /**
     * %en
     * Event triggered to allow or deny early removal of a mob effect.
     *
     * %zh
     * 当需要决定是否允许提前移除状态效果时触发。
     * @return
     * %en to allow removal, false to cancel it.
     * %zh 返回值允许移除，false 表示取消。
     */
    val onAllowEarlyRemove = createAll<MobEffectAllowEarlyRemoveArg>()

    /**
     * %en
     * Event triggered before a mob effect is removed from an entity.
     *
     * %zh
     * 当状态效果从实体上移除之前触发。
     */
    val onBeforeRemove = createUnit<MobEffectBeforeRemoveArg>()

    /**
     * %en
     * Event triggered after a mob effect has been removed from an entity.
     *
     * %zh
     * 当状态效果已从实体移除之后触发。
     */
    val onAfterRemove = createUnit<MobEffectAfterRemoveArg>()

    /**
     * %en
     * Argument class for mob effect allow-add events.
     *
     * %zh
     * 状态效果允许添加事件的参数类。
     * @property entity
     * %en The entity receiving the effect
     * %zh 接收该效果的实体。
     * @property effectInstance
     * %en The effect instance being added
     * %zh 正在添加的效果实例。
     * @property context
     * %en The context of the effect event
     * %zh 效果事件的上下文。
     */
    data class MobEffectAllowAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * %en
     * Argument class for mob effect add events.
     *
     * %zh
     * 状态效果添加事件的参数类。
     * @property entity
     * %en The entity receiving the effect
     * %zh 接收该效果的实体。
     * @property effectInstance
     * %en The effect instance being added
     * %zh 正在添加的效果实例。
     * @property context
     * %en The context of the effect event
     * %zh 效果事件的上下文。
     */
    data class MobEffectAddArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * %en
     * Argument class for mob effect allow-early-remove events.
     *
     * %zh
     * 状态效果允许提前移除事件的参数类。
     * @property entity
     * %en The entity losing the effect
     * %zh 正在失去该效果的实体。
     * @property effectInstance
     * %en The effect instance being removed
     * %zh 正在移除的效果实例。
     * @property context
     * %en The context of the effect event
     * %zh 效果事件的上下文。
     */
    data class MobEffectAllowEarlyRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * %en
     * Argument class for mob effect before-remove events.
     *
     * %zh
     * 状态效果移除前事件的参数类。
     * @property entity
     * %en The entity losing the effect
     * %zh 正在失去该效果的实体。
     * @property effectInstance
     * %en The effect instance being removed
     * %zh 正在移除的效果实例。
     * @property context
     * %en The context of the effect event
     * %zh 效果事件的上下文。
     */
    data class MobEffectBeforeRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)

    /**
     * %en
     * Argument class for mob effect after-remove events.
     *
     * %zh
     * 状态效果移除后事件的参数类。
     * @property entity
     * %en The entity that lost the effect
     * %zh 已失去该效果的实体。
     * @property effectInstance
     * %en The effect instance that was removed
     * %zh 已移除的效果实例。
     * @property context
     * %en The context of the effect event
     * %zh 效果事件的上下文。
     */
    data class MobEffectAfterRemoveArg(val entity: Entity, val effectInstance: MobEffectInstance, val context: EffectEventContext)
}
