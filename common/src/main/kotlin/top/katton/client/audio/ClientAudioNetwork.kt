package top.katton.client.audio

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import top.katton.api.audio.AudioHandle
import top.katton.api.audio.AudioSource
import top.katton.network.AudioPacket
import top.katton.network.AudioWire
import top.katton.pack.ServerPackCacheManager
import top.katton.pack.ScriptPackManager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.time.Duration.Companion.nanoseconds

/** Connection-local remote instances never expose local script-created handles. */
object ClientAudioNetwork {
    private val remote = mutableMapOf<UUID, AudioHandle>()
    private val sequence = linkedMapOf<UUID, Long>()
    private data class Pending(val packet: AudioPacket, val source: AudioSource, val reply: Consumer<AudioPacket>, val expires: Long)
    private val pending = mutableMapOf<UUID, Pending>()
    private var generation = 0L
    @JvmStatic fun receive(packet: AudioPacket, reply: Consumer<AudioPacket>) {
        val mc = Minecraft.getInstance()
        check(mc.isSameThread)
        if (packet.op == AudioPacket.Op.HELLO) { reply.accept(packet.copy(body = "{}")); return }
        if (packet.sequence <= sequence.getOrDefault(packet.id, -1L)) return
        sequence[packet.id] = packet.sequence
        if (sequence.size > 4096) sequence.remove(sequence.keys.first())
        val connection = generation
        fun respond(response: AudioPacket) { mc.execute { if (generation == connection) reply.accept(response) } }
        try {
            if (packet.op == AudioPacket.Op.CLOSE) {
                pending.remove(packet.id)
                remote.remove(packet.id)?.close()
                respond(packet.copy(op = AudioPacket.Op.STATUS, body = AudioWire.state(top.katton.api.audio.AudioSnapshot(
                    top.katton.api.audio.AudioState.CLOSED, kotlin.time.Duration.ZERO, null, 1f, 1f, false))))
                return
            }
            if (packet.op == AudioPacket.Op.START) {
                require(remote.size + pending.size < 64 && packet.id !in remote && packet.id !in pending)
                val json = packet.json()
                require(json["dimension"].asString == mc.level?.dimension()?.identifier()?.toString()) { "Audio dimension mismatch" }
                val source = AudioWire.source(json)
                if (source.kind == AudioSource.Kind.PACK_FILE && !hasPack(source)) {
                    // The source is parsed once here: a parked packet must not fail on a later tick.
                    pending[packet.id] = Pending(packet, source, reply, System.nanoTime() + 115_000_000_000L)
                    return
                }
                remote[packet.id] = ClientAudioManager.play(source, AudioWire.options(json), packet.id)
            }
            val handle = remote[packet.id] ?: error("Unknown audio instance")
            val json = packet.json()
            val operation: CompletableFuture<*> = when (packet.op) {
                AudioPacket.Op.START, AudioPacket.Op.QUERY -> handle.refresh()
                AudioPacket.Op.PAUSE -> handle.pause()
                AudioPacket.Op.RESUME -> handle.resume()
                AudioPacket.Op.STOP -> handle.stop()
                AudioPacket.Op.SEEK -> handle.seek(json["value"].asLong.nanoseconds)
                AudioPacket.Op.RATE -> handle.setPlaybackRate(json["value"].asFloat)
                AudioPacket.Op.VOLUME -> handle.setVolume(json["value"].asFloat)
                AudioPacket.Op.LOOP -> handle.setLoop(json["value"].asBoolean)
                AudioPacket.Op.FADE -> handle.fadeTo(json["value"].asFloat, json["duration"].asLong.nanoseconds)
                else -> error("Invalid client audio operation")
            }
            operation.whenComplete { _, error ->
                // Runs on the sound executor, outside the try block above; a throw here would be lost.
                runCatching {
                    if (error != null) respond(error(packet, error))
                    else respond(packet.copy(op = AudioPacket.Op.STATUS, body = AudioWire.state(handle.snapshot)))
                }
            }
        } catch (failure: Throwable) { respond(error(packet, failure)) }
    }
    private var packScanNanos = 0L
    private var packScan: List<top.katton.pack.ScriptPack> = emptyList()
    /** Snapshot rescans read pack directories, so at most one per second is enough. */
    private fun currentPacks(): List<top.katton.pack.ScriptPack> {
        val now = System.nanoTime()
        if (now - packScanNanos < 1_000_000_000L) return packScan
        packScanNanos = now
        packScan = ServerPackCacheManager.collectExecutablePacks() +
            if (Minecraft.getInstance().hasSingleplayerServer()) ScriptPackManager.collectExecutablePacks() else emptyList()
        return packScan
    }
    private fun hasPack(source: AudioSource): Boolean =
        currentPacks().any { it.syncId == source.packId && it.hash == source.revision }
    private fun error(packet: AudioPacket, failure: Throwable) = packet.copy(op = AudioPacket.Op.ERROR,
        body = JsonObject().apply { addProperty("error", ((failure.cause ?: failure).message ?: "Audio operation failed").take(512)) }.toString())
    @JvmStatic fun tick() {
        // Instances the client closed on its own must not hold the 64-instance budget forever.
        remote.entries.removeIf { (_, handle) ->
            val state = handle.snapshot.state
            state == top.katton.api.audio.AudioState.CLOSED || state == top.katton.api.audio.AudioState.FAILED
        }
        pending.values.toList().forEach { value ->
            try {
                if (System.nanoTime() >= value.expires) {
                    pending.remove(value.packet.id)
                    value.reply.accept(error(value.packet, IllegalStateException("Audio pack revision did not become available")))
                } else if (hasPack(value.source)) {
                    pending.remove(value.packet.id)
                    sequence.remove(value.packet.id)
                    receive(value.packet, value.reply)
                }
            } catch (failure: Throwable) {
                pending.remove(value.packet.id)
                runCatching { value.reply.accept(error(value.packet, failure)) }
            }
        }
    }
    @JvmStatic fun disconnect() {
        generation++
        remote.values.forEach { it.close() }
        remote.clear(); pending.clear(); sequence.clear()
        packScanNanos = 0L; packScan = emptyList()
    }
}
