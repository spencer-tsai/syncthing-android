package com.spencer.syncthing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spencer.syncthing.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class SyncthingService : Service() {

    companion object {
        const val ACTION_START = "com.spencer.syncthing.START"
        const val ACTION_STOP = "com.spencer.syncthing.STOP"
        const val CHANNEL_ID = "syncthing_service"
        const val NOTIFICATION_ID = 1
        const val API_PORT = 8384
        const val PREF_FILE = "syncthing"
        const val PREF_API_KEY = "api_key"
        private const val TAG = "SyncthingService"

        @Volatile
        var isRunning = false
            private set
    }

    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSyncthing()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopSyncthing()
        isRunning = false
        super.onDestroy()
    }

    private fun startSyncthing() {
        val apiKey = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            .getString(PREF_API_KEY, null) ?: run {
            Log.e(TAG, "No API key found — call ensureApiKey() before starting service")
            stopSelf()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        scope.launch {
            try {
                // nativeLibraryDir is SELinux-executable; no manual extraction needed
                val binary = File(applicationInfo.nativeLibraryDir, "libsyncthing.so")
                if (!binary.exists()) {
                    Log.e(TAG, "Binary not found at ${binary.absolutePath}")
                    stopSelf()
                    return@launch
                }
                val configDir = File(filesDir, "syncthing-config").also { it.mkdirs() }

                process = ProcessBuilder(
                    binary.absolutePath,
                    "--home", configDir.absolutePath,
                    "--gui-address", "127.0.0.1:$API_PORT",
                    "--gui-apikey", apiKey,
                    "--no-browser",
                    "--no-restart",
                    "--log-max-old-files=0"
                ).apply {
                    redirectErrorStream(true)
                    environment()["STNORESTART"] = "1"
                    environment()["STNODEFAULTFOLDER"] = "1"
                }.start()

                isRunning = true

                // Drain stdout/stderr to logcat
                launch {
                    process?.inputStream?.bufferedReader()?.forEachLine { line ->
                        Log.d(TAG, line)
                    }
                }

                val exitCode = process?.waitFor()
                Log.i(TAG, "Syncthing exited with code $exitCode")
            } catch (e: Exception) {
                Log.e(TAG, "Syncthing process error", e)
            } finally {
                isRunning = false
                stopSelf()
            }
        }
    }

    private fun stopSyncthing() {
        process?.destroy()
        process = null
        isRunning = false
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncthing")
            .setContentText("同步服務正在執行")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Syncthing 同步服務",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Syncthing 背景資料同步" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
