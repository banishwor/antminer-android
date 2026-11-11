package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.domain.model.MinerChartData
import com.example.antminerconnectiontest.domain.model.MinerPool
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.MinerStats
import com.example.antminerconnectiontest.domain.model.MinerSummary
import com.example.antminerconnectiontest.domain.model.MinerSystemInfo
import com.example.antminerconnectiontest.domain.model.MinerWarnings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Race-poller identity gate (v0.5.3): a rate may be assigned only to
 * the profile whose worker the answering miner proved. The cross-site
 * case: banishwor.001 and bishambhor.001 profiles both point at
 * 192.168.1.2; whichever machine physically answers there, only its
 * own profile may light up.
 */
class MultiMinerPollerTest {

    /** Answers per host: one physical endpoint per address. */
    private class FakeReadApi(
        private val summaries: Map<String, MinerSummary>,
        private val pools: Map<String, Result<List<MinerPool>>>
    ) : MinerReadApi {
        override suspend fun getSummary(profile: MinerProfile): Result<MinerSummary> =
            summaries[profile.host]?.let { Result.success(it) }
                ?: Result.failure(Exception("no answer at ${profile.host}"))

        override suspend fun getPools(profile: MinerProfile): Result<List<MinerPool>> =
            pools[profile.host] ?: Result.failure(Exception("pools unreadable"))

        override suspend fun getSystemInfo(profile: MinerProfile): Result<MinerSystemInfo> =
            Result.failure(Exception("unused"))
        override suspend fun getStats(profile: MinerProfile): Result<MinerStats> =
            Result.failure(Exception("unused"))
        override suspend fun getWarnings(profile: MinerProfile): Result<MinerWarnings> =
            Result.failure(Exception("unused"))
        override suspend fun getChart(profile: MinerProfile): Result<MinerChartData> =
            Result.failure(Exception("unused"))
        override suspend fun getCurrentLog(profile: MinerProfile): Result<String> =
            Result.failure(Exception("unused"))
        override suspend fun getHistoryLog(profile: MinerProfile): Result<String> =
            Result.failure(Exception("unused"))
        override suspend fun getAvailableLogDates(profile: MinerProfile): Result<List<String>> =
            Result.failure(Exception("unused"))
        override suspend fun createLogBackup(profile: MinerProfile, dateIds: List<String>): Result<String> =
            Result.failure(Exception("unused"))
        override suspend fun downloadLogArchive(
            profile: MinerProfile, archiveName: String, destinationFile: File,
            onProgress: (Long, Long) -> Unit
        ): Result<File> = Result.failure(Exception("unused"))
    }

    private fun summary(rate: Double) =
        MinerSummary(elapsedSeconds = 3600, hashrate5s = rate, hashrateAvg = rate)

    private fun pool(user: String) = MinerPool(
        index = 0, url = "stratum+tcp://pool.example:443", user = user,
        status = "Alive", priority = 0, acceptedShares = 1, rejectedShares = 0
    )

    private val sabaltongba = MinerProfile(
        id = "uuid-banishwor", workerName = "banishwor.001", host = "192.168.1.2"
    )
    private val bishambhor = MinerProfile(
        id = "uuid-bishambhor", workerName = "bishambhor.001", host = "192.168.1.2"
    )

    @Test
    fun sharedIp_bishambhorAnswers_rateGoesOnlyToBishambhor() = runBlocking {
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf("192.168.1.2" to summary(122.0)),
                pools = mapOf("192.168.1.2" to Result.success(listOf(pool("bishambhor.001"))))
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba, bishambhor))
        // banishwor.001 must NOT show bishambhor.001's hashrate as live.
        assertEquals(mapOf("uuid-bishambhor" to 122.0), rates)
    }

    @Test
    fun sharedIp_banishworAnswers_rateGoesOnlyToBanishwor() = runBlocking {
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf("192.168.1.2" to summary(121.0)),
                pools = mapOf("192.168.1.2" to Result.success(listOf(pool("Banishwor.001"))))
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba, bishambhor))
        assertEquals(mapOf("uuid-banishwor" to 121.0), rates)
    }

    @Test
    fun sharedIp_poolsUnreadable_noProfileGetsTheRate() = runBlocking {
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf("192.168.1.2" to summary(122.0)),
                pools = emptyMap() // pools fetch fails for the host
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba, bishambhor))
        assertTrue(rates.isEmpty())
    }

    @Test
    fun singleProfile_differentWorkerAnswers_noRate() = runBlocking {
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf("192.168.1.2" to summary(122.0)),
                pools = mapOf("192.168.1.2" to Result.success(listOf(pool("bishambhor.001"))))
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba))
        assertTrue(rates.isEmpty())
    }

    @Test
    fun singleProfile_matchingWorker_rateAssigned() = runBlocking {
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf("192.168.1.2" to summary(121.0)),
                pools = mapOf("192.168.1.2" to Result.success(listOf(pool("banishwor.001"))))
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba))
        assertEquals(mapOf("uuid-banishwor" to 121.0), rates)
    }

    @Test
    fun differentHosts_bothVerified_bothRatesAssigned() = runBlocking {
        val other = MinerProfile(id = "uuid-other", workerName = "banishwor.002", host = "192.168.1.3")
        val poller = MultiMinerPoller(
            FakeReadApi(
                summaries = mapOf(
                    "192.168.1.2" to summary(121.0),
                    "192.168.1.3" to summary(119.0)
                ),
                pools = mapOf(
                    "192.168.1.2" to Result.success(listOf(pool("banishwor.001"))),
                    "192.168.1.3" to Result.success(listOf(pool("banishwor.002")))
                )
            )
        )
        val rates = poller.pollRates(listOf(sabaltongba, other))
        assertEquals(mapOf("uuid-banishwor" to 121.0, "uuid-other" to 119.0), rates)
    }
}
