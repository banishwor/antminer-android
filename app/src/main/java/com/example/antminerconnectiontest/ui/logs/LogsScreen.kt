package com.example.antminerconnectiontest.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.DailyMinerAnalysis
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.DailyAnalysisUiState
import com.example.antminerconnectiontest.ui.LogsTab
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RENDERED_LOG_LINES = 2000

@Composable
fun LogsScreen(
    profile: MinerProfile,
    selectedTab: LogsTab,
    currentLogText: String?,
    historyLogText: String?,
    isLoading: Boolean,
    errorMessage: String?,
    lastUpdated: Long?,
    isOnline: Boolean = true,
    dailyAnalysisState: DailyAnalysisUiState = DailyAnalysisUiState(),
    onSelectTab: (LogsTab) -> Unit,
    onRefresh: () -> Unit,
    onToggleDateSelection: (String) -> Unit = {},
    onSelectAllUnanalyzed: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onStartDailyAnalysis: (List<String>) -> Unit = {},
    onOpenAnalysisDetail: (DailyMinerAnalysis) -> Unit = {},
    onCloseAnalysisDetail: () -> Unit = {},
    onRefreshDailyDates: () -> Unit = {},
    /**
     * Bottom dock clearance (v0.5.0): added to the root column's
     * bottom padding so the console card / analysis list ends above
     * the floating dock instead of behind it.
     */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val activeLogText = if (selectedTab == LogsTab.CURRENT) currentLogText else historyLogText

    val currentListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val listState = if (selectedTab == LogsTab.CURRENT) currentListState else historyListState
    val coroutineScope = rememberCoroutineScope()

    // Cap the rendered lines (see the console card below): only the most
    // recent MAX_RENDERED_LOG_LINES are composed. The full text is still
    // what the Copy button copies.
    val allLogLines = remember(activeLogText) { activeLogText?.lines() ?: emptyList() }
    val isLogTruncated = allLogLines.size > MAX_RENDERED_LOG_LINES
    val visibleLogLines = remember(allLogLines) {
        if (isLogTruncated) allLogLines.takeLast(MAX_RENDERED_LOG_LINES) else allLogLines
    }

    // Automatically snap to the bottom (latest logs) whenever log text arrives or tab changes
    LaunchedEffect(visibleLogLines, selectedTab) {
        if (visibleLogLines.isNotEmpty()) {
            listState.scrollToItem(visibleLogLines.lastIndex)
        }
    }

    val isScrolledUp by remember {
        derivedStateOf { listState.canScrollForward }
    }

    val isScrolledDown by remember {
        derivedStateOf { listState.canScrollBackward }
    }

    if (!isOnline) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Miner Logs",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live system dmesg and CGMiner logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            com.example.antminerconnectiontest.ui.components.MinerDataStateCard(
                state = com.example.antminerconnectiontest.ui.components.MinerDataState.OFFLINE,
                host = profile.host,
                message = "Logs need a live connection to the miner at ${profile.host}. Check that it's powered on and reachable, then retry.",
                onRetry = onRefresh
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Miner Logs",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (selectedTab == LogsTab.DAILY_ANALYSIS) "Daily log & thermal metrics" else if (selectedTab == LogsTab.CURRENT) "Live system & kernel stream" else "Historical operating log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quick jump to top/boot button
                if (selectedTab != LogsTab.DAILY_ANALYSIS && isScrolledDown && !activeLogText.isNullOrBlank()) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignTop,
                            contentDescription = "Scroll to Top",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Copy to clipboard button
                if (selectedTab != LogsTab.DAILY_ANALYSIS && !activeLogText.isNullOrBlank()) {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Miner Log", activeLogText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LogTabButton(
                label = "Current Log",
                isSelected = selectedTab == LogsTab.CURRENT,
                onClick = { onSelectTab(LogsTab.CURRENT) },
                modifier = Modifier.weight(1f)
            )
            LogTabButton(
                label = "History Log",
                isSelected = selectedTab == LogsTab.HISTORY,
                onClick = { onSelectTab(LogsTab.HISTORY) },
                modifier = Modifier.weight(1f)
            )
            LogTabButton(
                label = "Daily Analysis",
                isSelected = selectedTab == LogsTab.DAILY_ANALYSIS,
                onClick = { onSelectTab(LogsTab.DAILY_ANALYSIS) },
                modifier = Modifier.weight(1.1f)
            )
        }

        // Status & Metadata Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val endpoint = when (selectedTab) {
                LogsTab.CURRENT -> "/cgi-bin/log.cgi"
                LogsTab.HISTORY -> "/cgi-bin/hlog.cgi"
                LogsTab.DAILY_ANALYSIS -> "/cgi-bin/dlog.cgi"
            }
            Text(
                text = endpoint,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )

            if (lastUpdated != null) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Fetched: ${sdf.format(Date(lastUpdated))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        if (selectedTab == LogsTab.DAILY_ANALYSIS) {
            DailyAnalysisContent(
                state = dailyAnalysisState,
                onToggleDateSelection = onToggleDateSelection,
                onSelectAllUnanalyzed = onSelectAllUnanalyzed,
                onClearSelection = onClearSelection,
                onStartAnalysis = onStartDailyAnalysis,
                onOpenDetail = onOpenAnalysisDetail,
                onCloseDetail = onCloseAnalysisDetail,
                onRefreshDates = onRefreshDailyDates,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // Terminal Console Card (for Current and History Logs)
            Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                // The mockup's console is ALWAYS near-black with
                // light text, in light theme too.
                containerColor = GroupedSheetsSpec.ConsoleBackground
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                when {
                    isLoading && activeLogText.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentOk(),
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Reading ${if (selectedTab == LogsTab.CURRENT) "Current" else "History"} log stream...",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    !errorMessage.isNullOrBlank() && activeLogText.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = accentError(),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                color = Color(0xFFFFD2D2),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = accentOk())
                            ) {
                                Text(
                                    "Retry",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    activeLogText.isNullOrBlank() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No log output available.",
                                color = Color(0xFF64748B),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (isLogTruncated) {
                                Text(
                                    text = "Showing last $MAX_RENDERED_LOG_LINES of ${allLogLines.size} lines",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            // Lazy list: the entire log used to be a single Text,
                            // which made opening and scrolling large logs janky.
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                itemsIndexed(visibleLogLines) { _, line ->
                                    Text(
                                        text = line.ifEmpty { " " },
                                        color = GroupedSheetsSpec.ConsoleText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating "Jump to Latest" button when scrolled up
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolledUp && !activeLogText.isNullOrBlank(),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                if (visibleLogLines.isNotEmpty()) {
                                    listState.animateScrollToItem(visibleLogLines.lastIndex)
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentOk(),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Jump to Bottom",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Latest Logs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun LogTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Grouped Sheets (v0.4.0): the mockup's segmented control — the
    // selected segment is the raised pill (white / #636366) with
    // text-colour label; unselected segments are plain secondary
    // text on the grey track.
    val glassSettings = LocalGlassSettings.current
    Surface(
        color = when {
            !isSelected -> Color.Transparent
            else -> GroupedSheetsSpec.segmentedSelected(glassSettings.isDark)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
