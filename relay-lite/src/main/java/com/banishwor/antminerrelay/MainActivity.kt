package com.banishwor.antminerrelay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.banishwor.antminerrelay.storage.LiteRelayStorage
import com.banishwor.antminerrelay.storage.LiteState
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerProfile
import org.json.JSONArray
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

/**
 * Relay Lite's one screen: setup + status, native Views only.
 *
 * v0.1.1 presentation (approved mockup): light theme only, status
 * hero first, then grouped sections — the single Relay switch (ON
 * also starts the service; OFF stops it — there are no separate
 * Start/Stop buttons), identity with the webhook tucked under
 * Advanced, saved miners with a collapsible add form, status
 * details split into rows, an App group with a manual
 * check-for-updates button, and the first-run checklist collapsed
 * under Setup. All data and behaviour are unchanged from v0.1.0:
 * the relay itself auto-connects known machines; there is
 * deliberately no scanning and no fleet view.
 *
 * Status = the engine's own words, read back from [LiteState] (which
 * the service writes), plus the relay's last-upload report and the
 * boot/service counters that prove the restart machinery fired.
 *
 * The update check runs ONLY from its button — never on open, on
 * resume, or in any background path.
 */
class MainActivity : Activity() {

    private lateinit var storage: LiteRelayStorage

    private lateinit var relaySwitch: Switch
    private lateinit var userNameField: EditText
    private lateinit var webhookField: EditText
    private lateinit var workerField: EditText
    private lateinit var hostField: EditText
    private lateinit var minerUserField: EditText
    private lateinit var minerPassField: EditText
    private lateinit var profilesContainer: LinearLayout
    private lateinit var advancedDetail: TextView
    private lateinit var statusDot: View
    private lateinit var statusWord: TextView
    private lateinit var heroRelayLine: TextView
    private lateinit var heroUploadLine: TextView
    private lateinit var engineText: TextView
    private lateinit var statusUpdatedText: TextView
    private lateinit var countersText: TextView
    private lateinit var lastErrorText: TextView
    private lateinit var checkUpdatesButton: Button
    private lateinit var updateResultText: TextView

    /** Release page to open when the update result is tapped. */
    @Volatile
    private var updateUrl: String? = null

    @Volatile
    private var updateCheckRunning = false

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() {
            refreshStatus()
            refreshHandler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = LiteRelayStorage(applicationContext)

        findViewById<TextView>(R.id.headerText).text = "Antminer Relay Lite"
        findViewById<TextView>(R.id.headerSub).text =
            "Relay for this location · v${BuildConfig.VERSION_NAME}"
        findViewById<TextView>(R.id.versionText).text = BuildConfig.VERSION_NAME
        relaySwitch = findViewById(R.id.relaySwitch)
        userNameField = findViewById(R.id.userNameField)
        webhookField = findViewById(R.id.webhookField)
        workerField = findViewById(R.id.workerField)
        hostField = findViewById(R.id.hostField)
        minerUserField = findViewById(R.id.minerUserField)
        minerPassField = findViewById(R.id.minerPassField)
        profilesContainer = findViewById(R.id.profilesContainer)
        advancedDetail = findViewById(R.id.advancedDetail)
        statusDot = findViewById(R.id.statusDot)
        statusWord = findViewById(R.id.statusWord)
        heroRelayLine = findViewById(R.id.heroRelayLine)
        heroUploadLine = findViewById(R.id.heroUploadLine)
        engineText = findViewById(R.id.engineText)
        statusUpdatedText = findViewById(R.id.statusUpdatedText)
        countersText = findViewById(R.id.countersText)
        lastErrorText = findViewById(R.id.lastErrorText)
        checkUpdatesButton = findViewById(R.id.checkUpdatesButton)
        updateResultText = findViewById(R.id.updateResultText)

        val config = storage.loadCloudConfig()
        userNameField.setText(config.userName)
        webhookField.setText(config.webhookUrl)
        advancedDetail.text = advancedDetailText(config.webhookUrl)
        // Populate BEFORE attaching the listener: opening the screen
        // must not itself save anything or (re)start the service.
        relaySwitch.isChecked = config.isSyncEnabled
        relaySwitch.setOnCheckedChangeListener { _, isChecked -> applyRelaySwitch(isChecked) }

        findViewById<Button>(R.id.saveIdentityButton).setOnClickListener {
            saveConfigFromFields()
            Toast.makeText(this, "Identity saved", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        findViewById<Button>(R.id.addMinerButton).setOnClickListener { addMiner() }
        wireCollapsible(R.id.advancedToggle, R.id.advancedContainer)
        wireCollapsible(R.id.addMinerToggle, R.id.addMinerContainer)
        wireCollapsible(R.id.setupToggle, R.id.setupContainer)

        checkUpdatesButton.setOnClickListener { checkForUpdates() }
        updateResultText.setOnClickListener {
            val url = updateUrl ?: return@setOnClickListener
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                // No browser on an old appliance phone: leave the
                // result text standing; nothing else to do.
            }
        }

        refreshProfiles()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshTick)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshTick)
        super.onPause()
    }

    private fun wireCollapsible(toggleId: Int, containerId: Int) {
        val container = findViewById<View>(containerId)
        findViewById<View>(toggleId).setOnClickListener {
            container.visibility =
                if (container.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun advancedDetailText(webhookUrl: String): String =
        if (webhookUrl.isBlank() || webhookUrl == CloudSyncConfig.DEFAULT_WEBHOOK_URL) {
            "Webhook · Production ✓"
        } else {
            "Webhook · Custom"
        }

    // --- Relay switch / service control ---------------------------------------

    /**
     * The one switch, applied: persist it (with whatever identity is
     * currently typed, so nothing is lost), then start or stop the
     * service and arm/disarm the watchdog to match.
     */
    private fun applyRelaySwitch(on: Boolean) {
        saveConfigFromFields()
        if (on) {
            LiteRelayService.start(applicationContext)
            Watchdog.schedule(applicationContext)
        } else {
            LiteRelayService.stop(applicationContext)
            Watchdog.cancel(applicationContext)
        }
        refreshStatus()
    }

    private fun saveConfigFromFields() {
        val current = storage.loadCloudConfig()
        storage.saveCloudConfig(
            current.copy(
                webhookUrl = webhookField.text.toString().trim()
                    .ifBlank { CloudSyncConfig.DEFAULT_WEBHOOK_URL },
                userName = userNameField.text.toString().trim(),
                isSyncEnabled = relaySwitch.isChecked,
                isAutoUploadEnabled = relaySwitch.isChecked,
                isBackgroundServiceEnabled = relaySwitch.isChecked
            )
        )
        advancedDetail.text =
            advancedDetailText(webhookField.text.toString().trim())
    }

    // --- Saved miners ------------------------------------------------------------

    private fun addMiner() {
        val host = hostField.text.toString().trim()
        if (host.isBlank()) {
            Toast.makeText(this, "Enter the miner's IP address", Toast.LENGTH_SHORT).show()
            return
        }
        val worker = workerField.text.toString().trim()
        val profile = MinerProfile(
            workerName = worker,
            displayName = worker.ifBlank { "Antminer" },
            host = host,
            username = minerUserField.text.toString().trim().ifBlank { "root" },
            password = minerPassField.text.toString().ifBlank { "root" }
        )
        storage.saveProfiles(storage.loadSavedProfiles() + profile)
        hostField.text.clear()
        workerField.text.clear()
        minerPassField.text.clear()
        Toast.makeText(this, "Miner saved: $host", Toast.LENGTH_SHORT).show()
        refreshProfiles()
    }

    private fun refreshProfiles() {
        profilesContainer.removeAllViews()
        val profiles = storage.loadSavedProfiles()
        if (profiles.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No miners saved yet. Fleet machines from the sheet are also auto-connected when this phone joins their Wi-Fi."
            empty.setTextColor(Color.parseColor("#6E6E73"))
            empty.textSize = 13f
            empty.setPadding(dp(14), dp(14), dp(14), dp(14))
            profilesContainer.addView(empty)
            return
        }
        for (profile in profiles) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(10), dp(6), dp(10))

            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            texts.layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val identity = profile.workerName.ifBlank { "(identity learned on connect)" }
            val name = TextView(this)
            name.text = profile.workerName.ifBlank { profile.host }
            name.setTextColor(Color.BLACK)
            name.textSize = 16f
            texts.addView(name)

            val detail = TextView(this)
            detail.text =
                if (profile.workerName.isNotBlank()) {
                    "${profile.host} · user ${profile.username}"
                } else {
                    "$identity · user ${profile.username}"
                }
            detail.setTextColor(Color.parseColor("#6E6E73"))
            detail.textSize = 13f
            texts.addView(detail)
            row.addView(texts)

            val remove = Button(this)
            remove.text = "✕"
            remove.setBackgroundColor(Color.TRANSPARENT)
            remove.setTextColor(Color.parseColor("#6E6E73"))
            remove.textSize = 17f
            remove.setOnClickListener {
                storage.saveProfiles(storage.loadSavedProfiles().filterNot { it.id == profile.id })
                refreshProfiles()
            }
            row.addView(remove)

            profilesContainer.addView(row)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // --- Status readout ------------------------------------------------------------

    private fun refreshStatus() {
        val running = LiteState.isServiceRunning(applicationContext)
        val (title, content, updatedMs) = LiteState.loadStatus(applicationContext)
        val (lastUploadMs, lastError) = storage.loadRelayStatus()
        val config = storage.loadCloudConfig()

        statusDot.setBackgroundResource(if (running) R.drawable.dot_on else R.drawable.dot_off)
        statusWord.text = if (running) "RUNNING" else "Stopped"
        heroRelayLine.text =
            "Relay ${if (config.isSyncEnabled) "ON" else "OFF"} • User ${config.userName.ifBlank { "(not set)" }}"
        heroUploadLine.text = lastUploadMs?.let {
            "Last upload ${formatTime(it)} · ${ageText(it)}"
        } ?: "Last upload never"

        engineText.text = if (title != null) {
            if (content.isNullOrBlank()) title else "$title\n$content"
        } else {
            "No status yet — start the service."
        }
        statusUpdatedText.text = if (title != null) ageText(updatedMs) else "never"
        countersText.text =
            "${LiteState.serviceStarts(applicationContext)} · ${LiteState.bootStarts(applicationContext)}"
        lastErrorText.text = lastError?.takeIf { it.isNotBlank() } ?: "None"
    }

    private fun ageText(epochMs: Long): String {
        if (epochMs <= 0L) return "never"
        val ageSec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0)
        return when {
            ageSec < 60 -> "${ageSec}s ago"
            ageSec < 3600 -> "${ageSec / 60}m ago"
            else -> "${ageSec / 3600}h ago"
        }
    }

    private fun formatTime(epochMs: Long): String =
        SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(epochMs))

    // --- Manual update check (button only — never automatic) ---------------------

    private data class LatestLite(val version: String, val parts: List<Int>, val url: String)

    private fun checkForUpdates() {
        if (updateCheckRunning) return
        updateCheckRunning = true
        updateUrl = null
        checkUpdatesButton.isEnabled = false
        updateResultText.setTextColor(Color.BLACK)
        updateResultText.text = "Checking…"
        thread(name = "lite-update-check") {
            val latest = try {
                fetchLatestLiteRelease()
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                updateCheckRunning = false
                if (isFinishing) return@runOnUiThread
                checkUpdatesButton.isEnabled = true
                when {
                    latest == null ->
                        updateResultText.text = "Couldn't check for updates — try again."
                    compareVersions(latest.parts, versionParts(BuildConfig.VERSION_NAME) ?: emptyList()) > 0 -> {
                        updateUrl = latest.url
                        updateResultText.setTextColor(Color.parseColor("#007AFF"))
                        updateResultText.text = "Update available: v${latest.version}"
                    }
                    else -> updateResultText.text = "You're on the latest version."
                }
            }
        }
    }

    /** Highest non-draft `relay-lite-v*` release on GitHub, or null on any failure. */
    private fun fetchLatestLiteRelease(): LatestLite? {
        val connection = URL(RELEASES_URL).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "AntminerRelayLite/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            var best: LatestLite? = null
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) continue
                val version = tag.removePrefix(TAG_PREFIX).removePrefix("v")
                val parts = versionParts(version) ?: continue
                val current = best
                if (current == null || compareVersions(parts, current.parts) > 0) {
                    best = LatestLite(version, parts, release.optString("html_url"))
                }
            }
            return best
        } finally {
            connection.disconnect()
        }
    }

    private fun versionParts(version: String): List<Int>? {
        val pieces = version.split('.')
        if (pieces.isEmpty()) return null
        val parts = pieces.map { piece ->
            piece.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        }
        return parts
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    companion object {
        private const val REFRESH_MS = 2000L
        private const val RELEASES_URL =
            "https://api.github.com/repos/banishwor/antminer-android/releases?per_page=30"
        private const val TAG_PREFIX = "relay-lite-v"
    }
}
