package top.katton.network

import java.util.UUID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import top.katton.api.scene.SceneContext

/** Play-phase presentation commands. No client classes may be referenced here. */
data class ClientScenePacket(val instanceId: UUID, val start: Start? = null) : CustomPacketPayload {
    data class Start(
        val definitionId: String,
        val revision: String,
        val dimension: String,
        val context: SceneContext,
    )

    companion object {
        @JvmField
        val TYPE =
            CustomPacketPayload.Type<ClientScenePacket>(
                Identifier.fromNamespaceAndPath("katton", "client_scene")
            )
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ClientScenePacket> =
            StreamCodec.of(::write, ::read)

        private fun valid(start: Start): Boolean =
            Identifier.tryParse(start.definitionId) != null &&
                start.definitionId.length <= ClientPacketLimits.MAX_RESOURCE_ID_CHARS &&
                Identifier.tryParse(start.dimension) != null &&
                start.dimension.length <= ClientPacketLimits.MAX_RESOURCE_ID_CHARS &&
                start.revision.length in 1..128

        fun write(buf: FriendlyByteBuf, packet: ClientScenePacket) {
            packet.start?.let {
                ClientPacketLimits.requireEncoding(valid(it)) { "Invalid scene start" }
            }
            buf.writeUUID(packet.instanceId)
            buf.writeBoolean(packet.start != null)
            packet.start?.let { s ->
                buf.writeUtf(s.definitionId, ClientPacketLimits.MAX_RESOURCE_ID_CHARS)
                buf.writeUtf(s.revision, 128)
                buf.writeUtf(s.dimension, ClientPacketLimits.MAX_RESOURCE_ID_CHARS)
                buf.writeDouble(s.context.origin.x)
                buf.writeDouble(s.context.origin.y)
                buf.writeDouble(s.context.origin.z)
                buf.writeNullable(s.context.target) { b, id -> b.writeUUID(id) }
                buf.writeLong(s.context.seed)
            }
        }

        fun read(buf: FriendlyByteBuf): ClientScenePacket {
            val id = buf.readUUID()
            if (!buf.readBoolean()) return ClientScenePacket(id)
            val definition = buf.readUtf(ClientPacketLimits.MAX_RESOURCE_ID_CHARS)
            val revision = buf.readUtf(128)
            val dimension = buf.readUtf(ClientPacketLimits.MAX_RESOURCE_ID_CHARS)
            val x = buf.readDouble()
            val y = buf.readDouble()
            val z = buf.readDouble()
            ClientPacketLimits.requireDecoded(
                listOf(x, y, z).all { it.isFinite() && kotlin.math.abs(it) <= 6e7 }
            ) {
                "Invalid scene origin"
            }
            val target = buf.readNullable { it.readUUID() }
            val seed = buf.readLong()
            val start =
                Start(definition, revision, dimension, SceneContext(Vec3(x, y, z), target, seed))
            ClientPacketLimits.requireDecoded(valid(start)) { "Invalid scene start" }
            return ClientScenePacket(id, start)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Bounded tombstones prevent duplicate starts and stop-before-start races on a connection. */
class SceneRequestGate(private val capacity: Int = 4096) {
    init {
        require(capacity > 0)
    }

    private val seen = linkedSetOf<UUID>()

    fun accept(
        packet: ClientScenePacket,
        dimension: String?,
        revision: (String) -> String?,
        targetExists: (UUID) -> Boolean,
    ): Boolean {
        val first = mark(packet.instanceId)
        val start = packet.start ?: return true
        return first &&
            start.dimension == dimension &&
            revision(start.definitionId) == start.revision &&
            (start.context.target == null || targetExists(start.context.target))
    }

    fun mark(id: UUID): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > capacity) seen.remove(seen.first())
        return true
    }

    fun clear() = seen.clear()
}
