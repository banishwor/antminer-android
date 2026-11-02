package com.example.antminerconnectiontest.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.data.analysis.DailyLogAnalyzer
import com.example.antminerconnectiontest.domain.model.DailyLogDateItem
import com.example.antminerconnectiontest.domain.model.DailyMinerAnalysis
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.DailyAnalysisUiState
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DailyAnalysisContent(
    state: DailyAnalysisUiState,
    onToggleDateSelection: (String) -> Unit,
    onSelectAllUnanalyzed: () -> Unit,
    onClearSelection: () -> Unit,
    onStartAnalysis: (List<String>) -> Unit,
    onOpenDetail: (DailyMinerAnalysis) -> Unit,
    onCloseDetail: () -> Unit,
    onRefreshDates: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.selectedAnalysisDetail != null) {
        DailyAnalysisDetailView(
            analysis = state.selectedAnalysisDetail,
            onBack = onCloseDetail,
            onReanalyze = {
                onStartAnalysis(listOf(state.selectedAnalysisDetail.rawDateId))
            },
            modifier = modifier
        )
    } else {
        DailyAnalysisListView(
            state = state,
            onToggleDateSelection = onToggleDateSelection,
            onSelectAllUnanalyzed = onSelectAllUnanalyzed,
            onClearSelection = onClearSelection,
            onStartAnalysis = onStartAnalysis,
            onOpenDetail = onOpenDetail,
            onRefreshDates = onRefreshDates,
            modifier = modifier
        )
    }
}

@Composable
private fun DailyAnalysisListView(
    state: DailyAnalysisUiState,
    onToggleDateSelection: (String) -> Unit,
    onSelectAllUnanalyzed: () -> Unit,
    onClearSelection: () -> Unit,
    onStartAnalysis: (List<String>) -> Unit,
    onOpenDetail: (DailyMinerAnalysis) -> Unit,
    onRefreshDates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Analysis In-Progress Banner
        AnimatedVisibility(visible = state.isAnalyzing) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.analysisPhaseMessage ?: "Processing log analysis...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AntminerEmerald
                        )
                        Text(
                            text = "${(state.analysisProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.analysisProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = AntminerEmerald,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }

        // Selection & Batch Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Historical Log Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.availableDates.count { it.isAnalyzed }} analyzed • ${state.availableDates.size} available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.selectedRawDateIds.isNotEmpty()) {
                    Button(
                        onClick = { onStartAnalysis(state.selectedRawDateIds.toList()) },
                        enabled = !state.isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = AntminerEmerald),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Analyze (${state.selectedRawDateIds.size})",
                            color = Color(0xFF003919),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onClearSelection,
                        enabled = !state.isAnalyzing,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }
                } else {
                    val unanalyzedCount = state.availableDates.count { !it.isAnalyzed || it.isToday }
                    if (unanalyzedCount > 0) {
                        TextButton(
                            onClick = onSelectAllUnanalyzed,
                            enabled = !state.isAnalyzing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Select All ($unanalyzedCount)", fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onRefreshDates, enabled = !state.isFetchingDates && !state.isAnalyzing) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Dates",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Content Area: List, Loading, or Error
        when {
            state.isFetchingDates && state.availableDates.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AntminerEmerald, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Querying available log dates from miner...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.errorMessage != null && state.availableDates.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRuby, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.errorMessage, color = ErrorRuby, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRefreshDates, colors = ButtonDefaults.buttonColors(containerColor = AntminerEmerald)) {
                            Text("Retry", color = Color(0xFF003919))
                        }
                    }
                }
            }

            state.availableDates.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical log dates returned by miner.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.availableDates, key = { it.rawDateId }) { item ->
                        DailyLogDateItemRow(
                            item = item,
                            isSelected = state.selectedRawDateIds.contains(item.rawDateId),
                            isAnalyzing = state.isAnalyzing,
                            onToggleSelect = { onToggleDateSelection(item.rawDateId) },
                            onOpenDetail = { item.analysis?.let(onOpenDetail) },
                            onAnalyzeSingle = { onStartAnalysis(listOf(item.rawDateId)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLogDateItemRow(
    item: DailyLogDateItem,
    isSelected: Boolean,
    isAnalyzing: Boolean,
    onToggleSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onAnalyzeSingle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) AntminerEmerald
                else if (item.isAnalyzed) AntminerEmerald.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.isAnalyzed) {
                    onOpenDetail()
                } else {
                    onToggleSelect()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Checkbox (if not analyzed) + Date info
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (!item.isAnalyzed || item.isToday) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        enabled = !isAnalyzing,
                        colors = CheckboxDefaults.colors(checkedColor = AntminerEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Analyzed",
                        tint = AntminerEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.formattedDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.isToday) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyberCyan.copy(alpha = 0.2f),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "TODAY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = item.rawDateId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: Status / Action
            if (item.isAnalyzed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.analysis?.let { analysis ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (analysis.maxTemperature >= 75) ErrorRuby.copy(alpha = 0.15f)
                            else if (analysis.maxTemperature >= 70) WarningAmber.copy(alpha = 0.15f)
                            else AntminerEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Max ${analysis.maxTemperature}°C",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (analysis.maxTemperature >= 75) ErrorRuby
                                else if (analysis.maxTemperature >= 70) WarningAmber
                                else AntminerEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.isToday) {
                        OutlinedButton(
                            onClick = onAnalyzeSingle,
                            enabled = !isAnalyzing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Refresh", fontSize = 11.sp)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Analysis",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAnalyzeSingle,
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Analyze",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyAnalysisDetailView(
    analysis: DailyMinerAnalysis,
    onBack: () -> Unit,
    onReanalyze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val numFormat = NumberFormat.getNumberInstance(Locale.US)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(
                        text = DailyLogAnalyzer.formatHumanReadableDate(analysis.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${analysis.rawDateId} • ${if (analysis.minerType.isNotBlank()) analysis.minerType else "Antminer"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onReanalyze) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Re-analyze", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Peak Temperature Executive Hero Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (analysis.maxTemperature >= 75) ErrorRuby.copy(alpha = 0.12f)
                else if (analysis.maxTemperature >= 70) WarningAmber.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (analysis.maxTemperature >= 75) ErrorRuby
                    else if (analysis.maxTemperature >= 70) WarningAmber
                    else AntminerEmerald
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HIGHEST CHIP TEMPERATURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${analysis.maxTemperature}",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (analysis.maxTemperature >= 75) ErrorRuby
                            else if (analysis.maxTemperature >= 70) WarningAmber
                            else AntminerEmerald
                        )
                        Text(
                            text = "°C",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Recorded at ${analysis.maxTemperatureTime.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chain ${analysis.maxTemperatureChain}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Hashboard Thermal Breakdown Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "HASHBOARD MAXIMUMS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (analysis.chainMaxTemperatures.isEmpty()) {
                    Text(
                        text = "No chain temperature breakdown recorded for this date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    analysis.chainMaxTemperatures.toSortedMap().forEach { (chainId, maxTemp) ->
                        val time = analysis.chainMaxTemperatureTimes[chainId] ?: ""
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chain $chainId",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (time.isNotBlank()) {
                                    Text(
                                        text = "@ $time",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = "$maxTemp°C",
                                    fontWeight = FontWeight.Bold,
                                    color = if (maxTemp >= 75) ErrorRuby else if (maxTemp >= 70) WarningAmber else AntminerEmerald,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Operations & Data Depth Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricPillCard(
                label = "Log Sessions",
                value = "${analysis.sessionCount}",
                subvalue = "${analysis.restartCount} reboots",
                modifier = Modifier.weight(1f)
            )
            MetricPillCard(
                label = "Temp Records",
                value = numFormat.format(analysis.temperatureRecordCount),
                subvalue = "Parsed data points",
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricPillCard(
                label = "Peak PCB Temp",
                value = if (analysis.maxPcbTemperature > 0) "${analysis.maxPcbTemperature}°C" else "N/A",
                subvalue = if (analysis.maxPcbTemperatureTime.isNotBlank()) "@ ${analysis.maxPcbTemperatureTime}" else "Inlet temp",
                modifier = Modifier.weight(1f)
            )
            MetricPillCard(
                label = "Avg Hashrate",
                value = if (analysis.avgHashrateTh > 0.0) String.format(Locale.US, "%.1f TH/s", analysis.avgHashrateTh) else "Online",
                subvalue = if (analysis.hardwareErrorCount > 0) "${analysis.hardwareErrorCount} HW errors" else "0 HW errors",
                modifier = Modifier.weight(1f)
            )
        }

        // Notable Events Timeline
        if (analysis.events.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INCIDENT TIMELINE (${analysis.events.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    analysis.events.forEach { ev ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ev.timestamp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (ev.type) {
                                    "REBOOT" -> WarningAmber.copy(alpha = 0.2f)
                                    "TEMP_SPIKE" -> ErrorRuby.copy(alpha = 0.2f)
                                    "HW_ERROR" -> ErrorRuby.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = ev.type,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (ev.type) {
                                        "REBOOT" -> WarningAmber
                                        "TEMP_SPIKE" -> ErrorRuby
                                        "HW_ERROR" -> ErrorRuby
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = ev.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPillCard(
    label: String,
    value: String,
    subvalue: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subvalue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
