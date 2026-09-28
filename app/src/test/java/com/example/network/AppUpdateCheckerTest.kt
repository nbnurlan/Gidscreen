package com.example.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AppUpdateCheckerTest {
    private fun manifest(code: Int = 4, url: String =
        "https://github.com/nbnurlan/Gidscreen/releases/download/v1.2.2/Gidscreen.apk"
    ): String = JSONObject().apply {
        put("applicationId", "com.aistudio.screenlasso.aiwzqp")
        put("versionCode", code)
        put("versionName", "1.2.2")
        put("apkUrl", url)
    }.toString()

    @Test fun newerReleaseIsOffered() {
        val update = AppUpdateChecker.parseManifest(manifest(), 3)
        assertEquals(4, update?.versionCode)
        assertEquals("1.2.2", update?.versionName)
    }

    @Test fun currentOrOlderVersionIsNotOffered() {
        assertNull(AppUpdateChecker.parseManifest(manifest(3), 3))
        assertNull(AppUpdateChecker.parseManifest(manifest(2), 3))
    }

    @Test fun unsafeOrWrongRepositoryDownloadIsRejected() {
        for (url in listOf(
            "http://github.com/nbnurlan/Gidscreen/releases/download/v1.2.2/Gidscreen.apk",
            "https://github.com.evil.example/nbnurlan/Gidscreen/releases/download/v1.2.2/Gidscreen.apk",
            "https://github.com/other/repo/releases/download/v1.2.2/Gidscreen.apk",
            "https://github.com/nbnurlan/Gidscreen/releases/download/v1.2.1/Gidscreen.apk",
            "https://github.com/nbnurlan/Gidscreen/releases/download/v1.2.2/Gidscreen.apk?redirect=bad"
        )) assertNull(AppUpdateChecker.parseManifest(manifest(url = url), 3))
    }

    @Test fun malformedOrWrongAppManifestIsIgnored() {
        assertNull(AppUpdateChecker.parseManifest("not json", 3))
        assertNull(AppUpdateChecker.parseManifest("{}", 3))
        val wrongApp = JSONObject(manifest()).put("applicationId", "another.app").toString()
        assertNull(AppUpdateChecker.parseManifest(wrongApp, 3))
        val overflow = JSONObject(manifest()).put("versionCode", 9_000_000_000L).toString()
        assertNull(AppUpdateChecker.parseManifest(overflow, 3))
    }
}
