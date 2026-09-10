package top.katton.client.audio

import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.ChannelAccess
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import top.katton.api.audio.*
import top.katton.engine.AudioPackContext
import top.katton.engine.ManagedResources
import top.katton.pack.*
import top.katton.util.ScriptExecutionContext
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

object ClientAudioManager {
    private val handles = ConcurrentHashMap<UUID, LocalAudioHandle>()
    private var previousLevel: Any? = null
    @JvmStatic fun initialize() {
        AudioClientProvider.play = { source, options -> play(source, options) }
    }
    internal fun play(source: AudioSource, options: AudioOptions, id: UUID = UUID.randomUUID()): LocalAudioHandle {
        require(handles.size < 64) { "At most 64 audio instances may be open" }
        val mc = Minecraft.getInstance()
        check(mc.isSameThread) { "Create client audio on the client thread (runOnClient)" }
        val resolved = resolve(source)
        val handle = LocalAudioHandle(id, options, resolved.pitch, resolved.volume,
            mc.level?.dimension()?.identifier()?.toString(), ScriptExecutionContext.currentScriptOwner())
        check(handles.putIfAbsent(id, handle) == null) { "Duplicate audio instance" }
        handle.record = ManagedResources.record(
            attach = { handle.managedPause(false).join() }, detach = { handle.managedPause(true).join() },
            dispose = { handle.close() }, resumed = {})
        CompletableFuture.supplyAsync(resolved.bytes).whenComplete { bytes, error ->
            MinecraftAudioBackend.submit {
                if (handle.closed) return@submit
                if (error != null) handle.fail(error) else try {
                    val lease = AudioCache.acquire(bytes, options.spatial)
                    handle.lease = lease
                    lease.future.whenComplete { pcm, failure ->
                        if (failure != null) MinecraftAudioBackend.submit { if (!handle.closed) handle.fail(failure) }
                        else MinecraftAudioBackend.submit { if (!handle.closed) handle.prepared(pcm) }
                    }
                } catch (failure: Throwable) { handle.fail(failure) }
            }
        }
        return handle
    }
    private data class Resolved(val bytes: () -> ByteArray, val pitch: Float = 1f, val volume: Float = 1f)
    private fun resolve(source: AudioSource): Resolved {
        val mc = Minecraft.getInstance()
        if (source.kind == AudioSource.Kind.PACK_FILE) {
            val candidates = listOfNotNull(AudioPackContext.currentPack()) +
                ScriptPackManager.collectExecutablePacks() + ServerPackCacheManager.collectExecutablePacks()
            val pack = candidates.firstOrNull { it.syncId == source.packId && it.hash == source.revision }
                ?: error("Audio pack revision is not available: ${source.packId}")
            return Resolved({ ScriptPackAudioFiles.read(pack, source.path) })
        }
        var path = net.minecraft.resources.Identifier.parse(source.path)
        var pitch = 1f
        var volume = 1f
        if (source.kind == AudioSource.Kind.SOUND) {
            val random = RandomSource.create()
            val event = mc.soundManager.getSoundEvent(path) ?: error("Unknown sound event: $path")
            val sound = event.getSound(random)
            path = sound.path
            pitch = sound.pitch.sample(random)
            volume = sound.volume.sample(random)
        }
        val resource = mc.resourceManager.getResourceOrThrow(path)
        return Resolved({ resource.open().use { SafePackFileIo.readBytes(it, ScriptPackFileLimits.MAX_FILE_BYTES, path.toString()) } }, pitch, volume)
    }
    @JvmStatic fun tick() {
        val mc = Minecraft.getInstance()
        if (previousLevel != null && mc.level == null) disconnect()
        previousLevel = mc.level
        val dimension = mc.level?.dimension()?.identifier()?.toString()
        val pause = mc.isPaused
        // One index per tick instead of one linear scan per handle.
        val tracked = handles.values.mapNotNullTo(hashSetOf()) { it.options.entity }
        val entities = if (tracked.isEmpty()) emptyMap() else
            mc.level?.entitiesForRendering()?.filter { it.uuid in tracked }?.associateBy { it.uuid }.orEmpty()
        handles.values.forEach { handle ->
            val entity = handle.options.entity?.let(entities::get)
            val invalid = handle.dimension != dimension || handle.options.entity != null && entity == null
            val position = entity?.position() ?: handle.options.position ?: Vec3.ZERO
            val gain = if (handle.options.category == SoundSource.MASTER) 1f else mc.options.getSoundSourceVolume(handle.options.category)
            // Channel and lease state belong to the sound executor; never mutate them from the tick thread.
            MinecraftAudioBackend.submit {
                try {
                    if (handle.closed) Unit else if (invalid) handle.closeNow() else handle.tick(pause, position, gain)
                } catch (failure: Throwable) { handle.fail(failure) }
            }
        }
    }
    @JvmStatic fun disconnect() {
        handles.values.toList().forEach { it.close() }
        MinecraftAudioBackend.submit { AudioCache.destroy() }
    }
    /** Called before Minecraft clears its channels for a resource/device reload. */
    @JvmStatic fun beforeSoundReload() {
        if (handles.isEmpty()) return
        MinecraftAudioBackend.submit { handles.values.forEach { it.invalidateChannel() } }.join()
    }

    internal class LocalAudioHandle(override val id: UUID, val options: AudioOptions,
        private val basePitch: Float, private val baseVolume: Float, val dimension: String?, private val owner: String?) : AudioHandle {
        @Volatile override var snapshot = AudioSnapshot(AudioState.PREPARING, Duration.ZERO, null, options.playbackRate, options.volume, options.loop)
            private set
        var lease: AudioCache.Lease? = null
        var record: ManagedResources.Record? = null
        var closed = false
            private set
        private var pcm: PcmFile? = null
        private var channel: ChannelAccess.ChannelHandle? = null
        private var stream: PcmStream? = null
        private var creating = false
        private var epoch = 0L
        private var manualPause = false
        private var detached = false
        private var desiredPlay = true
        private var pendingPosition = Duration.ZERO
        private var fade: Fade? = null
        private var lastTick = System.nanoTime()
        private data class Fade(val from: Float, val to: Float, val duration: Long, var elapsed: Long = 0)
        fun prepared(value: PcmFile) {
            pcm = value
            snapshot = snapshot.copy(duration = (value.frames.toDouble() / value.sampleRate).seconds)
            pendingPosition = pendingPosition.coerceAtMost(snapshot.duration!!)
            snapshot = snapshot.copy(position = pendingPosition, state = if (!desiredPlay) AudioState.STOPPED else AudioState.PAUSED)
        }
        private fun mutate(action: () -> Unit) = MinecraftAudioBackend.submit {
            check(!closed && snapshot.state != AudioState.FAILED) { "Audio handle is closed or failed" }
            action()
        }
        override fun pause() = mutate { manualPause = true; channel?.execute { it.pause() }; if (pcm != null) snapshot = snapshot.copy(state = AudioState.PAUSED) }
        override fun resume() = mutate {
            manualPause = false; desiredPlay = true
            if (snapshot.state == AudioState.ENDED) seekNow(Duration.ZERO)
        }
        override fun seek(position: Duration): CompletableFuture<Unit> {
            require(position.isFinite() && position >= Duration.ZERO)
            return mutate { seekNow(position) }
        }
        private fun seekNow(position: Duration) {
            pendingPosition = snapshot.duration?.let { position.coerceAtMost(it) } ?: position
            snapshot = snapshot.copy(position = pendingPosition)
            releaseChannel()
            // Positioning a loaded clip is an explicit request to hear it again.
            if (snapshot.state != AudioState.STOPPED) desiredPlay = true
        }
        override fun stop() = mutate {
            desiredPlay = false; manualPause = false; fade = null
            seekNow(Duration.ZERO)
            desiredPlay = false
            snapshot = snapshot.copy(state = AudioState.STOPPED)
        }
        override fun setPlaybackRate(rate: Float): CompletableFuture<Unit> {
            requireRate(rate)
            return mutate { snapshot = snapshot.copy(playbackRate = rate); channel?.execute { it.setPitch(basePitch * rate) } }
        }
        override fun setVolume(volume: Float): CompletableFuture<Unit> {
            require(volume.isFinite() && volume in 0f..1f)
            return mutate { fade = null; snapshot = snapshot.copy(volume = volume) }
        }
        override fun setLoop(loop: Boolean) = mutate {
            // Recreate the queue at the actual media position, discarding previously buffered loop content.
            snapshot = snapshot.copy(loop = loop)
            seekNow(snapshot.position)
        }
        override fun fadeTo(volume: Float, duration: Duration): CompletableFuture<Unit> {
            require(volume.isFinite() && volume in 0f..1f && duration.isFinite() && duration >= Duration.ZERO)
            return mutate {
                if (duration == Duration.ZERO) { fade = null; snapshot = snapshot.copy(volume = volume) }
                else fade = Fade(snapshot.volume, volume, duration.inWholeNanoseconds)
            }
        }
        override fun refresh() = MinecraftAudioBackend.submit { snapshot }
        fun managedPause(value: Boolean) = MinecraftAudioBackend.submit {
            detached = value
            if (value) channel?.execute { it.pause() }
        }
        fun tick(paused: Boolean, position: Vec3, gain: Float) {
            val now = System.nanoTime()
            val delta = (now - lastTick).coerceAtLeast(0)
            lastTick = now
            if (closed || snapshot.state == AudioState.FAILED || pcm == null) return
            val pause = paused || manualPause || detached || ManagedResources.isPaused(owner)
            if (desiredPlay && !pause) fade?.let { f ->
                f.elapsed = (f.elapsed + delta).coerceAtMost(f.duration)
                snapshot = snapshot.copy(volume = f.from + (f.to - f.from) * (f.elapsed.toDouble() / f.duration).toFloat())
                if (f.elapsed == f.duration) fade = null
            }
            val current = channel
            if (current != null && current.isStopped) {
                val previous = stream
                if (previous != null && !snapshot.loop && previous.eof) {
                    ended()
                    return
                }
                // Minecraft clears channels when resources/device change. Resume from the last observed frame.
                releaseChannel()
            }
            if (!desiredPlay) return
            if (channel == null && !creating && !pause) {
                val file = pcm!!
                val start = (snapshot.position.inWholeNanoseconds / 1e9 * file.sampleRate).toLong().coerceIn(0, file.frames)
                if (start == file.frames && !snapshot.loop) { ended(); return }
                creating = true
                val generation = epoch
                MinecraftAudioBackend.create().whenComplete { handle, failure -> MinecraftAudioBackend.submit {
                    creating = false
                    if (generation != epoch || closed) { handle?.execute { it.stop() }; return@submit }
                    if (failure != null) { fail(failure); return@submit }
                    if (handle == null) return@submit
                    channel = handle
                    val pcmStream = PcmStream(file, if (start == file.frames) 0 else start, snapshot.loop)
                    stream = pcmStream
                    handle.execute { native ->
                        if (channel !== handle || closed) return@execute
                        try {
                            native.attachBufferStream(pcmStream)
                            native.setPitch(basePitch * snapshot.playbackRate)
                            native.setVolume(baseVolume * snapshot.volume * gain)
                            native.setRelative(!options.spatial)
                            native.setSelfPosition(position)
                            if (options.spatial) native.linearAttenuation(options.range) else native.disableAttenuation()
                            native.play()
                            if (manualPause || detached || paused || ManagedResources.isPaused(owner)) native.pause()
                        } catch (error: Throwable) { fail(error) }
                    }
                } }
            }
            val updating = channel
            updating?.execute { native ->
                if (closed || channel !== updating) return@execute
                if (native.stopped()) {
                    if (!snapshot.loop) ended()
                    return@execute
                }
                if (pause) native.pause() else native.unpause()
                native.setPitch(basePitch * snapshot.playbackRate)
                native.setVolume(baseVolume * snapshot.volume * gain)
                native.setSelfPosition(position)
                stream?.let { active ->
                    active.observeQueue(MinecraftAudioBackend.queued(native))
                    val frames = active.position(MinecraftAudioBackend.offset(native))
                    snapshot = snapshot.copy(position = (frames.toDouble() / active.pcm.sampleRate).seconds,
                        state = if (pause) AudioState.PAUSED else AudioState.PLAYING)
                }
            }
        }
        private fun ended() {
            desiredPlay = false
            snapshot = snapshot.copy(state = AudioState.ENDED, position = snapshot.duration ?: Duration.ZERO)
            releaseChannel()
        }
        private fun releaseChannel() {
            epoch++
            // ChannelAccess owns release/removal; releasing a handle ourselves leaves a null channel in its set.
            channel?.execute { it.stop() }
            channel = null
            stream?.let { runCatching { it.close() } }
            stream = null
        }
        fun invalidateChannel() { releaseChannel() }
        fun fail(failure: Throwable) {
            if (closed) return
            snapshot = snapshot.copy(state = AudioState.FAILED, error = (failure.cause ?: failure).message ?: failure.javaClass.simpleName)
            releaseChannel()
            lease?.close(); lease = null
            // A failed instance can never be commanded again; stop occupying the instance budget
            // and release the pack generation it was holding.
            handles.remove(id, this)
            record?.let { record = null; ManagedResources.release(it) }
        }
        override fun close() { MinecraftAudioBackend.submit { closeNow() } }
        fun closeNow() {
            if (closed) return
            closed = true
            releaseChannel()
            lease?.close(); lease = null
            snapshot = snapshot.copy(state = AudioState.CLOSED)
            handles.remove(id, this)
            record?.let { record = null; ManagedResources.release(it) }
        }
    }
}
