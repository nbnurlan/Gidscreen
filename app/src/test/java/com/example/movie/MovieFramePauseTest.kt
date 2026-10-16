package com.example.movie

import android.graphics.Bitmap
import com.example.service.ScreenCaptureHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MovieFramePauseTest {
    @Test fun pauseRecyclesSensitiveCachedPixelsAndReleaseResetsCaptureMode() {
        val cached = ScreenCaptureHelper::class.java.getDeclaredField("latestBitmap").apply { isAccessible = true }
        val paused = ScreenCaptureHelper::class.java.getDeclaredField("framesPaused").apply { isAccessible = true }
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            cached.set(null, bitmap)
            ScreenCaptureHelper.pauseFrames()
            assertTrue(bitmap.isRecycled)
            assertNull(cached.get(null))
            assertTrue(paused.getBoolean(null))
            ScreenCaptureHelper.release()
            assertFalse(paused.getBoolean(null))
        } finally { ScreenCaptureHelper.release() }
    }
}
