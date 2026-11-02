package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.*
import org.junit.Test

class TelemetryCodecTest {

    // ── fans ────────────────────────────────────────────────────────

    @Test
    fun fans_roundTrip_preservesOrderAndZeros() {
        val rpms = listOf(5910, 5880, 0, 6020)
        assertEquals("5910,5880,0,6020", TelemetryCodec.encodeFans(rpms))
        assertEquals(rpms, TelemetryCodec.decodeFans(TelemetryCodec.encodeFans(rpms)))
    }

    @Test
    fun fans_emptyAndMalformed_decodeToEmptyOrPartial() {
        assertEquals("", TelemetryCodec.encodeFans(emptyList()))
        assertTrue(TelemetryCodec.decodeFans("").isEmpty())
        assertTrue(TelemetryCodec.decodeFans(null).isEmpty())
        assertTrue(TelemetryCodec.decodeFans("   ").isEmpty())
        // Garbage tokens are dropped, real readings survive.
        assertEquals(listOf(5910, 12), TelemetryCodec.decodeFans("5910, abc ,,12"))
    }

    // ── health ──────────────────────────────────────────────────────

    @Test
    fun health_roundTrip_allCombinations() {
        for (bits in 0..15) {
            val flags = MinerHealthFlags(
                rateOk = bits and 8 != 0,
                networkOk = bits and 4 != 0,
                fansOk = bits and 2 != 0,
                tempOk = bits and 1 != 0
            )
            assertEquals(flags, TelemetryCodec.decodeHealth(TelemetryCodec.encodeHealth(flags)))
        }
        assertEquals("1011", TelemetryCodec.encodeHealth(MinerHealthFlags(true, false, true, true)))
        assertEquals("0000", TelemetryCodec.encodeHealth(MinerHealthFlags(false, false, false, false)))
    }

    @Test
    fun health_malformed_decodesToNull() {
        assertNull(TelemetryCodec.decodeHealth(null))
        assertNull(TelemetryCodec.decodeHealth(""))
        assertNull(TelemetryCodec.decodeHealth("111"))     // too short
        assertNull(TelemetryCodec.decodeHealth("11111"))   // too long
        assertNull(TelemetryCodec.decodeHealth("11x1"))    // bad char
        assertNull(TelemetryCodec.decodeHealth("true"))    // words are not the encoding
    }

    // ── chains ──────────────────────────────────────────────────────

    private val chain1 = RemoteChainSnapshot(
        chainIndex = 1, realHashrateTh = 40.52, idealHashrateTh = 40.0,
        frequencyMhz = 485.0, asicCount = 63,
        asicStatusString = "ooooxooo",
        chipMaxTemp = 63, chipMinTemp = 51, pcbMaxTemp = 41, pcbMinTemp = 35,
        hwErrors = 4L
    )
    private val chain2 = RemoteChainSnapshot(
        chainIndex = 2, realHashrateTh = 39.87, idealHashrateTh = 40.0,
        frequencyMhz = 480.0, asicCount = 63,
        asicStatusString = "oooooooo",
        chipMaxTemp = 61, chipMinTemp = 49, pcbMaxTemp = 40, pcbMinTemp = 34,
        hwErrors = 0L
    )

    @Test
    fun chains_roundTrip_preservesEveryField() {
        val encoded = TelemetryCodec.encodeChains(listOf(chain1, chain2))
        assertTrue(encoded.startsWith("["))
        assertEquals(listOf(chain1, chain2), TelemetryCodec.decodeChains(encoded))
    }

    @Test
    fun chains_emptyAndMalformed_decodeToEmpty() {
        assertEquals("", TelemetryCodec.encodeChains(emptyList()))
        assertTrue(TelemetryCodec.decodeChains("").isEmpty())
        assertTrue(TelemetryCodec.decodeChains(null).isEmpty())
        assertTrue(TelemetryCodec.decodeChains("not json").isEmpty())
        assertTrue(TelemetryCodec.decodeChains("{").isEmpty())
    }

    @Test
    fun chains_deadAndMissingChips_comeFromStatusString() {
        val chain = chain1.copy(asicStatusString = "ooxx--ooX")
        assertEquals(3, chain.deadChips)
        assertEquals(2, chain.missingChips)
        assertEquals(0, chain2.deadChips)
        assertEquals(0, chain2.missingChips)
    }

    // ── withTelemetry ───────────────────────────────────────────────

    private val summary = MinerSummary(
        elapsedSeconds = 3L * 86400 + 4 * 3600 + 12 * 60,
        hashrate5s = 121.56, hashrateAvg = 121.9,
        hashrate30m = 120.4, hashrateIdeal = 120.0,
        hwAll = 99L,
        rateHealth = true, networkHealth = true, fansHealth = false, tempHealth = true
    )

    private val stats = MinerStats(
        chains = listOf(
            ChainStats(
                chainIndex = 1, rawIndex = 0,
                realHashrateTh = 40.52, idealHashrateTh = 40.0,
                frequencyMhz = 485.0, asicCount = 63,
                asicStatusString = "ooooxooo",
                chipMaxTemp = 63, chipMinTemp = 51,
                chipTemps = listOf(51, 63, 58), // per-chip arrays must NOT be relayed
                pcbMaxTemp = 41, pcbMinTemp = 35,
                pcbTemps = listOf(35, 41),
                hwErrors = 4L, hwp = 0.001
            )
        ),
        fans = listOf(FanStats(2, 5880), FanStats(1, 5910)), // unsorted on purpose
        totalHwErrors = 4L,
        maxChipTemp = 63, maxPcbTemp = 41, maxFanRpm = 5910
    )

    private val pools = listOf(
        MinerPool(
            index = 1, url = "stratum+tcp://backup:3333", user = "banishwor.002",
            status = "Alive", priority = 1, acceptedShares = 10, rejectedShares = 1
        ),
        MinerPool(
            index = 0, url = "stratum+tcp://btc.viabtc.io:3333", user = "banishwor.002",
            status = "Alive", priority = 0, acceptedShares = 54321, rejectedShares = 17
        )
    )

    private val systemInfo = MinerSystemInfo(
        minerType = "Antminer S19k Pro", nettype = "dhcp", netdevice = "eth0",
        mac = "AA:BB:CC:DD:EE:FF", hostname = "antminer", ip = "192.168.1.3",
        systemMode = "mining", systemVersion = "fw-2024.11",
        filesystemVersion = "fs1", firmwareType = "stock"
    )

    private fun baseSnapshot() = RemoteMinerSnapshot(
        user = "Master", id = "banishwor.002", nickname = "banishwor.002",
        ip = "192.168.1.3", hashrate5s = 121.56, avgHashrate = 121.9,
        maxTemp = 63, maxFan = 5910, status = "Online", lastUpdated = ""
    )

    @Test
    fun withTelemetry_fullData_populatesEveryTier() {
        val snap = baseSnapshot().withTelemetry(summary, stats, pools, systemInfo)
        assertEquals("Antminer S19k Pro", snap.model)
        assertEquals("fw-2024.11", snap.firmware)
        assertEquals(summary.elapsedSeconds, snap.uptimeSec)
        assertEquals(4L, snap.hwErrors) // stats total wins over summary hwAll (99)
        assertEquals(120.4, snap.hashrate30m!!, 0.001)
        assertEquals(120.0, snap.idealHashrate!!, 0.001)
        assertEquals(63, snap.maxChipTemp)
        assertEquals(41, snap.maxPcbTemp)
        assertEquals(listOf(5910, 5880), snap.fanRpms) // sorted by fan index
        // Primary pool = priority 0, even though listed second.
        assertEquals("stratum+tcp://btc.viabtc.io:3333", snap.poolUrl)
        assertEquals("banishwor.002", snap.poolWorker)
        assertEquals("Alive", snap.poolStatus)
        assertEquals(54321L, snap.acceptedShares)
        assertEquals(17L, snap.rejectedShares)
        assertEquals(MinerHealthFlags(true, true, false, true), snap.health)
        assertEquals(1, snap.chains.size)
        assertEquals(1, snap.chains.single().deadChips)
        assertEquals("3d 4h 12m", snap.formattedUptime)
        assertEquals(0, snap.slowFanCount)
    }

    @Test
    fun withTelemetry_noData_leavesEverythingUnknown() {
        val snap = baseSnapshot().withTelemetry(null, null, emptyList(), null)
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
        assertNull(snap.health)
        assertTrue(snap.chains.isEmpty())
        assertNull(snap.formattedUptime)
    }

    @Test
    fun withTelemetry_cachedModelFallback_andSummaryHwFallback() {
        val snap = baseSnapshot().withTelemetry(
            summary, null, emptyList(), null,
            fallbackModel = "Antminer S19k Pro"
        )
        assertEquals("Antminer S19k Pro", snap.model)
        assertEquals(99L, snap.hwErrors) // summary hwAll when stats are unavailable
        assertNull(snap.maxChipTemp)
        assertTrue(snap.chains.isEmpty())
    }

    @Test
    fun withTelemetry_chainsJson_neverCarriesPerChipTemps() {
        val snap = baseSnapshot().withTelemetry(summary, stats, pools, systemInfo)
        val encoded = TelemetryCodec.encodeChains(snap.chains)
        // The encoded form has exactly the summary keys — no temp arrays.
        assertFalse(encoded.contains("chipTemps"))
        assertFalse(encoded.contains("[51,63,58]"))
        assertEquals(listOf(chain1.copy(chainIndex = 1)), TelemetryCodec.decodeChains(encoded))
    }

    @Test
    fun slowFanCount_usesLocalThreshold() {
        val snap = baseSnapshot().copy(fanRpms = listOf(5910, 2400, 0, 3000))
        assertEquals(2, snap.slowFanCount) // 2400 and the stopped fan; 3000 is not below 3000
    }
}
