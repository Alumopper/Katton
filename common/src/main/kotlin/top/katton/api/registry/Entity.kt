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
 * Registers a complete native Entity with hot-reload support.
 *
 * This is the primary API for registering custom entities from scripts.
 * It handles EntityType registration plus optional attributes, spawn egg,
 * and spawn placement configuration in a single call.
 *
 * @param id Entity identifier (e.g., "mymod:custom_mob")
 * @param registerMode Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * @param configure Configuration lambda for entity properties (dimensions, category, attributes, etc.)
 * @param entityFactory Factory function to create the EntityType instance
 * @return The registered KattonEntityTypeEntry
 *
 * @example
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
 */
@ApiStatus.Experimental
fun registerNativeEntity(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonEntityProperties.() -> Unit = {},
    entityFactory: (KattonEntityProperties) -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry = registerNativeEntity(id(id), registerMode, configure, entityFactory)

/**
 * Registers a complete native Entity with hot-reload support (Identifier overload).
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
 * @param id Entity identifier
 * @param entityType The already-registered entity type
 * @param configure Configuration lambda for attributes
 * @param reloadable true for RELOADABLE, false for GLOBAL
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
 * Registers a spawn placement rule independently.
 *
 * @param T The mob entity type
 * @param entityType The entity type
 * @param placementType Where the entity can spawn (e.g., ON_GROUND, IN_WATER)
 * @param heightmap The heightmap type for spawn checks
 * @param predicate Custom spawn predicate
 * @param reloadable true for RELOADABLE, false for GLOBAL
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
 * Registers a spawn egg item for an entity type independently.
 *
 * Use this to create a spawn egg for an entity registered via
 * [registerNativeEntityType]. For new entities, prefer
 * [registerNativeEntity] with `withSpawnEgg()`.
 *
 * In MC 1.21.11+, spawn egg colors are derived from the entity type
 * automatically.
 *
 * @param id Spawn egg item identifier (e.g., "mymod:custom_mob_spawn_egg")
 * @param entityType The entity type this egg spawns
 * @param registerMode Registration mode
 * @return The registered KattonItemEntry
 */
@ApiStatus.Experimental
fun registerSpawnEgg(
    id: String,
    entityType: EntityType<out net.minecraft.world.entity.Mob>,
    registerMode: RegisterMode = RegisterMode.WORLD
): KattonRegistry.KattonItemEntry = registerSpawnEgg(id(id), entityType, registerMode)

/**
 * Registers a spawn egg item for an entity type independently (Identifier overload).
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
