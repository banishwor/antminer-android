package com.example.antminerconnectiontest.ui

import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.NeedsPasswordMachine
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
    fun noStartChoice_freshStateIsTheShell_noSetupOpen() {
        // v0.1.9: there is no first-open choice anymore — a fresh
        // ViewModel is already "in the shell": Home tab, setup closed.
        val viewModel = DashboardViewModel()
        val state = viewModel.uiState.value
        assertEquals(DashboardTab.HOME, state.selectedTab)
        assertFalse(state.isLocalSetupOpen)
        assertTrue(state.autoConnectNeedsPassword.isEmpty())
    }

    @Test
    fun localSetup_opensAndClosesOnlyByExplicitAction() {
        val viewModel = DashboardViewModel()
        viewModel.openLocalSetup()
        assertTrue(viewModel.uiState.value.isLocalSetupOpen)
        viewModel.closeLocalSetup()
        assertFalse(viewModel.uiState.value.isLocalSetupOpen)
    }

    @Test
    fun needsPasswordEntry_prefillsIpAndOpensManualSetup() {
        val viewModel = DashboardViewModel()
        viewModel.openNeedsPasswordEntry(
            NeedsPasswordMachine(
                identity = "tompokchao.001",
                displayName = "tompokchao.001",
                ip = "192.168.1.20",
                ownerUser = "Tompokchao"
            )
        )
        val state = viewModel.uiState.value
        assertTrue(state.isLocalSetupOpen)
        assertEquals(SetupTab.MANUAL, state.setupTab)
        assertEquals("192.168.1.20", state.setupForm.ip)
        assertEquals("", state.setupForm.password)
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

    // ── v0.2.1 regression: the hero must follow the fleet, not the tap ──
    //
    // The bug: the remote selection was a snapshot copy frozen at
    // selection time. Cloud sync refreshed the fleet list, but the hero
    // kept showing the old copy ("Updated 11m ago · signal lost",
    // hashrate "--", OFFLINE) until the user tapped the machine's row
    // again. refreshSelectedSnapshot re-derives the selection from the
    // merged fleet — the same state the list renders.

    private fun baniSnapshot(hashrate: Double, status: String, updated: String) =
        com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot(
            user = "Tompokchao",
            id = "bani009.201",
            nickname = "bani009.201",
            ip = "192.168.1.30",
            hashrate5s = hashrate,
            avgHashrate = hashrate,
            maxTemp = 62,
            maxFan = 6000,
            status = status,
            lastUpdated = updated
        )

    @Test
    fun refreshSelectedSnapshot_fresherFleetCopy_updatesSelectionWithoutReselect() {
        val viewModel = DashboardViewModel()
        // Selection-time copy: stale, miner shown offline with no rate.
        val stale = baniSnapshot(hashrate = 0.0, status = "Offline", updated = "2026-11-02T16:59:00")
        viewModel.selectRemoteMiner(stale)
        assertEquals(stale, viewModel.uiState.value.selectedRemoteMiner)

        // A sync cycle lands a fresher copy of the SAME machine in the
        // merged fleet (what the fleet list renders).
        val fresh = baniSnapshot(hashrate = 122.8, status = "Online", updated = "2026-11-02T17:10:00")
        val entries = listOf(
            com.example.antminerconnectiontest.domain.model.MergedMinerEntry(
                key = "remote:bani009.201",
                remoteSnapshot = fresh
            )
        )
        viewModel.refreshSelectedSnapshot(entries)

        // The hero state (the stored selection the hero derives from)
        // now reflects the fresh values — no re-selection happened.
        val selected = viewModel.uiState.value.selectedRemoteMiner
        assertEquals(fresh, selected)
        assertEquals(122.8, selected!!.hashrate5s, 0.001)
        assertEquals("Online", selected.status)
        assertEquals("2026-11-02T17:10:00", selected.lastUpdated)
    }

    @Test
    fun refreshSelectedSnapshot_machineAbsentFromFleet_keepsLastKnownSelection() {
        val viewModel = DashboardViewModel()
        val stale = baniSnapshot(hashrate = 0.0, status = "Offline", updated = "2026-11-02T16:59:00")
        viewModel.selectRemoteMiner(stale)

        // The fleet no longer lists the machine: the selection keeps
        // its last known copy instead of being cleared or swapped.
        val other = baniSnapshot(hashrate = 50.0, status = "Online", updated = "2026-11-02T17:10:00")
            .copy(id = "bani009.202")
        viewModel.refreshSelectedSnapshot(
            listOf(
                com.example.antminerconnectiontest.domain.model.MergedMinerEntry(
                    key = "remote:bani009.202",
                    remoteSnapshot = other
                )
            )
        )
        assertEquals(stale, viewModel.uiState.value.selectedRemoteMiner)
    }

    @Test
    fun refreshSelectedSnapshot_sameSnapshot_leavesSelectionUntouched() {
        val viewModel = DashboardViewModel()
        val current = baniSnapshot(hashrate = 122.8, status = "Online", updated = "2026-11-02T17:10:00")
        viewModel.selectRemoteMiner(current)

        viewModel.refreshSelectedSnapshot(
            listOf(
                com.example.antminerconnectiontest.domain.model.MergedMinerEntry(
                    key = "remote:bani009.201",
                    remoteSnapshot = current
                )
            )
        )
        // No state churn when the fleet copy already equals the selection.
        assertSame(current, viewModel.uiState.value.selectedRemoteMiner)
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

    // ── v0.3.0: appearance style + relay-only mode ───────────────────

    @Test
    fun appStyle_defaultsToViolet_andCanSwitchToLiquidGlass() {
        val viewModel = DashboardViewModel()
        assertEquals(
            com.example.antminerconnectiontest.theme.AppStyle.VIOLET,
            viewModel.uiState.value.appStyle
        )

        viewModel.setAppStyle(com.example.antminerconnectiontest.theme.AppStyle.LIQUID_GLASS)
        assertEquals(
            com.example.antminerconnectiontest.theme.AppStyle.LIQUID_GLASS,
            viewModel.uiState.value.appStyle
        )

        viewModel.setAppStyle(com.example.antminerconnectiontest.theme.AppStyle.VIOLET)
        assertEquals(
            com.example.antminerconnectiontest.theme.AppStyle.VIOLET,
            viewModel.uiState.value.appStyle
        )
    }

    @Test
    fun relayOnlyMode_defaultsOff_andToggles() {
        val viewModel = DashboardViewModel()
        assertFalse(viewModel.uiState.value.relayOnlyMode)

        viewModel.setRelayOnlyMode(true)
        assertTrue(viewModel.uiState.value.relayOnlyMode)
        // Routing follows the flag: status screen only.
        assertEquals(
            MainRoute.RELAY_ONLY,
            MainShellRouting.route(
                isLocalSetupOpen = viewModel.uiState.value.isLocalSetupOpen,
                isRelayOnlyMode = viewModel.uiState.value.relayOnlyMode
            )
        )

        viewModel.setRelayOnlyMode(false)
        assertFalse(viewModel.uiState.value.relayOnlyMode)
        assertEquals(
            MainRoute.MAIN_SHELL,
            MainShellRouting.route(
                isLocalSetupOpen = viewModel.uiState.value.isLocalSetupOpen,
                isRelayOnlyMode = viewModel.uiState.value.relayOnlyMode
            )
        )
    }

    @Test
    fun relayOnlyMode_enablingClosesSetupFlow() {
        val viewModel = DashboardViewModel()
        viewModel.openLocalSetup()
        assertTrue(viewModel.uiState.value.isLocalSetupOpen)

        viewModel.setRelayOnlyMode(true)
        // The two top-level flags never fight: setup is closed.
        assertFalse(viewModel.uiState.value.isLocalSetupOpen)
        assertEquals(
            MainRoute.RELAY_ONLY,
            MainShellRouting.route(
                isLocalSetupOpen = viewModel.uiState.value.isLocalSetupOpen,
                isRelayOnlyMode = viewModel.uiState.value.relayOnlyMode
            )
        )
    }
}
