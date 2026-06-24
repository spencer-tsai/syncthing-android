package com.spencer.syncthing.ui

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spencer.syncthing.api.DeviceConfig

@Composable
fun AddFolderDialog(
    defaultBasePath: String,
    availableDevices: List<DeviceConfig>,
    inProgress: Boolean,
    errorMessage: String?,
    onRequestStoragePermission: () -> Unit,
    hasStoragePermission: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (label: String, path: String, sharedDeviceIds: List<String>) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var customPath by remember { mutableStateOf("") }

    val effectivePath = customPath.ifBlank {
        val slug = label.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        "$defaultBasePath/${slug.ifBlank { "sync" }}"
    }

    val selectedIds = remember { mutableStateOf(setOf<String>()) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { customPath = uriToRealPath(it) ?: customPath }
    }

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("新增資料夾") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("資料夾名稱") },
                    singleLine = true,
                    isError = label.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    label = { Text("同步路徑") },
                    placeholder = { Text(effectivePath) },
                    supportingText = {
                        if (customPath.isBlank())
                            Text("留空使用預設路徑：$effectivePath",
                                style = MaterialTheme.typography.bodySmall)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                if (hasStoragePermission) {
                    OutlinedButton(
                        onClick = {
                            // Pre-navigate to /storage/emulated/0 in the picker
                            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A")
                            folderPickerLauncher.launch(initialUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("瀏覽資料夾…")
                    }
                } else {
                    OutlinedButton(
                        onClick = onRequestStoragePermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("授權存取所有檔案（瀏覽資料夾所需）")
                    }
                }

                if (availableDevices.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("共享裝置", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    availableDevices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = device.deviceID in selectedIds.value,
                                onCheckedChange = { checked ->
                                    selectedIds.value = if (checked)
                                        selectedIds.value + device.deviceID
                                    else
                                        selectedIds.value - device.deviceID
                                }
                            )
                            Text(
                                text = device.name.ifBlank { device.deviceID.take(7) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (inProgress) {
                CircularProgressIndicator()
            } else {
                TextButton(
                    onClick = { onConfirm(label.trim(), effectivePath, selectedIds.value.toList()) },
                    enabled = label.isNotBlank()
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

// Converts a DocumentTree URI to a real filesystem path.
// Works for primary (internal) storage; returns null for SD cards / unknown providers.
private fun uriToRealPath(uri: Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    when {
        docId.startsWith("primary:") -> {
            val rel = docId.removePrefix("primary:").trimEnd('/')
            if (rel.isEmpty()) Environment.getExternalStorageDirectory().absolutePath
            else "${Environment.getExternalStorageDirectory().absolutePath}/$rel"
        }
        // Some devices expose home: for internal storage root
        docId.startsWith("home:") -> {
            val rel = docId.removePrefix("home:").trimEnd('/')
            if (rel.isEmpty()) Environment.getExternalStorageDirectory().absolutePath
            else "${Environment.getExternalStorageDirectory().absolutePath}/$rel"
        }
        else -> null
    }
}.getOrNull()
