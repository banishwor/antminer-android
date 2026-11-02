package com.example.antminerconnectiontest.domain.model

/**
 * Single notable incident or event extracted from miner logs.
 */
data class DailyMinerEvent(
    val timestamp: String,
    val type: String, // e.g. "REBOOT", "TEMP_SPIKE", "HW_ERROR"
    val description: String
)

/**
 * Compact, persistent analysis metrics for an Antminer for a single calendar date.
 */
data class DailyMinerAnalysis(
    val minerId: String,
    val date: String, // e.g. "2026-09-15"
    val rawDateId: String, // e.g. "/2026-09/15"
    val analyzedAt: Long = System.currentTimeMillis(),
    val maxTemperature: Int = 0, // Peak chip temperature across all chains (°C)
    val maxTemperatureTime: String = "", // Exact HH:mm:ss when peak occurred
    val maxTemperatureChain: Int = 0, // Chain index where peak occurred
    val chainMaxTemperatures: Map<Int, Int> = emptyMap(), // Chain ID -> Max Chip Temp (°C)
    val chainMaxTemperatureTimes: Map<Int, String> = emptyMap(), // Chain ID -> Exact HH:mm:ss
    val maxPcbTemperature: Int = 0, // Peak PCB / inlet temp (°C)
    val maxPcbTemperatureTime: String = "",
    val maxPcbTemperatureChain: Int = 0,
    val temperatureRecordCount: Long = 0L, // Total valid temperature records processed
    val sessionCount: Int = 0, // Number of distinct cglog sessions on this date
    val restartCount: Int = 0, // Reboots on this date (sessionCount - 1)
    val hardwareErrorCount: Int = 0, // Non-zero failure flags detected
    val avgHashrateTh: Double = 0.0, // 30m avg rate in TH/s if reported in miner.log
    val minerType: String = "", // e.g. "Antminer S19k Pro"
    val minerSn: String = "", // e.g. "DGAHGMUBEABJC00B5"
    val events: List<DailyMinerEvent> = emptyList()
)

/**
 * Represents a date item in the Daily Analysis date picker.
 */
data class DailyLogDateItem(
    val rawDateId: String, // e.g. "/2026-09/15"
    val dateString: String, // e.g. "2026-09-15"
    val formattedDisplay: String, // e.g. "15 September 2026"
    val isAnalyzed: Boolean = false,
    val isToday: Boolean = false,
    val analysis: DailyMinerAnalysis? = null
)
