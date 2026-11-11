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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dashboard identity gate (v0.5.3): when the active profile knows its
 * worker, a refresh may publish live data only if the answering
 * miner's own pools prove that worker. The cross-site case: the
 * Sabaltongba profile banishwor.001 points at 192.168.1.2, but at
 * Bishambhor's site the machine answering at 192.168.1.2 is
 * bishambhor.001 — its telemetry must never appear as banishwor.001's
 * live data; the profile falls back to its remote snapshot instead.
 */
class MinerRepositoryIdentityTest {

    private class FakeReadApi(
        private val poolsResult: Result<List<MinerPool>>
    ) : MinerReadApi {
        override suspend fun getSummary(profile: MinerProfile): Result<MinerSummary> =
            Result.success(MinerSummary(elapsedSeconds = 3600, hashrate5s = 122.0, hashrateAvg = 121.0))

        override suspend fun getPools(profile: MinerProfile): Result<List<MinerPool>> = poolsResult

        override suspend fun getStats(profile: MinerProfile): Result<MinerStats> =
            Result.success(
                MinerStats(
                    chains = emptyList(), fans = emptyList(), totalHwErrors = 0,
                    maxChipTemp = 61, maxPcbTemp = 50, maxFanRpm = 5400
                )
            )

        override suspend fun getSystemInfo(profile: MinerProfile): Result<MinerSystemInfo> =
            Result.failure(Exception("unused"))
        override suspend fun getWarnings(profile: MinerProfile): Result<MinerWarnings> =
            Result.success(MinerWarnings(emptyList()))
        override suspend fun getChart(profile: MinerProfile): Result<MinerChartData> =
            Result.success(MinerChartData())
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

    private fun pool(user: String) = MinerPool(
        index = 0, url = "stratum+tcp://pool.example:443", user = user,
        status = "Alive", priority = 0, acceptedShares = 1, rejectedShares = 0
    )

    private val sabaltongba = MinerProfile(
        id = "uuid-banishwor", workerName = "banishwor.001", host = "192.168.1.2"
    )

    @Test
    fun mismatch_bishambhorAnswers_banishworProfilePublishesOfflineNotLive() = runBlocking {
        val repo = MinerRepository(
            FakeReadApi(Result.success(listOf(pool("bishambhor.001"))))
        )
        repo.setActiveProfile(sabaltongba)

        val result = repo.refreshDashboardData()

        assertTrue(result.isFailure)
        val data = repo.dashboardData.value
        assertTrue(data != null)
        assertEquals("uuid-banishwor", data!!.profileId)
        assertFalse(data.isOnline)
        // The other machine's telemetry is nowhere in this profile's data.
        assertNull(data.summary)
        assertNull(data.stats)
        assertTrue(data.pools.isEmpty())
        assertTrue(data.partialErrors.any { it.contains("bishambhor.001") })
    }

    @Test
    fun verified_matchingWorker_publishesLive() = runBlocking {
        val repo = MinerRepository(
            FakeReadApi(Result.success(listOf(pool(" Banishwor.001 "))))
        )
        repo.setActiveProfile(sabaltongba)

        val result = repo.refreshDashboardData()

        assertTrue(result.isSuccess)
        val data = repo.dashboardData.value
        assertTrue(data != null && data.isOnline)
        assertEquals(122.0, data!!.summary?.hashrate5s!!, 0.001)
    }

    @Test
    fun unverified_poolsUnreadable_knownWorkerPublishesOffline() = runBlocking {
        val repo = MinerRepository(
            FakeReadApi(Result.failure(Exception("pools timeout")))
        )
        repo.setActiveProfile(sabaltongba)

        val result = repo.refreshDashboardData()

        assertTrue(result.isFailure)
        val data = repo.dashboardData.value
        assertTrue(data != null)
        assertFalse(data!!.isOnline)
        assertNull(data.summary)
    }

    @Test
    fun blankWorker_learnsFromPoolsAndPublishesLive() = runBlocking {
        val repo = MinerRepository(
            FakeReadApi(Result.success(listOf(pool("bishambhor.001"))))
        )
        val anonymous = MinerProfile(id = "uuid-anon", workerName = "", host = "192.168.1.2")
        repo.setActiveProfile(anonymous)

        val result = repo.refreshDashboardData()

        assertTrue(result.isSuccess)
        assertTrue(repo.dashboardData.value?.isOnline == true)
        // The pre-existing learn behaviour is preserved.
        assertEquals("bishambhor.001", repo.activeProfile.value?.workerName)
    }
}
