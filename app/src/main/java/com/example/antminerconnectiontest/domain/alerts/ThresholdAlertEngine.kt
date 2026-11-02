package com.example.antminerconnectiontest.domain.alerts

import com.example.antminerconnectiontest.domain.model.MinerDashboardData

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class MinerAlert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis()
)

object ThresholdAlertEngine {

    fun evaluateAlerts(data: MinerDashboardData?): List<MinerAlert> {
        if (data == null || !data.isOnline) return emptyList()

        val alerts = mutableListOf<MinerAlert>()

        // 1. Thermal Alerts
        val maxChip = data.stats?.maxChipTemp ?: 0
        if (maxChip >= 75) {
            alerts.add(
                MinerAlert(
                    id = "temp_critical",
                    title = "Critical Chip Temperature",
                    description = "Chip temp reached ${maxChip}°C (limit 75°C). Check airflow immediately.",
                    severity = AlertSeverity.CRITICAL
                )
            )
        } else if (maxChip >= 70) {
            alerts.add(
                MinerAlert(
                    id = "temp_warning",
                    title = "High Chip Temperature",
                    description = "Chip temp is ${maxChip}°C (threshold 70°C).",
                    severity = AlertSeverity.WARNING
                )
            )
        }

        // 2. Fan RPM Alerts
        val fans = data.stats?.fans ?: emptyList()
        if (fans.isNotEmpty()) {
            val lowFans = fans.filter { it.rpm < 3000 }
            if (lowFans.isNotEmpty()) {
                alerts.add(
                    MinerAlert(
                        id = "fan_low",
                        title = "Low Fan Speed Warning",
                        description = "${lowFans.size} fan(s) spinning below 3000 RPM.",
                        severity = AlertSeverity.WARNING
                    )
                )
            }
        }

        // 3. Hashrate Drop / Stopped Alerts
        val summary = data.summary
        if (summary != null && summary.hashrateIdeal > 0) {
            if (summary.hashrate5s <= 0.0) {
                // The old condition required hashrate5s > 0, so a miner whose
                // chains stopped hashing entirely (0 TH/s while "online")
                // never produced any alert.
                alerts.add(
                    MinerAlert(
                        id = "hashrate_stopped",
                        title = "Hashrate Stopped",
                        description = "Miner is online but reporting 0 TH/s — its chains may have stopped hashing.",
                        severity = AlertSeverity.CRITICAL
                    )
                )
            } else {
                val ratio = summary.hashrate5s / summary.hashrateIdeal
                if (ratio < 0.80) {
                    alerts.add(
                        MinerAlert(
                            id = "hashrate_drop",
                            title = "Hashrate Below Target",
                            description = "Current rate (${String.format("%.1f", summary.hashrate5s)} TH/s) is >20% below ideal target.",
                            severity = AlertSeverity.WARNING
                        )
                    )
                }
            }
        }

        // 4. Primary Pool Offline Alert
        val primaryPool = data.pools.firstOrNull { it.priority == 0 }
        if (primaryPool != null && !primaryPool.status.equals("Alive", ignoreCase = true)) {
            alerts.add(
                MinerAlert(
                    id = "pool_offline",
                    title = "Primary Pool Offline",
                    description = "Primary stratum connection (${primaryPool.url}) status is ${primaryPool.status}.",
                    severity = AlertSeverity.CRITICAL
                )
            )
        }

        // 5. Native Warnings from miner warning.cgi
        if (data.warnings.hasWarnings) {
            data.warnings.activeWarnings.forEachIndexed { index, msg ->
                alerts.add(
                    MinerAlert(
                        id = "miner_native_warning_$index",
                        title = "Miner Hardware Alert",
                        description = msg,
                        severity = AlertSeverity.WARNING
                    )
                )
            }
        }

        return alerts
    }
}
