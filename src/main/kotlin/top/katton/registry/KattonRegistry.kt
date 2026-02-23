package top.katton.registry

import com.mojang.serialization.Codec
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import top.katton.Katton
import top.katton.Katton.MOD_ID
import top.katton.LoadState
import top.katton.registry.KattonItemProperties.Companion.components

fun id(namespace: String, path: String) = Identifier.fromNamespaceAndPath(namespace, path)
fun id(string: String) = Identifier.parse(string)


interface Identifiable {
    val id: Identifier
}


enum class RegisterMode {
    GLOBAL,
    RELOADABLE,
    AUTO
}


object KattonRegistry {

    abstract class KattonRegistries<T : Identifiable>(
        override val id: Identifier,
        entries: MutableMap<Identifier, T> = mutableMapOf()
    ) : Identifiable, MutableMap<Identifier, T> by entries {
        abstract val default: T

        @Deprecated("Use Function In KattonRegistry Instead")
        internal fun register(implement: T): T {
            this[implement.id] = implement
            return implement
        }

        fun getOrDefault(id: Identifier) = this[id] ?: default

        fun initialize() {}
    }


    data class KattonItemEntry(
        override val id: Identifier,
        val agentItem: KattonItemInterface,
        val actualItem: KattonItem
    ) : Identifiable {
        fun getDefaultInstance() = actualItem.defaultInstance.apply {
            applyComponents(agentItem.components())
        }
    }

    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {
        override val default = new(components(id), RegisterMode.GLOBAL)

        private fun registerGlobalItem(id: Identifier, item: KattonItem) =
            Registry.register(BuiltInRegistries.ITEM, id, item)

        private fun registerActualItem(
            properties: KattonItemProperties,
            itemBuilder: (properties: KattonItemProperties) -> KattonItem,
            registerMode: RegisterMode = RegisterMode.AUTO
        ) = when (registerMode) {
            RegisterMode.GLOBAL -> {
                registerGlobalItem(properties.id, itemBuilder(properties))
            }

            RegisterMode.RELOADABLE -> {
                default.actualItem
            }

            RegisterMode.AUTO -> {
                Katton.globalState.let {
                    if (it.after(LoadState.INIT)) {
                        default.actualItem
                    } else {
                        registerGlobalItem(properties.id, itemBuilder(properties))
                    }
                }
            }
        }

        fun new(
            components: KattonItemProperties,
            registerMode: RegisterMode = RegisterMode.AUTO,
            itemFactory: (KattonItemProperties) -> KattonItemInterface = { KattonItemWrapper(it,Items.AIR) }
        ): KattonItemEntry {
            return register(
                KattonItemEntry(
                    components.id,
                    itemFactory(components),
                    registerActualItem(components, ::KattonItem, registerMode)
                )
            )
        }
    }


    object COMPONENTS {
        val KATTON_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, "id"),
            DataComponentType.builder<String>().persistent(Codec.STRING).build()
        )
    }


}