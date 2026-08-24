package top.katton.network

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.network.FriendlyByteBuf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ClientDataLimitsTest {
    @AfterTest
    fun clearClientData() {
        ClientDataManager.clear()
    }

    @Test
    fun `aggregate packet string budget is enforced while encoding and decoding`() {
        val value = "x".repeat(ClientPacketLimits.MAX_DATA_STRING_CHARS)
        val count = ClientPacketLimits.MAX_TOTAL_DATA_STRING_CHARS / value.length + 1
        val entries = List(count) { index -> ClientDataSyncPacket.DataEntry("key-$index", value) }

        assertFailsWith<EncoderException> {
            ClientDataSyncPacket.write(FriendlyByteBuf(Unpooled.buffer()), ClientDataSyncPacket(entries))
        }

        val encodedWithoutAggregateGuard = FriendlyByteBuf(Unpooled.buffer()).apply {
            writeVarInt(entries.size)
            entries.forEach { ClientDataSyncPacket.DataEntry.write(this, it) }
        }
        assertFailsWith<DecoderException> { ClientDataSyncPacket.read(encodedWithoutAggregateGuard) }
    }

    @Test
    fun `client data state cannot accumulate beyond the packet entry limit`() {
        repeat(ClientPacketLimits.MAX_DATA_ENTRIES) { index -> ClientDataManager.put("key-$index", index) }
        ClientDataManager.put("overflow", 1)

        assertEquals(ClientPacketLimits.MAX_DATA_ENTRIES, ClientDataManager.getAll().size)
        assertNull(ClientDataManager.get("overflow"))

        ClientDataManager.remove("key-0")
        ClientDataManager.put("replacement", 1)
        assertEquals(1, ClientDataManager.get("replacement"))
    }

    @Test
    fun `direct client data writes obey wire string limits`() {
        val oversizedKey = "k".repeat(ClientPacketLimits.MAX_DATA_KEY_CHARS + 1)
        val oversizedValue = "v".repeat(ClientPacketLimits.MAX_DATA_STRING_CHARS + 1)

        ClientDataManager.put(oversizedKey, "value")
        ClientDataManager.put("oversized-value", oversizedValue)

        assertNull(ClientDataManager.get(oversizedKey))
        assertNull(ClientDataManager.get("oversized-value"))
    }
}
