package com.example.antminerconnectiontest.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.antminerconnectiontest.MainActivity
import com.example.antminerconnectiontest.data.storage.MinerStorageManager
import com.example.antminerconnectiontest.relay.RelayEngine
import com.example.antminerconnectiontest.relay.RelayStatusListener
import kotlinx.coroutines.*

/**
 * 24/7 Foreground Service that keeps syncing local miners to the Google Sheet
 * even when the screen is locked/turned off or the app is closed.
 *
 * Thin Android shell (Step 2): everything the relay DOES — fleet fetch,
 * background auto-connect, polling, identity resolution, the cross-user
 * upload guard, uploads, legacy-duplicate healing — lives in
 * [RelayEngine] in :relay-core. This class owns only what is genuinely
 * Android: the foreground lifecycle, the notification channel, and
 * rendering the engine's status text as the persistent notification.
 */
class MinerRelayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var storageManager: MinerStorageManager
    private lateinit var engine: RelayEngine
    private var engineJob: Job? = null

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
        engine = RelayEngine(
            storage = storageManager,
            isWifiConnected = { isWifiConnected() },
            listener = RelayStatusListener { title, content -> updateNotification(title, content) }
        )
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
        engineJob?.cancel()
        engineJob = engine.start(serviceScope)
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

    // The last notification actually posted (v0.3.1): standby states
    // repeat the identical title/content for hours (e.g. "0 Miners
    // Online" overnight) — re-posting the same notification every
    // 30 s cycle only churns the notification pipeline, so identical
    // repeats are skipped. Anything that changes the text (miner
    // count, hashrate, sync time, error) posts as before.
    private var lastNotificationTitle: String? = null
    private var lastNotificationContent: String? = null

    private fun updateNotification(title: String, content: String) {
        if (title == lastNotificationTitle && content == lastNotificationContent) return
        lastNotificationTitle = title
        lastNotificationContent = content
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        super.onDestroy()
        engineJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
