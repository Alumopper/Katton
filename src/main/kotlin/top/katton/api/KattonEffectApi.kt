@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a native MobEffect with hot-reload support.
 */
fun registerNativeEffect(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry = registerNativeEffect(id(id), registerMode, effectFactory)

/**
 * Registers a native MobEffect with hot-reload support.
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
 */
fun createSimpleEffect(
    category: MobEffectCategory,
    color: Int
): MobEffect = object : MobEffect(category, color) {}

