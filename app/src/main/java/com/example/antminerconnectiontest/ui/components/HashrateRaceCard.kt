package com.example.antminerconnectiontest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.*
import java.util.Locale

data class MinerRaceParticipant(
    val id: String,
    val name: String,
    val userOrLocation: String,
    val hashrateTh: Double,
    val isActive: Boolean,
    val isRemote: Boolean,
    val localProfile: MinerProfile? = null,
    val remoteSnapshot: RemoteMinerSnapshot? = null
)

@Composable
fun HashrateRaceCard(
    activeProfile: MinerProfile?,
    mergedMiners: List<MergedMinerEntry> = emptyList(),
    selectedRemoteMinerId: String? = null,
    onSelectProfile: (MinerProfile) -> Unit,
    onSelectRemoteMiner: (RemoteMinerSnapshot) -> Unit = {},
    onAddNewMiner: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One participant per MACHINE (merged entries): a machine saved
    // locally AND present in the cloud sheet is a single row, never two.
    // Display precedence per row: a live local reading wins; when local
    // data is unavailable (away from site / not answering) the row falls
    // back to the machine's remote snapshot.
    val totalMiners = mergedMiners.mapNotNull { entry ->
        val local = entry.localProfile
        val remote = entry.remoteSnapshot
        val localRate = entry.localHashrateTh ?: 0.0
        when {
            // Live local reading available — local data is displayed.
            local != null && localRate > 0.0 -> MinerRaceParticipant(
                id = entry.key,
                name = entry.displayName,
                userOrLocation = if (local.location.isNotBlank() && local.location != "Default Location") local.location else "Local",
                hashrateTh = localRate,
                isActive = (local.id == activeProfile?.id && selectedRemoteMinerId == null) ||
                    (remote != null && remote.id == selectedRemoteMinerId),
                isRemote = false,
                localProfile = local,
                remoteSnapshot = remote
            )
            // Remote snapshot is the display data (remote-only machine, or
            // a saved machine that is not currently reachable locally).
            remote != null -> MinerRaceParticipant(
                id = entry.key,
                name = entry.displayName,
                userOrLocation = if (remote.isStale) "⚠️ ${remote.user} (Offline)" else "☁️ ${remote.user}",
                hashrateTh = remote.effectiveHashrate5s,
                isActive = remote.id == selectedRemoteMinerId,
                isRemote = true,
                localProfile = local,
                remoteSnapshot = remote
            )
            // Local-only and unreachable on current Wi-Fi: stays saved in
            // the background but hidden from the live race (as before).
            else -> null
        }
    }
    val hasCloudParticipants = totalMiners.any { it.remoteSnapshot != null }

    // Only render if 2 or more devices are available (locally or remotely)
    if (totalMiners.size < 2) return

    val haptic = LocalHapticFeedback.current

    val rankedParticipants = remember(totalMiners) {
        totalMiners.sortedByDescending { it.hashrateTh }
    }

    val topRate = remember(rankedParticipants) {
        (rankedParticipants.firstOrNull()?.hashrateTh ?: 120.0).coerceAtLeast(1.0)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HASHRATE RACE (${totalMiners.size} Miners)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasCloudParticipants) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = CyberCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Cloud Active", fontSize = 8.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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

            // Racer Items List
            rankedParticipants.take(5).forEachIndexed { rankIndex, participant ->
                RacerRowItem(
                    rank = rankIndex + 1,
                    participant = participant,
                    maxRate = topRate,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (participant.isRemote && participant.remoteSnapshot != null) {
                            // Display data is the snapshot: select it (the
                            // ViewModel activates the local profile too when
                            // this is a merged machine, so local data can
                            // take over once fetched).
                            onSelectRemoteMiner(participant.remoteSnapshot)
                        } else if (participant.localProfile != null) {
                            onSelectProfile(participant.localProfile)
                        } else if (participant.remoteSnapshot != null) {
                            onSelectRemoteMiner(participant.remoteSnapshot)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RacerRowItem(
    rank: Int,
    participant: MinerRaceParticipant,
    maxRate: Double,
    onClick: () -> Unit
) {
    val isActive = participant.isActive
    val speedProgress by animateFloatAsState(
        targetValue = ((participant.hashrateTh / maxRate).toFloat()).coerceIn(0.05f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "raceSpeed"
    )

    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val rankIcon = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }

    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Rank + Nickname + User/Location
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rankIcon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = medalColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = participant.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (participant.userOrLocation.isNotBlank() && participant.userOrLocation != "Default Location") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = if (participant.isRemote) CyberCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = participant.userOrLocation,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = if (participant.isRemote) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Right: Live Hashrate & Active Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rateText = if (participant.isRemote && participant.remoteSnapshot?.isStale == true) {
                        "Offline"
                    } else if (participant.hashrateTh > 0.0) {
                        String.format(Locale.US, "%.1f TH/s", participant.hashrateTh)
                    } else {
                        "0.0 TH/s"
                    }

                    Text(
                        text = rateText,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (participant.isRemote && participant.remoteSnapshot?.isStale == true) {
                            MaterialTheme.colorScheme.error
                        } else if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    if (isActive) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (participant.isRemote) CyberCyan else AntminerEmerald)
                        )
                    }
                }
            }

            // Mini Neon Speed Progress Bar
            LinearProgressIndicator(
                progress = { speedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = if (isActive) MaterialTheme.colorScheme.primary else if (participant.isRemote) CyberCyan else AntminerEmerald,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}
