package com.banishwor.antminerrelay.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.relay.RelayStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Relay Lite's [RelayStorage]: the same persistence contract as the
 * main app's MinerStorageManager — same preference keys, same profile
 * JSON field names and defaults, same cloud-config semantics (the
 * single Relay switch, legacy flags derived from it), same relay-status
 * rules — because the shared RelayEngine cannot tell the difference.
 *
 * The one deliberate difference is the encryption under the profiles:
 * the app's Keystore AES-GCM scheme needs API 23+, so here the profile
 * JSON is sealed with [LiteVault] (RSA-wrapped AES, proven on the
 * Y21L by :relay-probe). The prefs file lives in this app's own
 * sandbox; key names match the app's so the two stores stay
 * conceptually interchangeable, nothing more.
 */
class LiteRelayStorage(context: Context) : RelayStorage {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_FILE = "antminer_saved_profiles"

        private const val KEY_PROFILES = "key_miner_profiles"
        private const val KEY_RELAY_LAST_UPLOAD_MS = "relay_last_upload_ms"
        private const val KEY_RELAY_LAST_ERROR = "relay_last_error"

        private const val KEY_WEBHOOK_URL = "cloud_webhook_url"
        private const val KEY_USER_NAME = "cloud_user_name"
        private const val KEY_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_AUTO_UPLOAD = "cloud_auto_upload_enabled"
        private const val KEY_DOWNLOAD_REMOTE = "cloud_download_remote_enabled"
        private const val KEY_BG_SERVICE = "cloud_bg_service_enabled"
    }

    // --- Profiles ------------------------------------------------------------

    override fun loadSavedProfiles(): List<MinerProfile> {
        val stored = prefs.getString(KEY_PROFILES, null) ?: return emptyList()

        val encrypted = LiteVault.isEncrypted(stored)
        val jsonString = if (encrypted) {
            try {
                LiteVault.decrypt(appContext, stored)
            } catch (e: Exception) {
                // Never fall back to parsing ciphertext as plaintext.
                e.printStackTrace()
                return emptyList()
            }
        } else {
            stored
        }

        val list = mutableListOf<MinerProfile>()
        var parseFailed = false
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawPass = obj.optString("password", "")
                list.add(
                    MinerProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        workerName = obj.optString("workerName", ""),
                        macAddress = obj.optString("macAddress", ""),
                        displayName = obj.optString("displayName", "Antminer"),
                        nickname = obj.optString("nickname", ""),
                        location = obj.optString("location", "Default Location"),
                        host = obj.optString("host", ""),
                        username = obj.optString("username", "root"),
                        password = if (rawPass.isBlank()) "root" else rawPass,
                        lastConnected = obj.optLong("lastConnected", System.currentTimeMillis()),
                        cachedModel = if (obj.has("cachedModel") && !obj.isNull("cachedModel") &&
                            obj.optString("cachedModel").isNotBlank()
                        ) obj.optString("cachedModel") else null
                    )
                )
            }
        } catch (e: Exception) {
            parseFailed = true
            e.printStackTrace()
        }

        if (!encrypted && !parseFailed) {
            // Same transparent migration as the main app: rewrite legacy
            // plaintext entries encrypted.
            saveProfiles(list)
        }
        return list
    }

    override fun saveProfiles(profiles: List<MinerProfile>) {
        try {
            val array = JSONArray()
            profiles.forEach { profile ->
                val obj = JSONObject()
                obj.put("id", profile.id)
                obj.put("workerName", profile.workerName)
                obj.put("macAddress", profile.macAddress)
                obj.put("displayName", profile.displayName)
                obj.put("nickname", profile.nickname)
                obj.put("location", profile.location)
                obj.put("host", profile.host)
                obj.put("username", profile.username)
                obj.put("password", profile.password.ifBlank { "root" })
                obj.put("lastConnected", profile.lastConnected)
                obj.put("cachedModel", profile.cachedModel ?: "")
                array.put(obj)
            }
            val json = array.toString()
            val stored = try {
                LiteVault.encrypt(appContext, json)
            } catch (e: Exception) {
                // Vault unavailable (should not happen on a real device):
                // keep the relay functional rather than dropping profiles.
                e.printStackTrace()
                json
            }
            prefs.edit().putString(KEY_PROFILES, stored).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Cloud config ----------------------------------------------------------

    override fun loadCloudConfig(): CloudSyncConfig {
        val savedUrl = prefs.getString(KEY_WEBHOOK_URL, null)
        val syncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, true)
        return CloudSyncConfig(
            webhookUrl = if (savedUrl.isNullOrBlank()) CloudSyncConfig.DEFAULT_WEBHOOK_URL else savedUrl,
            userName = prefs.getString(KEY_USER_NAME, "") ?: "",
            // The single Relay switch; the legacy flags are derived from
            // it, exactly as the main app's loader derives them.
            isSyncEnabled = syncEnabled,
            isAutoUploadEnabled = syncEnabled,
            isDownloadRemoteEnabled = true,
            isBackgroundServiceEnabled = syncEnabled
        )
    }

    fun saveCloudConfig(config: CloudSyncConfig) {
        prefs.edit()
            .putString(KEY_WEBHOOK_URL, config.webhookUrl.trim())
            .putString(KEY_USER_NAME, config.userName.trim())
            .putBoolean(KEY_SYNC_ENABLED, config.isSyncEnabled)
            .putBoolean(KEY_AUTO_UPLOAD, config.isAutoUploadEnabled)
            .putBoolean(KEY_DOWNLOAD_REMOTE, config.isDownloadRemoteEnabled)
            .putBoolean(KEY_BG_SERVICE, config.isBackgroundServiceEnabled)
            .apply()
    }

    // --- Relay status ------------------------------------------------------------

    override fun saveRelayStatus(lastUploadMs: Long?, error: String?) {
        val editor = prefs.edit()
        if (lastUploadMs != null) {
            editor.putLong(KEY_RELAY_LAST_UPLOAD_MS, lastUploadMs)
        }
        if (error != null) {
            editor.putString(KEY_RELAY_LAST_ERROR, error)
        } else {
            editor.remove(KEY_RELAY_LAST_ERROR)
        }
        editor.apply()
    }

    /** (last successful upload epoch ms, last upload error) as last reported. */
    fun loadRelayStatus(): Pair<Long?, String?> {
        val ts = prefs.getLong(KEY_RELAY_LAST_UPLOAD_MS, 0L)
        return (if (ts > 0L) ts else null) to prefs.getString(KEY_RELAY_LAST_ERROR, null)
    }
}
