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
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerPool
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RelayUpload
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.withTelemetry
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
    /** Legacy-duplicate healing runs once per service start (best-effort). */
    private var legacyHealDone = false

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
                    if (config.isSyncEnabled && config.userName.isNotBlank() && config.webhookUrl.isNotBlank()) {
                        if (!isWifiConnected()) {
                            updateNotification(
                                "⚡ Relay Standby • Mobile Data",
                                "Paused upload • Wi-Fi connection required for local miners"
                            )
                        } else {
                            val profiles = storageManager.loadSavedProfiles()
                            val snapshots = mutableListOf<RemoteMinerSnapshot>()
                            var deferredCount = 0

                            for (profile in profiles) {
                                try {
                                    val summaryRes = readApi.getSummary(profile)
                                    val summary = summaryRes.getOrNull()

                                    if (summaryRes.isSuccess && summary != null) {
                                        val statsRes = readApi.getStats(profile)
                                        val stats = statsRes.getOrNull()
                                        // Pools + system info feed the
                                        // extended telemetry (v0.1.6) and
                                        // the identity resolution below.
                                        // Best-effort: a failed fetch just
                                        // leaves those fields unknown.
                                        val pools = readApi.getPools(profile).getOrNull().orEmpty()
                                        val systemInfo = readApi.getSystemInfo(profile).getOrNull()

                                        val rate5s = summary.hashrate5s
                                        val avgRate = if (summary.hashrateAvg > 0.0) summary.hashrateAvg else rate5s
                                        // Unknown when the stats fetch failed:
                                        // upload 0 (unknown), not an invented
                                        // 60 °C / 6000 RPM reading.
                                        val maxTemp = stats?.maxChipTemp ?: 0
                                        val maxFan = stats?.maxFanRpm ?: 0

                                        if (rate5s > 0.0 || avgRate > 0.0) {
                                            // Machine identity = the worker
                                            // username, resolved before the
                                            // snapshot exists (see
                                            // resolveSheetId). A machine whose
                                            // identity is not known yet is
                                            // DEFERRED — it is never uploaded
                                            // under this device's profile UUID,
                                            // which used to fork one machine
                                            // into two sheet rows.
                                            val sheetId = resolveSheetId(profile, pools)
                                            if (sheetId == null) {
                                                deferredCount++
                                            }
                                            if (sheetId != null) {
                                                snapshots.add(
                                                    RemoteMinerSnapshot(
                                                        user = config.userName.trim(),
                                                        id = sheetId,
                                                        // Phone-only nicknames never
                                                        // upload (v0.1.7): the nickname
                                                        // field carries the identity.
                                                        nickname = sheetId,
                                                        ip = profile.host,
                                                        hashrate5s = rate5s,
                                                        avgHashrate = avgRate,
                                                        maxTemp = maxTemp,
                                                        maxFan = maxFan,
                                                        status = "Online",
                                                        lastUpdated = ""
                                                    ).withTelemetry(
                                                        summary = summary,
                                                        stats = stats,
                                                        pools = pools,
                                                        systemInfo = systemInfo,
                                                        fallbackModel = profile.cachedModel ?: ""
                                                    )
                                                )
                                            }
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
                                    val deferredNote = if (deferredCount > 0) {
                                        " • $deferredCount deferred (worker name unknown)"
                                    } else {
                                        ""
                                    }
                                    updateNotification(
                                        "⚡ Relay: ${snapshots.size} Miners Active • ${String.format(Locale.US, "%.1f", totalTh)} TH/s",
                                        "Synced at $now to Google Sheet (${config.userName})$deferredNote"
                                    )
                                    if (!legacyHealDone) {
                                        legacyHealDone = true
                                        healLegacyDuplicates(config, snapshots.map { it.id })
                                    }
                                } else {
                                    updateNotification(
                                        "⚠️ Relay Sync Failed",
                                        "${uploadRes.exceptionOrNull()?.message ?: "Check internet connection"}"
                                    )
                                }
                            } else if (deferredCount > 0) {
                                updateNotification(
                                    "⚡ Relay Standby • Upload Deferred",
                                    "$deferredCount miner(s) reachable but their worker name is unknown • Nothing uploaded under a device id"
                                )
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

    /**
     * The machine's sheet identity: its pool worker username. Taken from
     * the profile when known; otherwise read from the miner's own pool
     * config (the same source the dashboard backfills worker names from)
     * and persisted back onto the stored profile, so the identity is
     * learned once and every device/install converges on the same sheet
     * row. Null when the identity cannot be established — the caller
     * defers the upload rather than substituting the profile UUID.
     */
    private suspend fun resolveSheetId(
        profile: MinerProfile,
        prefetchedPools: List<MinerPool>? = null
    ): String? {
        RelayUpload.sheetIdFor(profile)?.let { return it }
        val learned = try {
            (prefetchedPools ?: readApi.getPools(profile).getOrNull().orEmpty())
                .firstOrNull { it.user.isNotBlank() }?.user
        } catch (e: Exception) {
            null
        }
        val sheetId = RelayUpload.sheetIdForWorkerName(learned) ?: return null
        try {
            val stored = storageManager.loadSavedProfiles()
            if (stored.any { it.id == profile.id && it.workerName.isBlank() }) {
                storageManager.saveProfiles(
                    stored.map { if (it.id == profile.id) it.copy(workerName = sheetId) else it }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sheetId
    }

    /**
     * One-shot per service start, best-effort: ask the endpoint to drop
     * this user's legacy duplicate rows — the same machines under old
     * per-device UUID ids (selection in [RelayUpload.legacyDuplicateIds])
     * — now that uploads carry the worker identity. Failures (an older
     * endpoint without delete support) are ignored by design.
     */
    private suspend fun healLegacyDuplicates(config: CloudSyncConfig, canonicalIds: List<String>) {
        try {
            val fetched = cloudSyncManager.fetchRemoteMiners(config.webhookUrl, config.userName)
                .getOrNull() ?: return
            val duplicateIds = RelayUpload.legacyDuplicateIds(fetched.miners, config.userName, canonicalIds)
            if (duplicateIds.isNotEmpty()) {
                cloudSyncManager.deleteRemoteRows(config.webhookUrl, config.userName, duplicateIds)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
