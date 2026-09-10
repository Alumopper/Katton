package top.katton.pack

import java.util.jar.JarInputStream

/** Reads the immutable accepted snapshot; never reopens a mutable pack directory/JAR. */
internal object ScriptPackAudioFiles {
    fun read(pack: ScriptPack, path: String): ByteArray {
        require(ScriptPackFileLimits.isPortableRelativePath(path)) { "Invalid pack audio path" }
        if (pack.kind != ScriptPackKind.JAR) return pack.contentFiles.firstOrNull { it.relativePath == path }?.bytes
            ?: error("Missing pack audio: $path")
        JarInputStream(pack.contentFiles.single().bytes.inputStream()).use { input ->
            var entry = input.nextJarEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == path)
                    return SafePackFileIo.readBytes(input, ScriptPackFileLimits.MAX_FILE_BYTES, path)
                entry = input.nextJarEntry
            }
        }
        error("Missing JAR audio: $path")
    }
}
