package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.domain.model.FleetRow
import com.example.antminerconnectiontest.domain.model.FleetSorting
import com.example.antminerconnectiontest.domain.model.FleetSourceChip
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.example.antminerconnectiontest.theme.LocalDataFontFamily
import com.example.antminerconnectiontest.theme.LocalGlassSettings
import com.example.antminerconnectiontest.theme.glassEdgeBrush
import com.example.antminerconnectiontest.theme.panelShape
import kotlinx.coroutines.delay

/**
 * The combined fleet list: local and remote machines in ONE list, one
 * row per physical machine (see MinerMerge). Online machines sort by
 * hashrate descending; offline machines sit below them, most recently
 * updated first (see FleetSorting).
 *
 * v0.2.0 rows follow the approved mockup exactly (via
 * [FleetSorting.rowUi]): status dot, nickname / worker, the LIVE or
 * REMOTE chip, the hashrate (or "Offline"), and — offline only — the
 * full "Last updated <relative> · <clock time>" line. Same surface
 * colour for cloud and local rows: the chip is the only source signal,
 * and colour carries status only. No temps, no pool names, no shares
 * on Home — that detail lives on the tabs.
 */
@Composable
fun FleetListCard(
    mergedMiners: List<MergedMinerEntry>,
    activeProfile: MinerProfile?,
    selectedRemoteMinerId: String? = null,
    onSelectProfile: (MinerProfile) -> Unit,
    onSelectRemoteMiner: (RemoteMinerSnapshot) -> Unit = {},
    onAddNewMiner: () -> Unit,
    /** Opens the phone-nickname editor for a row's machine (v0.1.7); null hides the affordance. */
    onEditNickname: ((MergedMinerEntry) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (mergedMiners.isEmpty()) return

    // Freshness labels ("2 min ago", staleness) age in real time; re-derive
    // them periodically instead of freezing at first composition.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val rows = remember(mergedMiners, nowMs) {
        FleetSorting.sortEntries(mergedMiners, nowMs)
    }

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Fleet · ${rows.size} ${if (rows.size == 1) "machine" else "machines"}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sorted by hashrate · offline last",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onAddNewMiner) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(2.dp))
                Text("Add Unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        val onRowClick: (FleetRow) -> Unit = { row ->
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            val entry = row.entry
            val local = entry.localProfile
            val remote = entry.remoteSnapshot
            when {
                // Display data for this row is the snapshot: select it
                // (the ViewModel activates the local profile too for
                // merged machines, so live local data can take over
                // once fetched).
                remote != null && !entry.hasLiveLocalData && entry.localHashrateTh == null ->
                    onSelectRemoteMiner(remote)
                local != null -> onSelectProfile(local)
                remote != null -> onSelectRemoteMiner(remote)
            }
        }

        // Grouped Sheets (v0.4.0): the fleet is ONE panel — rows
        // separated by hairlines inset from the left edge, the
        // selected row washed with the accent tint (mockup
        // .fleetrow.selected).
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = panelShape(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    FleetRowContent(
                        row = row,
                        isActive = isRowActive(row, activeProfile, selectedRemoteMinerId),
                        nowMs = nowMs,
                        onEditNickname = onEditNickname?.let { edit -> { edit(row.entry) } },
                        onClick = { onRowClick(row) }
                    )
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isRowActive(
    row: FleetRow,
    activeProfile: MinerProfile?,
    selectedRemoteMinerId: String?
): Boolean {
    val remoteId = row.entry.remoteSnapshot?.id
    return if (selectedRemoteMinerId != null) {
        remoteId == selectedRemoteMinerId
    } else {
        row.entry.localProfile?.id == activeProfile?.id
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FleetRowContent(
    row: FleetRow,
    isActive: Boolean,
    nowMs: Long,
    onEditNickname: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val ui = FleetSorting.rowUi(row, nowMs)
    val dataFont = LocalDataFontFamily.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isActive) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (onEditNickname != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onEditNickname)
                    } else {
                        Modifier.clickable { onClick() }
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status colour only: green online, red offline.
            StatusDot(isOnline = ui.isOnline, size = 9)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ui.displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        // The name yields first: the chip and the
                        // hashrate keep their full intrinsic width.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceChip(isRemote = ui.chip == FleetSourceChip.REMOTE)
                }
                if (ui.workerLine.isNotBlank()) {
                    Text(
                        text = ui.workerLine,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = dataFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ui.freshnessLine.isNotBlank()) {
                    Text(
                        text = ui.freshnessLine,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (ui.isOnline) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (ui.isOnline) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ui.hashrateLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = dataFont,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                color = if (ui.isOnline) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    // Grouped Sheets: the offline label is secondary
                    // grey — the word "Offline" carries the state.
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
}
