package top.katton.network

import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import java.util.UUID
import kotlin.test.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import top.katton.api.scene.SceneContext

class ClientScenePacketTest {
    @Test
    fun `start and stop roundtrip`() {
        val id = UUID.randomUUID()
        listOf(
                ClientScenePacket(id),
                ClientScenePacket(
                    id,
                    ClientScenePacket.Start(
                        "demo:scene",
                        "revision-1",
                        "minecraft:overworld",
                        SceneContext(Vec3(1.0, 2.0, 3.0), UUID.randomUUID(), 42),
                    ),
                ),
            )
            .forEach { packet ->
                val buffer = FriendlyByteBuf(Unpooled.buffer())
                try {
                    ClientScenePacket.write(buffer, packet)
                    assertEquals(packet, ClientScenePacket.read(buffer))
                    assertEquals(0, buffer.readableBytes())
                } finally {
                    buffer.release()
                }
            }
    }

    @Test
    fun `invalid origin is rejected before allocating context`() {
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeBoolean(true)
            buffer.writeUtf("demo:scene")
            buffer.writeUtf("1")
            buffer.writeUtf("minecraft:overworld")
            buffer.writeDouble(Double.NaN)
            buffer.writeDouble(0.0)
            buffer.writeDouble(0.0)
            assertFailsWith<DecoderException> { ClientScenePacket.read(buffer) }
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `duplicate start stop before start and bounded history`() {
        val gate = SceneRequestGate(2)
        val stopped = UUID.randomUUID()
        assertTrue(gate.mark(stopped))
        assertFalse(gate.mark(stopped))
        assertTrue(gate.mark(UUID.randomUUID()))
        assertTrue(gate.mark(UUID.randomUUID()))
        assertTrue(gate.mark(stopped))
        gate.clear()
        assertTrue(gate.mark(stopped))
    }

    @Test
    fun `decoded messages respect revision dimension target and stop ordering`() {
        val gate = SceneRequestGate()
        val target = UUID.randomUUID()
        fun start(
            revision: String = "new",
            dimension: String = "minecraft:overworld",
            entity: UUID? = target,
            id: UUID = UUID.randomUUID(),
        ) =
            ClientScenePacket(
                id,
                ClientScenePacket.Start(
                    "demo:scene",
                    revision,
                    dimension,
                    SceneContext(target = entity),
                ),
            )
        fun accept(packet: ClientScenePacket): Boolean {
            val buffer = FriendlyByteBuf(Unpooled.buffer())
            return try {
                ClientScenePacket.write(buffer, packet)
                gate.accept(
                    ClientScenePacket.read(buffer),
                    "minecraft:overworld",
                    { "new" },
                    { it == target },
                )
            } finally {
                buffer.release()
            }
        }
        assertFalse(accept(start(revision = "old")))
        assertFalse(accept(start(dimension = "minecraft:the_nether")))
        assertFalse(accept(start(entity = UUID.randomUUID())))
        val valid = start()
        assertTrue(accept(valid))
        assertFalse(accept(valid))
        val stopped = start()
        assertTrue(accept(ClientScenePacket(stopped.instanceId)))
        assertFalse(accept(stopped))
        assertTrue(accept(ClientScenePacket(valid.instanceId)))
    }
}
