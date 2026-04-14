package qwq

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import top.katton.api.ServerScriptEntrypoint
import top.katton.api.datapack.blockTags
import top.katton.api.datapack.itemTags
import top.katton.api.datapack.recipes
import top.katton.api.datapack.tagRef
import top.katton.api.dpcaller.*
import top.katton.api.dpcaller.get
import top.katton.api.dpcaller.invoke
import top.katton.api.event.EntityLoadArg
import top.katton.api.event.ServerEntityEvent
import top.katton.api.event.ServerEvent.onStartServerTick
import top.katton.api.plus
import top.katton.api.registry.registerNativeBlock
import top.katton.api.registry.registerNativeEffect
import top.katton.api.registry.registerNativeItem
import top.katton.registry.RegisterMode

@ServerScriptEntrypoint
fun main() {
    // 示例：动态注册一个可热重载的方块
    registerNativeBlock(
        id = "qwq:test_block",
        registerMode = RegisterMode.RELOADABLE
    ) { props ->
        Block(
            props
                .strength(3.0f, 6.0f)
                .requiresCorrectToolForDrops()
        )
    }

    // 示例：动态注册一个可热重载的状态效果
    registerNativeEffect(
        id = "qwq:test_qwq",
        registerMode = RegisterMode.RELOADABLE
    ) {
        object : MobEffect(MobEffectCategory.BENEFICIAL, 0x55FF55) {
            override fun applyEffectTick(serverLevel: ServerLevel, mob: LivingEntity, amplification: Int): Boolean {
                mob.hurtServer(serverLevel, mob.damageSources().wither(), 1.0F);
                return super.applyEffectTick(serverLevel, mob, amplification)
            }

            override fun shouldApplyEffectTickThisTick(tickCount: Int, amplification: Int): Boolean {
                return tickCount % 20 == 0 // 每秒造成一次伤害
            }
        }
    }

    ServerEntityEvent.onAfterEntityLoad += load@
    fun(arg: EntityLoadArg) {
        val (entity, _) = arg
        if (entity !is Arrow) return
        //if a player shoots an arrow, check the bow's data
        val owner = entity.owner
        if (owner is ServerPlayer) {
            onArrowShot(owner, entity)
        }
    }

    onStartServerTick += tick@
    fun(_) {
        processTNTArrow()
    }

    // 使用延迟工厂注册原生 Item：构造器在注册窗口（已解冻的注册表）内执行，支持热重载
    registerNativeItem(
        id = "qwq:qwq",
        registerMode = RegisterMode.RELOADABLE,
        configure = {
            setName(Component.literal("Hello"))
            stacksTo(1)
            setModel(Identifier.fromNamespaceAndPath("minecraft", "diamond"))
            food(FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).build())
        }
    ) { it ->
        // it 可直接作为 Item 构造参数（由 Katton 提供的 Item 属性封装）
        object : Item(it) {
            override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
                (player as? ServerPlayer)?.let { p -> tell(p , "Used the hello item!") }
                // Do not consume the click so block interactions can still run.
                return InteractionResult.PASS
            }
        }
    }
}

val tntArrow = HashSet<Arrow>()

fun onArrowShot(player: ServerPlayer, arrow: Arrow) {
    tell(player, Component.empty() + "你手持的武器是: " + player[KattonItemCollection.Weapon.MainHand]?.itemName)
    //this arrow is shot by a tnt bow, make it explode
    if (player.mainHandItem.nbt["tnt"](false)) {
        tntArrow.add(arrow)
    }
}

fun processTNTArrow() {
    val iterator = tntArrow.iterator()
    while (iterator.hasNext()) {
        val arrow = iterator.next()

        if (getEntityNbt(arrow).getBooleanOr("inGround", false)) {

            summon(arrow.level() as ServerLevel, "creeper", arrow.position())
//
//            arrow.level().explode(
//                arrow,
//                arrow.damageSources().explosion(arrow, arrow.owner),
//                null,
//                arrow.position(),
//                16.0f,
//                false,
//                Level.ExplosionInteraction.TNT
//            )
            iterator.remove()
            arrow.kill(arrow.level() as ServerLevel)
        }
    }
}
