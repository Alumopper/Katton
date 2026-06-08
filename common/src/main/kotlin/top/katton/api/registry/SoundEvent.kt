@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * Registers a native SoundEvent with hot-reload support.
 *
 * %zh
 * 注册原生 SoundEvent，并支持热重载。
 * @param id
 * %en Sound identifier (e.g., "mymod:custom_sound")
 * %zh 声音标识符，例如 "mymod:custom_sound"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param soundEventFactory
 * %en Factory function to create the SoundEvent instance
 * %zh 创建 SoundEvent 实例的工厂函数。
 * @return
 * %en registered KattonSoundEventEntry
 * %zh 已注册的 KattonSoundEventEntry。
 */
fun registerNativeSoundEvent(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry = registerNativeSoundEvent(id(id), registerMode, soundEventFactory)

/**
 * %en
 * Registers a native SoundEvent with hot-reload support.
 *
 * %zh
 * 注册原生 SoundEvent，并支持热重载。
 * @param id
 * %en Sound identifier
 * %zh 声音标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param soundEventFactory
 * %en Factory function to create the SoundEvent instance
 * %zh 创建 SoundEvent 实例的工厂函数。
 * @return
 * %en registered KattonSoundEventEntry
 * %zh 已注册的 KattonSoundEventEntry。
 */
fun registerNativeSoundEvent(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry {
    return KattonRegistry.SOUND_EVENTS.newNative(id, registerMode, soundEventFactory)
}

/**
 * %en
 * Utility factory for quickly creating a variable-range SoundEvent.
 *
 * %zh
 * 用于快速创建可变范围 SoundEvent 的工厂函数。
 * @param id
 * %en The sound identifier
 * %zh 声音标识符。
 * @return
 * %en new SoundEvent with variable range
 * %zh 具有可变范围的新 SoundEvent。
 */
fun createVariableRangeSoundEvent(id: String): SoundEvent =
    SoundEvent.createVariableRangeEvent(id(id))
