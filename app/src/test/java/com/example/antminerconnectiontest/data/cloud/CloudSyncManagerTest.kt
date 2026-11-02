package com.example.antminerconnectiontest.data.cloud

import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import kotlinx.coroutines.test.runTest
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
