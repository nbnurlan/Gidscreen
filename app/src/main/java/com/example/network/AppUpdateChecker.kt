package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdate(val versionCode: Int, val versionName: String, val apkUrl: String)

object AppUpdateChecker {
    private const val MANIFEST_URL =
        "https://github.com/nbnurlan/Gidscreen/releases/latest/download/update.json"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    // An unavailable network, absent release, or malformed manifest must never block startup.
    suspend fun check(installedVersionCode: Int): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(MANIFEST_URL)
                .header("Cache-Control", "no-cache").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                // Avoid reading an unexpectedly large release asset into memory.
                val source = body.source()
                source.request(16_385)
                if (source.buffer.size > 16_384) return@withContext null
                parseManifest(body.string(), installedVersionCode)
            }
        } catch (_: IOException) {
            null
        }
    }

    internal fun parseManifest(json: String, installedVersionCode: Int): AppUpdate? {
        return try {
            val data = JSONObject(json)
            if (data.optString("applicationId") != "com.aistudio.screenlasso.aiwzqp") return null
            val code = data.optString("versionCode").toIntOrNull() ?: return null
            val name = data.optString("versionName")
            if (code <= installedVersionCode || !name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return null
            val url = data.optString("apkUrl").toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.host != "github.com" || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.query != null || url.fragment != null ||
                url.encodedPath != "/nbnurlan/Gidscreen/releases/download/v$name/Gidscreen.apk") return null
            AppUpdate(code, name, url.toString())
        } catch (_: org.json.JSONException) {
            null
        }
    }
}
