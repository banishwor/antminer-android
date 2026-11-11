package com.example.antminerconnectiontest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.antminerconnectiontest.data.cloud.CloudSyncManager
import com.example.antminerconnectiontest.data.repository.MinerProfileManager
import com.example.antminerconnectiontest.data.repository.MinerRepository
import com.example.antminerconnectiontest.data.repository.MultiMinerPoller
import com.example.antminerconnectiontest.data.scanner.AntminerDetector
import com.example.antminerconnectiontest.data.scanner.DiscoveredMiner
import com.example.antminerconnectiontest.data.scanner.NetworkScanner
import com.example.antminerconnectiontest.data.scanner.ScanProgress
import com.example.antminerconnectiontest.data.storage.MinerStorageManager
import com.example.antminerconnectiontest.domain.alerts.MinerAlert
import com.example.antminerconnectiontest.domain.alerts.ThresholdAlertEngine
import com.example.antminerconnectiontest.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import com.example.antminerconnectiontest.theme.ThemeMode
import com.example.antminerconnectiontest.data.analysis.DailyLogAnalyzer
import com.example.antminerconnectiontest.data.storage.DailyAnalysisStorageManager

enum class DashboardTab {
    HOME,
    HARDWARE,
    POOLS,
    LOGS,
    DEVICE
}

enum class LogsTab {
    CURRENT,
    HISTORY,
    DAILY_ANALYSIS
}

enum class SetupTab {
    MANUAL,
    SCANNER
}

data class SetupFormState(
    val ip: String = "192.168.1.2",
    val username: String = "root",
    val password: String = "root",
    val isPasswordVisible: Boolean = false,
    val isVerifying: Boolean = false,
    val errorMessage: String? = null
)

data class DailyAnalysisUiState(
    val loadedForMinerId: String? = null,
    val availableDates: List<DailyLogDateItem> = emptyList(),
    val selectedRawDateIds: Set<String> = emptySet(),
    val isFetchingDates: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisPhaseMessage: String? = null,
    val analysisProgress: Float = 0f,
    val selectedAnalysisDetail: DailyMinerAnalysis? = null,
    val errorMessage: String? = null
)

data class AppUiState(
    val activeProfile: MinerProfile? = null,
    val savedProfiles: List<MinerProfile> = emptyList(),
    val minerHashrates: Map<String, Double> = emptyMap(),
    val isProfileSwitcherOpen: Boolean = false,
    val dashboardData: MinerDashboardData? = null,
    val dashboardError: String? = null,
    val activeAlerts: List<MinerAlert> = emptyList(),
    val isRefreshing: Boolean = false,
    val selectedTab: DashboardTab = DashboardTab.HOME,
    val setupTab: SetupTab = SetupTab.MANUAL,
    val setupForm: SetupFormState = SetupFormState(),
    val scannerState: ScanProgress = ScanProgress(0, 0, "", false),
    /**
     * "Find moved miners" recovery scan (v0.5.4): runs ONLY from an
     * explicit button tap. [isFindingMovedMiners] drives the buttons'
     * progress/disabled state; [movedMinerScanLines] is the persistent
     * in-place result summary (never just a toast).
     */
    val isFindingMovedMiners: Boolean = false,
    val movedMinerScanProgress: String? = null,
    val movedMinerScanLines: List<String> = emptyList(),
    val selectedScannerMiner: DiscoveredMiner? = null,
    val scannerUsernameInput: String = "root",
    val scannerPasswordInput: String = "root",
    val isScannerPasswordVisible: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Relay-only mode (v0.3.0): the persisted Settings switch. When
     * true the app shows only the relay status screen; the relay
     * underneath (service, auto-connect, uploads) is unaffected.
     */
    val relayOnlyMode: Boolean = false,
    /** Last successful relay upload, as reported by the relay itself (persisted). */
    val lastRelayUploadAtMs: Long? = null,
    /** Last relay upload error, as reported by the relay itself (persisted). */
    val relayLastError: String? = null,
    /**
     * The persisted state (cloud config, theme,
     * relay-only switch, relay status, saved profiles, nicknames) has
     * been loaded into this state (v0.3.3). False only in the pre-load
     * default state: screens that render persisted values (relay-only
     * status, Home) show skeleton placeholders while this is false, so
     * an unloaded default — relay off, no miners, no upload yet — can
     * never be mistaken for the phone's real state on a cold start.
     * Set once, in init, by the same update that loads the values.
     */
    val isInitialStateLoaded: Boolean = false,
    /**
     * The first cloud fleet fetch of this session has completed —
     * success OR failure (v0.3.3). Before it lands, the merged fleet
     * can only *look* empty (a cloud-only viewer's machines have not
     * arrived yet), so Home stays in its loading state and the
     * "Add a miner" card stays hidden. Set in the sync cycle right
     * after the fetch attempt resolves.
     */
    val hasCompletedFirstCloudFetch: Boolean = false,
    val selectedLogsTab: LogsTab = LogsTab.CURRENT,
    val currentLogText: String? = null,
    val historyLogText: String? = null,
    val isLoadingLog: Boolean = false,
    val logErrorMessage: String? = null,
    val lastLogUpdated: Long? = null,
    val dailyAnalysisState: DailyAnalysisUiState = DailyAnalysisUiState(),
    val cloudConfig: CloudSyncConfig = CloudSyncConfig(),
    /** The add-miner setup flow is open (entered only by an explicit tap). */
    val isLocalSetupOpen: Boolean = false,
    /**
     * Known machines on this Wi-Fi that rejected every credential the
     * phone has (v0.1.9 auto-connect): shown on Home as tappable cards
     * that open the credential entry prefilled with the machine's IP.
     * Entering the password once saves a profile, and auto-connect uses
     * it from then on.
     */
    val autoConnectNeedsPassword: List<NeedsPasswordMachine> = emptyList(),
    val remoteMiners: List<RemoteMinerSnapshot> = emptyList(),
    val selectedRemoteMiner: RemoteMinerSnapshot? = null,
    /**
     * Phone-only nicknames (v0.1.7): worker-identity key → the label this
     * phone shows for that machine. Persisted in app-private storage via
     * MinerStorageManager; never uploaded anywhere.
     */
    val nicknameOverrides: Map<String, String> = emptyMap(),
    val isTestingCloud: Boolean = false,
    val cloudTestMessage: String? = null,
    val isCloudConnected: Boolean = false,
    val lastCloudSyncTimestamp: Long? = null,
    val isBatchConnecting: Boolean = false,
    val batchConnectProgress: Float = 0f,
    val batchConnectMessage: String? = null,
    val isWifiConnected: Boolean = true,
    /** Newer GitHub release found by the update check, if any. */
    val availableUpdate: com.example.antminerconnectiontest.data.update.AvailableUpdate? = null,
    /** Tag of the update the user dismissed with "Later" this session. */
    val updatePromptDismissedTag: String? = null,
    val isCheckingForUpdate: Boolean = false,
    /** Result line for the manual "Check for updates" entry. */
    val updateCheckMessage: String? = null
)

class DashboardViewModel(
    application: Application,
    private val repository: MinerRepository,
    private val profileManager: MinerProfileManager,
    private val networkScanner: NetworkScanner,
    private val detector: AntminerDetector,
    private val multiMinerPoller: MultiMinerPoller,
    private val storageManager: MinerStorageManager?,
    private val cloudSyncManager: CloudSyncManager = CloudSyncManager(),
    private val analysisStorage: DailyAnalysisStorageManager? = null
) : AndroidViewModel(application) {

    // Primary constructor for Android Compose viewModel() / AndroidViewModelFactory
    constructor(application: Application) : this(
        application = application,
        repository = MinerRepository(analysisStorage = DailyAnalysisStorageManager(application)),
        profileManager = MinerProfileManager(MinerStorageManager(application)),
        networkScanner = NetworkScanner(),
        detector = AntminerDetector(),
        multiMinerPoller = MultiMinerPoller(),
        storageManager = MinerStorageManager(application),
        cloudSyncManager = CloudSyncManager(),
        analysisStorage = DailyAnalysisStorageManager(application)
    )

    // Constructor for testing
    constructor(
        repository: MinerRepository = MinerRepository(),
        profileManager: MinerProfileManager = MinerProfileManager(),
        networkScanner: NetworkScanner = NetworkScanner(),
        detector: AntminerDetector = AntminerDetector(),
        multiMinerPoller: MultiMinerPoller = MultiMinerPoller(),
        storageManager: MinerStorageManager? = null,
        cloudSyncManager: CloudSyncManager = CloudSyncManager(),
        analysisStorage: DailyAnalysisStorageManager? = null
    ) : this(
        application = Application(),
        repository = repository,
        profileManager = profileManager,
        networkScanner = networkScanner,
        detector = detector,
        multiMinerPoller = multiMinerPoller,
        storageManager = storageManager,
        cloudSyncManager = cloudSyncManager,
        analysisStorage = analysisStorage
    )

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /**
     * The unified machine list: saved local profiles merged with cloud
     * snapshots, one entry per physical machine (see [MinerMerge]).
     * Derived continuously from the source flows — profile saves, race
     * poller readings, cloud sync cycles and dashboard refreshes all flow
     * through here — so the merge survives every refresh cycle without
     * flickering or duplicating a machine.
     */
    val mergedMiners: StateFlow<List<MergedMinerEntry>> = combine(
        profileManager.savedProfiles,
        _uiState.map { Triple(it.remoteMiners, it.selectedRemoteMiner, it.nicknameOverrides) }.distinctUntilChanged(),
        multiMinerPoller.minerHashrates,
        repository.activeProfile,
        repository.dashboardData
    ) { profiles, remoteState, raceRates, active, data ->
        val (remoteSnapshots, selectedSnapshot, nicknameOverrides) = remoteState
        MinerMerge.merge(
            profiles = profiles,
            // The selected snapshot takes part in the merge even if a sync
            // cycle has not (or no longer) listed it — MinerMerge dedupes.
            // It is listed FIRST: dedupe keeps the freshest copy per
            // machine and breaks timestamp ties toward the later entry,
            // so a freshly fetched fleet row must come after the
            // selection-time copy to win those ties (v0.2.1).
            snapshots = listOfNotNull(selectedSnapshot) + remoteSnapshots,
            localData = MinerMerge.LocalData(
                activeProfileId = active?.id,
                activeDataProfileId = data?.profileId,
                activeDataOnline = data?.isOnline == true,
                activeHashrateTh = data?.summary?.let { summary ->
                    summary.hashrate5s.takeIf { it > 0.0 } ?: summary.hashrateAvg.takeIf { it > 0.0 }
                },
                raceHashrates = raceRates
            )
        ).map { entry ->
            // The phone's private nickname layer is applied last, as a
            // display name only — merging above never sees it, so a
            // rename can never split or duplicate a machine.
            entry.copy(
                phoneDisplayName = PhoneNicknames.displayName(
                    nicknameOverrides, entry.localProfile, entry.remoteSnapshot
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Sets (blank = clears) this phone's private nickname for ANY machine
     * in the fleet — a local profile or a remote-only machine alike. The
     * label is stored under the machine's worker identity on this device
     * only; it is never uploaded and never affects merging.
     */
    fun setPhoneNickname(entry: MergedMinerEntry, nickname: String) {
        val key = PhoneNicknames.keyForEntry(entry)
        if (key.isBlank()) return
        val updated = PhoneNicknames.withNickname(_uiState.value.nicknameOverrides, key, nickname)
        if (updated == _uiState.value.nicknameOverrides) return
        _uiState.update { it.copy(nicknameOverrides = updated) }
        try {
            storageManager?.savePhoneNicknames(updated)
        } catch (_: Throwable) {}
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        try {
            storageManager?.saveThemeMode(mode.name)
        } catch (_: Throwable) {}
    }

    /**
     * Relay-only mode (v0.3.0): persisted, so a site phone restarts
     * back into relay-only. Turning it on also closes the setup flow
     * (the two top-level flags must never fight — see
     * [MainShellRouting]) and is immediate: no confirmation. It changes
     * NOTHING underneath: the background relay service, auto-connect
     * triggers and uploads keep running exactly as in full mode.
     */
    fun setRelayOnlyMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                relayOnlyMode = enabled,
                isLocalSetupOpen = if (enabled) false else it.isLocalSetupOpen
            )
        }
        try {
            storageManager?.saveRelayOnlyMode(enabled)
        } catch (_: Throwable) {}
        if (enabled) refreshRelayStatus()
    }

    /**
     * Re-reads the relay's own persisted report (last successful
     * upload, last error) — written by the background service and by
     * this ViewModel's sync cycle — so the relay-only status screen
     * shows the relay's actual state. Read-only; changes no cadence.
     */
    fun refreshRelayStatus() {
        val storage = storageManager ?: return
        try {
            val (lastUpload, error) = storage.loadRelayStatus()
            _uiState.update {
                it.copy(lastRelayUploadAtMs = lastUpload, relayLastError = error)
            }
        } catch (_: Throwable) {}
    }

    fun toggleThemeMode() {
        // Cycle through all three modes so the header toggle can also
        // return to "follow system" without opening Settings.
        val next = when (_uiState.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    private var scanJob: Job? = null
    private var movedScanJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var autoConnectJob: Job? = null
    private var lastAutoConnectMs: Long = 0L
    /** Candidate-set fingerprint of the last auto-connect pass — a changed fleet forces a fresh pass inside the throttle window. */
    private var lastAutoConnectFingerprint: String = ""
    /** Sheet row ids already sent a legacy-duplicate delete request for this session. */
    private val legacyHealAttemptedIds = mutableSetOf<String>()
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var wifiRefreshCallback: android.net.ConnectivityManager.NetworkCallback? = null

    init {
        // Observe real-time Wi-Fi connectivity changes
        try {
            val cm = getApplication<Application>()?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                _uiState.update { it.copy(isWifiConnected = isWifiConnected()) }
                val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        _uiState.update { it.copy(isWifiConnected = isWifiConnected()) }
                    }
                    override fun onLost(network: android.net.Network) {
                        _uiState.update { it.copy(isWifiConnected = isWifiConnected()) }
                    }
                    override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                        _uiState.update { it.copy(isWifiConnected = isWifiConnected()) }
                    }
                }
                val request = android.net.NetworkRequest.Builder().build()
                cm.registerNetworkCallback(request, callback)
                networkCallback = callback
            }
        } catch (_: Throwable) {}

        // Load persistent cloud config
        if (storageManager != null) {
            val savedCloudConfig = storageManager.loadCloudConfig()
            // Load the saved profiles synchronously so the very first
            // frame renders the real fleet. (The start choice older
            // versions persisted is ignored since v0.1.9 — there is one
            // shell for everyone, so nothing routes on it anymore.)
            val savedThemeMode = storageManager.loadThemeMode()?.let { stored ->
                ThemeMode.values().firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM
            // v0.5.0: the appearance style is no longer loaded — the
            // Violet style is gone and Grouped Sheets is the only
            // design. A "key_app_style" value persisted by an older
            // install (e.g. "VIOLET") is simply never read again; the
            // app always opens in the one remaining design.
            val (savedLastUpload, savedRelayError) = storageManager.loadRelayStatus()
            _uiState.update {
                it.copy(
                    cloudConfig = savedCloudConfig,
                    themeMode = savedThemeMode,
                    relayOnlyMode = storageManager.loadRelayOnlyMode(),
                    lastRelayUploadAtMs = savedLastUpload,
                    relayLastError = savedRelayError,
                    savedProfiles = profileManager.savedProfiles.value,
                    nicknameOverrides = storageManager.loadPhoneNicknames(),
                    // The persisted state is IN this emission — from
                    // here on, screens may render real values instead
                    // of loading skeletons (v0.3.3).
                    isInitialStateLoaded = true
                )
            }
            if (savedCloudConfig.isSyncEnabled) {
                try {
                    com.example.antminerconnectiontest.service.MinerRelayService.start(getApplication())
                } catch (_: Throwable) {}
            }
        } else {
            // No storage (unit tests): there is no persisted state to
            // wait for, so the state counts as loaded from the start.
            _uiState.update { it.copy(isInitialStateLoaded = true) }
        }

        // Observe saved profiles
        viewModelScope.launch {
            profileManager.savedProfiles.collect { profiles ->
                _uiState.update { it.copy(savedProfiles = profiles) }
            }
        }

        // Observe repository active profile
        viewModelScope.launch {
            repository.activeProfile.collect { profile ->
                _uiState.update { it.copy(activeProfile = profile) }
                if (profile != null) {
                    repository.startPolling(viewModelScope)
                } else {
                    repository.stopPolling()
                }
            }
        }

        // Start multi-miner poller for the Hashrate Race (Wi-Fi aware)
        multiMinerPoller.startPolling(viewModelScope, isWifiConnected = { isWifiConnected() }) {
            profileManager.savedProfiles.value
        }
        viewModelScope.launch {
            multiMinerPoller.minerHashrates.collect { rateMap ->
                _uiState.update { it.copy(minerHashrates = rateMap) }
            }
        }

        // Observe dashboard data and evaluate alerts
        viewModelScope.launch {
            repository.dashboardData.collect { data ->
                val alerts = ThresholdAlertEngine.evaluateAlerts(data)
                _uiState.update {
                    it.copy(
                        dashboardData = data,
                        activeAlerts = alerts
                    )
                }
                // Also push active rate to multiMinerPoller
                if (data != null && data.isOnline) {
                    val rate = data.summary?.hashrate5s ?: data.summary?.hashrateAvg ?: 0.0
                    if (rate > 0.0 && _uiState.value.activeProfile != null) {
                        multiMinerPoller.updateHashrate(_uiState.value.activeProfile!!.id, rate)
                    }
                } else if (data != null && !data.isOnline && isWifiConnected()) {
                    triggerDynamicIpSelfHealing()
                }
            }
        }

        viewModelScope.launch {
            repository.isRefreshing.collect { refreshing ->
                _uiState.update { it.copy(isRefreshing = refreshing) }
            }
        }

        // Mirror the repository's published refresh error so the UI can show
        // a real error state (with Retry) instead of a silent "--" dashboard.
        viewModelScope.launch {
            repository.dashboardError.collect { error ->
                _uiState.update { it.copy(dashboardError = error) }
            }
        }

        // Auto-save any profile updates (e.g. newly captured workerName or MAC).
        // The repository only fires this for the currently active profile, but
        // guard here too: an auto-capture must never switch the visible miner.
        repository.onProfileAutoUpdated = { updated ->
            profileManager.saveOrUpdateProfile(updated)
            if (_uiState.value.activeProfile?.id == updated.id) {
                _uiState.update { it.copy(activeProfile = updated) }
            }
        }

        // Restore initial active profile if previously saved. Always attempt
        // the first fetch: if it cannot reach the miner, the repository now
        // publishes an error the UI can show with Retry — gating this on a
        // Wi-Fi check used to leave a silent blank dashboard on cold open.
        val initialProfile = profileManager.getActiveProfile()
        if (initialProfile != null) {
            repository.setActiveProfile(initialProfile)
            viewModelScope.launch {
                repository.refreshDashboardData()
            }
        }

        // Immediate cloud sync on startup (< 100ms)
        syncCloudNow()

        // Cold start already on Wi-Fi: auto-connect the known miners
        // (v0.1.9) — the fleet fetch lands first (syncCloudNow above),
        // then the pass probes saved profiles + fleet rows. The sync
        // cycle re-triggers passes as the fleet arrives.
        viewModelScope.launch {
            delay(1200L)
            runAutoConnect(force = true)
        }

        // Auto-refresh when Wi-Fi connects or disconnects. Stored in a field
        // (it used to be anonymous) so onCleared() can unregister it — the
        // leaked registration kept every dead ViewModel generation receiving
        // Wi-Fi events and firing ghost refreshes.
        try {
            val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    viewModelScope.launch {
                        delay(600L) // Wait 600ms for Wi-Fi IP assignment
                        repository.refreshDashboardData()
                        syncCloudNow()
                        // Joined a Wi-Fi network: try the known miners
                        // on it right away (forced pass, v0.1.9).
                        runAutoConnect(force = true)
                    }
                }

                override fun onLost(network: android.net.Network) {
                    viewModelScope.launch {
                        val active = profileManager.getActiveProfile()
                        repository.disconnect()
                        if (active != null) {
                            repository.setActiveProfile(active)
                        }
                        syncCloudNow()
                    }
                }
            }
            cm?.registerNetworkCallback(request, callback)
            wifiRefreshCallback = callback
        } catch (_: Throwable) {}

        // Start Cloud Sync Loop
        startCloudSyncLoop()

        // Wipe any leftover update download from a previous session
        // (v0.4.0): a half-downloaded APK is never resumed or
        // offered. Internal runCatching — cannot fail the init.
        com.example.antminerconnectiontest.data.update.UpdateDownloader
            .clearCache(getApplication())

        // One update check per cold start. Silent on failure/offline.
        // In relay-only mode the check is skipped entirely: a site
        // phone must never be interrupted by update UI (v0.3.0).
        if (!_uiState.value.relayOnlyMode) {
            checkForUpdates(manual = false)
        }

        // Default selection (v0.2.0, approved model): there is always
        // ONE selected machine — the hero's machine, which every tab
        // follows. When nothing is selected (no active local profile,
        // no explicit fleet pick) but the fleet is non-empty, settle
        // the selection on the first machine of the ranked fleet
        // (online first by hashrate, offline last). Explicit user
        // selections always win: the guard below never overrides one.
        viewModelScope.launch {
            mergedMiners.collect { entries ->
                applyDefaultSelection(entries)
                refreshSelectedSnapshot(entries)
            }
        }
    }

    /**
     * Keeps the remote SELECTION in step with the fleet (v0.2.1 fix).
     *
     * The selection used to be a snapshot copy frozen at tap time: cloud
     * sync refreshed [AppUiState.remoteMiners] — and with it the merged
     * fleet the list renders — but nothing ever updated the stored
     * selection, so the Home hero (and the tabs) kept showing
     * selection-time values until the user tapped the machine's row
     * again to re-select it. Here the selection is re-derived from the
     * SAME merged fleet state the list uses: whenever an entry for the
     * selected machine carries a different snapshot, the selection
     * adopts it — on resume refreshes, sync cycles and live polls alike,
     * with no tap needed.
     *
     * If the machine is absent from the fleet (a sync cycle no longer
     * lists it), the selection keeps its last known copy, as before.
     * The equality guard keeps this from looping: adopting the merged
     * copy re-emits the fleet once, after which the entry's snapshot
     * equals the selection and no further update happens.
     */
    internal fun refreshSelectedSnapshot(entries: List<MergedMinerEntry>) {
        val selected = _uiState.value.selectedRemoteMiner ?: return
        val fresh = entries.firstOrNull { it.remoteSnapshot?.id == selected.id }
            ?.remoteSnapshot ?: return
        if (fresh == selected) return
        _uiState.update { state ->
            // Re-check inside the update: the selection may have been
            // changed or cleared since this fleet emission was read.
            if (state.selectedRemoteMiner?.id != selected.id) state
            else state.copy(selectedRemoteMiner = fresh)
        }
    }

    private fun applyDefaultSelection(entries: List<MergedMinerEntry>) {
        val state = _uiState.value
        if (state.activeProfile != null || state.selectedRemoteMiner != null) return
        if (state.isLocalSetupOpen) return
        if (entries.isEmpty()) return
        val first = FleetSorting.sortEntries(entries, System.currentTimeMillis())
            .firstOrNull() ?: return
        val entry = first.entry
        val snapshot = entry.remoteSnapshot
        when {
            snapshot != null -> selectRemoteMiner(snapshot, switchToHome = false)
            entry.localProfile != null -> entry.localProfile?.let { selectSavedProfile(it) }
        }
    }

    /** Installed versionName ("0.1.4"), or null when it cannot be read. */
    fun installedVersionName(): String? {
        return try {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Checks GitHub Releases for a build newer than the installed one.
     * Automatic checks (cold start) only fill [AppUiState.availableUpdate]
     * — the prompt UI decides whether to show it — and fail silently.
     * Manual checks additionally report "You're on the latest version" (or that the
     * check failed) via [AppUiState.updateCheckMessage].
     */
    fun checkForUpdates(manual: Boolean) {
        if (_uiState.value.isCheckingForUpdate) return
        val installed = installedVersionName() ?: run {
            if (manual) {
                _uiState.update { it.copy(updateCheckMessage = "Couldn't check for updates.") }
            }
            return
        }
        _uiState.update {
            it.copy(
                isCheckingForUpdate = true,
                updateCheckMessage = if (manual) null else it.updateCheckMessage
            )
        }
        viewModelScope.launch {
            val result = com.example.antminerconnectiontest.data.update.UpdateChecker
                .fetchLatestUpdate(installed)
            result.fold(
                onSuccess = { update ->
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            // A manual check is authoritative; an automatic
                            // one only ever adds a prompt, never removes.
                            availableUpdate = if (manual) update else (update ?: it.availableUpdate),
                            // A manual find re-opens the update dialog
                            // even if this version was dismissed earlier
                            // in the session (v0.4.0 — the Settings check
                            // must always reach the in-app download).
                            updatePromptDismissedTag = if (manual && update != null) {
                                null
                            } else {
                                it.updatePromptDismissedTag
                            },
                            updateCheckMessage = when {
                                !manual -> it.updateCheckMessage
                                update != null -> "Update available: v${update.versionName}"
                                else -> "You're on the latest version (v$installed)."
                            }
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            updateCheckMessage = if (manual) {
                                "Couldn't check for updates. Check your connection and try again."
                            } else {
                                it.updateCheckMessage
                            }
                        )
                    }
                }
            )
        }
    }

    /**
     * In-app update download (v0.4.0): streams the offered release's
     * APK into the app cache; progress lives in
     * [com.example.antminerconnectiontest.data.update.UpdateDownloader.state],
     * which the update dialog renders. No browser is involved.
     */
    fun downloadUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        viewModelScope.launch {
            com.example.antminerconnectiontest.data.update.UpdateDownloader
                .download(getApplication(), update)
        }
    }

    /** Cancels an in-flight update download (partial file is wiped at next cold start / download). */
    fun cancelUpdateDownload() {
        com.example.antminerconnectiontest.data.update.UpdateDownloader.cancel()
    }

    /** Resets the download state after install hand-off or failure, so the dialog offers Download again. */
    fun resetUpdateDownload() {
        com.example.antminerconnectiontest.data.update.UpdateDownloader.reset()
    }

    /** "Later" on the update prompt: don't nag again for this version this session. */
    fun dismissUpdatePrompt() {
        _uiState.update {
            it.copy(updatePromptDismissedTag = it.availableUpdate?.tag ?: it.updatePromptDismissedTag)
        }
    }

    private fun startCloudSyncLoop() {
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            while (isActive) {
                val cfg = _uiState.value.cloudConfig
                // The cycle runs whenever a user name is set — even with
                // Relay OFF — because viewing remote machines is not gated
                // by the Relay switch; only the upload inside the cycle is.
                if (cfg.webhookUrl.isNotBlank() && cfg.userName.isNotBlank()) {
                    performCloudSyncCycle(cfg)
                }
                delay(20000L) // 20s interval
            }
        }
    }

    private suspend fun performCloudSyncCycle(cfg: CloudSyncConfig) {
        // 1. Fetch permitted remote miners FIRST. Viewing is NOT gated
        //    by the Relay switch — it happens whenever a user name is
        //    set. The fetched fleet also feeds two decisions below: the
        //    cross-user upload guard (a machine whose sheet row belongs
        //    to another relay user must not be re-uploaded under this
        //    user's name) and auto-connect's known-miner candidate set.
        val fetchRes = cloudSyncManager.fetchRemoteMiners(cfg.webhookUrl, cfg.userName)
        fetchRes.onSuccess { response ->
            _uiState.update {
                it.copy(
                    remoteMiners = response.miners,
                    isCloudConnected = true,
                    lastCloudSyncTimestamp = System.currentTimeMillis()
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(isCloudConnected = false)
            }
        }
        // The session's first fleet fetch has now resolved, one way or
        // the other (v0.3.3): Home may leave its loading state — the
        // fleet it shows from here is the real fleet, empty or not.
        if (!_uiState.value.hasCompletedFirstCloudFetch) {
            _uiState.update { it.copy(hasCompletedFirstCloudFetch = true) }
        }
        val fetched = fetchRes.getOrNull()

        // 2. Auto-connect known miners on this Wi-Fi (v0.1.9): with the
        //    fresh fleet in state, try the known IPs of saved profiles
        //    and fleet machines so on-site machines flip LIVE without
        //    any manual step. Throttled inside runAutoConnect.
        runAutoConnect()

        // 3. Upload local miners — only when the Relay switch is ON and
        //    connected to Wi-Fi.
        if (cfg.isSyncEnabled && isWifiConnected()) {
            val localProfiles = profileManager.savedProfiles.value
            val currentData = repository.dashboardData.value
            val isLocalOnline = currentData?.isOnline == true

            val localSnapshots = localProfiles.mapNotNull { profile ->
                val isActive = profile.id == repository.activeProfile.value?.id
                val rate5s = if (isActive && isLocalOnline) {
                    currentData?.summary?.hashrate5s ?: 0.0
                } else {
                    _uiState.value.minerHashrates[profile.id] ?: 0.0
                }
                val avgRate = if (isActive && isLocalOnline) {
                    currentData?.summary?.hashrateAvg ?: rate5s
                } else {
                    rate5s
                }

                // The sheet row's id is the MACHINE's identity: its worker
                // name (pool username), the same on every device and
                // install. A profile that does not know its worker name
                // yet is DEFERRED — never uploaded under this phone's
                // per-device profile UUID, which used to fork one machine
                // into two sheet rows (see RelayUpload). The name is
                // backfilled from the miner's pool config the first time
                // the machine is opened, so deferral is temporary.
                val sheetId = RelayUpload.sheetIdFor(profile)
                if ((rate5s > 0.0 || avgRate > 0.0) && sheetId != null) {
                    // Only upload telemetry that was actually measured. For
                    // non-active miners we only know the hashrate (race
                    // poller), so temp/fan are unknown: report 0, which
                    // consumers render as unknown. This used to fabricate
                    // 60 °C / 6000 RPM for every non-active miner.
                    val maxTemp = if (isActive && isLocalOnline) currentData?.stats?.maxChipTemp ?: 0 else 0
                    val maxFan = if (isActive && isLocalOnline) currentData?.stats?.maxFanRpm ?: 0 else 0
                    val base = RemoteMinerSnapshot(
                        user = cfg.userName.trim(),
                        id = sheetId,
                        // Phone-only nicknames never upload (v0.1.7):
                        // the nickname field carries the worker identity.
                        nickname = sheetId,
                        ip = profile.host,
                        hashrate5s = rate5s,
                        avgHashrate = avgRate,
                        maxTemp = maxTemp,
                        maxFan = maxFan,
                        status = "Online",
                        lastUpdated = ""
                    )
                    // Extended telemetry (v0.1.6): the full dashboard
                    // data exists only for the ACTIVE miner; every other
                    // profile contributes just its cached model name and
                    // leaves the rest unknown rather than borrowing the
                    // active machine's readings.
                    if (isActive && isLocalOnline && currentData != null) {
                        base.withTelemetry(
                            summary = currentData.summary,
                            stats = currentData.stats,
                            pools = currentData.pools,
                            systemInfo = currentData.systemInfo,
                            fallbackModel = profile.cachedModel ?: ""
                        )
                    } else {
                        base.withTelemetry(
                            summary = null, stats = null,
                            pools = emptyList(), systemInfo = null,
                            fallbackModel = profile.cachedModel ?: ""
                        )
                    }
                } else {
                    null
                }
            }

            if (localSnapshots.isNotEmpty()) {
                // Cross-user guard: a machine auto-connected locally
                // whose sheet row belongs to another relay user is fully
                // usable here but must NOT be uploaded under this user's
                // name — that would fork its sheet row. Machines absent
                // from the fleet (brand-new) and this user's own pass.
                val uploadable = RelayUpload.filterUploadable(
                    localSnapshots,
                    cfg.userName,
                    fetched?.miners ?: _uiState.value.remoteMiners
                )
                if (uploadable.isNotEmpty()) {
                    // Stamp the relay's own status report (v0.3.0) so
                    // the relay-only screen shows real upload state:
                    // success records the time and clears the error;
                    // failure records the error message.
                    val uploadResult = cloudSyncManager.uploadLocalMiners(cfg.webhookUrl, cfg.userName, uploadable)
                    uploadResult.fold(
                        onSuccess = {
                            val now = System.currentTimeMillis()
                            try { storageManager?.saveRelayStatus(now, null) } catch (_: Throwable) {}
                            _uiState.update { it.copy(lastRelayUploadAtMs = now, relayLastError = null) }
                        },
                        onFailure = { err ->
                            val message = err.message ?: "Upload failed"
                            try { storageManager?.saveRelayStatus(null, message) } catch (_: Throwable) {}
                            _uiState.update { it.copy(relayLastError = message) }
                        }
                    )
                }
            }
        }

        // 4. Heal legacy duplicates, best-effort: rows this user's
        //    machines accumulated under per-device UUID ids before
        //    uploads carried the worker identity. Selection is limited
        //    to this user's own rows (see RelayUpload), each id is
        //    requested at most once per session, and endpoint versions
        //    without delete support simply ignore the request.
        if (fetched != null) {
            val canonicalIds = profileManager.savedProfiles.value
                .mapNotNull { RelayUpload.sheetIdFor(it) }
            val duplicateIds = RelayUpload.legacyDuplicateIds(fetched.miners, cfg.userName, canonicalIds)
                .filter { it !in legacyHealAttemptedIds }
            if (duplicateIds.isNotEmpty()) {
                legacyHealAttemptedIds += duplicateIds
                cloudSyncManager.deleteRemoteRows(cfg.webhookUrl, cfg.userName, duplicateIds)
            }
        }
    }

    /**
     * Applies the cloud config with the SINGLE Relay switch. ON =
     * uploads local miners under [userName] and the 24/7 background
     * relay service runs; OFF = no uploads and the service is stopped.
     * Remote viewing is unaffected either way — the remote list and any
     * selected remote miner are kept, and a fetch runs immediately.
     */
    fun updateCloudConfig(
        webhookUrl: String,
        userName: String,
        relayEnabled: Boolean
    ) {
        val effectiveUrl = webhookUrl.trim().ifBlank { _uiState.value.cloudConfig.webhookUrl.ifBlank { CloudSyncConfig.DEFAULT_WEBHOOK_URL } }
        val newCfg = CloudSyncConfig(
            webhookUrl = effectiveUrl,
            userName = userName.trim(),
            isSyncEnabled = relayEnabled,
            isAutoUploadEnabled = relayEnabled,
            isDownloadRemoteEnabled = true,
            isBackgroundServiceEnabled = relayEnabled
        )
        _uiState.update {
            it.copy(
                cloudConfig = newCfg,
                cloudTestMessage = if (relayEnabled) {
                    "✅ Configuration saved successfully!"
                } else {
                    "Relay is off — your miners are not uploaded. Remote viewing still works."
                }
            )
        }
        storageManager?.saveCloudConfig(newCfg)

        if (relayEnabled) {
            try {
                com.example.antminerconnectiontest.service.MinerRelayService.start(getApplication())
            } catch (_: Throwable) {}
        } else {
            try {
                com.example.antminerconnectiontest.service.MinerRelayService.stop(getApplication())
            } catch (_: Throwable) {}
        }

        syncCloudNow()
    }

    fun testCloudConnection(webhookUrl: String, userName: String) {
        val effectiveUrl = webhookUrl.trim().ifBlank { _uiState.value.cloudConfig.webhookUrl.ifBlank { CloudSyncConfig.DEFAULT_WEBHOOK_URL } }
        if (userName.isBlank()) {
            _uiState.update { it.copy(cloudTestMessage = "Please enter your User Name.") }
            return
        }

        _uiState.update { it.copy(isTestingCloud = true, cloudTestMessage = null) }
        viewModelScope.launch {
            val result = cloudSyncManager.fetchRemoteMiners(effectiveUrl, userName)
            result.fold(
                onSuccess = { resp ->
                    _uiState.update {
                        it.copy(
                            isTestingCloud = false,
                            isCloudConnected = true,
                            cloudTestMessage = "Connected as ${resp.user} • Can View: ${resp.canView ?: "ALL"} • Found ${resp.miners.size} miners",
                            remoteMiners = resp.miners,
                            lastCloudSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isTestingCloud = false,
                            isCloudConnected = false,
                            cloudTestMessage = "Connection failed: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun syncCloudNow() {
        val cfg = _uiState.value.cloudConfig
        // No Relay-switch gate: syncing includes fetching remote miners,
        // which stays available with Relay OFF.
        if (cfg.webhookUrl.isNotBlank() && cfg.userName.isNotBlank()) {
            viewModelScope.launch {
                performCloudSyncCycle(cfg)
            }
        }
    }

    fun selectDashboardTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val isLocalOnline = isWifiConnected() && _uiState.value.dashboardData?.isOnline == true && _uiState.value.selectedRemoteMiner == null
        if (tab == DashboardTab.LOGS && isLocalOnline) {
            val activeId = _uiState.value.activeProfile?.id
            val dailyMinerMismatch = _uiState.value.dailyAnalysisState.loadedForMinerId != activeId
            if (_uiState.value.selectedLogsTab == LogsTab.CURRENT && _uiState.value.currentLogText == null) {
                fetchCurrentLog()
            } else if (_uiState.value.selectedLogsTab == LogsTab.HISTORY && _uiState.value.historyLogText == null) {
                fetchHistoryLog()
            } else if (_uiState.value.selectedLogsTab == LogsTab.DAILY_ANALYSIS && (_uiState.value.dailyAnalysisState.availableDates.isEmpty() || dailyMinerMismatch)) {
                loadDailyAnalysisDates(forceRefresh = true)
            }
        }
    }

    fun selectLogsTab(tab: LogsTab) {
        _uiState.update { it.copy(selectedLogsTab = tab) }
        val isLocalOnline = isWifiConnected() && _uiState.value.dashboardData?.isOnline == true && _uiState.value.selectedRemoteMiner == null
        val activeId = _uiState.value.activeProfile?.id
        val dailyMinerMismatch = _uiState.value.dailyAnalysisState.loadedForMinerId != activeId

        if (tab == LogsTab.CURRENT && _uiState.value.currentLogText == null && isLocalOnline) {
            fetchCurrentLog()
        } else if (tab == LogsTab.HISTORY && _uiState.value.historyLogText == null && isLocalOnline) {
            fetchHistoryLog()
        } else if (tab == LogsTab.DAILY_ANALYSIS && isLocalOnline) {
            if (_uiState.value.dailyAnalysisState.availableDates.isEmpty() || dailyMinerMismatch) {
                loadDailyAnalysisDates(forceRefresh = true)
            }
        }
    }

    fun fetchCurrentLog(forceRefresh: Boolean = false) {
        val isLocalOnline = isWifiConnected() && _uiState.value.dashboardData?.isOnline == true && _uiState.value.selectedRemoteMiner == null
        if (!isLocalOnline && !forceRefresh) return
        if (!forceRefresh && _uiState.value.currentLogText != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLog = true, logErrorMessage = null) }
            val res = repository.fetchCurrentLog()
            res.fold(
                onSuccess = { logText ->
                    _uiState.update {
                        it.copy(
                            currentLogText = logText,
                            isLoadingLog = false,
                            lastLogUpdated = System.currentTimeMillis(),
                            logErrorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingLog = false,
                            logErrorMessage = err.message ?: "Failed to fetch current log"
                        )
                    }
                }
            )
        }
    }

    fun fetchHistoryLog(forceRefresh: Boolean = false) {
        val isLocalOnline = isWifiConnected() && _uiState.value.dashboardData?.isOnline == true && _uiState.value.selectedRemoteMiner == null
        if (!isLocalOnline && !forceRefresh) return
        if (!forceRefresh && _uiState.value.historyLogText != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLog = true, logErrorMessage = null) }
            val res = repository.fetchHistoryLog()
            res.fold(
                onSuccess = { logText ->
                    _uiState.update {
                        it.copy(
                            historyLogText = logText,
                            isLoadingLog = false,
                            lastLogUpdated = System.currentTimeMillis(),
                            logErrorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingLog = false,
                            logErrorMessage = err.message ?: "Failed to fetch history log"
                        )
                    }
                }
            )
        }
    }

    fun refreshActiveLog() {
        when (_uiState.value.selectedLogsTab) {
            LogsTab.CURRENT -> fetchCurrentLog(forceRefresh = true)
            LogsTab.HISTORY -> fetchHistoryLog(forceRefresh = true)
            LogsTab.DAILY_ANALYSIS -> loadDailyAnalysisDates(forceRefresh = true)
        }
    }

    fun loadDailyAnalysisDates(forceRefresh: Boolean = false) {
        val profile = _uiState.value.activeProfile ?: return
        val isLocalOnline = isWifiConnected() && _uiState.value.dashboardData?.isOnline == true && _uiState.value.selectedRemoteMiner == null
        val minerChanged = _uiState.value.dailyAnalysisState.loadedForMinerId != profile.id

        if (!isLocalOnline && !forceRefresh && !minerChanged) return
        if (!forceRefresh && !minerChanged && _uiState.value.dailyAnalysisState.availableDates.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    dailyAnalysisState = it.dailyAnalysisState.copy(
                        loadedForMinerId = profile.id,
                        isFetchingDates = true,
                        errorMessage = null,
                        availableDates = if (minerChanged) emptyList() else it.dailyAnalysisState.availableDates,
                        selectedAnalysisDetail = if (minerChanged) null else it.dailyAnalysisState.selectedAnalysisDetail
                    )
                )
            }

            val remoteDatesRes = repository.getAvailableLogDates()
            val storedAnalyses = repository.getStoredDailyAnalyses(profile.id).associateBy { it.date }

            remoteDatesRes.fold(
                onSuccess = { rawDateList ->
                    val items = rawDateList.map { rawId ->
                        val dateStr = DailyLogAnalyzer.convertRawIdToDate(rawId)
                        val display = DailyLogAnalyzer.formatHumanReadableDate(dateStr)
                        val isToday = DailyLogAnalyzer.isTodayDate(dateStr)
                        val analysis = storedAnalyses[dateStr]
                        DailyLogDateItem(
                            rawDateId = rawId,
                            dateString = dateStr,
                            formattedDisplay = display,
                            isAnalyzed = analysis != null,
                            isToday = isToday,
                            analysis = analysis
                        )
                    }.sortedByDescending { it.dateString }

                    _uiState.update {
                        it.copy(
                            dailyAnalysisState = it.dailyAnalysisState.copy(
                                loadedForMinerId = profile.id,
                                availableDates = items,
                                isFetchingDates = false,
                                errorMessage = null
                            )
                        )
                    }
                },
                onFailure = { err ->
                    // Fallback to locally stored analyses for this machine if remote query fails
                    val fallbackItems = storedAnalyses.values.map { analysis ->
                        DailyLogDateItem(
                            rawDateId = analysis.rawDateId,
                            dateString = analysis.date,
                            formattedDisplay = DailyLogAnalyzer.formatHumanReadableDate(analysis.date),
                            isAnalyzed = true,
                            isToday = DailyLogAnalyzer.isTodayDate(analysis.date),
                            analysis = analysis
                        )
                    }.sortedByDescending { it.dateString }

                    _uiState.update {
                        it.copy(
                            dailyAnalysisState = it.dailyAnalysisState.copy(
                                loadedForMinerId = profile.id,
                                availableDates = fallbackItems,
                                isFetchingDates = false,
                                errorMessage = if (fallbackItems.isEmpty()) (err.message ?: "Failed to load log dates from miner") else null
                            )
                        )
                    }
                }
            )
        }
    }

    fun toggleDailyDateSelection(rawDateId: String) {
        _uiState.update { state ->
            val currentSelected = state.dailyAnalysisState.selectedRawDateIds.toMutableSet()
            if (currentSelected.contains(rawDateId)) {
                currentSelected.remove(rawDateId)
            } else {
                currentSelected.add(rawDateId)
            }
            state.copy(
                dailyAnalysisState = state.dailyAnalysisState.copy(
                    selectedRawDateIds = currentSelected
                )
            )
        }
    }

    fun selectAllUnanalyzedDates() {
        _uiState.update { state ->
            val unanalyzed = state.dailyAnalysisState.availableDates
                .filter { !it.isAnalyzed || it.isToday }
                .map { it.rawDateId }
                .toSet()
            state.copy(
                dailyAnalysisState = state.dailyAnalysisState.copy(
                    selectedRawDateIds = unanalyzed
                )
            )
        }
    }

    fun clearDailyDateSelection() {
        _uiState.update { state ->
            state.copy(
                dailyAnalysisState = state.dailyAnalysisState.copy(
                    selectedRawDateIds = emptySet()
                )
            )
        }
    }

    fun startDailyAnalysis(targetRawIds: List<String>) {
        if (targetRawIds.isEmpty()) return
        if (_uiState.value.dailyAnalysisState.isAnalyzing) return

        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    dailyAnalysisState = state.dailyAnalysisState.copy(
                        isAnalyzing = true,
                        analysisPhaseMessage = "Initializing log acquisition...",
                        analysisProgress = 0.05f,
                        errorMessage = null
                    )
                )
            }

            val result = repository.analyzeLogDates(
                selectedRawDateIds = targetRawIds,
                cacheDir = app.cacheDir,
                onProgress = { phase, progress ->
                    _uiState.update { state ->
                        state.copy(
                            dailyAnalysisState = state.dailyAnalysisState.copy(
                                analysisPhaseMessage = phase,
                                analysisProgress = progress
                            )
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { analyses ->
                    val profile = _uiState.value.activeProfile
                    val storedAnalyses = if (profile != null) {
                        repository.getStoredDailyAnalyses(profile.id).associateBy { it.date }
                    } else {
                        emptyMap()
                    }

                    _uiState.update { state ->
                        val updatedDates = state.dailyAnalysisState.availableDates.map { item ->
                            val analysis = storedAnalyses[item.dateString]
                            item.copy(
                                isAnalyzed = analysis != null,
                                analysis = analysis
                            )
                        }

                        state.copy(
                            dailyAnalysisState = state.dailyAnalysisState.copy(
                                availableDates = updatedDates,
                                isAnalyzing = false,
                                analysisPhaseMessage = null,
                                analysisProgress = 0f,
                                selectedRawDateIds = emptySet(),
                                selectedAnalysisDetail = if (analyses.size == 1) analyses.first() else state.dailyAnalysisState.selectedAnalysisDetail,
                                errorMessage = null
                            )
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            dailyAnalysisState = state.dailyAnalysisState.copy(
                                isAnalyzing = false,
                                analysisPhaseMessage = null,
                                analysisProgress = 0f,
                                errorMessage = err.message ?: "Log analysis failed"
                            )
                        )
                    }
                }
            )
        }
    }

    fun openDailyAnalysisDetail(analysis: DailyMinerAnalysis) {
        _uiState.update { state ->
            state.copy(
                dailyAnalysisState = state.dailyAnalysisState.copy(
                    selectedAnalysisDetail = analysis
                )
            )
        }
    }

    fun closeDailyAnalysisDetail() {
        _uiState.update { state ->
            state.copy(
                dailyAnalysisState = state.dailyAnalysisState.copy(
                    selectedAnalysisDetail = null
                )
            )
        }
    }

    fun selectSetupTab(tab: SetupTab) {
        // Deliberately NO auto-scan here: a subnet scan only ever starts
        // from an explicit user action (the scan button on the setup
        // screen) — never as a side effect of navigation. (Auto-connect
        // of KNOWN miners is a different mechanism: no scan, see
        // runAutoConnect.)
        _uiState.update { it.copy(setupTab = tab) }
    }

    /** Opens the add-miner setup flow (scan / manual connect) inside the one shell. */
    fun openLocalSetup() {
        _uiState.update { it.copy(isLocalSetupOpen = true) }
    }

    // ── Auto-connect of known miners (v0.1.9) ─────────────────────────
    //
    // When the phone is on a Wi-Fi network, try the known IPs of the
    // machines this phone already knows — saved profiles and cloud
    // fleet rows — and turn the ones that answer into normal local
    // miners. No subnet scan, no unknown machines: the candidate set,
    // the identity verification and the failure classification are the
    // pure rules in AutoConnect, shared with the background relay
    // service so both paths behave identically.
    //
    // Triggers: Wi-Fi onAvailable (forced), app cold start on Wi-Fi
    // (forced, from init), and every cloud sync cycle (throttled — a
    // changed candidate set forces a pass inside the window).

    /** Minimum spacing between untriggered auto-connect passes. */
    private fun runAutoConnect(force: Boolean = false) {
        if (!isWifiConnected()) return
        if (autoConnectJob?.isActive == true) return
        val candidates = AutoConnect.buildCandidates(
            profileManager.savedProfiles.value,
            _uiState.value.remoteMiners
        )
        if (candidates.isEmpty()) return
        val fingerprint = candidates.joinToString("|") { "${it.identity}@${it.probeIps.joinToString(",")}" }
        val now = System.currentTimeMillis()
        if (!force && fingerprint == lastAutoConnectFingerprint && now - lastAutoConnectMs < 45_000L) return
        lastAutoConnectFingerprint = fingerprint
        lastAutoConnectMs = now
        autoConnectJob = viewModelScope.launch(Dispatchers.IO) {
            autoConnectPass(candidates)
        }
    }

    private suspend fun autoConnectPass(candidates: List<AutoConnectCandidate>) {
        val knownIdentities = candidates.mapNotNull {
            it.identity.takeIf { id -> id.isNotBlank() }
        }.toSet()
        // saveOrUpdateProfile moves the profile manager's active pointer
        // to the merged profile; remember the user's machine so the
        // pass never steals it (a NEW active miner is only adopted when
        // the user had none — see below).
        val previousActiveId = profileManager.getActiveProfile()?.id
        val needsPassword = mutableListOf<NeedsPasswordMachine>()
        var savedAny = false

        for (candidate in candidates) {
            for (ip in candidate.probeIps) {
                val username = candidate.savedProfile?.username?.ifBlank { AutoConnect.DEFAULT_USERNAME }
                    ?: AutoConnect.DEFAULT_USERNAME
                val password = candidate.savedProfile?.password?.ifBlank { AutoConnect.DEFAULT_PASSWORD }
                    ?: AutoConnect.DEFAULT_PASSWORD
                val result = detector.verifyMinerWithCredentials(ip, username, password)
                if (result.isConfirmed) {
                    when (AutoConnect.classify(candidate, result.workerName, knownIdentities)) {
                        ProbeClass.MATCH -> {
                            adoptAutoConnected(candidate, ip, username, result)
                            savedAny = true
                        }
                        ProbeClass.DIFFERENT_KNOWN_MACHINE -> {
                            // A different known fleet machine lives at
                            // this IP now (DHCP swap): claim THAT
                            // machine at this address instead.
                            val resolvedNorm = MinerMerge.normalizeWorkerName(result.workerName)
                            val other = candidates.firstOrNull { it.identity == resolvedNorm }
                            if (other != null) {
                                adoptAutoConnected(other, ip, username, result)
                                savedAny = true
                            }
                        }
                        // Unknown or unverifiable machine at a known IP:
                        // the known-only rule — never added. Its fleet
                        // row (if any) keeps showing the snapshot.
                        ProbeClass.UNKNOWN_MACHINE, ProbeClass.UNVERIFIED -> Unit
                        ProbeClass.NEEDS_PASSWORD, ProbeClass.UNREACHABLE -> Unit
                    }
                    break // Answered (one way or another) — next candidate.
                }

                // Not confirmed: distinguish "miner there, password
                // wrong" from "nothing there" via the unauthenticated
                // signature probe.
                val signature = detector.detectSignature(ip)
                val failure = AutoConnect.classifyFailure(
                    reachable = signature != com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.OFFLINE,
                    isAntminer = signature == com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.CONFIRMED_ANTMINER ||
                        signature == com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.POSSIBLE_ANTMINER_AUTH_REQUIRED
                )
                if (failure == ProbeClass.NEEDS_PASSWORD) {
                    needsPassword += NeedsPasswordMachine(
                        identity = candidate.identity,
                        displayName = candidate.snapshot?.let {
                            PhoneNicknames.displayName(_uiState.value.nicknameOverrides, null, it)
                        } ?: candidate.savedProfile?.effectiveName ?: ip,
                        ip = ip,
                        ownerUser = candidate.sheetOwner
                    )
                    break // Miner is at this IP; trying the alternate address is pointless.
                }
                if (failure == ProbeClass.UNKNOWN_MACHINE) break
                // UNREACHABLE: fall through to the alternate IP, if any.
            }
        }

        _uiState.update { it.copy(autoConnectNeedsPassword = needsPassword.distinctBy { it.key }) }

        if (previousActiveId != null) {
            // Restore the user's active pointer (see above).
            if (profileManager.getActiveProfile()?.id != previousActiveId) {
                profileManager.selectProfile(previousActiveId)
            }
        } else if (savedAny && _uiState.value.activeProfile == null) {
            // The user had no local miner (e.g. a phone that only ever
            // viewed the fleet): the first auto-connected machine
            // becomes the dashboard's miner, like any manual connect.
            profileManager.getActiveProfile()?.let { profile ->
                _uiState.update { it.copy(activeProfile = profile) }
                repository.setActiveProfile(profile)
                viewModelScope.launch { repository.refreshDashboardData() }
            }
        }
        if (savedAny) {
            // Fresh profiles feed the race poller and the next upload
            // cycle immediately instead of at the next 20 s tick.
            syncCloudNow()
        }
    }

    /**
     * Saves the machine an auto-connect probe verified: an existing
     * profile is updated in place (host, learned worker identity, the
     * credentials that worked); a fleet-only machine gets a new profile
     * carrying the verified identity and those credentials (encrypted at
     * rest by MinerStorageManager, never uploaded anywhere).
     */
    private fun adoptAutoConnected(
        candidate: AutoConnectCandidate,
        ip: String,
        username: String,
        result: com.example.antminerconnectiontest.data.scanner.MinerVerificationResult
    ) {
        val resolvedRaw = result.workerName?.trim().orEmpty()
        val existing = candidate.savedProfile
        val profile = if (existing != null) {
            existing.copy(
                host = ip,
                workerName = resolvedRaw.ifBlank { existing.workerName },
                username = username,
                password = result.effectivePassword,
                macAddress = result.macAddress?.ifBlank { existing.macAddress } ?: existing.macAddress,
                displayName = result.modelName ?: existing.displayName,
                cachedModel = result.modelName ?: existing.cachedModel,
                lastConnected = System.currentTimeMillis()
            )
        } else {
            MinerProfile(
                workerName = resolvedRaw,
                macAddress = result.macAddress.orEmpty(),
                displayName = result.modelName
                    ?: candidate.snapshot?.model?.ifBlank { null }
                    ?: "Antminer",
                host = ip,
                username = username,
                password = result.effectivePassword,
                cachedModel = result.modelName ?: candidate.snapshot?.model?.ifBlank { null }
            )
        }
        profileManager.saveOrUpdateProfile(profile)
    }

    /**
     * A needs-password card tap: open the existing manual credential
     * entry with the machine's IP prefilled. Connecting there saves the
     * profile with the entered password, and auto-connect uses it from
     * then on.
     */
    fun openNeedsPasswordEntry(machine: NeedsPasswordMachine) {
        _uiState.update {
            it.copy(
                setupForm = it.setupForm.copy(
                    ip = machine.ip,
                    username = AutoConnect.DEFAULT_USERNAME,
                    password = "",
                    errorMessage = null
                ),
                isLocalSetupOpen = true
            )
        }
        selectSetupTab(SetupTab.MANUAL)
    }

    fun closeLocalSetup() {
        _uiState.update { it.copy(isLocalSetupOpen = false) }
    }

    fun toggleProfileSwitcher(isOpen: Boolean? = null) {
        _uiState.update {
            it.copy(isProfileSwitcherOpen = isOpen ?: !it.isProfileSwitcherOpen)
        }
    }

    fun selectRemoteMiner(remote: RemoteMinerSnapshot) {
        selectRemoteMiner(remote, switchToHome = true)
    }

    /**
     * A pick from the Hardware / Pools / Logs header machine switcher
     * (v0.5.2): exactly the selection a Home fleet-row pick makes —
     * including activating the local profile of a merged machine, so
     * live data can take over — but the user STAYS on the tab they are
     * viewing. Every tab rebinds to the newly picked machine.
     */
    fun selectRemoteMinerFromTabHeader(remote: RemoteMinerSnapshot) {
        selectRemoteMiner(remote, switchToHome = false)
    }

    /**
     * [switchToHome] is true for deliberate user picks (a fleet-row tap
     * lands on Home, as before); the v0.2.0 default-selection passes
     * false so merely opening another tab never yanks the user Home.
     */
    private fun selectRemoteMiner(remote: RemoteMinerSnapshot, switchToHome: Boolean) {
        // Same machine saved locally? Then this is ONE machine, not two:
        // activate the local profile as well, so live local data is fetched
        // and wins for display (Home shows the remote snapshot only while
        // local data for this machine is unavailable). The remote selection
        // itself is kept — it is the user's deliberate view choice.
        val localMatch = MinerMerge.findLocalMatch(remote, _uiState.value.savedProfiles)
        if (localMatch != null) {
            _uiState.update {
                val updated = it.copy(
                    selectedRemoteMiner = remote,
                    activeProfile = localMatch,
                    currentLogText = null,
                    historyLogText = null,
                    dailyAnalysisState = DailyAnalysisUiState(loadedForMinerId = localMatch.id)
                )
                if (switchToHome) updated.copy(selectedTab = DashboardTab.HOME) else updated
            }
            profileManager.selectProfile(localMatch.id)
            repository.setActiveProfile(localMatch)
            viewModelScope.launch {
                repository.refreshDashboardData()
            }
        } else {
            _uiState.update {
                val updated = it.copy(selectedRemoteMiner = remote)
                if (switchToHome) updated.copy(selectedTab = DashboardTab.HOME) else updated
            }
        }
    }

    fun clearRemoteMinerView() {
        _uiState.update {
            it.copy(selectedRemoteMiner = null)
        }
    }

    /**
     * Lands the user on the Home tab with [profile] active and any
     * add-miner setup flow closed. Completing an add flow (scan batch,
     * single connect, manual add) must end here — Home is the default
     * screen once at least one miner exists, and opening a miner's
     * detail is never the gateway to it.
     */
    private fun landOnHomeWith(profile: MinerProfile) {
        selectSavedProfile(profile)
        _uiState.update {
            it.copy(
                isLocalSetupOpen = false,
                selectedTab = DashboardTab.HOME
            )
        }
    }

    fun selectSavedProfile(profile: MinerProfile) {
        _uiState.update {
            it.copy(
                selectedRemoteMiner = null,
                activeProfile = profile,
                currentLogText = null,
                historyLogText = null,
                dailyAnalysisState = DailyAnalysisUiState(loadedForMinerId = profile.id)
            )
        }
        profileManager.selectProfile(profile.id)
        repository.setActiveProfile(profile)
        viewModelScope.launch {
            repository.refreshDashboardData()
            if (_uiState.value.selectedTab == DashboardTab.LOGS) {
                when (_uiState.value.selectedLogsTab) {
                    LogsTab.CURRENT -> fetchCurrentLog(forceRefresh = true)
                    LogsTab.HISTORY -> fetchHistoryLog(forceRefresh = true)
                    LogsTab.DAILY_ANALYSIS -> loadDailyAnalysisDates(forceRefresh = true)
                }
            }
        }
    }

    fun updateMinerNicknameAndLocation(profileId: String, newNickname: String, newLocation: String) {
        profileManager.updateNicknameAndLocation(profileId, newNickname, newLocation)
        if (_uiState.value.activeProfile?.id == profileId) {
            val updated = profileManager.getActiveProfile()
            if (updated != null) {
                repository.setActiveProfile(updated)
                // Refresh right away: a rename used to blank/stale the
                // dashboard until the next 15 s poll tick.
                viewModelScope.launch {
                    repository.refreshDashboardData()
                }
            }
        }
    }

    /**
     * Called when the app returns to the foreground. Refreshes the dashboard
     * if there is no data yet, the last refresh failed, or the data is older
     * than ~2 poll cycles — previously the app just showed whatever stale
     * state it had when the phone slept.
     *
     * v0.2.1: the cloud side is covered too. A REMOTE selection has no
     * local profile to poll — its data is the relay snapshot — and the
     * old early return (no active profile → nothing) meant resume never
     * re-fetched it; the hero then sat on its selection-time copy until
     * the machine's row was tapped. Now a stale (or never-run) cloud
     * sync is re-triggered on resume regardless of the local profile,
     * and the arriving fleet refresh re-derives the selection (see
     * [refreshSelectedSnapshot]). [syncCloudNow] no-ops when cloud
     * viewing is not configured.
     */
    fun refreshIfStale() {
        val lastSync = _uiState.value.lastCloudSyncTimestamp
        val cloudStale = lastSync == null ||
            System.currentTimeMillis() - lastSync > 30_000L
        if (cloudStale) {
            syncCloudNow()
        }
        if (_uiState.value.activeProfile == null) return
        val data = _uiState.value.dashboardData
        val isStale = data == null ||
            _uiState.value.dashboardError != null ||
            System.currentTimeMillis() - data.lastUpdated > 30_000L
        // v0.5.1: while the viewed machine is shown from its cloud
        // snapshot and the phone is off Wi-Fi, the local dial cannot
        // succeed — the cloud re-sync above is the whole refresh.
        if (isStale && !snapshotShownOffWifi()) {
            viewModelScope.launch {
                repository.refreshDashboardData()
            }
        }
    }

    fun deleteSavedProfile(profileId: String) {
        profileManager.removeProfile(profileId)
        if (_uiState.value.activeProfile?.id == profileId) {
            val remaining = profileManager.getActiveProfile()
            if (remaining != null) {
                selectSavedProfile(remaining)
            } else {
                disconnect()
            }
        }
    }

    fun onSetupIpChanged(ip: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(ip = ip, errorMessage = null)) }
    }

    fun onSetupUsernameChanged(username: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(username = username, errorMessage = null)) }
    }

    fun onSetupPasswordChanged(password: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(password = password, errorMessage = null)) }
    }

    fun toggleSetupPasswordVisibility() {
        _uiState.update {
            it.copy(setupForm = it.setupForm.copy(isPasswordVisible = !it.setupForm.isPasswordVisible))
        }
    }

    fun onScannerUsernameChanged(username: String) {
        _uiState.update { it.copy(scannerUsernameInput = username) }
    }

    fun onScannerPasswordChanged(password: String) {
        _uiState.update { it.copy(scannerPasswordInput = password) }
    }

    fun toggleScannerPasswordVisibility() {
        _uiState.update { it.copy(isScannerPasswordVisible = !it.isScannerPasswordVisible) }
    }

    fun selectScannerMiner(miner: DiscoveredMiner?) {
        _uiState.update {
            it.copy(
                selectedScannerMiner = miner,
                scannerUsernameInput = "root",
                scannerPasswordInput = "root"
            )
        }
    }

    private var isHealingIp = false

    private fun triggerDynamicIpSelfHealing() {
        val active = _uiState.value.activeProfile ?: return
        if (active.workerName.isBlank() || isHealingIp || !isWifiConnected()) return

        isHealingIp = true
        viewModelScope.launch {
            try {
                val newIp = networkScanner.findMinerByWorkerName(
                    workerName = active.workerName,
                    username = active.username,
                    password = active.password,
                    context = getApplication()
                )
                if (newIp != null && newIp != active.host) {
                    val healed = profileManager.updateProfileHostByWorkerName(active.workerName, newIp)
                    if (healed != null) {
                        repository.setActiveProfile(healed)
                        _uiState.update { it.copy(activeProfile = healed) }
                        repository.refreshDashboardData()
                    }
                }
            } finally {
                isHealingIp = false
            }
        }
    }

    /**
     * Verifies manual credentials against /cgi-bin/get_system_info.cgi and enters dashboard if confirmed.
     */
    fun connectAndVerifyManual(nickname: String = "", location: String = "Default Location") {
        val form = _uiState.value.setupForm
        val cleanIp = form.ip.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

        if (cleanIp.isBlank()) {
            _uiState.update { it.copy(setupForm = it.setupForm.copy(errorMessage = "Please enter an IP address.")) }
            return
        }

        _uiState.update { it.copy(setupForm = it.setupForm.copy(isVerifying = true, errorMessage = null)) }

        viewModelScope.launch {
            val result = detector.verifyMinerWithCredentials(
                ip = cleanIp,
                username = form.username.trim(),
                password = form.password.ifBlank { "root" }
            )

            if (result.isConfirmed) {
                val profile = MinerProfile(
                    workerName = result.workerName.orEmpty(),
                    macAddress = result.macAddress.orEmpty(),
                    displayName = result.modelName ?: "Antminer S19k Pro",
                    nickname = nickname,
                    location = location,
                    host = cleanIp,
                    username = form.username.trim(),
                    password = result.effectivePassword,
                    cachedModel = result.modelName
                )
                profileManager.saveOrUpdateProfile(profile)
                val persisted = profileManager.getActiveProfile() ?: profile
                repository.setActiveProfile(persisted)
                repository.refreshDashboardData()
                _uiState.update {
                    it.copy(
                        activeProfile = persisted,
                        setupForm = it.setupForm.copy(isVerifying = false),
                        isLocalSetupOpen = false,
                        selectedTab = DashboardTab.HOME,
                        currentLogText = null,
                        historyLogText = null,
                        dailyAnalysisState = DailyAnalysisUiState(loadedForMinerId = persisted.id)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        setupForm = it.setupForm.copy(
                            isVerifying = false,
                            errorMessage = "This device is not a supported Antminer or authentication failed."
                        )
                    )
                }
            }
        }
    }

    /**
     * Connects a miner discovered from network scan with custom username/password.
     */
    fun connectDiscoveredMiner(miner: DiscoveredMiner, username: String, pass: String, nickname: String = "", location: String = "Default Location") {
        val cleanIp = miner.ip.trim()
        val cleanUser = username.trim().ifBlank { "root" }
        _uiState.update { it.copy(setupForm = it.setupForm.copy(isVerifying = true, errorMessage = null)) }

        viewModelScope.launch {
            val result = detector.verifyMinerWithCredentials(
                ip = cleanIp,
                username = cleanUser,
                password = pass.ifBlank { "root" }
            )

            if (result.isConfirmed) {
                val profile = MinerProfile(
                    workerName = result.workerName.orEmpty(),
                    macAddress = result.macAddress.orEmpty(),
                    displayName = result.modelName ?: "Antminer S19k Pro",
                    nickname = nickname,
                    location = location,
                    host = cleanIp,
                    username = cleanUser,
                    password = result.effectivePassword,
                    cachedModel = result.modelName
                )
                profileManager.saveOrUpdateProfile(profile)
                val persisted = profileManager.getActiveProfile() ?: profile
                repository.setActiveProfile(persisted)
                repository.refreshDashboardData()
                _uiState.update {
                    it.copy(
                        activeProfile = persisted,
                        setupForm = it.setupForm.copy(isVerifying = false),
                        selectedScannerMiner = null,
                        isLocalSetupOpen = false,
                        selectedTab = DashboardTab.HOME,
                        currentLogText = null,
                        historyLogText = null,
                        dailyAnalysisState = DailyAnalysisUiState(loadedForMinerId = persisted.id)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        setupForm = it.setupForm.copy(
                            isVerifying = false,
                            errorMessage = "Authentication failed for ${miner.ip}. Check credentials."
                        )
                    )
                }
            }
        }
    }

    fun findSavedProfileForMiner(miner: DiscoveredMiner): MinerProfile? {
        val active = _uiState.value.activeProfile
        val saved = _uiState.value.savedProfiles.let { list ->
            if (active != null && list.none { it.id == active.id }) {
                list + active
            } else {
                list
            }
        }
        val targetIp = miner.ip.trim()
        val targetWorker = miner.workerName?.trim()?.lowercase() ?: ""
        val targetMac = miner.macAddress?.trim()?.lowercase() ?: ""

        return saved.firstOrNull { profile ->
            (targetWorker.isNotBlank() && profile.workerName.isNotBlank() && profile.workerName.trim().lowercase() == targetWorker) ||
            (targetMac.isNotBlank() && profile.macAddress.isNotBlank() && profile.macAddress.trim().lowercase() == targetMac) ||
            profile.host.trim() == targetIp
        }
    }

    fun openDiscoveredMiner(profile: MinerProfile, discoveredIp: String) {
        val cleanIp = discoveredIp.trim()
        val targetProfile = if (cleanIp.isNotBlank() && profile.host != cleanIp) {
            val updated = profile.copy(host = cleanIp)
            profileManager.saveOrUpdateProfile(updated)
            updated
        } else {
            profile
        }
        selectSavedProfile(targetProfile)
    }

    fun startNetworkScan() {
        if (!isWifiConnected()) {
            _uiState.update {
                it.copy(
                    isWifiConnected = false,
                    scannerState = it.scannerState.copy(
                        isScanning = false,
                        activeIp = "Wi-Fi connection required for local miner scan"
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isWifiConnected = true,
                batchConnectProgress = 0f,
                batchConnectMessage = null
            )
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            networkScanner.scanSubnet(context = getApplication()).collect { progress ->
                _uiState.update { it.copy(scannerState = progress) }
            }
        }
    }

    fun stopNetworkScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(scannerState = it.scannerState.copy(isScanning = false))
        }
    }

    /**
     * "Find moved miners" (v0.5.4) — the DHCP-recovery scan. Runs ONLY
     * when the user taps the button (setup Scan area or the dashboard's
     * couldn't-reach card). There is deliberately no other caller: no
     * Wi-Fi hook, no timer, no relay cycle.
     *
     * Scans the current Wi-Fi subnet with the existing [NetworkScanner],
     * verifies every responding Antminer, and lets [MovedMinerScan]
     * decide: a known worker at a new address moves its saved profile
     * there; a fleet-only worker is adopted like auto-connect; unknown
     * or unverifiable miners are counted and ignored — never saved.
     * Targets are the known miners NOT already verified live at their
     * stored address; when there is nothing to look for, no scan runs.
     */
    fun findMovedMiners() {
        if (movedScanJob?.isActive == true) return

        val profiles = profileManager.savedProfiles.value
        val snapshots = _uiState.value.remoteMiners
        // saveOrUpdateProfile moves the profile manager's active pointer
        // to whatever it saves; remember the user's miner so an address
        // update for a DIFFERENT miner never steals the selection.
        val previousActiveId = profileManager.getActiveProfile()?.id
        val liveIdentities = mergedMiners.value
            .filter { it.hasLiveLocalData || it.localHashrateTh != null }
            .mapNotNull { entry ->
                entry.localProfile?.workerName?.takeIf { w -> w.isNotBlank() }
                    ?: entry.remoteSnapshot?.let { MinerMerge.workerIdentity(it) }
            }
            .toSet()
        val targets = MovedMinerScan.buildTargets(profiles, snapshots, liveIdentities)

        if (targets.isEmpty()) {
            val anyKnown = profiles.any { it.workerName.isNotBlank() } ||
                snapshots.any { MinerMerge.workerIdentity(it) != null }
            _uiState.update {
                it.copy(
                    movedMinerScanLines = listOf(
                        if (anyKnown) {
                            "All known miners are already reachable."
                        } else {
                            "No known miners to look for — add a miner first."
                        }
                    )
                )
            }
            return
        }

        if (!isWifiConnected()) {
            _uiState.update {
                it.copy(
                    isWifiConnected = false,
                    movedMinerScanLines = listOf(
                        "Connect to the miners' Wi-Fi first, then tap Find moved miners again."
                    )
                )
            }
            return
        }

        movedScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWifiConnected = true,
                    isFindingMovedMiners = true,
                    movedMinerScanProgress = "Scanning this Wi-Fi for Antminers…",
                    movedMinerScanLines = emptyList()
                )
            }
            try {
                var discovered: List<DiscoveredMiner> = emptyList()
                networkScanner.scanSubnet(context = getApplication()).collect { progress ->
                    _uiState.update {
                        it.copy(
                            movedMinerScanProgress =
                                "Scanning ${progress.scannedCount}/${progress.totalCount} addresses…"
                        )
                    }
                    if (!progress.isScanning) discovered = progress.discoveredMiners
                }

                // Credentials to try per responder: the default first
                // (every miner this app manages uses it unless a saved
                // profile says otherwise), then each target profile's
                // own saved set — bounded and never guessed beyond that.
                val credentialAttempts = buildList {
                    add(AutoConnect.DEFAULT_USERNAME to AutoConnect.DEFAULT_PASSWORD)
                    targets.mapNotNull { it.profile }.forEach { profile ->
                        add(
                            profile.username.ifBlank { AutoConnect.DEFAULT_USERNAME } to
                                profile.password.ifBlank { AutoConnect.DEFAULT_PASSWORD }
                        )
                    }
                }.distinct()

                val lines = mutableListOf<String>()
                val resolvedIdentities = mutableSetOf<String>()
                var unknownCount = 0
                var unverifiedCount = 0
                var changedAny = false
                var activeProfileMoved = false

                for (miner in discovered) {
                    var verification: com.example.antminerconnectiontest.data.scanner.MinerVerificationResult? = null
                    var usedUsername = AutoConnect.DEFAULT_USERNAME
                    for ((user, pass) in credentialAttempts) {
                        val result = detector.verifyMinerWithCredentials(miner.ip, user, pass)
                        if (result.isConfirmed) {
                            verification = result
                            usedUsername = user
                            break
                        }
                    }
                    val verified = verification
                    if (verified == null) {
                        // An Antminer signature answered but no known
                        // credential set could log in: identity unproven.
                        unverifiedCount++
                        continue
                    }

                    when (val decision = MovedMinerScan.decide(
                        targets,
                        MovedMinerScan.VerifiedResponder(miner.ip, verified.workerName)
                    )) {
                        is MovedMinerScan.Decision.UpdateProfile -> {
                            val profile = decision.target.profile!!
                            profileManager.saveOrUpdateProfile(
                                profile.copy(host = decision.newIp)
                            )
                            resolvedIdentities += decision.target.identity
                            changedAny = true
                            if (_uiState.value.activeProfile?.id == profile.id) {
                                activeProfileMoved = true
                            }
                            lines += "Found ${decision.target.identityRaw} at ${decision.newIp} — address updated"
                        }
                        is MovedMinerScan.Decision.AdoptFleet -> {
                            val target = decision.target
                            adoptAutoConnected(
                                AutoConnectCandidate(
                                    identity = target.identity,
                                    identityRaw = target.identityRaw,
                                    ip = decision.ip,
                                    savedProfile = null,
                                    snapshot = target.snapshot,
                                    sheetOwner = target.snapshot?.user?.trim().orEmpty()
                                ),
                                decision.ip,
                                usedUsername,
                                verified
                            )
                            resolvedIdentities += target.identity
                            changedAny = true
                            lines += "Found ${target.identityRaw} at ${decision.ip} — added to this phone"
                        }
                        is MovedMinerScan.Decision.AlreadyCorrect -> {
                            resolvedIdentities += decision.target.identity
                            lines += "${decision.target.identityRaw} is already at ${decision.target.storedIp} — no change needed"
                        }
                        is MovedMinerScan.Decision.IgnoredUnknown -> unknownCount++
                        MovedMinerScan.Decision.IgnoredUnverified -> unverifiedCount++
                    }
                }

                for (target in targets) {
                    if (target.identity !in resolvedIdentities) {
                        lines += "${target.identityRaw} not found on this Wi-Fi"
                    }
                }
                if (unknownCount > 0) {
                    lines += "$unknownCount unknown miner${if (unknownCount == 1) "" else "s"} ignored"
                }
                if (unverifiedCount > 0) {
                    lines += "$unverifiedCount miner${if (unverifiedCount == 1) "" else "s"} couldn't be verified and ${if (unverifiedCount == 1) "was" else "were"} ignored"
                }
                if (lines.isEmpty()) {
                    lines += "No Antminers answered on this Wi-Fi."
                }

                _uiState.update { it.copy(movedMinerScanLines = lines) }

                if (changedAny) {
                    // Refresh so a found miner flips LIVE, and sync so
                    // the sheet learns the new address on the next
                    // relay cycle.
                    if (activeProfileMoved) {
                        profileManager.getActiveProfile()?.let { selectSavedProfile(it) }
                    } else {
                        if (previousActiveId != null &&
                            profileManager.getActiveProfile()?.id != previousActiveId
                        ) {
                            profileManager.selectProfile(previousActiveId)
                        }
                        viewModelScope.launch { repository.refreshDashboardData() }
                    }
                    syncCloudNow()
                }
            } finally {
                _uiState.update {
                    it.copy(isFindingMovedMiners = false, movedMinerScanProgress = null)
                }
            }
        }
    }

    fun connectAllDiscoveredMiners(username: String = "root", pass: String = "root") {
        if (_uiState.value.isBatchConnecting) return
        val discovered = _uiState.value.scannerState.discoveredMiners
        if (discovered.isEmpty()) return

        // Auto-heal any existing discovered miners if their IP changed on DHCP
        for (miner in discovered) {
            val existing = findSavedProfileForMiner(miner)
            val cleanIp = miner.ip.trim()
            if (existing != null && cleanIp.isNotBlank() && existing.host != cleanIp) {
                profileManager.saveOrUpdateProfile(existing.copy(host = cleanIp))
            }
        }

        val unaddedMiners = discovered.filter { findSavedProfileForMiner(it) == null }
        if (unaddedMiners.isEmpty()) {
            // Everything the scan found is already saved: the add flow is
            // complete, so land on Home instead of leaving the user on the
            // setup screen to open a miner by hand.
            profileManager.getActiveProfile()?.let { landOnHomeWith(it) }
            return
        }

        val cleanUser = username.trim().ifBlank { "root" }
        val cleanPass = pass.ifBlank { "root" }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isBatchConnecting = true,
                    batchConnectProgress = 0.05f,
                    batchConnectMessage = "Connecting to ${unaddedMiners.size} new miners..."
                )
            }

            val newlyVerifiedProfiles = mutableListOf<MinerProfile>()
            val total = unaddedMiners.size
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(10)

            val jobs = unaddedMiners.map { miner ->
                async {
                    semaphore.withPermit {
                        try {
                            val result = detector.verifyMinerWithCredentials(
                                ip = miner.ip.trim(),
                                username = cleanUser,
                                password = cleanPass
                            )
                            if (result.isConfirmed) {
                                val profile = MinerProfile(
                                    workerName = result.workerName.orEmpty(),
                                    macAddress = result.macAddress.orEmpty(),
                                    displayName = result.modelName ?: "Antminer S19k Pro",
                                    nickname = "",
                                    location = "Default Location",
                                    host = miner.ip.trim(),
                                    username = cleanUser,
                                    password = result.effectivePassword,
                                    cachedModel = result.modelName
                                )
                                synchronized(newlyVerifiedProfiles) {
                                    newlyVerifiedProfiles.add(profile)
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        } finally {
                            val done = completed.incrementAndGet()
                            val progress = 0.05f + ((done.toFloat() / total) * 0.90f)
                            _uiState.update {
                                it.copy(
                                    batchConnectProgress = progress,
                                    batchConnectMessage = "Verified $done/$total miners..."
                                )
                            }
                        }
                    }
                }
            }

            jobs.awaitAll()

            for (p in newlyVerifiedProfiles) {
                profileManager.saveOrUpdateProfile(p)
            }

            _uiState.update {
                it.copy(
                    isBatchConnecting = false,
                    batchConnectProgress = 1.0f,
                    batchConnectMessage = if (newlyVerifiedProfiles.isNotEmpty()) {
                        "Successfully added ${newlyVerifiedProfiles.size} miners!"
                    } else {
                        "Authentication failed for discovered miners."
                    }
                )
            }

            // Completing the add flow lands on HOME with a miner active.
            // This previously only updated the profile manager's internal
            // active id — uiState.activeProfile (which the navigation
            // routes on) stayed null, so the user remained on the setup /
            // remote screens and had to open a miner by hand to reach
            // Home. saveOrUpdateProfile leaves the last-saved profile as
            // the manager's active one, which is the machine to land on.
            if (newlyVerifiedProfiles.isNotEmpty()) {
                profileManager.getActiveProfile()?.let { landOnHomeWith(it) }
            }
        }
    }

    fun isWifiConnected(): Boolean {
        return try {
            val app = getApplication<android.app.Application>() ?: return false
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * The tab-source decision for the machine the tabs are about
     * (v0.5.1, see [TabSource]) — resolved from the merged fleet, so a
     * machine selected through its saved local profile is recognised
     * as the same machine as its cloud snapshot.
     */
    private fun currentTabSource(): TabDataSource {
        val state = _uiState.value
        val entries = mergedMiners.value
        val entry = if (state.selectedRemoteMiner != null) {
            entries.firstOrNull { it.remoteSnapshot?.id == state.selectedRemoteMiner?.id }
        } else {
            state.activeProfile?.let { profile ->
                entries.firstOrNull { it.localProfile?.id == profile.id }
            }
        }
        val active = state.activeProfile
        val liveOnline = active != null &&
            entry?.localProfile?.id == active.id &&
            state.dashboardData?.profileId == active.id &&
            state.dashboardData?.isOnline == true
        return TabSource.decide(
            liveSessionOnline = liveOnline,
            hasSnapshot = entry?.remoteSnapshot != null || state.selectedRemoteMiner != null
        )
    }

    /**
     * True when the viewed machine is shown from its cloud snapshot
     * (v0.5.1) and the phone is not on Wi-Fi: a direct dial of the
     * miner's LAN IP cannot reach it, so refresh paths skip the dial
     * and refresh the cloud side only. On Wi-Fi the dial proceeds as
     * before — and a failure still leaves the snapshot on the tabs.
     */
    private fun snapshotShownOffWifi(): Boolean =
        !isWifiConnected() && currentTabSource() == TabDataSource.CLOUD

    fun manualRefresh() {
        // Attempt the local refresh — with ONE exception (v0.5.1): when
        // the viewed machine is shown from its cloud snapshot and the
        // phone is not on Wi-Fi, dialling its LAN IP is pointless, so
        // only the cloud side refreshes. Otherwise failures are
        // published as an error state by the repository; silently
        // skipping the fetch on a negative Wi-Fi check made the refresh
        // button look broken.
        viewModelScope.launch {
            if (!snapshotShownOffWifi()) {
                repository.refreshDashboardData()
            }
            syncCloudNow()
        }
    }

    fun disconnect() {
        repository.disconnect()
        _uiState.update {
            it.copy(
                selectedTab = DashboardTab.HOME,
                isLocalSetupOpen = false,
                setupForm = SetupFormState(),
                currentLogText = null,
                historyLogText = null,
                dailyAnalysisState = DailyAnalysisUiState()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = getApplication<Application>()?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
            wifiRefreshCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Throwable) {}
    }
}
