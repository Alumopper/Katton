package top.katton.registry

import com.mojang.serialization.Lifecycle
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RegistryEntryRestoreTest {
    @Test fun `rollback retains actual holder identity and numeric slots`() {
        val registryKey = ResourceKey.createRegistryKey<Any>(Identifier.fromNamespaceAndPath("katton_test", "rollback"))
        val registry = MappedRegistry(registryKey, Lifecycle.stable())
        val id = Identifier.fromNamespaceAndPath("katton_test", "first")
        val secondId = Identifier.fromNamespaceAndPath("katton_test", "second")
        val first = Any()
        val second = Any()
        Registry.register(registry, id, first)
        Registry.register(registry, secondId, second)
        registry.freeze()
        val key = ResourceKey.create(registryKey, id)
        val holder = registry.get(key).orElseThrow()
        val slot = registry.getId(first)
        val secondSlot = registry.getId(second)
        val restore = captureRegistryEntry(registry, key)
        unregisterAll(registry, listOf(id)) { ResourceKey.create(registryKey, it) }
        restore()
        assertSame(holder, registry.get(key).orElseThrow())
        assertSame(first, registry.getValue(id))
        assertEquals(slot, registry.getId(first))
        assertEquals(secondSlot, registry.getId(second))
    }
}
