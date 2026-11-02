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
    val selectedScannerMiner: DiscoveredMiner? = null,
    val scannerUsernameInput: String = "root",
    val scannerPasswordInput: String = "root",
    val isScannerPasswordVisible: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val selectedLogsTab: LogsTab = LogsTab.CURRENT,
    val currentLogText: String? = null,
    val historyLogText: String? = null,
    val isLoadingLog: Boolean = false,
    val logErrorMessage: String? = null,
    val lastLogUpdated: Long? = null,
    val dailyAnalysisState: DailyAnalysisUiState = DailyAnalysisUiState(),
    val cloudConfig: CloudSyncConfig = CloudSyncConfig(),
    val remoteMiners: List<RemoteMinerSnapshot> = emptyList(),
    val selectedRemoteMiner: RemoteMinerSnapshot? = null,
    val isTestingCloud: Boolean = false,
    val cloudTestMessage: String? = null,
    val isCloudConnected: Boolean = false,
    val lastCloudSyncTimestamp: Long? = null,
    val isBatchConnecting: Boolean = false,
    val batchConnectProgress: Float = 0f,
    val batchConnectMessage: String? = null,
    val isWifiConnected: Boolean = true
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

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleThemeMode() {
        val next = when (_uiState.value.themeMode) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(next)
    }

    private var scanJob: Job? = null
    private var cloudSyncJob: Job? = null
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
            _uiState.update { it.copy(cloudConfig = savedCloudConfig) }
            if (savedCloudConfig.isBackgroundServiceEnabled && savedCloudConfig.isSyncEnabled) {
                try {
                    com.example.antminerconnectiontest.service.MinerRelayService.start(getApplication())
                } catch (_: Throwable) {}
            }
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
    }

    private fun startCloudSyncLoop() {
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            while (isActive) {
                val cfg = _uiState.value.cloudConfig
                if (cfg.isSyncEnabled && cfg.webhookUrl.isNotBlank() && cfg.userName.isNotBlank()) {
                    performCloudSyncCycle(cfg)
                }
                delay(20000L) // 20s interval
            }
        }
    }

    private suspend fun performCloudSyncCycle(cfg: CloudSyncConfig) {
        // 1. Auto-upload local miners if enabled and connected to Wi-Fi
        if (cfg.isAutoUploadEnabled && isWifiConnected()) {
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

                if (rate5s > 0.0 || avgRate > 0.0) {
                    // Only upload telemetry that was actually measured. For
                    // non-active miners we only know the hashrate (race
                    // poller), so temp/fan are unknown: report 0, which
                    // consumers render as unknown. This used to fabricate
                    // 60 °C / 6000 RPM for every non-active miner.
                    val maxTemp = if (isActive && isLocalOnline) currentData?.stats?.maxChipTemp ?: 0 else 0
                    val maxFan = if (isActive && isLocalOnline) currentData?.stats?.maxFanRpm ?: 0 else 0
                    RemoteMinerSnapshot(
                        user = cfg.userName.trim(),
                        id = profile.id,
                        nickname = profile.effectiveName,
                        ip = profile.host,
                        hashrate5s = rate5s,
                        avgHashrate = avgRate,
                        maxTemp = maxTemp,
                        maxFan = maxFan,
                        status = "Online",
                        lastUpdated = ""
                    )
                } else {
                    null
                }
            }

            if (localSnapshots.isNotEmpty()) {
                cloudSyncManager.uploadLocalMiners(cfg.webhookUrl, cfg.userName, localSnapshots)
            }
        }

        // 2. Fetch permitted remote miners (only if isDownloadRemoteEnabled is true)
        if (cfg.isDownloadRemoteEnabled) {
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
        } else {
            _uiState.update {
                it.copy(remoteMiners = emptyList(), isCloudConnected = true)
            }
        }
    }

    fun updateCloudConfig(
        webhookUrl: String,
        userName: String,
        isSyncEnabled: Boolean,
        isAutoUploadEnabled: Boolean,
        isDownloadRemoteEnabled: Boolean = true,
        isBackgroundServiceEnabled: Boolean = false
    ) {
        val effectiveUrl = webhookUrl.trim().ifBlank { _uiState.value.cloudConfig.webhookUrl.ifBlank { CloudSyncConfig.DEFAULT_WEBHOOK_URL } }
        val newCfg = CloudSyncConfig(
            webhookUrl = effectiveUrl,
            userName = userName.trim(),
            isSyncEnabled = isSyncEnabled,
            isAutoUploadEnabled = isAutoUploadEnabled && isSyncEnabled,
            isDownloadRemoteEnabled = isDownloadRemoteEnabled && isSyncEnabled,
            isBackgroundServiceEnabled = isBackgroundServiceEnabled && isSyncEnabled
        )
        _uiState.update {
            it.copy(
                cloudConfig = newCfg,
                cloudTestMessage = if (isSyncEnabled) "✅ Configuration saved successfully!" else "Cloud sync disabled (Offline Mode)",
                remoteMiners = if (isSyncEnabled) it.remoteMiners else emptyList(),
                selectedRemoteMiner = if (isSyncEnabled) it.selectedRemoteMiner else null,
                isCloudConnected = if (isSyncEnabled) it.isCloudConnected else false
            )
        }
        storageManager?.saveCloudConfig(newCfg)

        if (isBackgroundServiceEnabled && isSyncEnabled) {
            try {
                com.example.antminerconnectiontest.service.MinerRelayService.start(getApplication())
            } catch (_: Throwable) {}
        } else {
            try {
                com.example.antminerconnectiontest.service.MinerRelayService.stop(getApplication())
            } catch (_: Throwable) {}
        }

        if (isSyncEnabled) {
            syncCloudNow()
        }
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
        if (!cfg.isSyncEnabled) {
            _uiState.update { it.copy(remoteMiners = emptyList(), selectedRemoteMiner = null, isCloudConnected = false) }
            return
        }
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
        _uiState.update { it.copy(setupTab = tab) }
        if (tab == SetupTab.SCANNER && !_uiState.value.scannerState.isScanning && _uiState.value.scannerState.scannedCount == 0) {
            startNetworkScan()
        }
    }

    fun toggleProfileSwitcher(isOpen: Boolean? = null) {
        _uiState.update {
            it.copy(isProfileSwitcherOpen = isOpen ?: !it.isProfileSwitcherOpen)
        }
    }

    fun selectRemoteMiner(remote: RemoteMinerSnapshot) {
        _uiState.update {
            it.copy(
                selectedRemoteMiner = remote,
                selectedTab = DashboardTab.HOME
            )
        }
    }

    fun clearRemoteMinerView() {
        _uiState.update {
            it.copy(selectedRemoteMiner = null)
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
     */
    fun refreshIfStale() {
        if (_uiState.value.activeProfile == null) return
        val data = _uiState.value.dashboardData
        val isStale = data == null ||
            _uiState.value.dashboardError != null ||
            System.currentTimeMillis() - data.lastUpdated > 30_000L
        if (isStale) {
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
        if (unaddedMiners.isEmpty()) return

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

            val currentActive = profileManager.getActiveProfile()
            if (currentActive == null && newlyVerifiedProfiles.isNotEmpty()) {
                val firstProfile = newlyVerifiedProfiles.first()
                selectSavedProfile(firstProfile)
            }

            _uiState.update {
                it.copy(
                    isBatchConnecting = false,
                    batchConnectProgress = 1.0f,
                    batchConnectMessage = if (newlyVerifiedProfiles.isNotEmpty()) {
                        "Successfully added ${newlyVerifiedProfiles.size} miners!"
                    } else {
                        "Authentication failed for discovered miners."
                    },
                    selectedTab = if (profileManager.getActiveProfile() != null) DashboardTab.HOME else it.selectedTab
                )
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

    fun manualRefresh() {
        // Always attempt the refresh. Failures are published as an error
        // state by the repository; silently skipping the fetch on a negative
        // Wi-Fi check made the refresh button look broken.
        viewModelScope.launch {
            repository.refreshDashboardData()
            syncCloudNow()
        }
    }

    fun disconnect() {
        repository.disconnect()
        _uiState.update {
            it.copy(
                selectedTab = DashboardTab.HOME,
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
