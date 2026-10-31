package com.example.analyzer

import android.content.Context
import android.net.Uri
import com.example.data.model.SectionType
import com.example.data.model.SongAnalysis
import com.example.data.model.SongSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Analyses a song from its actual audio: tempo, key, chords, sections, energy and
 * arrangement notes all come from [SongScanEngine]. Nothing is guessed from the title.
 */
object AudioFeatureAnalyzer {

    suspend fun analyzeAudio(
        context: Context,
        uri: Uri,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }
    ): SongAnalysis = withContext(Dispatchers.Default) {
        onProgress("Reading song details...", 0.05f)
        val meta = AudioMetadataExtractor.extract(context, uri)

        onProgress("Decoding audio...", 0.10f)
        val decoded = AudioDecoder.decode(context, uri) { p -> onProgress("Decoding audio...", 0.10f + 0.35f * p) }

        onProgress("Finding the beat, key, chords and sections...", 0.45f)
        val r = SongScanEngine.analyze(decoded.samples, curvePoints = 72) { p ->
            onProgress("Finding the beat, key, chords and sections...", 0.45f + 0.5f * p)
        }

        // If the beat is unclear and the file carries a BPM tag from its producer, prefer the tag.
        val taggedBpm = if (r.tempo.confidence < 0.45) AudioBpmDetector.readId3BpmTag(context, uri) else null
        val bpm = taggedBpm?.takeIf { it in 50.0..220.0 } ?: r.tempo.bpm.toDouble()

        val durationMs = if (meta.durationMs > 0) meta.durationMs else (decoded.durationSec * 1000).toLong()
        val sections = buildSections(r, durationMs)
        val minor = r.key.minor
        val feel = when {
            bpm < 90 -> "Slow"
            bpm < 115 -> "Mid-tempo"
            bpm < 135 -> "Upbeat"
            else -> "Fast"
        }
        val beatWord = when {
            r.tempo.confidence >= 0.7 -> "Steady beat"
            r.tempo.confidence >= 0.45 -> "Clear beat"
            else -> "Loose or no steady beat"
        }
        val crest = r.peakDb - r.avgDb
        val dynamics = when {
            crest > 14 -> "Wide dynamics"
            crest < 9 -> "Loud, compressed master"
            else -> "Moderate dynamics"
        }
        val energyScore = r.energyCurve.average().toFloat().coerceIn(0f, 1f)

        val chordPatterns = buildList {
            r.structure?.let { st ->
                if (st.mainLoop.isNotEmpty()) add(st.mainLoop.joinToString(" - "))
                st.sections.forEach { s -> if (s.loop.isNotEmpty()) add(s.loop.joinToString(" - ")) }
            }
        }.distinct().take(6)

        val notes = buildList {
            if (decoded.trimmed) add("Only the first 12 minutes were analysed.")
            if (r.tempo.confidence < 0.45) add("No steady beat was found, so the tempo, sections and chords are rough.")
            if (taggedBpm != null && bpm == taggedBpm) add("The tempo comes from the BPM tag stored in the file.")
            r.structure?.notes?.let { addAll(it) }
            if (r.key.confidence < 0.5) add("The key is a close call. Runner-up: ${r.key.runnerUp}.")
        }

        onProgress("Done", 1.0f)

        SongAnalysis(
            title = meta.title,
            artist = meta.artist,
            durationMs = durationMs,
            bpm = bpm,
            key = r.key.name,
            camelotCode = r.key.camelot,
            genre = "$feel, ${if (minor) "minor" else "major"} key",
            subgenres = listOf("${bpm.toInt()} BPM", r.key.camelot, beatWord, dynamics),
            mood = beatWord,
            energyScore = energyScore,
            danceability = r.tempo.confidence.toFloat(), // shown in the app as "Beat clarity"
            timeSignature = "4/4",
            sections = sections,
            waveformPoints = r.energyCurve.toList(),
            chords = chordPatterns,
            sourceUri = uri.toString(),
            fileFormat = meta.format,
            sampleRate = meta.sampleRate,
            bitrateKbps = meta.bitrateKbps,
            analyzedTimestamp = System.currentTimeMillis(),
            arrangementNotes = notes.joinToString("\n") { "• $it" }
        )
    }

    private fun buildSections(r: SongScanEngine.Result, durationMs: Long): List<SongSection> {
        val st = r.structure
        if (st == null || st.sections.isEmpty()) {
            return listOf(
                SongSection(
                    name = "Whole track", type = SectionType.PART, startMs = 0, endMs = durationMs,
                    energy = 0.6f, description = "No clear section changes were found."
                )
            )
        }
        val minDb = st.sections.minOf { it.db }
        val maxDb = st.sections.maxOf { it.db }
        return st.sections.map { s ->
            val energy = if (maxDb - minDb < 0.5) 0.7f else (0.15 + 0.85 * (s.db - minDb) / (maxDb - minDb)).toFloat()
            val level = when {
                energy > 0.75f -> "High"
                energy > 0.4f -> "Medium"
                else -> "Low"
            }
            val type = when (s.label) {
                "Intro" -> SectionType.INTRO
                "Verse" -> SectionType.VERSE
                "Chorus" -> SectionType.CHORUS
                "Bridge" -> SectionType.BRIDGE
                "Break" -> SectionType.BREAK
                "Outro" -> SectionType.OUTRO
                else -> SectionType.PART
            }
            val guess = if (type != SectionType.PART) " The name is a best guess from what repeats and how loud it is." else ""
            SongSection(
                name = s.name,
                type = type,
                startMs = (s.startSec * 1000).toLong(),
                endMs = (s.endSec * 1000).toLong().coerceAtMost(durationMs),
                energy = energy.coerceIn(0.05f, 1f),
                description = "Bars ${s.firstBar} to ${s.firstBar + s.bars - 1} (${s.bars} bars). $level energy.$guess",
                chordProgression = if (s.loop.isEmpty()) "" else s.loop.joinToString(" - ") + if (s.loopTimes > 1) " (x${s.loopTimes})" else ""
            )
        }
    }

    /** Writes the SongScan test song (known layout: 124 BPM, A minor, 8 sections) as a WAV file. */
    fun writeTestSong(context: Context): File {
        val file = File(context.cacheDir, "songscan_test_song.wav")
        if (file.exists() && file.length() > 44) return file
        val x = SongScanEngine.synthTestSong()
        val sr = SongScanEngine.SR
        val dataSize = x.size * 2
        FileOutputStream(file).use { fos ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + dataSize); h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
            h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(sr); h.putInt(sr * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(dataSize)
            fos.write(h.array())
            val b = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (v in x) b.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            fos.write(b.array())
        }
        return file
    }

    /** Demo shown in the app: a song built with a known structure, so you can check the analysis. */
    fun getPresetDemos(): List<PresetTrack> = listOf(
        PresetTrack(
            id = "songscan_test_song",
            title = "SongScan test song",
            artist = "Sound of Drip",
            durationMs = 100_600L,
            bpm = 124.0,
            key = "A Minor",
            baseFreq = 220.0,
            genre = "Test song",
            description = "Made at 124 BPM in A minor with an intro, two verses, three choruses, a break and an outro."
        )
    )

    data class PresetTrack(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val bpm: Double,
        val key: String,
        val baseFreq: Double,
        val genre: String,
        val description: String
    )
}
