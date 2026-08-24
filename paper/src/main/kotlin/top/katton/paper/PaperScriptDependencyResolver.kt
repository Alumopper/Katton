package top.katton.paper

import org.bukkit.plugin.Plugin
import top.katton.engine.PlatformDependencyResolver
import top.katton.engine.ResolvedScriptDependency
import java.nio.file.Files
import java.nio.file.Path
import java.util.WeakHashMap

class PaperScriptDependencyResolver(
    private val plugin: KattonPaperPlugin
) : PlatformDependencyResolver {
    // Weak keys avoid pinning a plugin classloader if a server replaces a plugin
    // at runtime. Cached values intentionally do not retain the plugin or its
    // classloader, and enabled state is read afresh on every lookup.
    private val metadataCache = WeakHashMap<Plugin, CachedPluginMetadata>()

    private data class CachedPluginMetadata(
        val id: String,
        val version: String,
        val classpath: List<Path>
    )

    override fun resolve(id: String): ResolvedScriptDependency? {
        val pluginManager = plugin.server.pluginManager
        val dependency = pluginManager.getPlugin(id)
            ?: pluginManager.plugins.firstOrNull { candidate -> candidate.name.equals(id, ignoreCase = true) }
            ?: return null
        val metadata = synchronized(metadataCache) {
            metadataCache.getOrPut(dependency) {
                CachedPluginMetadata(
                    id = dependency.name,
                    version = dependency.pluginMeta.version,
                    classpath = locatePluginClasspath(dependency)
                )
            }
        }
        return ResolvedScriptDependency(
            id = metadata.id,
            version = metadata.version,
            classpath = metadata.classpath,
            classLoader = dependency.javaClass.classLoader,
            enabled = dependency.isEnabled
        )
    }

    private fun locatePluginClasspath(dependency: Plugin): List<Path> {
        val codeSource = runCatching { dependency.javaClass.protectionDomain?.codeSource?.location }.getOrNull()
            ?: return emptyList()
        val path = runCatching { Path.of(codeSource.toURI()).toAbsolutePath().normalize() }.getOrNull()
            ?: return emptyList()
        return listOf(path).filter(Files::exists)
    }
}
