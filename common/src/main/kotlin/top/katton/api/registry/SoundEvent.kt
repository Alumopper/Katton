@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a native SoundEvent with hot-reload support.
 *
 * @param id Sound identifier (e.g., "mymod:custom_sound")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param soundEventFactory Factory function to create the SoundEvent instance
 * @return The registered KattonSoundEventEntry
 */
fun registerNativeSoundEvent(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry = registerNativeSoundEvent(id(id), registerMode, soundEventFactory)

/**
 * Registers a native SoundEvent with hot-reload support.
 *
 * @param id Sound identifier
 * @param registerMode Registration mode
 * @param soundEventFactory Factory function to create the SoundEvent instance
 * @return The registered KattonSoundEventEntry
 */
fun registerNativeSoundEvent(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry {
    return KattonRegistry.SOUND_EVENTS.newNative(id, registerMode, soundEventFactory)
}

/**
 * Utility factory for quickly creating a variable-range SoundEvent.
 *
 * @param id The sound identifier
 * @return A new SoundEvent with variable range
 */
fun createVariableRangeSoundEvent(id: String): SoundEvent =
    SoundEvent.createVariableRangeEvent(id(id))
