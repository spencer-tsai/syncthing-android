package com.spencer.syncthing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.spencer.syncthing.service.SyncthingService
import com.spencer.syncthing.ui.HomeScreen
import com.spencer.syncthing.ui.MainViewModel
import com.spencer.syncthing.ui.theme.SyncthingTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSyncthingService()
    }

    // Re-check MANAGE_EXTERNAL_STORAGE after user returns from Settings
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.refreshStoragePermission()

        if (SyncthingService.isRunning) {
            viewModel.onServiceStarted()
        }

        setContent {
            SyncthingTheme {
                val state by viewModel.state.collectAsState()
                HomeScreen(
                    state = state,
                    onStartService = { requestNotifPermissionAndStart() },
                    onStopService = { stopSyncthingService() },
                    onOpenAddDevice = viewModel::openAddDeviceDialog,
                    onCloseAddDevice = viewModel::closeAddDeviceDialog,
                    onAddDevice = viewModel::addDevice,
                    onOpenAddFolder = viewModel::openAddFolderDialog,
                    onCloseAddFolder = viewModel::closeAddFolderDialog,
                    onAddFolder = viewModel::addFolder,
                    onAcceptPendingDevice = viewModel::acceptPendingDevice,
                    onAcceptPendingFolder = viewModel::acceptPendingFolder,
                    onRequestDeleteFolder = viewModel::requestDeleteFolder,
                    onConfirmDeleteFolder = viewModel::confirmDeleteFolder,
                    onCancelDeleteFolder = viewModel::cancelDeleteFolder,
                    onRequestStoragePermission = { requestManageExternalStorage() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission state when user comes back from Settings
        viewModel.refreshStoragePermission()
    }

    private fun requestManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            storagePermissionLauncher.launch(intent)
        }
    }

    private fun requestNotifPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startSyncthingService()
    }

    private fun startSyncthingService() {
        ensureApiKey()
        val intent = Intent(this, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        viewModel.onServiceStarted()
    }

    private fun stopSyncthingService() {
        val intent = Intent(this, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_STOP
        }
        startService(intent)
        viewModel.onServiceStopped()
    }

    private fun ensureApiKey() {
        val prefs = getSharedPreferences(SyncthingService.PREF_FILE, Context.MODE_PRIVATE)
        if (prefs.getString(SyncthingService.PREF_API_KEY, null) == null) {
            val key = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(SyncthingService.PREF_API_KEY, key).apply()
        }
    }
}
