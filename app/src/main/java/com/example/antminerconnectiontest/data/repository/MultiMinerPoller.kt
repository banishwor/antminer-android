package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.domain.model.MinerProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight background poller that queries real-time hashrates for all saved miners
 * to populate the Live Hashrate Race Leaderboard.
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
                        val resultMap = mutableMapOf<String, Double>()
                        withContext(Dispatchers.IO) {
                            profiles.map { profile ->
                                async {
                                    val summaryRes = readApi.getSummary(profile)
                                    val rate = summaryRes.getOrNull()?.hashrate5s
                                        ?: summaryRes.getOrNull()?.hashrateAvg
                                        ?: 0.0
                                    synchronized(resultMap) {
                                        resultMap[profile.id] = rate
                                    }
                                }
                            }.awaitAll()
                        }
                        _minerHashrates.value = resultMap
                    }
                } else {
                    _minerHashrates.value = emptyMap()
                }
                delay(15000L) // 15s refresh interval
            }
        }
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
