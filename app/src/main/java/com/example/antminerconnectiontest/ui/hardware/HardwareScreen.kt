package com.example.antminerconnectiontest.ui.hardware

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.ChainStats
import com.example.antminerconnectiontest.domain.model.FanStats
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.AsicChipMatrix
import com.example.antminerconnectiontest.ui.components.InteractiveHashrateChart
import com.example.antminerconnectiontest.ui.components.MinerDataState
import com.example.antminerconnectiontest.ui.components.MinerDataStateCard
import java.util.Locale

@Composable
fun HardwareScreen(
    profile: MinerProfile,
    data: MinerDashboardData?,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    dashboardError: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val isOnline = data?.isOnline == true
    val stats = data?.stats
    val chart = data?.chartData
    val chains = stats?.chains ?: emptyList()
    val fans = stats?.fans ?: emptyList()

    if (!isOnline) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware & Analytics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Detailed hashboard chip matrix & diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                data == null && dashboardError == null -> MinerDataStateCard(
                    state = MinerDataState.LOADING,
                    host = profile.host
                )
                dashboardError != null -> MinerDataStateCard(
                    state = MinerDataState.ERROR,
                    host = profile.host,
                    message = dashboardError,
                    onRetry = onRetry ?: onManualRefresh
                )
                else -> MinerDataStateCard(
                    state = MinerDataState.OFFLINE,
                    host = profile.host,
                    onRetry = onRetry ?: onManualRefresh
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hardware & Analytics",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${chains.size} Hashboards • ${fans.size} Cooling Fans Detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onManualRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Hardware Overview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "System Summary",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OverviewStat("Chains", "${chains.size}", MaterialTheme.colorScheme.primary)
                    OverviewStat("Total ASICs", "${chains.sumOf { it.asicCount }}", CyberCyan)
                    OverviewStat("Max Temp", "${stats?.maxChipTemp ?: 0}°C", if ((stats?.maxChipTemp ?: 0) > 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    OverviewStat("Cooling", "${fans.size} Fans", ElectricBlue)
                }
            }
        }

        // Hashboard Chains List
        Text(
            text = "Hashboard Chains (${chains.size})",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (chains.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No chain statistics received yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            chains.forEach { chain ->
                ChainCard(chain = chain)
            }
        }

        // Cooling Fans Section
        Text(
            text = "Cooling Fans (${fans.size})",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (fans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No fan telemetry available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Fan grid (2 per row)
            val chunkedFans = fans.chunked(2)
            chunkedFans.forEach { rowFans ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowFans.forEach { fan ->
                        FanCard(fan = fan, modifier = Modifier.weight(1f))
                    }
                    if (rowFans.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OverviewStat(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
    }
}

@Composable
private fun ChainCard(chain: ChainStats) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${chain.chainIndex}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
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
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%.2f TH/s", chain.realHashrateTh),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Chain Summary Row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChainMetricChip("Chip Max", "${chain.chipMaxTemp}°C", if (chain.chipMaxTemp > 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                ChainMetricChip("PCB Max", "${chain.pcbMaxTemp}°C", CyberCyan)
                ChainMetricChip("HW Errors", "${chain.hwErrors}", if (chain.hwErrors > 0) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Expanded Details & Visual ASIC Matrix
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 77-Chip ASIC Matrix
                    AsicChipMatrix(
                        asicStatusString = chain.asicStatusString,
                        totalChips = chain.asicCount
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailRowItem("Ideal Target", String.format(Locale.US, "%.2f TH/s", chain.idealHashrateTh))
                        DetailRowItem("HWP Ratio", String.format(Locale.US, "%.4f", chain.hwp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailRowItem("Chip Min Temp", "${chain.chipMinTemp}°C")
                        DetailRowItem("PCB Min Temp", "${chain.pcbMinTemp}°C")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailRowItem("EEPROM Status", if (chain.eepromLoaded) "Loaded" else "Unloaded")
                        if (chain.picMaxTemp > 0) {
                            DetailRowItem("PIC Max Temp", "${chain.picMaxTemp}°C")
                        } else {
                            DetailRowItem("ASIC Count", "${chain.asicCount} Chips")
                        }
                    }

                    if (chain.serialNumber.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRowItem("Chain S/N", chain.serialNumber)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainMetricChip(label: String, value: String, color: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
private fun DetailRowItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FanCard(fan: FanStats, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CyberCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Air, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Fan ${fan.fanIndex}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${fan.rpm} RPM",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
