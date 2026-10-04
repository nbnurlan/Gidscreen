package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SoftInk
import com.example.ui.theme.SoftBackground
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.network.GeminiModelManager
import com.example.network.GeminiService
import com.example.ui.theme.SoftPrimary as CyanGlow
import com.example.ui.theme.SoftBorder as DarkBorder
import com.example.ui.theme.SoftSurface as DarkSurface
import com.example.ui.theme.SoftSurfaceVariant as DarkSurfaceVariant
import com.example.ui.theme.SoftPrimary as PurpleNeon
import com.example.util.AppLanguage
import com.example.util.LocaleHelper

@Composable
fun MasterServiceCard(
    isServiceActive: Boolean,
    hasOverlayPermission: Boolean,
    hasCaptureToken: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = { onToggleService(!isServiceActive) },
            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.White),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
                .testTag(if (isServiceActive) "stop_service_button" else "start_service_button")
        ) {
            Icon(if (isServiceActive) Icons.Default.Stop else Icons.Default.CropFree, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (isServiceActive) R.string.btn_stop_service else R.string.soft_start),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(R.string.soft_hint), color = SoftInk.copy(alpha = 0.7f),
            fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Surface(color = DarkSurface, shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DarkBorder)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = DarkSurfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ScreenShare, null, tint = CyanGlow,
                        modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.soft_bubble), color = SoftInk,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (isServiceActive) R.string.service_status_running else R.string.service_status_ready),
                        color = SoftInk.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Switch(checked = isServiceActive, onCheckedChange = onToggleService,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                        checkedTrackColor = CyanGlow, uncheckedThumbColor = CyanGlow,
                        uncheckedTrackColor = DarkSurfaceVariant, uncheckedBorderColor = DarkBorder),
                    modifier = Modifier.testTag("service_toggle_switch"))
            }
        }
    }
}

@Composable
fun PermissionStatusItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF197653) else Color(0xFF986000),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = SoftInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SoftInk.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
            }

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CyanGlow.copy(alpha = 0.2f),
                        contentColor = CyanGlow
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_grant),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.status_active),
                    color = Color(0xFF197653),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun GeminiApiStatusCard(
    onOpenModelSelection: () -> Unit = {}
) {
    val isConfigured = GeminiService.isApiKeyConfigured()
    val selectedModelId by GeminiModelManager.selectedModelId.collectAsState()
    val activeModel = GeminiModelManager.getSelectedModel()

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenModelSelection)
            .testTag("gemini_api_status_card")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = if (isConfigured) CyanGlow else PurpleNeon,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.api_card_title),
                        color = SoftInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConfigured) {
                            stringResource(R.string.api_card_ready)
                        } else {
                            stringResource(R.string.api_card_pending)
                        },
                        color = if (isConfigured) Color(0xFF197653) else Color(0xFF986000),
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = CyanGlow.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.3f))
            ) {
                Text(
                    text = activeModel.displayName,
                    color = CyanGlow,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun QuickGuideCard() {
    Surface(
        color = SoftBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.guide_title),
                color = SoftInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            GuideStepItem(
                step = "1",
                title = stringResource(R.string.guide_step1_title),
                desc = stringResource(R.string.guide_step1_desc)
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideStepItem(
                step = "2",
                title = stringResource(R.string.guide_step2_title),
                desc = stringResource(R.string.guide_step2_desc)
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideStepItem(
                step = "3",
                title = stringResource(R.string.guide_step3_title),
                desc = stringResource(R.string.guide_step3_desc)
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideStepItem(
                step = "4",
                title = stringResource(R.string.guide_step4_title),
                desc = stringResource(R.string.guide_step4_desc)
            )
        }
    }
}

@Composable
private fun GuideStepItem(step: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CyanGlow.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = CyanGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = SoftInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, color = SoftInk.copy(alpha = 0.72f), fontSize = 11.sp)
        }
    }
}

@Composable
fun SampleContentCard(
    title: String,
    icon: ImageVector,
    content: String,
    onTestLasso: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, color = SoftInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onTestLasso,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CyanGlow.copy(alpha = 0.15f),
                        contentColor = CyanGlow
                    )
                ) {
                    Icon(imageVector = Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_select_area),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = SoftBackground,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = content,
                    color = SoftInk,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
