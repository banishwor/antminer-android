package com.example.antminerconnectiontest.data.scanner

import com.example.antminerconnectiontest.data.AntminerClient
import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.parser.AntminerJsonParsers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class DiscoveryStatus {
    CONFIRMED_ANTMINER,
    POSSIBLE_ANTMINER_AUTH_REQUIRED,
    UNSUPPORTED_DEVICE,
    OFFLINE
}

data class DiscoveredMiner(
    val ip: String,
    val modelName: String? = null,
    val workerName: String? = null,
    val macAddress: String? = null,
    val status: DiscoveryStatus,
    val responseTimeMs: Long = 0
)

data class ScanProgress(
    val scannedCount: Int,
    val totalCount: Int,
    val activeIp: String,
    val isScanning: Boolean,
    val discoveredMiners: List<DiscoveredMiner> = emptyList()
)

data class MinerVerificationResult(
    val isConfirmed: Boolean,
    val modelName: String? = null,
    val workerName: String? = null,
    val macAddress: String? = null,
    val effectivePassword: String = "root"
)

/**
 * Device probe for checking reachability of port 80.
 */
class DeviceProbe {
    suspend fun isHttpReachable(ip: String, port: Int = 80, timeoutMs: Int = 600): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

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

/**
 * Local network scanner that derives current subnet and probes IPs with bounded concurrency.
 */
class NetworkScanner(
    private val probe: DeviceProbe = DeviceProbe(),
    private val detector: AntminerDetector = AntminerDetector()
) {
    /**
     * Determines local candidate IP range from active network interfaces.
     * Does not assume 192.168.1.x.
     */
    fun getLocalSubnetIps(): List<String> {
        val ipList = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        val parts = hostAddress.split(".")
                        if (parts.size == 4) {
                            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            // Scan hosts 1..254 on the /24 subnet
                            for (i in 1..254) {
                                ipList.add("$prefix.$i")
                            }
                            return ipList
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to 192.168.1.1..254 if interfaces cannot be queried
        }

        if (ipList.isEmpty()) {
            for (i in 1..254) {
                ipList.add("192.168.1.$i")
            }
        }
        return ipList
    }

    /**
     * Scans the subnet with bounded concurrency (16 workers) and emits progress updates.
     */
    fun scanSubnet(
        candidateIps: List<String> = getLocalSubnetIps()
    ): Flow<ScanProgress> = flow {
        val total = candidateIps.size
        val discovered = mutableListOf<DiscoveredMiner>()
        val semaphore = Semaphore(16) // Max 16 concurrent probes
        val progressMutex = Mutex()
        var completed = 0

        coroutineScope {
            val jobs = mutableListOf<Job>()
            for (ip in candidateIps) {
                val job = launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val startTime = System.currentTimeMillis()
                        val isReachable = probe.isHttpReachable(ip)
                        val duration = System.currentTimeMillis() - startTime

                        if (isReachable) {
                            val status = detector.detectSignature(ip)
                            if (status == DiscoveryStatus.CONFIRMED_ANTMINER || status == DiscoveryStatus.POSSIBLE_ANTMINER_AUTH_REQUIRED) {
                                val miner = DiscoveredMiner(
                                    ip = ip,
                                    status = status,
                                    modelName = if (status == DiscoveryStatus.CONFIRMED_ANTMINER) "Antminer (Confirmed)" else "Antminer (Auth Required)",
                                    responseTimeMs = duration
                                )
                                progressMutex.withLock {
                                    discovered.add(miner)
                                }
                            }
                        }

                        progressMutex.withLock {
                            completed++
                        }
                    }
                }
                jobs.add(job)
            }

            // Monitor and emit progress while workers execute
            while (completed < total) {
                emit(
                    ScanProgress(
                        scannedCount = completed,
                        totalCount = total,
                        activeIp = candidateIps.getOrNull(completed) ?: "",
                        isScanning = true,
                        discoveredMiners = discovered.toList()
                    )
                )
                delay(120)
            }

            jobs.joinAll()
        }

        // Final completion emission
        emit(
            ScanProgress(
                scannedCount = total,
                totalCount = total,
                activeIp = "Complete",
                isScanning = false,
                discoveredMiners = discovered.toList()
            )
        )
    }

    /**
     * Rapidly searches the local subnet for a specific miner by its workerName (e.g. "banishwor.001").
     * Uses 32 concurrent workers with a 450ms probe timeout to finish within 1-2 seconds.
     */
    suspend fun findMinerByWorkerName(
        workerName: String,
        username: String = "root",
        password: String = "root",
        candidateIps: List<String> = getLocalSubnetIps()
    ): String? = withContext(Dispatchers.IO) {
        if (workerName.isBlank()) return@withContext null
        val targetWorker = workerName.trim().lowercase()
        val semaphore = Semaphore(32)
        var resolvedIp: String? = null

        coroutineScope {
            val jobs = candidateIps.map { ip ->
                launch {
                    semaphore.withPermit {
                        if (resolvedIp != null) return@withPermit
                        if (probe.isHttpReachable(ip, timeoutMs = 450)) {
                            val worker = detector.fetchWorkerName(ip, username, password)
                            if (worker != null && worker.trim().lowercase() == targetWorker) {
                                resolvedIp = ip
                                this@coroutineScope.cancel()
                            }
                        }
                    }
                }
            }
            try {
                jobs.joinAll()
            } catch (_: CancellationException) {}
        }
        resolvedIp
    }
}
