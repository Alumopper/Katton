package top.katton.engine

import top.katton.api.LOGGER
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackDependencyGraph
import top.katton.pack.ScriptPackScope

/** Commits disjoint impact sets separately, including their resource views. */
internal object PackReloadBatch {
    fun run(
        previous: List<ScriptPack>, candidate: List<ScriptPack>,
        activate: (List<ScriptPack>) -> List<ScriptPack>?,
        restoreResources: (List<ScriptPack>) -> Unit
    ): List<ScriptPack>? {
        val old = previous.associateBy { it.syncId }
        val next = candidate.associateBy { it.syncId }
        val changed = (old.keys + next.keys).filterTo(linkedSetOf()) { id ->
            old[id]?.hash != next[id]?.hash || next[id]?.manifest?.dependencies?.isNotEmpty() == true
        }
        // A synchronized remote revision is acknowledged as a unit; local edits can commit independently.
        val strict = (previous + candidate).any { it.scope == ScriptPackScope.SERVER_CACHE }
        val groups = if (strict) listOf(changed) else ScriptPackDependencyGraph.transactions(previous, candidate, changed)
        var working = previous
        for (ids in groups.ifEmpty { listOf(emptySet()) }) {
            val proposed = working.filterNot { it.syncId in ids } + candidate.filter { it.syncId in ids }
            var accepted: List<ScriptPack>? = null
            val success = runCatching {
                PackRuntime.transaction {
                    accepted = activate(proposed)
                    accepted != null
                }
            }.onFailure { LOGGER.error("Pack resource transaction failed for {}", ids, it) }.getOrDefault(false)
            if (success) working = checkNotNull(accepted)
            else {
                restoreResources(working)
                if (strict) return null
            }
        }
        return working
    }
}
