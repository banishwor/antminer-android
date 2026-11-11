package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.domain.model.LiveIdentity
import com.example.antminerconnectiontest.domain.model.MinerMerge
import com.example.antminerconnectiontest.domain.model.MinerProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight background poller that queries real-time hashrates for all saved miners
 * to populate the Live Hashrate Race Leaderboard.
 *
 * Identity rule (v0.5.3): a rate is assigned to a profile only when the
 * miner that answered at the profile's address proves — through its own
 * pool config — that it IS that profile's machine (see [LiveIdentity]).
 * Private IPs repeat across sites, so an address alone proves nothing:
 * profiles are grouped by endpoint, each endpoint is probed once, and
 * its single physical reading is routed to the one identity it proves.
 */
class MultiMinerPoller(
    private val readApi: MinerReadApi = AntminerReadApiImpl()
) {

    private val _minerHashrates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val minerHashrates: StateFlow<Map<String, Double>> = _minerHashrates.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling(
        scope: CoroutineScope,
        isWifiConnected: () -> Boolean = { true },
        profilesProvider: () -> List<MinerProfile>
    ) {
        if (pollJob?.isActive == true) return

        pollJob = scope.launch {
            while (isActive) {
                if (isWifiConnected()) {
                    val profiles = profilesProvider()
                    if (profiles.size >= 2) {
                        // Wholesale replacement: profiles whose identity
                        // is not proven this cycle lose any stale rate.
                        _minerHashrates.value = pollRates(profiles)
                    }
                } else {
                    _minerHashrates.value = emptyMap()
                }
                delay(15000L) // 15s refresh interval
            }
        }
    }

    /**
     * One identity-verified race poll: profile id → hashrate (TH/s) for
     * every profile whose machine PROVED its identity this cycle.
     * Profiles sharing one host are probed once (with the first
     * profile's credentials — one physical endpoint answers) and the
     * reading goes to at most one of them: the one whose worker the
     * answering miner reported. When identity cannot be proven for a
     * known-worker profile (different worker answered, pools unreadable),
     * that profile gets no rate.
     */
    internal suspend fun pollRates(profiles: List<MinerProfile>): Map<String, Double> {
        val byHost = profiles.groupBy { it.host.trim() }
        val out = mutableMapOf<String, Double>()
        withContext(Dispatchers.IO) {
            val probed = byHost.values.map { group ->
                async {
                    val probe = group.first()
                    val summary = readApi.getSummary(probe).getOrNull()
                    if (summary == null) {
                        null
                    } else {
                        val rate = summary.hashrate5s.takeIf { it > 0.0 }
                            ?: summary.hashrateAvg
                        val pools = readApi.getPools(probe).getOrNull()
                        Triple(group, rate, pools)
                    }
                }
            }.awaitAll()
            for (result in probed) {
                val (group, rate, pools) = result ?: continue
                if (group.size == 1) {
                    val profile = group.single()
                    // A known worker must be proven by the endpoint's
                    // own pools; a profile that has not learned its
                    // worker yet has no identity a reading could
                    // contradict (the learn case).
                    if (LiveIdentity.decide(profile.workerName, pools.orEmpty()).isProven) {
                        out[profile.id] = rate
                    }
                } else {
                    // Shared address: route the one physical reading to
                    // the profile whose worker the answering miner
                    // proved — never to both, never on a guess.
                    val actual = pools?.let { LiveIdentity.poolWorker(it) } ?: continue
                    val actualNorm = MinerMerge.normalizeWorkerName(actual)
                    if (actualNorm.isBlank()) continue
                    val match = group.firstOrNull {
                        MinerMerge.normalizeWorkerName(it.workerName) == actualNorm
                    } ?: continue
                    out[match.id] = rate
                }
            }
        }
        return out
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun updateHashrate(profileId: String, hashrateTh: Double) {
        val current = _minerHashrates.value.toMutableMap()
        current[profileId] = hashrateTh
        _minerHashrates.value = current
    }
}
