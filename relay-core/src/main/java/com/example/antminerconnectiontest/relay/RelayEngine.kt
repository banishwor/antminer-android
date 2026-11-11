package com.example.antminerconnectiontest.relay

import com.example.antminerconnectiontest.data.api.AntminerReadApiImpl
import com.example.antminerconnectiontest.data.api.MinerReadApi
import com.example.antminerconnectiontest.data.cloud.CloudSyncManager
import com.example.antminerconnectiontest.data.scanner.AntminerDetector
import com.example.antminerconnectiontest.data.scanner.DiscoveryStatus
import com.example.antminerconnectiontest.data.scanner.MinerVerificationResult
import com.example.antminerconnectiontest.domain.model.AutoConnect
import com.example.antminerconnectiontest.domain.model.AutoConnectCandidate
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.LiveIdentity
import com.example.antminerconnectiontest.domain.model.MinerMerge
import com.example.antminerconnectiontest.domain.model.MinerPool
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.domain.model.ProbeClass
import com.example.antminerconnectiontest.domain.model.RelayUpload
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.withTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 24/7 relay, as a platform-thin engine (Step 2 extraction).
 *
 * One cycle: load the cloud config; when the Relay switch is on and a
 * user name is set, fetch the fleet, background-auto-connect any known
 * machines that have no saved profile yet, poll every saved profile,
 * resolve each machine's sheet identity (its pool worker username),
 * apply the cross-user upload guard, and upsert the survivors to the
 * sheet. Status text is reported through [listener]; persistence goes
 * through [storage]; Wi-Fi state is the host's answer to
 * [isWifiConnected] (uploads pause off Wi-Fi — local miners are only
 * reachable on their own LAN anyway).
 *
 * This is the exact logic that used to live inside the main app's
 * MinerRelayService; the service is now a thin Android shell (foreground
 * notification + lifecycle) around this engine, and Relay Lite will
 * drive the same engine from its own minimal shell.
 */
class RelayEngine(
    private val storage: RelayStorage,
    private val cloudSync: CloudSyncManager = CloudSyncManager(),
    private val readApi: MinerReadApi = AntminerReadApiImpl(),
    private val detector: AntminerDetector = AntminerDetector(),
    private val isWifiConnected: () -> Boolean,
    private val listener: RelayStatusListener
) {

    /** Legacy-duplicate healing runs once per engine start (best-effort). */
    private var legacyHealDone = false

    /**
     * Runs the relay loop on [scope] until the returned job is
     * cancelled: one cycle, then a [intervalMs] pause, forever. A
     * cycle-level failure (storage, config) stays silent and the next
     * cycle retries.
     */
    fun start(scope: CoroutineScope, intervalMs: Long = DEFAULT_INTERVAL_MS): Job {
        return scope.launch {
            while (isActive) {
                try {
                    runCycle()
                } catch (e: Exception) {
                    // Cycle-level failure (storage, config): stay silent
                    // and let the next cycle retry (v0.3.1: this catch
                    // used to dump a full stack trace to logcat every
                    // 30 s for as long as the failure persisted).
                }

                delay(intervalMs) // Poll every 30 seconds in background
            }
        }
    }

    /** One full relay cycle. Internal failures are contained per miner. */
    suspend fun runCycle() {
        val config = storage.loadCloudConfig()
        if (config.isSyncEnabled && config.userName.isNotBlank() && config.webhookUrl.isNotBlank()) {
            if (!isWifiConnected()) {
                listener.onRelayStatus(
                    "⚡ Relay Standby • Mobile Data",
                    "Paused upload • Wi-Fi connection required for local miners"
                )
            } else {
                // Fetch the fleet first (v0.1.9): it feeds
                // the background auto-connect candidate set
                // and the cross-user upload guard below.
                val fleet = cloudSync.fetchRemoteMiners(config.webhookUrl, config.userName)
                    .getOrNull()?.miners ?: emptyList()

                // Background auto-connect: a phone that has
                // never saved a local profile still connects
                // to the fleet's known miners on this Wi-Fi
                // with the app closed. Newly verified
                // machines are saved, then polled + uploaded
                // by the normal loop below in the same cycle.
                backgroundAutoConnect(fleet)

                val profiles = storage.loadSavedProfiles()
                val snapshots = mutableListOf<RemoteMinerSnapshot>()
                var deferredCount = 0
                // Honest failure counts (v0.1.2): a failed poll is no
                // longer silently dropped — it is classified (wrong
                // password vs unreachable vs answered-but-not-hashing),
                // counted for the cycle status below, and reported
                // per miner through storage.reportMinerOutcome.
                var authFailedCount = 0
                var unreachableCount = 0
                var notHashingCount = 0

                for (profile in profiles) {
                    var outcome: MinerPollOutcome? = null
                    try {
                        val summaryRes = readApi.getSummary(profile)
                        val summary = summaryRes.getOrNull()

                        if (summaryRes.isSuccess && summary != null) {
                            val statsRes = readApi.getStats(profile)
                            val stats = statsRes.getOrNull()
                            // Pools + system info feed the
                            // extended telemetry (v0.1.6) and
                            // the identity resolution below.
                            // Best-effort: a failed fetch just
                            // leaves those fields unknown.
                            val pools = readApi.getPools(profile).getOrNull().orEmpty()
                            val systemInfo = readApi.getSystemInfo(profile).getOrNull()

                            val rate5s = summary.hashrate5s
                            val avgRate = if (summary.hashrateAvg > 0.0) summary.hashrateAvg else rate5s
                            // Unknown when the stats fetch failed:
                            // upload 0 (unknown), not an invented
                            // 60 °C / 6000 RPM reading.
                            val maxTemp = stats?.maxChipTemp ?: 0
                            val maxFan = stats?.maxFanRpm ?: 0

                            // Identity gate (v0.5.3): the profile's
                            // address is only a hint — at another site a
                            // DIFFERENT miner can answer there (private
                            // IPs repeat across sites). When the profile
                            // knows its worker, the pools this endpoint
                            // just returned must prove the answering
                            // machine IS that worker before any telemetry
                            // is uploaded or reported under the profile's
                            // identity; an unproven poll is deferred and
                            // the profile's own machine counts as not
                            // answering. A profile that has not learned
                            // its worker yet resolves it from these same
                            // pools (see resolveSheetId).
                            val identityProven =
                                LiveIdentity.decide(profile.workerName, pools).isProven

                            if (!identityProven) {
                                deferredCount++
                                outcome = MinerPollOutcome(MinerPollState.UNREACHABLE)
                            } else if (rate5s > 0.0 || avgRate > 0.0) {
                                outcome = MinerPollOutcome(MinerPollState.CONNECTED, rate5s)
                                // Machine identity = the worker
                                // username, resolved before the
                                // snapshot exists (see
                                // resolveSheetId). A machine whose
                                // identity is not known yet is
                                // DEFERRED — it is never uploaded
                                // under this device's profile UUID,
                                // which used to fork one machine
                                // into two sheet rows.
                                val sheetId = resolveSheetId(profile, pools)
                                if (sheetId == null) {
                                    deferredCount++
                                }
                                if (sheetId != null) {
                                    snapshots.add(
                                        RemoteMinerSnapshot(
                                            user = config.userName.trim(),
                                            id = sheetId,
                                            // Phone-only nicknames never
                                            // upload (v0.1.7): the nickname
                                            // field carries the identity.
                                            nickname = sheetId,
                                            ip = profile.host,
                                            hashrate5s = rate5s,
                                            avgHashrate = avgRate,
                                            maxTemp = maxTemp,
                                            maxFan = maxFan,
                                            status = "Online",
                                            lastUpdated = ""
                                        ).withTelemetry(
                                            summary = summary,
                                            stats = stats,
                                            pools = pools,
                                            systemInfo = systemInfo,
                                            fallbackModel = profile.cachedModel ?: ""
                                        )
                                    )
                                }
                            } else {
                                // Answered and read fine, but mining
                                // nothing (v0.1.2): its own state —
                                // not "unreachable".
                                notHashingCount++
                                outcome = MinerPollOutcome(MinerPollState.NOT_HASHING)
                            }
                        } else {
                            // The summary read failed: classify it
                            // (401 = wrong password; anything else =
                            // no usable answer) instead of silently
                            // skipping the miner (v0.1.2).
                            val state = MinerPollClassifier
                                .stateForException(summaryRes.exceptionOrNull())
                            when (state) {
                                MinerPollState.AUTH_FAILED -> authFailedCount++
                                else -> unreachableCount++
                            }
                            outcome = MinerPollOutcome(state)
                        }
                    } catch (e: Exception) {
                        // One miner's unexpected failure must
                        // not stop the others (v0.3.1: no stack
                        // dump per miner per cycle — failures
                        // surface via the cycle status report).
                        unreachableCount++
                        outcome = MinerPollOutcome(MinerPollState.UNREACHABLE)
                    }
                    outcome?.let {
                        try {
                            storage.reportMinerOutcome(profile, it)
                        } catch (_: Exception) {
                            // Reporting is best-effort: it must never
                            // break the cycle it describes.
                        }
                    }
                }

                // Cross-user guard (v0.1.9): machines whose
                // sheet row belongs to another relay user —
                // auto-connected while visiting their site —
                // are polled and shown locally but never
                // re-uploaded under this user's name.
                val uploadable = RelayUpload.filterUploadable(snapshots, config.userName, fleet)
                val guardedCount = snapshots.size - uploadable.size

                if (uploadable.isNotEmpty()) {
                    val uploadRes = cloudSync.uploadLocalMiners(config.webhookUrl, config.userName, uploadable)
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val now = sdf.format(Date())
                    val totalTh = uploadable.sumOf { it.hashrate5s }

                    if (uploadRes.isSuccess) {
                        // The relay's own status report
                        // (v0.3.0): the relay-only screen
                        // reads this persisted state.
                        try {
                            storage.saveRelayStatus(System.currentTimeMillis(), null)
                        } catch (_: Exception) {}
                        val deferredNote = if (deferredCount > 0) {
                            " • $deferredCount deferred (worker name unknown)"
                        } else {
                            ""
                        }
                        val guardNote = if (guardedCount > 0) {
                            " • $guardedCount local-only (another user's machines)"
                        } else {
                            ""
                        }
                        listener.onRelayStatus(
                            "⚡ Relay: ${uploadable.size} Miners Active • ${String.format(Locale.US, "%.1f", totalTh)} TH/s",
                            "Synced at $now to Google Sheet (${config.userName})$deferredNote$guardNote"
                        )
                        if (!legacyHealDone) {
                            legacyHealDone = true
                            healLegacyDuplicates(config, uploadable.map { it.id }, fleet)
                        }
                    } else {
                        try {
                            storage.saveRelayStatus(
                                null,
                                uploadRes.exceptionOrNull()?.message ?: "Upload failed — check internet connection"
                            )
                        } catch (_: Exception) {}
                        listener.onRelayStatus(
                            "⚠️ Relay Sync Failed",
                            "${uploadRes.exceptionOrNull()?.message ?: "Check internet connection"}"
                        )
                    }
                } else if (guardedCount > 0) {
                    listener.onRelayStatus(
                        "⚡ Relay Standby • Local Only",
                        "$guardedCount machine(s) on this Wi-Fi belong to another relay user • Shown locally, not uploaded" +
                            MinerPollClassifier.extraNote(authFailedCount, unreachableCount, notHashingCount)
                    )
                } else if (deferredCount > 0) {
                    listener.onRelayStatus(
                        "⚡ Relay Standby • Upload Deferred",
                        "$deferredCount miner(s) reachable but their worker name is unknown • Nothing uploaded under a device id" +
                            MinerPollClassifier.extraNote(authFailedCount, unreachableCount, notHashingCount)
                    )
                } else {
                    // v0.1.2: composed from the real failure counts —
                    // a wrong password is never again reported as
                    // "unreachable" (see MinerPollClassifier).
                    listener.onRelayStatus(
                        "⚡ Relay Standby • 0 Miners Online",
                        MinerPollClassifier.standbyContent(authFailedCount, unreachableCount, notHashingCount)
                    )
                }
            }
        }
    }

    /**
     * The machine's sheet identity: its pool worker username. Taken from
     * the profile when known; otherwise read from the miner's own pool
     * config (the same source the dashboard backfills worker names from)
     * and persisted back onto the stored profile, so the identity is
     * learned once and every device/install converges on the same sheet
     * row. Null when the identity cannot be established — the caller
     * defers the upload rather than substituting the profile UUID.
     */
    private suspend fun resolveSheetId(
        profile: MinerProfile,
        prefetchedPools: List<MinerPool>? = null
    ): String? {
        RelayUpload.sheetIdFor(profile)?.let { return it }
        val learned = try {
            (prefetchedPools ?: readApi.getPools(profile).getOrNull().orEmpty())
                .firstOrNull { it.user.isNotBlank() }?.user
        } catch (e: Exception) {
            null
        }
        val sheetId = RelayUpload.sheetIdForWorkerName(learned) ?: return null
        try {
            val stored = storage.loadSavedProfiles()
            if (stored.any { it.id == profile.id && it.workerName.isBlank() }) {
                storage.saveProfiles(
                    stored.map { if (it.id == profile.id) it.copy(workerName = sheetId) else it }
                )
            }
        } catch (e: Exception) {
            // Persisting the learned identity is best-effort; the
            // in-memory sheetId is still returned for this cycle.
        }
        return sheetId
    }

    /**
     * Background auto-connect (v0.1.9): the same known-miner pass the
     * foreground ViewModel runs, so a phone that has never saved a local
     * profile still connects when it joins a site Wi-Fi with the app
     * closed. Only fleet machines WITHOUT a saved profile are probed
     * here — saved profiles are already polled by the relay loop itself
     * (with identity resolution via [resolveSheetId]), so probing them
     * again would only double the traffic. Verified machines are
     * appended to the stored profiles (credentials encrypted at rest by
     * the [RelayStorage] implementation, never uploaded), and the loop
     * that follows polls and uploads them in the same cycle, subject to
     * the cross-user guard. Machines that reject the default credentials
     * are skipped here — the foreground pass surfaces them as
     * "needs password" cards when the app is opened.
     */
    private suspend fun backgroundAutoConnect(fleet: List<RemoteMinerSnapshot>) {
        if (fleet.isEmpty()) return
        try {
            val stored = storage.loadSavedProfiles().toMutableList()
            val candidates = AutoConnect.buildCandidates(stored, fleet)
            val knownIdentities = candidates.mapNotNull {
                it.identity.takeIf { id -> id.isNotBlank() }
            }.toSet()
            var changed = false

            for (candidate in candidates) {
                if (candidate.savedProfile != null) continue
                for (ip in candidate.probeIps) {
                    val result = detector.verifyMinerWithCredentials(
                        ip, AutoConnect.DEFAULT_USERNAME, AutoConnect.DEFAULT_PASSWORD
                    )
                    if (!result.isConfirmed) {
                        val signature = detector.detectSignature(ip)
                        if (signature == DiscoveryStatus.OFFLINE) continue // try alternate IP
                        break // Something answered (auth or not a miner) — next candidate.
                    }
                    when (AutoConnect.classify(candidate, result.workerName, knownIdentities)) {
                        ProbeClass.MATCH -> {
                            stored += profileFor(candidate, ip, result)
                            changed = true
                        }
                        ProbeClass.DIFFERENT_KNOWN_MACHINE -> {
                            // A different known fleet machine lives at
                            // this IP now: claim it here instead, unless
                            // it was saved in the meantime.
                            val resolvedNorm = MinerMerge.normalizeWorkerName(result.workerName)
                            val other = candidates.firstOrNull { it.identity == resolvedNorm }
                            if (other != null && stored.none { MinerMerge.normalizeWorkerName(it.workerName) == resolvedNorm }) {
                                stored += profileFor(other, ip, result)
                                changed = true
                            }
                        }
                        else -> Unit // Unknown / unverified / needs password: never added from the background.
                    }
                    break
                }
            }

            if (changed) {
                storage.saveProfiles(stored)
            }
        } catch (e: Exception) {
            // Best-effort per cycle (v0.3.1: no per-cycle stack dump);
            // the next cycle retries with a fresh candidate set.
        }
    }

    /** Builds the saved profile for a fleet machine an auto-connect probe verified. */
    private fun profileFor(
        candidate: AutoConnectCandidate,
        ip: String,
        result: MinerVerificationResult
    ): MinerProfile = MinerProfile(
        workerName = result.workerName?.trim().orEmpty().ifBlank { candidate.identityRaw },
        macAddress = result.macAddress.orEmpty(),
        displayName = result.modelName
            ?: candidate.snapshot?.model?.ifBlank { null }
            ?: "Antminer",
        host = ip,
        username = AutoConnect.DEFAULT_USERNAME,
        password = result.effectivePassword,
        cachedModel = result.modelName ?: candidate.snapshot?.model?.ifBlank { null }
    )

    /**
     * One-shot per engine start, best-effort: ask the endpoint to drop
     * this user's legacy duplicate rows — the same machines under old
     * per-device UUID ids (selection in [RelayUpload.legacyDuplicateIds])
     * — now that uploads carry the worker identity. Failures (an older
     * endpoint without delete support) are ignored by design. The fleet
     * fetched at the top of the cycle is reused; when it is empty
     * (fetch failed) the heal is skipped this start.
     */
    private suspend fun healLegacyDuplicates(
        config: CloudSyncConfig,
        canonicalIds: List<String>,
        fleet: List<RemoteMinerSnapshot>
    ) {
        try {
            if (fleet.isEmpty()) return
            val duplicateIds = RelayUpload.legacyDuplicateIds(fleet, config.userName, canonicalIds)
            if (duplicateIds.isNotEmpty()) {
                cloudSync.deleteRemoteRows(config.webhookUrl, config.userName, duplicateIds)
            }
        } catch (e: Exception) {
            // Best-effort by design (see doc above); ignored quietly.
        }
    }

    companion object {
        /** The settled relay cadence: one cycle every 30 seconds. */
        const val DEFAULT_INTERVAL_MS = 30_000L
    }
}
