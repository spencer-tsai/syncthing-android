package com.spencer.syncthing.ui

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.spencer.syncthing.api.DeviceConfig
import com.spencer.syncthing.api.FolderConfig
import com.spencer.syncthing.api.FolderStatus
import com.spencer.syncthing.api.PendingDeviceInfo
import com.spencer.syncthing.api.PendingFolderInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenAddDevice: () -> Unit,
    onCloseAddDevice: () -> Unit,
    onAddDevice: (deviceId: String, name: String) -> Unit,
    onOpenAddFolder: () -> Unit,
    onCloseAddFolder: () -> Unit,
    onAddFolder: (label: String, path: String, sharedDeviceIds: List<String>) -> Unit,
    onAcceptPendingDevice: (deviceId: String, name: String) -> Unit,
    onAcceptPendingFolder: (folderId: String, label: String, path: String, sharedDeviceIds: List<String>) -> Unit,
    onRequestDeleteFolder: (FolderConfig) -> Unit,
    onConfirmDeleteFolder: () -> Unit,
    onCancelDeleteFolder: () -> Unit,
    onRequestStoragePermission: () -> Unit
) {
    val context = LocalContext.current

    // Default base path for new folders (app-specific external storage)
    val defaultBasePath = context.getExternalFilesDir(null)?.absolutePath
        ?: Environment.getExternalStorageDirectory().absolutePath + "/Syncthing"

    // Dialogs rendered outside LazyColumn to avoid recomposition issues
    if (state.showAddDeviceDialog) {
        AddDeviceDialog(
            inProgress = state.actionInProgress,
            errorMessage = state.actionError,
            onDismiss = onCloseAddDevice,
            onConfirm = onAddDevice
        )
    }

    state.deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = onCancelDeleteFolder,
            title = { Text("刪除資料夾") },
            text = {
                Text("確定要從 Syncthing 中移除「${folder.label.ifBlank { folder.id }}」？\n\n本機檔案不會被刪除。")
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteFolder,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDeleteFolder) { Text("取消") }
            }
        )
    }

    if (state.showAddFolderDialog) {
        AddFolderDialog(
            defaultBasePath = defaultBasePath,
            availableDevices = state.devices,
            inProgress = state.actionInProgress,
            errorMessage = state.actionError,
            hasStoragePermission = state.hasStoragePermission,
            onRequestStoragePermission = onRequestStoragePermission,
            onDismiss = onCloseAddFolder,
            onConfirm = onAddFolder
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Syncthing") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                ServiceCard(state = state, onStart = onStartService, onStop = onStopService)
            }

            // Pending device / folder requests
            if (state.pendingDevices.isNotEmpty()) {
                items(state.pendingDevices.entries.toList()) { (deviceId, info) ->
                    PendingDeviceCard(
                        deviceId = deviceId,
                        info = info,
                        onAccept = { onAcceptPendingDevice(deviceId, info.name) }
                    )
                }
            }

            if (state.pendingFolders.isNotEmpty()) {
                items(state.pendingFolders.entries.toList()) { (folderId, info) ->
                    val offeredLabel = info.offeredBy.values.firstOrNull()?.label ?: folderId
                    val offeringDeviceId = info.offeredBy.keys.firstOrNull() ?: ""
                    PendingFolderCard(
                        folderId = folderId,
                        label = offeredLabel,
                        offeredByDeviceId = offeringDeviceId,
                        defaultBasePath = defaultBasePath,
                        onAccept = { path ->
                            onAcceptPendingFolder(
                                folderId,
                                offeredLabel,
                                path,
                                if (offeringDeviceId.isNotEmpty()) listOf(offeringDeviceId) else emptyList()
                            )
                        }
                    )
                }
            }

            if (state.isApiReady) {
                item {
                    SectionHeader(
                        title = "資料夾",
                        onAdd = onOpenAddFolder
                    )
                }
                if (state.folders.isEmpty()) {
                    item {
                        Text(
                            "尚未新增任何資料夾",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    items(state.folders) { folder ->
                        FolderCard(
                            folder = folder,
                            status = state.folderStatuses[folder.id],
                            onDelete = { onRequestDeleteFolder(folder) }
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "裝置",
                        onAdd = onOpenAddDevice
                    )
                }
                if (state.devices.isEmpty()) {
                    item {
                        Text(
                            "尚未配對任何裝置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    items(state.devices) { device ->
                        DeviceCard(
                            device = device,
                            isConnected = state.connections[device.deviceID]?.connected == true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(state: UiState, onStart: () -> Unit, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("服務狀態", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            !state.isServiceRunning -> "已停止"
                            !state.isApiReady -> "啟動中…"
                            else -> "執行中"
                        },
                        color = if (state.isServiceRunning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(onClick = if (state.isServiceRunning) onStop else onStart) {
                    Text(if (state.isServiceRunning) "停止" else "啟動")
                }
            }

            if (state.version.isNotEmpty()) {
                Text("版本：${state.version}", style = MaterialTheme.typography.bodySmall)
            }

            if (state.deviceId.isNotEmpty()) {
                Text("裝置 ID：", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = state.deviceId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2
                )
            }

            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "新增$title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderCard(folder: FolderConfig, status: FolderStatus?, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.label.ifEmpty { folder.id },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "刪除資料夾",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            status?.let { s ->
                val progress = if (s.globalBytes > 0)
                    (s.globalBytes - s.needBytes).toFloat() / s.globalBytes
                else 1f
                val percent = (progress * 100).toInt()

                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (s.needBytes == 0L)
                        "已同步 · ${formatBytes(s.globalBytes)}"
                    else
                        "同步中 $percent% · 還需 ${formatBytes(s.needBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceConfig, isConnected: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifEmpty { device.deviceID.take(8) + "…" },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = device.deviceID.take(22) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Badge(
                containerColor = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(if (isConnected) "已連線" else "離線")
            }
        }
    }
}

@Composable
private fun PendingDeviceCard(
    deviceId: String,
    info: PendingDeviceInfo,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "新裝置請求連線",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    info.name.ifBlank { deviceId.take(7) },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    deviceId.take(23) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("接受")
            }
        }
    }
}

@Composable
private fun PendingFolderCard(
    folderId: String,
    label: String,
    offeredByDeviceId: String,
    defaultBasePath: String,
    onAccept: (path: String) -> Unit
) {
    val path = "$defaultBasePath/${label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { folderId }}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "對方共享資料夾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(label.ifBlank { folderId }, style = MaterialTheme.typography.titleSmall)
            Text(
                "儲存至：$path",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAccept(path) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("接受")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
