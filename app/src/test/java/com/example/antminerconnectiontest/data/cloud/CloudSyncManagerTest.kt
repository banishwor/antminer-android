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
