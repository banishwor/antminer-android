package com.example.antminerconnectiontest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.PhoneNicknames
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.MinerSwitcherBottomSheet
import com.example.antminerconnectiontest.ui.components.NicknameEditDialog
import com.example.antminerconnectiontest.ui.components.toMinerDetailUi
import com.example.antminerconnectiontest.ui.device.DeviceScreen
import com.example.antminerconnectiontest.ui.hardware.HardwareScreen
import com.example.antminerconnectiontest.ui.home.HomeScreen
import com.example.antminerconnectiontest.ui.logs.LogsScreen
import com.example.antminerconnectiontest.ui.pools.PoolsScreen
import com.example.antminerconnectiontest.ui.remote.RemoteHardwareTab
import com.example.antminerconnectiontest.ui.remote.RemoteLogsTab
import com.example.antminerconnectiontest.ui.remote.RemotePoolsTab
import com.example.antminerconnectiontest.ui.remote.RemoteViewScreen
import com.example.antminerconnectiontest.ui.setup.SetupScreen
import com.example.antminerconnectiontest.ui.start.StartChoiceScreen

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

    // The selected remote miner as unified detail data — feeds the
    // Hardware / Pools / Logs tabs when no local miner is active
    // (remote mode), resolved with the same name chain the fleet uses.
    val selectedRemoteDetail = uiState.selectedRemoteMiner?.let { snap ->
        val entry = mergedMiners.firstOrNull { it.remoteSnapshot?.id == snap.id }
        snap.toMinerDetailUi(
            displayName = entry?.displayName
                ?: PhoneNicknames.displayName(uiState.nicknameOverrides, entry?.localProfile, snap)
        )
    }

    AntminerConnectionTestTheme(themeMode = uiState.themeMode) {
        // Top-level routing (see MainShellRouting): once a start choice
        // exists the main shell ALWAYS renders — a Remote View starter
        // with no local miner connected gets the same Scaffold and
        // bottom navigation as everyone else (v0.1.8 fix; before, they
        // were stranded on a bare standalone remote screen).
        val route = MainShellRouting.route(
            hasSavedProfiles = uiState.savedProfiles.isNotEmpty(),
            startChoice = uiState.startChoice,
            hasActiveProfile = activeProfile != null,
            isLocalSetupOpen = uiState.isLocalSetupOpen
        )
        when (route) {
            MainRoute.START_CHOICE -> {
                StartChoiceScreen(viewModel = viewModel, uiState = uiState)
            }
            MainRoute.MAIN_SHELL -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == DashboardTab.HOME,
                            onClick = { viewModel.selectDashboardTab(DashboardTab.HOME) },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == DashboardTab.HARDWARE,
                            onClick = { viewModel.selectDashboardTab(DashboardTab.HARDWARE) },
                            icon = { Icon(Icons.Default.Memory, contentDescription = "Hardware") },
                            label = { Text("Hardware") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == DashboardTab.POOLS,
                            onClick = { viewModel.selectDashboardTab(DashboardTab.POOLS) },
                            icon = { Icon(Icons.Default.Storage, contentDescription = "Pools") },
                            label = { Text("Pools") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == DashboardTab.LOGS,
                            onClick = { viewModel.selectDashboardTab(DashboardTab.LOGS) },
                            icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs") },
                            label = { Text("Logs") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == DashboardTab.DEVICE,
                            onClick = { viewModel.selectDashboardTab(DashboardTab.DEVICE) },
                            icon = { Icon(Icons.Default.Devices, contentDescription = "Device") },
                            label = { Text("Device") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
                            if (activeProfile == null) {
                                // Remote mode: the fleet + unified
                                // remote detail as this shell's Home
                                // tab — same content Remote View always
                                // had, now with the menus around it.
                                RemoteViewScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    mergedMiners = mergedMiners,
                                    onEditNickname = { nicknameEditEntry = it }
                                )
                            } else {
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
                                onAddNewMiner = viewModel::disconnect,
                                onToggleTheme = viewModel::toggleThemeMode,
                                nicknameOverrides = uiState.nicknameOverrides,
                                onEditNickname = { nicknameEditEntry = it }
                            )
                            }
                        }
                        DashboardTab.HARDWARE -> {
                            if (activeProfile == null) {
                                RemoteHardwareTab(detail = selectedRemoteDetail)
                            } else {
                            HardwareScreen(
                                profile = activeProfile,
                                data = uiState.dashboardData,
                                isRefreshing = uiState.isRefreshing,
                                onManualRefresh = viewModel::manualRefresh,
                                dashboardError = uiState.dashboardError,
                                onRetry = viewModel::manualRefresh
                            )
                            }
                        }
                        DashboardTab.POOLS -> {
                            if (activeProfile == null) {
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
                        DashboardTab.LOGS -> {
                            if (activeProfile == null) {
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
                                isOnline = uiState.dashboardData?.isOnline == true && uiState.selectedRemoteMiner == null,
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
                                onSelectProfile = viewModel::selectSavedProfile,
                                onDeleteProfile = viewModel::deleteSavedProfile,
                                onUpdateNicknameAndLocation = viewModel::updateMinerNicknameAndLocation,
                                onSaveCloudConfig = viewModel::updateCloudConfig,
                                onTestCloudConnection = viewModel::testCloudConnection,
                                updateCheckMessage = uiState.updateCheckMessage,
                                isCheckingForUpdate = uiState.isCheckingForUpdate,
                                onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                                // In remote mode "Add Miner" must open
                                // the local setup flow (there is no
                                // active miner to disconnect from); with
                                // a miner active it stays the disconnect
                                // path it has always been.
                                onAddNewMiner = if (activeProfile == null) {
                                    viewModel::openLocalSetup
                                } else {
                                    viewModel::disconnect
                                },
                                onDisconnect = viewModel::disconnect
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
                                viewModel.disconnect()
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
            }
            // Local setup: scan/manual add flow. Reached by Scan starters,
            // by "Add New Miner", and by remote starters adding a local
            // miner on site (who exit back into the main shell via
            // closeLocalSetup — the shell's Home tab is their fleet).
            MainRoute.SETUP -> {
                SetupScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onExitSetup = if (uiState.startChoice == StartChoice.REMOTE) {
                        viewModel::closeLocalSetup
                    } else {
                        null
                    }
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
        if (pendingUpdate != null && uiState.updatePromptDismissedTag != pendingUpdate.tag) {
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
