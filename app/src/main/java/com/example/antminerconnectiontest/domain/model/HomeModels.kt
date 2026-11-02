package com.example.antminerconnectiontest.domain.model

import com.example.antminerconnectiontest.ui.components.MinerDetailUi

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
