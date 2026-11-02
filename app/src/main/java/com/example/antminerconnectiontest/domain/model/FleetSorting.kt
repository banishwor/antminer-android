package com.example.antminerconnectiontest.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One display row of the combined fleet list, derived from a
 * [MergedMinerEntry]. Pure view-data: online-ness, the rate to show,
 * the freshest timestamp we honestly know, and the machine's pool
 * identity (worker username; pool brand only when it is actually known).
 */
data class FleetRow(
    val entry: MergedMinerEntry,
    val isOnline: Boolean,
    /** Rate to display (TH/s); 0.0 for offline machines. */
    val hashrateTh: Double,
    /** Freshest timestamp (epoch ms) we can honestly claim, or null. */
    val lastUpdatedEpochMs: Long?,
    /** Pool worker username (e.g. "banishwor.001"), "" when unknown. */
    val workerName: String,
    /** Pool brand (e.g. "ViaBTC") when known from a live pool URL, else null. */
    val poolBrand: String?
) {
    val displayName: String get() = entry.displayName
    val isMerged: Boolean get() = entry.isMerged
    val isRemoteOnly: Boolean get() = entry.localProfile == null && entry.remoteSnapshot != null
    val hasRemote: Boolean get() = entry.remoteSnapshot != null
    val hasLocal: Boolean get() = entry.localProfile != null
    /** Max chip temp (°C) when the remote snapshot carries one. */
    val remoteTempC: Int? get() = entry.remoteSnapshot?.maxTemp?.takeIf { it > 0 }
}

/**
 * Ordering and labelling rules for the combined fleet list (Home):
 * online machines first, fastest first; offline machines below all
 * online ones, most recently updated first. All functions are pure and
 * take `nowMs` explicitly so the rules are unit-testable.
 */
object FleetSorting {

    /** A remote snapshot counts as live for 2 minutes, matching [RemoteMinerSnapshot.isStale]. */
    const val REMOTE_STALE_AFTER_MS = 2 * 60 * 1000L

    fun isRemoteStale(snapshot: RemoteMinerSnapshot, nowMs: Long): Boolean {
        val epoch = snapshot.lastUpdatedEpochMs ?: return false
        return nowMs - epoch > REMOTE_STALE_AFTER_MS
    }

    fun isOnline(entry: MergedMinerEntry, nowMs: Long): Boolean {
        if (entry.hasLiveLocalData) return true
        if ((entry.localHashrateTh ?: 0.0) > 0.0) return true
        val snapshot = entry.remoteSnapshot
        if (snapshot != null) {
            // A snapshot only proves liveness with a verifiable, fresh
            // timestamp — a missing timestamp cannot claim "online".
            val epoch = snapshot.lastUpdatedEpochMs
            if (epoch != null && nowMs - epoch <= REMOTE_STALE_AFTER_MS && snapshot.hashrate5s > 0.0) {
                return true
            }
        }
        return false
    }

    fun lastUpdatedEpochMs(entry: MergedMinerEntry): Long? {
        if (entry.hasLiveLocalData) return null // live right now; UI says "Live"
        entry.remoteSnapshot?.lastUpdatedEpochMs?.let { return it }
        return entry.localProfile?.lastConnected?.takeIf { it > 0L }
    }

    fun workerNameOf(entry: MergedMinerEntry): String {
        entry.localProfile?.workerName?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val snapshot = entry.remoteSnapshot ?: return ""
        return MinerMerge.remoteWorkerCandidates(snapshot).firstOrNull().orEmpty()
    }

    fun rowFor(
        entry: MergedMinerEntry,
        nowMs: Long,
        poolBrand: String? = null
    ): FleetRow {
        val online = isOnline(entry, nowMs)
        return FleetRow(
            entry = entry,
            isOnline = online,
            hashrateTh = if (online) entry.displayHashrateTh else 0.0,
            lastUpdatedEpochMs = lastUpdatedEpochMs(entry),
            workerName = workerNameOf(entry),
            poolBrand = poolBrand
        )
    }

    /**
     * The fleet order: online machines by hashrate descending, then
     * offline machines by most-recent last-update first (machines with
     * no known timestamp sink to the very bottom). Stable for ties.
     */
    fun sort(rows: List<FleetRow>): List<FleetRow> {
        val online = rows.filter { it.isOnline }.sortedByDescending { it.hashrateTh }
        val offline = rows.filter { !it.isOnline }
            .sortedByDescending { it.lastUpdatedEpochMs ?: Long.MIN_VALUE }
        return online + offline
    }

    fun sortEntries(
        entries: List<MergedMinerEntry>,
        nowMs: Long,
        poolBrandFor: (MergedMinerEntry) -> String? = { null }
    ): List<FleetRow> = sort(entries.map { rowFor(it, nowMs, poolBrandFor(it)) })

    /** Fleet total: the sum of the online machines' rates only. */
    fun fleetTotalTh(rows: List<FleetRow>): Double =
        rows.filter { it.isOnline }.sumOf { it.hashrateTh }

    // --- Last-updated labelling -------------------------------------------

    /** "just now" / "2 min ago" / "3 h ago" / "6 days ago". */
    fun formatRelative(epochMs: Long, nowMs: Long): String {
        val diffSec = ((nowMs - epochMs) / 1000L).coerceAtLeast(0)
        return when {
            diffSec < 60 -> "just now"
            diffSec < 3600 -> "${diffSec / 60} min ago"
            diffSec < 86400 -> "${diffSec / 3600} h ago"
            diffSec < 172800 -> "1 day ago"
            else -> "${diffSec / 86400} days ago"
        }
    }

    /** "27 Oct, 9:14 AM" — clock time in the device's timezone. */
    fun formatClock(epochMs: Long): String =
        SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(epochMs))

    /**
     * The offline-row label, both forms together:
     * "Last updated 6 days ago · 27 Oct, 9:14 AM". When no timestamp
     * exists the honest form is "Last updated —" — never an invented time.
     */
    fun formatLastUpdated(epochMs: Long?, nowMs: Long): String {
        if (epochMs == null) return "Last updated —"
        return "Last updated ${formatRelative(epochMs, nowMs)} · ${formatClock(epochMs)}"
    }

    /** Short freshness for online remote rows: "Updated 2 min ago". */
    fun formatUpdatedShort(epochMs: Long?, nowMs: Long): String {
        if (epochMs == null) return ""
        return "Updated ${formatRelative(epochMs, nowMs)}"
    }

    /**
     * Pool brand from a pool URL, for the one machine whose live pool
     * list we actually hold. Unknown hosts fall back to the bare host
     * name; blank/unparseable input yields null (show no brand rather
     * than a guessed one).
     */
    fun poolBrandFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = url.trim()
            .substringAfter("://", url.trim())
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()
        if (host.isBlank()) return null
        return when {
            host.contains("viabtc") -> "ViaBTC"
            host.contains("antpool") -> "AntPool"
            host.contains("f2pool") -> "F2Pool"
            host.contains("poolin") -> "Poolin"
            host.contains("btc.com") -> "BTC.com"
            host.contains("slushpool") || host.contains("braiins") -> "Braiins"
            else -> host.removePrefix("stratum.").removePrefix("pool.")
                .takeIf { it.isNotBlank() }
        }
    }
}
