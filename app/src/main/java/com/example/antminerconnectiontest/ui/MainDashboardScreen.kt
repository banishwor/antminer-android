package com.example.antminerconnectiontest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.antminerconnectiontest.domain.model.HomeLoadState
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.PhoneNicknames
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import com.example.antminerconnectiontest.ui.components.MinerSwitcherBottomSheet
import com.example.antminerconnectiontest.ui.components.NicknameEditDialog
import com.example.antminerconnectiontest.ui.components.SelectedMachineHeader
import com.example.antminerconnectiontest.ui.components.localMinerDetailUi
import com.example.antminerconnectiontest.ui.components.toMinerDetailUi
import com.example.antminerconnectiontest.ui.device.DeviceScreen
import com.example.antminerconnectiontest.ui.hardware.HardwareScreen
import com.example.antminerconnectiontest.ui.home.HomeScreen
import com.example.antminerconnectiontest.ui.logs.LogsScreen
import com.example.antminerconnectiontest.ui.pools.PoolsScreen
import com.example.antminerconnectiontest.ui.relay.RelayMinerLine
import com.example.antminerconnectiontest.ui.relay.RelayMinerState
import com.example.antminerconnectiontest.ui.relay.RelayOnlyScreen
import com.example.antminerconnectiontest.ui.remote.RemoteHardwareTab
import com.example.antminerconnectiontest.ui.remote.RemoteLogsTab
import com.example.antminerconnectiontest.ui.remote.RemotePoolsTab
import com.example.antminerconnectiontest.ui.setup.SetupScreen

/**
 * A Hardware / Pools / Logs tab with the selected-machine context
 * header on top (v0.2.0): every tab opens by naming the machine it is
 * about — nickname, worker, LIVE/REMOTE chip, cloud updated line —
 * and the content below rebinds whenever the selection changes.
 */
@Composable
private fun TabWithMachineHeader(
    detail: MinerDetailUi?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEditNickname: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (detail != null) {
            SelectedMachineHeader(
                detail = detail,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onEditNickname = onEditNickname,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
fun MainDashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mergedMiners by viewModel.mergedMiners.collectAsState()
    val activeProfile = uiState.activeProfile

    // Phone-nickname editor (v0.1.7): the fleet row pencil / long-press,
    // the detail header pencil, and the Home header pencil all open this
    // one dialog for the tapped machine.
    var nicknameEditEntry by remember { mutableStateOf<MergedMinerEntry?>(null) }

    // Refresh on return to the foreground when the dashboard is empty,
    // errored, or stale — instead of showing pre-sleep data indefinitely.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }

    // The selected snapshot miner as unified detail data — feeds the
    // Hardware / Pools / Logs tabs when the selected machine is shown
    // from its cloud snapshot, resolved with the same name chain the
    // fleet uses.
    val selectedRemoteDetail = uiState.selectedRemoteMiner?.let { snap ->
        val entry = mergedMiners.firstOrNull { it.remoteSnapshot?.id == snap.id }
        snap.toMinerDetailUi(
            displayName = entry?.displayName
                ?: PhoneNicknames.displayName(uiState.nicknameOverrides, entry?.localProfile, snap)
        )
    }

    // ── v0.2.0: the tabs follow the ONE selected machine ─────────────
    // Same precedence as Home: when the selected snapshot is the same
    // machine as the active local profile AND live local data for it is
    // online, the tabs show the LIVE machine; otherwise a selected
    // snapshot machine's tabs show its cloud data. With no snapshot
    // selected, the tabs show the active local profile.
    val selectedEntry = uiState.selectedRemoteMiner?.let { snap ->
        mergedMiners.firstOrNull { it.remoteSnapshot?.id == snap.id }
    }
    val activeEntry = activeProfile?.let { profile ->
        mergedMiners.firstOrNull { it.localProfile?.id == profile.id }
    }
    val localWinsForTabs = uiState.selectedRemoteMiner != null &&
        activeProfile != null &&
        selectedEntry?.localProfile?.id == activeProfile.id &&
        uiState.dashboardData?.profileId == activeProfile.id &&
        uiState.dashboardData?.isOnline == true
    val useCloudTabs = uiState.selectedRemoteMiner != null && !localWinsForTabs
    val localDetail = activeProfile?.let { profile ->
        localMinerDetailUi(
            profile = profile,
            data = uiState.dashboardData,
            displayName = activeEntry?.displayName
                ?: PhoneNicknames.displayName(uiState.nicknameOverrides, profile, null)
        )
    }
    /** The detail every Hardware/Pools/Logs header + cloud tab renders. */
    val tabDetail: MinerDetailUi? =
        if (useCloudTabs || activeProfile == null) selectedRemoteDetail else localDetail
    /** Merged entry of the machine the tabs are about (nickname editing). */
    val tabEntry = if (uiState.selectedRemoteMiner != null) selectedEntry else activeEntry
    /**
     * Device-tab identity card data: only when the selected machine is
     * NOT the active local profile — that profile's full identity cards
     * already render on the Device tab.
     */
    val identityDetail: MinerDetailUi? =
        if (activeProfile == null || useCloudTabs) selectedRemoteDetail else null

    // Liquid Glass capability for THIS device (v0.3.0): the low-end
    // guard — low-RAM or pre-Android 12 devices get glass-lite (no
    // live blur) even when the style is selected.
    val glassTier = rememberGlassTier()
    val isDarkResolved = when (uiState.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val isGlass = uiState.appStyle == AppStyle.LIQUID_GLASS

    AntminerConnectionTestTheme(
        themeMode = uiState.themeMode,
        appStyle = uiState.appStyle,
        glassTier = glassTier
    ) {
        // Top-level routing (see MainShellRouting): ONE shell for
        // everyone (v0.1.9) — fresh installs, fleet-only viewers and
        // connected users alike. The only other destination is the
        // add-miner setup flow, opened explicitly and exited back here.
        val route = MainShellRouting.route(uiState.isLocalSetupOpen, uiState.relayOnlyMode)
        when (route) {
            MainRoute.MAIN_SHELL -> {
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (isGlass) {
                        GlassTabBarCapsule(isDark = isDarkResolved) {
                            DashboardNavItems(
                                selectedTab = uiState.selectedTab,
                                onSelect = viewModel::selectDashboardTab
                            )
                        }
                    } else {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            DashboardNavItems(
                                selectedTab = uiState.selectedTab,
                                onSelect = viewModel::selectDashboardTab
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (uiState.selectedTab) {
                        DashboardTab.HOME -> {
                            // The ONE Home (v0.1.9): same composition
                            // with or without a local miner — fleet,
                            // selected machine (live or snapshot), and
                            // setup content while nothing is configured.
                            HomeScreen(
                                profile = activeProfile,
                                savedProfiles = uiState.savedProfiles,
                                mergedMiners = mergedMiners,
                                selectedRemoteMiner = uiState.selectedRemoteMiner,
                                minerHashrates = uiState.minerHashrates,
                                data = uiState.dashboardData,
                                alerts = uiState.activeAlerts,
                                cloudConfig = uiState.cloudConfig,
                                themeMode = uiState.themeMode,
                                isRefreshing = uiState.isRefreshing,
                                dashboardError = uiState.dashboardError,
                                onRetry = viewModel::manualRefresh,
                                onManualRefresh = viewModel::manualRefresh,
                                onOpenMinerSwitcher = { viewModel.toggleProfileSwitcher(true) },
                                onSelectMinerProfile = viewModel::selectSavedProfile,
                                onSelectRemoteMiner = viewModel::selectRemoteMiner,
                                onClearRemoteMinerView = viewModel::clearRemoteMinerView,
                                onOpenSetup = viewModel::openLocalSetup,
                                onToggleTheme = viewModel::toggleThemeMode,
                                nicknameOverrides = uiState.nicknameOverrides,
                                onEditNickname = { nicknameEditEntry = it },
                                needsPasswordMachines = uiState.autoConnectNeedsPassword,
                                onNeedsPasswordTap = viewModel::openNeedsPasswordEntry,
                                onSaveCloudConfig = viewModel::updateCloudConfig,
                                onTestCloudConnection = viewModel::testCloudConnection,
                                isTestingCloud = uiState.isTestingCloud,
                                cloudTestMessage = uiState.cloudTestMessage,
                                isCloudConnected = uiState.isCloudConnected,
                                // v0.3.3: skeleton until the first REAL
                                // fleet state has loaded — persisted
                                // state emitted AND (when cloud viewing
                                // is configured) the first cloud fetch
                                // completed. See HomeLoadState.
                                isLoading = !HomeLoadState.isLoaded(
                                    isInitialStateLoaded = uiState.isInitialStateLoaded,
                                    cloudConfigured = uiState.cloudConfig.userName.isNotBlank() &&
                                        uiState.cloudConfig.webhookUrl.isNotBlank(),
                                    hasCompletedFirstCloudFetch = uiState.hasCompletedFirstCloudFetch
                                )
                            )
                        }
                        DashboardTab.HARDWARE -> {
                            TabWithMachineHeader(
                                detail = tabDetail,
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = viewModel::manualRefresh,
                                onEditNickname = tabEntry?.let { entry -> { nicknameEditEntry = entry } }
                            ) {
                                if (useCloudTabs || activeProfile == null) {
                                    RemoteHardwareTab(detail = selectedRemoteDetail)
                                } else {
                                    HardwareScreen(
                                        profile = activeProfile,
                                        data = uiState.dashboardData,
                                        detail = localDetail!!,
                                        isRefreshing = uiState.isRefreshing,
                                        onManualRefresh = viewModel::manualRefresh,
                                        dashboardError = uiState.dashboardError,
                                        onRetry = viewModel::manualRefresh
                                    )
                                }
                            }
                        }
                        DashboardTab.POOLS -> {
                            TabWithMachineHeader(
                                detail = tabDetail,
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = viewModel::manualRefresh,
                                onEditNickname = tabEntry?.let { entry -> { nicknameEditEntry = entry } }
                            ) {
                                if (useCloudTabs || activeProfile == null) {
                                    RemotePoolsTab(detail = selectedRemoteDetail)
                                } else {
                                    PoolsScreen(
                                        profile = activeProfile,
                                        data = uiState.dashboardData,
                                        isRefreshing = uiState.isRefreshing,
                                        onManualRefresh = viewModel::manualRefresh,
                                        dashboardError = uiState.dashboardError,
                                        onRetry = viewModel::manualRefresh
                                    )
                                }
                            }
                        }
                        DashboardTab.LOGS -> {
                            TabWithMachineHeader(
                                detail = tabDetail,
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = viewModel::manualRefresh,
                                onEditNickname = tabEntry?.let { entry -> { nicknameEditEntry = entry } }
                            ) {
                                if (useCloudTabs || activeProfile == null) {
                                    // The selected machine's logs are
                                    // on-location: the panel names it.
                                    RemoteLogsTab(detail = selectedRemoteDetail)
                                } else {
                                    LogsScreen(
                                        profile = activeProfile,
                                        selectedTab = uiState.selectedLogsTab,
                                        currentLogText = uiState.currentLogText,
                                        historyLogText = uiState.historyLogText,
                                        isLoading = uiState.isLoadingLog,
                                        errorMessage = uiState.logErrorMessage,
                                        lastUpdated = uiState.lastLogUpdated,
                                        isOnline = uiState.dashboardData?.isOnline == true,
                                        dailyAnalysisState = uiState.dailyAnalysisState,
                                        onSelectTab = viewModel::selectLogsTab,
                                        onRefresh = viewModel::refreshActiveLog,
                                        onToggleDateSelection = viewModel::toggleDailyDateSelection,
                                        onSelectAllUnanalyzed = viewModel::selectAllUnanalyzedDates,
                                        onClearSelection = viewModel::clearDailyDateSelection,
                                        onStartDailyAnalysis = viewModel::startDailyAnalysis,
                                        onOpenAnalysisDetail = viewModel::openDailyAnalysisDetail,
                                        onCloseAnalysisDetail = viewModel::closeDailyAnalysisDetail,
                                        onRefreshDailyDates = { viewModel.loadDailyAnalysisDates(forceRefresh = true) }
                                    )
                                }
                            }
                        }
                        DashboardTab.DEVICE -> {
                            DeviceScreen(
                                profile = activeProfile,
                                savedProfiles = uiState.savedProfiles,
                                data = uiState.dashboardData,
                                themeMode = uiState.themeMode,
                                isRefreshing = uiState.isRefreshing,
                                cloudConfig = uiState.cloudConfig,
                                isTestingCloud = uiState.isTestingCloud,
                                cloudTestMessage = uiState.cloudTestMessage,
                                isCloudConnected = uiState.isCloudConnected,
                                onManualRefresh = viewModel::manualRefresh,
                                onSetThemeMode = viewModel::setThemeMode,
                                appStyle = uiState.appStyle,
                                glassTier = glassTier,
                                onSetAppStyle = viewModel::setAppStyle,
                                relayOnlyMode = uiState.relayOnlyMode,
                                onSetRelayOnlyMode = viewModel::setRelayOnlyMode,
                                onSelectProfile = viewModel::selectSavedProfile,
                                onDeleteProfile = viewModel::deleteSavedProfile,
                                onUpdateNicknameAndLocation = viewModel::updateMinerNicknameAndLocation,
                                onSaveCloudConfig = viewModel::updateCloudConfig,
                                onTestCloudConnection = viewModel::testCloudConnection,
                                updateCheckMessage = uiState.updateCheckMessage,
                                isCheckingForUpdate = uiState.isCheckingForUpdate,
                                onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                                // "Add Miner" opens the setup flow —
                                // with or without an active miner; the
                                // flow exits back into this shell and
                                // never disconnects the current miner.
                                onAddNewMiner = viewModel::openLocalSetup,
                                onDisconnect = viewModel::disconnect,
                                // Selected-machine identity card
                                // (v0.2.0) — follows the selection when
                                // it isn't the active local profile.
                                // Snapshots carry no serial: live-only.
                                selectedMachineName = identityDetail?.displayName,
                                selectedMachineWorker = identityDetail?.workerName ?: "",
                                selectedMachineModel = identityDetail?.modelName ?: "",
                                selectedMachineFirmware = identityDetail?.firmware ?: "",
                                selectedMachineSerial = "",
                                selectedMachineIp = uiState.selectedRemoteMiner?.ip ?: "",
                                selectedMachineIsRemote = identityDetail?.isRemote == true
                            )
                        }
                    }

                    // Multi-Miner Switcher Bottom Sheet
                    if (uiState.isProfileSwitcherOpen) {
                        MinerSwitcherBottomSheet(
                            activeProfile = activeProfile,
                            savedProfiles = uiState.savedProfiles,
                            minerHashrates = uiState.minerHashrates,
                            mergedMiners = mergedMiners,
                            selectedRemoteMinerId = uiState.selectedRemoteMiner?.id,
                            nicknameOverrides = uiState.nicknameOverrides,
                            onSelectProfile = { profile ->
                                viewModel.selectSavedProfile(profile)
                            },
                            onSelectRemoteMiner = viewModel::selectRemoteMiner,
                            onAddNewMiner = {
                                viewModel.toggleProfileSwitcher(false)
                                viewModel.openLocalSetup()
                            },
                            onDeleteProfile = { id ->
                                viewModel.deleteSavedProfile(id)
                            },
                            onDismiss = {
                                viewModel.toggleProfileSwitcher(false)
                            }
                        )
                    }
                }
            }
            } // wallpaper Box
            }
            // Relay-only (v0.3.0): the whole app is this one status
            // screen — no nav, no tabs. The relay underneath runs
            // unchanged; exiting is the screen's one button.
            MainRoute.RELAY_ONLY -> {
                val relayOn = uiState.cloudConfig.isSyncEnabled
                val relayMinerLines = uiState.savedProfiles.map { profile ->
                    val worker = profile.workerName.trim()
                    val rate = uiState.minerHashrates[profile.id] ?: 0.0
                    val activeOnline = activeProfile?.id == profile.id &&
                        uiState.dashboardData?.isOnline == true
                    val state = when {
                        !relayOn -> RelayMinerState.OFFLINE
                        worker.isBlank() -> RelayMinerState.WAITING
                        activeOnline || rate > 0.0 -> RelayMinerState.UPLOADING
                        else -> RelayMinerState.OFFLINE
                    }
                    RelayMinerLine(
                        name = worker.ifBlank { profile.displayName },
                        detail = profile.host + when {
                            !relayOn -> " · relay off"
                            state == RelayMinerState.UPLOADING -> " · connected"
                            state == RelayMinerState.WAITING -> " · worker name unknown — upload deferred"
                            else -> " · not reachable"
                        },
                        state = state
                    )
                }
                RelayOnlyScreen(
                    userName = uiState.cloudConfig.userName,
                    relayEnabled = relayOn,
                    cloudConfigured = uiState.cloudConfig.userName.isNotBlank() &&
                        uiState.cloudConfig.webhookUrl.isNotBlank(),
                    miners = relayMinerLines,
                    lastUploadAtMs = uiState.lastRelayUploadAtMs,
                    lastError = uiState.relayLastError,
                    appVersion = viewModel.installedVersionName() ?: "?",
                    onRefreshStatus = viewModel::refreshRelayStatus,
                    onExit = { viewModel.setRelayOnlyMode(false) },
                    // v0.3.3: skeleton panels until the persisted
                    // state has been emitted into the UI state — the
                    // pre-load defaults must not read as real state.
                    isLoading = !uiState.isInitialStateLoaded
                )
            }
            // Add-miner setup: scan/manual add flow, reached only by an
            // explicit tap (Add Miner / Add Unit / a needs-password
            // card). Exiting always returns to the one shell.
            MainRoute.SETUP -> {
                SetupScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onExitSetup = viewModel::closeLocalSetup
                )
            }
        }

        // Phone-nickname editor, over whichever screen is current.
        nicknameEditEntry?.let { entry ->
            NicknameEditDialog(
                machineLabel = entry.displayName,
                currentNickname = PhoneNicknames.overrideFor(uiState.nicknameOverrides, entry) ?: "",
                onSave = { name ->
                    viewModel.setPhoneNickname(entry, name)
                    nicknameEditEntry = null
                },
                onClear = {
                    viewModel.setPhoneNickname(entry, "")
                    nicknameEditEntry = null
                },
                onDismiss = { nicknameEditEntry = null }
            )
        }

        // Update prompt: shown once per version per session, over
        // whichever screen is current. "Update" opens the release APK
        // in the browser for download + install-over; "Later" dismisses
        // this version for the rest of the session.
        val pendingUpdate = uiState.availableUpdate
        // Never in relay-only mode: a site phone is never interrupted
        // by update UI (the cold-start check is skipped there too).
        if (route != MainRoute.RELAY_ONLY && pendingUpdate != null &&
            uiState.updatePromptDismissedTag != pendingUpdate.tag
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdatePrompt() },
                title = { Text("Update available — v${pendingUpdate.versionName}") },
                text = {
                    Text(
                        text = pendingUpdate.notes.ifBlank {
                            "A newer version of the app is ready to download."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(pendingUpdate.apkDownloadUrl)
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Throwable) {}
                            viewModel.dismissUpdatePrompt()
                        }
                    ) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePrompt() }) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

/**
 * The five bottom-navigation items, shared by the solid [NavigationBar]
 * (Violet style) and the floating glass capsule (Liquid Glass style) —
 * same destinations, same selection colours, only the container
 * material differs.
 */
@Composable
private fun RowScope.DashboardNavItems(
    selectedTab: DashboardTab,
    onSelect: (DashboardTab) -> Unit
) {
    val grouped = LocalGlassSettings.current.isGlass
    @Composable
    fun itemColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = if (grouped) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        },
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    NavigationBarItem(
        selected = selectedTab == DashboardTab.HOME,
        onClick = { onSelect(DashboardTab.HOME) },
        icon = { Icon(if (grouped) Icons.Default.Home else Icons.Default.Dashboard, contentDescription = "Home") },
        label = { Text("Home") },
        colors = itemColors()
    )
    NavigationBarItem(
        selected = selectedTab == DashboardTab.HARDWARE,
        onClick = { onSelect(DashboardTab.HARDWARE) },
        icon = { Icon(Icons.Default.Memory, contentDescription = "Hardware") },
        label = { Text("Hardware") },
        colors = itemColors()
    )
    NavigationBarItem(
        selected = selectedTab == DashboardTab.POOLS,
        onClick = { onSelect(DashboardTab.POOLS) },
        icon = { Icon(if (grouped) Icons.Default.Layers else Icons.Default.Storage, contentDescription = "Pools") },
        label = { Text("Pools") },
        colors = itemColors()
    )
    NavigationBarItem(
        selected = selectedTab == DashboardTab.LOGS,
        onClick = { onSelect(DashboardTab.LOGS) },
        icon = { Icon(if (grouped) Icons.Default.ListAlt else Icons.Default.Terminal, contentDescription = "Logs") },
        label = { Text("Logs") },
        colors = itemColors()
    )
    NavigationBarItem(
        selected = selectedTab == DashboardTab.DEVICE,
        onClick = { onSelect(DashboardTab.DEVICE) },
        icon = { Icon(if (grouped) Icons.Default.Settings else Icons.Default.Devices, contentDescription = "Settings") },
        label = { Text("Settings") },
        colors = itemColors()
    )
}

/**
 * The floating dock (v0.4.0, V3 mockup): the ONE glass surface left
 * in the app — a capsule with the mockup's frosted material
 * ([GroupedSheetsSpec.dockBackground] / dockBorder) and a soft
 * shadow, hovering over the grouped base instead of a full-width
 * bar. On FULL-tier devices the live-blur backdrop still frosts
 * what's behind it (GlassCapability rule unchanged); on glass-lite
 * the translucent fill + border alone carry the look.
 *
 * Sizing (v0.3.2 bugfix): the capsule's geometry comes ONLY from
 * [GlassTabBarSpec] — a fixed bar height, screen width minus fixed
 * margins — and nothing inside it may influence its size. The v0.3.0
 * version let the backdrop's `fillMaxSize()` resolve against the
 * Scaffold bottomBar slot's full-screen maxHeight, inflating the
 * capsule into a screen-filling pill that hid all content. It lives
 * in the bottomBar slot, so the Scaffold's content padding already
 * reserves exactly [GlassTabBarSpec.ReservedHeight] above it and no
 * content can slide behind it.
 */
@Composable
private fun GlassTabBarCapsule(
    isDark: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = GlassTabBarSpec.HorizontalMargin,
                vertical = GlassTabBarSpec.VerticalMargin
            )
    ) {
        Surface(
            shape = RoundedCornerShape(GlassTabBarSpec.CornerRadiusPercent),
            color = GroupedSheetsSpec.dockBackground(isDark),
            border = BorderStroke(1.dp, GroupedSheetsSpec.dockBorder(isDark)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassTabBarSpec.Height)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = GlassTabBarSpec.InnerHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
