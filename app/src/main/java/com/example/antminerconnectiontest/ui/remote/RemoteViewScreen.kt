package com.example.antminerconnectiontest.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.FleetSorting
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.RemoteChainSnapshot
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.TelemetryCodec
import com.example.antminerconnectiontest.theme.AntminerEmerald
import com.example.antminerconnectiontest.theme.CyberCyan
import com.example.antminerconnectiontest.theme.LocalDataFontFamily
import com.example.antminerconnectiontest.theme.ThemeMode
import com.example.antminerconnectiontest.theme.WarningAmber
import com.example.antminerconnectiontest.ui.AppUiState
import com.example.antminerconnectiontest.ui.DashboardViewModel
import com.example.antminerconnectiontest.ui.components.AsicChipMatrix
import java.util.Locale

/**
 * The Remote View home for a user who started remote (and has no local
 * miner selected): the whole cloud fleet in one list, one entry per
 * machine (merged — see MinerMerge), tap a machine for its snapshot.
 *
 * When cloud relay is not configured yet this screen hosts the existing
 * cloud setup fields (user name + test/save, same ViewModel calls the
 * Device tab uses) — choosing Remote never invents a second backend.
 * Local miners can be added from here at any time via "Add Local Miner";
 * starting remote is not a lock-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteViewScreen(
    viewModel: DashboardViewModel,
    uiState: AppUiState,
    mergedMiners: List<MergedMinerEntry>
) {
    val cloudConfig = uiState.cloudConfig
    // Viewing remote machines needs only a user name — it is not gated
    // by the Relay switch (which controls uploading, not viewing).
    val isCloudConfigured = cloudConfig.userName.isNotBlank()
    val selected = uiState.selectedRemoteMiner

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "REMOTE VIEW",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cloud farm — all your miners, anywhere",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleThemeMode) {
                        Icon(
                            imageVector = if (uiState.themeMode == ThemeMode.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isCloudConfigured) {
                CloudSetupCard(viewModel = viewModel, uiState = uiState)
            } else {
                // Status + refresh row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isCloudConnected) "Connected as ${cloudConfig.userName}" else "Cloud unreachable — showing last data",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = viewModel::syncCloudNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh remote miners",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selected != null) {
                    RemoteMinerDetailCard(
                        snapshot = selected,
                        onBack = viewModel::clearRemoteMinerView
                    )
                } else if (mergedMiners.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No remote miners found yet. They appear here once a device at the site uploads them, or after the next sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Your Miners (${mergedMiners.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sorted by hashrate · offline last",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val nowMs = System.currentTimeMillis()
                    FleetSorting.sortEntries(mergedMiners, nowMs).forEach { row ->
                        RemoteMinerRow(
                            entry = row.entry,
                            isOnline = row.isOnline,
                            lastUpdatedLabel = if (row.isOnline) {
                                FleetSorting.formatUpdatedShort(row.lastUpdatedEpochMs, nowMs)
                            } else {
                                FleetSorting.formatLastUpdated(row.lastUpdatedEpochMs, nowMs)
                            },
                            onClick = {
                                row.entry.remoteSnapshot?.let { viewModel.selectRemoteMiner(it) }
                            }
                        )
                    }
                }
            }

            // Not a lock-in: adding local miners stays one tap away.
            Card(
                shape = RoundedCornerShape(14.dp),
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
                            text = "At the miner location?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You can still add local miners when you are on the site Wi-Fi — Remote View does not lock you out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::openLocalSetup,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add Local Miner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSetupCard(
    viewModel: DashboardViewModel,
    uiState: AppUiState
) {
    val cloudConfig = uiState.cloudConfig
    var userNameInput by remember(cloudConfig.userName) { mutableStateOf(cloudConfig.userName) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(CyberCyan.copy(alpha = 0.5f))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Set up Cloud Relay",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Remote View reads your miners from the shared cloud sheet. Enter the user name your sites upload as.",
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

            if (!uiState.cloudTestMessage.isNullOrBlank()) {
                Surface(
                    color = if (uiState.isCloudConnected) AntminerEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.cloudTestMessage!!,
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
                    onClick = { viewModel.testCloudConnection(cloudConfig.webhookUrl, userNameInput.trim()) },
                    enabled = !uiState.isTestingCloud && userNameInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isTestingCloud) {
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
                        viewModel.updateCloudConfig(
                            webhookUrl = cloudConfig.webhookUrl,
                            userName = userNameInput.trim(),
                            // Setting up viewing must not silently flip
                            // the Relay switch — keep its current state.
                            relayEnabled = cloudConfig.isSyncEnabled
                        )
                    },
                    enabled = userNameInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save & View Remote", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RemoteMinerRow(
    entry: MergedMinerEntry,
    isOnline: Boolean = true,
    lastUpdatedLabel: String = "",
    onClick: () -> Unit
) {
    val snapshot = entry.remoteSnapshot ?: return
    val isStale = !isOnline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isStale) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else CyberCyan.copy(alpha = 0.4f))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "☁️ ${snapshot.user} • ${snapshot.ip}",
                    fontFamily = LocalDataFontFamily.current,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberCyan
                )
                Text(
                    text = lastUpdatedLabel.ifBlank {
                        if (isStale) "Last updated —" else "Updated ${snapshot.timeAgoFormatted}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isStale) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isStale) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isStale) "Offline" else if (entry.displayHashrateTh > 0.0) String.format(Locale.US, "%.1f TH/s", entry.displayHashrateTh) else "0.0 TH/s",
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalDataFontFamily.current,
                    color = if (isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = snapshot.effectiveStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RemoteMinerDetailCard(
    snapshot: RemoteMinerSnapshot,
    onBack: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (snapshot.isStale) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else CyberCyan.copy(alpha = 0.6f))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = snapshot.effectiveDisplayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = CyberCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "REMOTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Cloud Owner: ${snapshot.user} • ${snapshot.ip}",
                        fontFamily = LocalDataFontFamily.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan
                    )
                    // Model / firmware ride the extended telemetry
                    // (v0.1.6); rows written earlier simply omit the line.
                    val modelLine = listOf(snapshot.model, snapshot.firmware)
                        .filter { it.isNotBlank() }.joinToString(" • ")
                    if (modelLine.isNotBlank()) {
                        Text(
                            text = modelLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onBack) {
                    Text("← All miners", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (snapshot.effectiveHashrate5s > 0.0) String.format(Locale.US, "%.2f", snapshot.effectiveHashrate5s) else "--",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = LocalDataFontFamily.current,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "TH/s (remote)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("30m", snapshot.hashrate30m?.let { String.format(Locale.US, "%.1f TH/s", it) } ?: "--")
                DetailMetric("Avg", if (snapshot.effectiveAvgHashrate > 0.0) String.format(Locale.US, "%.1f TH/s", snapshot.effectiveAvgHashrate) else "--")
                DetailMetric("Ideal", snapshot.idealHashrate?.let { String.format(Locale.US, "%.1f TH/s", it) } ?: "--")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("Uptime", snapshot.formattedUptime ?: "--")
                DetailMetric("HW Errors", snapshot.hwErrors?.toString() ?: "--")
                DetailMetric("Status", snapshot.effectiveStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Chip and PCB temps are separate readings since v0.1.6;
                // the legacy combined maxTemp is the fallback for the
                // chip side on rows written before then.
                val chipTemp = snapshot.maxChipTemp ?: snapshot.maxTemp.takeIf { it > 0 }
                val maxFanRpm = snapshot.fanRpms.maxOrNull() ?: snapshot.maxFan.takeIf { it > 0 }
                DetailMetric("Chip Temp", chipTemp?.let { "$it°C" } ?: "--")
                DetailMetric("PCB Temp", snapshot.maxPcbTemp?.let { "$it°C" } ?: "--")
                DetailMetric("Max Fan", maxFanRpm?.let { "$it RPM" } ?: "--")
            }

            Text(
                text = "Updated ${snapshot.timeAgoFormatted} · via relay${if (snapshot.isStale) " — signal lost (>2m), values may be stale" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Fans (v0.1.6 telemetry) ────────────────────────────
            if (snapshot.fanRpms.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SectionTitle("Fans")
                if (snapshot.slowFanCount > 0) {
                    // Same threshold and wording as the local alert:
                    // ThresholdAlertEngine's "Low Fan Speed Warning".
                    Surface(
                        color = WarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Low Fan Speed Warning: ${snapshot.slowFanCount} fan(s) spinning below ${TelemetryCodec.SLOW_FAN_RPM} RPM.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                snapshot.fanRpms.forEachIndexed { index, rpm ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fan ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%,d RPM", rpm),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = LocalDataFontFamily.current,
                            color = if (rpm < TelemetryCodec.SLOW_FAN_RPM) WarningAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Health flags (v0.1.6 telemetry) ────────────────────
            snapshot.health?.let { health ->
                SectionTitle("Health")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HealthChip("Hashrate", health.rateOk)
                    HealthChip("Network", health.networkOk)
                    HealthChip("Fans", health.fansOk)
                    HealthChip("Temp", health.tempOk)
                }
            }

            // ── Primary pool (v0.1.6 telemetry) ────────────────────
            if (snapshot.poolUrl.isNotBlank() || snapshot.poolWorker.isNotBlank() || snapshot.poolStatus.isNotBlank()) {
                SectionTitle("Primary Pool")
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (snapshot.poolUrl.isNotBlank()) {
                            Text(
                                text = snapshot.poolUrl,
                                fontFamily = LocalDataFontFamily.current,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DetailMetric("Worker", snapshot.poolWorker.ifBlank { "--" })
                            DetailMetric(
                                "Status",
                                snapshot.poolStatus.ifBlank { "--" }
                            )
                            DetailMetric("Accepted", snapshot.acceptedShares?.toString() ?: "--")
                            DetailMetric("Rejected", snapshot.rejectedShares?.toString() ?: "--")
                        }
                    }
                }
            }

            // ── Hash boards (v0.1.6 telemetry) ─────────────────────
            if (snapshot.chains.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SectionTitle("Hash Boards")
                snapshot.chains.forEach { chain ->
                    RemoteChainCard(chain = chain)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Logs are never uploaded (by design — they are large and
            // change constantly), and rows written before v0.1.6 carry
            // no chain detail: show those sections as what they are
            // instead of inventing values for them.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (snapshot.chains.isEmpty()) {
                    UnavailableSection("Chain details", "ASIC status, per-board temps and frequencies")
                }
                UnavailableSection("Logs", "Miner event and kernel logs")
                Text(
                    text = "Available on location only — connect on the miner's Wi-Fi to view.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnavailableSection(title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun HealthChip(label: String, ok: Boolean) {
    Surface(
        color = if (ok) AntminerEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: ${if (ok) "OK" else "Issue"}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) AntminerEmerald else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * One hash board from the relayed chainsJson — the remote counterpart
 * of the local dashboard's ChainCard summary: rate vs ideal, per-board
 * chip/PCB maxima, HW errors, the ASIC chip matrix, and an explicit
 * dead/missing chip warning derived from the ASIC status string.
 */
@Composable
private fun RemoteChainCard(chain: RemoteChainSnapshot) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CHAIN ${chain.chainIndex}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${chain.asicCount} ASICs • ${chain.frequencyMhz.toInt()} MHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.2f TH/s", chain.realHashrateTh),
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalDataFontFamily.current,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("Chip Max", "${chain.chipMaxTemp}°C")
                DetailMetric("PCB Max", "${chain.pcbMaxTemp}°C")
                DetailMetric("HW Errors", "${chain.hwErrors}")
                DetailMetric("Ideal", String.format(Locale.US, "%.1f TH/s", chain.idealHashrateTh))
            }

            if (chain.deadChips > 0 || chain.missingChips > 0) {
                val parts = mutableListOf<String>()
                if (chain.deadChips > 0) parts += "${chain.deadChips} dead chip(s)"
                if (chain.missingChips > 0) parts += "${chain.missingChips} missing chip(s)"
                Text(
                    text = "⚠ " + parts.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (chain.asicStatusString.isNotBlank()) {
                AsicChipMatrix(
                    asicStatusString = chain.asicStatusString,
                    totalChips = chain.asicCount
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("Chip Min", "${chain.chipMinTemp}°C")
                DetailMetric("PCB Min", "${chain.pcbMinTemp}°C")
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalDataFontFamily.current,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
