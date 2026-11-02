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
/** Where a fleet row's numbers come from — shown ONLY by this chip. */
enum class FleetSourceChip { LIVE, REMOTE }

/**
 * Everything a Home fleet row is allowed to show (v0.2.0 strict strip):
 * status, nickname / worker, the LIVE-REMOTE chip, the hashrate (or
 * "Offline") and, for offline machines, when the cloud last heard from
 * them. No temperatures, no fan speeds, no pool names, no shares — that
 * detail lives on the tabs. Row surface colour is the same for cloud
 * and local machines; the chip is the only source signal.
 */
data class FleetRowUi(
    val displayName: String,
    /** Worker name for the small second line; "" when it adds nothing. */
    val workerLine: String,
    val chip: FleetSourceChip,
    val isOnline: Boolean,
    /** "%.1f TH/s" while online, "Offline" otherwise. */
    val hashrateLabel: String,
    /**
     * Offline rows: the full "Last updated 2h ago · 14:32" line.
     * Online cloud rows: "Updated 2 min ago". Live rows: "".
     */
    val freshnessLine: String
)

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
        // Never surface a per-device UUID as a "worker name" — rows whose
        // id is a UUID recover their worker name from the nickname.
        return MinerMerge.remoteWorkerCandidates(snapshot)
            .firstOrNull { !MinerMerge.isDeviceId(it) }
            .orEmpty()
    }

    /**
     * The fleet row's second line (pool identity). Normally the pool
     * worker username, prefixed by the pool brand when known.
     *
     * One exception (v0.1.8): when the row's TITLE already is the
     * worker identity — the common case now that uploads carry the
     * identity in the sheet's nickname field, so an un-nicknamed
     * machine's display name IS its worker name — repeating the same
     * string on the subtitle line is noise. The line then identifies
     * the machine by owner user / IP / model instead (whichever are
     * known, title excluded, duplicates collapsed), and is blank when
     * none of them are known. A row with a real nickname (title ≠
     * worker identity) keeps the worker name on this line, as before.
     */
    fun rowSubtitle(row: FleetRow): String {
        val entry = row.entry
        val workerLabel = row.workerName
            .takeUnless { MinerMerge.isDeviceId(it) }
            .orEmpty()
        val title = row.displayName.trim()
        val titleIsWorkerIdentity = workerLabel.isNotBlank() &&
            title.equals(workerLabel, ignoreCase = true)
        if (!titleIsWorkerIdentity) {
            return when {
                workerLabel.isNotBlank() && row.poolBrand != null -> "${row.poolBrand} · $workerLabel"
                workerLabel.isNotBlank() -> workerLabel
                entry.localProfile != null -> entry.localProfile.host
                row.isRemoteOnly -> "Remote machine"
                else -> ""
            }
        }
        val parts = listOfNotNull(
            row.poolBrand,
            entry.remoteSnapshot?.user?.takeIf { it.isNotBlank() },
            entry.remoteSnapshot?.ip?.takeIf { it.isNotBlank() }
                ?: entry.localProfile?.host?.takeIf { it.isNotBlank() },
            entry.remoteSnapshot?.model?.takeIf { it.isNotBlank() }
                ?: entry.localProfile?.cachedModel?.takeIf { it.isNotBlank() }
        ).map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            .distinctBy { it.lowercase() }
        return parts.joinToString(" · ")
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
     * The Home fleet-row projection (see [FleetRowUi]). The chip is
     * REMOTE only while the row is actually showing cloud data — a
     * merged machine with a live reading is LIVE, even though a
     * snapshot also exists for it.
     */
    fun rowUi(row: FleetRow, nowMs: Long): FleetRowUi {
        val entry = row.entry
        val isRemoteData = entry.remoteSnapshot != null &&
            entry.localHashrateTh == null && !entry.hasLiveLocalData
        return FleetRowUi(
            displayName = row.displayName,
            workerLine = row.workerName.takeIf {
                it.isNotBlank() && !MinerMerge.isDeviceId(it) &&
                    !it.equals(row.displayName, ignoreCase = true)
            } ?: "",
            chip = if (isRemoteData) FleetSourceChip.REMOTE else FleetSourceChip.LIVE,
            isOnline = row.isOnline,
            hashrateLabel = if (row.isOnline) {
                String.format(Locale.US, "%.1f TH/s", row.hashrateTh)
            } else {
                "Offline"
            },
            freshnessLine = when {
                !row.isOnline -> formatLastUpdated(row.lastUpdatedEpochMs, nowMs)
                isRemoteData -> formatUpdatedShort(row.lastUpdatedEpochMs, nowMs)
                else -> ""
            }
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
