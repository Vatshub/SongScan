package com.example.analyzer

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AudioSynthesizer {

    /**
     * Synthesizes a real playable 16-bit PCM Stereo WAV file in the app cache
     * matching the given BPM, Key frequency, and section layout.
     */
    fun synthesizeSong(
        context: Context,
        fileName: String,
        durationSeconds: Int,
        bpm: Double,
        baseFreq: Double,
        genre: String
    ): File {
        val file = File(context.cacheDir, "$fileName.wav")
        if (file.exists() && file.length() > 44) {
            return file
        }

        val sampleRate = 22050
        val numSamples = sampleRate * durationSeconds
        val numChannels = 2
        val bytesPerSample = 2
        val dataSize = numSamples * numChannels * bytesPerSample

        val fos = FileOutputStream(file)
        writeWavHeader(fos, sampleRate, numChannels, bytesPerSample, dataSize)

        val beatDurationSec = 60.0 / bpm
        val samplesPerBeat = (sampleRate * beatDurationSec).toInt()

        val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        // Musical scale factors (Minor or Major based on genre)
        val chordFreqs = if (genre.contains("Jazz") || genre.contains("Lo-Fi")) {
            listOf(baseFreq, baseFreq * 1.2, baseFreq * 1.5, baseFreq * 1.8) // Minor 7th
        } else {
            listOf(baseFreq, baseFreq * 1.25, baseFreq * 1.5, baseFreq * 2.0) // Major
        }

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val currentBeat = (i / samplesPerBeat)
            val beatPhase = (i % samplesPerBeat).toDouble() / samplesPerBeat

            // Song Structure Phase:
            // 0..15% = Intro, 15..45% = Verse, 45..75% = Chorus, 75..90% = Bridge, 90..100% = Outro
            val songProgress = t / durationSeconds
            val sectionVolume = when {
                songProgress < 0.15 -> 0.4
                songProgress < 0.45 -> 0.7
                songProgress < 0.75 -> 1.0 // Peak Chorus
                songProgress < 0.90 -> 0.5 // Bridge breakdown
                else -> (1.0 - (songProgress - 0.90) / 0.10).coerceIn(0.1, 0.7) // Outro fade
            }

            // 1. Drum Elements:
            // Kick on beat 0 and 2
            var drumSample = 0.0
            if (currentBeat % 2 == 0) {
                val kickDecay = exp(-beatPhase * 16.0)
                val kickFreq = 120.0 * (1.0 - beatPhase * 0.7)
                drumSample += sin(2 * PI * kickFreq * beatPhase * beatDurationSec) * kickDecay * 0.5
            }
            // Snare on beat 1 and 3 (if not in intro)
            if (songProgress >= 0.15 && currentBeat % 2 == 1) {
                val snareDecay = exp(-beatPhase * 12.0)
                val noise = ((i * 1103515245 + 12345) % 65536) / 32768.0 - 1.0
                drumSample += noise * snareDecay * 0.25
            }
            // Hi-hat on 8th notes
            val halfBeatPhase = (beatPhase * 2.0) % 1.0
            val hatDecay = exp(-halfBeatPhase * 24.0)
            val hatNoise = ((i * 1664525 + 1013904223) % 65536) / 32768.0 - 1.0
            drumSample += hatNoise * hatDecay * 0.12

            // 2. Bassline
            val bassFreq = baseFreq / 2.0
            val bassPulse = exp(-beatPhase * 4.0)
            val bassSample = sin(2 * PI * bassFreq * t) * bassPulse * 0.3

            // 3. Synth / Chords / Lead Melody
            val chordIndex = (currentBeat / 4) % chordFreqs.size
            val leadFreq = chordFreqs[chordIndex]
            val chordSample = (sin(2 * PI * leadFreq * t) + 0.5 * sin(2 * PI * leadFreq * 1.5 * t)) * 0.2

            // Combined Mix
            val rawMix = (drumSample + bassSample + chordSample) * sectionVolume
            val clamped = (rawMix * 22000.0).toInt().coerceIn(-32767, 32767).toShort()

            if (!buffer.hasRemaining()) {
                fos.write(buffer.array())
                buffer.clear()
            }
            // Stereo channels: Left & Right
            buffer.putShort(clamped)
            buffer.putShort(clamped)
        }

        if (buffer.position() > 0) {
            fos.write(buffer.array(), 0, buffer.position())
        }

        fos.flush()
        fos.close()
        return file
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bytesPerSample: Int,
        dataSize: Int
    ) {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * bytesPerSample
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bytesPerSample).toByte() // BlockAlign
        header[33] = 0
        header[34] = (bytesPerSample * 8).toByte() // BitsPerSample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
