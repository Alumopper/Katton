package top.katton.pack

import top.katton.engine.VersionConstraint
import java.util.Locale

data class ScriptPackDependency(
    val id: String,
    val version: String = "*",
    val required: Boolean = true
)

data class ScriptPackDependencySelection(
    val orderedPacks: List<ScriptPack>,
    val invalidPacks: Set<ScriptPack>,
    val errors: List<String>
)

/** Validates pack-to-pack dependencies and returns a deterministic topological order. */
object ScriptPackDependencyGraph {
    fun resolve(packs: Collection<ScriptPack>): ScriptPackDependencySelection {
        val candidates = packs.distinctBy { it.syncId }
        val bySyncId = candidates.associateBy { it.syncId.lowercase(Locale.ROOT) }
        val byManifestId = candidates.groupBy { it.manifest.id.lowercase(Locale.ROOT) }
        val requiredDependencies = linkedMapOf<ScriptPack, MutableSet<ScriptPack>>()
        val invalid = linkedSetOf<ScriptPack>()
        val errors = mutableListOf<String>()

        candidates.forEach { pack ->
            val requiredResolved = linkedSetOf<ScriptPack>()
            pack.manifest.packDependencies.forEach { dependency ->
                val normalizedId = dependency.id.lowercase(Locale.ROOT)
                val exact = bySyncId[normalizedId]
                val manifestMatches = byManifestId[normalizedId].orEmpty()
                val target = exact ?: manifestMatches.singleOrNull()
                val failure = when {
                    exact == null && manifestMatches.size > 1 ->
                        "is ambiguous (${manifestMatches.joinToString { it.syncId }})"
                    target == null -> "is not enabled"
                    target === pack -> "refers to the pack itself"
                    !VersionConstraint.matches(target.manifest.version, dependency.version) ->
                        "has version ${target.manifest.version}, which does not satisfy ${dependency.version}"
                    else -> null
                }
                if (failure != null) {
                    if (dependency.required) {
                        invalid += pack
                        errors += "Pack '${pack.syncId}' requires script pack '${dependency.id}' ${dependency.version}, but it $failure"
                    }
                } else if (target != null) {
                    if (dependency.required) requiredResolved += target
                }
            }
            requiredDependencies[pack] = requiredResolved
        }

        // A pack cannot remain valid when any required dependency was rejected.
        var changed: Boolean
        do {
            changed = false
            requiredDependencies.forEach { (pack, required) ->
                if (pack !in invalid && required.any { it in invalid }) {
                    invalid += pack
                    errors += "Pack '${pack.syncId}' depends on a rejected script pack"
                    changed = true
                }
            }
        } while (changed)

        val valid = candidates.filterNot(invalid::contains)
        val indegree = valid.associateWith { pack -> requiredDependencies.getValue(pack).count { it in valid } }.toMutableMap()
        val dependents = valid.associateWith { linkedSetOf<ScriptPack>() }
        valid.forEach { pack ->
            requiredDependencies.getValue(pack).filter { it in valid }.forEach { dependency ->
                dependents.getValue(dependency) += pack
            }
        }
        val ready = java.util.PriorityQueue(compareBy<ScriptPack> { it.syncId.lowercase(Locale.ROOT) })
        indegree.filterValues { it == 0 }.keys.forEach(ready::add)
        val ordered = mutableListOf<ScriptPack>()
        while (ready.isNotEmpty()) {
            val pack = ready.remove()
            ordered += pack
            dependents.getValue(pack).forEach { dependent ->
                val remaining = indegree.getValue(dependent) - 1
                indegree[dependent] = remaining
                if (remaining == 0) ready += dependent
            }
        }

        val cyclic = valid.filterNot(ordered::contains)
        if (cyclic.isNotEmpty()) {
            invalid += cyclic
            errors += "Script pack dependency cycle: ${cyclic.map { it.syncId }.sorted().joinToString(" -> ")}"
        }
        return ScriptPackDependencySelection(ordered.filterNot(invalid::contains), invalid, errors.distinct())
    }

    /** Weakly connected dependency components used as independent compilation units. */
    fun compilationGroups(packs: Collection<ScriptPack>): List<List<ScriptPack>> {
        val selection = resolve(packs)
        val ordered = selection.orderedPacks
        if (ordered.isEmpty()) return emptyList()
        val bySyncId = ordered.associateBy { it.syncId.lowercase(Locale.ROOT) }
        val byManifestId = ordered.groupBy { it.manifest.id.lowercase(Locale.ROOT) }
        val adjacent = ordered.associateWith { linkedSetOf<ScriptPack>() }
        ordered.forEach { pack ->
            pack.manifest.packDependencies.forEach dependencyLoop@{ dependency ->
                val key = dependency.id.lowercase(Locale.ROOT)
                val target = bySyncId[key] ?: byManifestId[key].orEmpty().singleOrNull() ?: return@dependencyLoop
                if (!VersionConstraint.matches(target.manifest.version, dependency.version)) return@dependencyLoop
                adjacent.getValue(pack) += target
                adjacent.getValue(target) += pack
            }
        }

        val visited = hashSetOf<ScriptPack>()
        return buildList {
            ordered.forEach { root ->
                if (!visited.add(root)) return@forEach
                val component = linkedSetOf<ScriptPack>()
                val queue = ArrayDeque<ScriptPack>()
                queue += root
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component += current
                    adjacent.getValue(current).forEach { next ->
                        if (visited.add(next)) queue += next
                    }
                }
                add(ordered.filter(component::contains))
            }
        }
    }
}
