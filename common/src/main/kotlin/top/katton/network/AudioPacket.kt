package top.katton.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import top.katton.api.audio.*
import java.util.UUID
import kotlin.time.Duration.Companion.nanoseconds

/** Versioned, bounded control messages. Audio bytes travel only through authenticated pack synchronization. */
data class AudioPacket(val id: UUID, val sequence: Long, val op: Op, val body: String = "{}") : CustomPacketPayload {
    enum class Op { HELLO, START, PAUSE, RESUME, SEEK, STOP, RATE, VOLUME, LOOP, FADE, QUERY, CLOSE, STATUS, ERROR }
    init { require(sequence >= 0 && body.length <= 8192) }
    fun json(): JsonObject = JsonParser.parseString(body).asJsonObject
    override fun type() = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<AudioPacket>(Identifier.fromNamespaceAndPath("katton", "audio_v1"))
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AudioPacket> = StreamCodec.of(
            { buf, packet -> buf.writeUUID(packet.id); buf.writeLong(packet.sequence); buf.writeEnum(packet.op); buf.writeUtf(packet.body, 8192) },
            { buf -> AudioPacket(buf.readUUID(), buf.readLong(), buf.readEnum(Op::class.java), buf.readUtf(8192)) })
    }
}

internal object AudioWire {
    fun start(source: AudioSource, options: AudioOptions, dimension: String) = JsonObject().apply {
        addProperty("kind", source.kind.name); addProperty("path", source.path)
        addProperty("pack", source.packId); addProperty("revision", source.revision)
        addProperty("dimension", dimension); addProperty("volume", options.volume)
        addProperty("rate", options.playbackRate); addProperty("loop", options.loop)
        addProperty("category", options.category.name); addProperty("range", options.range)
        options.entity?.let { addProperty("entity", it.toString()) }
        options.position?.let { addProperty("x", it.x); addProperty("y", it.y); addProperty("z", it.z) }
    }.toString()
    fun source(json: JsonObject) = AudioSource(AudioSource.Kind.valueOf(json["kind"].asString), json["path"].asString,
        json["pack"].asString, json["revision"].asString)
    fun options(json: JsonObject) = AudioOptions(json["volume"].asFloat, json["rate"].asFloat, json["loop"].asBoolean,
        SoundSource.valueOf(json["category"].asString), if (json.has("x")) Vec3(json["x"].asDouble, json["y"].asDouble, json["z"].asDouble) else null,
        json["entity"]?.asString?.let(UUID::fromString), json["range"].asFloat)
    fun state(snapshot: AudioSnapshot) = JsonObject().apply {
        addProperty("state", snapshot.state.name); addProperty("position", snapshot.position.inWholeNanoseconds)
        snapshot.duration?.let { addProperty("duration", it.inWholeNanoseconds) }
        addProperty("rate", snapshot.playbackRate); addProperty("volume", snapshot.volume); addProperty("loop", snapshot.loop)
        snapshot.error?.let { addProperty("error", it.take(1024)) }
    }.toString()
    fun state(json: JsonObject): AudioSnapshot {
        val position = json["position"].asLong.also { require(it >= 0) }.nanoseconds
        val duration = json["duration"]?.asLong?.also { require(it >= 0) }?.nanoseconds
        val rate = json["rate"].asFloat.also(::requireRate)
        val volume = json["volume"].asFloat.also { require(it.isFinite() && it in 0f..1f) }
        return AudioSnapshot(AudioState.valueOf(json["state"].asString), position, duration, rate, volume, json["loop"].asBoolean, json["error"]?.asString)
    }
    fun number(value: Number) = JsonObject().apply { addProperty("value", value) }.toString()
}
