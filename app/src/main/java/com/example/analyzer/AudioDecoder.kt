package com.example.analyzer

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes an audio file with Android's built-in decoders and returns the whole song
 * as mono floats at [SongScanEngine.SR] Hz, ready for [SongScanEngine.analyze].
 */
object AudioDecoder {

    class Decoded(val samples: FloatArray, val durationSec: Double, val trimmed: Boolean)

    /** Longest stretch analysed, to keep memory and time reasonable on phones. */
    private const val MAX_SECONDS = 12 * 60

    fun decode(context: Context, uri: Uri, onProgress: (Float) -> Unit = {}): Decoded {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; format = f; break }
            }
            require(track >= 0 && format != null) { "No audio track found in this file." }
            extractor.selectTrack(track)

            var sampleRate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var isFloat = false
            val totalUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val expectedSec = if (totalUs > 0) minOf(totalUs / 1_000_000.0, MAX_SECONDS.toDouble()) else 60.0
            val out = SongScanResampler(SongScanEngine.SR, ((expectedSec + 2) * SongScanEngine.SR).toInt())
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var trimmed = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > MAX_SECONDS * 1_000_000L) {
                            if (size >= 0) trimmed = true
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            if (totalUs > 0) onProgress((extractor.sampleTime.toDouble() / totalUs).toFloat().coerceIn(0f, 1f))
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = f.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val ordered = buf.order(ByteOrder.nativeOrder())
                            out.setInputRate(sampleRate)
                            if (isFloat) {
                                val fb = ordered.asFloatBuffer()
                                val frames = fb.remaining() / channels
                                for (i in 0 until frames) {
                                    var s = 0f
                                    for (c in 0 until channels) s += fb.get()
                                    out.push(s / channels)
                                }
                            } else {
                                val sb = ordered.asShortBuffer()
                                val frames = sb.remaining() / channels
                                for (i in 0 until frames) {
                                    var s = 0f
                                    for (c in 0 until channels) s += sb.get() / 32768f
                                    out.push(s / channels)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            val samples = out.result()
            require(samples.size > SongScanEngine.SR) { "This audio is too short to analyse." }
            return Decoded(samples, samples.size.toDouble() / SongScanEngine.SR, trimmed)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, fallback: Int) =
        if (containsKey(key)) getInteger(key) else fallback
}

/**
 * Streaming resampler: averages input samples into each output slot (a simple
 * anti-alias filter), which is plenty for tempo, key and chord analysis.
 */
internal class SongScanResampler(private val outRate: Int, initialCapacity: Int = outRate * 60) {
    private var ratio = 1.0
    private var acc = 0.0
    private var count = 0
    private var pos = 0.0
    private var data = FloatArray(maxOf(initialCapacity, outRate))
    private var size = 0

    fun setInputRate(inRate: Int) { ratio = inRate.toDouble() / outRate }

    fun push(v: Float) {
        acc += v; count++
        pos += 1.0
        if (pos >= ratio) {
            pos -= ratio
            if (size == data.size) data = data.copyOf(data.size + data.size / 2)
            data[size++] = (acc / count).toFloat()
            acc = 0.0; count = 0
        }
    }

    fun result(): FloatArray = if (size == data.size) data else data.copyOf(size)
}
