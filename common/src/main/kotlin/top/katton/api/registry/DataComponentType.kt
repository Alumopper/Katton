@file:Suppress("unused")

package top.katton.api.registry

import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a persistent (saved to disk) DataComponentType with hot-reload support.
 *
 * Persistent components are serialized using their codec and saved with the item.
 * Use this for data that must survive across save/load cycles.
 *
 * @param id Component identifier (e.g., "mymod:custom_data")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param codec The codec for serializing/deserializing the component value
 * @return The registered KattonDataComponentTypeEntry
 */
fun <T : Any> registerNativePersistentDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry = registerNativePersistentDataComponentType(id(id), registerMode, codec)

/**
 * Registers a persistent (saved to disk) DataComponentType with hot-reload support.
 *
 * @param id Component identifier
 * @param registerMode Registration mode
 * @param codec The codec for serializing/deserializing the component value
 * @return The registered KattonDataComponentTypeEntry
 */
fun <T : Any> registerNativePersistentDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry {
    return KattonRegistry.DATA_COMPONENT_TYPES.newNative(id, registerMode) {
        DataComponentType.builder<T>().persistent(codec).build()
    }
}

/**
 * Registers a network-synchronized DataComponentType with hot-reload support.
 *
 * Network-synchronized components are sent to the client but NOT saved to disk.
 * Use this for data that is computed at runtime (e.g., render-only state).
 *
 * @param id Component identifier (e.g., "mymod:sync_data")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param streamCodec The stream codec for network synchronization
 * @return The registered KattonDataComponentTypeEntry
 */
fun <T : Any> registerNativeNetworkDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry = registerNativeNetworkDataComponentType(id(id), registerMode, streamCodec)

/**
 * Registers a network-synchronized DataComponentType with hot-reload support.
 *
 * @param id Component identifier
 * @param registerMode Registration mode
 * @param streamCodec The stream codec for network synchronization
 * @return The registered KattonDataComponentTypeEntry
 */
fun <T : Any> registerNativeNetworkDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry {
    return KattonRegistry.DATA_COMPONENT_TYPES.newNative(id, registerMode) {
        @Suppress("UNCHECKED_CAST")
        DataComponentType.builder<T>()
            .networkSynchronized(streamCodec as StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T>)
            .build()
    }
}
