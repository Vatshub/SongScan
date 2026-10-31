package com.example.analyzer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AudioBpmDetector {
    private const val TAG = "AudioBpmDetector"

    /**
     * Detects song BPM with high accuracy by:
     * 1. Inspecting ID3 TBPM metadata frame (ground-truth studio BPM if tagged).
     * 2. High-resolution PCM audio decoding with MediaCodec at 200 Hz frame rate.
     * 3. Harmonic comb-filter resonator autocorrelation that rejects cross-rhythms (e.g. 4/5 or 3/2).
     */
    fun detectBpmFromAudio(context: Context, uri: Uri, totalDurationMs: Long): Double {
        // Step 1: Check for embedded ID3 TBPM frame
        val taggedBpm = readId3BpmTag(context, uri)
        if (taggedBpm != null && taggedBpm in 50.0..220.0) {
            Log.d(TAG, "Found embedded ID3 TBPM tag: $taggedBpm")
            return (taggedBpm * 10.0).roundToInt() / 10.0
        }

        // Step 2: High-resolution acoustic decoding
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return 120.0
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Seek ~20% into the song to capture the main rhythmic groove
            val seekTargetUs = if (totalDurationMs > 25000L) {
                (totalDurationMs * 0.20 * 1000).toLong()
            } else 0L
            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var actualSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var actualChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // High resolution frame size: ~220 samples at 44.1kHz (~200 frames per second = 5ms resolution)
            val hopSize = (actualSampleRate / 200).coerceAtLeast(128)
            val energyList = mutableListOf<Float>()
            var currentHopSum = 0.0
            var currentHopCount = 0

            val maxDurationSec = 22
            val maxSamplesToAnalyze = actualSampleRate * maxDurationSec
            var samplesCollected = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            var iterations = 0

            while (samplesCollected < maxSamplesToAnalyze && !isEOS && iterations < 3000) {
                iterations++
                val inIndex = codec.dequeueInputBuffer(5000L)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val timeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                } else if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        val numShorts = shortBuffer.remaining()

                        var s = 0
                        val step = actualChannels.coerceAtLeast(1)
                        while (s < numShorts && samplesCollected < maxSamplesToAnalyze) {
                            val sample = shortBuffer.get(s).toInt()
                            currentHopSum += (sample.toDouble() * sample.toDouble())
                            currentHopCount++

                            if (currentHopCount >= hopSize) {
                                val rms = sqrt(currentHopSum / currentHopCount).toFloat()
                                energyList.add(rms)
                                currentHopSum = 0.0
                                currentHopCount = 0
                            }

                            s += step
                            samplesCollected++
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            if (energyList.size > 300) {
                val frameRate = actualSampleRate.toDouble() / hopSize
                val detected = computeHarmonicCombBpm(energyList, frameRate)
                if (detected in 55.0..215.0) {
                    return detected
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio decoding BPM error", e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }

        return 120.0
    }

    /**
     * Reads ID3v2 TBPM (Beats Per Minute) frame directly from the audio header
     */
    internal fun readId3BpmTag(context: Context, uri: Uri): Double? {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(10)
                val readHeader = stream.read(header)
                if (readHeader == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    // ID3v2 header found, read up to 64KB to search for TBPM frame
                    val buffer = ByteArray(65536)
                    val readData = stream.read(buffer)
                    if (readData > 10) {
                        for (i in 0 until readData - 8) {
                            if (buffer[i] == 'T'.code.toByte() &&
                                buffer[i + 1] == 'B'.code.toByte() &&
                                buffer[i + 2] == 'P'.code.toByte() &&
                                buffer[i + 3] == 'M'.code.toByte()
                            ) {
                                // Frame size is at i+4..i+7 (syncsafe or int)
                                var frameSize = (buffer[i + 7].toInt() and 0xFF)
                                if (frameSize in 1..15) {
                                    val start = i + 10 // skip frame header + encoding byte
                                    val strBytes = buffer.copyOfRange(start, (start + frameSize).coerceAtMost(readData))
                                    val text = String(strBytes, Charsets.ISO_8859_1).filter { it.isDigit() || it == '.' }
                                    val bpmVal = text.toDoubleOrNull()
                                    if (bpmVal != null && bpmVal in 40.0..240.0) {
                                        return bpmVal
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Computes onset novelty curve and applies a harmonic comb-filter autocorrelation.
     * Harmonic comb summation reinforces true beat multiples (T, 2T) and suppresses
     * fractional cross-rhythms (e.g. 4/5, 3/4).
     */
    private fun computeHarmonicCombBpm(energy: List<Float>, frameRate: Double): Double {
        val n = energy.size
        // 1. Half-wave rectified onset novelty function
        val onsets = DoubleArray(n)
        for (i in 1 until n) {
            val diff = (energy[i] - energy[i - 1]).toDouble()
            onsets[i] = if (diff > 0) diff else 0.0
        }

        // Adaptive moving threshold subtraction to isolate sharp rhythmic beats
        val window = (frameRate * 0.12).toInt().coerceAtLeast(3)
        val novelty = DoubleArray(n)
        for (i in 0 until n) {
            val start = (i - window).coerceAtLeast(0)
            val end = (i + window).coerceAtMost(n)
            var sum = 0.0
            for (j in start until end) sum += onsets[j]
            val mean = sum / (end - start)
            novelty[i] = max(0.0, onsets[i] - mean)
        }

        // 2. Precompute standard Autocorrelation array
        val maxLag = (frameRate * 60.0 / 60.0).roundToInt().coerceAtMost(n / 2) // down to 60 BPM
        val ac = DoubleArray(maxLag + 1)

        for (lag in 1..maxLag) {
            var sum = 0.0
            val count = n - lag
            for (i in 0 until count) {
                sum += novelty[i] * novelty[i + lag]
            }
            ac[lag] = sum / count
        }

        // 3. Harmonic Comb Resonator across candidate BPMs (65.0 to 185.0 in 0.5 BPM steps)
        var bestBpm = 120.0
        var maxHarmonicScore = -1.0

        var testBpm = 65.0
        while (testBpm <= 185.0) {
            val lag = (frameRate * 60.0 / testBpm).roundToInt()
            if (lag in 1 until maxLag) {
                val r0 = ac[lag]
                // 2x lag (the 2-beat measure harmonic, strongly present in 4/4 time)
                val lag2 = lag * 2
                val r2 = if (lag2 <= maxLag) ac[lag2] else 0.0
                // Half lag (8th note harmonic)
                val lagHalf = lag / 2
                val rHalf = if (lagHalf in 1..maxLag) ac[lagHalf] else 0.0

                // Comb sum: fundamental + 0.65 * double_lag + 0.35 * half_lag
                var score = r0 + 0.65 * r2 + 0.35 * rHalf

                // Slight musical prior favoring common radio / dance tempo range (90 - 135)
                val prior = if (testBpm in 90.0..135.0) 1.10 else 1.0
                score *= prior

                if (score > maxHarmonicScore) {
                    maxHarmonicScore = score
                    bestBpm = testBpm
                }
            }
            testBpm += 0.5
        }

        return (bestBpm * 10.0).roundToInt() / 10.0
    }
}
