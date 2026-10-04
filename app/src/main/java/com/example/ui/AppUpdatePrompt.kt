package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.BuildConfig
import com.example.R
import com.example.network.AppUpdate
import com.example.network.AppUpdateChecker

@Composable
fun AppUpdatePrompt() {
    // Test installs must not offer production APKs with a different application ID.
    if (BuildConfig.APPLICATION_ID.endsWith(".test")) return
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!dismissed) update = AppUpdateChecker.check(BuildConfig.VERSION_CODE)
    }
    val available = update ?: return
    if (dismissed) return
    val openError = stringResource(R.string.update_open_error)
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_message, available.versionName)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.apkUrl)))
                    dismissed = true
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, openError, Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
        }
    )
}
