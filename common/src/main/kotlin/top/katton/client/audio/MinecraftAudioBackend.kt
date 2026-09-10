package top.katton.client.audio

import com.mojang.blaze3d.audio.Channel
import com.mojang.blaze3d.audio.Library
import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.ChannelAccess
import net.minecraft.client.sounds.SoundEngine
import net.minecraft.client.sounds.SoundManager
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.sound.sampled.AudioFormat

/** All native channel operations run on Minecraft's existing sound executor. */
internal object MinecraftAudioBackend {
    private val engineField = SoundManager::class.java.getDeclaredField("soundEngine").apply { isAccessible = true }
    private val executorField = SoundEngine::class.java.getDeclaredField("executor").apply { isAccessible = true }
    private val accessField = SoundEngine::class.java.getDeclaredField("channelAccess").apply { isAccessible = true }
    private val loadedField = SoundEngine::class.java.getDeclaredField("loaded").apply { isAccessible = true }
    private val sourceField = Channel::class.java.getDeclaredField("source").apply { isAccessible = true }
    private fun engine() = engineField.get(Minecraft.getInstance().soundManager) as SoundEngine
    fun <T> submit(action: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(action, executorField.get(engine()) as Executor)
    fun create(): CompletableFuture<ChannelAccess.ChannelHandle?> {
        val engine = engine()
        if (!loadedField.getBoolean(engine)) return CompletableFuture.completedFuture(null)
        return (accessField.get(engine) as ChannelAccess).createHandle(Library.Pool.STREAMING)
    }
    fun offset(channel: Channel): Long = AL10.alGetSourcei(sourceField.getInt(channel), AL11.AL_SAMPLE_OFFSET).toLong().coerceAtLeast(0)
    fun queued(channel: Channel): Int = AL10.alGetSourcei(sourceField.getInt(channel), AL10.AL_BUFFERS_QUEUED)
}

/** Observe native queue length, rather than inferring consumption from decoder read-ahead. */
internal class PcmStream(val pcm: PcmFile, val startFrame: Long, var loop: Boolean) : AudioStream {
    private val input = FileChannel.open(pcm.path, StandardOpenOption.READ)
    private val frameSize = pcm.channels * 2
    private val queuedFrames = ArrayDeque<Long>()
    var eof = false
        private set
    var consumedFrames = 0L
        private set
    init { input.position(startFrame * frameSize) }
    override fun getFormat() = AudioFormat(pcm.sampleRate.toFloat(), 16, pcm.channels, true, false)
    override fun read(size: Int): ByteBuffer {
        val result = ByteBuffer.allocateDirect(size - size % frameSize).order(ByteOrder.LITTLE_ENDIAN)
        while (result.hasRemaining()) {
            val count = input.read(result)
            if (count < 0) {
                if (!loop) { eof = true; break }
                input.position(0)
            } else if (count == 0) break
        }
        result.flip()
        // Every read produces exactly one queued OpenAL buffer, including a short final one.
        queuedFrames.addLast(result.remaining().toLong() / frameSize)
        return result
    }
    /** OpenAL owns the buffers we already handed over; a larger count only means unobserved ones. */
    fun observeQueue(count: Int) {
        require(count >= 0)
        while (queuedFrames.size > count) consumedFrames += queuedFrames.removeFirst()
    }
    fun position(offset: Long): Long {
        val absolute = startFrame + consumedFrames + offset
        return if (loop) absolute % pcm.frames else absolute.coerceAtMost(pcm.frames)
    }
    override fun close() = input.close()
}
