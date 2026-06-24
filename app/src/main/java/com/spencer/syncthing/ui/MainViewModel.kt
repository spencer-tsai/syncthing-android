package com.spencer.syncthing.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spencer.syncthing.api.ConnectionInfo
import com.spencer.syncthing.api.DeviceConfig
import com.spencer.syncthing.api.FolderConfig
import com.spencer.syncthing.api.FolderStatus
import com.spencer.syncthing.api.PendingDeviceInfo
import com.spencer.syncthing.api.PendingFolderInfo
import com.spencer.syncthing.repository.SyncthingRepository
import com.spencer.syncthing.service.SyncthingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val isServiceRunning: Boolean = false,
    val isApiReady: Boolean = false,
    val version: String = "",
    val deviceId: String = "",
    val folders: List<FolderConfig> = emptyList(),
    val folderStatuses: Map<String, FolderStatus> = emptyMap(),
    val devices: List<DeviceConfig> = emptyList(),
    val connections: Map<String, ConnectionInfo> = emptyMap(),
    val error: String? = null,
    // Pending (devices/folders waiting for approval)
    val pendingDevices: Map<String, PendingDeviceInfo> = emptyMap(),
    val pendingFolders: Map<String, PendingFolderInfo> = emptyMap(),
    // Dialog state
    val showAddDeviceDialog: Boolean = false,
    val showAddFolderDialog: Boolean = false,
    val deletingFolder: FolderConfig? = null,   // non-null = confirm delete dialog open
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
    // Storage permission
    val hasStoragePermission: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var repository: SyncthingRepository? = null
    private var pollingJob: Job? = null

    fun onServiceStarted() {
        if (_state.value.isServiceRunning) return
        _state.update { it.copy(isServiceRunning = true, error = null) }
        initRepository()
    }

    fun onServiceStopped() {
        pollingJob?.cancel()
        repository = null
        _state.value = UiState()
    }

    private fun initRepository() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(SyncthingService.PREF_FILE, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(SyncthingService.PREF_API_KEY, null) ?: return

        repository = SyncthingRepository(apiKey)

        viewModelScope.launch {
            val ready = repository!!.waitForReady()
            if (!ready) {
                _state.update { it.copy(error = "Syncthing 啟動逾時，請重試") }
                return@launch
            }
            _state.update { it.copy(isApiReady = true) }
            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchAll()
                delay(5_000)
            }
        }
    }

    // ── Dialog visibility ──────────────────────────────────────────────────────

    fun refreshStoragePermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            true
        _state.update { it.copy(hasStoragePermission = granted) }
    }

    fun openAddDeviceDialog() = _state.update { it.copy(showAddDeviceDialog = true, actionError = null) }
    fun closeAddDeviceDialog() = _state.update { it.copy(showAddDeviceDialog = false, actionError = null) }

    fun openAddFolderDialog() = _state.update { it.copy(showAddFolderDialog = true, actionError = null) }
    fun closeAddFolderDialog() = _state.update { it.copy(showAddFolderDialog = false, actionError = null) }

    // ── Actions ────────────────────────────────────────────────────────────────

    fun addDevice(deviceId: String, name: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionError = null) }
            repo.addDevice(deviceId, name)
                .onSuccess {
                    _state.update { it.copy(showAddDeviceDialog = false, actionInProgress = false) }
                    fetchAll()
                }
                .onFailure { e ->
                    _state.update { it.copy(actionInProgress = false, actionError = e.message ?: "新增裝置失敗") }
                }
        }
    }

    fun addFolder(label: String, path: String, sharedDeviceIds: List<String>) {
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, actionError = null) }
            repo.addFolder(label, path, sharedDeviceIds)
                .onSuccess {
                    _state.update { it.copy(showAddFolderDialog = false, actionInProgress = false) }
                    fetchAll()
                }
                .onFailure { e ->
                    _state.update { it.copy(actionInProgress = false, actionError = e.message ?: "新增資料夾失敗") }
                }
        }
    }

    fun requestDeleteFolder(folder: FolderConfig) =
        _state.update { it.copy(deletingFolder = folder) }

    fun cancelDeleteFolder() =
        _state.update { it.copy(deletingFolder = null) }

    fun confirmDeleteFolder() {
        val folder = _state.value.deletingFolder ?: return
        val repo = repository ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, deletingFolder = null) }
            repo.deleteFolder(folder.id)
                .onSuccess { fetchAll() }
                .onFailure { e ->
                    _state.update { it.copy(actionError = e.message ?: "刪除資料夾失敗") }
                }
            _state.update { it.copy(actionInProgress = false) }
        }
    }

    fun acceptPendingDevice(deviceId: String, name: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.acceptPendingDevice(deviceId, name)
                .onSuccess { fetchAll() }
        }
    }

    fun acceptPendingFolder(folderId: String, label: String, path: String, sharedDeviceIds: List<String>) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.acceptPendingFolder(folderId, label, path, sharedDeviceIds)
                .onSuccess { fetchAll() }
        }
    }

    private suspend fun fetchAll() {
        val repo = repository ?: return

        repo.getVersion().onSuccess { v ->
            _state.update { it.copy(version = v.version) }
        }

        repo.getSystemStatus().onSuccess { s ->
            _state.update { it.copy(deviceId = s.myId) }
        }

        repo.getFolders().onSuccess { folders ->
            _state.update { it.copy(folders = folders) }

            val statuses = folders.mapNotNull { folder ->
                repo.getFolderStatus(folder.id).getOrNull()?.let { folder.id to it }
            }.toMap()
            _state.update { it.copy(folderStatuses = statuses) }
        }

        repo.getDevices().onSuccess { devices ->
            _state.update { it.copy(devices = devices) }
        }

        repo.getConnections().onSuccess { resp ->
            _state.update { it.copy(connections = resp.connections) }
        }

        repo.getPendingDevices().onSuccess { pending ->
            _state.update { it.copy(pendingDevices = pending) }
        }

        repo.getPendingFolders().onSuccess { pending ->
            _state.update { it.copy(pendingFolders = pending) }
        }
    }
}
