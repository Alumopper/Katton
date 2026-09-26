package top.katton.network

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier

class ScriptPayloadPacketTest {
    private val channel = Identifier.parse("anki:save_unlock")

    @Test
    fun `host codec roundtrips script bytes without a script class`() {
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            val data = byteArrayOf(0, 1, -1, 42)
            ScriptPayloadPacket.STREAM_CODEC.encode(buffer, ScriptPayloadPacket(channel, data))
            val decoded = ScriptPayloadPacket.STREAM_CODEC.decode(buffer)
            assertEquals(channel, decoded.channel)
            assertContentEquals(data, decoded.data)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `oversized and truncated payloads are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ScriptPayloadPacket(channel, ByteArray(ScriptPayloadPacket.MAX_DATA_BYTES + 1))
        }
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            buffer.writeUtf(channel.toString())
            buffer.writeVarInt(ScriptPayloadPacket.MAX_DATA_BYTES + 1)
            assertFailsWith<IllegalArgumentException> { ScriptPayloadPacket.STREAM_CODEC.decode(buffer) }
            buffer.clear()
            buffer.writeUtf(channel.toString())
            buffer.writeVarInt(1)
            assertFailsWith<IllegalArgumentException> { ScriptPayloadPacket.STREAM_CODEC.decode(buffer) }
        } finally {
            buffer.release()
        }
    }
}
