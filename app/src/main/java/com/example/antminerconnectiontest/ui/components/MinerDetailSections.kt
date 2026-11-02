package com.example.antminerconnectiontest.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerHealthFlags
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteChainSnapshot
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.TelemetryCodec
import com.example.antminerconnectiontest.domain.model.toRemoteChain
import com.example.antminerconnectiontest.theme.AntminerEmerald
import com.example.antminerconnectiontest.theme.CyberCyan
import com.example.antminerconnectiontest.theme.ElectricBlue
import com.example.antminerconnectiontest.theme.LocalDataFontFamily
import com.example.antminerconnectiontest.theme.WarningAmber
import java.util.Locale

/**
 * THE miner detail presentation (v0.1.7) — one machine, one layout,
 * whether the data arrived live from the miner on the local network or
 * from a cloud relay snapshot. Both sources are first normalised into
 * [MinerDetailUi]; [MinerDetailContent] then renders the same sections
 * in the same order with the same components:
 *
 * header → hero hashrate → metric cards → session metrics → hash boards
 * → fans → primary pool → health → (remote only: on-location sections).
 *
 * The only honest differences between LIVE and REMOTE are the source
 * chip, the "last updated" line, and sections a snapshot genuinely
 * cannot carry (logs), which stay visible as greyed placeholders so the
 * layout never changes shape. Unknown values render "--" — never an
 * invented number.
 */
data class MinerDetailUi(
    val displayName: String,
    /** Model • firmware line, blank when neither is known. */
    val modelLine: String,
    /** "Cloud Owner: X • ip" for remote, "host • location" for local. */
    val subLine: String,
    val isRemote: Boolean,
    val isOnline: Boolean,
    val isStale: Boolean = false,
    /** Remote only: "Updated 2 min ago · via relay" style line. */
    val lastUpdatedLabel: String? = null,
    val hashrateTh: Double,
    val avgHashrateTh: Double,
    val idealHashrateTh: Double?,
    val rate30mTh: Double? = null,
    val uptimeLabel: String? = null,
    val hwErrors: Long? = null,
    val statusText: String,
    val chipTempC: Int? = null,
    val pcbTempC: Int? = null,
    /** Every fan's RPM in fan order; empty when fan detail is unknown. */
    val fanRpms: List<Int> = emptyList(),
    val maxFanRpm: Int? = null,
    val health: MinerHealthFlags? = null,
    val poolUrl: String = "",
    val poolWorker: String = "",
    val poolStatus: String = "",
    val acceptedShares: Long? = null,
    val rejectedShares: Long? = null,
    val chains: List<RemoteChainSnapshot> = emptyList(),
    /** Remote snapshots never carry logs — the section shows as a placeholder. */
    val showOnLocationSections: Boolean = false
) {
    val slowFanCount: Int get() = fanRpms.count { it < TelemetryCodec.SLOW_FAN_RPM }
}

/** A cloud snapshot as the unified detail. [displayName] is pre-resolved (phone nicknames applied). */
fun RemoteMinerSnapshot.toMinerDetailUi(displayName: String): MinerDetailUi {
    val chipTemp = maxChipTemp ?: maxTemp.takeIf { it > 0 }
    val fanMax = fanRpms.maxOrNull() ?: maxFan.takeIf { it > 0 }
    return MinerDetailUi(
        displayName = displayName,
        modelLine = listOf(model, firmware).filter { it.isNotBlank() }.joinToString(" • "),
        subLine = "Cloud Owner: $user • $ip",
        isRemote = true,
        isOnline = !isStale && effectiveHashrate5s > 0.0,
        isStale = isStale,
        lastUpdatedLabel = "Updated $timeAgoFormatted · via relay" +
            if (isStale) " — signal lost (>2m), values may be stale" else "",
        hashrateTh = effectiveHashrate5s,
        avgHashrateTh = effectiveAvgHashrate,
        idealHashrateTh = idealHashrate,
        rate30mTh = hashrate30m,
        uptimeLabel = formattedUptime,
        hwErrors = hwErrors,
        statusText = effectiveStatus,
        chipTempC = chipTemp,
        pcbTempC = maxPcbTemp,
        fanRpms = fanRpms,
        maxFanRpm = fanMax,
        health = health,
        poolUrl = poolUrl,
        poolWorker = poolWorker,
        poolStatus = poolStatus,
        acceptedShares = acceptedShares,
        rejectedShares = rejectedShares,
        chains = chains,
        showOnLocationSections = true
    )
}

/** Live local dashboard data as the unified detail — the same shape a snapshot produces. */
fun localMinerDetailUi(
    profile: MinerProfile,
    data: MinerDashboardData?,
    displayName: String
): MinerDetailUi {
    val summary = data?.summary
    val stats = data?.stats
    val isOnline = data?.isOnline == true
    val primaryPool = data?.pools?.firstOrNull { it.priority == 0 } ?: data?.pools?.firstOrNull()
    val hashrate = when {
        (summary?.hashrate5s ?: 0.0) > 0.0 -> summary!!.hashrate5s
        (stats?.chains?.sumOf { it.realHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.realHashrateTh }
        else -> summary?.hashrateAvg ?: 0.0
    }
    val avg = when {
        (summary?.hashrateAvg ?: 0.0) > 0.0 -> summary!!.hashrateAvg
        (summary?.hashrate30m ?: 0.0) > 0.0 -> summary!!.hashrate30m
        else -> hashrate
    }
    val ideal = when {
        (summary?.hashrateIdeal ?: 0.0) > 0.0 -> summary!!.hashrateIdeal
        (stats?.chains?.sumOf { it.idealHashrateTh } ?: 0.0) > 0.0 -> stats!!.chains.sumOf { it.idealHashrateTh }
        else -> null
    }
    return MinerDetailUi(
        displayName = displayName,
        modelLine = listOfNotNull(
            data?.systemInfo?.minerType?.takeIf { it.isNotBlank() } ?: profile.cachedModel,
            data?.systemInfo?.systemVersion?.takeIf { it.isNotBlank() }
        ).joinToString(" • "),
        subLine = profile.host + if (profile.location.isNotBlank() && profile.location != "Default Location") " • ${profile.location}" else "",
        isRemote = false,
        isOnline = isOnline,
        hashrateTh = if (isOnline) hashrate else 0.0,
        avgHashrateTh = if (isOnline) avg else 0.0,
        idealHashrateTh = ideal,
        rate30mTh = summary?.hashrate30m?.takeIf { it > 0.0 },
        uptimeLabel = summary?.formattedUptime,
        hwErrors = stats?.totalHwErrors ?: summary?.hwAll,
        statusText = if (isOnline) "Online" else "Offline",
        chipTempC = stats?.maxChipTemp?.takeIf { it > 0 },
        pcbTempC = stats?.maxPcbTemp?.takeIf { it > 0 },
        fanRpms = stats?.fans?.sortedBy { it.fanIndex }?.map { it.rpm } ?: emptyList(),
        maxFanRpm = stats?.maxFanRpm?.takeIf { it > 0 },
        health = summary?.let {
            MinerHealthFlags(
                rateOk = it.rateHealth,
                networkOk = it.networkHealth,
                fansOk = it.fansHealth,
                tempOk = it.tempHealth
            )
        },
        poolUrl = primaryPool?.url ?: "",
        poolWorker = primaryPool?.user ?: "",
        poolStatus = primaryPool?.status ?: "",
        acceptedShares = primaryPool?.acceptedShares,
        rejectedShares = primaryPool?.rejectedShares,
        chains = stats?.chains?.map { it.toRemoteChain() } ?: emptyList(),
        showOnLocationSections = false
    )
}

/**
 * The unified detail body. [showHeader] is false when the host screen
 * already renders its own header bar for the same machine (Home's local
 * dashboard chrome) — every section below the header is identical
 * either way.
 */
@Composable
fun MinerDetailContent(
    detail: MinerDetailUi,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onBack: (() -> Unit)? = null,
    onEditNickname: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showHeader) {
            MinerDetailHeader(detail = detail, onBack = onBack, onEditNickname = onEditNickname)
        }

        HeroHashrateCard(
            hashrate = detail.hashrateTh,
            avgRate = detail.avgHashrateTh,
            idealRate = detail.idealHashrateTh ?: 120.0,
            isOnline = detail.isOnline,
            isCloudMode = detail.isRemote,
            isStaleCloud = detail.isStale
        )

        // Quick metrics — the same 2×2 grid the local dashboard uses.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Chip Temp Max",
                value = detail.chipTempC?.let { "$it°C" } ?: "--°C",
                icon = Icons.Default.Thermostat,
                modifier = Modifier.weight(1f),
                accentColor = if ((detail.chipTempC ?: 0) > 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            MetricCard(
                title = "Max Fan RPM",
                value = detail.maxFanRpm?.let { "$it RPM" } ?: "-- RPM",
                icon = Icons.Default.Air,
                modifier = Modifier.weight(1f),
                accentColor = CyberCyan
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "HW Errors",
                value = detail.hwErrors?.toString() ?: "--",
                icon = Icons.Default.BugReport,
                modifier = Modifier.weight(1f),
                accentColor = if ((detail.hwErrors ?: 0L) > 100L) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
            MetricCard(
                title = "Status",
                value = if (detail.isRemote) detail.statusText else (detail.uptimeLabel ?: detail.statusText),
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f),
                accentColor = ElectricBlue
            )
        }

        // Session metrics the hero doesn't carry.
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("30m Avg", detail.rate30mTh?.let { String.format(Locale.US, "%.1f TH/s", it) } ?: "--")
                DetailMetric("Uptime", detail.uptimeLabel ?: "--")
                DetailMetric("PCB Temp", detail.pcbTempC?.let { "$it°C" } ?: "--")
            }
        }

        // Hash boards
        SectionTitle("Hash Boards")
        if (detail.chains.isEmpty()) {
            UnavailableSection("Chain details", "ASIC status, per-board temps and frequencies are not in this data.")
        } else {
            detail.chains.forEach { chain -> MinerChainCard(chain = chain) }
        }

        // Fans
        SectionTitle("Fans")
        if (detail.fanRpms.isEmpty()) {
            UnavailableSection("Fan list", "Per-fan speeds are not in this data.")
        } else {
            if (detail.slowFanCount > 0) {
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
                            text = "Low Fan Speed Warning: ${detail.slowFanCount} fan(s) spinning below ${TelemetryCodec.SLOW_FAN_RPM} RPM.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            detail.fanRpms.forEachIndexed { index, rpm ->
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

        // Primary pool
        SectionTitle("Primary Pool")
        if (detail.poolUrl.isBlank() && detail.poolWorker.isBlank() && detail.poolStatus.isBlank()) {
            UnavailableSection("Primary pool", "Pool endpoint and share counts are not in this data.")
        } else {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (detail.poolUrl.isNotBlank()) {
                        Text(
                            text = detail.poolUrl,
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
                        DetailMetric("Worker", detail.poolWorker.ifBlank { "--" })
                        DetailMetric("Status", detail.poolStatus.ifBlank { "--" })
                        DetailMetric("Accepted", detail.acceptedShares?.toString() ?: "--")
                        DetailMetric("Rejected", detail.rejectedShares?.toString() ?: "--")
                    }
                }
            }
        }

        // Health flags
        SectionTitle("Health")
        val health = detail.health
        if (health == null) {
            UnavailableSection("Health", "The miner's self-reported health flags are not in this data.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HealthChip("Hashrate", health.rateOk)
                HealthChip("Network", health.networkOk)
                HealthChip("Fans", health.fansOk)
                HealthChip("Temp", health.tempOk)
            }
        }

        // Sections a relay snapshot can never carry stay visible as
        // placeholders, so a remote detail has the same shape as a local
        // one instead of silently ending early.
        if (detail.showOnLocationSections) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UnavailableSection("Logs", "Miner event and kernel logs")
                Text(
                    text = "Available on location only — connect on the miner's Wi-Fi to view.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.lastUpdatedLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detail.isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MinerDetailHeader(
    detail: MinerDetailUi,
    onBack: (() -> Unit)? = null,
    onEditNickname: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (detail.isStale) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                else if (detail.isRemote) CyberCyan.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (detail.isStale) MaterialTheme.colorScheme.error
                            else if (detail.isRemote) CyberCyan
                            else if (detail.isOnline) AntminerEmerald
                            else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = detail.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (detail.isRemote) CyberCyan.copy(alpha = 0.15f) else AntminerEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (detail.isRemote) "REMOTE" else "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (detail.isRemote) CyberCyan else AntminerEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (onEditNickname != null) {
                            IconButton(
                                onClick = onEditNickname,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Set nickname on this phone",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = detail.subLine,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalDataFontFamily.current,
                        color = if (detail.isRemote) CyberCyan else MaterialTheme.colorScheme.primary
                    )
                    if (detail.modelLine.isNotBlank()) {
                        Text(
                            text = detail.modelLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (detail.isRemote) {
                        detail.lastUpdatedLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (detail.isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("← All miners", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * One hash board — the same card for a live [com.example.antminerconnectiontest.domain.model.ChainStats]
 * (converted via [toRemoteChain]) and a relayed [RemoteChainSnapshot]:
 * rate vs ideal, per-board chip/PCB maxima, HW errors, the ASIC chip
 * matrix, and an explicit dead/missing chip warning.
 */
@Composable
fun MinerChainCard(chain: RemoteChainSnapshot) {
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
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun DetailMetric(label: String, value: String) {
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

@Composable
fun HealthChip(label: String, ok: Boolean) {
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

/** A section the current data source cannot fill — shown, never hidden, so LIVE and REMOTE layouts match. */
@Composable
fun UnavailableSection(title: String, description: String) {
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

/**
 * The hero hashrate card — shared by the local dashboard (Home) and the
 * unified detail, so a machine's headline rate looks identical wherever
 * it is opened. (Moved here from HomeScreen in v0.1.7.)
 */
@Composable
fun HeroHashrateCard(
    hashrate: Double,
    avgRate: Double,
    idealRate: Double,
    isOnline: Boolean,
    isCloudMode: Boolean,
    isStaleCloud: Boolean,
    modifier: Modifier = Modifier
) {
    // Isolated animation scope - prevents the parent from recomposing on every frame!
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
                        style = MaterialTheme.typography.labelSmall,
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
                fontFamily = LocalDataFontFamily.current,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (avgRate > 0.0) String.format(Locale.US, "%.1f TH/s", avgRate) else "--",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalDataFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ideal Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f TH/s", idealRate),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalDataFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** The quick-metric card — shared by Home and the unified detail. (Moved here from HomeScreen in v0.1.7.) */
@Composable
fun MetricCard(
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalDataFontFamily.current,
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
