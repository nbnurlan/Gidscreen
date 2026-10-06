package com.example.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SoftInk
import com.example.ui.theme.SoftBackground
import com.example.network.GeminiModelManager
import com.example.network.GeminiService
import com.example.ui.theme.SoftPrimary as CyanGlow
import com.example.ui.theme.SoftBorder as DarkBorder
import com.example.ui.theme.SoftSurface as DarkSurface
import com.example.ui.theme.SoftPrimary as PurpleNeon
import com.example.util.AppLanguage
import com.example.util.LocaleHelper

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean = false,
    hasCaptureToken: Boolean = false,
    hasNotificationPermission: Boolean = false,
    onOpenOverlayPermission: () -> Unit = {},
    onOpenCapturePermission: () -> Unit = {},
    onOpenNotificationPermission: () -> Unit = {},
    onOpenModelSelection: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentLang by LocaleHelper.currentLanguage.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = DarkSurface,
            titleContentColor = SoftInk,
            textContentColor = SoftInk.copy(alpha = 0.85f),
            icon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_select_language_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocaleHelper.supportedLanguages.forEach { lang ->
                        val isSelected = currentLang == lang.code
                        Surface(
                            color = if (isSelected) CyanGlow.copy(alpha = 0.15f) else SoftBackground,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CyanGlow else DarkBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    LocaleHelper.setLanguage(context, lang.code)
                                    val msg = context.getString(R.string.toast_language_changed, lang.nativeName)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    showLanguageDialog = false
                                }
                                .testTag("dialog_lang_option_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(text = lang.flag, fontSize = 22.sp)
                                    Text(
                                        text = lang.nativeName,
                                        color = if (isSelected) CyanGlow else SoftInk,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        LocaleHelper.setLanguage(context, lang.code)
                                        val msg = context.getString(R.string.toast_language_changed, lang.nativeName)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        showLanguageDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyanGlow,
                                        unselectedColor = SoftInk.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showLanguageDialog = false },
                    modifier = Modifier.testTag("dialog_lang_cancel_btn")
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = CyanGlow
                    )
                }
            }
        )
    }

    val modelId by GeminiModelManager.selectedModelId.collectAsState()
    val model = GeminiModelManager.getSelectedModel()
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            SettingsRow(Icons.Default.Language, stringResource(R.string.language_settings_title),
                LocaleHelper.supportedLanguages.find { it.code == currentLang }?.nativeName ?: currentLang,
                onClick = { showLanguageDialog = true })
        }
        item { GeminiKeySettingsCard() }
        item { HuggingFaceKeySettingsCard() }
        item {
            // Reading modelId subscribes this row to model changes.
            androidx.compose.runtime.key(modelId) {
                SettingsRow(Icons.Default.AutoAwesome, stringResource(R.string.settings_section_ai),
                    model.displayName, onClick = onOpenModelSelection)
            }
        }
        item {
            SettingsRow(Icons.Default.CropFree, stringResource(R.string.settings_overlay_short),
                stringResource(if (hasOverlayPermission) R.string.settings_allowed else R.string.settings_not_allowed),
                onClick = onOpenOverlayPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                SettingsRow(Icons.Default.Security, stringResource(R.string.settings_notifications_short),
                    stringResource(if (hasNotificationPermission) R.string.settings_allowed else R.string.settings_not_allowed),
                    onClick = onOpenNotificationPermission)
            }
        }
        item {
            SettingsRow(Icons.Default.Info, stringResource(R.string.settings_section_about),
                "Gidscreen · ${com.example.BuildConfig.VERSION_NAME}")
        }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = SoftInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = SoftInk.copy(alpha = 0.65f), fontSize = 13.sp)
            }
            if (onClick != null) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = SoftInk.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
            }
        }
        androidx.compose.material3.HorizontalDivider(color = DarkBorder.copy(alpha = 0.65f))
    }
}
