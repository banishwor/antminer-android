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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.AntminerEmerald
import com.example.antminerconnectiontest.theme.CyberCyan
import com.example.antminerconnectiontest.theme.ThemeMode
import com.example.antminerconnectiontest.ui.AppUiState
import com.example.antminerconnectiontest.ui.DashboardViewModel
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
    val isCloudConfigured = cloudConfig.isSyncEnabled && cloudConfig.userName.isNotBlank()
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
                    mergedMiners.forEach { entry ->
                        RemoteMinerRow(
                            entry = entry,
                            onClick = {
                                entry.remoteSnapshot?.let { viewModel.selectRemoteMiner(it) }
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
                            isSyncEnabled = true,
                            isAutoUploadEnabled = cloudConfig.isAutoUploadEnabled,
                            isDownloadRemoteEnabled = true,
                            isBackgroundServiceEnabled = false
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
    onClick: () -> Unit
) {
    val snapshot = entry.remoteSnapshot ?: return
    val isStale = snapshot.isStale
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
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberCyan
                )
                Text(
                    text = "Updated ${snapshot.timeAgoFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isStale) "Offline" else if (entry.displayHashrateTh > 0.0) String.format(Locale.US, "%.1f TH/s", entry.displayHashrateTh) else "0.0 TH/s",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
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
                    Text(
                        text = snapshot.effectiveDisplayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cloud Owner: ${snapshot.user} • ${snapshot.ip}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan
                    )
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
                    fontFamily = FontFamily.Monospace,
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
                DetailMetric("Avg", if (snapshot.effectiveAvgHashrate > 0.0) String.format(Locale.US, "%.1f TH/s", snapshot.effectiveAvgHashrate) else "--")
                DetailMetric("Chip Temp", if (snapshot.maxTemp > 0) "${snapshot.maxTemp}°C" else "--")
                DetailMetric("Max Fan", if (snapshot.maxFan > 0) "${snapshot.maxFan} RPM" else "--")
                DetailMetric("Status", snapshot.effectiveStatus)
            }

            Text(
                text = "Last updated ${snapshot.timeAgoFormatted}${if (snapshot.isStale) " — signal lost (>2m), values may be stale" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
