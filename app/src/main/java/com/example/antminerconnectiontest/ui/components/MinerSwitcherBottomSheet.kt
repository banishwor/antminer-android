package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MergedMinerEntry
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.PhoneNicknames
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinerSwitcherBottomSheet(
    activeProfile: MinerProfile?,
    savedProfiles: List<MinerProfile>,
    minerHashrates: Map<String, Double> = emptyMap(),
    mergedMiners: List<MergedMinerEntry> = emptyList(),
    selectedRemoteMinerId: String? = null,
    /** This phone's private nicknames (v0.1.7) — display resolution only. */
    nicknameOverrides: Map<String, String> = emptyMap(),
    onSelectProfile: (MinerProfile) -> Unit,
    onSelectRemoteMiner: (RemoteMinerSnapshot) -> Unit = {},
    onAddNewMiner: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Miners",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select an active miner or add a new one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        onDismiss()
                        onAddNewMiner()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Miner", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Profiles list
            if (savedProfiles.isEmpty() && activeProfile != null) {
                // Show current active profile
                ProfileRowItem(
                    profile = activeProfile,
                    isActive = true,
                    displayName = PhoneNicknames.displayName(nicknameOverrides, activeProfile, null),
                    onSelect = { onDismiss() },
                    onDelete = {}
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedProfiles) { profile ->
                        val isActive = profile.id == activeProfile?.id
                        ProfileRowItem(
                            profile = profile,
                            isActive = isActive,
                            displayName = PhoneNicknames.displayName(nicknameOverrides, profile, null),
                            hashrateTh = minerHashrates[profile.id],
                            hasRemoteSnapshot = mergedMiners.any {
                                it.localProfile?.id == profile.id && it.remoteSnapshot != null
                            },
                            onSelect = {
                                onSelectProfile(profile)
                                onDismiss()
                            },
                            onDelete = { onDeleteProfile(profile.id) }
                        )
                    }

                    // Remote-only machines (in the cloud sheet, not saved
                    // locally): same list, one entry per machine — merged
                    // machines already appear above as local rows.
                    val remoteOnlyEntries = mergedMiners.filter {
                        it.localProfile == null && it.remoteSnapshot != null
                    }
                    if (remoteOnlyEntries.isNotEmpty()) {
                        item {
                            Text(
                                text = "Cloud Miners",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(remoteOnlyEntries) { entry ->
                            val snapshot = entry.remoteSnapshot!!
                            RemoteRowItem(
                                entry = entry,
                                isSelected = snapshot.id == selectedRemoteMinerId,
                                onSelect = {
                                    onSelectRemoteMiner(snapshot)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRowItem(
    profile: MinerProfile,
    isActive: Boolean,
    displayName: String = "",
    hashrateTh: Double? = null,
    hasRemoteSnapshot: Boolean = false,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val shownName = displayName.ifBlank { profile.effectiveName }
    val hasCustomName = shownName != profile.workerName && profile.workerName.isNotBlank() &&
        (profile.nickname.isNotBlank() || shownName != profile.effectiveName)
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isActive) accentOk() else MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isActive) accentOk().copy(alpha = 0.2f) else MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (isActive) accentOk() else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // The name the user actually recognises (phone nickname,
                    // else profile nickname, else worker name) — rows used to
                    // show only the model name, so two identical models were
                    // indistinguishable.
                    Text(
                        text = shownName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Live status dot when the race poller has a reading:
                        // green = hashing, red = not answering/0 TH/s.
                        if (hashrateTh != null) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hashrateTh > 0.0) accentOk() else offlineDotColor())
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = profile.host,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) accentOk() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hashrateTh != null && hashrateTh > 0.0) {
                            Text(
                                text = " • ${String.format(java.util.Locale.US, "%.1f", hashrateTh)} TH/s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasRemoteSnapshot) {
                            // Same machine also exists in the cloud sheet —
                            // it is ONE entry; the chip just says the remote
                            // snapshot is attached as fallback.
                            Text(
                                text = " • ☁ Cloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = accentCloud()
                            )
                        }
                    }
                    if (hasCustomName) {
                        Text(
                            text = profile.workerName,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Surface(
                        color = accentOk().copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentOk(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this miner?") },
            text = {
                Text("\"$shownName\" (${profile.host}) will be removed from saved miners. This can't be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = offlineDotColor(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemoteRowItem(
    entry: MergedMinerEntry,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val snapshot = entry.remoteSnapshot ?: return
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) accentCloud() else MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentCloud().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = accentCloud(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.displayName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "☁️ ${snapshot.user} • ${snapshot.ip}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentCloud()
                    )
                    Text(
                        text = if (snapshot.isStale) "Offline • ${snapshot.timeAgoFormatted}" else "${String.format(java.util.Locale.US, "%.1f", entry.displayHashrateTh)} TH/s • ${snapshot.timeAgoFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snapshot.isStale) offlineDotColor() else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Surface(
                    color = accentCloud().copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "VIEWING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentCloud(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * The switcher sheet's offline dot colour: the scheme error slot
 * (text colour) in the monochrome grouped design.
 */
@Composable
private fun offlineDotColor(): Color = MaterialTheme.colorScheme.error
