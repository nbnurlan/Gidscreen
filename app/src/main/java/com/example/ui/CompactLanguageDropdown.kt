package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import com.example.util.LocaleHelper

@Composable
fun CompactLanguageDropdown(
    modifier: Modifier = Modifier,
    testTag: String = "chat_language_button"
) {
    val context = LocalContext.current
    val currentLang by LocaleHelper.currentLanguage.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val activeLang = LocaleHelper.getActiveLanguage(currentLang)

    Box(modifier = modifier) {
        // Compact Top Flag-Icon Button
        Surface(
            color = Color(0x3300F0FF),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.5f)),
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = activeLang.flag,
                    fontSize = 15.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.btn_language),
                    tint = CyanGlow,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Dropdown Menu with flags & language names
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF0F172A))
                .border(1.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        ) {
            LocaleHelper.supportedLanguages.forEach { lang ->
                val isSelected = currentLang == lang.code
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = lang.flag, fontSize = 18.sp)
                            Text(
                                text = lang.nativeName,
                                color = if (isSelected) CyanGlow else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (currentLang != lang.code) {
                            LocaleHelper.setLanguage(context, lang.code)
                            val toastMsg = context.getString(R.string.toast_language_changed, lang.nativeName)
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = if (isSelected) {
                        Modifier.background(CyanGlow.copy(alpha = 0.12f))
                    } else Modifier
                )
            }
        }
    }
}
