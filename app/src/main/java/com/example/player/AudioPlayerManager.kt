package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.model.SongSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _loopingSection = MutableStateFlow<SongSection?>(null)
    val loopingSection: StateFlow<SongSection?> = _loopingSection.asStateFlow()

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    _currentPositionMs.value = pos

                    // Check looping section boundary
                    val section = _loopingSection.value
                    if (section != null && pos >= section.endMs) {
                        player.seekTo(section.startMs.toInt())
                        _currentPositionMs.value = section.startMs
                    }
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    init {
        handler.post(progressRunnable)
    }

    fun loadAndPlay(uri: Uri, startMs: Long = 0L) {
        releasePlayer()
        try {
            val player = MediaPlayer()
            player.setDataSource(context, uri)
            player.prepare()
            _durationMs.value = player.duration.toLong()
            if (startMs > 0L) {
                player.seekTo(startMs.toInt())
                _currentPositionMs.value = startMs
            }
            player.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = 0L
            }
            player.start()
            mediaPlayer = player
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to load audio from URI $uri", e)
        }
    }

    fun loadAndPlayFile(file: File, startMs: Long = 0L) {
        releasePlayer()
        try {
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.prepare()
            _durationMs.value = player.duration.toLong()
            if (startMs > 0L) {
                player.seekTo(startMs.toInt())
                _currentPositionMs.value = startMs
            }
            player.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = 0L
            }
            player.start()
            mediaPlayer = player
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to load audio from file $file", e)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        }
    }

    fun jumpToSection(section: SongSection, loop: Boolean = false) {
        seekTo(section.startMs)
        if (loop) {
            _loopingSection.value = section
        }
        if (_isPlaying.value == false) {
            mediaPlayer?.start()
            _isPlaying.value = true
        }
    }

    fun toggleLoopSection(section: SongSection) {
        if (_loopingSection.value == section) {
            _loopingSection.value = null
        } else {
            _loopingSection.value = section
            seekTo(section.startMs)
            if (_isPlaying.value == false) {
                mediaPlayer?.start()
                _isPlaying.value = true
            }
        }
    }

    fun clearLoop() {
        _loopingSection.value = null
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        releasePlayer()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
    }
}
