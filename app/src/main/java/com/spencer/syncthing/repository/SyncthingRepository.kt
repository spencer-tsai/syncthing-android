package com.spencer.syncthing.repository

import com.spencer.syncthing.api.ApiClient
import com.spencer.syncthing.api.ConnectionsResponse
import com.spencer.syncthing.api.DeviceConfig
import com.spencer.syncthing.api.FolderConfig
import com.spencer.syncthing.api.FolderDevice
import com.spencer.syncthing.api.FolderStatus
import com.spencer.syncthing.api.PendingDeviceInfo
import com.spencer.syncthing.api.PendingFolderInfo
import com.spencer.syncthing.api.SystemStatus
import com.spencer.syncthing.api.VersionResponse
import kotlinx.coroutines.delay
import java.util.UUID

class SyncthingRepository(apiKey: String) {

    private val api = ApiClient.create(apiKey)

    suspend fun getVersion(): Result<VersionResponse> = runCatching { api.getVersion() }

    suspend fun getSystemStatus(): Result<SystemStatus> = runCatching { api.getSystemStatus() }

    suspend fun getFolders(): Result<List<FolderConfig>> = runCatching { api.getFolders() }

    suspend fun getDevices(): Result<List<DeviceConfig>> = runCatching { api.getDevices() }

    suspend fun getFolderStatus(folderId: String): Result<FolderStatus> =
        runCatching { api.getFolderStatus(folderId) }

    suspend fun getConnections(): Result<ConnectionsResponse> =
        runCatching { api.getConnections() }

    suspend fun getPendingDevices(): Result<Map<String, PendingDeviceInfo>> =
        runCatching { api.getPendingDevices() }

    suspend fun getPendingFolders(): Result<Map<String, PendingFolderInfo>> =
        runCatching { api.getPendingFolders() }

    // Accept a pending device by adding it to config
    suspend fun acceptPendingDevice(deviceId: String, name: String): Result<Unit> =
        addDevice(deviceId, name)

    // Accept a pending folder: add it to config with the given label and path
    suspend fun acceptPendingFolder(
        folderId: String,
        label: String,
        path: String,
        sharedDeviceIds: List<String>
    ): Result<Unit> = runCatching {
        val config = FolderConfig(
            id = folderId,
            label = label.ifBlank { folderId },
            path = path,
            type = "sendreceive",
            devices = sharedDeviceIds.map { FolderDevice(deviceID = it) }
        )
        api.addFolder(config)
    }

    suspend fun addDevice(deviceId: String, name: String): Result<Unit> = runCatching {
        val config = DeviceConfig(
            deviceID = deviceId.trim(),
            name = name.trim().ifBlank { deviceId.trim().take(7) },
            addresses = listOf("dynamic")
        )
        api.addDevice(config)
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> =
        runCatching { api.deleteFolder(folderId) }

    suspend fun addFolder(
        label: String,
        path: String,
        sharedDeviceIds: List<String>
    ): Result<Unit> = runCatching {
        val folderId = UUID.randomUUID().toString().replace("-", "").take(11)
        val config = FolderConfig(
            id = folderId,
            label = label.trim(),
            path = path,
            type = "sendreceive",
            devices = sharedDeviceIds.map { FolderDevice(deviceID = it) }
        )
        api.addFolder(config)
    }

    // Retry until the API responds or maxRetries is exhausted
    suspend fun waitForReady(maxRetries: Int = 30): Boolean {
        repeat(maxRetries) {
            if (getVersion().isSuccess) return true
            delay(1_000)
        }
        return false
    }
}
