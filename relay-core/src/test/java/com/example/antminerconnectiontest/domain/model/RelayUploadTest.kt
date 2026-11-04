package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Write-side relay identity rules: one stable sheet identity per machine
 * (the worker username), never a profile UUID, and selection of the
 * legacy duplicate rows a relay write asks the endpoint to remove.
 */
class RelayUploadTest {

    private fun profile(workerName: String, id: String = "profile-uuid-1") = MinerProfile(
        id = id,
        workerName = workerName,
        host = "192.168.1.3"
    )

    private fun snapshot(
        user: String = "Master",
        id: String,
        nickname: String = "",
        ip: String = "192.168.1.3",
        lastUpdated: String = ""
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
        lastUpdated = lastUpdated
    )

    // ---- identity selection ----

    @Test
    fun sheetIdFor_knownWorkerName_isThatName() {
        assertEquals("banishwor.001", RelayUpload.sheetIdFor(profile("banishwor.001")))
    }

    @Test
    fun sheetIdFor_trimsWhitespaceButPreservesCase() {
        // The id must stay byte-identical to what the miner's pool config
        // reports (and to existing sheet rows) apart from stray padding.
        assertEquals("Banishwor.001", RelayUpload.sheetIdFor(profile("  Banishwor.001 ")))
    }

    @Test
    fun sheetIdFor_blankWorkerName_defers_noSubstituteIdentity() {
        assertNull(RelayUpload.sheetIdFor(profile("")))
        assertNull(RelayUpload.sheetIdFor(profile("   ")))
        assertNull(RelayUpload.sheetIdForWorkerName(null))
        assertNull(RelayUpload.sheetIdForWorkerName(""))
    }

    // ---- prepareSnapshots: the no-UUID gate ----

    @Test
    fun prepareSnapshots_dropsUuidKeyedSnapshots() {
        val uuidRow = snapshot(id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64")
        val workerRow = snapshot(id = "banishwor.001")
        val prepared = RelayUpload.prepareSnapshots(listOf(uuidRow, workerRow))
        assertEquals(listOf("banishwor.001"), prepared.map { it.id })
    }

    @Test
    fun prepareSnapshots_dropsBlankIdsAndCanonicalises() {
        val prepared = RelayUpload.prepareSnapshots(
            listOf(snapshot(id = ""), snapshot(id = "  bani009.002 "))
        )
        assertEquals(listOf("bani009.002"), prepared.map { it.id })
    }

    @Test
    fun prepareSnapshots_collapsesInBatchDuplicates_lastWins() {
        val older = snapshot(id = "banishwor.001").copy(hashrate5s = 90.0)
        val fresher = snapshot(id = " Banishwor.001").copy(hashrate5s = 121.6)
        val prepared = RelayUpload.prepareSnapshots(listOf(older, fresher))
        assertEquals(1, prepared.size)
        assertEquals(121.6, prepared.single().hashrate5s, 0.001)
        assertEquals("Banishwor.001", prepared.single().id)
    }

    // ---- phone-only nicknames (v0.1.7): the nickname field is the identity ----

    @Test
    fun prepareSnapshots_rewritesNicknameToWorkerIdentity() {
        // A snapshot built with a free-form (phone) nickname must reach
        // the sheet carrying the worker identity in the nickname field —
        // nicknames never leave the phone.
        val prepared = RelayUpload.prepareSnapshots(
            listOf(snapshot(id = "banishwor.001", nickname = "Hall 1, Left"))
        )
        assertEquals("banishwor.001", prepared.single().nickname)
        assertEquals("banishwor.001", prepared.single().id)
    }

    @Test
    fun prepareSnapshots_identityStillRecoverableFromNicknameByOldReaders() {
        // Older app versions recover a row's worker name by parsing the
        // nickname — with the identity in that field, recovery is exact.
        val prepared = RelayUpload.prepareSnapshots(
            listOf(snapshot(id = "banishwor.001", nickname = "My Secret Nickname"))
        )
        assertEquals("banishwor.001", MinerMerge.workerIdentity(prepared.single()))
        assertTrue(
            MinerMerge.remoteWorkerCandidates(prepared.single()).contains("banishwor.001")
        )
    }

    // ---- legacy duplicate selection (sheet healing) ----

    @Test
    fun legacyDuplicateIds_selectsUuidTwinOfMachineBeingUploaded() {
        val fetched = listOf(
            snapshot(id = "banishwor.001", nickname = "banishwor.001 (Antminer S19k Pro)"),
            snapshot(id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "banishwor.001 (Antminer S19k Pro)")
        )
        val delete = RelayUpload.legacyDuplicateIds(fetched, "Master", listOf("banishwor.001"))
        assertEquals(listOf("a998a851-92d0-4bac-8d7e-c59d24d0ed64"), delete)
    }

    @Test
    fun legacyDuplicateIds_selectsCaseVariantWorkerRow() {
        val fetched = listOf(snapshot(id = "Banishwor.001"))
        val delete = RelayUpload.legacyDuplicateIds(fetched, "Master", listOf("banishwor.001"))
        assertEquals(listOf("Banishwor.001"), delete)
    }

    @Test
    fun legacyDuplicateIds_neverSelectsTheCanonicalRowItself() {
        val fetched = listOf(snapshot(id = "banishwor.001"))
        assertTrue(RelayUpload.legacyDuplicateIds(fetched, "Master", listOf("banishwor.001")).isEmpty())
    }

    @Test
    fun legacyDuplicateIds_neverSelectsAnotherUsersRows() {
        // The fetch can return machines this user may only view; their
        // rows are not ours to delete, even for a matching worker name.
        val fetched = listOf(
            snapshot(user = "SomeoneElse", id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "banishwor.001 (Antminer S19k Pro)")
        )
        assertTrue(RelayUpload.legacyDuplicateIds(fetched, "Master", listOf("banishwor.001")).isEmpty())
    }

    @Test
    fun legacyDuplicateIds_ignoresOtherMachinesAndIdentitylessRows() {
        val fetched = listOf(
            snapshot(id = "bishambhor.001", nickname = "bishambhor.001 (Antminer S19k Pro)"),
            snapshot(id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "Nepra #1"),
            snapshot(id = "b998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "bani009.001 (Antminer S19k Pro)")
        )
        assertTrue(RelayUpload.legacyDuplicateIds(fetched, "Master", listOf("banishwor.001")).isEmpty())
    }

    @Test
    fun legacyDuplicateIds_blankUserOrNoCanonicalIds_selectsNothing() {
        val fetched = listOf(
            snapshot(id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "banishwor.001 (Antminer S19k Pro)")
        )
        assertTrue(RelayUpload.legacyDuplicateIds(fetched, "", listOf("banishwor.001")).isEmpty())
        assertTrue(RelayUpload.legacyDuplicateIds(fetched, "Master", emptyList()).isEmpty())
    }

    // ---- cross-user upload guard (v0.1.9) ----

    @Test
    fun filterUploadable_ownMachinesAndBrandNewMachines_pass() {
        val fleet = listOf(
            snapshot(user = "Master", id = "banishwor.001"),
            snapshot(user = "Tompokchao", id = "tompokchao.001")
        )
        val uploads = listOf(
            snapshot(id = "banishwor.001"), // own row in the sheet
            snapshot(id = "banishwor.002")  // not in the sheet yet — brand-new
        )
        val kept = RelayUpload.filterUploadable(uploads, "Master", fleet)
        assertEquals(listOf("banishwor.001", "banishwor.002"), kept.map { it.id })
    }

    @Test
    fun filterUploadable_anotherUsersMachine_isExcluded() {
        // Auto-connected on site at Tompokchao's: fully usable locally,
        // but uploading it as Master would fork its sheet row.
        val fleet = listOf(snapshot(user = "Tompokchao", id = "tompokchao.001"))
        val uploads = listOf(snapshot(id = "tompokchao.001"))
        assertTrue(RelayUpload.filterUploadable(uploads, "Master", fleet).isEmpty())
    }

    @Test
    fun filterUploadable_ownershipMatchIgnoresUserCaseAndWhitespace() {
        val fleet = listOf(snapshot(user = " master ", id = "banishwor.001"))
        val uploads = listOf(snapshot(id = "Banishwor.001"))
        assertEquals(1, RelayUpload.filterUploadable(uploads, "Master", fleet).size)
    }

    @Test
    fun filterUploadable_unknownFleetOrBlankUser_passesEverything() {
        val uploads = listOf(snapshot(id = "tompokchao.001"))
        // No fetch has succeeded yet: the guard cannot know ownership.
        assertEquals(1, RelayUpload.filterUploadable(uploads, "Master", emptyList()).size)
        assertEquals(1, RelayUpload.filterUploadable(uploads, "", listOf(snapshot(user = "X", id = "a.1"))).size)
    }
}
