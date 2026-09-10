package top.katton.pack

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.ZipInputStream

/** Both transports produce the same bounded logical files, hashes, and signature payload. */
internal class ScriptPackSnapshots(val manifest: String, val files: Map<String, ByteArray>) {
    fun toPack(path: Path, scope: ScriptPackScope, kind: ScriptPackKind,
               syncIdOverride: String? = null, enabledOverride: Boolean? = null): ScriptPack {
        val parsed = ScriptPackManifest.parse(path, manifest, fallbackIdOverride = syncIdOverride?.substringAfter(':'))
        val content = files.map { (name, bytes) -> ScriptPackContentFile(name, path.resolve(name), bytes) }
        val sources = content.filter { isSource(it.relativePath) }
        val kotlin = sources.filter { it.relativePath.endsWith(".kt", true) }.map { ScriptPackScriptFile(it.relativePath, it.absolutePath, it.bytes) }
        val java = sources.filter { it.relativePath.endsWith(".java", true) }.map { ScriptPackScriptFile(it.relativePath, it.absolutePath, it.bytes) }
        val libs = content.filter { isLibrary(it.relativePath) }
        val unsigned = JsonParser.parseString(manifest).asJsonObject.apply { remove("signature") }.toString()
        val codeHash = ScriptPackManager.computeScriptHash(unsigned, kotlin, java, libraries = libs)
        val hash = ScriptPackManager.computeScriptHash(manifest, kotlin, java,
            content.filter { it.relativePath.startsWith("assets/") }, content.filter { it.relativePath.startsWith("data/") }, libs,
            content.filter { !isSource(it.relativePath) && !isLibrary(it.relativePath) &&
                !it.relativePath.startsWith("assets/") && !it.relativePath.startsWith("data/") })
        return ScriptPack(syncIdOverride ?: "${scope.serializedName}:${parsed.id}", scope, kind, path, manifest,
            parsed, enabledOverride ?: parsed.enabledByDefault, hash, codeHash, kotlin, content, null)
    }

    companion object {
        fun isLibrary(name: String) = name.startsWith("libs/") && name.count { it == '/' } == 1 && name.endsWith(".jar", true)
        fun isSource(name: String) = !name.startsWith("assets/") && !name.startsWith("data/") && !name.startsWith("libs/") &&
            (name.endsWith(".kt", true) || name.endsWith(".java", true))
        // Katton's local enabled-state file is not distributable pack content, at any depth or casing.
        fun retained(name: String) = name != "manifest.json" &&
            !name.substringAfterLast('/').equals(".kattonpack.state.json", ignoreCase = true)

        fun directory(root: Path, manifest: String, limit: Int): ScriptPackSnapshots {
            val files = sortedMapOf<String, ByteArray>()
            val keys = hashSetOf<String>()
            var remaining = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES - manifest.toByteArray(Charsets.UTF_8).size
            var count = 0
            Files.walk(root).use { paths -> paths.forEach { path ->
                if (path == root) return@forEach
                require(++count <= limit) { "Too many directory entries" }
                require(!Files.isSymbolicLink(path)) { "Symbolic links are forbidden: $path" }
                val name = root.relativize(path).toString().replace('\\', '/')
                validate(name, keys)
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || name == "manifest.json") return@forEach
                // Excluded files are not pack content, so they must not consume the content budget.
                if (!retained(name)) return@forEach
                require(path.toRealPath().startsWith(root.toRealPath())) { "Path escapes pack: $name" }
                require(files.size < ScriptPackFileLimits.MAX_FILES_PER_PACK) { "Too many pack files" }
                val bytes = SafePackFileIo.readBytes(path, minOf(remaining, ScriptPackFileLimits.MAX_FILE_BYTES), name)
                remaining -= bytes.size
                files[name] = bytes
            } }
            return ScriptPackSnapshots(manifest, files)
        }

        fun zip(path: Path): ScriptPackSnapshots {
            val compressed = SafePackFileIo.readBytes(path, ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES, "ZIP pack")
            rejectZipLinks(compressed)
            val files = sortedMapOf<String, ByteArray>()
            val keys = hashSetOf<String>()
            val regularFiles = hashSetOf<String>()
            var manifest: String? = null
            var count = 0
            var remaining = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES
            ZipInputStream(compressed.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES) { "Too many ZIP entries" }
                    val name = entry.name.removeSuffix("/")
                    validate(name, keys)
                    val portable = ScriptPackFileLimits.portablePathKey(name)
                    require(regularFiles.none { portable.startsWith("$it/") } &&
                        (entry.isDirectory || keys.none { it.startsWith("$portable/") })) { "ZIP file/directory path conflict: $name" }
                    if (!entry.isDirectory) regularFiles += portable
                    val bytes = SafePackFileIo.readBytes(zip, minOf(remaining,
                        if (name == "manifest.json") ScriptPackFileLimits.MAX_MANIFEST_BYTES else ScriptPackFileLimits.MAX_FILE_BYTES), name)
                    // The directory scanner charges the manifest against the same content
                    // budget; mirror that here so both transports accept the same packs.
                    remaining -= if (name == "manifest.json") 2 * bytes.size else bytes.size
                    if (!entry.isDirectory) {
                        if (name == "manifest.json") manifest = bytes.toString(Charsets.UTF_8)
                        else if (retained(name)) {
                            require(files.size < ScriptPackFileLimits.MAX_FILES_PER_PACK) { "Too many pack files" }
                            files[name] = bytes
                        }
                    }
                }
            }
            return ScriptPackSnapshots(requireNotNull(manifest) { "ZIP root must contain manifest.json" }, files)
        }

        private fun validate(name: String, keys: MutableSet<String>) {
            require(name.length <= ScriptPackFileLimits.MAX_RELATIVE_PATH_CHARS &&
                (name == "manifest.json" || ScriptPackFileLimits.isPortableRelativePath(name))) { "Unsafe pack path: $name" }
            require(keys.add(ScriptPackFileLimits.portablePathKey(name))) { "Duplicate pack path: $name" }
        }

        /** Unix symlink modes live in the central directory, unavailable through ZipEntry. */
        private fun rejectZipLinks(bytes: ByteArray) {
            fun u16(i: Int) = (bytes[i].toInt() and 255) or ((bytes[i + 1].toInt() and 255) shl 8)
            fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
            val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65557)).firstOrNull { i ->
                u32(i) == 0x06054b50L && i + 22 + u16(i + 20) == bytes.size
            } ?: error("Missing ZIP central directory")
            require(u16(end + 4) == 0 && u16(end + 6) == 0) { "Split ZIP archives are unsupported" }
            var offset = u32(end + 16).toInt()
            repeat(u16(end + 10)) {
                require(offset >= 0 && offset + 46 <= end && u32(offset) == 0x02014b50L) { "Invalid ZIP central directory" }
                val mode = (u32(offset + 38) ushr 16).toInt()
                require(mode and 0xf000 != 0xa000) { "ZIP symbolic links are forbidden" }
                offset += 46 + u16(offset + 28) + u16(offset + 30) + u16(offset + 32)
            }
            require(offset == end) { "Unsupported or malformed ZIP directory" }
        }
    }
}
