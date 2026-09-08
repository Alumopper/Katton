package top.katton.engine

import top.katton.pack.*
import top.katton.api.LOGGER
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

internal data class PackPreparation(val pack: ScriptPack, val artifact: PackArtifact, val libraries: PrivateLibraries,
    val host: ScriptDependencySelection, val visible: List<String>)
internal data class PackInstance(val preparation: PackPreparation, val environment: ScriptEnvironment, val generation: Long,
    val loader: PackClassLoader, val dependencies: List<PackInstance>, val phases: MutableSet<String> = linkedSetOf()) {
    val ownerPrefix = "${preparation.pack.scope.serializedName}:${environment.name}:${preparation.pack.syncId}:$generation:"
}

/** Declaration resolution, immutable compilation plans and active generations have distinct lifetimes. */
internal object PackRuntime {
    private val generations = AtomicLong()
    private val active = mutableMapOf<Pair<ScriptEnvironment, String>, PackInstance>()
    private val retired = mutableListOf<PackInstance>()

    /** Persistent values pin their defining generation and its dependency instances. */
    fun collectRetired() {
        val releasable = synchronized(active) {
            val reachable = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<PackInstance, Boolean>())
            fun retain(instance: PackInstance) { if (reachable.add(instance)) instance.dependencies.forEach(::retain) }
            active.values.forEach(::retain)
            retired.filter { ManagedResources.retains(it.ownerPrefix) }.forEach(::retain)
            val loaders = reachable.mapTo(hashSetOf()) { it.loader }
            retired.filterNot { it.loader in loaders }.also { released -> retired.removeAll(released.toSet()) }
        }
        releasable.forEach { runCatching { it.loader.close() }.onFailure { failure -> LOGGER.warn("Cannot close retired pack loader", failure) } }
    }

    private data class Pending(val commit: () -> Unit, val rollback: () -> Unit)
    private val pending = ThreadLocal<MutableList<Pending>?>()
    fun carryTransaction(action: () -> Unit): () -> Unit {
        val captured = pending.get()
        return {
            val previous = pending.get()
            pending.set(captured)
            try { action() } finally { if (previous == null) pending.remove() else pending.set(previous) }
        }
    }
    private fun onThread(environment: ScriptEnvironment, action: () -> Unit) = ScriptEngine.onRuntimeThread(environment, action)

    fun transaction(action: () -> Boolean): Boolean {
        if (pending.get() != null) return action()
        val changes = mutableListOf<Pending>()
        pending.set(changes)
        try {
            val success = action()
            if (success) changes.forEach { it.commit() } else rollbackPending()
            return success
        } catch (failure: Throwable) {
            rollbackPending()
            throw failure
        } finally { pending.remove() }
    }
    fun rollbackPending() {
        val changes = pending.get() ?: return
        changes.asReversed().forEach { it.rollback() }
        changes.clear()
    }

    fun prepare(packs: Collection<ScriptPack>, invocation: ScriptInvocation, root: Path, hostClasspath: List<Path>): List<PackPreparation> {
        val graph = ScriptPackDependencyGraph.resolve(packs.filter { it.enabled })
        require(graph.invalidPacks.isEmpty()) { graph.errors.joinToString("\n") }
        val prepared = linkedMapOf<String, PackPreparation>()
        graph.orderedPacks.forEach { pack ->
            val host = ScriptDependencyManager.resolve(listOf(pack), invocation.environment, invocation.phaseName)
            require(host.validPacks.size == 1) { host.errors.joinToString("\n") }
            val libraries = PrivateLibraries.prepare(pack, root.resolve("libraries-v3"))
            val visible = graph.visiblePacks(pack).map { prepared.getValue(it.syncId) }
            val classpath = hostClasspath + host.resolved.flatMap { it.classpath } + libraries.paths + visible.map { it.artifact.jar }
            val fingerprints = host.fingerprints + visible.map { "${it.pack.syncId}:${it.artifact.fingerprint}" } +
                hostClasspath.map { "host:$it:${java.nio.file.Files.getLastModifiedTime(it)}" }
            val artifact = PackCompiler.compile(pack, root.resolve("artifacts-v3"), classpath, fingerprints)
            PackApiValidator.validate(pack, artifact, libraries, visible)
            val visibleNames = visible.flatMap { it.artifact.classNames }.toSet() + artifact.classNames
            val hidden = prepared.values.flatMap { dependency -> dependency.artifact.classNames.filterNot { it in visibleNames }
                .map { it to "non-exported package ${dependency.pack.syncId}" } }.toMap()
            visible.forEach { dependency -> PackApiValidator.validate(pack, dependency.artifact, dependency.libraries, visible, hidden) }
            prepared[pack.syncId] = PackPreparation(pack, artifact, libraries, host, visible.map { it.pack.syncId })
        }
        return prepared.values.toList()
    }

    fun execute(prepared: List<PackPreparation>, invocation: ScriptInvocation, requestedIds: Set<String>, hostLoader: (ScriptDependencySelection) -> ClassLoader,
                execute: (PackInstance, ScriptInvocation) -> Boolean): Boolean {
        val environment = invocation.environment
        val current = synchronized(active) { active.filterKeys { it.first == environment }.values.toList() }
        val oldPacks = current.map { it.preparation.pack }
        val newPacks = prepared.map { it.pack }
        val byId = prepared.associateBy { it.pack.syncId }
        val changes = prepared.filter { plan ->
            if (plan.pack.scope == ScriptPackScope.GLOBAL && current.any { it.preparation.pack.syncId == plan.pack.syncId }) {
                if (current.any { it.preparation.pack.syncId == plan.pack.syncId && it.preparation.pack.codeHash != plan.pack.codeHash })
                    LOGGER.warn("Global pack {} changed; restart required", plan.pack.syncId)
                return@filter false
            }
            current.none { it.preparation.pack.syncId == plan.pack.syncId && it.preparation.artifact.fingerprint == plan.artifact.fingerprint }
        }.mapTo(linkedSetOf()) { it.pack.syncId }
        // Only scopes explicitly dispatched by this lifecycle are eligible for removal.
        val scopes = newPacks.filter { it.syncId in requestedIds }.mapTo(hashSetOf()) { it.scope }.ifEmpty { hashSetOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE) }
        current.filter { it.preparation.pack.scope in scopes && it.preparation.pack.syncId !in byId }.forEach { changes += it.preparation.pack.syncId }
        val transactions = ScriptPackDependencyGraph.transactions(oldPacks, newPacks, changes)
        for (ids in transactions) {
            val previous = current.filter { it.preparation.pack.syncId in ids }
            val candidates = linkedMapOf<String, PackInstance>()
            try {
                prepared.filter { it.pack.syncId in ids }.forEach { plan ->
                    val dependencies = plan.visible.map { id -> candidates[id] ?: synchronized(active) { active[environment to id] }
                        ?: error("Missing active dependency instance: $id") }
                    candidates[plan.pack.syncId] = PackInstance(plan, environment, generations.incrementAndGet(),
                        PackClassLoader(plan.artifact, plan.libraries, hostLoader(plan.host), dependencies.map { it.loader }), dependencies)
                }
                val candidatePrefixes = candidates.values.map { it.ownerPrefix }
                ManagedResources.pause(candidatePrefixes, 0)
                val prefixes = previous.map { it.ownerPrefix }
                check(ManagedResources.pause(prefixes, 5000)) { "Timed out waiting for managed callbacks" }
                val saved = try { ManagedResources.detach(prefixes) } catch (failure: Throwable) {
                    ManagedResources.resume(prefixes)
                    throw failure
                }
                try {
                    candidates.values.filter { it.preparation.pack.syncId in requestedIds }.forEach { instance ->
                        check(ManagedResources.activation(instance.ownerPrefix) { execute(instance, invocation) }) { "Entrypoint failed: ${instance.preparation.pack.syncId}" }
                        instance.phases += invocation.phaseName
                    }
                    synchronized(active) {
                        ids.forEach { active.remove(environment to it) }
                        candidates.forEach { (id, instance) -> active[environment to id] = instance }
                    }
                    val pendingChange = Pending(
                        commit = { onThread(environment) {
                            ManagedResources.discard(saved)
                            synchronized(active) { retired += previous }
                            collectRetired()
                            ManagedResources.resume(candidatePrefixes)
                            ManagedResources.resume(prefixes)
                        } },
                        rollback = { onThread(environment) {
                            ManagedResources.discard(ManagedResources.detach(candidates.values.map { it.ownerPrefix }, includePersistent = true))
                            ManagedResources.restore(saved)
                            synchronized(active) {
                                ids.forEach { active.remove(environment to it) }
                                previous.forEach { active[environment to it.preparation.pack.syncId] = it }
                            }
                            ManagedResources.resume(prefixes)
                            ManagedResources.resume(candidatePrefixes)
                            candidates.values.forEach { it.loader.close() }
                        } }
                    )
                    pending.get()?.add(pendingChange) ?: pendingChange.commit()
                } catch (failure: Throwable) {
                    ManagedResources.discard(ManagedResources.detach(candidates.values.map { it.ownerPrefix }, includePersistent = true))
                    ManagedResources.restore(saved)
                    synchronized(active) {
                        ids.forEach { active.remove(environment to it) }
                        previous.forEach { active[environment to it.preparation.pack.syncId] = it }
                    }
                    ManagedResources.resume(prefixes)
                    throw failure
                }
            } catch (failure: Throwable) {
                ManagedResources.resume(candidates.values.map { it.ownerPrefix })
                candidates.values.forEach { runCatching { it.loader.close() } }
                LOGGER.error("Rejected pack transaction {}", ids, failure)
                if (prepared.any { it.pack.syncId in ids && it.pack.scope == ScriptPackScope.SERVER_CACHE }) return false
                continue
            }
        }
        // A dependency does not force any earlier phase; dispatch only the phase requested by the platform.
        prepared.filter { it.pack.syncId in requestedIds }.forEach { plan ->
            val instance = synchronized(active) { active[environment to plan.pack.syncId] } ?: return@forEach
            if (instance.preparation.artifact.fingerprint == plan.artifact.fingerprint && instance.preparation.pack.hash != plan.pack.hash) {
                val updated = instance.copy(preparation = plan)
                synchronized(active) { active[environment to plan.pack.syncId] = updated }
                pending.get()?.add(Pending({}, { synchronized(active) { active[environment to plan.pack.syncId] = instance } }))
            }
            if (invocation.phaseName !in instance.phases) {
                val phasePrefix = instance.ownerPrefix + invocation.phaseName + ":"
                try {
                    check(ManagedResources.activation(instance.ownerPrefix) { execute(instance, invocation) }) { "Entrypoint phase failed" }
                    instance.phases += invocation.phaseName
                    pending.get()?.add(Pending({}, { onThread(environment) {
                        ManagedResources.discard(ManagedResources.detach(listOf(phasePrefix), includePersistent = true))
                        instance.phases.remove(invocation.phaseName)
                    } }))
                } catch (failure: Throwable) {
                    ManagedResources.discard(ManagedResources.detach(listOf(phasePrefix), includePersistent = true))
                    LOGGER.error("Rejected lifecycle phase {} for {}", invocation.phaseName, plan.pack.syncId, failure)
                    return false
                }
            }
        }
        return true
    }

    fun resetGlobalPhase(environment: ScriptEnvironment, phase: String) {
        val instances = synchronized(active) { active.values.filter { it.environment == environment && it.preparation.pack.scope == ScriptPackScope.GLOBAL } }
        instances.forEach { instance ->
            ManagedResources.discard(ManagedResources.detach(listOf(instance.ownerPrefix + phase + ":")))
            instance.phases.remove(phase)
        }
    }

    fun effectivePacks(environment: ScriptEnvironment, scopes: Set<ScriptPackScope>): List<ScriptPack> = synchronized(active) {
        active.values.filter { it.environment == environment && it.preparation.pack.scope in scopes }.map { it.preparation.pack }
    }
    fun preparations(environment: ScriptEnvironment): List<PackPreparation> = synchronized(active) {
        active.values.filter { it.environment == environment }.map { it.preparation }
    }

    fun clear(environment: ScriptEnvironment, scopes: Set<ScriptPackScope>) {
        val prefixes = synchronized(active) { active.values.filter { it.environment == environment && it.preparation.pack.scope in scopes }.map { it.ownerPrefix } }
        check(ManagedResources.pause(prefixes, 5000)) { "Timed out draining script callbacks during lifecycle cleanup" }
        val removed = synchronized(active) {
            active.values.filter { it.environment == environment && it.preparation.pack.scope in scopes }.also { instances ->
                instances.forEach { active.remove(environment to it.preparation.pack.syncId) }
            }
        }
        try {
            ManagedResources.discard(ManagedResources.detach(prefixes))
            synchronized(active) { retired += removed }
            collectRetired()
        } finally { ManagedResources.resume(prefixes) }
    }
}
