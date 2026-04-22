package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton
import java.nio.charset.StandardCharsets

data class ScriptPackBundlePacket(
    val packs: List<PackData>
) : CustomPacketPayload {

    data class ScriptFileData(
        val relativePath: String,
        val content: ByteArray
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(relativePath)
            buf.writeByteArray(content)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): ScriptFileData {
                return ScriptFileData(
                    relativePath = buf.readUtf(),
                    content = buf.readByteArray()
                )
            }
        }
    }

    data class PackData(
        val syncId: String,
        val scope: String,
        val hash: String,
        val manifestJson: String,
        val files: List<ScriptFileData>
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(syncId)
            buf.writeUtf(scope)
            buf.writeUtf(hash)
            buf.writeByteArray(manifestJson.toByteArray(StandardCharsets.UTF_8))
            buf.writeVarInt(files.size)
            files.forEach { it.write(buf) }
        }

        companion object {
            fun read(buf: FriendlyByteBuf): PackData {
                val syncId = buf.readUtf()
                val scope = buf.readUtf()
                val hash = buf.readUtf()
                val manifestJson = String(buf.readByteArray(), StandardCharsets.UTF_8)
                val count = buf.readVarInt()
                val files = ArrayList<ScriptFileData>(count)
                repeat(count) {
                    files.add(ScriptFileData.read(buf))
                }
                return PackData(syncId, scope, hash, manifestJson, files)
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(packs.size)
        packs.forEach { it.write(buf) }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackBundlePacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_bundle"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackBundlePacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): ScriptPackBundlePacket {
            val count = buf.readVarInt()
            val packs = ArrayList<PackData>(count)
            repeat(count) {
                packs.add(PackData.read(buf))
            }
            return ScriptPackBundlePacket(packs)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
