package com.example.antminerconnectiontest.domain.model

/**
 * Representation of an Antminer connection profile.
 */
data class MinerProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val workerName: String = "",
    val macAddress: String = "",
    val displayName: String = "Antminer",
    val nickname: String = "",
    val location: String = "Default Location",
    val host: String,
    val username: String = "root",
    val password: String = "root",
    val lastConnected: Long = System.currentTimeMillis(),
    val cachedModel: String? = null
) {
    val effectiveName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            workerName.isNotBlank() -> "$workerName ($displayName)"
            else -> displayName.ifBlank { "Antminer ($host)" }
        }
}

/**
 * Parsed data from /cgi-bin/get_system_info.cgi
 */
data class MinerSystemInfo(
    val minerType: String,
    val nettype: String,
    val netdevice: String,
    val mac: String,
    val hostname: String,
    val ip: String,
    val netmask: String = "",
    val systemMode: String,
    val systemVersion: String,
    val filesystemVersion: String,
    val kernelVersion: String = "",
    val firmwareType: String,
    val serialNumber: String = ""
)

/**
 * Parsed data from /cgi-bin/summary.cgi
 */
data class MinerSummary(
    val elapsedSeconds: Long,
    val hashrate5s: Double, // in TH/s (converted from GH/s)
    val hashrateAvg: Double, // in TH/s
    val hashrate30m: Double = 0.0, // in TH/s if available
    val hashrateIdeal: Double = 0.0, // in TH/s
    val hwAll: Long = 0L,
    val bestShare: Long = 0L,
    val rateHealth: Boolean = true,
    val networkHealth: Boolean = true,
    val fansHealth: Boolean = true,
    val tempHealth: Boolean = true
) {
    val formattedUptime: String
        get() {
            val days = elapsedSeconds / 86400
            val hours = (elapsedSeconds % 86400) / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            return when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }
}

/**
 * Individual chain (hashboard) statistics from /cgi-bin/stats.cgi
 */
data class ChainStats(
    val chainIndex: Int, // 1-based display index (1, 2, 3)
    val rawIndex: Int, // 0-based index from miner
    val realHashrateTh: Double, // in TH/s
    val idealHashrateTh: Double, // in TH/s
    val frequencyMhz: Double,
    val asicCount: Int,
    val asicStatusString: String = "",
    val chipMaxTemp: Int,
    val chipMinTemp: Int,
    val chipTemps: List<Int> = emptyList(),
    val pcbMaxTemp: Int,
    val pcbMinTemp: Int,
    val pcbTemps: List<Int> = emptyList(),
    val picMaxTemp: Int = 0,
    val picTemps: List<Int> = emptyList(),
    val hwErrors: Long,
    val hwp: Double,
    val eepromLoaded: Boolean = true,
    val serialNumber: String = ""
)

/**
 * Fan information from /cgi-bin/stats.cgi
 */
data class FanStats(
    val fanIndex: Int,
    val rpm: Int
)

/**
 * Full hardware stats from /cgi-bin/stats.cgi
 */
data class MinerStats(
    val chains: List<ChainStats>,
    val fans: List<FanStats>,
    val totalHwErrors: Long,
    val maxChipTemp: Int,
    val maxPcbTemp: Int,
    val maxFanRpm: Int
)

/**
 * Mining pool configuration and performance from /cgi-bin/pools.cgi
 */
data class MinerPool(
    val index: Int,
    val url: String,
    val user: String,
    val status: String, // "Alive", "Dead", etc.
    val priority: Int, // 0 = Primary, 1 = Backup 1, 2 = Backup 2
    val acceptedShares: Long,
    val rejectedShares: Long,
    val staleShares: Long = 0,
    val getworks: Long = 0,
    val lastShareTimeString: String = "",
    val diff: String = ""
) {
    val priorityLabel: String
        get() = when (priority) {
            0 -> "Primary"
            1 -> "Backup 1"
            2 -> "Backup 2"
            else -> "Pool $priority"
        }
}

/**
 * Warnings and alert messages from /cgi-bin/warning.cgi
 */
data class MinerWarnings(
    val activeWarnings: List<String>
) {
    val hasWarnings: Boolean get() = activeWarnings.isNotEmpty()
}

/**
 * Hashrate history series from /cgi-bin/chart.cgi
 */
data class ChartTimePoint(
    val timeLabel: String,
    val totalHashrateTh: Double,
    val chainHashratesTh: List<Double> = emptyList()
)

data class MinerChartData(
    val unit: String = "TH/s",
    val timePoints: List<ChartTimePoint> = emptyList()
)

/**
 * Aggregated dashboard state combining all monitoring endpoints
 */
data class MinerDashboardData(
    /** Id of the [MinerProfile] this snapshot was fetched for. Lets every
     *  consumer verify the data actually belongs to the selected miner. */
    val profileId: String = "",
    val systemInfo: MinerSystemInfo? = null,
    val summary: MinerSummary? = null,
    val stats: MinerStats? = null,
    val pools: List<MinerPool> = emptyList(),
    val warnings: MinerWarnings = MinerWarnings(emptyList()),
    val chartData: MinerChartData = MinerChartData(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val partialErrors: List<String> = emptyList()
)
