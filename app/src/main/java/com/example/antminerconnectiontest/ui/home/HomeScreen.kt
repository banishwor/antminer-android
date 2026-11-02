package com.example.antminerconnectiontest.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.HashrateRaceCard
import com.example.antminerconnectiontest.ui.components.MinerDataState
import com.example.antminerconnectiontest.ui.components.MinerDataStateCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    profile: MinerProfile,
    savedProfiles: List<MinerProfile>,
    remoteMiners: List<RemoteMinerSnapshot> = emptyList(),
    selectedRemoteMiner: RemoteMinerSnapshot? = null,
    minerHashrates: Map<String, Double> = emptyMap(),
    data: MinerDashboardData?,
    alerts: List<MinerAlert>,
    themeMode: ThemeMode,
    isRefreshing: Boolean,
    dashboardError: String? = null,
    onRetry: (() -> Unit)? = null,
    onManualRefresh: () -> Unit,
    onOpenMinerSwitcher: () -> Unit,
    onSelectMinerProfile: (MinerProfile) -> Unit,
    onSelectRemoteMiner: (RemoteMinerSnapshot) -> Unit = {},
    onClearRemoteMinerView: () -> Unit = {},
    onAddNewMiner: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val summary = data?.summary
    val stats = data?.stats
    val isOnline = data?.isOnline == true
    val isExplicitRemote = selectedRemoteMiner != null

    // If explicit remote selected -> show it; else if local is offline -> show first available cloud miner
    val effectiveRemote = when {
        selectedRemoteMiner != null -> selectedRemoteMiner
        !isOnline && remoteMiners.isNotEmpty() -> remoteMiners.first()
        else -> null
    }

    val isCloudMode = effectiveRemote != null
    val isStaleCloud = isCloudMode && effectiveRemote?.isStale == true
    val effectiveRetry = onRetry ?: onManualRefresh

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Compute active hashrate values (supports 2-minute stale watchdog)
    val hashrate = if (isCloudMode) {
        effectiveRemote!!.effectiveHashrate5s
    } else when {
        (summary?.hashrate5s ?: 0.0) > 0.0 -> summary!!.hashrate5s
        (stats?.chains?.sumOf { it.realHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.realHashrateTh }
        else -> summary?.hashrateAvg ?: 0.0
    }

    val avgRate = if (isCloudMode) {
        effectiveRemote!!.effectiveAvgHashrate
    } else when {
        (summary?.hashrateAvg ?: 0.0) > 0.0 -> summary!!.hashrateAvg
        (summary?.hashrate30m ?: 0.0) > 0.0 -> summary!!.hashrate30m
        else -> hashrate
    }

    val idealRate = when {
        (summary?.hashrateIdeal ?: 0.0) > 0.0 -> summary!!.hashrateIdeal
        (stats?.chains?.sumOf { it.idealHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.idealHashrateTh }
        else -> 120.0
    }

    val displayTemp = if (isCloudMode) effectiveRemote!!.maxTemp else (stats?.maxChipTemp ?: 0)
    val displayFan = if (isCloudMode) effectiveRemote!!.maxFan else (stats?.maxFanRpm ?: 0)

    // 100% Non-Scrollable Layout Container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Miner Header Bar (Compact & Static)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(if (isStaleCloud) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else if (isCloudMode) CyberCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline)
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
                                .background(if (isStaleCloud) MaterialTheme.colorScheme.error else if (isCloudMode) CyberCyan else if (isOnline) AntminerEmerald else MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCloudMode) effectiveRemote!!.effectiveDisplayName else profile.effectiveName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!isCloudMode && profile.workerName.isNotBlank() && profile.nickname.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = profile.workerName,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCloudMode) "Cloud Owner: ${effectiveRemote!!.user}" else profile.host,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (isCloudMode) CyberCyan else MaterialTheme.colorScheme.primary
                                )
                                if (!isCloudMode && profile.location.isNotBlank() && profile.location != "Default Location") {
                                    Text(
                                        text = " • ${profile.location}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Header Action Buttons (Theme Toggle + Refresh)
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

            // Cloud Mode Banner (Displays when viewing cloud miner)
            if (isCloudMode) {
                Surface(
                    color = if (isStaleCloud) MaterialTheme.colorScheme.errorContainer else CyberCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(if (isStaleCloud) MaterialTheme.colorScheme.error else CyberCyan.copy(alpha = 0.4f))
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
                                imageVector = if (isStaleCloud) Icons.Default.Warning else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (isStaleCloud) MaterialTheme.colorScheme.error else CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (isStaleCloud) "⚠️ CLOUD SIGNAL LOST (>2m)" else "CLOUD FARM VIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStaleCloud) MaterialTheme.colorScheme.error else CyberCyan
                                )
                                Text(
                                    text = if (isStaleCloud) "Last seen ${effectiveRemote!!.timeAgoFormatted} (${effectiveRemote.user}) • Check power" else "Viewing ${effectiveRemote!!.user}'s live feed • ${effectiveRemote.timeAgoFormatted}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isExplicitRemote) {
                            TextButton(
                                onClick = onClearRemoteMinerView,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Surface(
                                color = if (isStaleCloud) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isStaleCloud) "OFFLINE" else "CLOUD LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStaleCloud) MaterialTheme.colorScheme.error else CyberCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1b. Loading / error state for the local miner (never in cloud mode):
            // a first fetch in flight shows progress; a failed fetch shows the
            // error with Retry instead of a fake "dead miner" dashboard.
            if (!isCloudMode) {
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
            AnimatedVisibility(visible = alerts.isNotEmpty() && !isCloudMode) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alerts.take(2).forEach { alert ->
                        AlertBanner(alert = alert)
                    }
                }
            }

            // 3. Hero Hashrate Banner (Breathing Gradient Border with isolated recomposition)
            HeroHashrateCard(
                hashrate = hashrate,
                avgRate = avgRate,
                idealRate = idealRate,
                isOnline = isOnline,
                isCloudMode = isCloudMode,
                isStaleCloud = isStaleCloud
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
                    value = if (isCloudMode) "0" else "$totalHw",
                    icon = Icons.Default.BugReport,
                    modifier = Modifier.weight(1f),
                    accentColor = if (totalHw > 100) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                MetricCard(
                    title = "Status",
                    value = if (isCloudMode) effectiveRemote!!.status else (summary?.formattedUptime ?: "Online"),
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f),
                    accentColor = ElectricBlue
                )
            }

            // 5. Live Hashrate Race Leaderboard (Only renders when 2+ miners exist!)
            HashrateRaceCard(
                activeProfile = profile,
                savedProfiles = savedProfiles,
                remoteMiners = remoteMiners,
                selectedRemoteMinerId = selectedRemoteMiner?.id,
                minerHashrates = minerHashrates,
                activeHashrateTh = hashrate,
                onSelectProfile = onSelectMinerProfile,
                onSelectRemoteMiner = onSelectRemoteMiner,
                onAddNewMiner = onAddNewMiner
            )
        }

        // 6. Footer Sync Timestamp (Always at bottom)
        val footerText = if (isCloudMode) {
            "Cloud Sync: ${effectiveRemote!!.lastUpdated.ifBlank { timeFormatter.format(Date()) }} • Cloud Mode"
        } else if (data != null) {
            "Last sync: ${timeFormatter.format(Date(data.lastUpdated))} • Polling 15s"
        } else {
            "Connecting..."
        }
        Text(
            text = footerText,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
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

@Composable
private fun HeroHashrateCard(
    hashrate: Double,
    avgRate: Double,
    idealRate: Double,
    isOnline: Boolean,
    isCloudMode: Boolean,
    isStaleCloud: Boolean,
    modifier: Modifier = Modifier
) {
    // Isolated animation scope - prevents parent HomeScreen from recomposing on every frame!
    val infiniteTransition = rememberInfiniteTransition(label = "HeroGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isStaleCloud) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                else if (isCloudMode) CyberCyan.copy(alpha = glowAlpha)
                else if (isOnline) AntminerEmerald.copy(alpha = glowAlpha)
                else MaterialTheme.colorScheme.outline
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCloudMode) "REMOTE HASHRATE (RT)" else "REAL-TIME HASHRATE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )

                Surface(
                    color = if (isStaleCloud) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else if (isCloudMode) CyberCyan.copy(alpha = 0.15f) else if (isOnline) AntminerEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isStaleCloud) "NO SIGNAL" else if (isCloudMode) "CLOUD LIVE" else if (isOnline) "ONLINE" else "OFFLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStaleCloud) MaterialTheme.colorScheme.error else if (isCloudMode) CyberCyan else if (isOnline) AntminerEmerald else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (hashrate > 0.0) String.format(Locale.US, "%.2f", hashrate) else "--",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "TH/s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCloudMode) CyberCyan else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary rates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Avg Hashrate",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (avgRate > 0.0) String.format(Locale.US, "%.1f TH/s", avgRate) else "--",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ideal Target",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f TH/s", idealRate),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

