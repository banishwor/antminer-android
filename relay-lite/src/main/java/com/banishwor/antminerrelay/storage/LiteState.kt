package com.banishwor.antminerrelay.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.antminerconnectiontest.relay.MinerPollOutcome
import com.example.antminerconnectiontest.relay.MinerPollState
import org.json.JSONObject

/**
 * Relay Lite's own run state, in SharedPreferences so it survives
 * process death, reboots and Recents clears — the service, the boot
 * receiver, the watchdog and the status screen all read/write here.
 * Counters are written with commit() (not apply()), mirroring the
 * probe: the counters are evidence, and they must be on disk before
 * any sudden kill.
 */
object LiteState {

    private const val PREFS = "relay_lite_state"

    private const val KEY_STATUS_TITLE = "status_title"
    private const val KEY_STATUS_CONTENT = "status_content"
    private const val KEY_STATUS_UPDATED_MS = "status_updated_ms"
    private const val KEY_SERVICE_STARTS = "service_starts"
    private const val KEY_BOOT_STARTS = "boot_starts"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_MINER_OUTCOMES = "miner_outcomes_json"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Engine status (written by the service's RelayStatusListener) -------

    fun saveStatus(context: Context, title: String, content: String) {
        prefs(context).edit()
            .putString(KEY_STATUS_TITLE, title)
            .putString(KEY_STATUS_CONTENT, content)
            .putLong(KEY_STATUS_UPDATED_MS, System.currentTimeMillis())
            .apply()
    }

    /** (title, content, updated epoch ms) as last reported by the engine. */
    fun loadStatus(context: Context): Triple<String?, String?, Long> {
        val p = prefs(context)
        return Triple(
            p.getString(KEY_STATUS_TITLE, null),
            p.getString(KEY_STATUS_CONTENT, null),
            p.getLong(KEY_STATUS_UPDATED_MS, 0L)
        )
    }

    // --- Counters ------------------------------------------------------------

    fun bumpServiceStarts(context: Context) = bump(context, KEY_SERVICE_STARTS)

    fun bumpBootStarts(context: Context) = bump(context, KEY_BOOT_STARTS)

    fun serviceStarts(context: Context): Long = prefs(context).getLong(KEY_SERVICE_STARTS, 0L)

    fun bootStarts(context: Context): Long = prefs(context).getLong(KEY_BOOT_STARTS, 0L)

    private fun bump(context: Context, key: String) {
        val p = prefs(context)
        p.edit().putLong(key, p.getLong(key, 0L) + 1L).commit()
    }

    // --- Per-miner poll outcomes (v0.1.2) -------------------------------------
    //
    // The latest engine outcome per miner host, as one JSON object:
    // { "<host>": {"s": "<MinerPollState>", "h": <TH/s>, "t": <epoch ms>} }.
    // Written by the service (via LiteRelayStorage.reportMinerOutcome),
    // read by the Saved miners list so every entry shows its live state
    // — including an entry saved as unreachable flipping to Connected
    // on its own once a later cycle reaches the miner.

    /** One miner's stored outcome, as read back for the UI. */
    data class StoredOutcome(
        val state: MinerPollState,
        val hashrateTh: Double,
        val atMs: Long
    )

    fun saveMinerOutcome(context: Context, host: String, outcome: MinerPollOutcome) {
        val p = prefs(context)
        val root = try {
            JSONObject(p.getString(KEY_MINER_OUTCOMES, null) ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        val entry = JSONObject()
        entry.put("s", outcome.state.name)
        entry.put("h", outcome.hashrateTh)
        entry.put("t", outcome.atMs)
        root.put(host, entry)
        p.edit().putString(KEY_MINER_OUTCOMES, root.toString()).apply()
    }

    /** Latest outcome per host; empty when the engine has not reported yet. */
    fun loadMinerOutcomes(context: Context): Map<String, StoredOutcome> {
        val raw = prefs(context).getString(KEY_MINER_OUTCOMES, null) ?: return emptyMap()
        val out = LinkedHashMap<String, StoredOutcome>()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val host = keys.next()
                val entry = root.optJSONObject(host) ?: continue
                val state = try {
                    MinerPollState.valueOf(entry.optString("s"))
                } catch (_: Exception) {
                    continue
                }
                out[host] = StoredOutcome(
                    state = state,
                    hashrateTh = entry.optDouble("h", 0.0),
                    atMs = entry.optLong("t", 0L)
                )
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return out
    }

    // --- Service liveness flag -------------------------------------------------

    fun setServiceRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_RUNNING, running).commit()
    }

    fun isServiceRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_RUNNING, false)
}
