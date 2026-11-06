package com.example.antminerconnectiontest.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.ui.components.MinerDetailPoolSection
import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import com.example.antminerconnectiontest.ui.components.MinerHardwareSections
import com.example.antminerconnectiontest.ui.components.UnavailableSection

/**
 * Tab contents of the main shell when the selected fleet machine is
 * shown from its cloud snapshot (v0.2.0): the Hardware / Pools / Logs
 * tabs for the selected machine. They are never dead ends and never
 * fabricate data — a tab either renders what the selected miner's
 * relay snapshot honestly carries (the same section composables the
 * live tabs use — detail parity), or a placeholder that names the
 * selected miner and says the tab is fully available on location.
 * The selected-machine context header is rendered by the shell above
 * these contents. With no miner selected, the tab points back to the
 * Home fleet list.
 */

@Composable
fun RemoteHardwareTab(
    detail: MinerDetailUi?,
    /** Bottom dock clearance (v0.5.0): added to the scroll column's bottom padding. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Hardware")
        return
    }
    RemoteTabColumn(bottomInset = bottomInset) {
        MinerHardwareSections(detail)
        OnLocationNote(
            text = "Full hardware control for ${detail.displayName} is available on location — connect on the miner's Wi-Fi."
        )
    }
}

@Composable
fun RemotePoolsTab(
    detail: MinerDetailUi?,
    /** Bottom dock clearance (v0.5.0): added to the scroll column's bottom padding. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Pools")
        return
    }
    RemoteTabColumn(bottomInset = bottomInset) {
        // Only the PRIMARY pool is uploaded to the cloud — shown in
        // full. Backup pools are never invented here.
        MinerDetailPoolSection(detail)
        OnLocationNote(
            text = "Only the primary pool is uploaded. Pool configuration for ${detail.displayName} is available on location — connect on the miner's Wi-Fi to view or change all pools."
        )
    }
}

@Composable
fun RemoteLogsTab(
    detail: MinerDetailUi?,
    /** Bottom dock clearance (v0.5.0): added to the scroll column's bottom padding. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    if (detail == null) {
        SelectMinerPlaceholder(tabName = "Logs")
        return
    }
    RemoteTabColumn(bottomInset = bottomInset) {
        // Log text is never uploaded — the honest on-location panel,
        // naming the selected machine.
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
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .verticalScroll(rememberScrollState())
            // Dock clearance (v0.5.0): padding INSIDE the scrollable
            // area (after verticalScroll) so the last rows can scroll
            // fully above the floating dock.
            .padding(bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun OnLocationNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
