package top.katton.api.event

import net.minecraft.world.entity.player.Player
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.PlayerWakeUpEvent
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import top.katton.Katton
import top.katton.api.event.LivingBehaviorEvent.onPlayerWakeUp
import top.katton.bridger.EventResult
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.create
import top.katton.util.createAll
import top.katton.util.createAny
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * %en
 * Living entity behavior events for NeoForge platform.
 *
 * This object provides events related to living entity behaviors including
 * animal taming, baby spawning, elytra flight, and sleeping.
 *
 * %zh
 * NeoForge 平台的生物行为事件。
 * 此对象提供与生物实体行为相关的事件，包括驯服动物、生成幼体、鞘翅飞行和睡眠。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object LivingBehaviorEvent {

    // === Animal Taming ===

    @JvmStatic
    @SubscribeEvent
    private fun handleAnimalTame(e: AnimalTameEvent) {
        val arg = AnimalTameArg(e.animal, e.tamer)
        onAnimalTame(arg)
        setCancel(onAnimalTame, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleBabySpawn(e: BabyEntitySpawnEvent) {
        val arg = BabySpawnArg(e.parentA, e.parentB, e.child)
        onBabySpawn(arg)
        setCancel(onBabySpawn, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerWakeUp(e: PlayerWakeUpEvent) {
        val arg = PlayerWakeUpArg(e.entity, e.wakeImmediately(), e.updateLevel())
        onPlayerWakeUp(arg)
    }

    /**
     * %en
     * Event triggered when an animal is being tamed.
     * Can be cancelled to prevent taming.
     *
     * %zh
     * 当动物正在被驯服时触发。
     * 可取消以阻止驯服。
     */
    val onAnimalTame = createCancellableUnit<AnimalTameArg>()

    // === Baby Spawning ===

    /**
     * %en
     * Event triggered when a baby entity is spawning (breeding).
     * Can be cancelled to prevent the spawn.
     *
     * %zh
     * 当幼体生成（繁殖）时触发。
     * 可取消以阻止生成。
     */
    val onBabySpawn = createCancellableUnit<BabySpawnArg>()

    // === Elytra Events ===

    /**
     * %en
     * Event triggered to check if an entity is allowed to use elytra.
     *
     * %zh
     * 用于检查实体是否允许使用鞘翅。
     * @return
     * %en to allow elytra usage, false to deny.
     * %zh 返回 true 以允许使用鞘翅，返回 false 以拒绝。
     */
    @JvmField
    val onElytraAllow = createAll<ElytraAllowArg>()

    /**
     * %en
     * Event triggered to provide custom elytra flight behavior.
     *
     * %zh
     * 用于提供自定义的鞘翅飞行行为。
     * @return
     * %en if custom behavior is applied, false to use default.
     * %zh 如果应用了自定义行为返回 true，否则返回 false 使用默认行为。
     */
    @JvmField
    val onElytraCustom = createAny<ElytraCustomArg>()

    // === Sleep Events ===

    /**
     * %en
     * Event triggered to check if a player is allowed to sleep.
     *
     * %zh
     * 用于检查玩家是否允许睡觉。
     * @return
     * %en if sleep is denied, null to allow.
     * %zh 如果不允许睡觉则返回非 null，返回 null 表示允许睡觉。
     */
    @JvmField
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    /**
     * %en
     * Event triggered when a player starts sleeping.
     *
     * %zh
     * 当玩家开始睡觉时触发。
     */
    @JvmField
    val onStartSleeping = createUnit<SleepingArg>()

    /**
     * %en
     * Event triggered when a player stops sleeping.
     *
     * %zh
     * 当玩家停止睡觉时触发。
     */
    @JvmField
    val onStopSleeping = createUnit<SleepingArg>()

    /**
     * %en
     * Event triggered to check if a player is allowed to use a bed.
     *
     * %zh
     * 用于检查玩家是否允许使用床。
     * @return
     * %en indicating the result of the check.
     * %zh 返回检查结果。
     */
    @JvmField
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    /**
     * %en
     * Event triggered to check if nearby monsters prevent sleeping.
     *
     * %zh
     * 用于检查附近怪物是否会阻止睡觉。
     * @return
     * %en indicating whether monsters should prevent sleep.
     * %zh 返回是否应由怪物阻止睡觉。
     */
    @JvmField
    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    /**
     * %en
     * Event triggered to check if time should reset after sleeping.
     *
     * %zh
     * 用于检查睡觉后是否应重置时间。
     * @return
     * %en to allow time reset, false to prevent it.
     * %zh 返回 true 以允许重置时间，返回 false 以阻止。
     */
    @JvmField
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    /**
     * %en
     * Event triggered to modify the sleeping direction when entering a bed.
     *
     * %zh
     * 用于修改进入床时玩家的朝向。
     * @return
     * %en modified direction for the player to face.
     * %zh 返回修改后的玩家朝向。
     */
    @JvmField
    val onModifySleepingDirection = create { events ->
        { arg: ModifySleepingDirectionArg ->
            var dir = arg.direction
            events.forEach {
                handler -> dir = handler(arg.copy(direction = dir))
            }
            dir
        }
    }

    /**
     * %en
     * Event triggered to check if spawn point should be set when sleeping.
     *
     * %zh
     * 用于检查睡觉时是否应设置重生点。
     * @return
     * %en to allow setting spawn, false to prevent it.
     * %zh 返回 true 以允许设置重生点，返回 false 以阻止。
     */
    @JvmField
    val onAllowSettingSpawn = createAll<AllowSettingSpawnArg>()

    /**
     * %en
     * Event triggered to set the bed occupation state.
     *
     * %zh
     * 用于设置床的占用状态。
     * @return
     * %en if the state was handled, false for default behavior.
     * %zh 如果状态已处理则返回 true，否则使用默认行为。
     */
    @JvmField
    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

    /**
     * %en
     * Event triggered to modify the player's wake-up position.
     *
     * %zh
     * 用于修改玩家醒来的位置。
     * @return
     * %en modified Vec3 wake-up position.
     * %zh 返回修改后的 Vec3 醒来位置。
     */
    @JvmField
    val onModifyWakeUpPosition = create { events ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { handler -> p = handler(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    /**
     * %en
     * Event triggered when a player wakes up from sleeping.
     *
     * %zh
     * 当玩家从睡眠中醒来时触发。
     */
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()

}
