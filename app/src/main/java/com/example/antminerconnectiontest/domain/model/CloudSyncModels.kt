package com.example.antminerconnectiontest.domain.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Configuration for Google Sheet Cloud Relay & Multi-Site Sync.
 *
 * There is exactly ONE user-facing switch: [isSyncEnabled] — "Relay".
 * ON means the app uploads local miners under [userName] AND the 24/7
 * background relay service runs. OFF means no uploads and no background
 * service. Viewing remote machines is NOT gated by the switch: the
 * sheet is fetched whenever a user name is set, relay on or off.
 *
 * The three legacy flags are kept only so stored configs and the
 * persisted keys stay meaningful; [updateCloudConfig] always writes
 * them derived from the single switch (upload = switch, background =
 * switch, download = always true), and nothing reads them for gating
 * anymore.
 */
data class CloudSyncConfig(
    val webhookUrl: String = DEFAULT_WEBHOOK_URL,
    val userName: String = "",
    /**
     * The Relay switch. ON by default: relaying is the point of the app
     * for multi-site users, and nothing is uploaded until a user name is
     * set — the sync cycle requires one. A stored OFF (the user chose it)
     * is always respected.
     */
    val isSyncEnabled: Boolean = true,
    /** Legacy mirror of [isSyncEnabled]; not a separate user control. */
    val isAutoUploadEnabled: Boolean = true,
    /** Legacy: remote viewing is always on when a user name is set. */
    val isDownloadRemoteEnabled: Boolean = true,
    /** Legacy mirror of [isSyncEnabled]; not a separate user control. */
    val isBackgroundServiceEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbyHfUeMDm_pnG_PzIeakyxFuWR1QtQIMGwuxj5y26QC_Vhf0i8H-u0EdngMXsDqGTyX1Q/exec"
    }
}

/**
 * Real-time snapshot of a remote miner fetched from Google Sheets.
 */
data class RemoteMinerSnapshot(
    val user: String,
    val id: String,
    val nickname: String,
    val ip: String,
    val hashrate5s: Double,
    val avgHashrate: Double,
    val maxTemp: Int,
    val maxFan: Int,
    val status: String,
    val lastUpdated: String
) {
    val effectiveDisplayName: String
        get() = nickname.ifBlank { "$user Miner ($ip)" }

    /**
     * Parses the Google Sheet LastUpdated timestamp into epoch milliseconds.
     */
    val lastUpdatedEpochMs: Long?
        get() {
            if (lastUpdated.isBlank()) return null
            // Check numeric epoch
            lastUpdated.toLongOrNull()?.let { return it }

            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss",
                "dd/MM/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss"
            )

            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    if (pattern.contains("'Z'")) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val date = sdf.parse(lastUpdated)
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
            return null
        }

    /**
     * Returns true if no update has been received for more than 2 minutes (120,000 ms).
     */
    val isStale: Boolean
        get() {
            val epoch = lastUpdatedEpochMs ?: return false
            val elapsedMs = System.currentTimeMillis() - epoch
            return elapsedMs > 2 * 60 * 1000L // 2 minutes timeout
        }

    /**
     * Real-time hashrate drops to 0.0 if the cloud signal is stale / offline.
     */
    val effectiveHashrate5s: Double
        get() = if (isStale) 0.0 else hashrate5s

    val effectiveAvgHashrate: Double
        get() = if (isStale) 0.0 else avgHashrate

    val effectiveStatus: String
        get() = if (isStale) "Offline (No Signal >2m)" else status

    /**
     * Human-readable relative time string (e.g. "18s ago", "3m ago", "1h ago").
     */
    val timeAgoFormatted: String
        get() {
            val epoch = lastUpdatedEpochMs ?: return lastUpdated.ifBlank { "Just now" }
            val diffSec = ((System.currentTimeMillis() - epoch) / 1000L).coerceAtLeast(0)
            return when {
                diffSec < 60 -> "${diffSec}s ago"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                else -> "${diffSec / 86400}d ago"
            }
        }
}

/**
 * Response structure returned by Google Apps Script Webhook.
 */
data class CloudSyncResponse(
    val status: String,
    val user: String? = null,
    val canView: String? = null,
    val miners: List<RemoteMinerSnapshot> = emptyList(),
    val message: String? = null
)
