package com.example.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.BuildConfig
import com.example.R
import com.example.network.AppUpdate
import com.example.network.AppUpdateChecker
import com.example.network.AppUpdateInstaller
import com.example.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun AppUpdatePrompt() {
    if (BuildConfig.APPLICATION_ID.endsWith(".test")) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var ready by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun translated(uz: String, ru: String, en: String) = when (LocaleHelper.currentLanguage.value) {
        LocaleHelper.LANG_RU -> ru
        LocaleHelper.LANG_EN -> en
        else -> uz
    }
    LaunchedEffect(Unit) {
        AppUpdateInstaller.cleanup(context.applicationContext)
        if (!dismissed) update = AppUpdateChecker.check(BuildConfig.VERSION_CODE)
    }
    val available = update ?: return
    if (dismissed) return
    val failure = translated("Yuklash yoki o‘rnatishni ochib bo‘lmadi. Qayta urinib ko‘ring.", "Не удалось загрузить или открыть установку. Повторите попытку.", "Could not download or open the installer. Please retry.")
    val permission = translated("O‘rnatishga ruxsat bering, qaytib kelib «O‘rnatish»ni bosing.", "Разрешите установку, вернитесь и нажмите «Установить».", "Allow installation, return here and tap Install.")
    AlertDialog(
        onDismissRequest = { if (!busy) dismissed = true },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Text(if (busy) translated("Yuklanmoqda", "Загрузка", "Downloading") +
                if (progress >= 0) " — $progress%" else "…"
            else message ?: stringResource(R.string.update_message, available.versionName))
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                message = null
                scope.launch {
                    try {
                        val file = ready?.takeIf { it.exists() } ?: AppUpdateInstaller.download(context.applicationContext, available) { value ->
                            // Download runs on IO; update Compose state on the UI dispatcher.
                            scope.launch(Dispatchers.Main.immediate) { progress = value }
                        }
                        ready = file
                        if (!AppUpdateInstaller.install(context, file)) message = permission
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        message = failure
                    } finally { busy = false }
                }
            }) {
                Text(if (ready != null) translated("O‘rnatish", "Установить", "Install") else stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
        }
    )
}
