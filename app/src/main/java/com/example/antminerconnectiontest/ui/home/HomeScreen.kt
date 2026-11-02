package com.example.antminerconnectiontest.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.alerts.MinerAlert
import com.example.antminerconnectiontest.domain.model.AddMinerCardVisibility
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.FleetSorting
import com.example.antminerconnectiontest.domain.model.HeroSummary
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.NeedsPasswordMachine
import com.example.antminerconnectiontest.domain.model.PhoneNicknames
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.FleetListCard
import com.example.antminerconnectiontest.ui.components.MinerDataState
import com.example.antminerconnectiontest.ui.components.MinerDataStateCard
import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import com.example.antminerconnectiontest.ui.components.SourceChip
import com.example.antminerconnectiontest.ui.components.StatusDot
import com.example.antminerconnectiontest.ui.components.localMinerDetailUi
import com.example.antminerconnectiontest.ui.components.toMinerDetailUi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * THE Home — the only one (v0.1.9), rebuilt as Layout 4 "Dashboard
 * Split" in v0.2.0 (user-approved mockups):
 *
 *   fixed:  fleet header strip → selected-machine HERO → alerts/states
 *   scroll: the ranked fleet list (highest hashrate first, offline last)
 *
 * The hero is a SUMMARY ONLY (strict strip): nickname / worker, model,
 * the LIVE-REMOTE chip, the cloud "last updated" line, status, the big
 * real-time hashrate with the average beneath, uptime, and ONE health
 * indicator. Boards & fans live on the Hardware tab, pool & shares on
 * the Pools tab, logs on the Logs tab — none of that data is even
 * modelled here (see HeroSummary).
 *
 * There is always ONE selected machine: the active local profile, else
 * the explicitly selected fleet machine, else the first machine in the
 * ranked fleet (first online, else first overall). Tapping a fleet row
 * re-selects, and the hero + every tab rebind to the same machine.
 *
 * Setup CONTENT (cloud setup card, Add Miner card) still lives inside
 * this same Home for fresh installs — never a separate first-run flow.
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
    needsPasswordMachines: List<NeedsPasswordMachine> = emptyList(),
    onNeedsPasswordTap: (NeedsPasswordMachine) -> Unit = {},
    /** Cloud setup card wiring (same ViewModel calls the Device tab uses). */
    onSaveCloudConfig: (webhookUrl: String, userName: String, relayEnabled: Boolean) -> Unit = { _, _, _ -> },
    onTestCloudConnection: (webhookUrl: String, userName: String) -> Unit = { _, _ -> },
    isTestingCloud: Boolean = false,
    cloudTestMessage: String? = null,
    isCloudConnected: Boolean = false
) {
    // Remote display is ALWAYS a deliberate selection (v0.1.9 rule,
    // unchanged): no silent swap to a cloud feed. The one designed
    // precedence: when the selected snapshot is the SAME machine as the
    // active local profile and live local data for it has been fetched,
    // the local data overwrites the snapshot for display — it is
    // fresher. While local data is unavailable, the snapshot shows.
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
    val localWinsForSelectedRemote = selectedRemoteMiner != null &&
        profile != null &&
        selectedEntry?.localProfile?.id == profile.id &&
        data?.profileId == profile.id &&
        data?.isOnline == true
    val effectiveRemote = if (localWinsForSelectedRemote) null else selectedRemoteMiner
    val isSnapshotSelection = effectiveRemote != null
    val effectiveRetry = onRetry ?: onManualRefresh

    // Tick every 5 s so freshness/stale indicators can appear even when
    // no new state arrives (e.g. polling silently stalled after sleep).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(5_000L)
        }
    }
    // Older than ~2 poll cycles (2 x 15 s) without a refresh landing.
    val isStaleLocal = !isSnapshotSelection && profile != null && data != null && nowMs - data.lastUpdated > 30_000L

    val sortedRows = remember(mergedMiners, nowMs) {
        FleetSorting.sortEntries(mergedMiners, nowMs)
    }
    // Default selection (approved model): with no explicit selection the
    // hero shows the first machine of the ranked fleet — online first by
    // hashrate, offline last — until the user taps a row (or the
    // ViewModel's default-selection settles the same choice).
    val defaultRow = sortedRows.firstOrNull()

    val selectedDisplayName = if (selectedRemoteMiner != null) {
        selectedEntry?.displayName
            ?: PhoneNicknames.displayName(nicknameOverrides, selectedEntry?.localProfile, selectedRemoteMiner)
    } else if (profile != null) {
        activeEntry?.displayName ?: PhoneNicknames.displayName(nicknameOverrides, profile, null)
    } else {
        ""
    }

    val heroDetail: MinerDetailUi? = when {
        effectiveRemote != null -> effectiveRemote.toMinerDetailUi(displayName = selectedDisplayName)
        profile != null -> localMinerDetailUi(profile, data, selectedDisplayName)
        defaultRow?.entry?.remoteSnapshot != null ->
            defaultRow.entry.remoteSnapshot!!.toMinerDetailUi(displayName = defaultRow.displayName)
        defaultRow?.entry?.localProfile != null ->
            localMinerDetailUi(defaultRow.entry.localProfile!!, null, defaultRow.displayName)
        else -> null
    }
    val heroSummary = heroDetail?.let { HeroSummary.from(it) }
    val heroEntry = when {
        effectiveRemote != null -> selectedEntry
        profile != null -> activeEntry
        else -> defaultRow?.entry
    }
    val heroSnapshot = effectiveRemote
        ?: defaultRow?.entry?.remoteSnapshot?.takeIf { profile == null && selectedRemoteMiner == null }

    // The "Add a miner" card rule (v0.2.3, user-approved): it shows
    // ONLY when there is no miner at all — no saved local profiles
    // AND no merged fleet entries (local or cloud). See
    // AddMinerCardVisibility for the pure, tested rule.
    val showAddMinerCard = AddMinerCardVisibility.shouldShow(
        savedProfileCount = savedProfiles.size,
        mergedFleetCount = mergedMiners.size
    )

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Fixed region: fleet strip, hero, state cards ──────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FleetHeaderCard(
                mergedMiners = mergedMiners,
                cloudConfig = cloudConfig,
                nowMs = nowMs,
                isDark = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            )

            // Needs-password cards (auto-connect, v0.1.9): known machines
            // on this Wi-Fi that rejected the phone's credentials.
            // Tapping one opens the credential entry with the IP
            // prefilled — enter it once, auto-connect takes over.
            needsPasswordMachines.forEach { machine ->
                NeedsPasswordCard(machine = machine, onClick = { onNeedsPasswordTap(machine) })
            }

            if (heroSummary != null) {
                SelectedMachineHero(
                    summary = heroSummary,
                    isStaleLocal = isStaleLocal,
                    themeMode = themeMode,
                    isRefreshing = isRefreshing,
                    onToggleTheme = onToggleTheme,
                    onManualRefresh = onManualRefresh,
                    onEditNickname = heroEntry?.let { entry -> { onEditNickname(entry) } }
                )
            } else {
                // Setup CONTENT for a fresh install — inside the same
                // Home, never a separate first-run flow (v0.1.9): the
                // cloud setup card while no relay user name is set, and
                // the Add Miner card while there is no miner at all
                // (v0.2.3 rule — see showAddMinerCard).
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
                if (showAddMinerCard) {
                    HomeAddMinerCard(onOpenSetup = onOpenSetup)
                }
            }

            // Loading / error state for the local miner (never for a
            // snapshot selection): a first fetch in flight shows
            // progress; a failed fetch shows the error with Retry
            // instead of a fake "dead miner" dashboard.
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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

            // Active alert banners (local monitoring only, as before).
            AnimatedVisibility(visible = alerts.isNotEmpty() && !isSnapshotSelection) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alerts.take(2).forEach { alert ->
                        AlertBanner(alert = alert)
                    }
                }
            }
        }

        // ── Scrolling region: the ranked fleet list under the hero ────
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onManualRefresh,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FleetListCard(
                    mergedMiners = mergedMiners,
                    activeProfile = profile,
                    selectedRemoteMinerId = selectedRemoteMiner?.id,
                    onSelectProfile = onSelectMinerProfile,
                    onSelectRemoteMiner = onSelectRemoteMiner,
                    onAddNewMiner = onOpenSetup,
                    onEditNickname = onEditNickname
                )

                // Empty fleet with cloud configured: say where miners
                // come from instead of leaving a blank page.
                if (mergedMiners.isEmpty() && cloudConfig.userName.isNotBlank()) {
                    Text(
                        text = "No miners yet. Machines appear here once a device at a site uploads them, when auto-connect finds them on this Wi-Fi, or when you add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // The bottom Add Miner card follows the same v0.2.3
                // rule: with any miner in the fleet (local or cloud)
                // it stays hidden; it can only appear here when the
                // fleet is empty but a kept selection still gives the
                // hero something to show. Adding is otherwise always
                // reachable via the fleet header and the Device tab.
                if (heroSummary != null && showAddMinerCard) {
                    HomeAddMinerCard(onOpenSetup = onOpenSetup)
                }

                // Footer sync line.
                val footerText = when {
                    heroSnapshot != null ->
                        "Cloud sync: ${heroSnapshot.lastUpdated.ifBlank { timeFormatter.format(Date()) }} • via relay"
                    data != null && profile != null ->
                        "Last sync: ${timeFormatter.format(Date(data.lastUpdated))} • Polling 15s"
                    profile != null -> "Connecting..."
                    else -> "No miner connected"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isStaleLocal) {
                        StalePill()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = footerText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStaleLocal) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StalePill() {
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
}

/**
 * The selected-machine HERO (v0.2.0, Layout 4): summary only — see
 * [HeroSummary] for the exact, test-enforced field set. Identity and
 * actions on top, the big real-time hashrate in the middle, and a
 * single status / uptime / health row at the bottom.
 */
@Composable
private fun SelectedMachineHero(
    summary: HeroSummary,
    isStaleLocal: Boolean,
    themeMode: ThemeMode,
    isRefreshing: Boolean,
    onToggleTheme: () -> Unit,
    onManualRefresh: () -> Unit,
    onEditNickname: (() -> Unit)?
) {
    val dataFont = LocalDataFontFamily.current
    // Liquid Glass (v0.3.0): the hero is one of the two surfaces that
    // get the full treatment — translucent fill (from the glass
    // colour scheme), the bright specular edge, and on capable
    // devices a blurred-wallpaper backdrop inside the panel. On
    // glass-lite and in the Violet style this is the plain card.
    val glass = LocalGlassSettings.current
    val heroIsDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (glass.isGlass) BorderStroke(1.dp, glassEdgeBrush(heroIsDark)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
      Box {
        GlassBackdrop()
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                StatusDot(isOnline = summary.isOnline, size = 10)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val workerLine = summary.workerName.takeIf {
                        it.isNotBlank() && !it.equals(summary.displayName, ignoreCase = true)
                    }
                    if (workerLine != null) {
                        Text(
                            text = workerLine,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = dataFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (summary.modelName.isNotBlank()) {
                        Text(
                            text = summary.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    summary.updatedLine?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    SourceChip(isRemote = summary.isRemote)
                    Row {
                        if (onEditNickname != null) {
                            IconButton(onClick = onEditNickname, modifier = Modifier.size(32.dp)) {
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

            Column {
                Text(
                    text = "REAL-TIME HASHRATE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (summary.hashrateTh > 0.0) String.format(Locale.US, "%.1f", summary.hashrateTh) else "--",
                        style = MaterialTheme.typography.displayLarge,
                        fontFamily = dataFont,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TH/s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    text = "Avg " + if (summary.avgHashrateTh > 0.0) {
                        String.format(Locale.US, "%.1f TH/s", summary.avgHashrateTh)
                    } else {
                        "--"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = dataFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (summary.isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = summary.statusText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isOnline) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (summary.uptimeLabel.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = summary.uptimeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = dataFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // The ONE health indicator the hero may show.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (dotColor, healthLabel) = when (summary.health) {
                        HeroSummary.HealthState.HEALTHY ->
                            MaterialTheme.colorScheme.primary to "Healthy"
                        HeroSummary.HealthState.ISSUES ->
                            MaterialTheme.colorScheme.tertiary to "Check miner"
                        HeroSummary.HealthState.UNKNOWN ->
                            MaterialTheme.colorScheme.onSurfaceVariant to "Health unknown"
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = healthLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = dotColor
                    )
                }
                if (isStaleLocal) {
                    StalePill()
                }
            }
        }
      }
    }
}

/** A known machine on this Wi-Fi that needs its password typed once (auto-connect). */
@Composable
private fun NeedsPasswordCard(
    machine: NeedsPasswordMachine,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        text = "See your miners from anywhere: enter the user name your sites upload as. You can also do this later under Settings.",
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
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
 * Add Miner as Home CONTENT (v0.1.9): the manual path for brand-new
 * machines. The button opens the regular setup flow (scan / manual
 * connect) — on site, known miners usually appear by themselves via
 * auto-connect.
 */
@Composable
private fun HomeAddMinerCard(onOpenSetup: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add Miner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/**
 * The compact fleet strip (v0.2.0): total hashrate of the online fleet
 * and the online count on the left, the relay account chip on the
 * right — who this phone relays as, and whether the relay is on.
 */
@Composable
private fun FleetHeaderCard(
    mergedMiners: List<MergedMinerEntry>,
    cloudConfig: CloudSyncConfig,
    nowMs: Long,
    isDark: Boolean = false
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

    // Liquid Glass (v0.3.0): specular edge like the hero's; the fill
    // translucency comes from the glass colour scheme. (No backdrop
    // layer here — live blur is scoped to the hero + tab bar only.)
    val glass = LocalGlassSettings.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (glass.isGlass) BorderStroke(1.dp, glassEdgeBrush(isDark)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (totalTh > 0.0) String.format(Locale.US, "%.1f", totalTh) else "--",
                        style = MaterialTheme.typography.headlineMedium,
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
                Text(
                    text = "Fleet · $onlineCount online",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (relayOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
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
        shape = RoundedCornerShape(12.dp),
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
