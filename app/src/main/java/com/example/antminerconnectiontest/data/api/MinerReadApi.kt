package com.example.antminerconnectiontest.data.api

import com.example.antminerconnectiontest.data.AntminerClient
import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.parser.AntminerJsonParsers
import com.example.antminerconnectiontest.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Interface for reading Antminer telemetry and logs.
 */
interface MinerReadApi {
    suspend fun getSystemInfo(profile: MinerProfile): Result<MinerSystemInfo>
    suspend fun getSummary(profile: MinerProfile): Result<MinerSummary>
    suspend fun getStats(profile: MinerProfile): Result<MinerStats>
    suspend fun getPools(profile: MinerProfile): Result<List<MinerPool>>
    suspend fun getWarnings(profile: MinerProfile): Result<MinerWarnings>
    suspend fun getChart(profile: MinerProfile): Result<MinerChartData>
    suspend fun getCurrentLog(profile: MinerProfile): Result<String>
    suspend fun getHistoryLog(profile: MinerProfile): Result<String>
    suspend fun getAvailableLogDates(profile: MinerProfile): Result<List<String>>
    suspend fun createLogBackup(profile: MinerProfile, dateIds: List<String>): Result<String>
    suspend fun downloadLogArchive(profile: MinerProfile, archiveName: String, destinationFile: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): Result<File>
}

/**
 * Implementation of MinerReadApi using the proven AntminerClient Digest Auth networking layer.
 */
class AntminerReadApiImpl(
    private val client: AntminerClient = AntminerClient()
) : MinerReadApi {

    override suspend fun getSystemInfo(profile: MinerProfile): Result<MinerSystemInfo> {
        return fetchAndParse(profile, "/cgi-bin/get_system_info.cgi") { json ->
            AntminerJsonParsers.parseSystemInfo(json)
        }
    }

    override suspend fun getSummary(profile: MinerProfile): Result<MinerSummary> {
        return fetchAndParse(profile, "/cgi-bin/summary.cgi") { json ->
            AntminerJsonParsers.parseSummary(json)
        }
    }

    override suspend fun getStats(profile: MinerProfile): Result<MinerStats> {
        return fetchAndParse(profile, "/cgi-bin/stats.cgi") { json ->
            AntminerJsonParsers.parseStats(json)
        }
    }

    override suspend fun getPools(profile: MinerProfile): Result<List<MinerPool>> {
        return fetchAndParse(profile, "/cgi-bin/pools.cgi") { json ->
            AntminerJsonParsers.parsePools(json)
        }
    }

    override suspend fun getWarnings(profile: MinerProfile): Result<MinerWarnings> {
        return fetchAndParse(profile, "/cgi-bin/warning.cgi") { json ->
            AntminerJsonParsers.parseWarnings(json)
        }
    }

    override suspend fun getChart(profile: MinerProfile): Result<MinerChartData> {
        return fetchAndParse(profile, "/cgi-bin/chart.cgi") { json ->
            AntminerJsonParsers.parseChart(json)
        }
    }

    override suspend fun getCurrentLog(profile: MinerProfile): Result<String> {
        return fetchRawText(profile, "/cgi-bin/log.cgi")
    }

    override suspend fun getHistoryLog(profile: MinerProfile): Result<String> {
        return fetchRawText(profile, "/cgi-bin/hlog.cgi")
    }

    override suspend fun getAvailableLogDates(profile: MinerProfile): Result<List<String>> {
        return fetchAndParse(profile, "/cgi-bin/dlog.cgi") { jsonStr ->
            val obj = JSONObject(jsonStr)
            val list = mutableListOf<String>()
            val array = obj.optJSONArray("log_list")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    if (item.isNotBlank()) {
                        list.add(item)
                    }
                }
            }
            list
        }
    }

    override suspend fun createLogBackup(profile: MinerProfile, dateIds: List<String>): Result<String> {
        if (dateIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("No log dates selected for backup"))
        }

        val jsonArray = JSONArray()
        dateIds.forEach { jsonArray.put(it) }

        val res = client.postJsonEndpoint(
            rawIp = profile.host,
            username = profile.username,
            password = profile.password,
            endpointPath = "/cgi-bin/create_log_backup.cgi",
            jsonBody = jsonArray.toString()
        )

        return when (res) {
            is ConnectionResult.Success -> {
                try {
                    val obj = JSONObject(res.responseBody)
                    val stats = obj.optString("stats", "")
                    val code = obj.optString("code", "")
                    val msg = obj.optString("msg", "")

                    if (stats.equals("success", ignoreCase = true) && msg.isNotBlank()) {
                        Result.success(msg)
                    } else {
                        Result.failure(Exception("Firmware rejected backup creation (code=$code, stats=$stats): $msg"))
                    }
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to parse backup creation response: ${e.message}", e))
                }
            }
            is ConnectionResult.Failure -> Result.failure(Exception(res.message))
        }
    }

    override suspend fun downloadLogArchive(
        profile: MinerProfile,
        archiveName: String,
        destinationFile: File,
        onProgress: (Long, Long) -> Unit
    ): Result<File> {
        val cleanArchiveName = archiveName.trimStart('/')
        return client.downloadFileEndpoint(
            rawIp = profile.host,
            username = profile.username,
            password = profile.password,
            endpointPath = "/log/$cleanArchiveName",
            destinationFile = destinationFile,
            onProgress = onProgress
        )
    }

    private suspend fun fetchRawText(
        profile: MinerProfile,
        endpointPath: String
    ): Result<String> {
        val result = client.fetchEndpoint(
            rawIp = profile.host,
            username = profile.username,
            password = profile.password,
            endpointPath = endpointPath
        )

        return when (result) {
            is ConnectionResult.Success -> Result.success(result.responseBody)
            is ConnectionResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    private suspend fun <T> fetchAndParse(
        profile: MinerProfile,
        endpointPath: String,
        parser: (String) -> T
    ): Result<T> {
        val result = client.fetchEndpoint(
            rawIp = profile.host,
            username = profile.username,
            password = profile.password,
            endpointPath = endpointPath
        )

        return when (result) {
            is ConnectionResult.Success -> {
                try {
                    val parsed = parser(result.responseBody)
                    Result.success(parsed)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to parse response from $endpointPath: ${e.message}", e))
                }
            }
            is ConnectionResult.Failure -> {
                Result.failure(Exception(result.message))
            }
        }
    }
}
