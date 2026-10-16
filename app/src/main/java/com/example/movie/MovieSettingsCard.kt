package com.example.movie

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ScreenCapturePermissionActivity
import com.example.service.LassoOverlayService

@Composable
fun MovieSettingsCard() {
    val context = LocalContext.current
    val active by LassoOverlayService.isMovieActive.collectAsState()
    var consent by remember { mutableStateOf(false) }
    Column {
        Text("AI Movie Mode")
        Text(stringResource(R.string.movie_consent))
        Switch(checked = active, enabled = Build.VERSION.SDK_INT >= 29, onCheckedChange = { enabled ->
            if (enabled) consent = true else context.stopService(Intent(context, LassoOverlayService::class.java))
        })
        TextButton(onClick = { context.stopService(Intent(context, LassoOverlayService::class.java)) }) { Text(stringResource(R.string.movie_stop)) }
        TmdbKeySettingsCard()
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("AI Movie Mode") }, text = { Text(stringResource(R.string.movie_consent)) },
        confirmButton = { TextButton(onClick = {
            consent = false
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
            } else if (com.example.network.GeminiKeyStore.get().isBlank() || TmdbKeyStore.get().isBlank() ||
                !com.example.network.GeminiService.getActiveModelId().startsWith("gemini-")) {
                android.widget.Toast.makeText(context, R.string.movie_error, android.widget.Toast.LENGTH_LONG).show()
            } else {
                // Terminate any existing projection before requesting a new single-use consent token.
                context.stopService(Intent(context, LassoOverlayService::class.java))
                context.startActivity(Intent(context, ScreenCapturePermissionActivity::class.java).putExtra("movie_mode", true))
            }
        }) { Text(stringResource(R.string.movie_start)) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.btn_cancel)) } })
}
