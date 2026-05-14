@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.core.particles.ParticleType
import net.minecraft.resources.Identifier
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a native ParticleType with hot-reload support.
 *
 * @param id Particle identifier (e.g., "mymod:custom_particle")
 * @param registerMode Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * @param particleTypeFactory Factory function to create the ParticleType instance
 * @return The registered KattonParticleTypeEntry
 */
fun registerNativeParticleType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry = registerNativeParticleType(id(id), registerMode, particleTypeFactory)

/**
 * Registers a native ParticleType with hot-reload support.
 *
 * @param id Particle identifier
 * @param registerMode Registration mode
 * @param particleTypeFactory Factory function to create the ParticleType instance
 * @return The registered KattonParticleTypeEntry
 */
fun registerNativeParticleType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry {
    return KattonRegistry.PARTICLE_TYPES.newNative(id, registerMode, particleTypeFactory)
}
