package com.example.antminerconnectiontest.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.ui.components.DetailMetric
import com.example.antminerconnectiontest.ui.components.MinerDetailChainsSection
import com.example.antminerconnectiontest.ui.components.MinerDetailFansSection
import com.example.antminerconnectiontest.ui.components.MinerDetailPoolSection
import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import com.example.antminerconnectiontest.ui.components.UnavailableSection

/**
 * Tab contents of the main shell when no local miner is active: the
 * Hardware / Pools / Logs tabs for the selected fleet machine when it
 * exists only as a cloud snapshot. They are never dead ends and never
 * fabricate data — a tab either renders what the selected miner's
 * relay snapshot honestly carries (the same section composables the
 * unified detail uses), or a placeholder that names the selected miner
 * and says the tab is fully available on location. With no miner
 * selected, the tab points back to the Home fleet list.
 */

@Composable
fun RemoteHardwareTab(detail: MinerDetailUi?) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Hardware")
        return
    }
    RemoteTabColumn(title = "Hardware — ${detail.displayName}") {
        // Temperatures the snapshot honestly carries.
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailMetric("Chip Temp", detail.chipTempC?.let { "$it°C" } ?: "--")
                DetailMetric("PCB Temp", detail.pcbTempC?.let { "$it°C" } ?: "--")
                DetailMetric("Max Fan", detail.maxFanRpm?.let { "$it RPM" } ?: "--")
            }
        }
        MinerDetailChainsSection(detail)
        MinerDetailFansSection(detail)
        OnLocationNote(
            text = "Full hardware control for ${detail.displayName} is available on location — connect on the miner's Wi-Fi."
        )
    }
}

@Composable
fun RemotePoolsTab(detail: MinerDetailUi?) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Pools")
        return
    }
    RemoteTabColumn(title = "Pools — ${detail.displayName}") {
        MinerDetailPoolSection(detail)
        OnLocationNote(
            text = "Pool configuration for ${detail.displayName} is available on location — connect on the miner's Wi-Fi to view or change all pools."
        )
    }
}

@Composable
fun RemoteLogsTab(detail: MinerDetailUi?) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Logs")
        return
    }
    RemoteTabColumn(title = "Logs — ${detail.displayName}") {
        // The same on-location-only placeholder the unified detail shows.
        UnavailableSection("Logs", "Miner event and kernel logs")
        OnLocationNote(
            text = "Available on location only — connect on ${detail.displayName}'s Wi-Fi to view its logs."
        )
    }
}

/** No miner selected yet: point at the Home fleet list instead of a dead end. */
@Composable
private fun SelectMinerPlaceholder(tabName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tabName,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        UnavailableSection(
            tabName,
            "Select a miner on Home first — its $tabName details will appear here. On location, with the miner connected locally, this tab is fully live."
        )
    }
}

@Composable
private fun RemoteTabColumn(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun OnLocationNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
