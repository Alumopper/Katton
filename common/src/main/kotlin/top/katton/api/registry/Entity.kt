@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.SpawnPlacementType
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.levelgen.Heightmap
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonEntityProperties
import top.katton.registry.KattonItemProperties
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id
import top.katton.platform.SpawnPlacementHooks

/**
 * %en
 * Registers a complete native Entity with hot-reload support.
 *
 * This is the primary API for registering custom entities from scripts.
 * It handles EntityType registration plus optional attributes, spawn egg,
 * and spawn placement configuration in a single call.
 *
 * %zh
 * 注册完整的原生 Entity，并支持热重载。
 * 这是脚本中注册自定义实体的主要 API。
 * 它会在一次调用中完成 EntityType 注册，以及可选的属性、刷怪蛋和生成位置配置。
 * @param id
 * %en Entity identifier (e.g., "mymod:custom_mob")
 * %zh Entity 标识符，例如 "mymod:custom_mob"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param configure
 * %en Configuration lambda for entity properties (dimensions, category, attributes, etc.)
 * %zh Entity 属性配置 lambda（尺寸、类别、属性等）。
 * @param entityFactory
 * %en Factory function to create the EntityType instance
 * %zh 创建 EntityType 实例的工厂函数。
 * @return
 * %en registered KattonEntityTypeEntry
 * %zh 已注册的 KattonEntityTypeEntry。
 * @example
 * %en
 * ```kotlin
 * registerNativeEntity(
 *     id = "mymod:custom_mob",
 *     registerMode = RegisterMode.GLOBAL,
 *     configure = {
 *         dimensions(0.6f, 1.8f)
 *         category = MobCategory.CREATURE
 *         maxHealth(20.0)
 *         movementSpeed(0.25)
 *         followRange(32.0)
 *         withSpawnEgg()
 *         spawnPlacement(SpawnPlacementTypes.ON_GROUND)
 *     }
 * ) { props ->
 *     EntityType.Builder.create<CustomMob>(::CustomMob, props.category)
 *         .dimensions(props.dimensions.width, props.dimensions.height)
 *         .clientTrackingRange(props.clientTrackingRange)
 *         .updateInterval(props.trackingTickInterval)
 *         .build(ResourceKey.create(Registries.ENTITY_TYPE, id(props.id)))
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun registerNativeEntity(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonEntityProperties.() -> Unit = {},
    entityFactory: (KattonEntityProperties) -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry = registerNativeEntity(id(id), registerMode, configure, entityFactory)

/**
 * %en
 * Registers a complete native Entity with hot-reload support (Identifier overload).
 *
 * %zh
 * 注册完整的原生 Entity，并支持热重载（Identifier 重载）。
 */
fun registerNativeEntity(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonEntityProperties.() -> Unit = {},
    entityFactory: (KattonEntityProperties) -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry {
    val properties = KattonEntityProperties(id).apply(configure)
    return KattonRegistry.ENTITY_TYPES.newNativeWithProperties(id, registerMode, properties, entityFactory)
}

/**
 * %en
 * Registers entity default attributes independently.
 *
 * Use this when you want to register attributes for an entity that was
 * already registered via [registerNativeEntityType]. For new entities,
 * prefer [registerNativeEntity] which handles attributes automatically.
 *
 * Note: This uses the reloadable path by default since standalone
 * attribute registration typically happens during hot-reload.
 * For global entities, use [registerNativeEntity] which routes
 * through the correct mode-aware path.
 *
 * %zh
 * 独立注册实体的默认属性。
 * 当你要为已经通过 [registerNativeEntityType] 注册的实体补充属性时使用此方法。
 * 对于新实体，优先使用 [registerNativeEntity]，它会自动处理属性注册。
 * 注意：这里默认使用可重载路径，因为独立的属性注册通常发生在热重载期间。
 * 对于全局实体，请使用 [registerNativeEntity]，它会走正确的模式分流路径。
 * @param id
 * %en Entity identifier
 * %zh 实体标识符。
 * @param entityType
 * %en The already-registered entity type
 * %zh 已注册的实体类型。
 * @param configure
 * %en Configuration lambda for attributes
 * %zh 属性配置 lambda。
 * @param reloadable
 * %en true for RELOADABLE, false for GLOBAL
 * %zh `true` 表示 RELOADABLE，`false` 表示 GLOBAL。
 */
@ApiStatus.Experimental
fun registerEntityAttributes(
    id: String,
    entityType: EntityType<out net.minecraft.world.entity.LivingEntity>,
    configure: KattonEntityProperties.() -> Unit,
    reloadable: Boolean = true
) {
    val properties = KattonEntityProperties(id(id)).apply(configure)
    val attributeSupplier = properties.buildAttributes()
    if (attributeSupplier != null) {
        top.katton.platform.EntityAttributeHooks.registerAttributes(entityType, attributeSupplier, reloadable)
    }
}

/**
 * %en
 * Registers a spawn placement rule independently.
 *
 * %zh
 * 独立注册生成位置规则。
 * @param T
 * %en The mob entity type
 * %zh 生物实体类型。
 * @param entityType
 * %en The entity type
 * %zh 实体类型。
 * @param placementType
 * %en Where the entity can spawn (e.g., ON_GROUND, IN_WATER)
 * %zh 实体可生成的位置，例如 ON_GROUND、IN_WATER。
 * @param heightmap
 * %en The heightmap type for spawn checks
 * %zh 用于生成检查的 Heightmap 类型。
 * @param predicate
 * %en Custom spawn predicate
 * %zh 自定义生成条件。
 * @param reloadable
 * %en true for RELOADABLE, false for GLOBAL
 * %zh `true` 表示 RELOADABLE，`false` 表示 GLOBAL。
 */
@ApiStatus.Experimental
@Suppress("UNCHECKED_CAST")
fun <T : net.minecraft.world.entity.Mob> registerSpawnPlacement(
    entityType: EntityType<T>,
    placementType: SpawnPlacementType,
    heightmap: Heightmap.Types = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
    predicate: SpawnPlacements.SpawnPredicate<T>,
    reloadable: Boolean = true
) {
    if (reloadable) {
        SpawnPlacementHooks.registerReloadable(entityType, placementType, heightmap, predicate)
    } else {
        SpawnPlacementHooks.registerGlobal(entityType, placementType, heightmap, predicate)
    }
}

/**
 * %en
 * Registers a spawn egg item for an entity type independently.
 *
 * Use this to create a spawn egg for an entity registered via
 * [registerNativeEntityType]. For new entities, prefer
 * [registerNativeEntity] with `withSpawnEgg()`.
 *
 * In MC 1.21.11+, spawn egg colors are derived from the entity type
 * automatically.
 *
 * %zh
 * 独立为某个实体类型注册刷怪蛋物品。
 * 当实体是通过 [registerNativeEntityType] 注册时，可以使用这个方法创建刷怪蛋。
 * 对于新实体，优先使用带有 `withSpawnEgg()` 的 [registerNativeEntity]。
 * 在 MC 1.21.11+ 中，刷怪蛋颜色会自动根据实体类型推导。
 * @param id
 * %en Spawn egg item identifier (e.g., "mymod:custom_mob_spawn_egg")
 * %zh 刷怪蛋物品标识符，例如 "mymod:custom_mob_spawn_egg"。
 * @param entityType
 * %en The entity type this egg spawns
 * %zh 该刷怪蛋生成的实体类型。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @return
 * %en registered KattonItemEntry
 * %zh 已注册的 KattonItemEntry。
 */
@ApiStatus.Experimental
fun registerSpawnEgg(
    id: String,
    entityType: EntityType<out net.minecraft.world.entity.Mob>,
    registerMode: RegisterMode = RegisterMode.WORLD
): KattonRegistry.KattonItemEntry = registerSpawnEgg(id(id), entityType, registerMode)

/**
 * %en
 * Registers a spawn egg item for an entity type independently (Identifier overload).
 *
 * %zh
 * 独立为某个实体类型注册刷怪蛋物品（Identifier 重载）。
 */
@ApiStatus.Experimental
fun registerSpawnEgg(
    id: Identifier,
    entityType: EntityType<out net.minecraft.world.entity.Mob>,
    registerMode: RegisterMode = RegisterMode.WORLD
): KattonRegistry.KattonItemEntry {
    val eggProperties = KattonItemProperties(id).apply {
        stacksTo(64)
        setModel(top.katton.registry.id("minecraft:item/zombie_spawn_egg"))
        spawnEgg(entityType)
    }
    return KattonRegistry.ITEMS.newNative(eggProperties, registerMode) { properties ->
        SpawnEggItem(properties)
    }
}
