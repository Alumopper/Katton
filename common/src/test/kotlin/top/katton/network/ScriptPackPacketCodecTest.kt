package top.katton.network

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.network.FriendlyByteBuf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ScriptPackPacketCodecTest {
    private val validHash = "0".repeat(64)

    @Test
    fun `collection counts are rejected before allocating`() {
        val bundle = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(Int.MAX_VALUE)
        }
        val hashes = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(Int.MAX_VALUE)
        }
        val request = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(Int.MAX_VALUE)
        }

        assertFailsWith<DecoderException> { ScriptPackBundlePacket.read(bundle) }
        assertFailsWith<DecoderException> { ScriptPackHashListPacket.read(hashes) }
        assertFailsWith<DecoderException> { ScriptPackRequestPacket.read(request) }
    }

    @Test
    fun `manifest byte arrays are bounded before copying`() {
        val bundle = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(1)
            writeUtf("world:example")
            writeUtf("world")
            writeUtf(validHash)
            // FriendlyByteBuf.readByteArray(max) validates this declared length
            // before allocating or attempting to copy the missing bytes.
            writeVarInt(ScriptPackPacketLimits.MAX_MANIFEST_BYTES + 1)
        }

        assertFailsWith<DecoderException> { ScriptPackBundlePacket.read(bundle) }
    }

    @Test
    fun `duplicate outbound identifiers are rejected before writing`() {
        val packet = ScriptPackRequestPacket(listOf("world:example", "world:example"))

        assertFailsWith<EncoderException> { packet.write(FriendlyByteBuf(Unpooled.buffer())) }
    }

    @Test
    fun `bundle rejects traversal paths before reading file content`() {
        val bundle = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(1)
            writeUtf("world:example")
            writeUtf("world")
            writeUtf(validHash)
            writeByteArray("{\"dependencies\":[]}".toByteArray())
            writeVarInt(1)
            writeUtf("../outside.kt")
            writeByteArray(byteArrayOf())
        }

        assertFailsWith<DecoderException> { ScriptPackBundlePacket.read(bundle) }
    }

    @Test
    fun `bundle rejects paths that collide on case insensitive clients`() {
        val pack = ScriptPackBundlePacket.PackData(
            syncId = "world:example",
            scope = "world",
            hash = validHash,
            manifestJson = "{\"dependencies\":[]}",
            files = listOf(
                ScriptPackBundlePacket.ScriptFileData("A.kt", byteArrayOf()),
                ScriptPackBundlePacket.ScriptFileData("a.kt", byteArrayOf())
            )
        )

        assertFailsWith<EncoderException> {
            ScriptPackBundlePacket(listOf(pack)).write(FriendlyByteBuf(Unpooled.buffer()))
        }
    }

    @Test
    fun `bundle rejects a sync id whose prefix disagrees with scope`() {
        val bundle = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(1)
            writeUtf("global:example")
            writeUtf("world")
            writeUtf(validHash)
            writeByteArray("{\"dependencies\":[]}".toByteArray())
            writeVarInt(0)
        }

        assertFailsWith<DecoderException> { ScriptPackBundlePacket.read(bundle) }
    }

    @Test
    fun `pack packets reject non sha256 hashes at the codec boundary`() {
        val bundle = buffer {
            writeInt(0x4b500003)
            writeVarLong(0L)
            writeVarInt(1)
            writeUtf("world:example")
            writeUtf("world")
            writeUtf("not-a-sha256")
        }

        assertFailsWith<DecoderException> { ScriptPackBundlePacket.read(bundle) }
        assertFailsWith<EncoderException> {
            ScriptPackHashListPacket(
                listOf(ScriptPackHashListPacket.HashEntry("world:example", "world", "bad", "Example"))
            ).write(FriendlyByteBuf(Unpooled.buffer()))
        }
    }

    @Test
    fun `client data rejects hostile collection sizes and duplicate keys`() {
        val hostileCount = buffer { writeVarInt(Int.MAX_VALUE) }
        val duplicateKeys = buffer {
            writeVarInt(2)
            writeUtf("same")
            writeByte(0)
            writeUtf("same")
            writeByte(0)
        }

        assertFailsWith<DecoderException> { ClientDataSyncPacket.read(hostileCount) }
        assertFailsWith<DecoderException> { ClientDataSyncPacket.read(duplicateKeys) }
    }

    private fun buffer(write: FriendlyByteBuf.() -> Unit): FriendlyByteBuf =
        FriendlyByteBuf(Unpooled.buffer()).apply(write)
}
