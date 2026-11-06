package com.example.antminerconnectiontest.relay

import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerProfile

/**
 * The persistence the [RelayEngine] needs, behind an interface.
 *
 * The engine never touches Android storage directly: the main app
 * supplies its MinerStorageManager (SharedPreferences + Android
 * Keystore AES-256-GCM, API 23+), whose on-disk format this interface
 * deliberately says nothing about. That is the seam a future API 22
 * implementation plugs into — Relay Lite can back the same four calls
 * with the API 22 RSA-wrapped-AES vault the :relay-probe module proved
 * out on the Vivo Y21L, without the engine changing at all.
 *
 * Implementations must preserve the established storage contract:
 * profile JSON field names, preference keys and the cloud config keys
 * are all load-bearing (existing installs migrate in place).
 */
interface RelayStorage {

    /** The single Relay switch + webhook URL + relay user name. */
    fun loadCloudConfig(): CloudSyncConfig

    /** All saved miner profiles, credentials decrypted. */
    fun loadSavedProfiles(): List<MinerProfile>

    /** Persists the full profile list, credentials encrypted at rest. */
    fun saveProfiles(profiles: List<MinerProfile>)

    /**
     * The relay's own report of itself: last successful upload time
     * (null keeps the stored one) and last upload error (null clears
     * it). The relay-only status screen reads this back.
     */
    fun saveRelayStatus(lastUploadMs: Long?, error: String?)

    /**
     * Per-miner live state (v0.1.2): the engine reports the outcome of
     * every saved miner's poll, every cycle — connected (with hashrate),
     * wrong password, unreachable, or not hashing — so a host can show
     * WHY an entry is not uploading instead of leaving it silent.
     *
     * Default no-op: existing hosts (the main app) keep their exact
     * behaviour; Relay Lite overrides it to persist the outcomes for
     * its Saved miners list. Implementations must be cheap and must
     * not throw (the engine calls this inside its per-miner guard).
     */
    fun reportMinerOutcome(profile: MinerProfile, outcome: MinerPollOutcome) {}
}
