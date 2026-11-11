package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.theme.panelShape

/** Which dashboard data state [MinerDataStateCard] should present. */
enum class MinerDataState {
    /** No data yet for the selected miner and no error: first fetch in flight. */
    LOADING,

    /** The last fetch failed; [MinerDataStateCard.message] carries the detail. */
    ERROR,

    /** The miner answered nothing / reported offline. */
    OFFLINE
}

/**
 * Honest dashboard state card: loading, unreachable-with-error, or offline.
 *
 * Replaces the old "Cloud Remote View Active" placeholder, which was shown
 * whenever a local miner simply hadn't answered yet and sent users to the
 * Home tab looking for a cloud feed they may never have asked for.
 */
@Composable
fun MinerDataStateCard(
    state: MinerDataState,
    host: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    /**
     * "Find moved miners" recovery scan (v0.5.4): when provided, the
     * error/offline states offer it as a secondary action — the miner
     * may simply have moved to a new IP (DHCP). Tap-initiated only.
     */
    onFindMovedMiners: (() -> Unit)? = null,
    isFindingMovedMiners: Boolean = false,
    movedMinerScanLines: List<String> = emptyList()
) {
    val isError = state == MinerDataState.ERROR
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    Card(
        shape = panelShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(accent)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                MinerDataState.LOADING -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Contacting $host…",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fetching live telemetry from the miner. This can take a few seconds.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MinerDataState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Couldn't reach this miner",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = message ?: "The miner at $host did not respond.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MinerDataState.OFFLINE -> {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Miner offline",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = message
                            ?: "The miner at $host isn't responding. Check that it's powered on and that this device is on the same Wi-Fi network.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state != MinerDataState.LOADING && onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }

            if (state != MinerDataState.LOADING && onFindMovedMiners != null) {
                OutlinedButton(
                    onClick = onFindMovedMiners,
                    enabled = !isFindingMovedMiners
                ) {
                    if (isFindingMovedMiners) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Searching this Wi-Fi…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Find moved miners", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state != MinerDataState.LOADING && movedMinerScanLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    movedMinerScanLines.forEach { line ->
                        Text(
                            text = line,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
