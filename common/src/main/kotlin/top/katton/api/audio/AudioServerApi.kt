@file:Suppress("unused")
package top.katton.api.audio

import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerPlayer
import top.katton.engine.ManagedResources
import top.katton.network.AudioPacket
import top.katton.network.AudioWire
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Loader-owned transport. Paper deliberately has no full-player transport. */
object AudioServerTransport {
    @Volatile var send: ((ServerPlayer, AudioPacket) -> Boolean)? = null
    private val handles = ConcurrentHashMap<UUID, RemoteAudioHandle>()
    internal fun register(handle: RemoteAudioHandle) { handles[handle.id] = handle }
    internal fun remove(handle: RemoteAudioHandle) { handles.remove(handle.id, handle) }
    internal fun tick(server: net.minecraft.server.MinecraftServer) {
        // A respawn or dimension change replaces the ServerPlayer instance for the same UUID,
        // so only a missing player is a disconnect; otherwise re-point the handle.
        handles.values.forEach { handle ->
            val current = server.playerList.getPlayer(handle.player.uuid)
            if (current == null) handle.disconnected() else if (current !== handle.player) handle.rebind(current)
        }
    }
    internal fun clear() { handles.values.toList().forEach { it.disconnected() } }
    @JvmStatic fun receive(player: ServerPlayer, packet: AudioPacket) {
        val handle = handles[packet.id] ?: return
        if (handle.player.uuid != player.uuid) return
        handle.receive(packet)
    }
}

/** %en Remote state is client-confirmed; refresh requests a fresh snapshot.
 * %zh 远程状态来自客户端确认；调用 refresh 获取最新进度。 */
class RemoteAudioHandle internal constructor(@Volatile internal var player: ServerPlayer, source: AudioSource, options: AudioOptions) : AudioHandle {
    private companion object { val LOGGER = LogUtils.getLogger() }

    override val id: UUID = UUID.randomUUID()
    @Volatile override var snapshot = AudioSnapshot(AudioState.PREPARING, Duration.ZERO, null, options.playbackRate, options.volume, options.loop)
        private set
    private var sequence = 0L
    private val server = player.level().server
    private val dimension = player.level().dimension().identifier().toString()
    private val activation = CompletableFuture<Unit>()
    @Volatile private var closed = false
    private val pending = ConcurrentHashMap<Long, CompletableFuture<AudioSnapshot>>()
    private var tail: CompletableFuture<*> = CompletableFuture.completedFuture(Unit)
    private var record: ManagedResources.Record? = null
    private var restorePlaying = false
    init {
        AudioServerTransport.register(this)
        record = ManagedResources.record(
            attach = { if (restorePlaying) resume() },
            detach = { restorePlaying = snapshot.state == AudioState.PLAYING || snapshot.state == AudioState.PREPARING; pause() },
            dispose = { close() }, resumed = { activation.complete(Unit) })
        if (!ManagedResources.isPaused(top.katton.util.ScriptExecutionContext.currentScriptOwner())) activation.complete(Unit)
        tail = activation.thenCompose { request(AudioPacket.Op.HELLO) }.thenCompose {
            request(AudioPacket.Op.START, AudioWire.start(source, options, dimension))
        }
        tail.whenComplete { _, error -> if (error != null && !closed) fail(error, terminal = true) }
    }
    @Synchronized private fun request(op: AudioPacket.Op, body: String = "{}"): CompletableFuture<AudioSnapshot> {
        if (closed && op != AudioPacket.Op.CLOSE) return CompletableFuture.failedFuture(IllegalStateException("Audio handle is closed"))
        val seq = ++sequence
        val future = CompletableFuture<AudioSnapshot>()
        pending[seq] = future
        val packet = AudioPacket(id, seq, op, body)
        // All platform transports and player access are confined to the server thread.
        try {
            val target = player
            server.execute {
                try {
                    if (AudioServerTransport.send?.invoke(target, packet) != true)
                        future.completeExceptionally(UnsupportedOperationException("Player has no compatible Katton audio client"))
                } catch (failure: Throwable) { future.completeExceptionally(failure) }
            }
        } catch (failure: Throwable) {
            // A rejected scheduler must not strand the pending entry nor the handle.
            pending.remove(seq)
            future.completeExceptionally(failure)
            return future
        }
        future.orTimeout(if (op == AudioPacket.Op.START) 120 else 10, TimeUnit.SECONDS)
            .whenComplete { _, _ -> pending.remove(seq) }
        return future
    }
    /** A failed request must not poison every later command; only its own result is reported. */
    @Synchronized private fun command(op: AudioPacket.Op, body: String = "{}"): CompletableFuture<Unit> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Audio handle is closed"))
        val head = if (tail.isCompletedExceptionally) CompletableFuture.completedFuture(Unit) else tail
        val next = head.thenCompose { request(op, body) }
        tail = next
        return next.thenApply { Unit }
    }
    internal fun receive(packet: AudioPacket) {
        val future = pending[packet.sequence] ?: return
        try {
            when (packet.op) {
                AudioPacket.Op.HELLO -> future.complete(snapshot)
                AudioPacket.Op.STATUS -> {
                    val value = AudioWire.state(packet.json())
                    // A reply that raced the close callback must not resurrect the handle state.
                    if (closed) future.completeExceptionally(IllegalStateException("Audio handle is closed"))
                    else { snapshot = value; future.complete(value) }
                }
                AudioPacket.Op.ERROR -> {
                    val message = packet.json()["error"].asString
                    fail(IllegalStateException(message))
                    future.completeExceptionally(IllegalStateException(message))
                }
                else -> return
            }
        } catch (error: Throwable) { future.completeExceptionally(error) }
    }
    private fun fail(error: Throwable, terminal: Boolean = false) {
        if (closed) return
        snapshot = snapshot.copy(state = AudioState.FAILED, error = (error.cause ?: error).message)
        // A handle whose initial handshake failed can never be commanded again; a failed
        // control command only reports the failure and keeps the handle usable.
        if (terminal) closeAndRelease()
    }
    /** Re-points the handle after a respawn or dimension change replaced the player instance. */
    internal fun rebind(current: ServerPlayer) { player = current }
    override fun pause() = command(AudioPacket.Op.PAUSE)
    override fun resume() = command(AudioPacket.Op.RESUME)
    override fun stop() = command(AudioPacket.Op.STOP)
    override fun seek(position: Duration): CompletableFuture<Unit> {
        require(position.isFinite() && position >= Duration.ZERO)
        return command(AudioPacket.Op.SEEK, AudioWire.number(position.inWholeNanoseconds))
    }
    override fun setPlaybackRate(rate: Float): CompletableFuture<Unit> {
        requireRate(rate)
        return command(AudioPacket.Op.RATE, AudioWire.number(rate))
    }
    override fun setVolume(volume: Float): CompletableFuture<Unit> {
        require(volume.isFinite() && volume in 0f..1f)
        return command(AudioPacket.Op.VOLUME, AudioWire.number(volume))
    }
    override fun setLoop(loop: Boolean) = command(AudioPacket.Op.LOOP, "{\"value\":$loop}")
    override fun fadeTo(volume: Float, duration: Duration): CompletableFuture<Unit> {
        require(volume.isFinite() && volume in 0f..1f && duration.isFinite() && duration >= Duration.ZERO)
        return command(AudioPacket.Op.FADE, "{\"value\":$volume,\"duration\":${duration.inWholeNanoseconds}}")
    }
    @Synchronized override fun refresh(): CompletableFuture<AudioSnapshot> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Audio handle is closed"))
        val head = if (tail.isCompletedExceptionally) CompletableFuture.completedFuture(Unit) else tail
        val next = head.thenCompose { request(AudioPacket.Op.QUERY) }
        tail = next
        return next
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        activation.completeExceptionally(IllegalStateException("Audio handle closed"))
        try {
            request(AudioPacket.Op.CLOSE)
        } catch (failure: Throwable) {
            LOGGER.warn("Failed to queue the audio close request", failure)
        }
        // Local cleanup is unconditional: a client that never answers must not leak the handle.
        release()
    }
    @Synchronized internal fun disconnected() {
        if (closed) return
        closed = true
        activation.completeExceptionally(IllegalStateException("Player disconnected"))
        pending.values.toList().forEach { it.completeExceptionally(IllegalStateException("Player disconnected")) }
        snapshot = snapshot.copy(state = AudioState.CLOSED)
        release()
    }
    private fun closeAndRelease() {
        closed = true
        snapshot = snapshot.copy(state = AudioState.CLOSED)
        release()
    }
    private fun release() {
        AudioServerTransport.remove(this)
        record?.let { record = null; ManagedResources.release(it) }
    }
}

/** %en Full remote playback requires a compatible Katton mod client.
 * %zh 完整远程播放需要兼容的 Katton 模组客户端，Paper 不提供此能力。 */
fun playPlayerAudio(player: ServerPlayer, source: AudioSource, options: AudioOptions = AudioOptions()): RemoteAudioHandle {
    check(AudioServerTransport.send != null) { "Full remote audio is unavailable on this platform" }
    check(player.level().server.isSameThread) { "Create remote audio on the server thread" }
    return RemoteAudioHandle(player, source, options)
}
