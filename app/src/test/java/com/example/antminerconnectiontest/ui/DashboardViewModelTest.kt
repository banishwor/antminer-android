package com.example.antminerconnectiontest.ui

import com.example.antminerconnectiontest.domain.model.MinerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialSetupState_defaultsToManualAndPrepopulatesRoot() {
        val viewModel = DashboardViewModel()
        val state = viewModel.uiState.value

        assertNull(state.activeProfile)
        assertEquals(SetupTab.MANUAL, state.setupTab)
        assertEquals("192.168.1.2", state.setupForm.ip)
        assertEquals("root", state.setupForm.username)
        assertEquals("root", state.setupForm.password)
    }

    @Test
    fun tabNavigation_switchesCorrectly() {
        val viewModel = DashboardViewModel()
        viewModel.selectDashboardTab(DashboardTab.HARDWARE)
        assertEquals(DashboardTab.HARDWARE, viewModel.uiState.value.selectedTab)

        viewModel.selectDashboardTab(DashboardTab.POOLS)
        assertEquals(DashboardTab.POOLS, viewModel.uiState.value.selectedTab)

        viewModel.selectDashboardTab(DashboardTab.DEVICE)
        assertEquals(DashboardTab.DEVICE, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun formState_updatesOnInput() {
        val viewModel = DashboardViewModel()

        viewModel.onSetupIpChanged("192.168.1.50")
        assertEquals("192.168.1.50", viewModel.uiState.value.setupForm.ip)

        viewModel.onSetupPasswordChanged("secret")
        assertEquals("secret", viewModel.uiState.value.setupForm.password)

        viewModel.toggleSetupPasswordVisibility()
        assertTrue(viewModel.uiState.value.setupForm.isPasswordVisible)
    }

    @Test
    fun updateCloudConfig_updatesStateProperly() {
        val viewModel = DashboardViewModel()

        viewModel.updateCloudConfig(
            webhookUrl = "https://script.google.com/test",
            userName = "Tompokchao",
            relayEnabled = true
        )

        val config = viewModel.uiState.value.cloudConfig
        assertEquals("https://script.google.com/test", config.webhookUrl)
        assertEquals("Tompokchao", config.userName)
        assertTrue(config.isSyncEnabled)
        assertTrue(config.isAutoUploadEnabled)
    }

    @Test
    fun relaySwitch_derivesUploadAndBackgroundFromTheOneSwitch() {
        val viewModel = DashboardViewModel()

        viewModel.updateCloudConfig(
            webhookUrl = "https://script.google.com/test",
            userName = "Master",
            relayEnabled = true
        )
        var config = viewModel.uiState.value.cloudConfig
        assertTrue(config.isSyncEnabled)
        assertTrue(config.isAutoUploadEnabled)
        assertTrue(config.isBackgroundServiceEnabled)
        assertTrue(config.isDownloadRemoteEnabled)

        viewModel.updateCloudConfig(
            webhookUrl = "https://script.google.com/test",
            userName = "Master",
            relayEnabled = false
        )
        config = viewModel.uiState.value.cloudConfig
        assertFalse(config.isSyncEnabled)
        assertFalse(config.isAutoUploadEnabled)
        assertFalse(config.isBackgroundServiceEnabled)
        // Viewing remote machines is never gated by the Relay switch.
        assertTrue(config.isDownloadRemoteEnabled)
    }

    @Test
    fun turningRelayOff_keepsRemoteViewingState() {
        val viewModel = DashboardViewModel()
        val remote = com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot(
            user = "Luckyson",
            id = "athok_1",
            nickname = "Athokpam #1",
            ip = "192.168.1.2",
            hashrate5s = 122.5,
            avgHashrate = 121.9,
            maxTemp = 62,
            maxFan = 6500,
            status = "Online",
            lastUpdated = "2026-08-30"
        )
        viewModel.selectRemoteMiner(remote)
        assertEquals(remote, viewModel.uiState.value.selectedRemoteMiner)

        viewModel.updateCloudConfig(
            webhookUrl = "https://script.google.com/test",
            userName = "Master",
            relayEnabled = false
        )

        // Relay OFF stops uploads only — the remote miner being viewed
        // must not be kicked out from under the user.
        assertEquals(remote, viewModel.uiState.value.selectedRemoteMiner)
    }

    @Test
    fun remoteMinerSelection_togglesRemoteViewMode() {
        val viewModel = DashboardViewModel()

        val remote = com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot(
            user = "Luckyson",
            id = "athok_1",
            nickname = "Athokpam #1",
            ip = "192.168.1.2",
            hashrate5s = 122.5,
            avgHashrate = 121.9,
            maxTemp = 62,
            maxFan = 6500,
            status = "Online",
            lastUpdated = "2026-08-30"
        )

        viewModel.selectRemoteMiner(remote)
        assertEquals(remote, viewModel.uiState.value.selectedRemoteMiner)
        assertEquals(DashboardTab.HOME, viewModel.uiState.value.selectedTab)

        viewModel.clearRemoteMinerView()
        assertNull(viewModel.uiState.value.selectedRemoteMiner)
    }

    @Test
    fun selectSavedProfile_resetsAndIsolatesDailyAnalysisAndLogs() {
        val viewModel = DashboardViewModel()
        val profileA = MinerProfile(id = "miner_A", host = "192.168.1.10", displayName = "Miner A")
        val profileB = MinerProfile(id = "miner_B", host = "192.168.1.20", displayName = "Miner B")

        viewModel.selectSavedProfile(profileA)
        assertEquals("miner_A", viewModel.uiState.value.activeProfile?.id)
        assertEquals("miner_A", viewModel.uiState.value.dailyAnalysisState.loadedForMinerId)

        // Switch to Profile B
        viewModel.selectSavedProfile(profileB)
        assertEquals("miner_B", viewModel.uiState.value.activeProfile?.id)
        assertEquals("miner_B", viewModel.uiState.value.dailyAnalysisState.loadedForMinerId)
        assertTrue(viewModel.uiState.value.dailyAnalysisState.availableDates.isEmpty())
        assertNull(viewModel.uiState.value.dailyAnalysisState.selectedAnalysisDetail)
        assertNull(viewModel.uiState.value.currentLogText)
        assertNull(viewModel.uiState.value.historyLogText)
    }

    @Test
    fun findSavedProfileForMiner_matchesByWorkerMacOrIp() {
        val viewModel = DashboardViewModel()
        val profile1 = MinerProfile(
            id = "m1",
            workerName = "banishwor.001",
            macAddress = "EC:84:39:D0:5E:1B",
            host = "192.168.1.10"
        )
        val profile2 = MinerProfile(
            id = "m2",
            workerName = "banishwor.002",
            macAddress = "AA:BB:CC:DD:EE:FF",
            host = "192.168.1.20"
        )
        viewModel.selectSavedProfile(profile1)

        // Matching by worker name
        val discoveredByWorker = com.example.antminerconnectiontest.data.scanner.DiscoveredMiner(
            ip = "192.168.1.55", // different IP (DHCP changed)
            workerName = "banishwor.001",
            status = com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.CONFIRMED_ANTMINER
        )
        val matched1 = viewModel.findSavedProfileForMiner(discoveredByWorker)
        assertNotNull(matched1)
        assertEquals("m1", matched1?.id)

        // Matching by IP
        val discoveredByIp = com.example.antminerconnectiontest.data.scanner.DiscoveredMiner(
            ip = "192.168.1.10",
            status = com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.CONFIRMED_ANTMINER
        )
        val matched2 = viewModel.findSavedProfileForMiner(discoveredByIp)
        assertNotNull(matched2)
        assertEquals("m1", matched2?.id)

        // Unadded miner
        val discoveredNew = com.example.antminerconnectiontest.data.scanner.DiscoveredMiner(
            ip = "192.168.1.99",
            workerName = "banishwor.099",
            status = com.example.antminerconnectiontest.data.scanner.DiscoveryStatus.CONFIRMED_ANTMINER
        )
        val matchedNew = viewModel.findSavedProfileForMiner(discoveredNew)
        assertNull(matchedNew)
    }

    @Test
    fun startNetworkScan_abortsOnMobileNetwork() {
        val viewModel = DashboardViewModel()
        // In unit test environment, Application does not have real Wi-Fi network capability
        assertFalse(viewModel.isWifiConnected())

        viewModel.startNetworkScan()
        assertFalse(viewModel.uiState.value.scannerState.isScanning)
        assertTrue(viewModel.uiState.value.scannerState.activeIp.contains("Wi-Fi"))
    }

    @Test
    fun startChoice_defaultsToNull_untilUserChooses() {
        val viewModel = DashboardViewModel()
        assertNull(viewModel.uiState.value.startChoice)

        viewModel.chooseRemoteStart()
        assertEquals(StartChoice.REMOTE, viewModel.uiState.value.startChoice)
    }

    @Test
    fun chooseScanStart_setsChoiceAndAttemptsScan() {
        val viewModel = DashboardViewModel()
        viewModel.chooseScanStart()

        assertEquals(StartChoice.SCAN, viewModel.uiState.value.startChoice)
        // The explicit tap DOES start the scan flow; in the test
        // environment there is no Wi-Fi, so it lands on the Wi-Fi notice
        // instead of scanning — which proves the attempt happened.
        assertTrue(viewModel.uiState.value.scannerState.activeIp.contains("Wi-Fi"))
        assertFalse(viewModel.uiState.value.scannerState.isScanning)
    }

    @Test
    fun selectSetupTab_scanner_doesNotAutoStartScan() {
        val viewModel = DashboardViewModel()
        // Regression: navigating to the scanner tab used to start a scan
        // as a side effect. Scans now start only from explicit actions.
        viewModel.selectSetupTab(SetupTab.SCANNER)

        assertEquals(SetupTab.SCANNER, viewModel.uiState.value.setupTab)
        assertFalse(viewModel.uiState.value.scannerState.isScanning)
        assertEquals("", viewModel.uiState.value.scannerState.activeIp)
    }

    @Test
    fun selectRemoteMiner_sameMachineAsLocal_activatesLocalAndKeepsRemoteSelection() {
        val manager = com.example.antminerconnectiontest.data.repository.MinerProfileManager()
        val local = MinerProfile(id = "p1", workerName = "banishwor.001", host = "192.168.1.10")
        manager.saveOrUpdateProfile(local)
        val viewModel = DashboardViewModel(profileManager = manager)
        testDispatcher.scheduler.runCurrent()

        val remote = com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot(
            user = "banishwor",
            id = "banishwor.001",
            nickname = "banishwor.001 (Antminer S19k Pro)",
            ip = "192.168.1.10",
            hashrate5s = 110.0,
            avgHashrate = 109.0,
            maxTemp = 62,
            maxFan = 6000,
            status = "Online",
            lastUpdated = ""
        )
        viewModel.selectRemoteMiner(remote)

        // One machine: the local profile is activated (so live local data
        // can be fetched and win) AND the remote selection is kept (so the
        // snapshot shows while local data is unavailable).
        assertEquals("p1", viewModel.uiState.value.activeProfile?.id)
        assertEquals(remote, viewModel.uiState.value.selectedRemoteMiner)
    }

    @Test
    fun mergedMiners_sameMachineAppearsOnce() {
        val manager = com.example.antminerconnectiontest.data.repository.MinerProfileManager()
        val local = MinerProfile(id = "p1", workerName = "banishwor.001", host = "192.168.1.10")
        manager.saveOrUpdateProfile(local)
        val viewModel = DashboardViewModel(profileManager = manager)
        testDispatcher.scheduler.runCurrent()

        val remote = com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot(
            user = "banishwor",
            id = "banishwor.001",
            nickname = "banishwor.001 (Antminer S19k Pro)",
            ip = "192.168.1.10",
            hashrate5s = 110.0,
            avgHashrate = 109.0,
            maxTemp = 62,
            maxFan = 6000,
            status = "Online",
            lastUpdated = ""
        )
        viewModel.selectRemoteMiner(remote)
        testDispatcher.scheduler.runCurrent()

        val entries = viewModel.mergedMiners.value
        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
        assertEquals("p1", entries[0].localProfile?.id)
        assertEquals("banishwor.001", entries[0].remoteSnapshot?.id)
    }

    @Test
    fun openDiscoveredMiner_updatesHostIfIpChanged() {
        val viewModel = DashboardViewModel()
        val profile = MinerProfile(
            id = "miner_1",
            workerName = "banishwor.001",
            host = "192.168.1.10"
        )
        // Discovered at new DHCP IP
        viewModel.openDiscoveredMiner(profile, "192.168.1.55")

        assertEquals("192.168.1.55", viewModel.uiState.value.activeProfile?.host)
        assertEquals("miner_1", viewModel.uiState.value.activeProfile?.id)
    }
}
