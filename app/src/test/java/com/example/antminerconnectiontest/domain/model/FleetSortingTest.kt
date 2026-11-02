package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FleetSortingTest {

    private val now = 1_798_000_000_000L // fixed "now" for deterministic tests
    private val dayMs = 24L * 60 * 60 * 1000

    private fun profile(id: String, worker: String = "", lastConnected: Long = 0L) = MinerProfile(
        id = id,
        workerName = worker,
        host = "192.168.1.10",
        lastConnected = lastConnected
    )

    private fun snapshot(
        id: String,
        rate: Double,
        updatedEpochMs: Long?,
        user: String = "banishwor"
    ) = RemoteMinerSnapshot(
        user = user,
        id = id,
        nickname = id,
        ip = "192.168.1.10",
        hashrate5s = rate,
        avgHashrate = rate,
        maxTemp = 62,
        maxFan = 4200,
        status = "Online",
        lastUpdated = updatedEpochMs?.toString() ?: ""
    )

    // --- Online determination ---------------------------------------------

    @Test
    fun liveLocalDataIsOnline() {
        val entry = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1"),
            hasLiveLocalData = true
        )
        assertTrue(FleetSorting.isOnline(entry, now))
    }

    @Test
    fun raceReadingIsOnline() {
        val entry = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1"),
            localHashrateTh = 37.1
        )
        assertTrue(FleetSorting.isOnline(entry, now))
    }

    @Test
    fun freshRemoteSnapshotIsOnline() {
        val entry = MergedMinerEntry(
            key = "remote:a",
            remoteSnapshot = snapshot("bani009.001", 35.4, now - 60_000L)
        )
        assertTrue(FleetSorting.isOnline(entry, now))
    }

    @Test
    fun staleRemoteSnapshotIsOffline() {
        val entry = MergedMinerEntry(
            key = "remote:a",
            remoteSnapshot = snapshot("bani009.201", 33.0, now - 6 * dayMs)
        )
        assertFalse(FleetSorting.isOnline(entry, now))
    }

    @Test
    fun localOnlyWithoutReadingIsOffline() {
        val entry = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1", lastConnected = now - dayMs)
        )
        assertFalse(FleetSorting.isOnline(entry, now))
    }

    // --- Sorting ------------------------------------------------------------

    @Test
    fun onlineSortedByHashrateDescendingOfflineBelow() {
        val merged = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1", "banishwor.001"),
            remoteSnapshot = snapshot("banishwor.001", 30.0, now - 60_000L),
            localHashrateTh = 37.1
        )
        val remoteFast = MergedMinerEntry(
            key = "remote:b",
            remoteSnapshot = snapshot("bani009.001", 35.4, now - 60_000L)
        )
        val remoteSlow = MergedMinerEntry(
            key = "remote:c",
            remoteSnapshot = snapshot("bani009.002", 33.9, now - 60_000L)
        )
        val offlineLocal = MergedMinerEntry(
            key = "local:2",
            localProfile = profile("2", "banishwor.002", lastConnected = now - 3_600_000L)
        )
        val offlineRemote = MergedMinerEntry(
            key = "remote:d",
            remoteSnapshot = snapshot("bani009.201", 40.0, now - 6 * dayMs)
        )

        val sorted = FleetSorting.sortEntries(
            listOf(offlineRemote, remoteSlow, merged, offlineLocal, remoteFast), now
        )

        assertEquals(
            listOf("banishwor.001", "bani009.001", "bani009.002", "banishwor.002", "bani009.201"),
            sorted.map { it.workerName }
        )
        // The offline remote machine had the highest snapshot rate of all —
        // it still sits below every online machine, showing 0.
        assertFalse(sorted.last().isOnline)
        assertEquals(0.0, sorted.last().hashrateTh, 0.0001)
    }

    @Test
    fun offlineOrderedByMostRecentUpdateFirstUnknownTimestampLast() {
        val older = MergedMinerEntry(
            key = "remote:old",
            remoteSnapshot = snapshot("old.001", 10.0, now - 6 * dayMs)
        )
        val newer = MergedMinerEntry(
            key = "local:new",
            localProfile = profile("n", "new.001", lastConnected = now - 3_600_000L)
        )
        val unknown = MergedMinerEntry(
            key = "remote:unk",
            remoteSnapshot = snapshot("unk.001", 10.0, null)
        )
        val sorted = FleetSorting.sortEntries(listOf(older, unknown, newer), now)
        assertEquals(listOf("new.001", "old.001", "unk.001"), sorted.map { it.workerName })
    }

    @Test
    fun fleetTotalSumsOnlineOnly() {
        val rows = FleetSorting.sortEntries(
            listOf(
                MergedMinerEntry(key = "local:1", localProfile = profile("1"), localHashrateTh = 37.1),
                MergedMinerEntry(key = "remote:b", remoteSnapshot = snapshot("b.001", 35.4, now - 60_000L)),
                MergedMinerEntry(key = "remote:c", remoteSnapshot = snapshot("c.001", 99.0, now - 6 * dayMs))
            ),
            now
        )
        assertEquals(72.5, FleetSorting.fleetTotalTh(rows), 0.0001)
    }

    // --- Last-updated formatting --------------------------------------------

    @Test
    fun lastUpdatedShowsRelativeAndClockTime() {
        val epoch = now - 6 * dayMs
        val expectedClock = SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(epoch))
        assertEquals(
            "Last updated 6 days ago · $expectedClock",
            FleetSorting.formatLastUpdated(epoch, now)
        )
    }

    @Test
    fun lastUpdatedWithoutTimestampIsHonestDash() {
        assertEquals("Last updated —", FleetSorting.formatLastUpdated(null, now))
    }

    @Test
    fun relativeFormats() {
        assertEquals("just now", FleetSorting.formatRelative(now - 5_000L, now))
        assertEquals("2 min ago", FleetSorting.formatRelative(now - 120_000L, now))
        assertEquals("3 h ago", FleetSorting.formatRelative(now - 3 * 3_600_000L, now))
        assertEquals("1 day ago", FleetSorting.formatRelative(now - dayMs, now))
        assertEquals("6 days ago", FleetSorting.formatRelative(now - 6 * dayMs, now))
    }

    // --- Pool brand -----------------------------------------------------------

    @Test
    fun poolBrandFromKnownPoolUrls() {
        assertEquals("ViaBTC", FleetSorting.poolBrandFromUrl("stratum+tcp://stratum.viabtc.com:3333"))
        assertEquals("AntPool", FleetSorting.poolBrandFromUrl("stratum+tcp://ss.antpool.com:3333"))
        assertNull(FleetSorting.poolBrandFromUrl(null))
        assertNull(FleetSorting.poolBrandFromUrl(""))
        assertNull(FleetSorting.poolBrandFromUrl("   "))
    }

    @Test
    fun workerNameFallsBackToRemoteIdentity() {
        val remoteOnly = MergedMinerEntry(
            key = "remote:x",
            remoteSnapshot = snapshot("bani009.001", 10.0, now - 60_000L)
        )
        assertEquals("bani009.001", FleetSorting.workerNameOf(remoteOnly))
        val local = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1", "banishwor.001")
        )
        assertEquals("banishwor.001", FleetSorting.workerNameOf(local))
    }

    @Test
    fun workerNameNeverSurfacesAUuid() {
        // Regression (v0.1.4): rows relayed before the uploader knew its
        // worker name carry a per-device UUID in the id field — the
        // fleet row used to print that UUID as the machine's subtitle.
        // The worker name is recovered from the nickname instead.
        val uuidRow = RemoteMinerSnapshot(
            user = "bishambhor",
            id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64",
            nickname = "bishambhor.001 (Antminer S19k Pro)",
            ip = "192.168.1.20",
            hashrate5s = 0.0,
            avgHashrate = 0.0,
            maxTemp = 60,
            maxFan = 0,
            status = "Offline",
            lastUpdated = (now - dayMs).toString()
        )
        val entry = MergedMinerEntry(key = "remote:x", remoteSnapshot = uuidRow)

        assertEquals("bishambhor.001", FleetSorting.workerNameOf(entry))
    }

    // --- Row subtitle (v0.1.8: never repeat the title) --------------------

    @Test
    fun subtitleDoesNotRepeatWorkerIdentityTitle() {
        // Uploads carry the worker identity in the nickname field, so an
        // un-nicknamed remote machine's title IS its worker name — the
        // subtitle must identify it by owner / IP instead of repeating.
        val snap = RemoteMinerSnapshot(
            user = "bishambhor",
            id = "bishambhor.001",
            nickname = "bishambhor.001",
            ip = "192.168.1.20",
            hashrate5s = 100.0,
            avgHashrate = 100.0,
            maxTemp = 67,
            maxFan = 4300,
            status = "Online",
            lastUpdated = now.toString(),
            model = "Antminer S19k Pro"
        )
        val entry = MergedMinerEntry(key = "remote:x", remoteSnapshot = snap)
        val row = FleetSorting.rowFor(entry, now)
        assertEquals("bishambhor.001", row.displayName)
        assertEquals(
            "bishambhor · 192.168.1.20 · Antminer S19k Pro",
            FleetSorting.rowSubtitle(row)
        )
    }

    @Test
    fun subtitleIsBlankWhenTitleIsWorkerAndNothingElseKnown() {
        val snap = RemoteMinerSnapshot(
            user = "",
            id = "banishwor.002",
            nickname = "banishwor.002",
            ip = "",
            hashrate5s = 0.0,
            avgHashrate = 0.0,
            maxTemp = 0,
            maxFan = 0,
            status = "Offline",
            lastUpdated = ""
        )
        val entry = MergedMinerEntry(key = "remote:x", remoteSnapshot = snap)
        val row = FleetSorting.rowFor(entry, now)
        assertEquals("", FleetSorting.rowSubtitle(row))
    }

    @Test
    fun subtitleKeepsWorkerUnderRealNickname() {
        // A (phone / profile) nickname as the title: the worker identity
        // stays on the subtitle line, brand first when known.
        val snap = RemoteMinerSnapshot(
            user = "banishwor",
            id = "banishwor.001",
            nickname = "banishwor.001",
            ip = "192.168.1.2",
            hashrate5s = 110.0,
            avgHashrate = 110.0,
            maxTemp = 62,
            maxFan = 4200,
            status = "Online",
            lastUpdated = now.toString()
        )
        val entry = MergedMinerEntry(
            key = "remote:x",
            remoteSnapshot = snap,
            phoneDisplayName = "Hall 1, Left"
        )
        val row = FleetSorting.rowFor(entry, now, poolBrand = "ViaBTC")
        assertEquals("Hall 1, Left", row.displayName)
        assertEquals("ViaBTC · banishwor.001", FleetSorting.rowSubtitle(row))
    }

    @Test
    fun subtitleForLocalRowWithoutWorkerFallsBackToHost() {
        val entry = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1")
        )
        val row = FleetSorting.rowFor(entry, now)
        assertEquals("192.168.1.10", FleetSorting.rowSubtitle(row))
    }

    // --- Row UI projection (v0.2.0 strict strip) ---------------------------
    // The Home row renders FleetRowUi only: source appears solely as the
    // LIVE/REMOTE chip, and no pool/temp/share data exists in the model.

    @Test
    fun rowUi_remoteOnlyOnlineShowsRemoteChipAndShortFreshness() {
        val entry = MergedMinerEntry(
            key = "remote:a",
            remoteSnapshot = snapshot("bani009.001", 35.4, now - 60_000L)
        )
        val ui = FleetSorting.rowUi(FleetSorting.rowFor(entry, now), now)
        assertEquals(FleetSourceChip.REMOTE, ui.chip)
        assertTrue(ui.isOnline)
        assertEquals("35.4 TH/s", ui.hashrateLabel)
        assertEquals("Updated 1 min ago", ui.freshnessLine)
        // Title already is the worker identity — no repeated second line.
        assertEquals("", ui.workerLine)
    }

    @Test
    fun rowUi_mergedMachineWithLiveReadingIsLive() {
        val entry = MergedMinerEntry(
            key = "local:1",
            localProfile = profile("1", "banishwor.001"),
            remoteSnapshot = snapshot("banishwor.001", 30.0, now - 60_000L),
            localHashrateTh = 37.1
        )
        val ui = FleetSorting.rowUi(FleetSorting.rowFor(entry, now), now)
        assertEquals(FleetSourceChip.LIVE, ui.chip)
        assertTrue(ui.isOnline)
        assertEquals("37.1 TH/s", ui.hashrateLabel)
        assertEquals("", ui.freshnessLine)
    }

    @Test
    fun rowUi_offlineRemoteShowsFullLastUpdatedLine() {
        val entry = MergedMinerEntry(
            key = "remote:d",
            remoteSnapshot = snapshot("bani009.201", 40.0, now - 6 * dayMs)
        )
        val ui = FleetSorting.rowUi(FleetSorting.rowFor(entry, now), now)
        assertEquals(FleetSourceChip.REMOTE, ui.chip)
        assertFalse(ui.isOnline)
        assertEquals("Offline", ui.hashrateLabel)
        assertTrue(ui.freshnessLine.startsWith("Last updated 6 days ago · "))
    }

    @Test
    fun rowUi_nicknamedMachineShowsWorkerUnderNickname() {
        val entry = MergedMinerEntry(
            key = "remote:x",
            remoteSnapshot = snapshot("banishwor.001", 110.0, now - 60_000L),
            phoneDisplayName = "Hall Miner"
        )
        val ui = FleetSorting.rowUi(FleetSorting.rowFor(entry, now), now)
        assertEquals("Hall Miner", ui.displayName)
        assertEquals("banishwor.001", ui.workerLine)
    }

    @Test
    fun rowUi_offlineLocalIsLiveChipWithLastUpdatedLine() {
        val entry = MergedMinerEntry(
            key = "local:2",
            localProfile = profile("2", "banishwor.002", lastConnected = now - 3_600_000L)
        )
        val ui = FleetSorting.rowUi(FleetSorting.rowFor(entry, now), now)
        assertEquals(FleetSourceChip.LIVE, ui.chip)
        assertFalse(ui.isOnline)
        assertEquals("Offline", ui.hashrateLabel)
        assertTrue(ui.freshnessLine.startsWith("Last updated 1 h ago · "))
    }

    @Test
    fun rowUi_declaresOnlyRowFields() {
        val fieldNames = FleetRowUi::class.java.declaredFields
            .map { it.name.lowercase() }
            .filter { !it.startsWith("$") }
            .toSet()
        assertEquals(
            setOf("displayname", "workerline", "chip", "isonline", "hashratelabel", "freshnessline"),
            fieldNames
        )
        val forbidden = listOf("pool", "temp", "fan", "share", "brand")
        assertTrue(fieldNames.none { name -> forbidden.any { name.contains(it) } })
    }
}
