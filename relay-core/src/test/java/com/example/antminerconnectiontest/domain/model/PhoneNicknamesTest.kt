package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone-only nicknames (v0.1.7): keys follow the machine's worker
 * identity, display resolution follows the documented precedence, the
 * store round-trips through its JSON encoding, and none of it touches
 * identity or merging.
 */
class PhoneNicknamesTest {

    private fun profile(
        id: String = "profile-uuid-1",
        workerName: String = "banishwor.001",
        nickname: String = "",
        displayName: String = "Antminer S19k Pro",
        host: String = "192.168.1.2"
    ) = MinerProfile(
        id = id,
        workerName = workerName,
        displayName = displayName,
        nickname = nickname,
        host = host
    )

    private fun snapshot(
        user: String = "Master",
        id: String = "banishwor.001",
        nickname: String = "",
        ip: String = "192.168.1.2"
    ) = RemoteMinerSnapshot(
        user = user,
        id = id,
        nickname = nickname,
        ip = ip,
        hashrate5s = 120.0,
        avgHashrate = 119.0,
        maxTemp = 63,
        maxFan = 5900,
        status = "Online",
        lastUpdated = ""
    )

    // ---- keys ----

    @Test
    fun workerKey_normalisesTrimAndCase() {
        assertEquals("banishwor.001", PhoneNicknames.workerKey("  Banishwor.001 "))
        assertNull(PhoneNicknames.workerKey(""))
        assertNull(PhoneNicknames.workerKey("   "))
        assertNull(PhoneNicknames.workerKey(null))
    }

    @Test
    fun keyForProfile_prefersWorkerIdentity_fallsBackToProfileId() {
        assertEquals("banishwor.001", PhoneNicknames.keyForProfile(profile()))
        assertEquals(
            "id:profile-uuid-9",
            PhoneNicknames.keyForProfile(profile(id = "Profile-UUID-9", workerName = ""))
        )
    }

    @Test
    fun keyForSnapshot_usesWorkerIdentity_evenForLegacyUuidRows() {
        assertEquals("banishwor.001", PhoneNicknames.keyForSnapshot(snapshot()))
        // Legacy row: UUID id, worker name recoverable from the nickname.
        assertEquals(
            "banishwor.002",
            PhoneNicknames.keyForSnapshot(
                snapshot(
                    id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64",
                    nickname = "banishwor.002 (Antminer S19k Pro)"
                )
            )
        )
        // No provable identity at all: the raw id is the fallback key.
        assertEquals(
            "id:a998a851-92d0-4bac-8d7e-c59d24d0ed64",
            PhoneNicknames.keyForSnapshot(
                snapshot(id = "a998a851-92d0-4bac-8d7e-c59d24d0ed64", nickname = "Nepra #1")
            )
        )
    }

    @Test
    fun keyForEntry_prefersWorkerIdentityFromEitherSide() {
        val merged = MergedMinerEntry(
            key = "local:x",
            localProfile = profile(workerName = ""),
            remoteSnapshot = snapshot()
        )
        assertEquals("banishwor.001", PhoneNicknames.keyForEntry(merged))
        val remoteOnly = MergedMinerEntry(key = "remote:x", remoteSnapshot = snapshot())
        assertEquals("banishwor.001", PhoneNicknames.keyForEntry(remoteOnly))
    }

    // ---- set / clear ----

    @Test
    fun withNickname_setsTrimsAndClears() {
        var map = PhoneNicknames.withNickname(emptyMap(), "banishwor.001", "  Hall 1, Left  ")
        assertEquals(mapOf("banishwor.001" to "Hall 1, Left"), map)
        map = PhoneNicknames.withNickname(map, "banishwor.001", "   ")
        assertTrue(map.isEmpty())
        // A blank key can never create an entry.
        assertTrue(PhoneNicknames.withNickname(emptyMap(), "", "Name").isEmpty())
    }

    // ---- persistence encoding ----

    @Test
    fun encodeParse_roundTrips() {
        val map = mapOf("banishwor.001" to "Hall 1, Left", "bani009.201" to "Roof unit")
        assertEquals(map, PhoneNicknames.parse(PhoneNicknames.encode(map)))
    }

    @Test
    fun parse_toleratesGarbageAndBlanks() {
        assertTrue(PhoneNicknames.parse(null).isEmpty())
        assertTrue(PhoneNicknames.parse("").isEmpty())
        assertTrue(PhoneNicknames.parse("not json").isEmpty())
        assertTrue(PhoneNicknames.parse("""{"a":"  "}""").isEmpty())
    }

    // ---- override lookup ----

    @Test
    fun overrideFor_findsWorkerKeyAndLegacyFallbackKey() {
        val byWorker = mapOf("banishwor.001" to "Hall 1")
        assertEquals("Hall 1", PhoneNicknames.overrideFor(byWorker, profile(), null))
        assertEquals("Hall 1", PhoneNicknames.overrideFor(byWorker, null, snapshot()))
        // Set before the worker name was known: stored under the profile id.
        val byFallback = mapOf("id:profile-uuid-1" to "Old Label")
        assertEquals("Old Label", PhoneNicknames.overrideFor(byFallback, profile(), null))
        assertNull(PhoneNicknames.overrideFor(emptyMap(), profile(), snapshot()))
    }

    // ---- display precedence ----

    @Test
    fun displayName_phoneOverrideWinsOverEverything() {
        val overrides = mapOf("banishwor.001" to "Phone Name")
        val name = PhoneNicknames.displayName(
            overrides,
            profile(nickname = "Profile Name"),
            snapshot(nickname = "Snapshot Name")
        )
        assertEquals("Phone Name", name)
    }

    @Test
    fun displayName_profileNicknameBeatsSnapshotNickname() {
        val name = PhoneNicknames.displayName(
            emptyMap(),
            profile(nickname = "Profile Name"),
            snapshot(nickname = "Snapshot Name")
        )
        assertEquals("Profile Name", name)
    }

    @Test
    fun displayName_snapshotNicknameBeatsWorkerIdentity() {
        val name = PhoneNicknames.displayName(
            emptyMap(),
            null,
            snapshot(user = "Tompokchao", id = "tompokchao.001", nickname = "Nepra #1")
        )
        assertEquals("Nepra #1", name)
    }

    @Test
    fun displayName_identityFormNicknamesAreNotNames() {
        // Current uploads: nickname field IS the worker identity.
        assertEquals(
            "banishwor.001",
            PhoneNicknames.displayName(emptyMap(), null, snapshot(nickname = "banishwor.001"))
        )
        // Older uploads: nickname is the uploader's effectiveName form.
        assertEquals(
            "banishwor.001",
            PhoneNicknames.displayName(
                emptyMap(), null,
                snapshot(nickname = "banishwor.001 (Antminer S19k Pro)")
            )
        )
    }

    @Test
    fun displayName_profileWithoutNicknameKeepsEffectiveName() {
        assertEquals(
            "banishwor.001 (Antminer S19k Pro)",
            PhoneNicknames.displayName(emptyMap(), profile(), null)
        )
    }

    @Test
    fun displayName_identitylessSnapshotFallsBackToUserAndIp() {
        val name = PhoneNicknames.displayName(
            emptyMap(), null,
            snapshot(user = "Joykumar", id = "", nickname = "", ip = "192.168.1.5")
        )
        assertEquals("Joykumar Miner (192.168.1.5)", name)
    }

    // ---- nicknames never touch identity / merging ----

    @Test
    fun mergingIgnoresNicknames_machineStillMergesByWorkerIdentity() {
        val overrides = mapOf("banishwor.001" to "Hall 1, Left")
        val entries = MinerMerge.merge(
            profiles = listOf(profile()),
            snapshots = listOf(snapshot(nickname = "banishwor.001"))
        )
        assertEquals(1, entries.size)
        // The override resolves for display on the merged entry…
        assertEquals(
            "Hall 1, Left",
            PhoneNicknames.displayName(overrides, entries.single())
        )
        // …while the machine's identity is exactly what it was without one.
        assertEquals(
            "banishwor.001",
            MinerMerge.workerIdentity(entries.single().remoteSnapshot!!)
        )
    }
}
