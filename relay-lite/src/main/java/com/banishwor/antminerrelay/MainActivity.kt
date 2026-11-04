package com.banishwor.antminerrelay

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relay Lite's one screen: setup + status, native Views only.
 *
 * Setup = relay identity (username + webhook), the single Relay
 * switch (ON also starts the service; OFF stops it — the product rule
 * is one switch, and Start/Stop below drive that same switch), and
 * saved-miner entry. The relay itself auto-connects known machines
 * from there; there is deliberately no scanning and no fleet view.
 *
 * Status = the engine's own words, read back from [LiteState] (which
 * the service writes), plus the relay's last-upload report and the
 * boot/service counters that prove the restart machinery fired.
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
    private lateinit var statusText: TextView

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

        findViewById<TextView>(R.id.headerText).text =
            "Antminer Relay Lite v${BuildConfig.VERSION_NAME}"
        relaySwitch = findViewById(R.id.relaySwitch)
        userNameField = findViewById(R.id.userNameField)
        webhookField = findViewById(R.id.webhookField)
        workerField = findViewById(R.id.workerField)
        hostField = findViewById(R.id.hostField)
        minerUserField = findViewById(R.id.minerUserField)
        minerPassField = findViewById(R.id.minerPassField)
        profilesContainer = findViewById(R.id.profilesContainer)
        statusText = findViewById(R.id.statusText)

        val config = storage.loadCloudConfig()
        userNameField.setText(config.userName)
        webhookField.setText(config.webhookUrl)
        // Populate BEFORE attaching the listener: opening the screen
        // must not itself save anything or (re)start the service.
        relaySwitch.isChecked = config.isSyncEnabled
        relaySwitch.setOnCheckedChangeListener { _, isChecked -> applyRelaySwitch(isChecked) }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!relaySwitch.isChecked) relaySwitch.isChecked = true else applyRelaySwitch(true)
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            if (relaySwitch.isChecked) relaySwitch.isChecked = false else applyRelaySwitch(false)
        }
        findViewById<Button>(R.id.saveIdentityButton).setOnClickListener {
            saveConfigFromFields()
            Toast.makeText(this, "Identity saved", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        findViewById<Button>(R.id.addMinerButton).setOnClickListener { addMiner() }

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
            profilesContainer.addView(empty)
            return
        }
        for (profile in profiles) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val label = TextView(this)
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val identity = profile.workerName.ifBlank { "(identity learned on connect)" }
            label.text = "${profile.host} — $identity — user ${profile.username}"
            row.addView(label)

            val remove = Button(this)
            remove.text = "✕"
            remove.setOnClickListener {
                storage.saveProfiles(storage.loadSavedProfiles().filterNot { it.id == profile.id })
                refreshProfiles()
            }
            row.addView(remove)

            profilesContainer.addView(row)
        }
    }

    // --- Status readout ------------------------------------------------------------

    private fun refreshStatus() {
        val running = LiteState.isServiceRunning(applicationContext)
        val (title, content, updatedMs) = LiteState.loadStatus(applicationContext)
        val (lastUploadMs, lastError) = storage.loadRelayStatus()
        val config = storage.loadCloudConfig()

        val sb = StringBuilder()
        sb.append("Service: ").append(if (running) "RUNNING" else "stopped").append('\n')
        sb.append("Relay: ").append(if (config.isSyncEnabled) "ON" else "OFF")
        sb.append(" • User: ").append(config.userName.ifBlank { "(not set)" }).append('\n')
        if (title != null) {
            sb.append("Engine: ").append(title).append('\n')
            if (!content.isNullOrBlank()) sb.append(content).append('\n')
            sb.append("Status updated: ").append(ageText(updatedMs)).append('\n')
        } else {
            sb.append("Engine: no status yet — start the service.\n")
        }
        sb.append("Last upload: ")
            .append(lastUploadMs?.let { formatTime(it) } ?: "never").append('\n')
        if (!lastError.isNullOrBlank()) {
            sb.append("Last error: ").append(lastError).append('\n')
        }
        sb.append("Service starts: ").append(LiteState.serviceStarts(applicationContext))
        sb.append(" • Boot-triggered starts: ").append(LiteState.bootStarts(applicationContext))
        statusText.text = sb.toString()
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

    companion object {
        private const val REFRESH_MS = 2000L
    }
}
