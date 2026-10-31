package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SongAnalysis
import com.example.data.model.SongSection

@Entity(tableName = "song_analyses")
data class SongAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
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
    val energyScore: Float,
    val danceability: Float,
    val timeSignature: String,
    val sections: List<SongSection>,
    val waveformPoints: List<Float>,
    val chords: List<String>,
    val sourceUri: String?,
    val fileFormat: String,
    val sampleRate: Int,
    val bitrateKbps: Int,
    val analyzedTimestamp: Long,
    val arrangementNotes: String
) {
    fun toDomain(): SongAnalysis {
        return SongAnalysis(
            id = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            bpm = bpm,
            key = key,
            camelotCode = camelotCode,
            genre = genre,
            subgenres = subgenres,
            mood = mood,
            energyScore = energyScore,
            danceability = danceability,
            timeSignature = timeSignature,
            sections = sections,
            waveformPoints = waveformPoints,
            chords = chords,
            sourceUri = sourceUri,
            fileFormat = fileFormat,
            sampleRate = sampleRate,
            bitrateKbps = bitrateKbps,
            analyzedTimestamp = analyzedTimestamp,
            arrangementNotes = arrangementNotes
        )
    }

    companion object {
        fun fromDomain(domain: SongAnalysis): SongAnalysisEntity {
            return SongAnalysisEntity(
                id = domain.id,
                title = domain.title,
                artist = domain.artist,
                durationMs = domain.durationMs,
                bpm = domain.bpm,
                key = domain.key,
                camelotCode = domain.camelotCode,
                genre = domain.genre,
                subgenres = domain.subgenres,
                mood = domain.mood,
                energyScore = domain.energyScore,
                danceability = domain.danceability,
                timeSignature = domain.timeSignature,
                sections = domain.sections,
                waveformPoints = domain.waveformPoints,
                chords = domain.chords,
                sourceUri = domain.sourceUri,
                fileFormat = domain.fileFormat,
                sampleRate = domain.sampleRate,
                bitrateKbps = domain.bitrateKbps,
                analyzedTimestamp = domain.analyzedTimestamp,
                arrangementNotes = domain.arrangementNotes
            )
        }
    }
}
