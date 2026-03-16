@file:Suppress("unused")

package top.katton.api.datapack

import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import top.katton.datapack.ServerDatapackManager
import top.katton.datapack.TagMutation

fun itemTags(block: RegistryTagEvent.() -> Unit) {
    RegistryTagEvent(Registries.ITEM).apply(block)
}

fun blockTags(block: RegistryTagEvent.() -> Unit) {
    RegistryTagEvent(Registries.BLOCK).apply(block)
}

fun fluidTags(block: RegistryTagEvent.() -> Unit) {
    RegistryTagEvent(Registries.FLUID).apply(block)
}

fun entityTypeTags(block: RegistryTagEvent.() -> Unit) {
    RegistryTagEvent(Registries.ENTITY_TYPE).apply(block)
}

class RegistryTagEvent(private val registryKey: ResourceKey<out Registry<*>>) {
    fun tag(id: String, block: TagMutation.() -> Unit) {
        ServerDatapackManager.mutateTag(registryKey, Identifier.parse(id), block)
    }
}