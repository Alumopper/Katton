package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * %en
 * Server-side living entity events for Fabric platform.
 *
 * This object provides events related to living entity lifecycle including
 * damage, death, and mob conversion events.
 *
 * %zh
 * Fabric 平台的服务端生物实体事件。
 * 此对象提供与生物实体生命周期相关的事件，包括伤害、死亡以及生物转换事件。
 */
@Suppress("unused")
object ServerLivingEntityEvent {

    fun initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { a, b, c ->
            onAllowDamage(AllowDamageArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { a, b, c, d, e ->
            onAfterDamage(AfterDamageArg(a, b, c, d, e))
        }

        ServerLivingEntityEvents.ALLOW_DEATH.register { a, b, c ->
            onAllowDeath(AllowDeathArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { a, b ->
            onAfterDeath(AfterDeathArg(a, b))
        }

        ServerLivingEntityEvents.MOB_CONVERSION.register { a, b, c ->
            onMobConversion(MobConversionArg(a, b, c))
        }
    }

    /**
 * %en
 * Event triggered when a living entity is hurt.
 * Can be cancelled to prevent the damage.
 *
 * %zh
 * 当生物实体受到伤害时触发。
 * 可取消以阻止伤害处理。
 */
    @JvmField
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    /**
 * %en
 * Event triggered to allow or deny damage to a living entity.
 *
 * %zh
 * 当需要决定是否允许生物实体受到伤害时触发。
 * @return
 * %en to allow the damage, false to cancel it.
 * %zh 返回值允许伤害，false 表示取消。
 */
    val onAllowDamage = createAll<AllowDamageArg>()

    /**
 * %en
 * Event triggered after a living entity takes damage.
 *
 * %zh
 * 当生物实体受到伤害之后触发。
 */
    val onAfterDamage = createUnit<AfterDamageArg>()

    /**
 * %en
 * Event triggered to allow or deny death of a living entity.
 *
 * %zh
 * 当需要决定是否允许生物实体死亡时触发。
 * @return
 * %en to allow the death, false to cancel it.
 * %zh 返回值允许死亡，false 表示取消。
 */
    val onAllowDeath = createAll<AllowDeathArg>()

    /**
 * %en
 * Event triggered after a living entity dies.
 *
 * %zh
 * 当生物实体死亡之后触发。
 */
    val onAfterDeath = createUnit<AfterDeathArg>()

    /**
 * %en
 * Event triggered when a living entity falls.
 * Can be cancelled to prevent fall damage processing.
 *
 * %zh
 * 当生物实体摔落时触发。
 * 可取消以阻止后续处理。
 */
    @JvmField
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    /**
 * %en
 * Event triggered when a mob is converted to another type
 * (e.g., zombie villager curing, piglin zombification).
 *
 * %zh
 * 当生物转换为其他类型时触发。
 * （例如僵尸村民治愈、疣猪兽僵尸化）。
 */
    val onMobConversion = createUnit<MobConversionArg>()
}
