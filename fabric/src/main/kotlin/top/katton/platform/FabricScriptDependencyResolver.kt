package top.katton.platform

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModDependency
import top.katton.engine.ResolvedScriptDependency
import top.katton.engine.PlatformDependencyResolver
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object FabricScriptDependencyResolver : PlatformDependencyResolver {
    // Fabric's mod graph is immutable after startup. Cache the transitive root
    // walk because it may otherwise repeat for every script hot reload.
    private val resolved = ConcurrentHashMap<String, ResolvedScriptDependency>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    override fun resolve(id: String): ResolvedScriptDependency? {
        val key = id.lowercase(Locale.ROOT)
        resolved[key]?.let { return it }
        if (key in missing) return null

        val loader = FabricLoader.getInstance()
        val container = loader.getModContainer(id).orElse(null) ?: run {
            missing += key
            return null
        }
        val visited = linkedSetOf<String>()
        val roots = buildList {
            fun collect(modId: String) {
                if (!visited.add(modId)) return
                val current = loader.getModContainer(modId).orElse(null) ?: return
                addAll(current.origin.paths)
                addAll(current.rootPaths)
                current.metadata.dependencies
                    .asSequence()
                    .filter { it.kind == ModDependency.Kind.DEPENDS }
                    .forEach { collect(it.modId) }
            }
            collect(container.metadata.id)
        }
            .map { it.toAbsolutePath().normalize() }
            .filter { Files.exists(it) && (it.fileSystem == java.nio.file.FileSystems.getDefault()) }
            .distinct()
        return ResolvedScriptDependency(
            id = container.metadata.id,
            version = container.metadata.version.friendlyString,
            classpath = roots,
            classLoader = FabricScriptDependencyResolver::class.java.classLoader,
            enabled = true
        ).also { resolved[key] = it }
    }
}
