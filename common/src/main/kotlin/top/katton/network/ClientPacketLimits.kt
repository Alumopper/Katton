package top.katton.network

import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.network.FriendlyByteBuf

/** Shared allocation guards for client-facing play-phase packets. */
internal object ClientPacketLimits {
    const val MAX_DATA_ENTRIES = 4_096
    const val MAX_DATA_KEY_CHARS = 256
    const val MAX_DATA_STRING_CHARS = 16_384
    const val MAX_TOTAL_DATA_STRING_CHARS = 1024 * 1024
    const val MAX_RESOURCE_ID_CHARS = 256

    fun readCount(buf: FriendlyByteBuf, maximum: Int, label: String): Int {
        val count = buf.readVarInt()
        if (count !in 0..maximum) {
            throw DecoderException("Invalid $label count $count (maximum $maximum)")
        }
        return count
    }

    fun requireCount(count: Int, maximum: Int, label: String) {
        if (count !in 0..maximum) {
            throw EncoderException("Invalid $label count $count (maximum $maximum)")
        }
    }

    inline fun requireDecoded(condition: Boolean, message: () -> String) {
        if (!condition) throw DecoderException(message())
    }

    inline fun requireEncoding(condition: Boolean, message: () -> String) {
        if (!condition) throw EncoderException(message())
    }

    fun <T> requireUniqueDecoded(value: T, seen: MutableSet<T>, label: String) {
        requireDecoded(seen.add(value)) { "Duplicate $label '$value'" }
    }

    fun <T> requireUniqueEncoding(value: T, seen: MutableSet<T>, label: String) {
        requireEncoding(seen.add(value)) { "Duplicate $label '$value'" }
    }
}
