package com.example.antminerconnectiontest.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.antminerconnectiontest.domain.model.MinerProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists miner connection profiles and credentials locally to device storage.
 */
class MinerStorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("antminer_saved_profiles", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILES = "key_miner_profiles"
        private const val KEY_ACTIVE_ID = "key_active_profile_id"
    }

    fun loadSavedProfiles(): List<MinerProfile> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<MinerProfile>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawPass = obj.optString("password", "")
                list.add(
                    MinerProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        workerName = obj.optString("workerName", ""),
                        macAddress = obj.optString("macAddress", ""),
                        displayName = obj.optString("displayName", "Antminer"),
                        nickname = obj.optString("nickname", ""),
                        location = obj.optString("location", "Default Location"),
                        host = obj.optString("host", ""),
                        username = obj.optString("username", "root"),
                        password = if (rawPass.isBlank()) "root" else rawPass,
                        lastConnected = obj.optLong("lastConnected", System.currentTimeMillis()),
                        cachedModel = if (obj.has("cachedModel") && !obj.isNull("cachedModel") && obj.optString("cachedModel").isNotBlank()) obj.optString("cachedModel") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveProfiles(profiles: List<MinerProfile>) {
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
            prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastActiveProfileId(): String? {
        return prefs.getString(KEY_ACTIVE_ID, null)
    }

    fun setLastActiveProfileId(profileId: String?) {
        if (profileId != null) {
            prefs.edit().putString(KEY_ACTIVE_ID, profileId).apply()
        } else {
            prefs.edit().remove(KEY_ACTIVE_ID).apply()
        }
    }

    fun loadCloudConfig(): com.example.antminerconnectiontest.domain.model.CloudSyncConfig {
        val savedUrl = prefs.getString("cloud_webhook_url", null)
        return com.example.antminerconnectiontest.domain.model.CloudSyncConfig(
            webhookUrl = if (savedUrl.isNullOrBlank()) com.example.antminerconnectiontest.domain.model.CloudSyncConfig.DEFAULT_WEBHOOK_URL else savedUrl,
            userName = prefs.getString("cloud_user_name", "") ?: "",
            isSyncEnabled = prefs.getBoolean("cloud_sync_enabled", false),
            isAutoUploadEnabled = prefs.getBoolean("cloud_auto_upload_enabled", true),
            isDownloadRemoteEnabled = prefs.getBoolean("cloud_download_remote_enabled", true),
            isBackgroundServiceEnabled = prefs.getBoolean("cloud_bg_service_enabled", false)
        )
    }

    fun saveCloudConfig(config: com.example.antminerconnectiontest.domain.model.CloudSyncConfig) {
        prefs.edit()
            .putString("cloud_webhook_url", config.webhookUrl.trim())
            .putString("cloud_user_name", config.userName.trim())
            .putBoolean("cloud_sync_enabled", config.isSyncEnabled)
            .putBoolean("cloud_auto_upload_enabled", config.isAutoUploadEnabled)
            .putBoolean("cloud_download_remote_enabled", config.isDownloadRemoteEnabled)
            .putBoolean("cloud_bg_service_enabled", config.isBackgroundServiceEnabled)
            .apply()
    }
}
