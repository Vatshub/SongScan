package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class SectionType(val displayName: String, val defaultColorHex: Long) {
    INTRO("Intro", 0xFF06B6D4),        // Cyan
    VERSE("Verse", 0xFF3B82F6),        // Blue
    PRE_CHORUS("Pre-Chorus", 0xFFF59E0B), // Amber
    CHORUS("Chorus", 0xFFEC4899),      // Pink/Magenta (Primary hook)
    BRIDGE("Bridge", 0xFF10B981),      // Emerald green
    SOLO("Solo / Drop", 0xFF8B5CF6),   // Purple
    OUTRO("Outro", 0xFF64748B),        // Slate
    BREAK("Break", 0xFF14B8A6),        // Teal (quieter middle section)
    PART("Section", 0xFF94A3B8);       // Grey (section without a clear name)

    fun toComposeColor(): Color = Color(defaultColorHex)
}

data class SongSection(
    val name: String,
    val type: SectionType,
    val startMs: Long,
    val endMs: Long,
    val energy: Float, // 0.0 to 1.0
    val description: String,
    val chordProgression: String = ""
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    
    fun formatRange(): String {
        return "${formatTime(startMs)} - ${formatTime(endMs)}"
    }

    companion object {
        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}

data class CamelotInfo(
    val code: String,             // e.g. "11A"
    val standardKey: String,       // e.g. "F# Minor"
    val relativeKey: String,       // e.g. "A Major (11B)"
    val compatibleEnergyBoost: String, // e.g. "12A"
    val compatibleEnergyDrop: String,  // e.g. "10A"
    val compatibleKeys: List<String>
)

data class SongAnalysis(
    val id: Long = 0,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val bpm: Double,
    val key: String,
    val camelotCode: String,
    val genre: String,
    val subgenres: List<String>,
    val mood: String,
    val energyScore: Float, // 0.0 to 1.0
    val danceability: Float,
    val timeSignature: String = "4/4",
    val sections: List<SongSection>,
    val waveformPoints: List<Float>, // 60-100 normalized points
    val chords: List<String>,
    val sourceUri: String? = null,
    val fileFormat: String = "MP3",
    val sampleRate: Int = 44100,
    val bitrateKbps: Int = 320,
    val analyzedTimestamp: Long = System.currentTimeMillis(),
    val arrangementNotes: String = ""
) {
    val durationFormatted: String
        get() {
            val totalSec = (durationMs / 1000).toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }

    val totalBars: Int
        get() {
            // Bars calculation @ 4/4 time signature:
            // seconds per beat = 60 / bpm
            // seconds per bar = 4 * (60 / bpm) = 240 / bpm
            val secPerBar = if (bpm > 0) 240.0 / bpm else 2.0
            return ((durationMs / 1000.0) / secPerBar).toInt().coerceAtLeast(1)
        }
}
