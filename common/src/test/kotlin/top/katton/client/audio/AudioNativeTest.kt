package top.katton.client.audio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.AL11.AL_SEC_OFFSET
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in device probe: output is muted, but a real OpenAL device advances the sample clock. */
@EnabledIfSystemProperty(named = "katton.audio.native", matches = "true")
class AudioNativeTest {
    @Test fun `native rate pause resume seek and stop`() {
        val device = alcOpenDevice(null as ByteBuffer?)
        check(device != 0L) { "No OpenAL output device available" }
        val context = alcCreateContext(device, null as java.nio.IntBuffer?)
        check(context != 0L)
        try {
            check(alcMakeContextCurrent(context))
            AL.createCapabilities(ALC.createCapabilities(device))
            val source = alGenSources()
            val buffer = alGenBuffers()
            try {
                val pcm = ByteBuffer.allocateDirect(44100 * 2 * 8).order(ByteOrder.nativeOrder())
                while (pcm.hasRemaining()) pcm.putShort(0)
                pcm.flip()
                alBufferData(buffer, AL_FORMAT_MONO16, pcm, 44100)
                alSourcei(source, AL_BUFFER, buffer)
                alSourcef(source, AL_GAIN, 0f)
                alSourcef(source, AL_PITCH, 2f)
                alSourcePlay(source)
                Thread.sleep(300)
                val fast = alGetSourcef(source, AL_SEC_OFFSET)
                assertTrue(fast in 0.35f..0.9f, "2x media clock: $fast")
                alSourcePause(source)
                val paused = alGetSourcef(source, AL_SEC_OFFSET)
                Thread.sleep(150)
                assertEquals(paused, alGetSourcef(source, AL_SEC_OFFSET), 0.001f)
                alSourcef(source, AL_PITCH, 0.5f)
                alSourcePlay(source)
                Thread.sleep(300)
                val delta = alGetSourcef(source, AL_SEC_OFFSET) - paused
                assertTrue(delta in 0.05f..0.3f, "0.5x media clock: $delta")
                alSourcePause(source)
                alSourcef(source, AL_SEC_OFFSET, 3f)
                assertTrue(abs(alGetSourcef(source, AL_SEC_OFFSET) - 3f) < 0.01f)
                alSourceStop(source)
                assertEquals(AL_STOPPED, alGetSourcei(source, AL_SOURCE_STATE))
                assertEquals(AL_NO_ERROR, alGetError())
            } finally { alDeleteSources(source); alDeleteBuffers(buffer) }
        } finally {
            alcMakeContextCurrent(0)
            alcDestroyContext(context)
            alcCloseDevice(device)
        }
    }
}
