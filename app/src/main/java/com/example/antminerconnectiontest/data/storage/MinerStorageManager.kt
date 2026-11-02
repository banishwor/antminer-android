package com.example.antminerconnectiontest.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.antminerconnectiontest.domain.model.MinerProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists miner connection profiles and credentials locally to device storage.
 *
 * The profiles JSON (which contains the miners' passwords) is stored
 * AES-256-GCM encrypted with a key held in the Android Keystore. Entries
 * written by older, plaintext versions are migrated transparently: the first
 * load that finds a legacy plaintext value parses it as before and rewrites
 * it encrypted, so an app update never loses saved profiles. The prefs file
 * is also excluded from Android backups (see res/xml/backup_rules.xml and
 * data_extraction_rules.xml).
 */
class MinerStorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("antminer_saved_profiles", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILES = "key_miner_profiles"
        private const val KEY_ACTIVE_ID = "key_active_profile_id"
        private const val KEY_START_CHOICE = "key_start_choice"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_PHONE_NICKNAMES = "key_phone_nicknames"
        private const val KEY_APP_STYLE = "key_app_style"
        private const val KEY_RELAY_ONLY = "key_relay_only"
        private const val KEY_RELAY_LAST_UPLOAD_MS = "relay_last_upload_ms"
        private const val KEY_RELAY_LAST_ERROR = "relay_last_error"

        /** Marks an encrypted payload: prefix + Base64(IV || ciphertext). */
        private const val ENCRYPTED_PREFIX = "enc_v1:"
        private const val KEYSTORE_ALIAS = "antminer_profiles_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    // --- Credential encryption (Android Keystore, AES-256-GCM) ---------------

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encryptToString(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptFromString(stored: String): String {
        val payload = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12) // GCM standard IV size produced by Cipher
        val cipherText = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    internal fun isEncryptedPayload(value: String?): Boolean {
        return value != null && value.startsWith(ENCRYPTED_PREFIX)
    }

    fun loadSavedProfiles(): List<MinerProfile> {
        val stored = prefs.getString(KEY_PROFILES, null) ?: return emptyList()

        val encrypted = isEncryptedPayload(stored)
        val jsonString = if (encrypted) {
            try {
                decryptFromString(stored)
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
            parseFailed = true
            e.printStackTrace()
        }

        if (!encrypted && !parseFailed) {
            // Transparent migration: rewrite legacy plaintext entries encrypted.
            saveProfiles(list)
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
            val json = array.toString()
            val stored = try {
                encryptToString(json)
            } catch (e: Exception) {
                // Keystore unavailable (should not happen on a real device):
                // keep the app functional rather than dropping the profiles.
                e.printStackTrace()
                json
            }
            prefs.edit().putString(KEY_PROFILES, stored).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * LEGACY (pre-v0.1.9): the first-open start choice older versions
     * persisted under [KEY_START_CHOICE]. v0.1.9 removed the start fork
     * — there is one shell for everyone — so nothing reads this value
     * anymore; a stored choice from an older install is simply ignored.
     * The loader stays only so a future migration could inspect it.
     */
    fun loadStartChoice(): String? {
        return prefs.getString(KEY_START_CHOICE, null)
    }

    fun saveStartChoice(choice: String?) {
        if (choice != null) {
            prefs.edit().putString(KEY_START_CHOICE, choice).apply()
        } else {
            prefs.edit().remove(KEY_START_CHOICE).apply()
        }
    }

    /**
     * The user's theme choice ("DARK" / "LIGHT" / "SYSTEM"), or null when
     * they have never picked one — the app then follows the system theme.
     */
    fun loadThemeMode(): String? {
        return prefs.getString(KEY_THEME_MODE, null)
    }

    fun saveThemeMode(modeName: String) {
        prefs.edit().putString(KEY_THEME_MODE, modeName).apply()
    }

    /**
     * The appearance style ("VIOLET" / "LIQUID_GLASS"), or null when
     * never chosen — the app then uses the Violet default.
     */
    fun loadAppStyle(): String? {
        return prefs.getString(KEY_APP_STYLE, null)
    }

    fun saveAppStyle(styleName: String) {
        prefs.edit().putString(KEY_APP_STYLE, styleName).apply()
    }

    /**
     * Relay-only mode (v0.3.0): when true the app shows only the relay
     * status screen. Persisted so a site phone comes back up in
     * relay-only after a reboot/restart, exactly as it was left.
     */
    fun loadRelayOnlyMode(): Boolean {
        return prefs.getBoolean(KEY_RELAY_ONLY, false)
    }

    fun saveRelayOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RELAY_ONLY, enabled).apply()
    }

    /**
     * The relay's own report of itself, written by whoever completed
     * an upload cycle (the 24/7 background service, or the foreground
     * sync cycle): the last successful upload time and the last upload
     * error. The relay-only status screen reads these so what it shows
     * is the relay's actual state, not the UI's guess. A null timestamp
     * argument keeps the stored one; a null error clears it.
     */
    fun saveRelayStatus(lastUploadMs: Long?, error: String?) {
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

    /**
     * Phone-only nicknames (v0.1.7): the user's private per-machine
     * labels, keyed by worker identity (see PhoneNicknames). They live
     * in THIS prefs file on purpose: the whole file is already excluded
     * from Android cloud backup and device-to-device transfer (see
     * res/xml/backup_rules.xml and data_extraction_rules.xml), so the
     * nicknames can never leave the device through backup either — and
     * nothing ever puts them in an upload payload.
     */
    fun loadPhoneNicknames(): Map<String, String> {
        return com.example.antminerconnectiontest.domain.model.PhoneNicknames.parse(
            prefs.getString(KEY_PHONE_NICKNAMES, null)
        )
    }

    fun savePhoneNicknames(overrides: Map<String, String>) {
        prefs.edit()
            .putString(
                KEY_PHONE_NICKNAMES,
                com.example.antminerconnectiontest.domain.model.PhoneNicknames.encode(overrides)
            )
            .apply()
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
            // The single Relay switch. Migration: this reads the old
            // master "Cloud Relay" toggle (cloud_sync_enabled), so a
            // previously enabled master relay stays ON and a stored OFF
            // stays OFF. The old sub-toggle keys are no longer read —
            // upload and 24/7 background relay now follow this switch,
            // and remote viewing is not gated by it at all.
            isSyncEnabled = prefs.getBoolean("cloud_sync_enabled", true),
            isAutoUploadEnabled = prefs.getBoolean("cloud_sync_enabled", true),
            isDownloadRemoteEnabled = true,
            isBackgroundServiceEnabled = prefs.getBoolean("cloud_sync_enabled", true)
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
