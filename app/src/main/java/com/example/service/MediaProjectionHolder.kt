package com.example.service

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaProjectionHolder {
    var resultCode: Int = 0
    var resultData: Intent? = null
    var mediaProjection: MediaProjection? = null

    private val _isProjectionActive = MutableStateFlow(false)
    val isProjectionActive: StateFlow<Boolean> = _isProjectionActive.asStateFlow()

    val isAvailable: Boolean
        get() = mediaProjection != null || (resultCode != 0 && resultData != null)

    @Synchronized
    fun obtainMediaProjection(manager: MediaProjectionManager): MediaProjection? {
        // Return existing projection if active
        mediaProjection?.let { return it }

        val data = resultData ?: return null
        if (resultCode == 0) return null

        return try {
            val projection = manager.getMediaProjection(resultCode, data)
            mediaProjection = projection
            _isProjectionActive.value = true
            // Android 14+ (API 34+): Token is strictly single-use. Clear Intent so it is never reused.
            resultCode = 0
            resultData = null
            projection
        } catch (e: Exception) {
            e.printStackTrace()
            _isProjectionActive.value = false
            null
        }
    }

    @Synchronized
    fun markStopped() {
        mediaProjection = null
        resultCode = 0
        resultData = null
        _isProjectionActive.value = false
    }

    @Synchronized
    fun clear() {
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        markStopped()
    }
}
