package top.katton.client.audio

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import net.minecraft.client.sounds.JOrbisAudioStream
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

internal data class PcmFile(val path: Path, val sampleRate: Int, val channels: Int, val frames: Long) {
    val bytes get() = frames * channels * 2
}

/** Content sniffing is independent of the filename. All backends output signed little-endian PCM16. */
internal object AudioDecoders {
    const val MAX_PCM_BYTES = 512L * 1024 * 1024
    fun decode(bytes: ByteArray, output: Path, mono: Boolean, reserve: (Int) -> Unit = {}): PcmFile {
        PcmWriter(output, mono, reserve).use { writer ->
            when {
                bytes.startsWithAscii("RIFF") && bytes.size >= 12 && bytes.copyOfRange(8, 12).startsWithAscii("WAVE") -> wave(bytes, writer)
                bytes.startsWithAscii("fLaC") -> flac(bytes, writer)
                bytes.startsWithAscii("OggS") -> vorbis(bytes, writer)
                bytes.startsWithAscii("ID3") || (bytes.size >= 2 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 224 == 224) -> mp3(bytes, writer)
                else -> error("Unsupported audio encoding; expected PCM WAV, MP3, Ogg Vorbis or FLAC")
            }
            return writer.result()
        }
    }
    private fun ByteArray.startsWithAscii(value: String) = size >= value.length && value.indices.all { this[it].toInt() == value[it].code }

    private fun wave(bytes: ByteArray, writer: PcmWriter) {
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.format.encoding == AudioFormat.Encoding.PCM_SIGNED || input.format.encoding == AudioFormat.Encoding.PCM_UNSIGNED ||
                input.format.encoding == AudioFormat.Encoding.PCM_FLOAT) { "WAV must contain PCM audio" }
            val target = AudioFormat(input.format.sampleRate, 16, input.format.channels, true, false)
            AudioSystem.getAudioInputStream(target, input).use { pcm ->
                writer.format(target.sampleRate.toInt(), target.channels)
                val buffer = ByteArray(8192 * target.frameSize)
                while (true) {
                    val count = pcm.read(buffer)
                    if (count < 0) break
                    writer.pcm16(buffer, count)
                }
            }
        }
    }
    private fun mp3(bytes: ByteArray, writer: PcmWriter) {
        val stream = Bitstream(ByteArrayInputStream(bytes))
        try {
            val decoder = Decoder()
            while (true) {
                val header = stream.readFrame() ?: break
                try {
                    val samples = decoder.decodeFrame(header, stream) as SampleBuffer
                    writer.format(samples.sampleFrequency, samples.channelCount)
                    writer.samples(samples.buffer, samples.bufferLength)
                } finally { stream.closeFrame() }
            }
        } finally { stream.close() }
    }
    private fun vorbis(bytes: ByteArray, writer: PcmWriter) {
        JOrbisAudioStream(ByteArrayInputStream(bytes)).use { stream ->
            val format = stream.format
            writer.format(format.sampleRate.toInt(), format.channels)
            // FloatSampleSource converts Vorbis floats to native-endian PCM16.
            while (true) {
                val buffer = stream.read(32768).order(ByteOrder.nativeOrder())
                if (!buffer.hasRemaining()) break
                val samples = ShortArray(buffer.remaining() / 2) { buffer.short }
                writer.samples(samples, samples.size)
            }
        }
    }
    private fun flac(bytes: ByteArray, writer: PcmWriter) {
        val decoder = FLACDecoder(ByteArrayInputStream(bytes))
        var bits = 0
        decoder.addPCMProcessor(object : PCMProcessor {
            override fun processStreamInfo(info: StreamInfo) {
                bits = info.bitsPerSample
                require(bits in listOf(8, 16, 24)) { "FLAC supports 8, 16 and 24 bit samples" }
                writer.format(info.sampleRate, info.channels)
            }
            override fun processPCM(data: ByteData) {
                val width = bits / 8
                check(width > 0)
                val pcm = data.data
                val samples = ShortArray(data.len / width) { index ->
                    val at = index * width
                    var value = 0
                    for (b in 0 until width) value = value or ((pcm[at + b].toInt() and 255) shl (8 * b))
                    value = (value shl (32 - bits)) shr (32 - bits)
                    (if (bits > 16) value shr (bits - 16) else value shl (16 - bits)).toShort()
                }
                writer.samples(samples, samples.size)
            }
        })
        decoder.decode()
    }
    private class PcmWriter(private val path: Path, private val mono: Boolean, private val reserve: (Int) -> Unit) : Closeable {
        private val output = BufferedOutputStream(Files.newOutputStream(path))
        private var rate = 0
        private var inputChannels = 0
        private var frames = 0L
        fun format(rate: Int, channels: Int) {
            require(rate in 8000..192000 && channels in 1..2) { "Audio must be mono/stereo at 8..192 kHz" }
            require(this.rate == 0 || this.rate == rate && inputChannels == channels) { "Changing audio format is unsupported" }
            this.rate = rate
            inputChannels = channels
        }
        fun pcm16(bytes: ByteArray, count: Int) {
            require(count % (inputChannels * 2) == 0) { "Truncated PCM frame" }
            samples(ShortArray(count / 2) { ((bytes[it * 2].toInt() and 255) or (bytes[it * 2 + 1].toInt() shl 8)).toShort() }, count / 2)
        }
        fun samples(samples: ShortArray, count: Int) {
            check(!Thread.currentThread().isInterrupted) { "Audio preparation cancelled" }
            require(inputChannels > 0 && count % inputChannels == 0)
            require(count <= samples.size) { "Truncated sample buffer" }
            val outputChannels = if (mono) 1 else inputChannels
            val inputFrames = count / inputChannels
            // One output frame per input frame: mono writes frames*2 bytes, stereo frames*channels*2.
            val size = inputFrames * outputChannels * 2
            require((frames + inputFrames) * outputChannels * 2 <= MAX_PCM_BYTES) { "Decoded audio exceeds 512 MiB" }
            reserve(size)
            fun write(sample: Int) { output.write(sample and 255); output.write(sample shr 8 and 255) }
            if (mono && inputChannels == 2) {
                // One output frame per input frame: average each left/right pair.
                for (i in 0 until count step 2) write((samples[i].toInt() + samples[i + 1].toInt()) / 2)
            } else for (i in 0 until count) write(samples[i].toInt())
            frames += inputFrames
        }
        fun result(): PcmFile {
            require(frames > 0) { "Audio contains no decodable samples" }
            output.flush()
            return PcmFile(path, rate, if (mono) 1 else inputChannels, frames)
        }
        override fun close() = output.close()
    }
}
