package com.spencer.syncthing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun AddDeviceDialog(
    inProgress: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (deviceId: String, name: String) -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }

    // Syncthing device IDs: 63 chars of uppercase base32 separated by dashes every 7 chars
    val idIsValid = deviceId.trim().replace("-", "").length >= 52

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("新增裝置") },
        text = {
            Column {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("裝置 ID") },
                    placeholder = { Text("XXXXXXX-XXXXXXX-...") },
                    isError = deviceId.isNotBlank() && !idIsValid,
                    supportingText = {
                        if (deviceId.isNotBlank() && !idIsValid)
                            Text("裝置 ID 格式不正確", color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("裝置名稱（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (inProgress) {
                CircularProgressIndicator()
            } else {
                TextButton(
                    onClick = { onConfirm(deviceId.trim(), deviceName.trim()) },
                    enabled = idIsValid
                ) {
                    Text("新增")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("取消")
            }
        }
    )
}
