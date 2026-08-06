package top.katton.platform

import net.fabricmc.loader.api.FabricLoader
import top.katton.engine.ResolvedScriptDependency
import top.katton.engine.PlatformDependencyResolver
import java.nio.file.Files

object FabricScriptDependencyResolver : PlatformDependencyResolver {
    override fun resolve(id: String): ResolvedScriptDependency? {
        val loader = FabricLoader.getInstance()
        val container = loader.getModContainer(id).orElse(null) ?: return null
        val visited = linkedSetOf<String>()
        val roots = buildList {
            fun collect(modId: String) {
                if (!visited.add(modId)) return
                val current = loader.getModContainer(modId).orElse(null) ?: return
                addAll(current.origin.paths)
                addAll(current.rootPaths)
                current.metadata.depends.forEach { collect(it.modId) }
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
        )
    }
}
