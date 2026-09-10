package top.katton.client.audio

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import org.junit.jupiter.api.io.TempDir
import top.katton.api.audio.*
import top.katton.network.AudioPacket
import top.katton.network.AudioWire
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackAudioFiles
import top.katton.pack.ScriptPackSnapshots
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackContentFile
import top.katton.pack.RemoteScriptSignatureVerifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class AudioTest {
    @TempDir lateinit var temporary: Path

    @Test fun `four formats decode by content without filenames`() {
        for (format in listOf("wav", "mp3", "ogg", "flac", "flac24", "wav24")) {
            val bytes = javaClass.getResourceAsStream("/audio/sine.$format")!!.use { it.readBytes() }
            val pcm = AudioDecoders.decode(bytes, temporary.resolve(format), false)
            assertEquals(44100, pcm.sampleRate, format)
            assertEquals(2, pcm.channels, format)
            assertTrue(abs(pcm.frames.toDouble() / pcm.sampleRate - 2.0) < 0.15, "$format duration")
            assertEquals(pcm.bytes, Files.size(pcm.path))
            val decoded = ByteBuffer.wrap(Files.readAllBytes(pcm.path)).order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            while (decoded.hasRemaining()) peak = maxOf(peak, abs(decoded.short.toInt()))
            assertTrue(peak in 1000..12000, "$format PCM amplitude: $peak")
        }
    }
    @Test fun `spatial downmix has half the bytes and identical duration`() {
        val bytes = javaClass.getResourceAsStream("/audio/sine.wav")!!.use { it.readBytes() }
        val stereo = AudioDecoders.decode(bytes, temporary.resolve("stereo"), false)
        val mono = AudioDecoders.decode(bytes, temporary.resolve("mono"), true)
        assertEquals(1, mono.channels)
        assertEquals(stereo.frames, mono.frames)
        assertEquals(stereo.bytes / 2, mono.bytes)
    }
    @Test fun `spatial downmix averages both channels per output frame`() {
        // A left-only tone is silent when downmixed and a right-only tone is silence today:
        // the mono file must carry one averaged sample per stereo frame, not a decimated channel.
        val bytes = stereoPcm16(listOf(intArrayOf(1000, -1000), intArrayOf(2000, -2000), intArrayOf(-3000, 3000)))
        val stereo = AudioDecoders.decode(bytes, temporary.resolve("pair-stereo"), false)
        val mono = AudioDecoders.decode(bytes, temporary.resolve("pair-mono"), true)
        assertEquals(3, stereo.frames)
        assertEquals(3, mono.frames)
        // Three stereo frames are 12 bytes; the downmixed file holds one sample per frame.
        assertEquals(12, Files.size(stereo.path))
        assertEquals(6, Files.size(mono.path))
        val samples = ByteBuffer.wrap(Files.readAllBytes(mono.path)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, samples.short.toInt())
        assertEquals(0, samples.short.toInt())
        assertEquals(0, samples.short.toInt())
        val leftOnly = AudioDecoders.decode(stereoPcm16(listOf(intArrayOf(800, 0), intArrayOf(400, 0))),
            temporary.resolve("left-only"), true)
        val leftSamples = ByteBuffer.wrap(Files.readAllBytes(leftOnly.path)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(400, leftSamples.short.toInt())
        assertEquals(200, leftSamples.short.toInt())
    }
    private fun stereoPcm16(frames: List<IntArray>): ByteArray {
        val data = ByteBuffer.allocate(frames.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        frames.forEach { frame -> frame.forEach { data.putShort(it.toShort()) } }
        val bytes = data.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + bytes.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(44100).putInt(44100 * 4).putShort(4).putShort(16)
        header.put("data".toByteArray()).putInt(bytes.size)
        return header.array() + bytes
    }
    @Test fun `bad encodings and interrupted budget fail preparation`() {
        assertFails { AudioDecoders.decode(byteArrayOf(1, 2, 3), temporary.resolve("bad"), false) }
        assertFails { AudioDecoders.decode("fLaC".toByteArray(), temporary.resolve("truncated"), false) }
        val bytes = javaClass.getResourceAsStream("/audio/sine.wav")!!.use { it.readBytes() }
        assertFails { AudioDecoders.decode(bytes, temporary.resolve("limited"), false) { error("budget exceeded") } }
    }
    @Test fun `rate limits reject nonfinite zero and out of range values`() {
        listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f).forEach { assertEquals(it, AudioOptions(playbackRate = it).playbackRate) }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, 0.249f, 4.001f).forEach {
            assertFailsWith<IllegalArgumentException> { AudioOptions(playbackRate = it) }
        }
    }
    @Test fun `stream progress counts consumed frames not read ahead buffers`() {
        val bytes = javaClass.getResourceAsStream("/audio/sine.wav")!!.use { it.readBytes() }
        val file = AudioDecoders.decode(bytes, temporary.resolve("pcm"), false)
        PcmStream(file, 4410, true).use { stream ->
            repeat(4) { assertEquals(17640, stream.read(17640).remaining()) }
            stream.observeQueue(4)
            assertEquals(4410, stream.position(0))
            assertEquals(6615, stream.position(2205))
            stream.read(17640)
            stream.observeQueue(4)
            assertEquals(8820, stream.position(0))
            repeat(30) { stream.read(17640); stream.observeQueue(4) }
            assertTrue(stream.position(100) in 0 until file.frames)
        }
        PcmStream(file, file.frames - 4410, false).use { stream ->
            assertEquals(17640, stream.read(100000).remaining())
            assertEquals(0, stream.read(100000).remaining())
            assertEquals(0, stream.read(100000).remaining())
            stream.observeQueue(0)
            assertEquals(file.frames, stream.position(0))
        }
    }
    @Test fun `wire round trips fractional rate and original media duration`() {
        val state = AudioSnapshot(AudioState.PLAYING, 4.seconds, 60.seconds, 1.5f, 0.6f, true)
        assertEquals(state, AudioWire.state(com.google.gson.JsonParser.parseString(AudioWire.state(state)).asJsonObject))
        val packet = AudioPacket(UUID.randomUUID(), 1, AudioPacket.Op.STATUS, AudioWire.state(state))
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            AudioPacket.STREAM_CODEC.encode(buffer, packet)
            assertEquals(packet, AudioPacket.STREAM_CODEC.decode(buffer))
        } finally { buffer.release() }
        assertFails { AudioPacket(UUID.randomUUID(), -1, AudioPacket.Op.RATE) }
        assertFails { AudioPacket(UUID.randomUUID(), 0, AudioPacket.Op.START, "x".repeat(8193)) }
    }
    @Test fun `arbitrary pack files alter content hash but not code hash`() {
        val root = Files.createDirectory(temporary.resolve("pack"))
        Files.writeString(root.resolve("manifest.json"), """{"id":"audio-test","dependencies":[]}""")
        Files.writeString(root.resolve("main.kt"), "val answer = 42")
        val sound = Files.createDirectories(root.resolve("custom/nested")).resolve("music.blob")
        Files.write(sound, byteArrayOf(1, 2, 3))
        val first = ScriptPackManager.scanPackDirectory(root, ScriptPackScope.WORLD)!!
        assertTrue(first.contentFiles.any { it.relativePath == "custom/nested/music.blob" })
        Files.write(sound, byteArrayOf(4, 5, 6))
        val second = ScriptPackManager.scanPackDirectory(root, ScriptPackScope.WORLD)!!
        assertEquals(first.codeHash, second.codeHash)
        assertNotEquals(first.hash, second.hash)
        assertFalse(RemoteScriptSignatureVerifier.buildSignedPayload(first).contentEquals(RemoteScriptSignatureVerifier.buildSignedPayload(second)))
        assertContentEquals(byteArrayOf(1, 2, 3), ScriptPackAudioFiles.read(first, "custom/nested/music.blob"))
        Files.writeString(root.resolve(".kattonpack.state.json"), "{}")
        val third = ScriptPackManager.scanPackDirectory(root, ScriptPackScope.WORLD)!!
        assertEquals(second.hash, third.hash)
        assertFails { AudioSource(AudioSource.Kind.PACK_FILE, "../music", "world:p", "0".repeat(64)) }
    }
    @Test fun `ZIP and JAR audio reads use accepted bytes`() {
        val manifest = """{"id":"archive-audio","dependencies":[]}"""
        val bytes = byteArrayOf(5, 6, 7)
        val zip = temporary.resolve("pack.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            output.putNextEntry(ZipEntry("manifest.json")); output.write(manifest.toByteArray()); output.closeEntry()
            output.putNextEntry(ZipEntry("nested/audio")); output.write(bytes); output.closeEntry()
        }
        val snapshot = ScriptPackSnapshots.zip(zip).toPack(zip, ScriptPackScope.WORLD, ScriptPackKind.ZIP)
        assertContentEquals(bytes, ScriptPackAudioFiles.read(snapshot, "nested/audio"))
        val jar = snapshot.copy(kind = ScriptPackKind.JAR,
            contentFiles = listOf(ScriptPackContentFile("pack.jar", zip, Files.readAllBytes(zip))))
        Files.write(zip, byteArrayOf(0))
        assertContentEquals(bytes, ScriptPackAudioFiles.read(jar, "nested/audio"))
        assertFails { ScriptPackAudioFiles.read(jar, "missing") }
        assertFails { ScriptPackAudioFiles.read(jar, "../nested/audio") }
    }
}
