package com.example.network

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()
    private fun directory(context: Context) = File(context.cacheDir, "app-updates")

    internal fun shouldDelete(name: String, modified: Long, now: Long, installed: Int): Boolean {
        val version = Regex("update-(\\d+)\\.apk").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
        return name.endsWith(".part") || (version != null &&
            (version <= installed || now - modified > TimeUnit.DAYS.toMillis(7)))
    }

    suspend fun cleanup(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory(context).listFiles()?.forEach { file ->
                if (shouldDelete(file.name, file.lastModified(), System.currentTimeMillis(), BuildConfig.VERSION_CODE)) file.delete()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun validate(context: Context, file: File, update: AppUpdate) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
            ?: throw IOException("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val signatures = archive.signatures?.map { it.toCharsString() }?.toSet()
        val currentSignatures = installed.signatures?.map { it.toCharsString() }?.toSet()
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName != context.packageName || code != update.versionCode.toLong() ||
            code <= BuildConfig.VERSION_CODE || signatures.isNullOrEmpty() || signatures != currentSignatures) {
            throw IOException("APK identity or signing certificate mismatch")
        }
    }

    suspend fun download(context: Context, update: AppUpdate, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(update.apkUrl == "https://github.com/nbnurlan/Gidscreen/releases/download/v${update.versionName}/Gidscreen.apk")
            val dir = directory(context)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create update cache")
            val target = File(dir, "update-${update.versionCode}.apk")
            if (target.exists()) {
                try { validate(context, target, update); return@withLock target }
                catch (_: IOException) { target.delete() }
            }
            // This directory contains only this app's temporary update downloads.
            dir.listFiles()?.forEach { it.delete() }
            val partial = File(dir, "download.part")
            try {
                client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed")
                    val body = response.body ?: throw IOException("Empty download")
                    val expected = body.contentLength()
                    val limit = 150L * 1024 * 1024
                    if (expected > limit) throw IOException("APK too large")
                    var total = 0L
                    var lastProgress = -2
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > limit) throw IOException("APK too large")
                                output.write(buffer, 0, count)
                                val percent = if (expected > 0) (total * 100 / expected).toInt().coerceAtMost(100) else -1
                                if (percent != lastProgress) { progress(percent); lastProgress = percent }
                            }
                        }
                    }
                    if (total == 0L || (expected >= 0 && total != expected)) throw IOException("Incomplete APK")
                }
                validate(context, partial, update)
                if (!partial.renameTo(target)) throw IOException("Cannot save APK")
                target
            } finally { partial.delete() }
        }
    }

    // Returns false if the user first needs to grant Android's install permission.
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Gidscreen update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return true
    }
}
