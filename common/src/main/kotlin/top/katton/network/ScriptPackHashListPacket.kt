package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ScriptPackHashListPacket(
    val entries: List<HashEntry>,
    val revision: Long = 0L
) : CustomPacketPayload {

    data class HashEntry(
        val syncId: String,
        val scope: String,
        val hash: String,
        val name: String
    ) {
        fun write(buf: FriendlyByteBuf) {
            ScriptPackPacketLimits.requireScopeForEncoding(scope)
            ScriptPackPacketLimits.requireSyncIdMatchesScopeForEncoding(syncId, scope)
            ScriptPackPacketLimits.requireContentHashForEncoding(hash)
            buf.writeUtf(syncId, ScriptPackPacketLimits.MAX_SYNC_ID_CHARS)
            buf.writeUtf(scope, ScriptPackPacketLimits.MAX_SCOPE_CHARS)
            buf.writeUtf(hash, ScriptPackPacketLimits.MAX_HASH_CHARS)
            buf.writeUtf(name, ScriptPackPacketLimits.MAX_PACK_NAME_CHARS)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): HashEntry {
                val syncId = buf.readUtf(ScriptPackPacketLimits.MAX_SYNC_ID_CHARS)
                val scope = buf.readUtf(ScriptPackPacketLimits.MAX_SCOPE_CHARS)
                ScriptPackPacketLimits.requireScopeForDecoding(scope)
                ScriptPackPacketLimits.requireSyncIdMatchesScopeForDecoding(syncId, scope)
                val hash = buf.readUtf(ScriptPackPacketLimits.MAX_HASH_CHARS)
                ScriptPackPacketLimits.requireContentHashForDecoding(hash)
                return HashEntry(
                    syncId = syncId,
                    scope = scope,
                    hash = hash,
                    name = buf.readUtf(ScriptPackPacketLimits.MAX_PACK_NAME_CHARS)
                )
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        ScriptPackPacketLimits.requireCount(entries.size, ScriptPackPacketLimits.MAX_PACKS, "script pack hashes")
        val syncIds = HashSet<String>(entries.size)
        entries.forEach { entry ->
            ScriptPackPacketLimits.requireUniqueForEncoding(entry.syncId, syncIds, "script pack id")
        }
        buf.writeInt(0x4b500003)
        buf.writeVarLong(revision)
        buf.writeVarInt(entries.size)
        entries.forEach { it.write(buf) }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackHashListPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_hashes"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackHashListPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): ScriptPackHashListPacket {
            require(buf.readInt() == 0x4b500003) { "Incompatible Katton pack protocol; update client/server and rebuild the v3 cache" }
            val revision = buf.readVarLong()
            val count = ScriptPackPacketLimits.readCount(buf, ScriptPackPacketLimits.MAX_PACKS, "script pack hashes")
            val entries = ArrayList<HashEntry>(count)
            val syncIds = HashSet<String>(count)
            repeat(count) {
                val entry = HashEntry.read(buf)
                ScriptPackPacketLimits.requireUnique(entry.syncId, syncIds, "script pack id")
                entries.add(entry)
            }
            return ScriptPackHashListPacket(entries, revision)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
