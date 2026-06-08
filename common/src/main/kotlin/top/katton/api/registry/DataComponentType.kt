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
 * %en
 * Registers a persistent (saved to disk) DataComponentType with hot-reload support.
 *
 * Persistent components are serialized using their codec and saved with the item.
 * Use this for data that must survive across save/load cycles.
 *
 * %zh
 * 注册持久化（保存到磁盘）的 DataComponentType，并支持热重载。
 * 持久化组件会使用其 codec 进行序列化，并随物品一并保存。
 * 适用于必须跨存档读写周期保留的数据。
 * @param id
 * %en Component identifier (e.g., "mymod:custom_data")
 * %zh 组件标识符，例如 "mymod:custom_data"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param codec
 * %en The codec for serializing/deserializing the component value
 * %zh 用于序列化和反序列化组件值的 codec。
 * @return
 * %en registered KattonDataComponentTypeEntry
 * %zh 已注册的 KattonDataComponentTypeEntry。
 */
fun <T : Any> registerNativePersistentDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry = registerNativePersistentDataComponentType(id(id), registerMode, codec)

/**
 * %en
 * Registers a persistent (saved to disk) DataComponentType with hot-reload support.
 *
 * %zh
 * 注册持久化（保存到磁盘）的 DataComponentType，并支持热重载。
 * @param id
 * %en Component identifier
 * %zh 组件标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param codec
 * %en The codec for serializing/deserializing the component value
 * %zh 用于序列化和反序列化组件值的 codec。
 * @return
 * %en registered KattonDataComponentTypeEntry
 * %zh 已注册的 KattonDataComponentTypeEntry。
 */
fun <T : Any> registerNativePersistentDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry {
    return KattonRegistry.DATA_COMPONENT_TYPES.newNative(id, registerMode) {
        DataComponentType.builder<T>().persistent(codec).build()
    }
}

/**
 * %en
 * Registers a network-synchronized DataComponentType with hot-reload support.
 *
 * Network-synchronized components are sent to the client but NOT saved to disk.
 * Use this for data that is computed at runtime (e.g., render-only state).
 *
 * %zh
 * 注册网络同步的 DataComponentType，并支持热重载。
 * 这种组件会发送到客户端，但不会保存到磁盘。
 * 适用于运行时计算的数据，例如仅供渲染使用的状态。
 * @param id
 * %en Component identifier (e.g., "mymod:sync_data")
 * %zh 组件标识符，例如 "mymod:sync_data"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param streamCodec
 * %en The stream codec for network synchronization
 * %zh 用于网络同步的 stream codec。
 * @return
 * %en registered KattonDataComponentTypeEntry
 * %zh 已注册的 KattonDataComponentTypeEntry。
 */
fun <T : Any> registerNativeNetworkDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry = registerNativeNetworkDataComponentType(id(id), registerMode, streamCodec)

/**
 * %en
 * Registers a network-synchronized DataComponentType with hot-reload support.
 *
 * %zh
 * 注册网络同步的 DataComponentType，并支持热重载。
 * @param id
 * %en Component identifier
 * %zh 组件标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param streamCodec
 * %en The stream codec for network synchronization
 * %zh 用于网络同步的 stream codec。
 * @return
 * %en registered KattonDataComponentTypeEntry
 * %zh 已注册的 KattonDataComponentTypeEntry。
 */
fun <T : Any> registerNativeNetworkDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry {
    return KattonRegistry.DATA_COMPONENT_TYPES.newNative(id, registerMode) {
        @Suppress("UNCHECKED_CAST")
        DataComponentType.builder<T>()
            .networkSynchronized(streamCodec as StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T>)
            .build()
    }
}
