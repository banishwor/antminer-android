package com.example.antminerconnectiontest.ui.pools

import androidx.compose.foundation.background
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerPool
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolsScreen(
    profile: MinerProfile,
    data: MinerDashboardData?,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    dashboardError: String? = null,
    onRetry: (() -> Unit)? = null,
    /** Bottom dock clearance (v0.5.0): appended inside the scroll columns. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val isOnline = data?.isOnline == true
    val pools = data?.pools ?: emptyList()
    var showFutureEditDialog by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onManualRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
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
                        text = "Mining Pools",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stratum pool configurations & active workers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                data == null && dashboardError == null -> com.example.antminerconnectiontest.ui.components.MinerDataStateCard(
                    state = com.example.antminerconnectiontest.ui.components.MinerDataState.LOADING,
                    host = profile.host
                )
                dashboardError != null -> com.example.antminerconnectiontest.ui.components.MinerDataStateCard(
                    state = com.example.antminerconnectiontest.ui.components.MinerDataState.ERROR,
                    host = profile.host,
                    message = dashboardError,
                    onRetry = onRetry ?: onManualRefresh
                )
                else -> com.example.antminerconnectiontest.ui.components.MinerDataStateCard(
                    state = com.example.antminerconnectiontest.ui.components.MinerDataState.OFFLINE,
                    host = profile.host,
                    onRetry = onRetry ?: onManualRefresh
                )
            }
            // Dock clearance (v0.5.0).
            Spacer(modifier = Modifier.height(bottomInset))
        }
    } else {
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
                    text = "Mining Pools",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${pools.size} Configured Stratum Pools",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Future Edit Action Button
                OutlinedButton(
                    onClick = { showFutureEditDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Pools", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onManualRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Hashrate Performance Chart Card (Total vs 3 Chains with Touch Scrubbing)
        val chart = data?.chartData
        Card(
            shape = panelShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hashrate Performance Chart",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${chart?.timePoints?.size ?: 0} Intervals",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (chart != null && chart.timePoints.isNotEmpty()) {
                    com.example.antminerconnectiontest.ui.components.InteractiveHashrateChart(points = chart.timePoints)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting telemetry chart data...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Stratum Share Efficiency Donut Ring
        if (pools.isNotEmpty()) {
            com.example.antminerconnectiontest.ui.components.ShareHealthDonut(pools = pools)
        }

        // Pools List
        Text(
            text = "Configured Stratum Pools (${pools.size})",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (pools.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No mining pools received from miner.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            pools.forEach { pool ->
                PoolCard(pool = pool)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Dock clearance (v0.5.0): the last pool card can scroll
        // fully above the floating dock.
        Spacer(modifier = Modifier.height(bottomInset))
    }
    }

    if (showFutureEditDialog) {
        AlertDialog(
            onDismissRequest = { showFutureEditDialog = false },
            title = {
                Text("Pool Configuration (Coming Soon)", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "The pool editing architecture (MinerConfigApi) is prepared in the codebase. Write functionality will be enabled with your miner's pool update parameters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { showFutureEditDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    }
}

@Composable
private fun PoolCard(pool: MinerPool) {
    val isPrimary = pool.priority == 0
    val isAlive = pool.status.equals("Alive", ignoreCase = true)
    // Grouped Sheets (v0.4.0): an alive pool reads monochrome — grey
    // dot, text-colour status word (the mockup's pool rows).
    val aliveDotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val aliveTextColor = MaterialTheme.colorScheme.onSurface

    Card(
        shape = panelShape(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Row: Priority Badge & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = pool.priorityLabel.uppercase(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isAlive) aliveDotColor else MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pool.status,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAlive) aliveTextColor else MaterialTheme.colorScheme.error
                    )
                }
            }

            // URL & Worker
            Column {
                Text(
                    text = pool.url,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pool.user,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentData()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Performance Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PoolMetric("Accepted", "${pool.acceptedShares}", MaterialTheme.colorScheme.primary)
                PoolMetric("Rejected", "${pool.rejectedShares}", if (pool.rejectedShares > 0) accentWarn() else MaterialTheme.colorScheme.onSurfaceVariant)
                if (pool.diff.isNotBlank()) {
                    PoolMetric("Diff", pool.diff, accentData())
                }
                if (pool.lastShareTimeString.isNotBlank() && pool.lastShareTimeString != "0") {
                    PoolMetric("Last Share", pool.lastShareTimeString, MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PoolMetric("Stale", "${pool.staleShares}", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PoolMetric(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}
