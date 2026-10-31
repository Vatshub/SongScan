package com.example.analyzer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

data class RawAudioMeta(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRate: Int,
    val mimeType: String,
    val format: String,
    val fileSizeFormatted: String
)

object AudioMetadataExtractor {
    private const val TAG = "AudioMetadataExtractor"

    fun extract(context: Context, uri: Uri): RawAudioMeta {
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var album = ""
        var durationMs = 0L
        var bitrate = 320
        var sampleRate = 44100
        var mimeType = "audio/mpeg"
        var fileSizeStr = "Unknown"

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rawBitrate = bitrateStr?.toIntOrNull() ?: 320000
            bitrate = (rawBitrate / 1000).coerceIn(64, 1411)

            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mimeStr.isNullOrBlank()) {
                mimeType = mimeStr
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                srStr?.toIntOrNull()?.let { sampleRate = it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from uri $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Fallback for Title from ContentResolver if ID3 title tag was missing
        if (title.isBlank()) {
            title = queryFileName(context, uri) ?: "Audio Track"
            // Strip file extension
            val dot = title.lastIndexOf('.')
            if (dot > 0) {
                title = title.substring(0, dot)
            }
        }
        if (artist.isBlank()) {
            artist = "Original Artist"
        }
        if (durationMs <= 0L) {
            durationMs = 180000L // 3 minutes default fallback if unreadable
        }

        val format = when {
            mimeType.contains("mp3", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "M4A"
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            else -> "AUDIO"
        }

        return RawAudioMeta(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            bitrateKbps = bitrate,
            sampleRate = sampleRate,
            mimeType = mimeType,
            format = format,
            fileSizeFormatted = fileSizeStr
        )
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
