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
import onArrowShot
import test.say
import test.say2
import top.katton.api.dpcaller.KattonItemCollection
import top.katton.api.dpcaller.get
import top.katton.api.dpcaller.getEntityNbt
import top.katton.api.dpcaller.invoke
import top.katton.api.dpcaller.nbt
import top.katton.api.dpcaller.tell
import top.katton.api.datapack.advancements
import top.katton.api.datapack.blockTags
import top.katton.api.datapack.itemTags
import top.katton.api.datapack.recipes
import top.katton.api.datapack.tagRef
import top.katton.api.event.ChunkAndBlockEvent
import top.katton.api.event.EntityLoadArg
import top.katton.api.event.ServerEntityEvent
import top.katton.api.event.ServerEvent.onStartServerTick
import top.katton.api.event.ServerLivingEntityEvent
import top.katton.api.plus
import top.katton.api.registry.registerNativeBlock
import top.katton.api.registry.registerNativeEffect
import top.katton.api.registry.registerNativeItem
import top.katton.registry.RegisterMode

fun main() {
    recipes {
        shaped("qwq:hello_block", "qwq:test_block") {
            pattern(
                "DD",
                "DD"
            )
            define('D', "minecraft:diamond")
        }

        smelting("qwq:smelt_hello", "qwq:qwq") {
            input("minecraft:diamond")
            experience = 0.5f
            cookingTime = 100
        }

        remove("minecraft:diamond_block")
    }

    advancements {
        advancement("qwq:hello_progress") {
            parent("minecraft:story/root")
            display {
                title = "Hello Progress"
                description = "Craft the scripted hello block"
                icon = "qwq:test_block"
                frame = "task"
            }
            rewards {
                experience = 50
                recipe("qwq:hello_block")
            }
            recipeUnlocked("crafted_hello_block", "qwq:hello_block")
            inventoryChanged("has_hello_block", "qwq:test_block")
            requireAny()
        }
    }

    itemTags {
        tag("c:gems") {
            add("minecraft:diamond")
            add("qwq:qwq")
        }
    }

    blockTags {
        tag("minecraft:mineable/pickaxe") {
            add("qwq:test_block")
        }

        tag("c:needs_hello_tool") {
            add("qwq:test_block")
        }
    }

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
            say(owner)
            say2(owner)
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

    recipes {
        shapeless("qwq:hello_from_tag", "qwq:qwq", count = 2) {
            input(tagRef("c:gems"))
            input("minecraft:stick")
        }
    }


//    onPlayerJoin += started@
//    fun(arg: PlayerArg) {
//        tell(arg.player, Component.literal("Give you a hello world!"))
//        // 获取物品
//        ITEMS[id("qwq:custom_item")]?.let {
//            giveItem(arg.player, it.getDefaultInstance())
//        }
//    }


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
            arrow.level().explode(
                arrow,
                arrow.damageSources().explosion(arrow, arrow.owner),
                null,
                arrow.position(),
                16.0f,
                false,
                Level.ExplosionInteraction.TNT
            )
            iterator.remove()
            arrow.kill(arrow.level() as ServerLevel)
        }
    }
}

@Suppress("unused") private val main = main()
