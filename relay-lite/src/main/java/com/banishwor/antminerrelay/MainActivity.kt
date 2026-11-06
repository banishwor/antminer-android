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
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.banishwor.antminerrelay.storage.LiteRelayStorage
import com.banishwor.antminerrelay.storage.LiteState
import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.scanner.AntminerDetector
import com.example.antminerconnectiontest.data.scanner.DiscoveryStatus
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.relay.MinerPollState
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
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
 *
 * v0.1.2: Add a miner is add-and-connect — the entered miner is tried
 * for real before anything is stored, and the outcome stands inline
 * (Connected / wrong password / unreachable, with Save anyway);
 * every saved row shows the engine's latest per-miner outcome
 * (persisted in [LiteState]); and a found update can be downloaded
 * and installed in-app, still only ever from a tap.
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
    private lateinit var addMinerButton: Button
    private lateinit var addMinerResultText: TextView
    private lateinit var addMinerActions: LinearLayout
    private lateinit var addMinerSaveAnywayButton: Button
    private lateinit var addMinerDismissButton: Button
    private lateinit var updateActionButton: Button
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadCancelButton: Button

    /** Release page to open when the update result is tapped. */
    @Volatile
    private var updateUrl: String? = null

    @Volatile
    private var updateCheckRunning = false

    // --- Add-and-connect state (v0.1.2) ---------------------------------------

    /** The profile from the latest connect attempt, awaiting a decision. */
    @Volatile
    private var pendingProfile: MinerProfile? = null

    @Volatile
    private var connectRunning = false

    /** What the dismiss button does for the standing outcome. */
    private var pendingDismissIsCancel = true

    // --- Update download state (v0.1.2) ----------------------------------------

    private var pendingApkUrl: String? = null
    private var pendingApkVersion: String? = null
    private var downloadedApk: File? = null

    @Volatile
    private var downloadRunning = false

    @Volatile
    private var downloadCancelled = false

    /** Per-row outcome lines (profile id -> view), updated by the 2 s tick. */
    private val outcomeViews = LinkedHashMap<String, TextView>()

    /** Profiles as last rendered, so the tick can map outcomes by host. */
    private var currentProfiles: List<MinerProfile> = emptyList()

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
        addMinerButton = findViewById(R.id.addMinerButton)
        addMinerResultText = findViewById(R.id.addMinerResultText)
        addMinerActions = findViewById(R.id.addMinerActions)
        addMinerSaveAnywayButton = findViewById(R.id.addMinerSaveAnywayButton)
        addMinerDismissButton = findViewById(R.id.addMinerDismissButton)
        updateActionButton = findViewById(R.id.updateActionButton)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadCancelButton = findViewById(R.id.downloadCancelButton)

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
        addMinerButton.setOnClickListener { addMiner() }
        addMinerSaveAnywayButton.setOnClickListener { savePendingMinerAnyway() }
        addMinerDismissButton.setOnClickListener { dismissPendingMiner() }
        updateActionButton.setOnClickListener { onUpdateAction() }
        downloadCancelButton.setOnClickListener { downloadCancelled = true }
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

    /**
     * Add-and-connect (v0.1.2): Save no longer stores blindly. The
     * entered miner is tried for real on a background thread — summary
     * with the entered credentials, pools for the worker name — and the
     * outcome stands as persistent inline text: Connected (saved),
     * wrong password, or unreachable, the failures offering Save anyway
     * (the engine retries saved entries every cycle) or edit/cancel.
     * Failure classification follows the detector's signature probe —
     * the same signal the engine's auto-connect uses: OFFLINE means
     * nothing answered; anything answering means the login was rejected.
     */
    private fun addMiner() {
        if (connectRunning) return
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
        pendingProfile = profile
        connectRunning = true
        setAddFormEnabled(false)
        addMinerActions.visibility = View.GONE
        showAddResult("Connecting to $host…")

        thread(name = "lite-add-miner") {
            val readApi = AntminerReadApiImpl()
            val summaryRes = try {
                runBlocking { readApi.getSummary(profile) }
            } catch (e: Exception) {
                null
            }
            val outcome: AddOutcome = if (summaryRes != null && summaryRes.isSuccess) {
                // Connected: learn the worker identity from the
                // miner's own pool config; a learned name wins for
                // the saved profile (same rule as the engine).
                val learned = try {
                    runBlocking { readApi.getPools(profile) }.getOrNull()
                        ?.firstOrNull { it.user.isNotBlank() }?.user?.trim()
                        ?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }
                AddOutcome.Connected(learned)
            } else {
                val signature = try {
                    runBlocking { AntminerDetector().detectSignature(host) }
                } catch (e: Exception) {
                    DiscoveryStatus.OFFLINE
                }
                if (signature == DiscoveryStatus.OFFLINE) {
                    AddOutcome.Unreachable
                } else {
                    AddOutcome.WrongPassword
                }
            }
            runOnUiThread {
                connectRunning = false
                if (isFinishing) return@runOnUiThread
                onAddOutcome(profile, outcome)
            }
        }
    }

    private sealed interface AddOutcome {
        /** Summary read OK; [learnedWorker] from pools, may be null. */
        data class Connected(val learnedWorker: String?) : AddOutcome
        object WrongPassword : AddOutcome
        object Unreachable : AddOutcome
    }

    private fun onAddOutcome(profile: MinerProfile, outcome: AddOutcome) {
        when (outcome) {
            is AddOutcome.Connected -> {
                val workerName = outcome.learnedWorker ?: profile.workerName
                val saved = profile.copy(
                    workerName = workerName,
                    displayName = workerName.ifBlank { profile.displayName }
                )
                storage.saveProfiles(storage.loadSavedProfiles() + saved)
                pendingProfile = null
                resetAddForm()
                showAddResult(
                    "✓ Connected — ${workerName.ifBlank { profile.host }} at ${profile.host}"
                )
                refreshProfiles()
            }
            AddOutcome.WrongPassword -> {
                setAddFormEnabled(true)
                showAddResult("⚠ The miner answered but the login failed — check the password.")
                pendingDismissIsCancel = false
                addMinerDismissButton.text = "Edit"
                addMinerActions.visibility = View.VISIBLE
            }
            AddOutcome.Unreachable -> {
                setAddFormEnabled(true)
                showAddResult("⚠ Can't reach a miner at ${profile.host}.")
                pendingDismissIsCancel = true
                addMinerDismissButton.text = "Cancel"
                addMinerActions.visibility = View.VISIBLE
            }
        }
    }

    /** Save anyway: store exactly what was entered; the engine retries it every cycle. */
    private fun savePendingMinerAnyway() {
        val profile = pendingProfile ?: return
        storage.saveProfiles(storage.loadSavedProfiles() + profile)
        pendingProfile = null
        addMinerActions.visibility = View.GONE
        resetAddForm()
        showAddResult("Saved anyway — the relay keeps retrying ${profile.host} every cycle.")
        refreshProfiles()
    }

    /** Dismiss: Edit keeps the typed values for fixing; Cancel discards the entry. */
    private fun dismissPendingMiner() {
        pendingProfile = null
        addMinerActions.visibility = View.GONE
        if (pendingDismissIsCancel) {
            resetAddForm()
            addMinerResultText.visibility = View.GONE
        }
        setAddFormEnabled(true)
    }

    private fun showAddResult(text: String) {
        addMinerResultText.text = text
        addMinerResultText.visibility = View.VISIBLE
    }

    private fun resetAddForm() {
        hostField.text.clear()
        workerField.text.clear()
        minerPassField.text.clear()
        setAddFormEnabled(true)
    }

    private fun setAddFormEnabled(enabled: Boolean) {
        addMinerButton.isEnabled = enabled
        workerField.isEnabled = enabled
        hostField.isEnabled = enabled
        minerUserField.isEnabled = enabled
        minerPassField.isEnabled = enabled
    }

    private fun refreshProfiles() {
        profilesContainer.removeAllViews()
        outcomeViews.clear()
        val profiles = storage.loadSavedProfiles()
        currentProfiles = profiles
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

            // Live state (v0.1.2): the engine's latest poll outcome
            // for this host, refreshed by the 2 s tick — an entry the
            // relay cannot use says why, and a saved-anyway entry
            // flips to Connected by itself once a cycle reaches it.
            val live = TextView(this)
            live.textSize = 13f
            live.visibility = View.GONE
            texts.addView(live)
            outcomeViews[profile.id] = live
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

        refreshOutcomeLines()
    }

    /** Repaints each Saved miner row's live-state line from [LiteState]. */
    private fun refreshOutcomeLines() {
        if (outcomeViews.isEmpty()) return
        val outcomes = LiteState.loadMinerOutcomes(applicationContext)
        for (profile in currentProfiles) {
            val view = outcomeViews[profile.id] ?: continue
            val outcome = outcomes[profile.host]
            if (outcome == null) {
                view.visibility = View.GONE
                continue
            }
            view.visibility = View.VISIBLE
            when (outcome.state) {
                MinerPollState.CONNECTED -> {
                    view.text = "Connected • ${String.format(Locale.US, "%.1f", outcome.hashrateTh)} TH/s"
                    view.setTextColor(Color.parseColor("#248A3D"))
                }
                MinerPollState.AUTH_FAILED -> {
                    view.text = "Wrong password — check the entry"
                    view.setTextColor(Color.parseColor("#B3261E"))
                }
                MinerPollState.UNREACHABLE -> {
                    view.text = "Unreachable — retrying automatically"
                    view.setTextColor(Color.parseColor("#6E6E73"))
                }
                MinerPollState.NOT_HASHING -> {
                    view.text = "Answered — not hashing"
                    view.setTextColor(Color.parseColor("#B26A00"))
                }
            }
        }
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

    private data class LatestLite(
        val version: String,
        val parts: List<Int>,
        val url: String,
        val apkUrl: String?
    )

    private fun checkForUpdates() {
        if (updateCheckRunning) return
        updateCheckRunning = true
        updateUrl = null
        pendingApkUrl = null
        pendingApkVersion = null
        downloadedApk = null
        updateActionButton.visibility = View.GONE
        downloadProgress.visibility = View.GONE
        downloadCancelButton.visibility = View.GONE
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
                        // In-app download (v0.1.2): offered only when
                        // the release carries an APK asset; the
                        // release-page link (tap the line above) stays
                        // as the fallback either way.
                        if (latest.apkUrl != null) {
                            pendingApkUrl = latest.apkUrl
                            pendingApkVersion = latest.version
                            downloadedApk = null
                            updateActionButton.text = "Download update"
                            updateActionButton.visibility = View.VISIBLE
                        }
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
                    var apkUrl: String? = null
                    val assets = release.optJSONArray("assets")
                    if (assets != null) {
                        for (j in 0 until assets.length()) {
                            val asset = assets.optJSONObject(j) ?: continue
                            if (asset.optString("name").endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url")
                                    .takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    }
                    best = LatestLite(version, parts, release.optString("html_url"), apkUrl)
                }
            }
            return best
        } finally {
            connection.disconnect()
        }
    }

    // --- In-app update download (v0.1.2; still only ever user-tapped) ---------

    private fun onUpdateAction() {
        val downloaded = downloadedApk
        if (downloaded != null) {
            installDownloadedUpdate(downloaded)
        } else if (!downloadRunning) {
            downloadUpdate()
        }
    }

    /**
     * Streams the release APK to cacheDir/updates with progress and a
     * working Cancel. GitHub asset URLs redirect to storage, so
     * redirects are followed manually (HttpsURLConnection does not
     * follow cross-host redirects on its own).
     */
    private fun downloadUpdate() {
        val url = pendingApkUrl ?: return
        val version = pendingApkVersion ?: return
        downloadRunning = true
        downloadCancelled = false
        updateActionButton.visibility = View.GONE
        downloadCancelButton.visibility = View.VISIBLE
        downloadProgress.progress = 0
        downloadProgress.visibility = View.VISIBLE
        updateResultText.setTextColor(Color.BLACK)
        updateResultText.text = "Downloading update v$version…"

        thread(name = "lite-update-download") {
            val dir = File(cacheDir, "updates")
            val target = File(dir, "RelayLite-v$version.apk")
            var failure: String? = null
            var lastPercent = -1
            try {
                dir.mkdirs()
                var connection = openHttps(url)
                var redirects = 0
                while (connection.responseCode in REDIRECT_CODES && redirects < 5) {
                    val location = connection.getHeaderField("Location")
                        ?: throw Exception("HTTP ${connection.responseCode} with no redirect target")
                    connection.disconnect()
                    connection = openHttps(location)
                    redirects++
                }
                if (connection.responseCode != 200) {
                    throw Exception("HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            if (downloadCancelled) break
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread {
                                        if (isFinishing) return@runOnUiThread
                                        downloadProgress.progress = percent
                                        updateResultText.text =
                                            "Downloading update v$version… $percent%"
                                    }
                                }
                            }
                        }
                    }
                }
                connection.disconnect()
                if (downloadCancelled) {
                    target.delete()
                }
            } catch (e: Exception) {
                failure = e.message ?: "network error"
                target.delete()
            }

            runOnUiThread {
                downloadRunning = false
                if (isFinishing) return@runOnUiThread
                downloadCancelButton.visibility = View.GONE
                downloadProgress.visibility = View.GONE
                when {
                    downloadCancelled -> {
                        updateResultText.text =
                            "Download cancelled. Tap the version line to open the release page instead."
                        updateActionButton.text = "Download update"
                        updateActionButton.visibility = View.VISIBLE
                    }
                    failure != null -> {
                        updateResultText.text =
                            "Download failed ($failure). Tap the version line to open the release page instead."
                        updateActionButton.text = "Download update"
                        updateActionButton.visibility = View.VISIBLE
                    }
                    else -> {
                        downloadedApk = target
                        updateResultText.text = "Update v$version downloaded."
                        updateActionButton.text = "Install"
                        updateActionButton.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun openHttps(url: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "AntminerRelayLite/${BuildConfig.VERSION_NAME}")
        return connection
    }

    /**
     * Hands the downloaded APK to the system installer via the
     * FileProvider (same pattern as the main app's updater). On API 22
     * "install unknown apps" is the single global setting — there is
     * no per-app detour; if the installer still refuses, the
     * release-page link above remains the fallback.
     */
    private fun installDownloadedUpdate(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            updateResultText.text =
                "Couldn't open the installer (${e.message}). Tap the version line to use the release page."
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
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
