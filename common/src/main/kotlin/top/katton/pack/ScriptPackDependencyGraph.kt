package top.katton.pack

import top.katton.engine.VersionConstraint
import java.util.Locale

data class ScriptPackDependency(val id: String, val version: String = "*", val required: Boolean = true, val export: Boolean = false)
data class ResolvedPackEdge(val target: ScriptPack, val declaration: ScriptPackDependency)
data class ScriptPackDependencySelection(
    val orderedPacks: List<ScriptPack>, val invalidPacks: Set<ScriptPack>, val errors: List<String>,
    val edges: Map<ScriptPack, List<ResolvedPackEdge>> = emptyMap()
) {
    /** Direct artifacts and only explicitly exported transitive artifacts. Never includes private libraries. */
    fun visiblePacks(pack: ScriptPack): List<ScriptPack> {
        val visible = linkedSetOf<ScriptPack>()
        fun exported(target: ScriptPack) {
            if (!visible.add(target)) return
            edges[target].orEmpty().filter { it.declaration.export }.forEach { exported(it.target) }
        }
        edges[pack].orEmpty().forEach { exported(it.target) }
        return orderedPacks.filter { it in visible }
    }
}

object ScriptPackDependencyGraph {
    fun resolve(packs: Collection<ScriptPack>): ScriptPackDependencySelection {
        val candidates = packs.toList()
        val bySync = candidates.groupBy { it.syncId.lowercase(Locale.ROOT) }
        val byId = candidates.groupBy { it.manifest.id.lowercase(Locale.ROOT) }
        val invalid = linkedSetOf<ScriptPack>()
        val errors = mutableListOf<String>()
        val edges = linkedMapOf<ScriptPack, List<ResolvedPackEdge>>()
        bySync.filterValues { it.size > 1 }.forEach { (id, duplicates) ->
            invalid += duplicates
            errors += "Duplicate script pack identity: $id"
        }
        candidates.forEach { pack ->
            edges[pack] = pack.manifest.packDependencies.mapNotNull { dependency ->
                val id = dependency.id.lowercase(Locale.ROOT)
                val matches = bySync[id] ?: byId[id].orEmpty()
                val target = matches.singleOrNull()
                val failure = when {
                    matches.size > 1 -> "is ambiguous (${matches.joinToString { it.syncId }})"
                    target == null -> "is not enabled"
                    !VersionConstraint.matches(target.manifest.version, dependency.version) -> "has incompatible version ${target.manifest.version}"
                    pack.scope == ScriptPackScope.GLOBAL && target.scope != ScriptPackScope.GLOBAL -> "has a shorter lifecycle than a global pack"
                    pack.scope == ScriptPackScope.SERVER_CACHE && target.scope != ScriptPackScope.SERVER_CACHE -> "is outside the remote synchronization set"
                    else -> null
                }
                if (failure != null) {
                    if (dependency.required) {
                        invalid += pack
                        errors += "Pack '${pack.syncId}' requires '${dependency.id}' ${dependency.version}, but it $failure"
                    }
                    null
                } else ResolvedPackEdge(target!!, dependency)
            }
        }
        val visited = hashSetOf<ScriptPack>()
        val stack = mutableListOf<ScriptPack>()
        val ordered = mutableListOf<ScriptPack>()
        fun visit(pack: ScriptPack) {
            val cycleStart = stack.indexOf(pack)
            if (cycleStart >= 0) {
                val cycle = stack.subList(cycleStart, stack.size).toList() + pack
                invalid += cycle
                errors += "Script pack dependency cycle: ${cycle.joinToString(" -> ") { it.syncId }}"
                return
            }
            if (!visited.add(pack)) return
            stack += pack
            edges[pack].orEmpty().forEach { visit(it.target) }
            stack.removeAt(stack.lastIndex)
            ordered += pack
        }
        candidates.sortedBy { it.syncId }.forEach(::visit)
        do {
            val rejected = candidates.filter { it !in invalid && edges[it].orEmpty().any { edge -> edge.declaration.required && edge.target in invalid } }
            invalid += rejected
            rejected.forEach { errors += "Pack '${it.syncId}' depends on a rejected script pack" }
        } while (rejected.isNotEmpty())
        val valid = ordered.filterNot { it in invalid }
        return ScriptPackDependencySelection(valid, invalid, errors.distinct(),
            edges.filterKeys { it !in invalid }.mapValues { (_, value) -> value.filter { it.target !in invalid } })
    }

    /** Reverse closure over both graphs handles removed edges, deletions and optional dependencies appearing. */
    fun affected(previous: Collection<ScriptPack>, next: Collection<ScriptPack>, changed: Set<String>): Set<String> {
        val consumers = mutableMapOf<String, MutableSet<String>>()
        listOf(resolve(previous), resolve(next)).forEach { graph -> graph.edges.forEach { (pack, edges) ->
            edges.forEach { consumers.getOrPut(it.target.syncId) { linkedSetOf() } += pack.syncId }
        } }
        val result = changed.toMutableSet()
        val queue = ArrayDeque(changed)
        while (queue.isNotEmpty()) consumers[queue.removeFirst()].orEmpty().forEach { if (result.add(it)) queue += it }
        return result
    }

    fun transactions(previous: Collection<ScriptPack>, next: Collection<ScriptPack>, changed: Set<String>): List<Set<String>> {
        val groups = mutableListOf<MutableSet<String>>()
        changed.sorted().forEach { id ->
            val impact = affected(previous, next, setOf(id)).toMutableSet()
            val overlaps = groups.filter { it.any(impact::contains) }
            overlaps.forEach { impact += it }
            groups.removeAll(overlaps.toSet())
            groups += impact
        }
        return groups
    }
}
