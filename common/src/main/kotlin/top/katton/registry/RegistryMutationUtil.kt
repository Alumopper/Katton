package top.katton.registry

import com.mojang.serialization.Lifecycle
import it.unimi.dsi.fastutil.objects.Reference2IntMap
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.resources.ResourceKey
import top.katton.util.ReflectUtil

object RegistryMutationUtil {

    private fun isRegistryFrozen(registry: MappedRegistry<*>): Boolean {
        return ReflectUtil.getT<Boolean>(registry, "frozen").getOrNull() ?: true
    }

    private fun setRegistryFrozen(registry: MappedRegistry<*>, frozen: Boolean) {
        ReflectUtil.set(registry, "frozen", frozen)
    }

    fun <T : Any> unregister(registry: MappedRegistry<T>, key: ResourceKey<T>): T? {
        val holder = registry.get(key).orElse(null) ?: return null
        val value = holder.value()
        val savedFrozen = isRegistryFrozen(registry)

        return try {
            setRegistryFrozen(registry, false)
            removeEntry(registry, key, holder, value)
            value
        } finally {
            if (savedFrozen) {
                setRegistryFrozen(registry, true)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> removeEntry(
        registry: MappedRegistry<T>,
        key: ResourceKey<T>,
        holder: Holder.Reference<T>,
        value: T
    ) {
        val byKey = ReflectUtil.get(registry, "byKey").getOrNull() as? MutableMap<ResourceKey<T>, Holder.Reference<T>> ?: return
        val byLocation = ReflectUtil.get(registry, "byLocation").getOrNull() as? MutableMap<Any?, Holder.Reference<T>> ?: return
        val byValue = ReflectUtil.get(registry, "byValue").getOrNull() as? MutableMap<T, Holder.Reference<T>> ?: return
        val byId = ReflectUtil.get(registry, "byId").getOrNull() as? MutableList<Holder.Reference<T>> ?: return
        val toId = ReflectUtil.get(registry, "toId").getOrNull() as? Reference2IntMap<T> ?: return
        val registrationInfos = ReflectUtil.get(registry, "registrationInfos").getOrNull() as? MutableMap<ResourceKey<T>, Any?> ?: return

        val removedIndex = toId.removeInt(value)
        byKey.remove(key)
        byLocation.remove(key.identifier())
        byValue.remove(value)
        registrationInfos.remove(key)

        if (removedIndex in 0 until byId.size && byId[removedIndex] === holder) {
            byId.removeAt(removedIndex)
        } else {
            byId.remove(holder)
        }

        toId.clear()
        byId.forEachIndexed { index, reference ->
            toId.put(reference.value(), index)
        }

        ReflectUtil.set(registry, "registryLifecycle", recalculateLifecycle(registrationInfos.values))
        ReflectUtil.set(holder, "tags", emptySet<Any>())
    }

    private fun recalculateLifecycle(registrationInfos: Collection<Any?>): Lifecycle {
        var lifecycle = Lifecycle.stable()
        for (registrationInfo in registrationInfos) {
            val entryLifecycle = ReflectUtil.invoke(registrationInfo!!, "lifecycle").getOrNull() as? Lifecycle ?: continue
            lifecycle = lifecycle.add(entryLifecycle)
        }
        return lifecycle
    }
}