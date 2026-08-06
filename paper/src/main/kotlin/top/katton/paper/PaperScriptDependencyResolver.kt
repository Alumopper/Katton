package top.katton.paper

import org.bukkit.plugin.Plugin
import top.katton.engine.PlatformDependencyResolver
import top.katton.engine.ResolvedScriptDependency
import java.nio.file.Files
import java.nio.file.Path

class PaperScriptDependencyResolver(
    private val plugin: KattonPaperPlugin
) : PlatformDependencyResolver {
    override fun resolve(id: String): ResolvedScriptDependency? {
        val dependency = plugin.server.pluginManager.plugins
            .firstOrNull { candidate -> candidate.name.equals(id, ignoreCase = true) }
            ?: return null
        return ResolvedScriptDependency(
            id = dependency.name,
            version = dependency.pluginMeta.version,
            classpath = locatePluginClasspath(dependency),
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
