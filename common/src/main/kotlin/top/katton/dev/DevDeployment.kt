package top.katton.dev

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.engine.ScriptDependencyManager
import top.katton.engine.ScriptEnvironment
import top.katton.network.ScriptPackPacketLimits
import top.katton.pack.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

/** A complete deployment snapshot. No project-supplied absolute paths reach the filesystem. */
internal class DevDeployment(request: JsonObject, private val world: Path,
                             private val existing: List<ScriptPack>, private val game: Path? = null) {
    val workspace = request.string("workspaceId").also { require(it.length <= 128) }
    private val packRoot = world.resolve("kattonpacks").toAbsolutePath().normalize()
    data class Change(val pack: ScriptPack, val snapshot: ScriptPackSnapshots, val previous: ScriptPack?)
    val changes = mutableListOf<Change>()
    val requested = mutableListOf<ScriptPack>()
    val restart = mutableListOf<String>()
    private data class Stage(val root: Path, val ownership: Path, val owners: JsonObject)
    private val stages = linkedMapOf<Path, Stage>()
    private val installed = mutableListOf<Change>()

    init {
        validateRoot(world)
        game?.let(::validateRoot)
        val inputs = request.getAsJsonArray("packs") ?: error("Missing packs")
        require(inputs.size() in 1..128) { "Expected 1–128 packs" }
        var total = 0L
        val ids = hashSetOf<String>()
        inputs.forEach { element ->
            val input = element.asJsonObject
            val scope = when (input.string("scope")) {
                "world" -> ScriptPackScope.WORLD
                "global" -> ScriptPackScope.GLOBAL
                else -> error("Server cache is read-only")
            }
            val manifest = input.get("manifest")?.asString ?: error("Missing manifest")
            require(manifest.toByteArray().size <= ScriptPackFileLimits.MAX_MANIFEST_BYTES) { "Manifest too large" }
            // A deployed folder is named after the digest of this id, so an id-less manifest
            // would be re-discovered under a different identity after the swap.
            val parsed = ScriptPackManifest.parse(packRoot.resolve("pack"), manifest)
            require(parsed.id != "pack") { "Pack manifest must declare a non-blank id" }
            val syncId = "${scope.serializedName}:${parsed.id}"
            require(ids.add(syncId.lowercase())) { "Duplicate pack $syncId" }
            val files = sortedMapOf<String, ByteArray>()
            val names = hashSetOf("manifest.json", ".kattonpack.state.json")
            val data = input.getAsJsonObject("files") ?: error("Missing files")
            require(data.size() <= ScriptPackFileLimits.MAX_FILES_PER_PACK) { "Too many files" }
            var size = manifest.toByteArray().size.toLong()
            data.entrySet().forEach { (name, value) ->
                require(ScriptPackFileLimits.isPortableRelativePath(name) && ScriptPackSnapshots.retained(name)) { "Invalid path: $name" }
                require(names.add(ScriptPackFileLimits.portablePathKey(name))) { "Colliding path: $name" }
                val bytes = Base64.getDecoder().decode(value.asString)
                require(bytes.size <= ScriptPackFileLimits.MAX_FILE_BYTES) { "File too large: $name" }
                size += bytes.size
                require(size <= ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES) { "Pack too large: $syncId" }
                files[name] = bytes
            }
            total += size
            require(total <= ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES) { "Deployment exceeds 64 MiB" }
            val previous = existing.filter { it.syncId.equals(syncId, true) }.also { require(it.size <= 1) { "Ambiguous pack $syncId" } }.singleOrNull()
            val folder = "dev-" + digest(syncId).take(24)
            val targetRoot = if (scope == ScriptPackScope.GLOBAL && game != null) game.resolve("kattonpacks").toAbsolutePath().normalize() else packRoot
            val location = previous?.location ?: targetRoot.resolve(folder)
            val snapshot = ScriptPackSnapshots(manifest, files)
            val pack = snapshot.toPack(location, scope, ScriptPackKind.DIRECTORY, syncIdOverride = syncId)
            require(pack.enabled) { "Enable $syncId before applying" }
            require(previous?.enabled != false) { "Target pack is disabled: $syncId" }
            requested += pack
            if (previous?.hash == pack.hash) return@forEach
            if (scope == ScriptPackScope.GLOBAL && (game == null || previous?.codeHash != pack.codeHash)) { restart += syncId; return@forEach }
            require(location.toAbsolutePath().normalize().parent == targetRoot && !Files.isSymbolicLink(location)) { "Pack is outside the writable scope directory: $syncId" }
            require(previous == null || previous.kind == ScriptPackKind.DIRECTORY) { "ZIP packs must be imported into a separate directory" }
            val expected = input.get("expectedHash")?.takeUnless { it.isJsonNull }?.asString
            val disk = if (Files.exists(location, NOFOLLOW_LINKS)) {
                require(Files.isDirectory(location, NOFOLLOW_LINKS)) { "Target is not a directory" }
                ScriptPackSnapshots.directory(location, Files.readString(location.resolve("manifest.json")), ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES)
                    .toPack(location, scope, ScriptPackKind.DIRECTORY, syncIdOverride = syncId)
            } else null
            require(disk?.hash == expected) { "Pack changed on disk; refresh before applying: $syncId" }
            val owner = readOwners(targetRoot.parent).get(syncId.lowercase())?.asString
            require(owner == null || owner == workspace) { "Pack belongs to another workspace: $syncId" }
            require(disk == null || owner == workspace || input.get("adopt")?.asBoolean == true) { "Explicit adoption required: $syncId" }
            changes += Change(pack, snapshot, previous)
        }
    }

    /** Rejects a deployment that would exceed the client sync bundle budget at join time. */
    fun requireSyncBudget(limitFiles: Int, limitBytes: Long) {
        var files = 0L
        var bytes = 0L
        proposed().filter { it.manifest.clientSync && ScriptDependencyManager.isPackAvailable(it, ScriptEnvironment.SERVER) }
            .forEach { pack ->
                files += pack.contentFiles.size
                bytes += pack.manifestJson.toByteArray(Charsets.UTF_8).size
                pack.contentFiles.forEach { bytes += it.bytes.size }
            }
        require(files <= limitFiles) { "Client sync would carry $files files (maximum $limitFiles): ${restart.joinToString()}" }
        require(bytes <= limitBytes) { "Client sync would carry $bytes bytes (maximum $limitBytes); reduce synchronized pack content" }
    }

    fun proposed(): List<ScriptPack> = existing.filter { old -> old.enabled && requested.none { it.syncId.equals(old.syncId, true) } } + requested
    fun affected(): Set<String> = ScriptPackDependencyGraph.affected(existing.filter { it.enabled }, proposed(), changes.mapTo(hashSetOf()) { it.pack.syncId })

    private fun validateRoot(root: Path) {
        listOf(root.resolve("kattonpacks"), root.resolve(".katton"), root.resolve(".katton/dev"), root.resolve(".katton/dev/owners.json")).forEach {
            require(!Files.isSymbolicLink(it) && (!Files.exists(it, NOFOLLOW_LINKS) || it.toRealPath().startsWith(root.toRealPath()))) { "Development paths must stay inside their scope directory: $it" }
        }
    }
    private fun readOwners(root: Path): JsonObject {
        val file = root.resolve(".katton/dev/owners.json")
        return if (Files.exists(file)) JsonParser.parseString(Files.readString(file)).asJsonObject else JsonObject()
    }
    private fun stageFor(change: Change): Stage = stages.getValue(change.pack.location.toAbsolutePath().normalize().parent.parent)

    fun install() {
        check(stages.isEmpty())
        try {
            changes.forEachIndexed { index, change ->
                val scopeRoot = change.pack.location.toAbsolutePath().normalize().parent.parent
                validateRoot(scopeRoot)
                val stage = stages.getOrPut(scopeRoot) {
                    Files.createDirectories(scopeRoot.resolve("kattonpacks"))
                    val parent = scopeRoot.resolve(".katton/dev")
                    Files.createDirectories(parent)
                    Stage(Files.createTempDirectory(parent, "apply-"), parent.resolve("owners.json"), readOwners(scopeRoot))
                }
                val fresh = stage.root.resolve("new-$index")
                Files.createDirectories(fresh)
                Files.writeString(fresh.resolve("manifest.json"), change.snapshot.manifest)
                change.snapshot.files.forEach { (name, bytes) ->
                    val destination = fresh.resolve(name)
                    Files.createDirectories(destination.parent)
                    Files.write(destination, bytes)
                }
                val state = change.pack.location.resolve(".kattonpack.state.json")
                if (Files.isRegularFile(state, NOFOLLOW_LINKS)) Files.copy(state, fresh.resolve(state.fileName))
            }
            changes.forEachIndexed { index, change ->
                val stage = stageFor(change).root
                if (Files.exists(change.pack.location, NOFOLLOW_LINKS)) Files.move(change.pack.location, stage.resolve("old-$index"))
                installed += change
                Files.move(stage.resolve("new-$index"), change.pack.location)
            }
        } catch (t: Throwable) { finish(emptySet()); throw t }
    }

    /** Disk follows the generations actually committed by Katton's existing transactions. */
    fun finish(accepted: Set<String>) {
        if (stages.isEmpty()) return
        installed.forEach { change ->
            val index = changes.indexOf(change)
            val stage = stageFor(change)
            if (change.pack.syncId in accepted) stage.owners.addProperty(change.pack.syncId.lowercase(), workspace)
            else {
                deleteTree(change.pack.location)
                val old = stage.root.resolve("old-$index")
                if (Files.exists(old)) Files.move(old, change.pack.location)
            }
        }
        stages.values.forEach { stage ->
            val pending = Files.createTempFile(stage.ownership.parent, "owners-", ".json")
            Files.writeString(pending, stage.owners.toString())
            Files.move(pending, stage.ownership, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            deleteTree(stage.root)
        }
        stages.clear(); installed.clear()
    }

    private fun deleteTree(path: Path) {
        val absolute = path.toAbsolutePath().normalize()
        require(listOfNotNull(world, game).any { root -> absolute.startsWith(root.toAbsolutePath().normalize()) && absolute != root.toAbsolutePath().normalize() })
        if (Files.exists(path, NOFOLLOW_LINKS)) Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
