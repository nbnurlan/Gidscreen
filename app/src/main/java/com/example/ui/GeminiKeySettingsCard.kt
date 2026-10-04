package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.GeminiKeyStore

@Composable
fun GeminiKeySettingsCard() {
    val configured by GeminiKeyStore.configured.collectAsState()
    var editing by remember { mutableStateOf(false) }
    // Deliberately not rememberSaveable: secrets must not enter saved-instance state.
    var input by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().testTag("gemini_key_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gemini_key_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (configured) R.string.gemini_key_saved else R.string.gemini_key_missing))
            Text(stringResource(R.string.gemini_key_help), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { input = ""; failed = false; editing = true }) {
                    Text(stringResource(R.string.gemini_key_edit))
                }
                if (configured) TextButton(onClick = { GeminiKeyStore.clear() }) {
                    Text(stringResource(R.string.gemini_key_remove))
                }
            }
        }
    }
    if (editing) AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        onDismissRequest = { input = ""; editing = false },
        title = { Text(stringResource(R.string.gemini_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; failed = false },
                    label = { Text(stringResource(R.string.gemini_key_title)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = failed,
                    modifier = Modifier.fillMaxWidth().testTag("gemini_key_input")
                )
                if (failed) Text(stringResource(R.string.gemini_key_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = input.isNotBlank(), onClick = {
                try {
                    GeminiKeyStore.save(input)
                    input = ""
                    editing = false
                } catch (_: Exception) { failed = true }
            }) { Text(stringResource(R.string.gemini_key_save)) }
        },
        dismissButton = {
            TextButton(onClick = { input = ""; editing = false }) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
