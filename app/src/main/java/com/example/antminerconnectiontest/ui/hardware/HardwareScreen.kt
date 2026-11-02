package com.example.antminerconnectiontest.ui.hardware

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.ui.components.MinerDataState
import com.example.antminerconnectiontest.ui.components.MinerDataStateCard
import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import com.example.antminerconnectiontest.ui.components.MinerHardwareSections

/**
 * The Hardware tab for a LIVE selected machine (v0.2.0): the same
 * unified detail sections a cloud machine gets (detail parity) —
 * temperatures, session metrics, hash boards, fans, health — driven by
 * live local data. Loading / error / offline keep their honest state
 * cards instead of a fake "dead miner" dashboard. The selected-machine
 * context header is rendered by the shell above this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(
    profile: MinerProfile,
    data: MinerDashboardData?,
    detail: MinerDetailUi,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    dashboardError: String? = null,
    onRetry: (() -> Unit)? = null
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onManualRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                data != null && data.isOnline -> MinerHardwareSections(detail)
                data == null && dashboardError == null -> MinerDataStateCard(
                    state = MinerDataState.LOADING,
                    host = profile.host
                )
                dashboardError != null -> MinerDataStateCard(
                    state = MinerDataState.ERROR,
                    host = profile.host,
                    message = dashboardError,
                    onRetry = onRetry ?: onManualRefresh
                )
                else -> MinerDataStateCard(
                    state = MinerDataState.OFFLINE,
                    host = profile.host,
                    onRetry = onRetry ?: onManualRefresh
                )
            }
        }
    }
}
