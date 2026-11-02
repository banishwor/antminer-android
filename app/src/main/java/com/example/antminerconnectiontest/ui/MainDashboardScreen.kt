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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.MinerSwitcherBottomSheet
import com.example.antminerconnectiontest.ui.device.DeviceScreen
import com.example.antminerconnectiontest.ui.hardware.HardwareScreen
import com.example.antminerconnectiontest.ui.home.HomeScreen
import com.example.antminerconnectiontest.ui.logs.LogsScreen
import com.example.antminerconnectiontest.ui.pools.PoolsScreen
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

    // Refresh on return to the foreground when the dashboard is empty,
    // errored, or stale — instead of showing pre-sleep data indefinitely.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }

    AntminerConnectionTestTheme(themeMode = uiState.themeMode) {
        when {
            // Fresh install: no local miners saved and no start choice made
            // yet — ask once. Nothing scans or loads locally before this.
            uiState.savedProfiles.isEmpty() && uiState.startChoice == null -> {
                StartChoiceScreen(viewModel = viewModel, uiState = uiState)
            }
            activeProfile != null -> {
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
                            HomeScreen(
                                profile = activeProfile,
                                savedProfiles = uiState.savedProfiles,
                                remoteMiners = uiState.remoteMiners,
                                mergedMiners = mergedMiners,
                                selectedRemoteMiner = uiState.selectedRemoteMiner,
                                minerHashrates = uiState.minerHashrates,
                                data = uiState.dashboardData,
                                alerts = uiState.activeAlerts,
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
                                onToggleTheme = viewModel::toggleThemeMode
                            )
                        }
                        DashboardTab.HARDWARE -> {
                            HardwareScreen(
                                profile = activeProfile,
                                data = uiState.dashboardData,
                                isRefreshing = uiState.isRefreshing,
                                onManualRefresh = viewModel::manualRefresh,
                                dashboardError = uiState.dashboardError,
                                onRetry = viewModel::manualRefresh
                            )
                        }
                        DashboardTab.POOLS -> {
                            PoolsScreen(
                                profile = activeProfile,
                                data = uiState.dashboardData,
                                isRefreshing = uiState.isRefreshing,
                                onManualRefresh = viewModel::manualRefresh,
                                dashboardError = uiState.dashboardError,
                                onRetry = viewModel::manualRefresh
                            )
                        }
                        DashboardTab.LOGS -> {
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
                                onAddNewMiner = viewModel::disconnect,
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
                            onSelectProfile = { profile ->
                                viewModel.selectSavedProfile(profile)
                            },
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
            // Remote starter with no local miner selected: the cloud farm
            // view. (Also where a remote user lands after disconnecting a
            // local miner — their fleet view, not a setup screen.)
            uiState.startChoice == StartChoice.REMOTE && !uiState.isLocalSetupOpen -> {
                RemoteViewScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    mergedMiners = mergedMiners
                )
            }
            // Local setup: scan/manual add flow. Reached by Scan starters,
            // by "Add New Miner", and by remote starters adding a local
            // miner on site (who get a way back to Remote View).
            else -> {
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
    }
}
