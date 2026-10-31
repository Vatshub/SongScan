package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.analyzer.AudioFeatureAnalyzer
import com.example.analyzer.AudioSynthesizer
import com.example.data.local.AppDatabase
import com.example.data.local.SongAnalysisEntity
import com.example.data.model.SongAnalysis
import com.example.data.model.SongSection
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val dao = database.songAnalysisDao()

    val audioPlayer = AudioPlayerManager(application)

    private val _currentAnalysis = MutableStateFlow<SongAnalysis?>(null)
    val currentAnalysis: StateFlow<SongAnalysis?> = _currentAnalysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStep = MutableStateFlow("Ready")
    val analysisStep: StateFlow<String> = _analysisStep.asStateFlow()

    private val _analysisProgress = MutableStateFlow(0f)
    val analysisProgress: StateFlow<Float> = _analysisProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val analysisHistory: StateFlow<List<SongAnalysis>> = dao.getAllAnalyses()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Pre-load default featured demo track ("Neon Horizon")
        viewModelScope.launch {
            val presets = AudioFeatureAnalyzer.getPresetDemos()
            if (presets.isNotEmpty()) {
                loadPresetDemo(presets.first(), autoPlay = false)
            }
        }
    }

    fun analyzeAudioUri(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            _analysisProgress.value = 0.05f
            _analysisStep.value = "Analyzing audio file..."

            try {
                val analysis = AudioFeatureAnalyzer.analyzeAudio(
                    context = getApplication(),
                    uri = uri
                ) { step, progress ->
                    _analysisStep.value = step
                    _analysisProgress.value = progress
                }

                // Save to Room database
                val entity = SongAnalysisEntity.fromDomain(analysis)
                val newId = withContext(Dispatchers.IO) {
                    dao.insertAnalysis(entity)
                }
                val savedAnalysis = analysis.copy(id = newId)
                _currentAnalysis.value = savedAnalysis

                // Load in player
                audioPlayer.loadAndPlay(uri)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to analyze audio: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeAudioUrl(url: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            _analysisProgress.value = 0.1f
            _analysisStep.value = "Downloading audio from link..."

            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url(url.trim())
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw java.io.IOException("HTTP error code ${response.code}")
                    }

                    val body = response.body ?: throw java.io.IOException("Empty audio response body")
                    val ext = when {
                        url.contains(".wav", ignoreCase = true) -> "wav"
                        url.contains(".m4a", ignoreCase = true) -> "m4a"
                        url.contains(".flac", ignoreCase = true) -> "flac"
                        else -> "mp3"
                    }
                    val file = java.io.File(getApplication<Application>().cacheDir, "imported_song_${System.currentTimeMillis()}.$ext")
                    file.outputStream().use { fos ->
                        body.byteStream().copyTo(fos)
                    }
                    file
                }

                _analysisStep.value = "Running acoustic analysis..."
                _analysisProgress.value = 0.45f

                val uri = Uri.fromFile(tempFile)
                val analysis = AudioFeatureAnalyzer.analyzeAudio(
                    context = getApplication(),
                    uri = uri
                ) { step, progress ->
                    _analysisStep.value = step
                    _analysisProgress.value = progress
                }

                val entity = SongAnalysisEntity.fromDomain(analysis)
                val newId = withContext(Dispatchers.IO) {
                    dao.insertAnalysis(entity)
                }
                _currentAnalysis.value = analysis.copy(id = newId)
                audioPlayer.loadAndPlayFile(tempFile)
            } catch (e: Exception) {
                _errorMessage.value = "Could not load audio from URL: ${e.localizedMessage ?: "Check link and try again"}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun loadPresetDemo(preset: AudioFeatureAnalyzer.PresetTrack, autoPlay: Boolean = true) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisStep.value = "Synthesizing demo track (${preset.title})..."
            _analysisProgress.value = 0.25f

            try {
                // Synthesize actual playable PCM audio WAV in cache
                val wavFile = withContext(Dispatchers.IO) {
                    AudioSynthesizer.synthesizeSong(
                        context = getApplication(),
                        fileName = preset.id,
                        durationSeconds = (preset.durationMs / 1000).toInt(),
                        bpm = preset.bpm,
                        baseFreq = preset.baseFreq,
                        genre = preset.genre
                    )
                }

                _analysisStep.value = "Running acoustic analysis..."
                _analysisProgress.value = 0.65f

                val uri = Uri.fromFile(wavFile)
                val analysis = AudioFeatureAnalyzer.analyzeAudio(
                    context = getApplication(),
                    uri = uri
                ) { step, progress ->
                    _analysisStep.value = step
                    _analysisProgress.value = progress
                }

                // Save to Room if not already saved
                val entity = SongAnalysisEntity.fromDomain(
                    analysis.copy(
                        title = preset.title,
                        artist = preset.artist,
                        bpm = preset.bpm,
                        key = preset.key,
                        genre = preset.genre
                    )
                )
                val id = withContext(Dispatchers.IO) {
                    dao.insertAnalysis(entity)
                }
                val finalAnalysis = entity.toDomain().copy(id = id)
                _currentAnalysis.value = finalAnalysis

                if (autoPlay) {
                    audioPlayer.loadAndPlayFile(wavFile)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load preset: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun updateBpm(newBpm: Double) {
        val current = _currentAnalysis.value ?: return
        val clamped = ((newBpm * 10.0).roundToInt() / 10.0).coerceIn(40.0, 240.0)
        val updated = current.copy(bpm = clamped)
        _currentAnalysis.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateAnalysis(SongAnalysisEntity.fromDomain(updated))
        }
    }

    fun halveBpm() {
        val current = _currentAnalysis.value ?: return
        updateBpm(current.bpm / 2.0)
    }

    fun doubleBpm() {
        val current = _currentAnalysis.value ?: return
        updateBpm(current.bpm * 2.0)
    }

    fun selectHistoryItem(item: SongAnalysis) {
        _currentAnalysis.value = item
        val uriStr = item.sourceUri
        if (!uriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriStr)
                audioPlayer.loadAndPlay(uri)
            } catch (_: Exception) {}
        }
    }

    fun deleteHistoryItem(item: SongAnalysis) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(item.id)
            if (_currentAnalysis.value?.id == item.id) {
                _currentAnalysis.value = null
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
