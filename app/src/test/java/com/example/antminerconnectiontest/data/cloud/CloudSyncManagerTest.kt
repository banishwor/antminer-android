package com.example.antminerconnectiontest.data.cloud

import com.example.antminerconnectiontest.domain.model.MinerHealthFlags
import com.example.antminerconnectiontest.domain.model.RemoteChainSnapshot
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudSyncManagerTest {

    private val cloudSyncManager = CloudSyncManager()

    @Test
    fun fetchRemoteMiners_withEmptyUrl_returnsFailure() = runTest {
        val result = cloudSyncManager.fetchRemoteMiners("", "Master")
        assertTrue(result.isFailure)
    }

    @Test
    fun fetchRemoteMiners_withEmptyUser_returnsFailure() = runTest {
        val result = cloudSyncManager.fetchRemoteMiners("https://script.google.com/test", "")
        assertTrue(result.isFailure)
    }

    @Test
    fun uploadLocalMiners_withEmptyUrl_returnsFailure() = runTest {
        val result = cloudSyncManager.uploadLocalMiners("", "Master", emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun deleteRemoteRows_withEmptyUrl_returnsFailure() = runTest {
        val result = cloudSyncManager.deleteRemoteRows("", "Master", listOf("banishwor.001"))
        assertTrue(result.isFailure)
    }

    @Test
    fun deleteRemoteRows_withNoIds_succeedsWithoutCallingEndpoint() = runTest {
        val result = cloudSyncManager.deleteRemoteRows("https://script.google.com/test", "Master", emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun buildUploadPayloadData_declaresUpsertAndCarriesWorkerIds() {
        val miner = RemoteMinerSnapshot(
            user = "Master",
            id = "banishwor.001",
            nickname = "banishwor.001 (Antminer S19k Pro)",
            ip = "192.168.1.3",
            hashrate5s = 121.6,
            avgHashrate = 121.9,
            maxTemp = 63,
            maxFan = 5910,
            status = "Online",
            lastUpdated = ""
        )
        val payload = CloudSyncManager.buildUploadPayloadData(" Master ", listOf(miner))
        assertEquals("Master", payload["user"])
        assertEquals("upsert", payload["mode"])
        @Suppress("UNCHECKED_CAST")
        val miners = payload["miners"] as List<Map<String, Any>>
        assertEquals(1, miners.size)
        assertEquals("banishwor.001", miners.single()["id"])
        assertEquals(121.6, miners.single()["hashrate5s"])
        assertEquals("Online", miners.single()["status"])
    }

    private fun extendedMiner() = RemoteMinerSnapshot(
        user = "Master",
        id = "banishwor.002",
        nickname = "banishwor.002 (Antminer S19k Pro)",
        ip = "192.168.1.3",
        hashrate5s = 121.56,
        avgHashrate = 121.9,
        maxTemp = 63,
        maxFan = 5910,
        status = "Online",
        lastUpdated = "",
        model = "Antminer S19k Pro",
        firmware = "fw-2024.11",
        uptimeSec = 273_120L,
        hwErrors = 4L,
        hashrate30m = 120.4,
        idealHashrate = 120.0,
        maxChipTemp = 63,
        maxPcbTemp = 41,
        fanRpms = listOf(5910, 5880, 2400, 6020),
        poolUrl = "stratum+tcp://btc.viabtc.io:3333",
        poolWorker = "banishwor.002",
        poolStatus = "Alive",
        acceptedShares = 54321L,
        rejectedShares = 17L,
        health = MinerHealthFlags(rateOk = true, networkOk = true, fansOk = false, tempOk = true),
        chains = listOf(
            RemoteChainSnapshot(
                chainIndex = 1, realHashrateTh = 40.52, idealHashrateTh = 40.0,
                frequencyMhz = 485.0, asicCount = 63, asicStatusString = "ooooxooo",
                chipMaxTemp = 63, chipMinTemp = 51, pcbMaxTemp = 41, pcbMinTemp = 35,
                hwErrors = 4L
            )
        )
    )

    @Test
    fun buildUploadPayloadData_extendedSnapshot_carriesEveryTier() {
        val payload = CloudSyncManager.buildUploadPayloadData("Master", listOf(extendedMiner()))
        @Suppress("UNCHECKED_CAST")
        val miner = (payload["miners"] as List<Map<String, Any>>).single()
        assertEquals("Antminer S19k Pro", miner["model"])
        assertEquals("fw-2024.11", miner["firmware"])
        assertEquals(273_120L, miner["uptimeSec"])
        assertEquals(4L, miner["hwErrors"])
        assertEquals(120.4, miner["hashrate30m"])
        assertEquals(120.0, miner["idealHashrate"])
        assertEquals(63, miner["maxChipTemp"])
        assertEquals(41, miner["maxPcbTemp"])
        assertEquals("5910,5880,2400,6020", miner["fans"])
        assertEquals("stratum+tcp://btc.viabtc.io:3333", miner["poolUrl"])
        assertEquals("banishwor.002", miner["poolWorker"])
        assertEquals("Alive", miner["poolStatus"])
        assertEquals(54321L, miner["acceptedShares"])
        assertEquals(17L, miner["rejectedShares"])
        assertEquals("1101", miner["health"])
        val chainsJson = miner["chainsJson"] as String
        assertTrue(chainsJson.startsWith("["))
        assertTrue(chainsJson.contains("\"status\":\"ooooxooo\""))
        // Legacy columns remain populated for older readers.
        assertEquals(63, miner["maxTemp"])
        assertEquals(5910, miner["maxFan"])
    }

    @Test
    fun buildUploadPayloadData_legacySnapshot_omitsExtendedKeys() {
        val miner = RemoteMinerSnapshot(
            user = "Master", id = "banishwor.001", nickname = "banishwor.001",
            ip = "192.168.1.2", hashrate5s = 119.7, avgHashrate = 120.1,
            maxTemp = 0, maxFan = 0, status = "Online", lastUpdated = ""
        )
        val payload = CloudSyncManager.buildUploadPayloadData("Master", listOf(miner))
        @Suppress("UNCHECKED_CAST")
        val map = (payload["miners"] as List<Map<String, Any>>).single()
        for (key in listOf(
            "model", "firmware", "uptimeSec", "hwErrors", "hashrate30m",
            "idealHashrate", "maxChipTemp", "maxPcbTemp", "fans",
            "poolUrl", "poolWorker", "poolStatus", "acceptedShares",
            "rejectedShares", "health", "chainsJson"
        )) {
            assertFalse("key $key must be omitted when unknown", map.containsKey(key))
        }
    }

    @Test
    fun parseRemoteMiner_oldRow_extendedFieldsUnknown() {
        // A row exactly as written before v0.1.6: only the 10 old keys.
        val row = JSONObject(
            """{
              "user": "Master", "id": "banishwor.001",
              "nickname": "banishwor.001 (Antminer S19k Pro)",
              "ip": "192.168.1.2", "hashrate5s": 119.7, "avgHashrate": 120.1,
              "maxTemp": 63, "maxFan": 5880, "status": "Online",
              "lastUpdated": "2026-11-02T09:14:00.000Z"
            }"""
        )
        val snap = CloudSyncManager.parseRemoteMiner(row)
        assertEquals("banishwor.001", snap.id)
        assertEquals(119.7, snap.hashrate5s, 0.001)
        assertEquals(63, snap.maxTemp)
        assertEquals("", snap.model)
        assertEquals("", snap.firmware)
        assertNull(snap.uptimeSec)
        assertNull(snap.hwErrors)
        assertNull(snap.hashrate30m)
        assertNull(snap.idealHashrate)
        assertNull(snap.maxChipTemp)
        assertNull(snap.maxPcbTemp)
        assertTrue(snap.fanRpms.isEmpty())
        assertEquals("", snap.poolUrl)
        assertNull(snap.acceptedShares)
        assertNull(snap.rejectedShares)
        assertNull(snap.health)
        assertTrue(snap.chains.isEmpty())
    }

    @Test
    fun parseRemoteMiner_newRow_decodesEveryTier() {
        val original = extendedMiner().copy(lastUpdated = "2026-11-02T09:14:00.000Z")
        val payload = CloudSyncManager.buildUploadPayloadData("Master", listOf(original))
        @Suppress("UNCHECKED_CAST")
        val minerMap = (payload["miners"] as List<Map<String, Any>>).single()
        // Simulate the endpoint round-trip: row = payload fields + user.
        val row = JSONObject(minerMap).put("user", "Master")
        val snap = CloudSyncManager.parseRemoteMiner(row)
        assertEquals(original.model, snap.model)
        assertEquals(original.firmware, snap.firmware)
        assertEquals(original.uptimeSec, snap.uptimeSec)
        assertEquals(original.hwErrors, snap.hwErrors)
        assertEquals(original.hashrate30m!!, snap.hashrate30m!!, 0.001)
        assertEquals(original.idealHashrate!!, snap.idealHashrate!!, 0.001)
        assertEquals(original.maxChipTemp, snap.maxChipTemp)
        assertEquals(original.maxPcbTemp, snap.maxPcbTemp)
        assertEquals(original.fanRpms, snap.fanRpms)
        assertEquals(original.poolUrl, snap.poolUrl)
        assertEquals(original.poolWorker, snap.poolWorker)
        assertEquals(original.poolStatus, snap.poolStatus)
        assertEquals(original.acceptedShares, snap.acceptedShares)
        assertEquals(original.rejectedShares, snap.rejectedShares)
        assertEquals(original.health, snap.health)
        assertEquals(original.chains, snap.chains)
        assertEquals(1, snap.slowFanCount) // 2400 RPM fan survived the trip
    }

    @Test
    fun parseRemoteMiner_sheetStyleValues_stringsAndBlanks() {
        // The endpoint passes sheet cells through: numbers can arrive as
        // strings and empty cells as "" — both must parse honestly.
        val row = JSONObject()
            .put("user", "Master")
            .put("id", "banishwor.002")
            .put("nickname", "banishwor.002")
            .put("ip", "192.168.1.3")
            .put("hashrate5s", "121.56")
            .put("avgHashrate", "121.9")
            .put("maxTemp", "63")
            .put("maxFan", "5910")
            .put("status", "Online")
            .put("lastUpdated", "2026-11-02T09:14:00.000Z")
            .put("uptimeSec", "273120")
            .put("hwErrors", 4)          // integer cell
            .put("hashrate30m", "")       // empty cell
            .put("maxChipTemp", "")       // empty cell
            .put("acceptedShares", "54321")
        val snap = CloudSyncManager.parseRemoteMiner(row)
        assertEquals(121.56, snap.hashrate5s, 0.001)
        assertEquals(273120L, snap.uptimeSec)
        assertEquals(4L, snap.hwErrors)
        assertNull(snap.hashrate30m)  // "" is unknown, not 0.0
        assertNull(snap.maxChipTemp)  // "" is unknown, not 0
        assertEquals(54321L, snap.acceptedShares)
    }

    @Test
    fun buildDeletePayloadData_carriesActionAndIds() {
        val payload = CloudSyncManager.buildDeletePayloadData(
            "Master",
            listOf("a998a851-92d0-4bac-8d7e-c59d24d0ed64")
        )
        assertEquals("Master", payload["user"])
        assertEquals("deleteRows", payload["action"])
        assertEquals(listOf("a998a851-92d0-4bac-8d7e-c59d24d0ed64"), payload["ids"])
    }

    @Test
    fun remoteMinerSnapshot_effectiveDisplayName_formatsProperly() {
        val withNickname = RemoteMinerSnapshot(
            user = "Tompokchao",
            id = "1",
            nickname = "Nepra S19k #1",
            ip = "192.168.1.2",
            hashrate5s = 121.4,
            avgHashrate = 120.8,
            maxTemp = 64,
            maxFan = 6800,
            status = "Online",
            lastUpdated = "2026-08-30"
        )
        assertEquals("Nepra S19k #1", withNickname.effectiveDisplayName)

        val withoutNickname = RemoteMinerSnapshot(
            user = "Joykumar",
            id = "2",
            nickname = "",
            ip = "192.168.1.5",
            hashrate5s = 120.0,
            avgHashrate = 119.5,
            maxTemp = 62,
            maxFan = 6500,
            status = "Online",
            lastUpdated = "2026-08-30"
        )
        assertEquals("Joykumar Miner (192.168.1.5)", withoutNickname.effectiveDisplayName)
    }
}
