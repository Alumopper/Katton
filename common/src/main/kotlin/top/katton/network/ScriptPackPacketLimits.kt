package top.katton.network

import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.network.FriendlyByteBuf
import top.katton.pack.ScriptPackFileLimits

/**
 * Hard limits for script-pack payloads.
 *
 * A VarInt collection size must be checked before constructing an ArrayList:
 * a malicious peer can encode `Int.MAX_VALUE` in only five bytes and otherwise
 * force an allocation long before Minecraft's payload-size limit is reached.
 */
internal object ScriptPackPacketLimits {
    private val CONTENT_HASH_PATTERN = Regex("[0-9a-f]{64}")
    const val MAX_PACKS = 1_024
    const val MAX_FILES_PER_PACK = ScriptPackFileLimits.MAX_FILES_PER_PACK
    const val MAX_FILES_PER_BUNDLE = 16_384
    const val MAX_SYNC_ID_CHARS = 512
    const val MAX_SCOPE_CHARS = 32
    const val MAX_HASH_CHARS = 128
    const val MAX_PACK_NAME_CHARS = 512
    const val MAX_RELATIVE_PATH_CHARS = ScriptPackFileLimits.MAX_RELATIVE_PATH_CHARS
    const val MAX_MANIFEST_BYTES = ScriptPackFileLimits.MAX_MANIFEST_BYTES
    const val MAX_FILE_BYTES = ScriptPackFileLimits.MAX_FILE_BYTES
    const val MAX_BUNDLE_CONTENT_BYTES = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES

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

    fun requireByteLength(length: Int, maximum: Int, label: String) {
        if (length !in 0..maximum) {
            throw EncoderException("Invalid $label length $length (maximum $maximum)")
        }
    }

    fun requireUnique(value: String, seen: MutableSet<String>, label: String) {
        if (!seen.add(value)) {
            throw DecoderException("Duplicate $label '$value'")
        }
    }

    fun requireUniqueForEncoding(value: String, seen: MutableSet<String>, label: String) {
        if (!seen.add(value)) {
            throw EncoderException("Duplicate $label '$value'")
        }
    }

    fun requireSafeRelativePathForDecoding(path: String) {
        if (!ScriptPackFileLimits.isPortableRelativePath(path)) {
            throw DecoderException("Unsafe or non-portable script pack path '$path'")
        }
    }

    fun requireSafeRelativePathForEncoding(path: String) {
        if (!ScriptPackFileLimits.isPortableRelativePath(path)) {
            throw EncoderException("Unsafe or non-portable script pack path '$path'")
        }
    }

    fun requireScopeForDecoding(scope: String) {
        if (scope != "global" && scope != "world") {
            throw DecoderException("Invalid synchronized script pack scope '$scope'")
        }
    }

    fun requireScopeForEncoding(scope: String) {
        if (scope != "global" && scope != "world") {
            throw EncoderException("Invalid synchronized script pack scope '$scope'")
        }
    }

    fun requireSyncIdMatchesScopeForDecoding(syncId: String, scope: String) {
        if (!syncId.startsWith("$scope:") || syncId.length == scope.length + 1) {
            throw DecoderException("Script pack id '$syncId' does not match scope '$scope'")
        }
    }

    fun requireSyncIdMatchesScopeForEncoding(syncId: String, scope: String) {
        if (!syncId.startsWith("$scope:") || syncId.length == scope.length + 1) {
            throw EncoderException("Script pack id '$syncId' does not match scope '$scope'")
        }
    }

    fun requireContentHashForDecoding(hash: String) {
        if (!CONTENT_HASH_PATTERN.matches(hash)) {
            throw DecoderException("Invalid script pack SHA-256 hash")
        }
    }

    fun requireContentHashForEncoding(hash: String) {
        if (!CONTENT_HASH_PATTERN.matches(hash)) {
            throw EncoderException("Invalid script pack SHA-256 hash")
        }
    }

    /** Shared byte/file budget across all packs in one decoded bundle. */
    class BundleDecodeBudget {
        private var remainingBytes = MAX_BUNDLE_CONTENT_BYTES
        private var remainingFiles = MAX_FILES_PER_BUNDLE

        fun readFileCount(buf: FriendlyByteBuf): Int {
            val perPack = readCount(buf, MAX_FILES_PER_PACK, "files in pack")
            if (perPack > remainingFiles) {
                throw DecoderException(
                    "Script-pack bundle contains too many files " +
                        "(${MAX_FILES_PER_BUNDLE - remainingFiles + perPack}, maximum $MAX_FILES_PER_BUNDLE)"
                )
            }
            remainingFiles -= perPack
            return perPack
        }

        fun readBytes(buf: FriendlyByteBuf, perEntryMaximum: Int): ByteArray {
            val bytes = buf.readByteArray(minOf(perEntryMaximum, remainingBytes))
            remainingBytes -= bytes.size
            return bytes
        }
    }
}
