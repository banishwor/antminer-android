package com.example.antminerconnectiontest.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.alerts.MinerAlert
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.FleetSorting
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.PhoneNicknames
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.FleetListCard
import com.example.antminerconnectiontest.ui.components.HeroHashrateCard
import com.example.antminerconnectiontest.ui.components.MetricCard
import com.example.antminerconnectiontest.ui.components.MinerDataState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.antminerconnectiontest.ui.components.MinerDataStateCard
import com.example.antminerconnectiontest.ui.components.MinerDetailContent
import com.example.antminerconnectiontest.ui.components.toMinerDetailUi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * THE Home — the only one (v0.1.9). Everyone lands here in the single
 * shell: users with local miners, users who only view the cloud fleet,
 * and fresh installs with nothing configured yet. [profile] is the
 * active local miner, or null when none is connected; a selected fleet
 * machine that exists only as a cloud snapshot renders through the same
 * header/detail components with the same styling — the only markers of
 * its source are the REMOTE chip and the "last updated" line. When
 * nothing is configured yet, Home carries setup CONTENT (cloud setup
 * card, Add Miner card) inside the same shell instead of routing to a
 * separate first-run flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: MinerProfile?,
    savedProfiles: List<MinerProfile>,
    mergedMiners: List<MergedMinerEntry> = emptyList(),
    selectedRemoteMiner: RemoteMinerSnapshot? = null,
    minerHashrates: Map<String, Double> = emptyMap(),
    data: MinerDashboardData?,
    alerts: List<MinerAlert>,
    cloudConfig: CloudSyncConfig = CloudSyncConfig(),
    themeMode: ThemeMode,
    isRefreshing: Boolean,
    dashboardError: String? = null,
    onRetry: (() -> Unit)? = null,
    onManualRefresh: () -> Unit,
    onOpenMinerSwitcher: () -> Unit,
    onSelectMinerProfile: (MinerProfile) -> Unit,
    onSelectRemoteMiner: (RemoteMinerSnapshot) -> Unit = {},
    onClearRemoteMinerView: () -> Unit = {},
    /** Opens the add-miner setup flow (scan / manual connect). */
    onOpenSetup: () -> Unit,
    onToggleTheme: () -> Unit,
    /** This phone's private nicknames (v0.1.7), for name resolution in the header. */
    nicknameOverrides: Map<String, String> = emptyMap(),
    onEditNickname: (MergedMinerEntry) -> Unit = {},
    /** Known machines on this Wi-Fi that need their password typed once (auto-connect, v0.1.9). */
    needsPasswordMachines: List<com.example.antminerconnectiontest.domain.model.NeedsPasswordMachine> = emptyList(),
    onNeedsPasswordTap: (com.example.antminerconnectiontest.domain.model.NeedsPasswordMachine) -> Unit = {},
    /** Cloud setup card wiring (same ViewModel calls the Device tab uses). */
    onSaveCloudConfig: (webhookUrl: String, userName: String, relayEnabled: Boolean) -> Unit = { _, _, _ -> },
    onTestCloudConnection: (webhookUrl: String, userName: String) -> Unit = { _, _ -> },
    isTestingCloud: Boolean = false,
    cloudTestMessage: String? = null,
    isCloudConnected: Boolean = false
) {
    val summary = data?.summary
    val stats = data?.stats
    val isOnline = data?.isOnline == true
    val isExplicitRemote = selectedRemoteMiner != null

    // Remote display is ALWAYS a deliberate selection. The dashboard used
    // to silently substitute remoteMiners.first() whenever the local miner
    // went offline — the user could end up looking at a cloud feed (maybe
    // another machine entirely) without choosing it. That swap is gone.
    //
    // The one designed precedence: when the selected snapshot is the SAME
    // machine as the active local profile and live local data for it has
    // been fetched, the local data overwrites the snapshot for display —
    // it is fresher. While local data is unavailable (still loading,
    // miner offline), the snapshot shows. The banner below makes which
    // one is on screen visible either way.
    val selectedEntry = selectedRemoteMiner?.let { snap ->
        mergedMiners.firstOrNull { it.remoteSnapshot?.id == snap.id }
    }
    // The merged entry for the active local profile (nickname editing and
    // resolved display names come from the same entry the fleet shows).
    val activeEntry = if (profile != null) {
        mergedMiners.firstOrNull { it.localProfile?.id == profile.id }
    } else {
        null
    }
    // True when something is selected to head the page: the active local
    // miner, or a fleet machine selected from the list. A selected
    // snapshot whose machine is ALSO saved locally activates that
    // profile (see selectRemoteMiner), so a header with no profile is a
    // snapshot-only machine.
    val hasSelection = profile != null || selectedRemoteMiner != null
    val headerDisplayName = if (selectedRemoteMiner != null) {
        selectedEntry?.displayName
            ?: PhoneNicknames.displayName(nicknameOverrides, selectedEntry?.localProfile, selectedRemoteMiner)
    } else if (profile != null) {
        activeEntry?.displayName ?: PhoneNicknames.displayName(nicknameOverrides, profile, null)
    } else {
        ""
    }
    val headerHasCustomName = if (selectedRemoteMiner != null) {
        PhoneNicknames.overrideFor(nicknameOverrides, selectedEntry?.localProfile, selectedRemoteMiner) != null ||
            !selectedEntry?.localProfile?.nickname.isNullOrBlank()
    } else {
        profile != null &&
            (PhoneNicknames.overrideFor(nicknameOverrides, profile, null) != null || profile.nickname.isNotBlank())
    }
    val localWinsForSelectedRemote = selectedRemoteMiner != null &&
        profile != null &&
        selectedEntry?.localProfile?.id == profile.id &&
        data?.profileId == profile.id &&
        data?.isOnline == true
    val effectiveRemote = if (localWinsForSelectedRemote) null else selectedRemoteMiner

    // A snapshot is on screen (the selection exists only as cloud data).
    // Since v0.1.9 this is NOT a visual "mode": the same components and
    // styling render it; only the REMOTE chip and the last-updated line
    // in the header say where the data came from.
    val isSnapshotSelection = effectiveRemote != null
    val isStaleCloud = isSnapshotSelection && effectiveRemote?.isStale == true
    val effectiveRetry = onRetry ?: onManualRefresh

    // Tick every 5 s so the stale indicator can appear even when no new
    // state arrives (e.g. polling silently stalled after the phone slept).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(5_000L)
        }
    }
    // Older than ~2 poll cycles (2 x 15 s) without a refresh landing.
    val isStaleLocal = !isSnapshotSelection && profile != null && data != null && nowMs - data.lastUpdated > 30_000L

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Worker identity shown as a chip next to a custom display name —
    // for a snapshot it is the row's worker name (never a device UUID).
    val headerWorkerName = when {
        effectiveRemote != null -> effectiveRemote.id.trim()
            .takeIf { it.isNotBlank() && !com.example.antminerconnectiontest.domain.model.MinerMerge.isDeviceId(it) }
            ?: com.example.antminerconnectiontest.domain.model.MinerMerge.workerIdentity(effectiveRemote)
            ?: ""
        profile != null -> profile.workerName
        else -> ""
    }
    // Online for the header dot: a snapshot counts online when its data
    // is fresh and hashing — the same rule the unified detail applies.
    val headerOnline = if (effectiveRemote != null) {
        !effectiveRemote.isStale && effectiveRemote.effectiveHashrate5s > 0.0
    } else {
        isOnline
    }

    // Compute active hashrate values (supports 2-minute stale watchdog)
    val hashrate = if (isSnapshotSelection) {
        effectiveRemote!!.effectiveHashrate5s
    } else when {
        (summary?.hashrate5s ?: 0.0) > 0.0 -> summary!!.hashrate5s
        (stats?.chains?.sumOf { it.realHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.realHashrateTh }
        else -> summary?.hashrateAvg ?: 0.0
    }

    val avgRate = if (isSnapshotSelection) {
        effectiveRemote!!.effectiveAvgHashrate
    } else when {
        (summary?.hashrateAvg ?: 0.0) > 0.0 -> summary!!.hashrateAvg
        (summary?.hashrate30m ?: 0.0) > 0.0 -> summary!!.hashrate30m
        else -> hashrate
    }

    val idealRate = when {
        // Snapshot selection: the relayed ideal (v0.1.6 telemetry) is
        // the real nameplate rate for the machine; only rows written
        // before it existed fall through to the generic default.
        isSnapshotSelection && (effectiveRemote?.idealHashrate ?: 0.0) > 0.0 -> effectiveRemote!!.idealHashrate!!
        (summary?.hashrateIdeal ?: 0.0) > 0.0 -> summary!!.hashrateIdeal
        (stats?.chains?.sumOf { it.idealHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.idealHashrateTh }
        else -> 120.0
    }

    val displayTemp = if (isSnapshotSelection) effectiveRemote!!.maxTemp else (stats?.maxChipTemp ?: 0)
    val displayFan = if (isSnapshotSelection) effectiveRemote!!.maxFan else (stats?.maxFanRpm ?: 0)

    // Scrollable layout inside pull-to-refresh. This used to be a fixed,
    // non-scrollable SpaceBetween column with hard-coded text sizes, which
    // clipped content on small phones or with larger system font scaling.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onManualRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 0. Fleet header: total hashrate of the online fleet and the
            // cloud account chip (who this phone relays as, and whether
            // the relay is on) — the fleet is the home, the selected
            // machine's dashboard follows below.
            FleetHeaderCard(
                mergedMiners = mergedMiners,
                cloudConfig = cloudConfig,
                nowMs = nowMs
            )

            // 1a. Needs-password cards (auto-connect, v0.1.9): known
            // machines on this Wi-Fi that rejected the phone's
            // credentials. Tapping one opens the credential entry with
            // the IP prefilled — enter it once, auto-connect takes over.
            needsPasswordMachines.forEach { machine ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(WarningAmber.copy(alpha = 0.6f))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNeedsPasswordTap(machine) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${machine.displayName} needs its password",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Found at ${machine.ip} on this Wi-Fi" +
                                    if (machine.ownerUser.isNotBlank()) " • Uploads as ${machine.ownerUser}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = LocalDataFontFamily.current,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Enter password",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 1b. Setup CONTENT for a fresh install — inside the same
            // Home, never a separate first-run flow (v0.1.9): the cloud
            // setup card while no relay user name is set, and the Add
            // Miner card while no local miner exists.
            if (!hasSelection) {
                if (cloudConfig.userName.isBlank()) {
                    HomeCloudSetupCard(
                        cloudConfig = cloudConfig,
                        isTestingCloud = isTestingCloud,
                        cloudTestMessage = cloudTestMessage,
                        isCloudConnected = isCloudConnected,
                        onTestCloudConnection = onTestCloudConnection,
                        onSaveCloudConfig = onSaveCloudConfig
                    )
                }
                if (savedProfiles.isEmpty()) {
                    HomeAddMinerCard(onOpenSetup = onOpenSetup)
                }
            }

            // 1. Miner Header Bar (Compact & Static) — shown whenever a
            // machine is selected, local or snapshot, in ONE styling:
            // a snapshot differs only by its REMOTE chip and the
            // "last updated" line under the subtitle.
            if (hasSelection) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(if (isStaleCloud) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Indicator & Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isStaleCloud) MaterialTheme.colorScheme.error else if (headerOnline) AntminerEmerald else MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = headerDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSnapshotSelection) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "REMOTE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (headerWorkerName.isNotBlank() && (headerHasCustomName || headerDisplayName != headerWorkerName)) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = headerWorkerName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = LocalDataFontFamily.current,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isSnapshotSelection) {
                                        listOf(effectiveRemote!!.ip, "Owner: ${effectiveRemote.user}")
                                            .filter { it.isNotBlank() }
                                            .joinToString(" • ")
                                    } else {
                                        profile?.host ?: ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = LocalDataFontFamily.current,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isSnapshotSelection && profile != null && profile.location.isNotBlank() && profile.location != "Default Location") {
                                    Text(
                                        text = " • ${profile.location}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSnapshotSelection) {
                                Text(
                                    text = "Updated ${effectiveRemote!!.timeAgoFormatted} · via relay" +
                                        if (isStaleCloud) " — signal lost (>2m), values may be stale" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isStaleCloud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Header Action Buttons (Nickname + Theme Toggle + Refresh)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val nicknameTarget = if (isSnapshotSelection) selectedEntry else activeEntry
                        if (nicknameTarget != null) {
                            IconButton(
                                onClick = { onEditNickname(nicknameTarget) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Set nickname on this phone",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(onClick = onToggleTheme, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (themeMode == ThemeMode.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = onManualRefresh, enabled = !isRefreshing, modifier = Modifier.size(32.dp)) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            }

            // Merged selection where live LOCAL data won over the machine's
            // cloud snapshot: say so on screen — precedence is visible,
            // never a silent swap in either direction.
            if (selectedRemoteMiner != null && !isSnapshotSelection) {
                Surface(
                    color = AntminerEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(AntminerEmerald.copy(alpha = 0.4f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = AntminerEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "LOCAL LIVE — SAME MACHINE",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AntminerEmerald
                                )
                                Text(
                                    text = "Showing live data from this miner • Its cloud snapshot is kept as fallback",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = onClearRemoteMinerView,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 1c. Loading / error state for the local miner (never for a
            // snapshot selection): a first fetch in flight shows progress;
            // a failed fetch shows the error with Retry instead of a fake
            // "dead miner" dashboard.
            if (!isSnapshotSelection && profile != null) {
                if (data == null) {
                    if (dashboardError != null) {
                        MinerDataStateCard(
                            state = MinerDataState.ERROR,
                            host = profile.host,
                            message = dashboardError,
                            onRetry = effectiveRetry
                        )
                    } else {
                        MinerDataStateCard(
                            state = MinerDataState.LOADING,
                            host = profile.host
                        )
                    }
                } else if (dashboardError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dashboardError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = effectiveRetry,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 2. Active Alert Banners (if any)
            AnimatedVisibility(visible = alerts.isNotEmpty() && !isSnapshotSelection) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alerts.take(2).forEach { alert ->
                        AlertBanner(alert = alert)
                    }
                }
            }

            // 3. Hero + metrics for the local machine, or — for a
            // snapshot selection — the SAME unified detail (v0.1.7): one
            // machine, one presentation; only the data source differs.
            // Home's header bar above already carries the name/source
            // line, so the detail header is not repeated here.
            if (isSnapshotSelection) {
                MinerDetailContent(
                    detail = effectiveRemote!!.toMinerDetailUi(displayName = headerDisplayName),
                    showHeader = false,
                    onEditNickname = { selectedEntry?.let(onEditNickname) }
                )
            } else if (profile != null) {
                HeroHashrateCard(
                    hashrate = hashrate,
                    avgRate = avgRate,
                    idealRate = idealRate,
                    isOnline = isOnline,
                    isCloudMode = false,
                    isStaleCloud = false
                )

                // 4. Quick Metrics 2x2 Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Chip Temp Max",
                        value = if (displayTemp > 0) "$displayTemp°C" else "--°C",
                        icon = Icons.Default.Thermostat,
                        modifier = Modifier.weight(1f),
                        accentColor = if (displayTemp > 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    MetricCard(
                        title = "Max Fan RPM",
                        value = if (displayFan > 0) "$displayFan RPM" else "-- RPM",
                        icon = Icons.Default.Air,
                        modifier = Modifier.weight(1f),
                        accentColor = CyberCyan
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalHw = summary?.hwAll ?: stats?.totalHwErrors ?: 0L
                    MetricCard(
                        title = "HW Errors",
                        value = "$totalHw",
                        icon = Icons.Default.BugReport,
                        modifier = Modifier.weight(1f),
                        accentColor = if (totalHw > 100) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MetricCard(
                        title = "Status",
                        value = summary?.formattedUptime ?: "Online",
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f),
                        accentColor = ElectricBlue
                    )
                }
            }

            // 5. Combined fleet list: local + remote in one sorted list.
            FleetListCard(
                mergedMiners = mergedMiners,
                activeProfile = profile,
                selectedRemoteMinerId = selectedRemoteMiner?.id,
                activePoolBrand = FleetSorting.poolBrandFromUrl(
                    data?.pools?.firstOrNull { it.priority == 0 }?.url
                        ?: data?.pools?.firstOrNull()?.url
                ),
                activeTempC = stats?.maxChipTemp,
                onSelectProfile = onSelectMinerProfile,
                onSelectRemoteMiner = onSelectRemoteMiner,
                onAddNewMiner = onOpenSetup,
                onEditNickname = onEditNickname
            )

            // Empty fleet with cloud configured: say where miners come
            // from instead of leaving a blank page under the header.
            if (mergedMiners.isEmpty() && cloudConfig.userName.isNotBlank()) {
                Text(
                    text = "No miners yet. Machines appear here once a device at a site uploads them, when auto-connect finds them on this Wi-Fi, or when you add one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Footer Sync Timestamp (Always at bottom)
        val footerText = if (isSnapshotSelection) {
            "Cloud sync: ${effectiveRemote!!.lastUpdated.ifBlank { timeFormatter.format(Date()) }} • via relay"
        } else if (data != null) {
            "Last sync: ${timeFormatter.format(Date(data.lastUpdated))} • Polling 15s"
        } else if (profile != null) {
            "Connecting..."
        } else {
            "No miner connected"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isStaleLocal) {
                Surface(
                    color = WarningAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "STALE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = footerText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isStaleLocal) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
    }
}

/**
 * Cloud setup as Home CONTENT (v0.1.9): shown inside the one Home while
 * no relay user name is set. It is a settings step, not a mode — the
 * same calls the Device tab makes (test / save cloud config); saving a
 * user name makes the fleet fetch start and this card disappear. The
 * Relay switch is left exactly as it is — setting up viewing never
 * flips it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCloudSetupCard(
    cloudConfig: CloudSyncConfig,
    isTestingCloud: Boolean,
    cloudTestMessage: String?,
    isCloudConnected: Boolean,
    onTestCloudConnection: (webhookUrl: String, userName: String) -> Unit,
    onSaveCloudConfig: (webhookUrl: String, userName: String, relayEnabled: Boolean) -> Unit
) {
    var userNameInput by remember(cloudConfig.userName) { mutableStateOf(cloudConfig.userName) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Set up cloud relay",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "See your miners from anywhere: enter the user name your sites upload as. You can also do this later under Device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it },
                label = { Text("Your User Name") },
                placeholder = { Text("Enter your user name") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!cloudTestMessage.isNullOrBlank()) {
                Surface(
                    color = if (isCloudConnected) AntminerEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cloudTestMessage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTestCloudConnection(cloudConfig.webhookUrl, userNameInput.trim()) },
                    enabled = !isTestingCloud && userNameInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTestingCloud) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        onSaveCloudConfig(
                            cloudConfig.webhookUrl,
                            userNameInput.trim(),
                            cloudConfig.isSyncEnabled
                        )
                    },
                    enabled = userNameInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Add Miner as Home CONTENT (v0.1.9): shown while no local miner exists.
 * The button opens the regular setup flow (scan / manual connect) —
 * on site, known miners usually appear by themselves via auto-connect,
 * so this is the manual path for brand-new machines.
 */
@Composable
private fun HomeAddMinerCard(onOpenSetup: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add a miner",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "On the miner's Wi-Fi, your known miners connect by themselves. Add a brand-new one by scan or IP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOpenSetup,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add Miner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FleetHeaderCard(
    mergedMiners: List<MergedMinerEntry>,
    cloudConfig: CloudSyncConfig,
    nowMs: Long
) {
    val rows = remember(mergedMiners, nowMs) {
        FleetSorting.sortEntries(mergedMiners, nowMs)
    }
    val totalTh = FleetSorting.fleetTotalTh(rows)
    val onlineCount = rows.count { it.isOnline }
    val relayOn = cloudConfig.isSyncEnabled
    val accountLabel = when {
        cloudConfig.userName.isNotBlank() -> "${cloudConfig.userName} · Relay ${if (relayOn) "ON" else "OFF"}"
        relayOn -> "Relay ON · no user name set"
        else -> "Relay OFF"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FLEET HASHRATE · $onlineCount/${rows.size} ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (totalTh > 0.0) String.format(Locale.US, "%.1f", totalTh) else "--",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = LocalDataFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TH/s",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            Surface(
                color = if (relayOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(if (relayOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (relayOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = accountLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = LocalDataFontFamily.current,
                        fontWeight = FontWeight.Bold,
                        color = if (relayOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(alert: MinerAlert) {
    val haptic = LocalHapticFeedback.current
    val (bg, fg, icon) = when (alert.severity) {
        com.example.antminerconnectiontest.domain.alerts.AlertSeverity.CRITICAL ->
            Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, Icons.Default.Error)
        com.example.antminerconnectiontest.domain.alerts.AlertSeverity.WARNING ->
            Triple(WarningAmber.copy(alpha = 0.15f), WarningAmber, Icons.Default.Warning)
        com.example.antminerconnectiontest.domain.alerts.AlertSeverity.INFO ->
            Triple(ElectricBlue.copy(alpha = 0.15f), ElectricBlue, Icons.Default.Info)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${alert.title}: ${alert.description}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
