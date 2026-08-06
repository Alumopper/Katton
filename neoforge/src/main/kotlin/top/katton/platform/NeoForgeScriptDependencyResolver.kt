package top.katton.platform

import net.neoforged.fml.ModList
import top.katton.engine.PlatformDependencyResolver
import top.katton.engine.ResolvedScriptDependency
import java.nio.file.Files

object NeoForgeScriptDependencyResolver : PlatformDependencyResolver {
    override fun resolve(id: String): ResolvedScriptDependency? {
        val info = ModList.get().mods.firstOrNull { it.modId.equals(id, ignoreCase = true) } ?: return null
        val modsById = ModList.get().mods.associateBy { it.modId.lowercase() }
        val visited = linkedSetOf<String>()
        val paths = buildList {
            fun collect(modId: String) {
                if (!visited.add(modId.lowercase())) return
                val current = modsById[modId.lowercase()] ?: return
                add(current.owningFile.file.filePath.toAbsolutePath().normalize())
                requiredDependencyIds(current).forEach(::collect)
            }
            collect(info.modId)
        }
        return ResolvedScriptDependency(
            id = info.modId,
            version = info.version.toString(),
            classpath = paths.filter(Files::exists).distinct(),
            classLoader = NeoForgeScriptDependencyResolver::class.java.classLoader,
            enabled = true
        )
    }

    /** NeoForge has changed its dependency metadata interfaces across loader releases. */
    private fun requiredDependencyIds(modInfo: Any): List<String> = runCatching {
        val getter = modInfo.javaClass.methods.firstOrNull { it.name == "getDependencies" && it.parameterCount == 0 }
            ?: return@runCatching emptyList()
        val dependencies = getter.invoke(modInfo) as? Iterable<*> ?: return@runCatching emptyList()
        dependencies.mapNotNull { dependency ->
            dependency ?: return@mapNotNull null
            val type = dependency.javaClass.methods
                .firstOrNull { it.name in setOf("getType", "getDependencyType") && it.parameterCount == 0 }
                ?.invoke(dependency)?.toString()
            if (type != null && type !in setOf("REQUIRED", "REQUIRED_BEFORE", "REQUIRED_AFTER")) return@mapNotNull null
            dependency.javaClass.methods
                .firstOrNull { it.name in setOf("getModId", "getId") && it.parameterCount == 0 }
                ?.invoke(dependency) as? String
        }
    }.getOrDefault(emptyList())
}
