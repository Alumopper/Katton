package top.katton.pack

import java.nio.file.Path

data class ScriptPackScriptFile(
    val relativePath: String,
    val absolutePath: Path,
    val bytes: ByteArray
)

data class ScriptPack(
    val syncId: String,
    val scope: ScriptPackScope,
    val directory: Path,
    val manifestJson: String,
    val manifest: ScriptPackManifest,
    val enabled: Boolean,
    val hash: String,
    val scripts: List<ScriptPackScriptFile>
)

data class ScriptPackView(
    val syncId: String,
    val scope: ScriptPackScope,
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
