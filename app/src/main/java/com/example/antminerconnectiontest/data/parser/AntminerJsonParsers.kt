package com.example.antminerconnectiontest.data.parser

import com.example.antminerconnectiontest.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

object AntminerJsonParsers {

    /**
     * Parses get_system_info.cgi response
     */
    fun parseSystemInfo(jsonString: String): MinerSystemInfo {
        val root = JSONObject(jsonString)
        return MinerSystemInfo(
            minerType = root.optString("minertype", root.optString("miner_type", root.optString("model", "Antminer S19k Pro"))),
            nettype = root.optString("nettype", root.optString("net_type", "DHCP")),
            netdevice = root.optString("netdevice", root.optString("net_device", "eth0")),
            mac = root.optString("macaddr", root.optString("mac", root.optString("mac_address", "00:00:00:00:00:00"))),
            hostname = root.optString("hostname", "Antminer"),
            ip = root.optString("ipaddress", root.optString("ip", "")),
            netmask = root.optString("netmask", ""),
            systemMode = root.optString("system_mode", root.optString("mode", "GNU/Linux")),
            systemVersion = root.optString("system_version", root.optString("version", "Release")),
            filesystemVersion = root.optString("system_filesystem_version", root.optString("filesystem_version", "")),
            kernelVersion = root.optString("system_kernel_version", ""),
            firmwareType = root.optString("firmware_type", "Release"),
            serialNumber = root.optString("serinum", root.optString("serial_number", root.optString("sn", "")))
        )
    }

    /**
     * Parses summary.cgi response
     */
    fun parseSummary(jsonString: String): MinerSummary {
        val root = JSONObject(jsonString)
        val summaryObj = when {
            root.has("SUMMARY") -> {
                val array = root.optJSONArray("SUMMARY")
                if (array != null && array.length() > 0) array.getJSONObject(0) else root
            }
            root.has("summary") -> {
                val array = root.optJSONArray("summary")
                if (array != null && array.length() > 0) array.getJSONObject(0) else root
            }
            else -> root
        }

        val elapsed = summaryObj.optLong("elapsed", summaryObj.optLong("Elapsed", summaryObj.optLong("uptime", 0L)))

        // GH/s to TH/s conversion (divide by 1000)
        val ghs5s = parseGhsField(
            summaryObj,
            "rate_5s", "GHS 5s", "GHS5s", "ghs_5s", "ghs5s",
            "rate5s", "rate_real", "rate_realtime", "rt_rate", "realtime_rate",
            "hs_5s", "hashrate_5s", "rate", "hashrate", "GHS"
        )

        val ghsAv = parseGhsField(
            summaryObj,
            "rate_avg", "GHS av", "GHSav", "ghs_av", "ghsav",
            "rate_av", "rateav", "rateavg",
            "hs_av", "hashrate_av", "avg_rate"
        )

        val ghs30m = parseGhsField(
            summaryObj,
            "rate_30m", "GHS 30m", "GHS30m", "ghs_30m", "ghs30m",
            "rate30m", "hs_30m", "hashrate_30m"
        )

        val ghsTarget = parseGhsField(
            summaryObj,
            "rate_ideal", "GHS target", "GHStarget", "GHS ideal", "GHSideal",
            "ghs_target", "ghs_ideal", "rate_target", "rateideal", "ratetarget"
        )

        val hwAll = summaryObj.optLong("hw_all", summaryObj.optLong("hw", 0L))
        val bestShare = summaryObj.optLong("bestshare", 0L)

        // Parse health from status array or flat keys
        val statusArray = summaryObj.optJSONArray("status")
        val rateHealth = if (statusArray != null) isStatusArrayTypeOk(statusArray, "rate") else isHealthOk(summaryObj, "rate")
        val netHealth = if (statusArray != null) isStatusArrayTypeOk(statusArray, "network", "net") else isHealthOk(summaryObj, "network", "net")
        val fanHealth = if (statusArray != null) isStatusArrayTypeOk(statusArray, "fans", "fan") else isHealthOk(summaryObj, "fans", "fan")
        val tempHealth = if (statusArray != null) isStatusArrayTypeOk(statusArray, "temp", "temperature") else isHealthOk(summaryObj, "temp", "temperature")

        return MinerSummary(
            elapsedSeconds = elapsed,
            hashrate5s = ghs5s / 1000.0,
            hashrateAvg = ghsAv / 1000.0,
            hashrate30m = ghs30m / 1000.0,
            hashrateIdeal = ghsTarget / 1000.0,
            hwAll = hwAll,
            bestShare = bestShare,
            rateHealth = rateHealth,
            networkHealth = netHealth,
            fansHealth = fanHealth,
            tempHealth = tempHealth
        )
    }

    /**
     * Parses stats.cgi response
     */
    fun parseStats(jsonString: String): MinerStats {
        val root = JSONObject(jsonString)
        val statsObj = when {
            root.has("STATS") -> {
                val array = root.optJSONArray("STATS")
                if (array != null && array.length() > 0) array.getJSONObject(0) else root
            }
            root.has("stats") -> {
                val array = root.optJSONArray("stats")
                if (array != null && array.length() > 0) array.getJSONObject(0) else root
            }
            else -> root
        }

        val chains = mutableListOf<ChainStats>()
        var totalHwErrors = 0L
        var globalMaxChipTemp = 0
        var globalMaxPcbTemp = 0

        // Parse chains
        val chainArray = statsObj.optJSONArray("chain") ?: statsObj.optJSONArray("chains")
        if (chainArray != null) {
            for (i in 0 until chainArray.length()) {
                val c = chainArray.optJSONObject(i) ?: continue
                val rawIdx = c.optInt("index", i)
                val chainDisplayIdx = rawIdx + 1 // 1-based index (Chain 1, 2, 3)

                val freq = parseGhsValue(c.opt("freq_avg") ?: c.opt("freq") ?: c.opt("frequency"))
                val realGhs = parseGhsField(c, "rate_real", "rate", "rate_5s", "hashrate", "ghs_real", "ghs_5s")
                val idealGhs = parseGhsField(c, "rate_ideal", "rate_target", "ghs_ideal", "ghs_target")
                val asicCount = c.optInt("asic_num", c.optInt("asics", 77))
                val asicStr = c.optString("asic", "").trim()

                val chipTemps = extractIntList(c.opt("temp_chip") ?: c.opt("temp_chips"))
                val pcbTemps = extractIntList(c.opt("temp_pcb") ?: c.opt("temp_pcbs"))
                val picTemps = extractIntList(c.opt("temp_pic") ?: c.opt("temp_pics"))

                val maxChip = chipTemps.filter { it > 0 }.maxOrNull() ?: 0
                val minChip = chipTemps.filter { it > 0 }.minOrNull() ?: 0
                val maxPcb = pcbTemps.filter { it > 0 }.maxOrNull() ?: 0
                val minPcb = pcbTemps.filter { it > 0 }.minOrNull() ?: 0
                val maxPic = picTemps.filter { it > 0 }.maxOrNull() ?: 0

                if (maxChip > globalMaxChipTemp) globalMaxChipTemp = maxChip
                if (maxPcb > globalMaxPcbTemp) globalMaxPcbTemp = maxPcb

                val hw = c.optLong("hw", c.optLong("hw_errors", 0L))
                totalHwErrors += hw

                val hwpStr = c.optString("hwp", "0")
                val hwp = hwpStr.toDoubleOrNull() ?: 0.0
                val eeprom = c.optBoolean("eeprom_loaded", c.optString("eeprom", "loaded").equals("loaded", ignoreCase = true))
                val sn = c.optString("sn", c.optString("serial_number", ""))

                chains.add(
                    ChainStats(
                        chainIndex = chainDisplayIdx,
                        rawIndex = rawIdx,
                        realHashrateTh = realGhs / 1000.0,
                        idealHashrateTh = idealGhs / 1000.0,
                        frequencyMhz = freq,
                        asicCount = asicCount,
                        asicStatusString = asicStr,
                        chipMaxTemp = maxChip,
                        chipMinTemp = minChip,
                        chipTemps = chipTemps,
                        pcbMaxTemp = maxPcb,
                        pcbMinTemp = minPcb,
                        pcbTemps = pcbTemps,
                        picMaxTemp = maxPic,
                        picTemps = picTemps,
                        hwErrors = hw,
                        hwp = hwp,
                        eepromLoaded = eeprom,
                        serialNumber = sn
                    )
                )
            }
        }

        // Parse fans: Handles array of numbers [7000, 7000, 5991, 5991] or array of objects or individual keys
        val fans = mutableListOf<FanStats>()
        var globalMaxFanRpm = 0

        val fanArray = statsObj.optJSONArray("fan") ?: statsObj.optJSONArray("fans")
        if (fanArray != null) {
            for (i in 0 until fanArray.length()) {
                val item = fanArray.opt(i)
                val (idx, speed) = when (item) {
                    is Number -> Pair(i + 1, item.toInt())
                    is JSONObject -> Pair(item.optInt("index", i + 1), item.optInt("speed", item.optInt("rpm", 0)))
                    is String -> Pair(i + 1, item.toIntOrNull() ?: 0)
                    else -> Pair(i + 1, 0)
                }

                if (speed > globalMaxFanRpm) globalMaxFanRpm = speed
                fans.add(FanStats(fanIndex = idx, rpm = speed))
            }
        } else {
            // Check for fan1, fan2, fan3, fan4 keys
            for (i in 1..8) {
                if (statsObj.has("fan$i")) {
                    val speed = statsObj.optInt("fan$i", 0)
                    if (speed > 0) {
                        if (speed > globalMaxFanRpm) globalMaxFanRpm = speed
                        fans.add(FanStats(fanIndex = i, rpm = speed))
                    }
                }
            }
        }

        return MinerStats(
            chains = chains,
            fans = fans,
            totalHwErrors = totalHwErrors,
            maxChipTemp = globalMaxChipTemp,
            maxPcbTemp = globalMaxPcbTemp,
            maxFanRpm = globalMaxFanRpm
        )
    }

    /**
     * Parses pools.cgi response
     */
    fun parsePools(jsonString: String): List<MinerPool> {
        val root = JSONObject(jsonString)
        val poolArray = root.optJSONArray("POOLS") ?: root.optJSONArray("pools") ?: JSONArray()
        val result = mutableListOf<MinerPool>()

        for (i in 0 until poolArray.length()) {
            val p = poolArray.optJSONObject(i) ?: continue
            val poolIdx = p.optInt("index", p.optInt("POOL", p.optInt("pool", i)))
            val url = p.optString("url", p.optString("URL", ""))
            val user = p.optString("user", p.optString("User", ""))
            val status = p.optString("status", p.optString("Status", "Alive"))
            val priority = p.optInt("priority", p.optInt("Priority", i))
            val accepted = p.optLong("accepted", p.optLong("Accepted", 0L))
            val rejected = p.optLong("rejected", p.optLong("Rejected", 0L))
            val stale = p.optLong("stale", p.optLong("Stale", 0L))
            val getworks = p.optLong("getworks", 0L)
            val lstime = p.optString("lstime", p.optString("Last Share Time", ""))
            val diff = p.optString("diff", p.optString("Diff", ""))

            result.add(
                MinerPool(
                    index = poolIdx,
                    url = url,
                    user = user,
                    status = status,
                    priority = priority,
                    acceptedShares = accepted,
                    rejectedShares = rejected,
                    staleShares = stale,
                    getworks = getworks,
                    lastShareTimeString = lstime,
                    diff = diff
                )
            )
        }

        return result.sortedBy { it.priority }
    }

    /**
     * Parses warning.cgi response
     */
    fun parseWarnings(jsonString: String): MinerWarnings {
        val root = JSONObject(jsonString)
        val warnings = mutableListOf<String>()

        if (root.has("error_message")) {
            val msg = root.optString("error_message", "").trim()
            if (msg.isNotBlank()) warnings.add(msg)
        }

        val warningArray = root.optJSONArray("WARNING") ?: root.optJSONArray("warning")
        if (warningArray != null) {
            for (i in 0 until warningArray.length()) {
                val item = warningArray.opt(i)
                if (item is JSONObject) {
                    val msg = item.optString("error_message", item.optString("message", "")).trim()
                    if (msg.isNotBlank()) warnings.add(msg)
                } else if (item is String && item.isNotBlank()) {
                    warnings.add(item.trim())
                }
            }
        }

        return MinerWarnings(activeWarnings = warnings)
    }

    /**
     * Parses chart.cgi response
     */
    fun parseChart(jsonString: String): MinerChartData {
        val root = JSONObject(jsonString)
        val rateArray = root.optJSONArray("RATE") ?: root.optJSONArray("rate")
        if (rateArray == null || rateArray.length() == 0) {
            return MinerChartData()
        }

        val rateObj = rateArray.getJSONObject(0)
        val unit = rateObj.optString("unit", "GH/s")
        val xAxisArray = rateObj.optJSONArray("xAxis") ?: JSONArray()
        val seriesArray = rateObj.optJSONArray("series") ?: JSONArray()

        val timeLabels = mutableListOf<String>()
        for (i in 0 until xAxisArray.length()) {
            timeLabels.add(xAxisArray.optString(i, "$i"))
        }

        val seriesList = mutableListOf<List<Double>>()
        for (s in 0 until seriesArray.length()) {
            val sObj = seriesArray.optJSONObject(s) ?: continue
            val dataArray = sObj.optJSONArray("data") ?: JSONArray()
            val dataPoints = mutableListOf<Double>()
            for (d in 0 until dataArray.length()) {
                val valueGhs = dataArray.optDouble(d, 0.0)
                // Convert GH/s to TH/s
                dataPoints.add(valueGhs / 1000.0)
            }
            seriesList.add(dataPoints)
        }

        val points = mutableListOf<ChartTimePoint>()
        for (t in timeLabels.indices) {
            val label = timeLabels[t]
            val chainValues = seriesList.mapNotNull { it.getOrNull(t) }
            val totalTh = chainValues.sum()
            points.add(
                ChartTimePoint(
                    timeLabel = label,
                    totalHashrateTh = totalTh,
                    chainHashratesTh = chainValues
                )
            )
        }

        return MinerChartData(unit = "TH/s", timePoints = points)
    }

    // --- Helper parsing methods ---

    private fun parseGhsField(obj: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (obj.has(key)) {
                val v = parseGhsValue(obj.opt(key))
                if (v > 0.0) return v
            }
        }
        return 0.0
    }

    private fun parseGhsValue(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.replace(",", "").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun isStatusArrayTypeOk(array: JSONArray, vararg types: String): Boolean {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val itemType = item.optString("type", "")
            if (types.any { it.equals(itemType, ignoreCase = true) }) {
                val status = item.optString("status", "")
                val code = item.optInt("code", 0)
                return status.equals("s", ignoreCase = true) || status.equals("OK", ignoreCase = true) || code == 0
            }
        }
        return true
    }

    private fun isHealthOk(obj: JSONObject, vararg keys: String): Boolean {
        for (key in keys) {
            if (obj.has(key)) {
                val value = obj.opt(key)
                return when (value) {
                    is Boolean -> value
                    is String -> value.equals("OK", ignoreCase = true) || value.equals("s", ignoreCase = true) || value.equals("Normal", ignoreCase = true) || value.equals("true", ignoreCase = true)
                    is Number -> value.toInt() == 1 || value.toInt() == 0
                    else -> true
                }
            }
        }
        return true
    }

    private fun extractIntList(value: Any?): List<Int> {
        val result = mutableListOf<Int>()
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val num = value.optInt(i, -1)
                    if (num != -1) result.add(num)
                }
            }
            is String -> {
                value.split(",", " ", "-").forEach { s ->
                    s.trim().toIntOrNull()?.let { result.add(it) }
                }
            }
            is Number -> result.add(value.toInt())
        }
        return result
    }
}
