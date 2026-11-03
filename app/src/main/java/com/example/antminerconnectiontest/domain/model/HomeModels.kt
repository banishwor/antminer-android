package com.example.antminerconnectiontest.domain.model

import com.example.antminerconnectiontest.ui.components.MinerDetailUi

/**
 * When Home shows its "Add a miner" card (user rule, v0.2.3): ONLY
 * when there is no miner at all — no saved local profiles AND no
 * merged fleet entries (local or cloud). One miner of either kind is
 * enough to hide the card; it returns only at a completely empty
 * fleet, as the way to add the first machine. Adding stays reachable
 * regardless: the fleet header's "+ Add Unit" link, and the permanent
 * "Add a miner" entry on the Device tab.
 *
 * Pure and count-based so the rule is unit-testable without Compose.
 */
object AddMinerCardVisibility {
    fun shouldShow(savedProfileCount: Int, mergedFleetCount: Int): Boolean =
        savedProfileCount == 0 && mergedFleetCount == 0
}

/** What Home renders, as one decision (v0.3.3). */
enum class HomeContent {
    /** First real fleet state not in yet: skeleton placeholders only. */
    LOADING,

    /** Loaded, and the fleet is genuinely completely empty. */
    EMPTY,

    /** Loaded, with at least one miner (local or cloud) in the fleet. */
    FLEET
}

/**
 * Home's loading rule (v0.3.3): until the first REAL fleet state has
 * loaded, Home shows skeletons — never the empty-fleet content. The
 * fleet only *looks* empty before data arrives, and the settled
 * v0.2.3 rule is that the "Add a miner" card shows ONLY when the
 * loaded fleet is genuinely completely empty.
 *
 * "Loaded" is explicit state, never a timer: the persisted state has
 * been emitted into the UI state (AppUiState.isInitialStateLoaded)
 * AND — when cloud viewing is configured — the session's first cloud
 * fetch has completed, success or failure
 * (AppUiState.hasCompletedFirstCloudFetch). With no cloud configured
 * there is no fetch to wait for. Pure, so the rule is unit-testable.
 */
object HomeLoadState {

    fun isLoaded(
        isInitialStateLoaded: Boolean,
        cloudConfigured: Boolean,
        hasCompletedFirstCloudFetch: Boolean
    ): Boolean =
        isInitialStateLoaded && (!cloudConfigured || hasCompletedFirstCloudFetch)

    fun content(
        isLoaded: Boolean,
        savedProfileCount: Int,
        mergedFleetCount: Int
    ): HomeContent = when {
        !isLoaded -> HomeContent.LOADING
        AddMinerCardVisibility.shouldShow(savedProfileCount, mergedFleetCount) -> HomeContent.EMPTY
        else -> HomeContent.FLEET
    }
}

/**
 * Everything the Home hero is allowed to show — and nothing else.
 *
 * The v0.2.0 "strict strip" rule (user-approved): the selected-machine
 * hero on Home is a SUMMARY. Hash-board, fan, pool, share and log detail
 * live on the Hardware / Pools / Logs tabs only. This model is the
 * enforcement point: it is built from the full detail model but carries
 * only summary fields, so board/fan/pool data cannot reach Home by
 * accident. A unit test asserts the field set stays this small.
 */
data class HeroSummary(
    /** Phone nickname (or profile name / worker identity fallback). */
    val displayName: String,
    /** Pool worker name, shown small under the nickname. */
    val workerName: String,
    /** Miner model (e.g. "Antminer S19k Pro"), "" when unknown. */
    val modelName: String,
    /** true when the numbers come from the cloud relay snapshot. */
    val isRemote: Boolean,
    val isOnline: Boolean,
    /** "Updated 2 min ago · via relay" for a cloud machine; null for live. */
    val updatedLine: String?,
    /** "Online" / "Offline" (or the snapshot's own status text). */
    val statusText: String,
    /** Real-time hashrate, TH/s. */
    val hashrateTh: Double,
    /** Session average hashrate, TH/s. */
    val avgHashrateTh: Double,
    /** Human uptime ("2d 4h"), "" when unknown. */
    val uptimeLabel: String,
    /** The single health indicator the hero may show. */
    val health: HealthState
) {
    enum class HealthState { HEALTHY, ISSUES, UNKNOWN }

    companion object {
        fun from(detail: MinerDetailUi): HeroSummary {
            val flags = detail.health
            val health = when {
                flags == null -> HealthState.UNKNOWN
                flags.allOk -> HealthState.HEALTHY
                else -> HealthState.ISSUES
            }
            return HeroSummary(
                displayName = detail.displayName,
                workerName = detail.workerName,
                modelName = detail.modelName,
                isRemote = detail.isRemote,
                isOnline = detail.isOnline,
                updatedLine = if (detail.isRemote) detail.lastUpdatedLabel else null,
                statusText = detail.statusText.ifBlank {
                    if (detail.isOnline) "Online" else "Offline"
                },
                hashrateTh = detail.hashrateTh,
                avgHashrateTh = detail.avgHashrateTh,
                uptimeLabel = detail.uptimeLabel ?: "",
                health = health
            )
        }
    }
}
