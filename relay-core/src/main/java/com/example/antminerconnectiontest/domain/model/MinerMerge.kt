package com.example.antminerconnectiontest.domain.model

/**
 * One row in the unified miner list: a physical machine, seen locally,
 * remotely, or both.
 *
 * The admin's fleet lives in two places at once — saved [MinerProfile]s on
 * this phone and [RemoteMinerSnapshot]s in the cloud sheet. The same machine
 * must appear as ONE entry everywhere (dashboard selection, switcher, race
 * list), and when live local data for it exists it wins over the remote
 * snapshot, because it is fresher.
 */
data class MergedMinerEntry(
    /** Stable identity of the machine within the merged list. */
    val key: String,
    val localProfile: MinerProfile? = null,
    val remoteSnapshot: RemoteMinerSnapshot? = null,
    /**
     * Best live local hashrate (TH/s) for this machine right now: the
     * dashboard reading when this machine is the active profile, otherwise
     * the race poller's latest reading. Null when no live local reading
     * exists (away from the site, miner not answering, never polled).
     */
    val localHashrateTh: Double? = null,
    /**
     * True when the full live dashboard data currently on screen belongs to
     * this machine — the strongest form of "local data is available", used
     * to let local data overwrite the remote snapshot in the dashboard hero.
     */
    val hasLiveLocalData: Boolean = false,
    /**
     * Display name resolved with this phone's private nickname layer
     * applied (see [PhoneNicknames.displayName]). Set by the ViewModel,
     * which owns the override store; null in pure merge contexts (unit
     * tests), where [displayName] falls back to the pre-v0.1.7 chain.
     */
    val phoneDisplayName: String? = null
) {
    val isMerged: Boolean get() = localProfile != null && remoteSnapshot != null

    /** Rate lists should display: live local wins, remote is the fallback. */
    val displayHashrateTh: Double
        get() = localHashrateTh ?: remoteSnapshot?.effectiveHashrate5s ?: 0.0

    val displayName: String
        get() = phoneDisplayName ?: localProfile?.effectiveName ?: remoteSnapshot?.effectiveDisplayName ?: "Antminer"
}

/**
 * Merges saved local profiles and cloud snapshots into one entry per
 * physical machine.
 *
 * The machine identity is its **worker name** (the pool username, e.g.
 * "banishwor.001") — the only identifier that names the machine itself
 * rather than a phone, an app install, or a DHCP lease:
 * - [MinerProfile.workerName] carries it directly.
 * - On the remote side the worker name arrives in [RemoteMinerSnapshot.id]
 *   (uploads from this app put it there — the profile UUID it replaced was
 *   per-device and useless for matching) and, for rows uploaded by older
 *   versions, embedded in [RemoteMinerSnapshot.nickname], which is the
 *   uploader's `effectiveName`: either the worker name itself or
 *   "workerName (Model)".
 * - MAC/hostname are not present in the sheet payload at all, so they
 *   cannot be used remotely. As a last resort for legacy rows whose
 *   nickname is a free-form user nickname, an unambiguous IP pairing
 *   (exactly one unmatched profile and one unmatched snapshot share the
 *   host) is accepted.
 *
 * Matching is normalisation-insensitive (trim + case-insensitive), a
 * profile can be claimed by at most one snapshot and vice versa, and the
 * output order is deterministic — local profiles in saved order, then
 * remote-only machines in sheet order — so recomputing the merge on every
 * poll/sync cycle never reshuffles or flickers the list.
 */
object MinerMerge {

    /** Live local readings available at merge time. */
    data class LocalData(
        val activeProfileId: String? = null,
        /** [MinerDashboardData.profileId] of the dashboard data on screen. */
        val activeDataProfileId: String? = null,
        val activeDataOnline: Boolean = false,
        val activeHashrateTh: Double? = null,
        /** Race poller readings by profile id. */
        val raceHashrates: Map<String, Double> = emptyMap()
    )

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * True when [raw] is a per-device / per-install id (a UUID) rather
     * than a pool worker name. Sheet rows uploaded while the uploader's
     * profile did not know its worker name yet (uploads use
     * `workerName.ifBlank { profile.id }`), and rows from older versions,
     * carry the profile UUID in the `id` field. A UUID names a phone's
     * saved profile, never the machine — it must not be treated as a
     * worker identity, and it must never be displayed as one.
     */
    fun isDeviceId(raw: String?): Boolean =
        UUID_REGEX.matches(raw?.trim().orEmpty())

    fun normalizeWorkerName(raw: String?): String = raw?.trim()?.lowercase().orEmpty()

    /**
     * Worker-name candidates a remote snapshot carries, strongest first.
     * Blank candidates are dropped; a blank result means "no worker
     * identity", which must never match anything.
     */
    fun remoteWorkerCandidates(snapshot: RemoteMinerSnapshot): List<String> {
        val out = LinkedHashSet<String>()
        // Uploads from this app put the machine's worker name in `id` —
        // unless the row carries a per-device UUID there (see isDeviceId),
        // which is not a worker name and must not become a candidate: it
        // can never equal a profile's worker name, and it used to leak
        // into the fleet UI as the row's "worker name".
        if (!isDeviceId(snapshot.id)) {
            normalizeWorkerName(snapshot.id).takeIf { it.isNotBlank() }?.let(out::add)
        }
        // Older uploads: nickname is the uploader's effectiveName — the
        // worker name itself, or "workerName (Model)".
        val nick = snapshot.nickname.trim()
        if (nick.isNotBlank()) {
            normalizeWorkerName(nick.substringBefore(" (")).takeIf { it.isNotBlank() }?.let(out::add)
            normalizeWorkerName(nick).takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out.toList()
    }

    /**
     * The machine identity a snapshot can prove, normalised, or null when
     * the row carries no worker identity at all. The `id` field wins when
     * it holds a worker name; for UUID-id rows the identity is recovered
     * from the nickname, which is the uploader's `effectiveName`
     * ("workerName" or "workerName (Model)") — but only when the part
     * before the model suffix actually looks like a worker name
     * ("account.worker"), so a free-form user nickname ("Nepra #1") is
     * never mistaken for an identity.
     */
    fun workerIdentity(snapshot: RemoteMinerSnapshot): String? {
        if (!isDeviceId(snapshot.id)) {
            normalizeWorkerName(snapshot.id).takeIf { it.isNotBlank() }?.let { return it }
        }
        val nickHead = normalizeWorkerName(snapshot.nickname.trim().substringBefore(" ("))
        return nickHead.takeIf { looksLikeWorkerName(it) }
    }

    /** Pool worker names are "account.worker" — a dot with both sides non-blank. */
    private fun looksLikeWorkerName(normalized: String): Boolean {
        val dot = normalized.indexOf('.')
        return dot > 0 && dot < normalized.length - 1
    }

    /**
     * Collapses sheet rows that describe the same machine into one,
     * keeping the LATEST snapshot — the sheet can hold several rows per
     * machine from different relay times and different id forms (worker
     * name vs per-device UUID), and the newest relay is the truth.
     * Rows are keyed by worker identity when one is provable, otherwise
     * by their raw id / row content. First-seen order is preserved.
     */
    fun dedupeSnapshots(snapshots: List<RemoteMinerSnapshot>): List<RemoteMinerSnapshot> {
        val byKey = LinkedHashMap<String, RemoteMinerSnapshot>()
        for (snapshot in snapshots) {
            val key = snapshotIdentity(snapshot)
            val existing = byKey[key]
            if (existing == null || isAtLeastAsFresh(snapshot, existing)) {
                byKey[key] = snapshot
            }
        }
        return byKey.values.toList()
    }

    /**
     * Latest wins: compares parsed sheet timestamps; a row with no
     * parseable timestamp is the oldest possible. Ties go to the later
     * row — the sheet appends newer relays after older ones.
     */
    private fun isAtLeastAsFresh(
        candidate: RemoteMinerSnapshot,
        existing: RemoteMinerSnapshot
    ): Boolean {
        val c = candidate.lastUpdatedEpochMs ?: Long.MIN_VALUE
        val e = existing.lastUpdatedEpochMs ?: Long.MIN_VALUE
        return c >= e
    }

    /**
     * Finds the saved local profile for a remote snapshot, or null when the
     * machine is not saved locally. Worker-name match first, then the
     * unambiguous-IP fallback.
     */
    fun findLocalMatch(
        snapshot: RemoteMinerSnapshot,
        profiles: List<MinerProfile>
    ): MinerProfile? {
        val candidates = remoteWorkerCandidates(snapshot).toSet()
        if (candidates.isNotEmpty()) {
            profiles.firstOrNull { profile ->
                val worker = normalizeWorkerName(profile.workerName)
                worker.isNotBlank() && worker in candidates
            }?.let { return it }
        }
        // The IP fallback is for identity-less legacy rows ONLY
        // (v0.5.3): when the snapshot carries a provable worker identity
        // that no profile matched above, it is a DIFFERENT machine —
        // pairing it with the one profile that happens to share its
        // (cross-site, reused) IP would hand that machine's snapshot to
        // the wrong profile.
        if (workerIdentity(snapshot) != null) return null
        val ip = snapshot.ip.trim()
        if (ip.isNotBlank()) {
            val byIp = profiles.filter { it.host.trim() == ip }
            if (byIp.size == 1) return byIp.first()
        }
        return null
    }

    fun merge(
        profiles: List<MinerProfile>,
        snapshots: List<RemoteMinerSnapshot>,
        localData: LocalData = LocalData()
    ): List<MergedMinerEntry> {
        // 1) Drop duplicate sheet rows for the same machine, keeping the
        //    LATEST relay (see dedupeSnapshots) so a duplicated row can
        //    never produce a duplicated entry — including duplicates that
        //    arrive under different id forms (worker name vs UUID).
        val uniqueList = dedupeSnapshots(snapshots)

        val claimedSnapshotIdx = mutableSetOf<Int>()
        val snapshotForProfile = mutableMapOf<String, RemoteMinerSnapshot>()

        // 2) Worker-name matching — the machine's real identity.
        for (profile in profiles) {
            val worker = normalizeWorkerName(profile.workerName)
            if (worker.isBlank()) continue
            val idx = uniqueList.indices.firstOrNull { i ->
                i !in claimedSnapshotIdx && worker in remoteWorkerCandidates(uniqueList[i])
            }
            if (idx != null) {
                claimedSnapshotIdx += idx
                snapshotForProfile[profile.id] = uniqueList[idx]
            }
        }

        // 3) IP fallback for legacy rows, only when the pairing is
        //    unambiguous on both sides (private IPs repeat across sites)
        //    AND the row carries no provable worker identity (v0.5.3):
        //    a row that proves a worker identity either matched its
        //    profile in step 2 or belongs to a different machine — an
        //    address must never pair it with a profile whose known
        //    identity failed to match.
        for (profile in profiles) {
            if (snapshotForProfile.containsKey(profile.id)) continue
            val host = profile.host.trim()
            if (host.isBlank()) continue
            val profilesWithHost = profiles.count { it.host.trim() == host }
            if (profilesWithHost != 1) continue
            val candidateIdx = uniqueList.indices.filter { i ->
                i !in claimedSnapshotIdx && uniqueList[i].ip.trim() == host &&
                    workerIdentity(uniqueList[i]) == null
            }
            if (candidateIdx.size == 1) {
                claimedSnapshotIdx += candidateIdx.first()
                snapshotForProfile[profile.id] = uniqueList[candidateIdx.first()]
            }
        }

        // 4) Emit: local profiles in saved order, then remote-only rows in
        //    sheet order. Deterministic across recomputations.
        val entries = mutableListOf<MergedMinerEntry>()
        for (profile in profiles) {
            val snapshot = snapshotForProfile[profile.id]
            val hasLive = localData.activeDataOnline &&
                localData.activeProfileId == profile.id &&
                localData.activeDataProfileId == profile.id
            val localRate = when {
                hasLive -> localData.activeHashrateTh
                else -> localData.raceHashrates[profile.id]?.takeIf { it > 0.0 }
            }
            entries += MergedMinerEntry(
                key = "local:${profile.id}",
                localProfile = profile,
                remoteSnapshot = snapshot,
                localHashrateTh = localRate,
                hasLiveLocalData = hasLive
            )
        }
        uniqueList.forEachIndexed { idx, snapshot ->
            if (idx !in claimedSnapshotIdx) {
                entries += MergedMinerEntry(
                    key = "remote:${snapshotIdentity(snapshot)}",
                    localProfile = null,
                    remoteSnapshot = snapshot
                )
            }
        }
        return entries
    }

    /**
     * Identity used to dedupe sheet rows and to key remote-only entries.
     * The worker identity wins when one is provable — that is what makes
     * the same machine collapse into one entry even when its sheet rows
     * carry different `id` forms (worker name in current uploads, the
     * uploader's per-device profile UUID in older/early ones). Rows with
     * no provable worker identity fall back to their raw id, then to
     * their row content.
     */
    private fun snapshotIdentity(snapshot: RemoteMinerSnapshot): String {
        workerIdentity(snapshot)?.let { return "worker:$it" }
        val id = snapshot.id.trim().lowercase()
        return if (id.isNotBlank()) {
            "id:$id"
        } else {
            "row:${snapshot.user.trim().lowercase()}|${snapshot.ip.trim()}|${snapshot.nickname.trim().lowercase()}"
        }
    }
}
