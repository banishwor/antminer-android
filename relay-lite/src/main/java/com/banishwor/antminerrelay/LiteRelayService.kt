package com.banishwor.antminerrelay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import com.banishwor.antminerrelay.storage.LiteRelayStorage
import com.banishwor.antminerrelay.storage.LiteState
import com.example.antminerconnectiontest.relay.RelayEngine
import com.example.antminerconnectiontest.relay.RelayStatusListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Relay Lite's foreground service: the same thin Android shell around
 * the shared [RelayEngine] that :app's MinerRelayService is — foreground
 * lifecycle + persistent notification — written against API 22
 * framework APIs only:
 *
 *  - No notification channels (they start at API 26; this app targets
 *    22, so the framework Notification.Builder is correct everywhere).
 *  - Wi-Fi state comes from ConnectivityManager.activeNetworkInfo, the
 *    pre-23 API (the app's NetworkCapabilities path starts at 23).
 *  - START_STICKY: if the system kills the service for memory on the
 *    1 GB target phone, it asks to be recreated.
 *
 * The engine's status text is both posted as the notification and
 * persisted via [LiteState], so the setup screen (possibly in a fresh
 * process) shows the relay's actual state.
 */
class LiteRelayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var storage: LiteRelayStorage
    private lateinit var engine: RelayEngine
    private var engineJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            context.startService(Intent(context, LiteRelayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiteRelayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        storage = LiteRelayStorage(applicationContext)
        engine = RelayEngine(
            storage = storage,
            isWifiConnected = { isWifiConnected() },
            listener = RelayStatusListener { title, content -> onEngineStatus(title, content) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LiteState.bumpServiceStarts(applicationContext)
        LiteState.setServiceRunning(applicationContext, true)

        val (title, content, _) = LiteState.loadStatus(applicationContext)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(title ?: "⚡ Antminer Relay Lite Active", content ?: "Preparing sync...")
        )

        startRelayLoop()
        // Keep the watchdog armed for as long as the relay is in use.
        Watchdog.schedule(applicationContext)
        return START_STICKY
    }

    private fun startRelayLoop() {
        engineJob?.cancel()
        engineJob = engine.start(serviceScope)
    }

    /** API 22 Wi-Fi check (activeNetworkInfo predates NetworkCapabilities). */
    @Suppress("DEPRECATION")
    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // Same dedupe as the main app's service (v0.3.1): standby states
    // repeat the identical title/content for hours — re-posting the
    // same notification every 30 s cycle only churns the pipeline.
    private var lastNotificationTitle: String? = null
    private var lastNotificationContent: String? = null

    private fun onEngineStatus(title: String, content: String) {
        LiteState.saveStatus(applicationContext, title, content)
        if (title == lastNotificationTitle && content == lastNotificationContent) return
        lastNotificationTitle = title
        lastNotificationContent = content
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        LiteState.setServiceRunning(applicationContext, false)
        engineJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
