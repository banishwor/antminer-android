package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerHealthFlags
import com.example.antminerconnectiontest.domain.model.MinerMerge
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteChainSnapshot
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.TelemetryCodec
import com.example.antminerconnectiontest.domain.model.toRemoteChain
import com.example.antminerconnectiontest.theme.AntminerEmerald
import com.example.antminerconnectiontest.theme.LocalDataFontFamily
import com.example.antminerconnectiontest.theme.WarningAmber
import java.util.Locale

/**
 * THE miner detail presentation — one machine, one layout, whether the
 * data arrived live from the miner on the local network or from a cloud
 * relay snapshot. Both sources are first normalised into [MinerDetailUi];
 * the tab screens then render the same sections in the same order with
 * the same components (detail parity, v0.1.7+).
 *
 * v0.2.0 split the old single detail body by tab, per the approved
 * mockups: hash boards / fans / temps / session / health live on the
 * Hardware tab ([MinerHardwareSections]), the primary pool on the Pools
 * tab ([MinerDetailPoolSection]), logs on the Logs tab. Home shows only
 * the summary projection (HeroSummary) — none of these sections.
 *
 * The only honest differences between LIVE and REMOTE are the source
 * chip, the "last updated" line, and sections a snapshot genuinely
 * cannot carry (logs), which stay visible as greyed placeholders so the
 * layout never changes shape. Unknown values render "--" — never an
 * invented number.
 */
data class MinerDetailUi(
    val displayName: String,
    /** Pool worker name (e.g. "banishwor.001"), "" when unknown. */
    val workerName: String = "",
    /** Miner model (e.g. "Antminer S19k Pro"), "" when unknown. */
    val modelName: String = "",
    /** Firmware / system version, "" when unknown. */
    val firmware: String = "",
    /** Model • firmware line, blank when neither is known. */
    val modelLine: String,
    /** "ip • Owner: X" for a snapshot, "host • location" for local — one plain subtitle style for both (v0.1.9). */
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
        workerName = MinerMerge.workerIdentity(this) ?: "",
        modelName = model,
        firmware = firmware,
        modelLine = listOf(model, firmware).filter { it.isNotBlank() }.joinToString(" • "),
        subLine = "$ip • Owner: $user",
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
        workerName = profile.workerName,
        modelName = data?.systemInfo?.minerType?.takeIf { it.isNotBlank() } ?: profile.cachedModel ?: "",
        firmware = data?.systemInfo?.systemVersion ?: "",
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
 * The LIVE / REMOTE source chip — the ONLY place data source is shown
 * (v0.2.0). Same neutral styling for both; colour never encodes source.
 */
@Composable
fun SourceChip(isRemote: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = if (isRemote) "REMOTE" else "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Small status dot: green online, red offline/stale. Status colour only. */
@Composable
fun StatusDot(isOnline: Boolean, isStale: Boolean = false, size: Int = 9) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (isOnline && !isStale) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
    )
}

/**
 * The context header every Hardware / Pools / Logs tab opens with
 * (v0.2.0): which machine this tab is about — nickname big, worker
 * small, the source chip, and the cloud "last updated" line.
 */
@Composable
fun SelectedMachineHeader(
    detail: MinerDetailUi,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onEditNickname: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(isOnline = detail.isOnline, isStale = detail.isStale)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = detail.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                val workerLine = detail.workerName.takeIf {
                    it.isNotBlank() && !it.equals(detail.displayName, ignoreCase = true)
                }
                val secondLine = listOfNotNull(
                    workerLine,
                    detail.modelName.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (secondLine.isNotBlank()) {
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (detail.isRemote) {
                    detail.lastUpdatedLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (detail.isStale) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            SourceChip(isRemote = detail.isRemote)
            if (onRefresh != null) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Hardware tab body for the selected machine (v0.2.0): temperatures,
 * session metrics, hash boards, fans, health — identical for live data
 * and cloud snapshots.
 */
@Composable
fun MinerHardwareSections(detail: MinerDetailUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MinerDetailTempsSection(detail)
        MinerDetailSessionSection(detail)
        MinerDetailChainsSection(detail)
        MinerDetailFansSection(detail)
        MinerDetailHealthSection(detail)
    }
}

/** Temperatures + fastest fan — Hardware tab only (strict strip). */
@Composable
fun MinerDetailTempsSection(detail: MinerDetailUi) {
    SectionTitle("Temperatures")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailMetric("Chip Temp", detail.chipTempC?.let { "$it°C" } ?: "--")
            DetailMetric("PCB Temp", detail.pcbTempC?.let { "$it°C" } ?: "--")
            DetailMetric("Max Fan", detail.maxFanRpm?.let { String.format(Locale.US, "%,d RPM", it) } ?: "--")
        }
    }
}

/** Session metrics — 30m average, uptime, HW errors, firmware. Hardware tab only. */
@Composable
fun MinerDetailSessionSection(detail: MinerDetailUi) {
    SectionTitle("Session")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailMetric("30m Avg", detail.rate30mTh?.let { String.format(Locale.US, "%.1f TH/s", it) } ?: "--")
            DetailMetric("Uptime", detail.uptimeLabel ?: "--")
            DetailMetric("HW Errors", detail.hwErrors?.toString() ?: "--")
            DetailMetric("Firmware", detail.firmware.ifBlank { "--" })
        }
    }
}

/**
 * Hash boards section of the unified detail, shared by the Hardware tab
 * for live and cloud machines alike.
 */
@Composable
fun MinerDetailChainsSection(detail: MinerDetailUi) {
    SectionTitle("Hash Boards")
    if (detail.chains.isEmpty()) {
        UnavailableSection("Chain details", "ASIC status, per-board temps and frequencies are not in this data.")
    } else {
        detail.chains.forEach { chain -> MinerChainCard(chain = chain) }
    }
}

/** Fans section of the unified detail — see [MinerDetailChainsSection]. */
@Composable
fun MinerDetailFansSection(detail: MinerDetailUi) {
    SectionTitle("Fans")
    if (detail.fanRpms.isEmpty()) {
        UnavailableSection("Fan list", "Per-fan speeds are not in this data.")
    } else {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%,d RPM", rpm),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = LocalDataFontFamily.current,
                            color = if (rpm < TelemetryCodec.SLOW_FAN_RPM) WarningAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Primary pool section of the unified detail — the Pools tab body for a cloud machine. */
@Composable
fun MinerDetailPoolSection(detail: MinerDetailUi) {
    SectionTitle("Primary Pool")
    if (detail.poolUrl.isBlank() && detail.poolWorker.isBlank() && detail.poolStatus.isBlank()) {
        UnavailableSection("Primary pool", "Pool endpoint and share counts are not in this data.")
    } else {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (detail.poolUrl.isNotBlank()) {
                    Text(
                        text = detail.poolUrl,
                        fontFamily = LocalDataFontFamily.current,
                        style = MaterialTheme.typography.bodyMedium,
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
}

/** Health flags section of the unified detail. */
@Composable
fun MinerDetailHealthSection(detail: MinerDetailUi) {
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
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
        style = MaterialTheme.typography.titleMedium,
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
            style = MaterialTheme.typography.bodyMedium,
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
