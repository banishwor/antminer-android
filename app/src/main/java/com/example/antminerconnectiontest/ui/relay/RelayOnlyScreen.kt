package com.example.antminerconnectiontest.ui.relay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.theme.WarningAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Upload state of one local miner, as the relay-only screen shows it. */
enum class RelayMinerState {
    /** Reachable and being uploaded. */
    UPLOADING,

    /** Saved on this phone but not reachable right now. */
    OFFLINE,

    /** Reachable but not uploadable yet (e.g. worker name unknown — deferred). */
    WAITING
}

/** One miner row on the relay-only status screen. */
data class RelayMinerLine(
    val name: String,
    val detail: String,
    val state: RelayMinerState
)

/**
 * The relay-only status screen (v0.3.0, round-6 mockup): when the
 * Settings "Relay only" switch is on, this ONE plain screen replaces
 * the whole app — no tabs, no fleet list, no settings — because the
 * phone's job at a miner site is to be left alone and read quickly.
 * Deliberately plain Violet Material even when the Liquid Glass style
 * is selected: no glass, no blur, nothing decorative.
 *
 * Everything it shows is the relay's actual state: who it relays as,
 * the local miners and their upload state, the last successful upload
 * (stamped by the relay itself after every cycle — see
 * MinerStorageManager.saveRelayStatus), auto-connect, and the last
 * error. The background behaviour is untouched by this screen: the
 * relay service, auto-connect triggers and uploads run exactly as in
 * full mode.
 */
@Composable
fun RelayOnlyScreen(
    userName: String,
    relayEnabled: Boolean,
    cloudConfigured: Boolean,
    miners: List<RelayMinerLine>,
    lastUploadAtMs: Long?,
    lastError: String?,
    appVersion: String,
    onRefreshStatus: () -> Unit,
    onExit: () -> Unit
) {
    // Keep the displayed upload state fresh while this screen sits
    // open: re-read the relay's persisted report periodically. This
    // only reads state — it changes no sync or polling cadence.
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            onRefreshStatus()
        }
    }

    val statusWord = when {
        !cloudConfigured -> "Not set up"
        relayEnabled -> "Relaying"
        else -> "Relay off"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = if (relayEnabled && cloudConfigured) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "● Relay only · $statusWord",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (relayEnabled && cloudConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                text = "Antminer v$appVersion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Text(
            text = "This phone is\njust a relay.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!cloudConfigured) {
            RelayNote(
                text = "Cloud relay is not set up on this phone. Exit relay-only mode to finish setup.",
                isError = true
            )
        } else if (!relayEnabled) {
            RelayNote(
                text = "Relay is off — this phone's miners are not being uploaded. Exit relay-only mode to turn it on.",
                isError = true
            )
        }

        // ── Relaying as ──────────────────────────────────────────────
        RelayPanel {
            RelayLabel("Relaying as")
            Text(
                text = userName.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Relay user name · uploads appear under this name",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Miners on this phone ─────────────────────────────────────
        RelayPanel {
            RelayLabel(
                if (miners.isEmpty()) "Miners on this phone"
                else "Uploading · ${miners.size} ${if (miners.size == 1) "miner" else "miners"} on this phone"
            )
            if (miners.isEmpty()) {
                Text(
                    text = "No local miners saved on this phone yet. On a miner's Wi-Fi, known miners connect by themselves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            miners.forEachIndexed { index, miner ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = miner.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = miner.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val (chipColor, chipText) = when (miner.state) {
                        RelayMinerState.UPLOADING ->
                            MaterialTheme.colorScheme.primary to "UPLOADING"
                        RelayMinerState.OFFLINE ->
                            MaterialTheme.colorScheme.onSurfaceVariant to "OFFLINE"
                        RelayMinerState.WAITING ->
                            WarningAmber to "WAITING"
                    }
                    Surface(
                        color = chipColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = chipColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // ── Status ───────────────────────────────────────────────────
        RelayPanel {
            RelayLabel("Status")
            RelayStatusRow(
                label = "Last successful upload",
                value = formatLastUpload(lastUploadAtMs)
            )
            RelayStatusRow(
                label = "Auto-connect",
                value = "On · known miners only"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Errors",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lastError ?: "None",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (lastError == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onExit,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Exit relay-only mode", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(
            text = "Returns to the full app — Home, fleet and Settings.\nRelay stays on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RelayPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun RelayLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RelayStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RelayNote(text: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** "Just now · 11:42 AM" / "12 min ago · 11:30 AM" / "Not yet". */
private fun formatLastUpload(lastUploadAtMs: Long?): String {
    if (lastUploadAtMs == null) return "Not yet"
    val now = System.currentTimeMillis()
    val ageMin = ((now - lastUploadAtMs) / 60_000L).coerceAtLeast(0L)
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastUploadAtMs))
    return when {
        ageMin < 1L -> "Just now · $clock"
        ageMin < 60L -> "$ageMin min ago · $clock"
        ageMin < 60L * 24L -> "${ageMin / 60L} h ago · $clock"
        else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(lastUploadAtMs))
    }
}
