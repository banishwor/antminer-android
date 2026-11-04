package com.example.antminerconnectiontest.data.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


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
 * Local network scanner that derives current subnet and probes IPs with bounded concurrency.
 */
class NetworkScanner(
    private val probe: DeviceProbe = DeviceProbe(),
    private val detector: AntminerDetector = AntminerDetector()
) {
    /**
     * Determines local candidate IP range, preferring the active Wi-Fi
     * network's own address (via ConnectivityManager LinkProperties).
     *
     * Deriving the subnet from "the first network interface" could pick a
     * cellular or VPN interface and scan a carrier/tunnel /24 instead of
     * the LAN the miners are on.
     */
    fun getLocalSubnetIps(context: Context): List<String> {
        return wifiSubnetIps(context) ?: getLocalSubnetIps()
    }

    private fun wifiSubnetIps(context: Context): List<String>? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val wifiNetwork = cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: return null
            val linkProperties = cm.getLinkProperties(wifiNetwork) ?: return null
            val ipv4 = linkProperties.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?: return null
            subnetIpsForHost(ipv4.hostAddress)
        } catch (_: Exception) {
            null
        }
    }

    private fun subnetIpsForHost(hostAddress: String?): List<String>? {
        val parts = hostAddress?.split(".") ?: return null
        if (parts.size != 4) return null
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        return (1..254).map { "$prefix.$it" }
    }

    /**
     * Fallback: derive the candidate range from network interfaces when no
     * Wi-Fi link properties are available. Skips interfaces that are clearly
     * not the LAN (VPN tunnels, cellular data). Does not assume 192.168.1.x.
     */
    fun getLocalSubnetIps(): List<String> {
        val ipList = mutableListOf<String>()
        val nonLanPrefixes = listOf("tun", "ppp", "rmnet", "ccmni", "ipsec", "v4-")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                if (nonLanPrefixes.any { iface.name.startsWith(it) }) continue

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
        context: Context? = null,
        candidateIps: List<String> = if (context != null) getLocalSubnetIps(context) else getLocalSubnetIps()
    ): Flow<ScanProgress> = flow {
        val total = candidateIps.size
        val discovered = mutableListOf<DiscoveredMiner>()
        val semaphore = Semaphore(16) // Max 16 concurrent probes
        val progressMutex = Mutex()
        // Workers mutate progress from 16 concurrent coroutines; the emit
        // loop reads it from another. completed/discovered used to be a
        // plain var/list read outside the lock (data race, possible
        // ConcurrentModificationException). completed is now atomic, the
        // discovered snapshot is taken under the same mutex that guards
        // writes, and the "active IP" shown is the address actually being
        // probed rather than candidateIps[completed].
        val completed = AtomicInteger(0)
        val currentProbingIp = AtomicReference("")

        coroutineScope {
            val jobs = mutableListOf<Job>()
            for (ip in candidateIps) {
                val job = launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        currentProbingIp.set(ip)
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

                        completed.incrementAndGet()
                    }
                }
                jobs.add(job)
            }

            // Monitor and emit progress while workers execute
            while (completed.get() < total) {
                val snapshot = progressMutex.withLock { discovered.toList() }
                emit(
                    ScanProgress(
                        scannedCount = completed.get(),
                        totalCount = total,
                        activeIp = currentProbingIp.get(),
                        isScanning = true,
                        discoveredMiners = snapshot
                    )
                )
                delay(120)
            }

            jobs.joinAll()
        }

        // Final completion emission
        val finalSnapshot = progressMutex.withLock { discovered.toList() }
        emit(
            ScanProgress(
                scannedCount = total,
                totalCount = total,
                activeIp = "Complete",
                isScanning = false,
                discoveredMiners = finalSnapshot
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
        context: Context? = null,
        candidateIps: List<String> = if (context != null) getLocalSubnetIps(context) else getLocalSubnetIps()
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
