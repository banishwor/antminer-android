package com.example.antminerconnectiontest.domain.model

/**
 * Write-side identity rules for cloud relay uploads.
 *
 * A machine's ONE stable identity on the Google Sheet is its normalised
 * pool worker username ("banishwor.001") — it names the machine itself
 * and is identical on every phone and every install. The per-device
 * profile UUID ([MinerProfile.id]) names a row in one phone's storage;
 * it must NEVER reach the sheet.
 *
 * Until v0.1.4 both upload sites used `workerName.ifBlank { profile.id }`:
 * a profile whose worker name had not been learned yet (fresh profile,
 * fresh install — the name is only backfilled from the miner's pool
 * config on the first successful dashboard fetch) relayed under its
 * profile UUID. Once the name was learned, uploads switched to the
 * worker name, leaving the same machine on the sheet under TWO ids
 * forever; a reinstall minted yet another UUID. v0.1.4 collapsed those
 * rows on the read side ([MinerMerge.dedupeSnapshots]); this object
 * stops them being created and selects the legacy rows a relay write
 * asks the endpoint to remove, so dirty sheets heal at the source.
 */
object RelayUpload {

    /**
     * The sheet id for a pool worker name: trimmed (stray whitespace
     * must not fork a machine into two sheet rows), case preserved (the
     * string comes from the miner's own pool config and is identical on
     * every device; existing worker-keyed rows carry exactly it, so an
     * upsert keyed on it replaces the machine's row in place). Null when
     * the name is blank — the machine's identity is not known yet.
     */
    fun sheetIdForWorkerName(workerName: String?): String? =
        workerName?.trim()?.takeIf { it.isNotBlank() }

    /**
     * The sheet id for a saved profile, or null when the profile does
     * not know its machine's worker name yet. Null means DEFER: the
     * caller must not upload this machine at all this cycle — uploading
     * it under any substitute id (profile UUID, host, nickname) would
     * fork the machine's sheet identity. The name is resolved from the
     * miner's pool config on connect / dashboard fetch, so a deferred
     * machine starts uploading as soon as it has been connected once.
     */
    fun sheetIdFor(profile: MinerProfile): String? = sheetIdForWorkerName(profile.workerName)

    /**
     * Final gate applied to every upload batch, whoever built it: drops
     * snapshots whose id is blank or a per-device UUID (no caller can
     * ever push a UUID-keyed row again), canonicalises ids to their
     * trimmed form, and collapses in-batch duplicates by normalised
     * identity (last one wins — it carries the freshest reading).
     *
     * The sheet `nickname` field is rewritten to the worker identity
     * too (v0.1.7): nicknames are phone-only and never upload, and the
     * identity string is exactly what older app versions parse back out
     * of the nickname field to recover a row's worker name, so legacy
     * readers keep working unchanged.
     */
    fun prepareSnapshots(snapshots: List<RemoteMinerSnapshot>): List<RemoteMinerSnapshot> {
        val byIdentity = LinkedHashMap<String, RemoteMinerSnapshot>()
        for (snapshot in snapshots) {
            val id = sheetIdForWorkerName(snapshot.id) ?: continue
            if (MinerMerge.isDeviceId(id)) continue
            byIdentity[MinerMerge.normalizeWorkerName(id)] = snapshot.copy(id = id, nickname = id)
        }
        return byIdentity.values.toList()
    }

    /**
     * Selects the sheet rows a relay for [canonicalIds] (the machine ids
     * this user now writes, as returned by [sheetIdFor]) should ask the
     * endpoint to delete: rows OWNED BY [uploadUser] that provably
     * describe one of those machines (worker identity recovered per
     * [MinerMerge.workerIdentity], so UUID-keyed rows whose nickname
     * carries the worker name qualify, as do case-variant worker rows)
     * but whose stored id is not itself the canonical id being written.
     * Other users' rows are never selected — the fetch can return
     * machines this user may merely view. Returns the rows' raw stored
     * ids, distinct, in sheet order.
     */
    fun legacyDuplicateIds(
        fetched: List<RemoteMinerSnapshot>,
        uploadUser: String,
        canonicalIds: Collection<String>
    ): List<String> {
        val user = uploadUser.trim().lowercase()
        if (user.isBlank()) return emptyList()
        val canonicalRaw = canonicalIds.mapNotNull { sheetIdForWorkerName(it) }.toSet()
        val canonicalNorm = canonicalRaw.map { MinerMerge.normalizeWorkerName(it) }.toSet()
        if (canonicalNorm.isEmpty()) return emptyList()

        val out = LinkedHashSet<String>()
        for (row in fetched) {
            if (row.user.trim().lowercase() != user) continue
            val rawId = row.id.trim()
            if (rawId.isBlank() || rawId in canonicalRaw) continue
            val identity = MinerMerge.workerIdentity(row) ?: continue
            if (identity in canonicalNorm) out += rawId
        }
        return out.toList()
    }
}
