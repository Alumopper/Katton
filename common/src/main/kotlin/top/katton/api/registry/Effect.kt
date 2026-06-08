@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * MobEffect registration API for custom status effects.
 *
 * This module provides functions to register custom MobEffects (status effects)
 * with hot-reload support. Effects registered through this API can be reloaded
 * during development without restarting the game.
 *
 * %zh
 * 用于注册自定义状态效果的 MobEffect API。
 * 该模块提供一组在热重载支持下注册自定义 MobEffect 的函数。
 * 通过此 API 注册的效果可以在开发过程中重新加载，而无需重启游戏。
 */

/**
 * %en
 * Registers a native MobEffect with hot-reload support (String overload).
 *
 * This is the primary API for registering custom MobEffect subclasses from scripts.
 * The effect will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * %zh
 * 注册原生 MobEffect，并支持热重载（String 重载）。
 * 这是脚本中注册自定义 MobEffect 子类的主要 API。
 * 该效果会注册到全局 Minecraft 注册表，并具备完整的热重载能力。
 * @param id
 * %en Effect identifier (e.g., "mymod:custom_effect")
 * %zh Effect 标识符，例如 "mymod:custom_effect"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param effectFactory
 * %en Factory function to create the MobEffect instance
 * %zh 用于创建 MobEffect 实例的工厂函数。
 * @return
 * %en registered KattonMobEffectEntry
 * %zh 已注册的 KattonMobEffectEntry。
 * @example
 * %en
 * ```kotlin
 * registerNativeEffect("mymod:custom_effect") {
 *     object : MobEffect(MobEffectCategory.BENEFICIAL, 0xFF5500) {
 *         override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
 *             // Custom tick logic
 *         }
 *     }
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
fun registerNativeEffect(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry = registerNativeEffect(id(id), registerMode, effectFactory)

/**
 * %en
 * Registers a native MobEffect with hot-reload support (Identifier overload).
 *
 * %zh
 * 注册原生 MobEffect，并支持热重载（Identifier 重载）。
 * @param id
 * %en Effect identifier
 * %zh Effect 标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param effectFactory
 * %en Factory function to create the MobEffect instance
 * %zh 用于创建 MobEffect 实例的工厂函数。
 * @return
 * %en registered KattonMobEffectEntry
 * %zh 已注册的 KattonMobEffectEntry。
 */
fun registerNativeEffect(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry {
    return KattonRegistry.EFFECTS.newNative(id, registerMode, effectFactory)
}

/**
 * %en
 * Utility factory for quickly creating a simple custom MobEffect.
 *
 * Creates a basic MobEffect with the specified category and color.
 * For more complex effects, use the full factory pattern with registerNativeEffect.
 *
 * %zh
 * 用于快速创建简单自定义 MobEffect 的工厂函数。
 * 会按照指定类别和颜色创建一个基础 MobEffect。
 * 如果需要更复杂的效果，请使用 registerNativeEffect 的完整工厂模式。
 * @param category
 * %en The effect category (BENEFICIAL, HARMFUL, or NEUTRAL)
 * %zh 效果类别（BENEFICIAL、HARMFUL 或 NEUTRAL）。
 * @param color
 * %en The effect color in RGB format
 * %zh 以 RGB 格式表示的效果颜色。
 * @return
 * %en new MobEffect instance
 * %zh 新建的 MobEffect 实例。
 */
fun createSimpleEffect(
    category: MobEffectCategory,
    color: Int
): MobEffect = object : MobEffect(category, color) {}
