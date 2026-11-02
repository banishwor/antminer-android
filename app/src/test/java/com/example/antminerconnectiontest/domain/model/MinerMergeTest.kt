package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.*
import org.junit.Test

class MinerMergeTest {

    private fun profile(
        id: String,
        workerName: String = "",
        host: String = "192.168.1.10",
        nickname: String = ""
    ) = MinerProfile(
        id = id,
        workerName = workerName,
        displayName = "Antminer S19k Pro",
        nickname = nickname,
        host = host
    )

    private fun snapshot(
        id: String = "some-uuid",
        nickname: String = "Antminer",
        ip: String = "192.168.1.10",
        user: String = "banishwor",
        hashrate5s: Double = 110.0
    ) = RemoteMinerSnapshot(
        user = user,
        id = id,
        nickname = nickname,
        ip = ip,
        hashrate5s = hashrate5s,
        avgHashrate = hashrate5s,
        maxTemp = 62,
        maxFan = 6000,
        status = "Online",
        lastUpdated = ""
    )

    @Test
    fun matchesByWorkerNameCarriedInSnapshotId() {
        // Uploads from this app put the machine's worker name in the id field.
        val local = profile(id = "p1", workerName = "banishwor.001")
        val remote = snapshot(id = "banishwor.001", nickname = "Site Miner")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
        assertEquals("p1", entries[0].localProfile?.id)
        assertEquals("banishwor.001", entries[0].remoteSnapshot?.id)
    }

    @Test
    fun matchesByWorkerNameEmbeddedInLegacyNickname() {
        // Older uploads: nickname is the uploader's effectiveName,
        // "workerName (Model)" when no user nickname was set.
        val local = profile(id = "p1", workerName = "banishwor.002")
        val remote = snapshot(id = "a7f3-uuid", nickname = "banishwor.002 (Antminer S19k Pro)")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        val local = profile(id = "p1", workerName = "  Banishwor.001 ")
        val remote = snapshot(id = "BANISHWOR.001")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
    }

    @Test
    fun blankWorkerNamesNeverMatch() {
        val local = profile(id = "p1", workerName = "")
        val remote = snapshot(id = "", nickname = "")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(2, entries.size)
        assertFalse(entries.any { it.isMerged })
    }

    @Test
    fun ipFallbackMatchesLegacyRowWithUserNickname() {
        // Legacy row: uploader had a free-form nickname, so no worker name
        // is recoverable — the unambiguous IP pairing is the fallback.
        val local = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.42")
        val remote = snapshot(id = "a7f3-uuid", nickname = "Nepra #1", ip = "192.168.1.42")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
    }

    @Test
    fun ipFallbackSkippedWhenAmbiguous() {
        // Two local profiles share a host (e.g. same IP reused across
        // sites): the fallback must not guess.
        val a = profile(id = "pa", workerName = "banishwor.001", host = "192.168.1.42")
        val b = profile(id = "pb", workerName = "banishwor.002", host = "192.168.1.42")
        val remote = snapshot(id = "a7f3-uuid", nickname = "Nepra #1", ip = "192.168.1.42")

        val entries = MinerMerge.merge(listOf(a, b), listOf(remote))

        assertEquals(3, entries.size)
        assertFalse(entries.any { it.isMerged })
    }

    @Test
    fun workerMatchWinsOverIpFallbackOfAnotherProfile() {
        // The snapshot belongs to p2 by worker name even though its stored
        // IP currently equals p1's host (DHCP shuffle): p1 must not steal it.
        val p1 = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.50")
        val p2 = profile(id = "p2", workerName = "banishwor.002", host = "192.168.1.60")
        val remote = snapshot(id = "banishwor.002", nickname = "x", ip = "192.168.1.50")

        val entries = MinerMerge.merge(listOf(p1, p2), listOf(remote))

        assertEquals(2, entries.size)
        val merged = entries.first { it.isMerged }
        assertEquals("p2", merged.localProfile?.id)
    }

    @Test
    fun duplicateSheetRowsForSameMachineCollapseToOneEntry() {
        val local = profile(id = "p1", workerName = "banishwor.001")
        val row1 = snapshot(id = "banishwor.001", nickname = "A")
        val row2 = snapshot(id = "banishwor.001", nickname = "A")

        val entries = MinerMerge.merge(listOf(local), listOf(row1, row2))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
    }

    @Test
    fun unmatchedMachinesStaySeparateAndOrderIsStable() {
        val p1 = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.10")
        val p2 = profile(id = "p2", workerName = "banishwor.002", host = "192.168.1.11")
        val r1 = snapshot(id = "other.001", nickname = "Other", ip = "10.0.0.5")
        val r2 = snapshot(id = "other.002", nickname = "Other 2", ip = "10.0.0.6")

        val entries = MinerMerge.merge(listOf(p1, p2), listOf(r1, r2))

        assertEquals(4, entries.size)
        assertEquals(listOf("p1", "p2"), entries.take(2).map { it.localProfile?.id })
        assertEquals(listOf("other.001", "other.002"), entries.drop(2).map { it.remoteSnapshot?.id })

        // Recomputing with the same inputs yields the identical list.
        assertEquals(entries, MinerMerge.merge(listOf(p1, p2), listOf(r1, r2)))
    }

    @Test
    fun liveLocalDataWinsDisplayAndRaceRateIsSecond() {
        val local = profile(id = "p1", workerName = "banishwor.001")
        val remote = snapshot(id = "banishwor.001", hashrate5s = 90.0)

        // No local readings at all: display falls back to the snapshot.
        val away = MinerMerge.merge(listOf(local), listOf(remote))
        assertEquals(90.0, away[0].displayHashrateTh, 0.001)
        assertFalse(away[0].hasLiveLocalData)
        assertNull(away[0].localHashrateTh)

        // Race poller has a live local reading: it wins over the snapshot.
        val onSite = MinerMerge.merge(
            listOf(local), listOf(remote),
            MinerMerge.LocalData(raceHashrates = mapOf("p1" to 115.0))
        )
        assertEquals(115.0, onSite[0].displayHashrateTh, 0.001)
        assertEquals(115.0, onSite[0].localHashrateTh!!, 0.001)

        // Full dashboard data for this machine (active + online): strongest.
        val activeData = MinerMerge.merge(
            listOf(local), listOf(remote),
            MinerMerge.LocalData(
                activeProfileId = "p1",
                activeDataProfileId = "p1",
                activeDataOnline = true,
                activeHashrateTh = 118.0,
                raceHashrates = mapOf("p1" to 115.0)
            )
        )
        assertTrue(activeData[0].hasLiveLocalData)
        assertEquals(118.0, activeData[0].displayHashrateTh, 0.001)

        // Dashboard data belonging to a DIFFERENT profile must not count.
        val otherActive = MinerMerge.merge(
            listOf(local), listOf(remote),
            MinerMerge.LocalData(
                activeProfileId = "pX",
                activeDataProfileId = "pX",
                activeDataOnline = true,
                activeHashrateTh = 118.0
            )
        )
        assertFalse(otherActive[0].hasLiveLocalData)
        assertNull(otherActive[0].localHashrateTh)
    }

    @Test
    fun findLocalMatchUsesWorkerNameThenIp() {
        val p1 = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.10")
        val p2 = profile(id = "p2", workerName = "banishwor.002", host = "192.168.1.11")

        assertEquals(
            "p2",
            MinerMerge.findLocalMatch(snapshot(id = "banishwor.002", ip = "10.9.9.9"), listOf(p1, p2))?.id
        )
        assertEquals(
            "p1",
            MinerMerge.findLocalMatch(snapshot(id = "uuid", nickname = "Nepra", ip = "192.168.1.10"), listOf(p1, p2))?.id
        )
        assertNull(MinerMerge.findLocalMatch(snapshot(id = "nobody.001", ip = "10.9.9.9"), listOf(p1, p2)))
    }
}
