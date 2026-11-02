package com.example.antminerconnectiontest.data.repository

import com.example.antminerconnectiontest.data.analysis.DailyLogAnalyzer
import com.example.antminerconnectiontest.data.analysis.TarExtractor
import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.data.storage.DailyAnalysisStorageManager
import com.example.antminerconnectiontest.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MinerRepository(
    private val readApi: MinerReadApi = AntminerReadApiImpl(),
    private val analysisStorage: DailyAnalysisStorageManager? = null
) {
    private val _activeProfile = MutableStateFlow<MinerProfile?>(null)
    val activeProfile: StateFlow<MinerProfile?> = _activeProfile.asStateFlow()

    private val _dashboardData = MutableStateFlow<MinerDashboardData?>(null)
    val dashboardData: StateFlow<MinerDashboardData?> = _dashboardData.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var pollingJob: Job? = null
    var onProfileAutoUpdated: ((MinerProfile) -> Unit)? = null

    // --- Refresh ownership ---------------------------------------------------
    // Refreshes are serialised through [refreshMutex], and every refresh is
    // stamped with the [refreshGeneration] that was current when it started.
    // Switching the active profile bumps the generation and cancels in-flight
    // refresh jobs, so a slow fetch for the previous miner can never publish
    // its data (or an auto-captured profile) over the newly selected miner.
    private val refreshMutex = Mutex()

    @Volatile
    private var refreshGeneration = 0L

    private val inFlightRefreshJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    private fun cancelInFlightRefreshes() {
        inFlightRefreshJobs.forEach { it.cancel() }
    }

    /** True while [generation] still belongs to the currently active profile. */
    private fun isCurrentGeneration(generation: Long, profileId: String): Boolean {
        return refreshGeneration == generation && _activeProfile.value?.id == profileId
    }

    fun setActiveProfile(profile: MinerProfile) {
        val previousId = _activeProfile.value?.id
        refreshGeneration++
        cancelInFlightRefreshes()
        _activeProfile.value = profile
        if (previousId != profile.id) {
            // Different miner: drop the previous miner's data. Same-id updates
            // (rename, IP self-heal, auto-capture) keep the current data on
            // screen instead of blanking the dashboard until the next poll.
            _dashboardData.value = null
        }
    }

    fun disconnect() {
        refreshGeneration++
        cancelInFlightRefreshes()
        stopPolling()
        _activeProfile.value = null
        _dashboardData.value = null
    }

    /**
     * Starts the 15-second background polling cycle while the screen is visible.
     */
    fun startPolling(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            while (isActive) {
                if (_activeProfile.value != null && !_isRefreshing.value) {
                    // Child job per tick: cancelling an in-flight refresh on a
                    // profile switch must not kill the polling loop itself.
                    val tick = launch { refreshDashboardData() }
                    tick.join()
                }
                delay(15000L) // 15-second refresh interval
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Refreshes all monitoring endpoints concurrently.
     * Preserves last good data if an endpoint temporarily fails.
     *
     * Refreshes are serialised and ownership-checked: if the active profile
     * changes while a fetch is in flight, the stale result is dropped instead
     * of being published over the newly selected miner.
     */
    suspend fun refreshDashboardData(): Result<MinerDashboardData> {
        val job = currentCoroutineContext()[Job]
        if (job != null) inFlightRefreshJobs.add(job)
        try {
            return refreshMutex.withLock { doRefreshDashboardData() }
        } finally {
            if (job != null) inFlightRefreshJobs.remove(job)
        }
    }

    private suspend fun doRefreshDashboardData(): Result<MinerDashboardData> = withContext(Dispatchers.IO) {
        val profile = _activeProfile.value
            ?: return@withContext Result.failure(IllegalStateException("No active miner profile"))
        val generation = refreshGeneration

        _isRefreshing.value = true
        // Only reuse previous data that belongs to this same miner.
        val previousData = _dashboardData.value?.takeIf { it.profileId == profile.id }
        val partialErrors = mutableListOf<String>()

        try {
            // Concurrently fetch all six endpoints
            val summaryDeferred = async { readApi.getSummary(profile) }
            val statsDeferred = async { readApi.getStats(profile) }
            val warningsDeferred = async { readApi.getWarnings(profile) }
            val chartDeferred = async { readApi.getChart(profile) }
            val systemInfoDeferred = async {
                // If system info is already cached, we can refresh it or reuse
                if (previousData?.systemInfo != null) {
                    readApi.getSystemInfo(profile).getOrDefault(previousData.systemInfo)
                } else {
                    readApi.getSystemInfo(profile).getOrNull()
                }
            }
            val poolsDeferred = async { readApi.getPools(profile) }

            val summaryRes = summaryDeferred.await()
            val statsRes = statsDeferred.await()
            val warningsRes = warningsDeferred.await()
            val chartRes = chartDeferred.await()
            val sysInfo = systemInfoDeferred.await()
            val poolsRes = poolsDeferred.await()

            val isAnySuccess = summaryRes.isSuccess || statsRes.isSuccess || poolsRes.isSuccess

            val summary = if (isAnySuccess) summaryRes.getOrNull() else null
            val stats = if (isAnySuccess) statsRes.getOrNull() else null
            val warnings = if (isAnySuccess) warningsRes.getOrElse { MinerWarnings(emptyList()) } else MinerWarnings(emptyList())
            val chart = if (isAnySuccess) chartRes.getOrElse { previousData?.chartData ?: MinerChartData() } else MinerChartData()
            val pools = if (isAnySuccess) poolsRes.getOrElse { previousData?.pools ?: emptyList() } else emptyList()

            val updatedData = MinerDashboardData(
                profileId = profile.id,
                systemInfo = if (isAnySuccess) (sysInfo ?: previousData?.systemInfo) else null,
                summary = summary,
                stats = stats,
                pools = pools,
                warnings = warnings,
                chartData = chart,
                lastUpdated = System.currentTimeMillis(),
                isOnline = isAnySuccess,
                partialErrors = partialErrors
            )

            if (!isCurrentGeneration(generation, profile.id)) {
                // The user switched miners while this fetch was in flight.
                // Drop the result: it must never overwrite the new miner's
                // screen or resurrect the old active profile.
                return@withContext Result.failure(
                    IllegalStateException("Refresh superseded by a miner switch")
                )
            }

            _dashboardData.value = updatedData

            if (isAnySuccess) {
                val firstPoolUser = pools.firstOrNull()?.user
                val mac = sysInfo?.mac
                if ((profile.workerName.isBlank() && !firstPoolUser.isNullOrBlank()) || (profile.macAddress.isBlank() && !mac.isNullOrBlank())) {
                    val updatedProfile = profile.copy(
                        workerName = if (profile.workerName.isNotBlank()) profile.workerName else (firstPoolUser ?: ""),
                        macAddress = if (profile.macAddress.isNotBlank()) profile.macAddress else (mac ?: "")
                    )
                    _activeProfile.value = updatedProfile
                    onProfileAutoUpdated?.invoke(updatedProfile)
                }
                Result.success(updatedData)
            } else {
                Result.failure(Exception("Could not connect to miner at ${profile.host}"))
            }
        } catch (e: CancellationException) {
            // A profile switch cancels in-flight refreshes on purpose; that is
            // not a connection failure and must not touch the published state.
            throw e
        } catch (e: Exception) {
            if (isCurrentGeneration(generation, profile.id)) {
                _dashboardData.update { current ->
                    if (current?.profileId == profile.id) {
                        current.copy(
                            isOnline = false,
                            partialErrors = listOf(e.message ?: "Unknown connection error")
                        )
                    } else {
                        current
                    }
                }
            }
            Result.failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun fetchCurrentLog(): Result<String> = withContext(Dispatchers.IO) {
        val profile = _activeProfile.value
            ?: return@withContext Result.failure(IllegalStateException("No active miner profile"))
        readApi.getCurrentLog(profile)
    }

    suspend fun fetchHistoryLog(): Result<String> = withContext(Dispatchers.IO) {
        val profile = _activeProfile.value
            ?: return@withContext Result.failure(IllegalStateException("No active miner profile"))
        readApi.getHistoryLog(profile)
    }

    suspend fun getAvailableLogDates(): Result<List<String>> = withContext(Dispatchers.IO) {
        val profile = _activeProfile.value
            ?: return@withContext Result.failure(IllegalStateException("No active miner profile"))
        readApi.getAvailableLogDates(profile)
    }

    fun getStoredDailyAnalyses(minerId: String): List<DailyMinerAnalysis> {
        return analysisStorage?.getAllAnalyses(minerId) ?: emptyList()
    }

    fun getDailyAnalysis(minerId: String, date: String): DailyMinerAnalysis? {
        return analysisStorage?.getAnalysis(minerId, date)
    }

    /**
     * Executes the internal temporary acquisition, extraction, analysis, and persistence pipeline.
     */
    suspend fun analyzeLogDates(
        selectedRawDateIds: List<String>,
        cacheDir: File,
        onProgress: (phase: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<List<DailyMinerAnalysis>> = withContext(Dispatchers.IO) {
        val profile = _activeProfile.value
            ?: return@withContext Result.failure(IllegalStateException("No active miner profile"))

        if (selectedRawDateIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No dates selected for analysis"))
        }

        val tempSessionDir = File(cacheDir, "analysis_temp_${System.currentTimeMillis()}")
        tempSessionDir.mkdirs()

        var tarFile: File? = null
        val extractedDir = File(tempSessionDir, "extracted")

        try {
            // Phase 1: Request archive creation on miner
            onProgress("Requesting log archive from miner...", 0.15f)
            val backupResult = readApi.createLogBackup(profile, selectedRawDateIds)
            val archiveName = backupResult.getOrElse { err ->
                return@withContext Result.failure(err)
            }

            // Phase 2: Stream download the archive to temporary storage
            onProgress("Downloading log archive...", 0.35f)
            tarFile = File(tempSessionDir, archiveName)
            val downloadResult = readApi.downloadLogArchive(
                profile = profile,
                archiveName = archiveName,
                destinationFile = tarFile,
                onProgress = { bytesRead, totalBytes ->
                    val frac = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                    val mappedProgress = 0.35f + (frac * 0.35f) // 0.35 to 0.70
                    val mbRead = String.format(java.util.Locale.US, "%.1f", bytesRead / (1024.0 * 1024.0))
                    val mbTotal = if (totalBytes > 0) String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0)) + " MB" else ""
                    onProgress("Downloading archive ($mbRead MB / $mbTotal)...", mappedProgress)
                }
            )

            downloadResult.getOrElse { err ->
                return@withContext Result.failure(err)
            }

            // Phase 3: Extract archive in temporary folder
            onProgress("Extracting log files...", 0.75f)
            extractedDir.mkdirs()
            TarExtractor.extractTar(tarFile, extractedDir)

            // Phase 4: Analyze files per calendar date
            onProgress("Analyzing log data and metrics...", 0.88f)
            val analyses = DailyLogAnalyzer.analyzeExtractedData(
                extractedRoot = extractedDir,
                minerId = profile.id,
                targetRawDateIds = selectedRawDateIds
            )

            // Phase 5: Persist compact daily records
            onProgress("Saving analysis results...", 0.96f)
            analysisStorage?.saveAnalyses(profile.id, analyses)

            onProgress("Analysis complete!", 1.0f)
            Result.success(analyses)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Phase 6: ALWAYS cleanup temporary extracted files and downloaded TAR
            try {
                tarFile?.delete()
                extractedDir.deleteRecursively()
                tempSessionDir.deleteRecursively()
            } catch (cleanupEx: Exception) {
                cleanupEx.printStackTrace()
            }
        }
    }
}
