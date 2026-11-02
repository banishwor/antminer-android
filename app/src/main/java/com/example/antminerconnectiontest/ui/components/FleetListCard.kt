package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.FleetRow
import com.example.antminerconnectiontest.domain.model.FleetSorting
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.LocalDataFontFamily
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The combined fleet list: local and remote machines in ONE list, one
 * row per physical machine (see MinerMerge). Online machines sort by
 * hashrate descending; offline machines sit below them, most recently
 * updated first (see FleetSorting). Offline rows — remote ones in
 * particular — carry a dual-format last-updated label (relative + clock
 * time) so a dead machine says exactly when it was last heard from.
 */
@Composable
fun FleetListCard(
    mergedMiners: List<MergedMinerEntry>,
    activeProfile: MinerProfile?,
    selectedRemoteMinerId: String? = null,
    /** Pool brand for the active profile's row, derived from its live pool URL. */
    activePoolBrand: String? = null,
    /** Live chip temp for the machine whose full local data is on screen. */
    activeTempC: Int? = null,
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

    val rows = remember(mergedMiners, nowMs, activeProfile?.id, activePoolBrand) {
        FleetSorting.sortEntries(mergedMiners, nowMs) { entry ->
            if (entry.localProfile?.id == activeProfile?.id) activePoolBrand else null
        }
    }

    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLEET (${rows.size} ${if (rows.size == 1) "machine" else "machines"})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sorted by hashrate · offline last",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onAddNewMiner,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Add Unit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            rows.forEach { row ->
                FleetRowItem(
                    row = row,
                    isActive = isRowActive(row, activeProfile, selectedRemoteMinerId),
                    tempC = rowTempC(row, activeTempC),
                    nowMs = nowMs,
                    onEditNickname = onEditNickname?.let { edit -> { edit(row.entry) } },
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val entry = row.entry
                        val local = entry.localProfile
                        val remote = entry.remoteSnapshot
                        when {
                            // Display data for this row is the snapshot:
                            // select it (the ViewModel activates the local
                            // profile too for merged machines, so live
                            // local data can take over once fetched).
                            remote != null && !entry.hasLiveLocalData && entry.localHashrateTh == null ->
                                onSelectRemoteMiner(remote)
                            local != null -> onSelectProfile(local)
                            remote != null -> onSelectRemoteMiner(remote)
                        }
                    }
                )
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

private fun rowTempC(row: FleetRow, activeTempC: Int?): Int? {
    if (row.entry.hasLiveLocalData && activeTempC != null && activeTempC > 0) return activeTempC
    return row.remoteTempC
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FleetRowItem(
    row: FleetRow,
    isActive: Boolean,
    tempC: Int?,
    nowMs: Long,
    onEditNickname: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val entry = row.entry
    val snapshot = entry.remoteSnapshot
    val dataFont = LocalDataFontFamily.current
    val statusColor = when {
        !row.isOnline -> MaterialTheme.colorScheme.error
        row.isRemoteOnly -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEditNickname != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onEditNickname)
                } else {
                    Modifier.clickable { onClick() }
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = row.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        // The name yields first: chips and the hashrate keep
                        // their full intrinsic width instead of being
                        // squeezed until their text wraps mid-word.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (row.hasLocal) FleetChip("LOCAL", MaterialTheme.colorScheme.primary)
                    if (row.isMerged) {
                        Spacer(modifier = Modifier.width(4.dp))
                        FleetChip("CLOUD", MaterialTheme.colorScheme.secondary)
                    } else if (row.isRemoteOnly) {
                        FleetChip("REMOTE", MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    text = if (row.isOnline) String.format(Locale.US, "%.1f TH/s", row.hashrateTh) else "Offline",
                    fontWeight = FontWeight.Bold,
                    fontFamily = dataFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = if (row.isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                if (onEditNickname != null) {
                    IconButton(
                        onClick = onEditNickname,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Set nickname on this phone",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Pool identity: brand (when known) + worker username. A
            // per-device UUID is never an identity — remote rows without
            // a known worker name get a plain label instead.
            val workerLabel = row.workerName.takeUnless {
                com.example.antminerconnectiontest.domain.model.MinerMerge.isDeviceId(it)
            }.orEmpty()
            val poolLine = when {
                workerLabel.isNotBlank() && row.poolBrand != null -> "${row.poolBrand} · $workerLabel"
                workerLabel.isNotBlank() -> workerLabel
                entry.localProfile != null -> entry.localProfile.host
                row.isRemoteOnly -> "Remote machine"
                else -> ""
            }
            if (poolLine.isNotBlank()) {
                Text(
                    text = poolLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = dataFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tempC != null) {
                    Text(
                        text = "$tempC°C",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = dataFont,
                        color = if (tempC > 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = statusText(row, snapshot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Freshness: online remote rows keep the short relative form;
            // offline rows (remote above all) carry relative + clock time.
            when {
                row.isOnline && snapshot != null && !entry.hasLiveLocalData -> {
                    val short = FleetSorting.formatUpdatedShort(row.lastUpdatedEpochMs, nowMs)
                    if (short.isNotBlank()) {
                        Text(
                            text = short,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                !row.isOnline -> {
                    Text(
                        text = FleetSorting.formatLastUpdated(row.lastUpdatedEpochMs, nowMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun FleetChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun statusText(row: FleetRow, snapshot: RemoteMinerSnapshot?): String {
    return when {
        row.isOnline && row.entry.hasLiveLocalData -> "Online · live local data"
        row.isOnline && snapshot != null -> snapshot.status.ifBlank { "Online" }
        row.isOnline -> "Online"
        snapshot != null -> "Offline"
        else -> "Offline · not answering locally"
    }
}
