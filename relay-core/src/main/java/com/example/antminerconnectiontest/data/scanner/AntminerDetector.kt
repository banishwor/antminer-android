package com.example.antminerconnectiontest.data.scanner

import com.example.antminerconnectiontest.data.AntminerClient
import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.parser.AntminerJsonParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit


enum class DiscoveryStatus {
    CONFIRMED_ANTMINER,
    POSSIBLE_ANTMINER_AUTH_REQUIRED,
    UNSUPPORTED_DEVICE,
    OFFLINE
}

data class MinerVerificationResult(
    val isConfirmed: Boolean,
    val modelName: String? = null,
    val workerName: String? = null,
    val macAddress: String? = null,
    val effectivePassword: String = "root"
)

/**
 * Detector for identifying Antminer signature from an HTTP service.
 */
class AntminerDetector(
    private val client: AntminerClient = AntminerClient()
) {
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .build()

    /**
     * Probes an IP without credentials to detect Antminer Digest Realm or open info.
     */
    suspend fun detectSignature(ip: String): DiscoveryStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip/cgi-bin/get_system_info.cgi")
                .header("User-Agent", "AntminerScanner/1.0")
                .get()
                .build()

            probeClient.newCall(request).execute().use { response ->
                val authHeader = response.header("WWW-Authenticate") ?: ""
                val code = response.code

                when {
                    code == 401 && (authHeader.contains("antMiner", ignoreCase = true) || authHeader.contains("Digest", ignoreCase = true)) -> {
                        // Quick check if default root/root works
                        val rootCheck = client.fetchEndpoint(ip, "root", "root", "/cgi-bin/get_system_info.cgi")
                        if (rootCheck is ConnectionResult.Success) {
                            DiscoveryStatus.CONFIRMED_ANTMINER
                        } else {
                            DiscoveryStatus.POSSIBLE_ANTMINER_AUTH_REQUIRED
                        }
                    }
                    code == 200 -> {
                        val body = response.body?.string().orEmpty()
                        if (body.contains("minertype", ignoreCase = true) || body.contains("antminer", ignoreCase = true)) {
                            DiscoveryStatus.CONFIRMED_ANTMINER
                        } else {
                            DiscoveryStatus.UNSUPPORTED_DEVICE
                        }
                    }
                    else -> DiscoveryStatus.UNSUPPORTED_DEVICE
                }
            }
        } catch (_: Exception) {
            DiscoveryStatus.OFFLINE
        }
    }

    /**
     * Confirms the Antminer device with credentials using get_system_info.cgi and pools.cgi.
     * Tries given password first, and auto-falls back to "root".
     */
    suspend fun verifyMinerWithCredentials(
        ip: String,
        username: String = "root",
        password: String = "root"
    ): MinerVerificationResult = withContext(Dispatchers.IO) {
        val passwordsToTry = mutableListOf<String>()
        val effectivePass = password.ifBlank { "root" }
        passwordsToTry.add(effectivePass)
        if (effectivePass != "root") {
            passwordsToTry.add("root")
        }

        for (pass in passwordsToTry) {
            val result = client.fetchEndpoint(
                rawIp = ip,
                username = username.ifBlank { "root" },
                password = pass,
                endpointPath = "/cgi-bin/get_system_info.cgi"
            )

            if (result is ConnectionResult.Success) {
                val info = try {
                    AntminerJsonParsers.parseSystemInfo(result.responseBody)
                } catch (_: Exception) { null }
                val model = info?.minerType?.ifBlank { "Antminer" } ?: "Antminer"
                val mac = info?.mac.orEmpty()

                // Fetch pools to capture the permanent workerName (e.g. banishwor.001)
                val poolRes = client.fetchEndpoint(
                    rawIp = ip,
                    username = username.ifBlank { "root" },
                    password = pass,
                    endpointPath = "/cgi-bin/pools.cgi"
                )
                val worker = if (poolRes is ConnectionResult.Success) {
                    try {
                        AntminerJsonParsers.parsePools(poolRes.responseBody).firstOrNull()?.user
                    } catch (_: Exception) { null }
                } else null

                return@withContext MinerVerificationResult(
                    isConfirmed = true,
                    modelName = model,
                    workerName = worker,
                    macAddress = mac,
                    effectivePassword = pass
                )
            }
        }
        MinerVerificationResult(isConfirmed = false)
    }

    /**
     * Attempts to fetch the primary workerName from an IP using credentials.
     */
    suspend fun fetchWorkerName(ip: String, username: String = "root", pass: String = "root"): String? = withContext(Dispatchers.IO) {
        val passwordsToTry = if (pass.isNotBlank() && pass != "root") listOf(pass, "root") else listOf(pass.ifBlank { "root" })
        for (p in passwordsToTry) {
            val res = client.fetchEndpoint(ip, username.ifBlank { "root" }, p, "/cgi-bin/pools.cgi")
            if (res is ConnectionResult.Success) {
                try {
                    val pools = AntminerJsonParsers.parsePools(res.responseBody)
                    val user = pools.firstOrNull()?.user
                    if (!user.isNullOrBlank()) return@withContext user
                } catch (_: Exception) {}
            }
        }
        null
    }
}
