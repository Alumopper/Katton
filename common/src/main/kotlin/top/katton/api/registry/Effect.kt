@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * MobEffect registration API for custom status effects.
 *
 * This module provides functions to register custom MobEffects (status effects)
 * with hot-reload support. Effects registered through this API can be reloaded
 * during development without restarting the game.
 */

/**
 * Registers a native MobEffect with hot-reload support (String overload).
 *
 * This is the primary API for registering custom MobEffect subclasses from scripts.
 * The effect will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * @param id Effect identifier (e.g., "mymod:custom_effect")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param effectFactory Factory function to create the MobEffect instance
 * @return The registered KattonMobEffectEntry
 *
 * @example
 * ```kotlin
 * registerNativeEffect("mymod:custom_effect") {
 *     object : MobEffect(MobEffectCategory.BENEFICIAL, 0xFF5500) {
 *         override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
 *             // Custom tick logic
 *         }
 *     }
 * }
 * ```
 */
fun registerNativeEffect(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry = registerNativeEffect(id(id), registerMode, effectFactory)

/**
 * Registers a native MobEffect with hot-reload support (Identifier overload).
 *
 * @param id Effect identifier
 * @param registerMode Registration mode
 * @param effectFactory Factory function to create the MobEffect instance
 * @return The registered KattonMobEffectEntry
 */
fun registerNativeEffect(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry {
    return KattonRegistry.EFFECTS.newNative(id, registerMode, effectFactory)
}

/**
 * Utility factory for quickly creating a simple custom MobEffect.
 *
 * Creates a basic MobEffect with the specified category and color.
 * For more complex effects, use the full factory pattern with registerNativeEffect.
 *
 * @param category The effect category (BENEFICIAL, HARMFUL, or NEUTRAL)
 * @param color The effect color in RGB format
 * @return A new MobEffect instance
 */
fun createSimpleEffect(
    category: MobEffectCategory,
    color: Int
): MobEffect = object : MobEffect(category, color) {}

