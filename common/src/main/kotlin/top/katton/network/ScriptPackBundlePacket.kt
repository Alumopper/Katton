package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton
import top.katton.pack.ScriptPackFileLimits
import java.nio.charset.StandardCharsets

data class ScriptPackBundlePacket(
    val packs: List<PackData>,
    val revision: Long = 0L
) : CustomPacketPayload {

    data class ScriptFileData(
        val relativePath: String,
        val content: ByteArray
    ) {
        fun write(buf: FriendlyByteBuf) {
            ScriptPackPacketLimits.requireByteLength(content.size, ScriptPackPacketLimits.MAX_FILE_BYTES, "script file")
            ScriptPackPacketLimits.requireSafeRelativePathForEncoding(relativePath)
            buf.writeUtf(relativePath, ScriptPackPacketLimits.MAX_RELATIVE_PATH_CHARS)
            buf.writeByteArray(content)
        }

        companion object {
            /** Decodes one standalone file while retaining the original public API. */
            fun read(buf: FriendlyByteBuf): ScriptFileData =
                read(buf, ScriptPackPacketLimits.BundleDecodeBudget())

            internal fun read(buf: FriendlyByteBuf, budget: ScriptPackPacketLimits.BundleDecodeBudget): ScriptFileData {
                val relativePath = buf.readUtf(ScriptPackPacketLimits.MAX_RELATIVE_PATH_CHARS)
                ScriptPackPacketLimits.requireSafeRelativePathForDecoding(relativePath)
                return ScriptFileData(
                    relativePath = relativePath,
                    content = budget.readBytes(buf, ScriptPackPacketLimits.MAX_FILE_BYTES)
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
            val manifestBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
            ScriptPackPacketLimits.requireCount(files.size, ScriptPackPacketLimits.MAX_FILES_PER_PACK, "files in pack")
            ScriptPackPacketLimits.requireByteLength(
                manifestBytes.size,
                ScriptPackPacketLimits.MAX_MANIFEST_BYTES,
                "manifest"
            )
            ScriptPackPacketLimits.requireScopeForEncoding(scope)
            ScriptPackPacketLimits.requireSyncIdMatchesScopeForEncoding(syncId, scope)
            ScriptPackPacketLimits.requireContentHashForEncoding(hash)
            buf.writeUtf(syncId, ScriptPackPacketLimits.MAX_SYNC_ID_CHARS)
            buf.writeUtf(scope, ScriptPackPacketLimits.MAX_SCOPE_CHARS)
            buf.writeUtf(hash, ScriptPackPacketLimits.MAX_HASH_CHARS)
            buf.writeByteArray(manifestBytes)
            buf.writeVarInt(files.size)
            files.forEach { it.write(buf) }
        }

        companion object {
            /** Decodes one standalone pack while retaining the original public API. */
            fun read(buf: FriendlyByteBuf): PackData =
                read(buf, ScriptPackPacketLimits.BundleDecodeBudget())

            internal fun read(buf: FriendlyByteBuf, budget: ScriptPackPacketLimits.BundleDecodeBudget): PackData {
                val syncId = buf.readUtf(ScriptPackPacketLimits.MAX_SYNC_ID_CHARS)
                val scope = buf.readUtf(ScriptPackPacketLimits.MAX_SCOPE_CHARS)
                ScriptPackPacketLimits.requireScopeForDecoding(scope)
                ScriptPackPacketLimits.requireSyncIdMatchesScopeForDecoding(syncId, scope)
                val hash = buf.readUtf(ScriptPackPacketLimits.MAX_HASH_CHARS)
                ScriptPackPacketLimits.requireContentHashForDecoding(hash)
                val manifestJson = String(
                    budget.readBytes(buf, ScriptPackPacketLimits.MAX_MANIFEST_BYTES),
                    StandardCharsets.UTF_8
                )
                val count = budget.readFileCount(buf)
                val files = ArrayList<ScriptFileData>(count)
                val relativePaths = HashSet<String>(count)
                repeat(count) {
                    val file = ScriptFileData.read(buf, budget)
                    ScriptPackPacketLimits.requireUnique(
                        ScriptPackFileLimits.portablePathKey(file.relativePath),
                        relativePaths,
                        "portable script file path"
                    )
                    files.add(file)
                }
                return PackData(syncId, scope, hash, manifestJson, files)
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        ScriptPackPacketLimits.requireCount(packs.size, ScriptPackPacketLimits.MAX_PACKS, "script packs")
        val syncIds = HashSet<String>(packs.size)
        var totalFiles = 0L
        var totalBytes = 0L
        packs.forEach { pack ->
            ScriptPackPacketLimits.requireUniqueForEncoding(pack.syncId, syncIds, "script pack id")
            totalFiles += pack.files.size
            totalBytes += pack.manifestJson.toByteArray(StandardCharsets.UTF_8).size
            val relativePaths = HashSet<String>(pack.files.size)
            pack.files.forEach { file ->
                ScriptPackPacketLimits.requireUniqueForEncoding(
                    ScriptPackFileLimits.portablePathKey(file.relativePath),
                    relativePaths,
                    "portable script file path"
                )
                totalBytes += file.content.size
            }
        }
        ScriptPackPacketLimits.requireCount(
            totalFiles.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ScriptPackPacketLimits.MAX_FILES_PER_BUNDLE,
            "files in script-pack bundle"
        )
        ScriptPackPacketLimits.requireByteLength(
            totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ScriptPackPacketLimits.MAX_BUNDLE_CONTENT_BYTES,
            "script-pack bundle content"
        )
        buf.writeInt(0x4b500003)
        buf.writeVarLong(revision)
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
            require(buf.readInt() == 0x4b500003) { "Incompatible Katton pack protocol; update client/server and rebuild the v3 cache" }
            val revision = buf.readVarLong()
            val count = ScriptPackPacketLimits.readCount(buf, ScriptPackPacketLimits.MAX_PACKS, "script packs")
            val packs = ArrayList<PackData>(count)
            val syncIds = HashSet<String>(count)
            val budget = ScriptPackPacketLimits.BundleDecodeBudget()
            repeat(count) {
                val pack = PackData.read(buf, budget)
                ScriptPackPacketLimits.requireUnique(pack.syncId, syncIds, "script pack id")
                packs.add(pack)
            }
            return ScriptPackBundlePacket(packs, revision)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
