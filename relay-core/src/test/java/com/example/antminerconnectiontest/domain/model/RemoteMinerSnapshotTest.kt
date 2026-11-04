package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class RemoteMinerSnapshotTest {

    private val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun recentSnapshot_isNotStale_andRetainsHashrate() {
        val nowIso = sdfIso.format(Date(System.currentTimeMillis() - 30 * 1000L)) // 30s ago
        val snapshot = RemoteMinerSnapshot(
            user = "Tompokchao",
            id = "miner_1",
            nickname = "Nepra #1",
            ip = "192.168.1.2",
            hashrate5s = 121.5,
            avgHashrate = 120.8,
            maxTemp = 64,
            maxFan = 6800,
            status = "Online",
            lastUpdated = nowIso
        )

        assertFalse(snapshot.isStale)
        assertEquals(121.5, snapshot.effectiveHashrate5s, 0.01)
        assertEquals("Online", snapshot.effectiveStatus)
    }

    @Test
    fun oldSnapshot_isStale_andDropsHashrateToZero() {
        val oldIso = sdfIso.format(Date(System.currentTimeMillis() - 5 * 60 * 1000L)) // 5 minutes ago
        val snapshot = RemoteMinerSnapshot(
            user = "Tompokchao",
            id = "miner_1",
            nickname = "Nepra #1",
            ip = "192.168.1.2",
            hashrate5s = 121.5,
            avgHashrate = 120.8,
            maxTemp = 64,
            maxFan = 6800,
            status = "Online",
            lastUpdated = oldIso
        )

        assertTrue(snapshot.isStale)
        assertEquals(0.0, snapshot.effectiveHashrate5s, 0.01)
        assertEquals(0.0, snapshot.effectiveAvgHashrate, 0.01)
        assertTrue(snapshot.effectiveStatus.contains("Offline"))
    }

    @Test
    fun humanReadableTimeAgo_formatsCorrectly() {
        val nowIso = sdfIso.format(Date(System.currentTimeMillis() - 45 * 1000L)) // 45s ago
        val snapshot = RemoteMinerSnapshot(
            user = "Tompokchao",
            id = "miner_1",
            nickname = "Nepra #1",
            ip = "192.168.1.2",
            hashrate5s = 121.5,
            avgHashrate = 120.8,
            maxTemp = 64,
            maxFan = 6800,
            status = "Online",
            lastUpdated = nowIso
        )

        assertTrue(snapshot.timeAgoFormatted.contains("s ago"))
    }
}
