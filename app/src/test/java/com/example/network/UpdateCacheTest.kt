package com.example.network

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCacheTest {
    private val now = TimeUnit.DAYS.toMillis(20)
    @Test fun installedUpdateAndPartialAreRemoved() {
        assertTrue(AppUpdateInstaller.shouldDelete("update-6.apk", now, now, 6))
        assertTrue(AppUpdateInstaller.shouldDelete("download.part", now, now, 6))
    }
    @Test fun pendingUpdateSurvivesRestartButExpires() {
        assertFalse(AppUpdateInstaller.shouldDelete("update-7.apk", now, now, 6))
        assertTrue(AppUpdateInstaller.shouldDelete("update-7.apk", 0, now, 6))
    }
    @Test fun unrelatedFilesAreNotSelected() {
        assertFalse(AppUpdateInstaller.shouldDelete("photo.jpg", 0, now, 6))
        assertFalse(AppUpdateInstaller.shouldDelete("Gidscreen.apk", 0, now, 6))
    }
}
