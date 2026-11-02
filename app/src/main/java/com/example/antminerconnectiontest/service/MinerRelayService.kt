package com.example.antminerconnectiontest.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.antminerconnectiontest.MainActivity
import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.data.cloud.CloudSyncManager
import com.example.antminerconnectiontest.data.storage.MinerStorageManager
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 24/7 Foreground Service that keeps syncing local miners to the Google Sheet
 * even when the screen is locked/turned off or the app is closed.
 */
class MinerRelayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cloudSyncManager = CloudSyncManager()
    private val readApi: MinerReadApi = AntminerReadApiImpl()
    private lateinit var storageManager: MinerStorageManager
    private var syncJob: Job? = null

    companion object {
        const val CHANNEL_ID = "antminer_cloud_relay_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MinerRelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MinerRelayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        storageManager = MinerStorageManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("⚡ Antminer Cloud Relay Active • Preparing sync...")
        startForeground(NOTIFICATION_ID, notification)

        startRelayLoop()

        return START_STICKY
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Throwable) {
            false
        }
    }

    private fun startRelayLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val config = storageManager.loadCloudConfig()
                    if (config.isSyncEnabled && config.isAutoUploadEnabled && config.userName.isNotBlank() && config.webhookUrl.isNotBlank()) {
                        if (!isWifiConnected()) {
                            updateNotification(
                                "⚡ Relay Standby • Mobile Data",
                                "Paused upload • Wi-Fi connection required for local miners"
                            )
                        } else {
                            val profiles = storageManager.loadSavedProfiles()
                            val snapshots = mutableListOf<RemoteMinerSnapshot>()

                            for (profile in profiles) {
                                try {
                                    val summaryRes = readApi.getSummary(profile)
                                    val summary = summaryRes.getOrNull()

                                    if (summaryRes.isSuccess && summary != null) {
                                        val statsRes = readApi.getStats(profile)
                                        val stats = statsRes.getOrNull()

                                        val rate5s = summary.hashrate5s
                                        val avgRate = if (summary.hashrateAvg > 0.0) summary.hashrateAvg else rate5s
                                        // Unknown when the stats fetch failed:
                                        // upload 0 (unknown), not an invented
                                        // 60 °C / 6000 RPM reading.
                                        val maxTemp = stats?.maxChipTemp ?: 0
                                        val maxFan = stats?.maxFanRpm ?: 0

                                        if (rate5s > 0.0 || avgRate > 0.0) {
                                            snapshots.add(
                                                RemoteMinerSnapshot(
                                                    user = config.userName.trim(),
                                                    id = profile.id,
                                                    nickname = profile.effectiveName,
                                                    ip = profile.host,
                                                    hashrate5s = rate5s,
                                                    avgHashrate = avgRate,
                                                    maxTemp = maxTemp,
                                                    maxFan = maxFan,
                                                    status = "Online",
                                                    lastUpdated = ""
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (snapshots.isNotEmpty()) {
                                val uploadRes = cloudSyncManager.uploadLocalMiners(config.webhookUrl, config.userName, snapshots)
                                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val now = sdf.format(Date())
                                val totalTh = snapshots.sumOf { it.hashrate5s }

                                if (uploadRes.isSuccess) {
                                    updateNotification(
                                        "⚡ Relay: ${snapshots.size} Miners Active • ${String.format(Locale.US, "%.1f", totalTh)} TH/s",
                                        "Synced at $now to Google Sheet (${config.userName})"
                                    )
                                } else {
                                    updateNotification(
                                        "⚠️ Relay Sync Failed",
                                        "${uploadRes.exceptionOrNull()?.message ?: "Check internet connection"}"
                                    )
                                }
                            } else {
                                updateNotification(
                                    "⚡ Relay Standby • 0 Miners Online",
                                    "Local miners unreachable on current Wi-Fi • Upload skipped"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(30000L) // Poll every 30 seconds in background
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Antminer Cloud Relay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps syncing local miners to Google Sheet 24/7 in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String = "Syncing local miners in background..."): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
