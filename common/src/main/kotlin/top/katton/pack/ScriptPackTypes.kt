package top.katton.pack

import java.nio.file.Path

enum class ScriptPackKind {
    DIRECTORY,
    JAR
}

data class ScriptPackScriptFile(
    val relativePath: String,
    val absolutePath: Path,
    val bytes: ByteArray
)

data class ScriptPackContentFile(
    val relativePath: String,
    val absolutePath: Path,
    val bytes: ByteArray
)

data class ScriptPack(
    /**
     * Identifier used for synchronization,
     * composed of the script pack's scope name and its id configured in manifest file.
     */
    val syncId: String,
    val scope: ScriptPackScope,
    val kind: ScriptPackKind,
    val location: Path,
    val manifestJson: String,
    val manifest: ScriptPackManifest,
    val enabled: Boolean,
    val hash: String,
    val scripts: List<ScriptPackScriptFile>,
    val contentFiles: List<ScriptPackContentFile>,
    val compiledJar: Path?
)

data class ScriptPackView(
    val syncId: String,
    val scope: ScriptPackScope,
    val kind: ScriptPackKind,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val authors: List<String>,
    val hash: String,
    val enabled: Boolean,
    val locked: Boolean,
    val sourcePath: String
)
