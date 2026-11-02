package com.example.antminerconnectiontest.domain.alerts

import com.example.antminerconnectiontest.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ThresholdAlertEngineTest {

    @Test
    fun evaluateAlerts_criticalTempTriggered() {
        val stats = MinerStats(
            chains = emptyList(),
            fans = emptyList(),
            totalHwErrors = 0,
            maxChipTemp = 78, // Over 75
            maxPcbTemp = 60,
            maxFanRpm = 6000
        )

        val data = MinerDashboardData(
            stats = stats,
            isOnline = true
        )

        val alerts = ThresholdAlertEngine.evaluateAlerts(data)
        assertTrue(alerts.any { it.id == "temp_critical" && it.severity == AlertSeverity.CRITICAL })
    }

    @Test
    fun evaluateAlerts_lowFanTriggered() {
        val stats = MinerStats(
            chains = emptyList(),
            fans = listOf(FanStats(1, 2500), FanStats(2, 6000)),
            totalHwErrors = 0,
            maxChipTemp = 60,
            maxPcbTemp = 50,
            maxFanRpm = 6000
        )

        val data = MinerDashboardData(
            stats = stats,
            isOnline = true
        )

        val alerts = ThresholdAlertEngine.evaluateAlerts(data)
        assertTrue(alerts.any { it.id == "fan_low" && it.severity == AlertSeverity.WARNING })
    }
}
