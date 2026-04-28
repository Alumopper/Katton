@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import top.katton.registry.KattonEntityProperties
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a native EntityType with hot-reload support.
 *
 * This is a lower-level API that only registers the EntityType itself.
 * For complete entity registration (including attributes, spawn egg, and
 * spawn placement), use [registerNativeEntity] instead.
 *
 * @param id Entity identifier (e.g., "mymod:custom_entity")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param entityTypeFactory Factory function to create the EntityType instance
 * @return The registered KattonEntityTypeEntry
 */
fun registerNativeEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry = registerNativeEntityType(id(id), registerMode, entityTypeFactory)

/**
 * Registers a native EntityType with hot-reload support (Identifier overload).
 *
 * @param id Entity identifier
 * @param registerMode Registration mode
 * @param entityTypeFactory Factory function to create the EntityType instance
 * @return The registered KattonEntityTypeEntry
 */
fun registerNativeEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry {
    return KattonRegistry.ENTITY_TYPES.newNative(id, registerMode, entityTypeFactory)
}