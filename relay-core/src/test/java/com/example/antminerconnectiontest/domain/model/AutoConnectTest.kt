package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-connect (v0.1.9): the known-miner candidate set and the probe
 * classification. "Known" = saved profiles + cloud fleet rows; nothing
 * else is ever probed or added (no subnet scan, no unknown machines).
 */
class AutoConnectTest {

    private fun profile(
        workerName: String,
        host: String,
        id: String = "profile-$workerName-$host"
    ) = MinerProfile(id = id, workerName = workerName, host = host)

    private fun snapshot(
        user: String = "Master",
        id: String,
        nickname: String = "",
        ip: String
    ) = RemoteMinerSnapshot(
        user = user,
        id = id,
        nickname = nickname,
        ip = ip,
        hashrate5s = 100.0,
        avgHashrate = 99.0,
        maxTemp = 63,
        maxFan = 5900,
        status = "Online",
        lastUpdated = ""
    )

    // ---- candidate building ----

    @Test
    fun candidates_profileAndFleetRowForSameMachine_collapseIntoOne() {
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = listOf(snapshot(id = "banishwor.001", ip = "192.168.1.2"))
        )
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("banishwor.001", candidate.identity)
        assertEquals("192.168.1.2", candidate.ip)
        assertTrue(candidate.savedProfile != null)
        assertTrue(candidate.snapshot != null)
        assertEquals("Master", candidate.sheetOwner)
    }

    @Test
    fun candidates_matchingIsCaseInsensitive_andRowIpBecomesAlternate() {
        // DHCP moved the machine: profile says .2, the sheet says .9.
        // One candidate, profile IP primary, sheet IP as the fallback.
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("Banishwor.001", "192.168.1.2")),
            snapshots = listOf(snapshot(id = "banishwor.001", ip = "192.168.1.9"))
        )
        assertEquals(1, candidates.size)
        assertEquals(listOf("192.168.1.2", "192.168.1.9"), candidates.single().probeIps)
    }

    @Test
    fun candidates_fleetOnlyMachine_hasNoProfileAndCarriesOwner() {
        val candidates = AutoConnect.buildCandidates(
            profiles = emptyList(),
            snapshots = listOf(
                snapshot(user = "Tompokchao", id = "tompokchao.001", ip = "192.168.1.20")
            )
        )
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertNull(candidate.savedProfile)
        assertEquals("tompokchao.001", candidate.identity)
        assertEquals("Tompokchao", candidate.sheetOwner)
        assertEquals("192.168.1.20", candidate.ip)
    }

    @Test
    fun candidates_profileWithoutWorkerName_staysCandidateWithBlankIdentity() {
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("", "192.168.1.5")),
            snapshots = emptyList()
        )
        assertEquals(1, candidates.size)
        assertEquals("", candidates.single().identity)
        assertTrue(candidates.single().savedProfile != null)
    }

    @Test
    fun candidates_rowWithoutProvableIdentity_isSkipped() {
        // A UUID-id row whose nickname is a free-form user nickname
        // proves no worker identity — there is nothing to verify a
        // probe against, so it can never become a candidate.
        val candidates = AutoConnect.buildCandidates(
            profiles = emptyList(),
            snapshots = listOf(
                snapshot(
                    id = "123e4567-e89b-12d3-a456-426614174000",
                    nickname = "Nepra #1",
                    ip = "192.168.1.30"
                )
            )
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun candidates_duplicateSheetRowsForOneMachine_collapse() {
        val candidates = AutoConnect.buildCandidates(
            profiles = emptyList(),
            snapshots = listOf(
                snapshot(id = "banishwor.002", ip = "192.168.1.3"),
                snapshot(id = "Banishwor.002", ip = "192.168.1.3")
            )
        )
        assertEquals(1, candidates.size)
        assertEquals("banishwor.002", candidates.single().identity)
    }

    @Test
    fun candidates_sameIpDifferentWorkers_bothRemainCandidates() {
        // v0.5.3 regression: banishwor.001 (saved, Sabaltongba) and
        // bishambhor.001 (fleet row) share 192.168.1.2 across sites.
        // The candidate set used to dedupe by IP and drop bishambhor.001
        // entirely — the consumers' known-identity set then lacked it,
        // so when its machine answered at that address it was classified
        // UNKNOWN_MACHINE and could never be claimed. Both identities
        // must stay candidates; the probe answer decides which one is
        // physically present.
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = listOf(
                snapshot(id = "banishwor.001", ip = "192.168.1.2"),
                snapshot(id = "bishambhor.001", ip = "192.168.1.2")
            )
        )
        assertEquals(2, candidates.size)
        assertEquals(
            setOf("banishwor.001", "bishambhor.001"),
            candidates.map { it.identity }.toSet()
        )
        // Deterministic order: profile-backed candidate first.
        assertEquals("banishwor.001", candidates.first().identity)
        assertTrue(candidates.first().savedProfile != null)
    }

    @Test
    fun sameIp_atBishambhorSite_bishambhorAnswerIsRoutedToBishambhorCandidate() {
        // At Bishambhor's place the phone probes the saved banishwor.001
        // candidate at 192.168.1.2; the machine there proves it is
        // bishambhor.001. With the full candidate set the answer is a
        // DIFFERENT_KNOWN_MACHINE and the consumers' identity lookup
        // finds the bishambhor.001 candidate to adopt — while
        // banishwor.001 itself stays remote-only.
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = listOf(
                snapshot(id = "banishwor.001", ip = "192.168.1.2"),
                snapshot(id = "bishambhor.001", ip = "192.168.1.2")
            )
        )
        val knownIdentities = candidates.map { it.identity }.toSet()
        val probed = candidates.first { it.identity == "banishwor.001" }

        assertEquals(
            ProbeClass.DIFFERENT_KNOWN_MACHINE,
            AutoConnect.classify(probed, resolvedIdentity = "bishambhor.001", knownIdentities)
        )
        val routed = candidates.firstOrNull {
            it.identity == MinerMerge.normalizeWorkerName("bishambhor.001")
        }
        assertTrue(routed != null)
        assertEquals("bishambhor.001", routed!!.identity)
        // And probing the bishambhor candidate itself is a plain MATCH.
        assertEquals(
            ProbeClass.MATCH,
            AutoConnect.classify(routed, resolvedIdentity = "bishambhor.001", knownIdentities)
        )
    }

    @Test
    fun sameIp_atSabaltongbaSite_banishworAnswerIsRoutedToBanishworCandidate() {
        // Reverse direction: an earlier visit saved a bishambhor.001
        // profile at 192.168.1.2; back at Sabaltongba the machine at
        // 192.168.1.2 proves it is banishwor.001, which must route to
        // the banishwor.001 candidate, not the probed profile.
        val candidates = AutoConnect.buildCandidates(
            profiles = listOf(profile("bishambhor.001", "192.168.1.2")),
            snapshots = listOf(
                snapshot(id = "bishambhor.001", ip = "192.168.1.2"),
                snapshot(id = "banishwor.001", ip = "192.168.1.2")
            )
        )
        assertEquals(2, candidates.size)
        val knownIdentities = candidates.map { it.identity }.toSet()
        val probed = candidates.first { it.identity == "bishambhor.001" }

        assertEquals(
            ProbeClass.DIFFERENT_KNOWN_MACHINE,
            AutoConnect.classify(probed, resolvedIdentity = "banishwor.001", knownIdentities)
        )
        val routed = candidates.firstOrNull {
            it.identity == MinerMerge.normalizeWorkerName("banishwor.001")
        }
        assertTrue(routed != null)
        assertEquals(
            ProbeClass.MATCH,
            AutoConnect.classify(routed!!, resolvedIdentity = "Banishwor.001", knownIdentities)
        )
    }

    // ---- classification of a successful probe ----

    private fun candidate(
        identity: String,
        withProfile: Boolean
    ) = AutoConnectCandidate(
        identity = identity,
        identityRaw = identity,
        ip = "192.168.1.2",
        savedProfile = if (withProfile) profile(identity, "192.168.1.2") else null,
        snapshot = if (withProfile) null else snapshot(id = identity, ip = "192.168.1.2")
    )

    @Test
    fun classify_resolvedIdentityMatches_isMatch() {
        assertEquals(
            ProbeClass.MATCH,
            AutoConnect.classify(
                candidate("banishwor.001", withProfile = false),
                resolvedIdentity = "Banishwor.001",
                knownIdentities = setOf("banishwor.001")
            )
        )
    }

    @Test
    fun classify_savedProfileWithoutIdentity_adoptsResolved() {
        assertEquals(
            ProbeClass.MATCH,
            AutoConnect.classify(
                candidate("", withProfile = true),
                resolvedIdentity = "banishwor.007",
                knownIdentities = emptySet()
            )
        )
    }

    @Test
    fun classify_differentKnownFleetMachine_isClaimedNotRejected() {
        assertEquals(
            ProbeClass.DIFFERENT_KNOWN_MACHINE,
            AutoConnect.classify(
                candidate("banishwor.001", withProfile = false),
                resolvedIdentity = "tompokchao.001",
                knownIdentities = setOf("banishwor.001", "tompokchao.001")
            )
        )
    }

    @Test
    fun classify_unknownMachineAtKnownIp_isNeverAdded() {
        assertEquals(
            ProbeClass.UNKNOWN_MACHINE,
            AutoConnect.classify(
                candidate("banishwor.001", withProfile = false),
                resolvedIdentity = "stranger.001",
                knownIdentities = setOf("banishwor.001")
            )
        )
    }

    @Test
    fun classify_identityUnreadable_fleetCandidateIsUnverified_savedProfileStillMatches() {
        assertEquals(
            ProbeClass.UNVERIFIED,
            AutoConnect.classify(
                candidate("banishwor.001", withProfile = false),
                resolvedIdentity = null,
                knownIdentities = setOf("banishwor.001")
            )
        )
        assertEquals(
            ProbeClass.MATCH,
            AutoConnect.classify(
                candidate("banishwor.001", withProfile = true),
                resolvedIdentity = null,
                knownIdentities = setOf("banishwor.001")
            )
        )
    }

    // ---- classification of a failed probe ----

    @Test
    fun classifyFailure_antminerAnsweringButAuthRejected_needsPassword() {
        assertEquals(ProbeClass.NEEDS_PASSWORD, AutoConnect.classifyFailure(reachable = true, isAntminer = true))
    }

    @Test
    fun classifyFailure_nothingAnswered_unreachable() {
        assertEquals(ProbeClass.UNREACHABLE, AutoConnect.classifyFailure(reachable = false, isAntminer = false))
    }

    @Test
    fun classifyFailure_otherDeviceAnswering_unknownMachine() {
        assertEquals(ProbeClass.UNKNOWN_MACHINE, AutoConnect.classifyFailure(reachable = true, isAntminer = false))
    }
}
