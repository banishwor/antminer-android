package com.example.antminerconnectiontest.domain.model

import org.json.JSONObject

/**
 * Phone-only nicknames (v0.1.7).
 *
 * A nickname is a label the user of THIS phone gives a machine ("Hall 1,
 * Left"). It lives only in this phone's app-private storage: it is never
 * uploaded to the cloud sheet (uploads carry the worker identity in the
 * sheet's nickname field instead — see [RelayUpload]), never synced, and
 * the storage file is excluded from Android backup / device transfer
 * alongside the saved profiles. Another phone looking at the same machine
 * sees its own nickname, or none.
 *
 * Keys are the machine's identity, never its display name: the normalised
 * pool worker username (trim + lowercase, the same normalisation
 * [MinerMerge] matches on), falling back to the profile id / snapshot id
 * when no worker identity is known yet. Because the key follows the
 * worker identity, a nickname set while a machine is remote-only keeps
 * applying when the machine is later added locally (and vice versa), and
 * renaming can never change how machines merge — identity and merging do
 * not depend on nicknames in any way.
 */
object PhoneNicknames {

    /** Storage key for a worker identity, or null when there is none. */
    fun workerKey(workerName: String?): String? =
        MinerMerge.normalizeWorkerName(workerName).takeIf { it.isNotBlank() }

    /** Storage key for a machine with no known worker identity. */
    fun fallbackKey(rawId: String?): String? {
        val id = rawId?.trim()?.lowercase().orEmpty()
        return if (id.isBlank()) null else "id:$id"
    }

    fun keyForProfile(profile: MinerProfile): String =
        workerKey(profile.workerName) ?: fallbackKey(profile.id) ?: ""

    fun keyForSnapshot(snapshot: RemoteMinerSnapshot): String =
        MinerMerge.workerIdentity(snapshot)
            ?: fallbackKey(snapshot.id)
            ?: fallbackKey("${snapshot.user}|${snapshot.ip}")
            ?: ""

    /**
     * The single key an edit writes to: the worker identity whenever
     * either side of the entry proves one, else the fallback id key.
     */
    fun keyForEntry(entry: MergedMinerEntry): String {
        entry.localProfile?.let { profile ->
            workerKey(profile.workerName)?.let { return it }
        }
        entry.remoteSnapshot?.let { snapshot ->
            MinerMerge.workerIdentity(snapshot)?.let { return it }
        }
        entry.localProfile?.let { return keyForProfile(it) }
        entry.remoteSnapshot?.let { return keyForSnapshot(it) }
        return entry.key
    }

    /**
     * Every key a machine might have a nickname stored under, strongest
     * first. A nickname written before the worker identity was learned
     * sits under the fallback id key; once the identity is known the edit
     * writes the worker key — lookups try both so the older label is not
     * orphaned.
     */
    fun keyCandidates(profile: MinerProfile?, snapshot: RemoteMinerSnapshot?): List<String> {
        val out = LinkedHashSet<String>()
        profile?.let { workerKey(it.workerName)?.let(out::add) }
        snapshot?.let { MinerMerge.workerIdentity(it)?.let(out::add) }
        profile?.let { fallbackKey(it.id)?.let(out::add) }
        snapshot?.let { fallbackKey(it.id)?.let(out::add) }
        return out.toList()
    }

    /** The phone nickname for a machine, or null when none is set. */
    fun overrideFor(
        overrides: Map<String, String>,
        profile: MinerProfile?,
        snapshot: RemoteMinerSnapshot?
    ): String? {
        for (key in keyCandidates(profile, snapshot)) {
            overrides[key]?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    fun overrideFor(overrides: Map<String, String>, entry: MergedMinerEntry): String? =
        overrideFor(overrides, entry.localProfile, entry.remoteSnapshot)

    /**
     * Sets (or, for a blank [nickname], clears) the nickname stored under
     * [key], returning the new override map. Pure — persistence is the
     * caller's job.
     */
    fun withNickname(
        overrides: Map<String, String>,
        key: String,
        nickname: String
    ): Map<String, String> {
        if (key.isBlank()) return overrides
        val clean = nickname.trim()
        return if (clean.isBlank()) {
            overrides - key
        } else {
            overrides + (key to clean)
        }
    }

    /**
     * A snapshot's nickname counts as a real user nickname only when it
     * is not just the machine's worker identity restated: current
     * uploads put the worker identity itself in the nickname field, and
     * older ones put the uploader's effectiveName ("worker (Model)") —
     * neither is a name anyone chose for display.
     */
    fun snapshotNickname(snapshot: RemoteMinerSnapshot): String? {
        val nick = snapshot.nickname.trim()
        if (nick.isBlank()) return null
        val identity = MinerMerge.workerIdentity(snapshot)
        if (identity != null) {
            if (MinerMerge.normalizeWorkerName(nick) == identity) return null
            if (MinerMerge.normalizeWorkerName(nick.substringBefore(" (")) == identity) return null
        }
        return nick
    }

    /**
     * The name every surface shows for a machine, in precedence order:
     * phone nickname override → local profile nickname → remote snapshot
     * nickname → worker identity → the previous fallbacks (profile
     * effective name, then the snapshot's "user Miner (ip)").
     */
    fun displayName(
        overrides: Map<String, String>,
        profile: MinerProfile?,
        snapshot: RemoteMinerSnapshot?
    ): String {
        overrideFor(overrides, profile, snapshot)?.let { return it }
        profile?.nickname?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        snapshot?.let { snap -> snapshotNickname(snap)?.let { return it } }
        profile?.let { return it.effectiveName }
        snapshot?.let { snap -> MinerMerge.workerIdentity(snap)?.let { return it } }
        snapshot?.let { return it.effectiveDisplayName }
        return "Antminer"
    }

    fun displayName(overrides: Map<String, String>, entry: MergedMinerEntry): String =
        displayName(overrides, entry.localProfile, entry.remoteSnapshot)

    // --- Persistence encoding (a flat JSON object: key → nickname) ------

    fun encode(overrides: Map<String, String>): String {
        val obj = JSONObject()
        for ((key, value) in overrides) {
            if (key.isNotBlank() && value.isNotBlank()) obj.put(key, value)
        }
        return obj.toString()
    }

    /** Tolerant decode: malformed storage yields no nicknames, never a crash. */
    fun parse(stored: String?): Map<String, String> {
        if (stored.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(stored)
            val out = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "").trim()
                if (key.isNotBlank() && value.isNotBlank()) out[key] = value
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
