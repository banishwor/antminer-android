package com.example.antminerconnectiontest.data.analysis

import com.example.antminerconnectiontest.domain.model.DailyMinerAnalysis
import com.example.antminerconnectiontest.domain.model.DailyMinerEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Analyzes extracted Antminer log directories and produces compact, historical DailyMinerAnalysis records.
 */
object DailyLogAnalyzer {

    // Matches: 2026-09-15 14:04:44 chain 2 ctrlboard temp: 040-051 , failure:000-000
    private val TEMP_LINE_REGEX = Regex(
        """^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})\s+chain\s+(\d+)\s+ctrlboard\s+temp:\s*(\d+)-(\d+)\s*,\s*failure:([0-9a-fA-F-]+)"""
    )

    // Matches: 2026-09-15 14:13:44 30m avg rate is 122420.03 in 30 mins
    private val HASHRATE_LINE_REGEX = Regex(
        """^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})\s+30m avg rate is ([\d.]+)\s+in 30 mins"""
    )

    // Matches: Miner compile time: ... type: Antminer S19k Pro
    private val MINER_TYPE_REGEX = Regex("""type:\s*([^\r\n,]+)""")
    private val MINER_SN_REGEX = Regex("""Miner sn:\s*([^\r\n,]+)""")

    /**
     * Analyzes all target dates from an extracted Antminer root directory (containing nvdata/).
     *
     * @param extractedRoot The folder containing the unpacked nvdata/ hierarchy.
     * @param minerId ID of the active miner profile.
     * @param targetRawDateIds List of raw date identifiers to analyze, e.g. ["/2026-09/15", "/2026-09/16"].
     * @return List of parsed DailyMinerAnalysis objects.
     */
    fun analyzeExtractedData(
        extractedRoot: File,
        minerId: String,
        targetRawDateIds: List<String>
    ): List<DailyMinerAnalysis> {
        val results = mutableListOf<DailyMinerAnalysis>()

        for (rawDateId in targetRawDateIds) {
            val dateStr = convertRawIdToDate(rawDateId)
            val analysis = analyzeSingleDate(extractedRoot, minerId, rawDateId, dateStr)
            results.add(analysis)
        }

        return results
    }

    /**
     * Analyzes a single calendar date across all sessions in the extracted folder.
     */
    fun analyzeSingleDate(
        extractedRoot: File,
        minerId: String,
        rawDateId: String,
        dateStr: String
    ): DailyMinerAnalysis {
        val parts = rawDateId.trimStart('/').split('/')
        val dateDir = if (parts.size >= 2) {
            File(extractedRoot, "nvdata/${parts[0]}/${parts[1]}")
        } else {
            findDateDirectory(extractedRoot, dateStr)
        }

        val sessionDirs = if (dateDir.exists() && dateDir.isDirectory) {
            dateDir.listFiles { f -> f.isDirectory && f.name.startsWith("cglog_init_") }
                ?.toList()
                ?.sortedBy { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }

        var maxChipTemp = 0
        var maxChipTempTime = ""
        var maxChipTempChain = 0

        val chainMaxChipTemps = mutableMapOf<Int, Int>()
        val chainMaxChipTimes = mutableMapOf<Int, String>()

        var maxPcbTemp = 0
        var maxPcbTempTime = ""
        var maxPcbTempChain = 0

        var temperatureRecordCount = 0L
        var hardwareErrorCount = 0

        val hashrateReadings = mutableListOf<Double>()
        var detectedMinerType = ""
        var detectedMinerSn = ""
        val events = mutableListOf<DailyMinerEvent>()

        // 1. Process temperature logs across all sessions for this date
        sessionDirs.forEachIndexed { sessionIndex, sessionDir ->
            // Record reboot events for sessions after the first
            if (sessionIndex > 0) {
                val sessionTime = parseTimeFromSessionDir(sessionDir.name)
                events.add(
                    DailyMinerEvent(
                        timestamp = sessionTime,
                        type = "REBOOT",
                        description = "Miner service restart / watchdog reboot"
                    )
                )
            }

            // Find all temp.log* files in this session directory
            val tempFiles = sessionDir.listFiles { f ->
                f.isFile && (f.name == "temp.log" || f.name.startsWith("temp.log."))
            } ?: emptyArray()

            for (tempFile in tempFiles) {
                try {
                    tempFile.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            // FAST PATH: Line MUST start with the exact calendar date (e.g. "2026-09-15")
                            // This strictly excludes boot timestamps (e.g. "2015-01-01") and rollover days
                            if (!line.startsWith(dateStr)) continue

                            val match = TEMP_LINE_REGEX.find(line) ?: continue
                            val time = match.groupValues[2]
                            val chain = match.groupValues[3].toIntOrNull() ?: continue
                            val pcb = match.groupValues[4].toIntOrNull() ?: continue
                            val chip = match.groupValues[5].toIntOrNull() ?: continue
                            val failure = match.groupValues[6]

                            temperatureRecordCount++

                            // Check for hardware / ASIC failures
                            if (failure != "000-000" && failure.any { it != '0' && it != '-' }) {
                                hardwareErrorCount++
                                if (events.none { it.type == "HW_ERROR" }) {
                                    events.add(
                                        DailyMinerEvent(
                                            timestamp = time,
                                            type = "HW_ERROR",
                                            description = "Chain $chain chip failure code: $failure"
                                        )
                                    )
                                }
                            }

                            // Track global chip max
                            if (chip > maxChipTemp) {
                                maxChipTemp = chip
                                maxChipTempTime = time
                                maxChipTempChain = chain
                            }

                            // Track per-chain chip max
                            val currentChainMax = chainMaxChipTemps[chain] ?: 0
                            if (chip > currentChainMax) {
                                chainMaxChipTemps[chain] = chip
                                chainMaxChipTimes[chain] = time
                            }

                            // Track global PCB max
                            if (pcb > maxPcbTemp) {
                                maxPcbTemp = pcb
                                maxPcbTempTime = time
                                maxPcbTempChain = chain
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Process miner.log for hashrate and hardware info
            val minerLogFile = File(sessionDir, "miner.log")
            if (minerLogFile.exists() && minerLogFile.isFile) {
                try {
                    minerLogFile.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (detectedMinerType.isBlank()) {
                                MINER_TYPE_REGEX.find(line)?.let {
                                    detectedMinerType = it.groupValues[1].trim()
                                }
                            }
                            if (detectedMinerSn.isBlank()) {
                                MINER_SN_REGEX.find(line)?.let {
                                    detectedMinerSn = it.groupValues[1].trim()
                                }
                            }

                            if (line.startsWith(dateStr)) {
                                HASHRATE_LINE_REGEX.find(line)?.let {
                                    val gh = it.groupValues[3].toDoubleOrNull()
                                    if (gh != null && gh > 0.0) {
                                        hashrateReadings.add(gh / 1000.0) // Convert GH/s to TH/s
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Add temperature spike event if temperature was high
        if (maxChipTemp >= 70 && maxChipTempTime.isNotBlank()) {
            events.add(
                DailyMinerEvent(
                    timestamp = maxChipTempTime,
                    type = "TEMP_SPIKE",
                    description = "Peak chip temperature reached ${maxChipTemp}°C on Chain $maxChipTempChain"
                )
            )
        }

        val avgHashrate = if (hashrateReadings.isNotEmpty()) {
            hashrateReadings.average()
        } else {
            0.0
        }

        events.sortBy { it.timestamp }

        return DailyMinerAnalysis(
            minerId = minerId,
            date = dateStr,
            rawDateId = rawDateId,
            analyzedAt = System.currentTimeMillis(),
            maxTemperature = maxChipTemp,
            maxTemperatureTime = maxChipTempTime,
            maxTemperatureChain = maxChipTempChain,
            chainMaxTemperatures = chainMaxChipTemps,
            chainMaxTemperatureTimes = chainMaxChipTimes,
            maxPcbTemperature = maxPcbTemp,
            maxPcbTemperatureTime = maxPcbTempTime,
            maxPcbTemperatureChain = maxPcbTempChain,
            temperatureRecordCount = temperatureRecordCount,
            sessionCount = sessionDirs.size,
            restartCount = maxOf(0, sessionDirs.size - 1),
            hardwareErrorCount = hardwareErrorCount,
            avgHashrateTh = avgHashrate,
            minerType = detectedMinerType,
            minerSn = detectedMinerSn,
            events = events
        )
    }

    /**
     * Converts a raw date identifier e.g. "/2026-09/15" to standard "2026-09-15".
     */
    fun convertRawIdToDate(rawId: String): String {
        val clean = rawId.trim().trimStart('/')
        val parts = clean.split('/')
        return if (parts.size == 2) {
            "${parts[0]}-${parts[1]}"
        } else {
            clean.replace('/', '-')
        }
    }

    /**
     * Formats a date string (e.g. "2026-09-15") into human-friendly format "15 September 2026".
     */
    fun formatHumanReadableDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("d MMMM yyyy", Locale.US)
            val d = parser.parse(dateStr)
            if (d != null) formatter.format(d) else dateStr
        } catch (_: Exception) {
            dateStr
        }
    }

    /**
     * Determines if a date string corresponds to today's calendar date in local timezone.
     */
    fun isTodayDate(dateStr: String): Boolean {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return dateStr == todayStr
    }

    private fun findDateDirectory(root: File, dateStr: String): File {
        val parts = dateStr.split('-')
        if (parts.size == 3) {
            val candidate = File(root, "nvdata/${parts[0]}-${parts[1]}/${parts[2]}")
            if (candidate.exists()) return candidate
        }
        return File(root, dateStr)
    }

    private fun parseTimeFromSessionDir(dirName: String): String {
        // e.g. cglog_init_2026-09-15_13-37-02 -> 13:37:02
        val parts = dirName.split('_')
        if (parts.size >= 4) {
            return parts.last().replace('-', ':')
        }
        return "00:00:00"
    }
}
