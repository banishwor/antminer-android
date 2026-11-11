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
        hashrate5s: Double = 110.0,
        lastUpdated: String = ""
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
        lastUpdated = lastUpdated
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
        // Different IPs too, so the IP fallback cannot legitimately pair
        // them either: with no worker identity anywhere, no merge.
        val local = profile(id = "p1", workerName = "", host = "192.168.1.10")
        val remote = snapshot(id = "", nickname = "", ip = "192.168.1.99")

        val entries = MinerMerge.merge(listOf(local), listOf(remote))

        assertEquals(2, entries.size)
        assertFalse(entries.any { it.isMerged })
    }

    @Test
    fun ipFallbackMatchesLegacyRowWithUserNickname() {
        // Legacy row: uploader had a free-form nickname, so no worker name
        // is recoverable — the unambiguous IP pairing is the fallback.
        // (The id is a real per-device UUID, so the row provably carries
        // no worker identity.)
        val local = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.42")
        val remote = snapshot(id = "a7f3c1d2-1111-4222-8333-444455556666", nickname = "Nepra #1", ip = "192.168.1.42")

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
        val remote = snapshot(id = "a7f3c1d2-1111-4222-8333-444455556666", nickname = "Nepra #1", ip = "192.168.1.42")

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

    // --- Regression: cross-site private-IP collision (v0.5.3) ----------
    // banishwor.001 (Sabaltongba) and bishambhor.001 (Bishambhor's site)
    // are different machines that both sit at 192.168.1.2 on their own
    // networks. Neither the merge nor the local-match lookup may ever
    // pair one machine's profile with the other's snapshot just because
    // the address matches.

    @Test
    fun sameIpDifferentKnownWorkers_pairOnlyWithOwnSnapshot() {
        val local = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.2")
        val mine = snapshot(id = "banishwor.001", nickname = "banishwor.001", ip = "192.168.1.2", hashrate5s = 100.0)
        val theirs = snapshot(
            id = "bishambhor.001", nickname = "bishambhor.001", ip = "192.168.1.2",
            user = "bishambhor", hashrate5s = 122.0
        )

        val entries = MinerMerge.merge(listOf(local), listOf(mine, theirs))

        assertEquals(2, entries.size)
        val merged = entries.first { it.isMerged }
        assertEquals("p1", merged.localProfile?.id)
        assertEquals("banishwor.001", merged.remoteSnapshot?.id)
        val remoteOnly = entries.first { it.localProfile == null }
        assertEquals("bishambhor.001", remoteOnly.remoteSnapshot?.id)
        // No live overlay leaks onto the remote-only machine.
        assertNull(remoteOnly.localHashrateTh)
        assertFalse(remoteOnly.hasLiveLocalData)
    }

    @Test
    fun knownWorkerRowAtSharedIp_neverIpPairsWithDifferentKnownProfile() {
        // Only bishambhor.001's row is in the sheet right now. The
        // banishwor.001 profile is the ONLY local profile at
        // 192.168.1.2, so the old unambiguous-IP fallback paired them —
        // handing bishambhor's snapshot (and, with a live poll, its
        // telemetry) to the banishwor entry. The row proves its worker
        // identity, so it must stay a separate remote-only entry.
        val local = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.2")
        val theirs = snapshot(
            id = "bishambhor.001", nickname = "bishambhor.001", ip = "192.168.1.2",
            user = "bishambhor", hashrate5s = 122.0
        )

        val entries = MinerMerge.merge(listOf(local), listOf(theirs))

        assertEquals(2, entries.size)
        assertFalse(entries.any { it.isMerged })
        assertEquals("bishambhor.001", entries.first { it.localProfile == null }.remoteSnapshot?.id)
    }

    @Test
    fun findLocalMatch_differentKnownWorkerAtSameIp_returnsNull() {
        val p1 = profile(id = "p1", workerName = "banishwor.001", host = "192.168.1.2")
        val theirs = snapshot(id = "bishambhor.001", nickname = "bishambhor.001", ip = "192.168.1.2")

        assertNull(MinerMerge.findLocalMatch(theirs, listOf(p1)))
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

    // --- Regression: the same machine under two id forms (v0.1.4) -------
    // The user's real sheet holds one machine as TWO rows: a current row
    // whose id is the worker name, and an older row whose id is the
    // uploader's per-device profile UUID (uploaded before the profile
    // knew its worker name), with the worker name only in the nickname
    // ("banishwor.001 (Antminer S19k Pro)"). Dedupe used to key on the
    // raw id, so both rows survived; the profile claimed one and the
    // other appeared as a second, duplicate machine in Home.

    private val uuidA = "a998a851-92d0-4bac-8d7e-c59d24d0ed64"
    private val uuidB = "b1c2d3e4-1111-4222-8333-444455556666"
    private val t1 = "1793400000000" // older relay
    private val t2 = "1793500000000" // newer relay

    @Test
    fun sameMachineUnderWorkerIdAndUuidIdCollapsesToLatestRow() {
        val local = profile(id = "p1", workerName = "banishwor.001")
        val workerRow = snapshot(
            id = "banishwor.001", nickname = "banishwor.001 (Antminer S19k Pro)",
            hashrate5s = 90.0, lastUpdated = t1
        )
        val uuidRow = snapshot(
            id = uuidA, nickname = "banishwor.001 (Antminer S19k Pro)",
            hashrate5s = 121.0, lastUpdated = t2
        )

        val entries = MinerMerge.merge(listOf(local), listOf(workerRow, uuidRow))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
        // The LATEST relay is the one kept — not the first row seen.
        assertEquals(uuidA, entries[0].remoteSnapshot?.id)
        assertEquals(121.0, entries[0].remoteSnapshot?.hashrate5s!!, 0.001)
    }

    @Test
    fun latestRowWinsRegardlessOfSheetOrder() {
        val local = profile(id = "p1", workerName = "banishwor.001")
        val workerRow = snapshot(
            id = "banishwor.001", nickname = "banishwor.001 (Antminer S19k Pro)",
            hashrate5s = 119.0, lastUpdated = t2
        )
        val uuidRow = snapshot(
            id = uuidA, nickname = "banishwor.001 (Antminer S19k Pro)",
            hashrate5s = 90.0, lastUpdated = t1
        )

        // Newest row first this time: the kept snapshot must not change.
        val entries = MinerMerge.merge(listOf(local), listOf(workerRow, uuidRow))

        assertEquals(1, entries.size)
        assertTrue(entries[0].isMerged)
        assertEquals("banishwor.001", entries[0].remoteSnapshot?.id)
        assertEquals(119.0, entries[0].remoteSnapshot?.hashrate5s!!, 0.001)
    }

    @Test
    fun sameMachineRelayedAsTwoDifferentUuidRowsCollapses() {
        // Two phones/installs relayed the same machine before either knew
        // to use the worker name as id: two different UUIDs, same worker
        // name in the nickname.
        val older = snapshot(
            id = uuidA, nickname = "bishambhor.001 (Antminer S19k Pro)",
            user = "bishambhor", hashrate5s = 100.0, lastUpdated = t1
        )
        val newer = snapshot(
            id = uuidB, nickname = "bishambhor.001 (Antminer S19k Pro)",
            user = "bishambhor", hashrate5s = 118.0, lastUpdated = t2
        )

        val entries = MinerMerge.merge(emptyList(), listOf(older, newer))

        assertEquals(1, entries.size)
        assertEquals(uuidB, entries[0].remoteSnapshot?.id)
        assertEquals(118.0, entries[0].remoteSnapshot?.hashrate5s!!, 0.001)
    }

    @Test
    fun differentAccountsWithMixedIdFormsStaySeparate() {
        // No over-merge: banishwor.001, bishambhor.001 and bani009.001
        // are three different machines even in mixed id forms.
        val local = profile(id = "p1", workerName = "banishwor.001")
        val mine = snapshot(
            id = uuidA, nickname = "banishwor.001 (Antminer S19k Pro)", lastUpdated = t2
        )
        val bish = snapshot(
            id = uuidB, nickname = "bishambhor.001 (Antminer S19k Pro)",
            user = "bishambhor", lastUpdated = t2
        )
        val bani = snapshot(id = "bani009.001", nickname = "bani009.001", user = "bani009", lastUpdated = t2)

        val entries = MinerMerge.merge(listOf(local), listOf(mine, bish, bani))

        assertEquals(3, entries.size)
        assertEquals(1, entries.count { it.isMerged })
        assertEquals("p1", entries.first { it.isMerged }.localProfile?.id)
        assertEquals(
            setOf("bishambhor.001", "bani009.001"),
            entries.filter { it.localProfile == null }
                .map { MinerMerge.workerIdentity(it.remoteSnapshot!!) }
                .toSet()
        )
    }

    @Test
    fun uuidIdIsNeverAWorkerCandidate() {
        val snap = snapshot(id = uuidA, nickname = "bishambhor.001 (Antminer S19k Pro)")

        val candidates = MinerMerge.remoteWorkerCandidates(snap)

        assertFalse(candidates.contains(uuidA))
        assertEquals("bishambhor.001", candidates.first())
        assertTrue(MinerMerge.isDeviceId(uuidA))
        assertFalse(MinerMerge.isDeviceId("banishwor.001"))
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
            MinerMerge.findLocalMatch(
                snapshot(id = "123e4567-e89b-12d3-a456-426614174000", nickname = "Nepra", ip = "192.168.1.10"),
                listOf(p1, p2)
            )?.id
        )
        assertNull(MinerMerge.findLocalMatch(snapshot(id = "nobody.001", ip = "10.9.9.9"), listOf(p1, p2)))
    }
}
