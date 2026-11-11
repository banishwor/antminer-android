package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Find moved miners" (v0.5.4): the tap-initiated DHCP-recovery scan's
 * matching rules. A verified responder is matched by worker identity
 * only — the IP is just where it was found.
 */
class MovedMinerScanTest {

    private fun profile(workerName: String, host: String, id: String = "profile-$workerName") =
        MinerProfile(id = id, workerName = workerName, host = host)

    private fun snapshot(id: String, ip: String, user: String = "Master") = RemoteMinerSnapshot(
        user = user,
        id = id,
        nickname = id,
        ip = ip,
        hashrate5s = 100.0,
        avgHashrate = 99.0,
        maxTemp = 63,
        maxFan = 5900,
        status = "Online",
        lastUpdated = ""
    )

    @Test
    fun movedMiner_foundAtNewIp_updatesProfileAddress() {
        // banishwor.001 was saved at .2; DHCP moved it to .7.
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = emptyList()
        )
        val decision = MovedMinerScan.decide(
            targets,
            MovedMinerScan.VerifiedResponder("192.168.1.7", "banishwor.001")
        )
        assertTrue(decision is MovedMinerScan.Decision.UpdateProfile)
        decision as MovedMinerScan.Decision.UpdateProfile
        assertEquals("192.168.1.7", decision.newIp)
        assertEquals("banishwor.001", decision.target.identity)
    }

    @Test
    fun minerFoundAtStoredIp_isAlreadyCorrect_noWrite() {
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = emptyList()
        )
        val decision = MovedMinerScan.decide(
            targets,
            MovedMinerScan.VerifiedResponder("192.168.1.2", "Banishwor.001")
        )
        assertTrue(decision is MovedMinerScan.Decision.AlreadyCorrect)
    }

    @Test
    fun unknownWorker_isIgnored_neverAdopted() {
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = emptyList()
        )
        val decision = MovedMinerScan.decide(
            targets,
            MovedMinerScan.VerifiedResponder("192.168.1.9", "stranger.001")
        )
        assertTrue(decision is MovedMinerScan.Decision.IgnoredUnknown)
    }

    @Test
    fun fleetOnlyWorker_isAdopted() {
        val targets = MovedMinerScan.buildTargets(
            profiles = emptyList(),
            snapshots = listOf(snapshot(id = "bishambhor.001", ip = "192.168.1.2", user = "Bishambhor"))
        )
        assertEquals(1, targets.size)
        val decision = MovedMinerScan.decide(
            targets,
            MovedMinerScan.VerifiedResponder("192.168.1.21", "bishambhor.001")
        )
        assertTrue(decision is MovedMinerScan.Decision.AdoptFleet)
        decision as MovedMinerScan.Decision.AdoptFleet
        assertEquals("192.168.1.21", decision.ip)
    }

    @Test
    fun responderWithUnreadableWorker_isIgnoredUnverified() {
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(profile("banishwor.001", "192.168.1.2")),
            snapshots = emptyList()
        )
        assertTrue(
            MovedMinerScan.decide(
                targets,
                MovedMinerScan.VerifiedResponder("192.168.1.7", null)
            ) is MovedMinerScan.Decision.IgnoredUnverified
        )
        assertTrue(
            MovedMinerScan.decide(
                targets,
                MovedMinerScan.VerifiedResponder("192.168.1.7", "  ")
            ) is MovedMinerScan.Decision.IgnoredUnverified
        )
    }

    @Test
    fun targets_excludeAlreadyLiveIdentities_andCollapseProfilePlusRow() {
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(
                profile("banishwor.001", "192.168.1.2"),
                profile("banishwor.002", "192.168.1.3")
            ),
            snapshots = listOf(
                snapshot(id = "banishwor.001", ip = "192.168.1.2"),
                snapshot(id = "bishambhor.001", ip = "192.168.1.2", user = "Bishambhor")
            ),
            liveIdentities = setOf("banishwor.002")
        )
        // .002 is live → not a target. .001 collapses profile+row into
        // ONE profile target. bishambhor.001 is a fleet-only target.
        assertEquals(listOf("banishwor.001", "bishambhor.001"), targets.map { it.identity })
        val first = targets.first()
        assertTrue(first.profile != null)
        assertTrue(first.snapshot != null)
    }

    @Test
    fun targets_skipProfilesWithoutWorkerIdentity() {
        val targets = MovedMinerScan.buildTargets(
            profiles = listOf(profile("", "192.168.1.5")),
            snapshots = emptyList()
        )
        assertTrue(targets.isEmpty())
    }
}
