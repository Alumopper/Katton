@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.core.particles.ParticleType
import net.minecraft.resources.Identifier
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * Registers a native ParticleType with hot-reload support.
 *
 * %zh
 * 注册原生 ParticleType，并支持热重载。
 * @param id
 * %en Particle identifier (e.g., "mymod:custom_particle")
 * %zh 粒子标识符，例如 "mymod:custom_particle"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param particleTypeFactory
 * %en Factory function to create the ParticleType instance
 * %zh 创建 ParticleType 实例的工厂函数。
 * @return
 * %en registered KattonParticleTypeEntry
 * %zh 已注册的 KattonParticleTypeEntry。
 */
fun registerNativeParticleType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry = registerNativeParticleType(id(id), registerMode, particleTypeFactory)

/**
 * %en
 * Registers a native ParticleType with hot-reload support.
 *
 * %zh
 * 注册原生 ParticleType，并支持热重载。
 * @param id
 * %en Particle identifier
 * %zh 粒子标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param particleTypeFactory
 * %en Factory function to create the ParticleType instance
 * %zh 创建 ParticleType 实例的工厂函数。
 * @return
 * %en registered KattonParticleTypeEntry
 * %zh 已注册的 KattonParticleTypeEntry。
 */
fun registerNativeParticleType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry {
    return KattonRegistry.PARTICLE_TYPES.newNative(id, registerMode, particleTypeFactory)
}
