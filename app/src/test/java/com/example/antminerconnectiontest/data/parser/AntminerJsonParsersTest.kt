package com.example.antminerconnectiontest.data.parser

import org.junit.Assert.*
import org.junit.Test

class AntminerJsonParsersTest {

    @Test
    fun parseSystemInfo_realHardwarePayload() {
        val json = """
            {
                "minertype":"Antminer S19k Pro",
                "nettype":"DHCP",
                "netdevice":"eth0",
                "macaddr":"EC:84:39:D0:5E:1B",
                "hostname":"Antminer",
                "ipaddress":"192.168.1.2",
                "netmask":"255.255.255.0",
                "gateway":"",
                "dnsservers":"",
                "system_mode":"GNU/Linux",
                "system_kernel_version":"Linux 4.9.113 #1 SMP PREEMPT Tue Jun 24 17:17:09 CST 2025",
                "system_filesystem_version":"FR-1.17(250624-S19k Pro)",
                "firmware_type":"Release",
                "serinum":"DGAHGMUBEABJC00B6"
            }
        """.trimIndent()

        val info = AntminerJsonParsers.parseSystemInfo(json)
        assertEquals("Antminer S19k Pro", info.minerType)
        assertEquals("EC:84:39:D0:5E:1B", info.mac)
        assertEquals("192.168.1.2", info.ip)
        assertEquals("255.255.255.0", info.netmask)
        assertEquals("DGAHGMUBEABJC00B6", info.serialNumber)
        assertEquals("FR-1.17(250624-S19k Pro)", info.filesystemVersion)
        assertTrue(info.kernelVersion.startsWith("Linux 4.9.113"))
    }

    @Test
    fun parseSummary_realHardwarePayload() {
        val json = """
            {
                "STATUS": {"STATUS": "S", "when": 1788078975, "Msg": "summary", "api_version": "1.0.0"},
                "INFO": {"miner_version": "uart_trans.1.3", "CompileTime": "Tue Jun 24 16:41:27 CST 2025", "type": "Antminer S19k Pro"},
                "SUMMARY": [{
                    "elapsed": 19457,
                    "rate_5s": 120770.36,
                    "rate_30m": 121947.34,
                    "rate_avg": 122054.06,
                    "rate_ideal": 120000.0,
                    "rate_unit": "GH/s",
                    "hw_all": 5,
                    "bestshare": 1348955175,
                    "status": [
                        {"type": "rate", "status": "s", "code": 0, "msg": ""},
                        {"type": "network", "status": "s", "code": 0, "msg": ""},
                        {"type": "fans", "status": "s", "code": 0, "msg": ""},
                        {"type": "temp", "status": "s", "code": 0, "msg": ""}
                    ]
                }]
            }
        """.trimIndent()

        val summary = AntminerJsonParsers.parseSummary(json)
        assertEquals(19457L, summary.elapsedSeconds)
        assertEquals(120.77, summary.hashrate5s, 0.01)
        assertEquals(121.95, summary.hashrate30m, 0.01)
        assertEquals(122.05, summary.hashrateAvg, 0.01)
        assertEquals(120.00, summary.hashrateIdeal, 0.01)
        assertEquals(5L, summary.hwAll)
        assertEquals(1348955175L, summary.bestShare)
        assertTrue(summary.rateHealth)
        assertTrue(summary.networkHealth)
        assertTrue(summary.fansHealth)
        assertTrue(summary.tempHealth)
        assertEquals("5h 24m", summary.formattedUptime)
    }

    @Test
    fun parseStats_realHardwarePayload() {
        val json = """
            {
                "STATUS": {"STATUS": "S", "when": 1788078975, "Msg": "stats", "api_version": "1.0.0"},
                "INFO": {"miner_version": "uart_trans.1.3", "CompileTime": "Tue Jun 24 16:41:27 CST 2025", "type": "Antminer S19k Pro"},
                "STATS": [{
                    "elapsed": 19457,
                    "rate_5s": 120770.36,
                    "rate_30m": 121947.34,
                    "rate_avg": 122053.3,
                    "rate_ideal": 120000.0,
                    "rate_unit": "GH/s",
                    "chain_num": 3,
                    "fan_num": 4,
                    "fan": [7000, 7000, 5991, 5991],
                    "hwp_total": 0.0,
                    "miner-mode": 0,
                    "freq-level": 100,
                    "chain": [
                        {
                            "index": 0,
                            "freq_avg": 590,
                            "rate_ideal": 40614.0,
                            "rate_real": 39758.33,
                            "asic_num": 77,
                            "asic": " ooooooo ooooooo",
                            "temp_pic": [42, 42, 49, 49],
                            "temp_pcb": [52, 52, 59, 59],
                            "temp_chip": [57, 57, 64, 64],
                            "hw": 1,
                            "eeprom_loaded": true,
                            "sn": "DGAHYTFBEAABF0AJ9",
                            "hwp": 0.0007
                        },
                        {
                            "index": 1,
                            "freq_avg": 590,
                            "rate_ideal": 40614.0,
                            "rate_real": 40835.86,
                            "asic_num": 77,
                            "asic": " ooooooo ooooooo",
                            "temp_pic": [43, 43, 50, 50],
                            "temp_pcb": [53, 53, 60, 60],
                            "temp_chip": [58, 58, 65, 65],
                            "hw": 3,
                            "eeprom_loaded": true,
                            "sn": "DGAHYTFBEAABF0ATJ",
                            "hwp": 0.0021
                        },
                        {
                            "index": 2,
                            "freq_avg": 590,
                            "rate_ideal": 40614.0,
                            "rate_real": 40176.15,
                            "asic_num": 77,
                            "asic": " ooooooo ooooooo",
                            "temp_pic": [41, 41, 50, 50],
                            "temp_pcb": [51, 51, 60, 60],
                            "temp_chip": [56, 56, 65, 65],
                            "hw": 1,
                            "eeprom_loaded": true,
                            "sn": "DGAHYTFBEAABF0AY9",
                            "hwp": 0.0007
                        }
                    ]
                }]
            }
        """.trimIndent()

        val stats = AntminerJsonParsers.parseStats(json)
        assertEquals(3, stats.chains.size)
        assertEquals(4, stats.fans.size)
        assertEquals(5L, stats.totalHwErrors)
        assertEquals(65, stats.maxChipTemp)
        assertEquals(60, stats.maxPcbTemp)
        assertEquals(7000, stats.maxFanRpm)

        assertEquals(1, stats.chains[0].chainIndex)
        assertEquals(39.76, stats.chains[0].realHashrateTh, 0.01)
        assertEquals(40.61, stats.chains[0].idealHashrateTh, 0.01)
        assertEquals(590.0, stats.chains[0].frequencyMhz, 0.01)
        assertEquals("DGAHYTFBEAABF0AJ9", stats.chains[0].serialNumber)
        assertEquals(64, stats.chains[0].chipMaxTemp)
        assertEquals(57, stats.chains[0].chipMinTemp)
        assertEquals(59, stats.chains[0].pcbMaxTemp)
        assertEquals(49, stats.chains[0].picMaxTemp)

        assertEquals(2, stats.chains[1].chainIndex)
        assertEquals(40.84, stats.chains[1].realHashrateTh, 0.01)

        assertEquals(3, stats.chains[2].chainIndex)
        assertEquals(40.18, stats.chains[2].realHashrateTh, 0.01)

        assertEquals(7000, stats.fans[0].rpm)
        assertEquals(7000, stats.fans[1].rpm)
        assertEquals(5991, stats.fans[2].rpm)
        assertEquals(5991, stats.fans[3].rpm)
    }

    @Test
    fun parsePools_realHardwarePayload() {
        val json = """
            {
                "STATUS": {"STATUS": "S", "when": 1788078975, "Msg": "pools", "api_version": "1.0.0"},
                "INFO": {"miner_version": "uart_trans.1.3", "CompileTime": "Tue Jun 24 16:41:27 CST 2025", "type": "Antminer S19k Pro"},
                "POOLS": [
                    {
                        "index": 0,
                        "url": "stratum+tcp://btc.viabtc.io:3333",
                        "user": "banishwor.002",
                        "status": "Alive",
                        "priority": 0,
                        "getworks": 1891,
                        "accepted": 2162,
                        "rejected": 7,
                        "discarded": 1802793,
                        "stale": 0,
                        "diff": "262K",
                        "lstime": "0:00:28"
                    },
                    {
                        "index": 1,
                        "url": "stratum+tcp://btc.viabtc.io:3333",
                        "user": "banishwor.002",
                        "status": "Alive",
                        "priority": 1,
                        "getworks": 0,
                        "accepted": 0,
                        "rejected": 0,
                        "discarded": 0,
                        "stale": 0,
                        "diff": "",
                        "lstime": "0"
                    },
                    {
                        "index": 2,
                        "url": "stratum+tcp://bitcoin.viabtc.io:3333",
                        "user": "banishwor.002",
                        "status": "Alive",
                        "priority": 2,
                        "getworks": 1,
                        "accepted": 0,
                        "rejected": 0,
                        "discarded": 0,
                        "stale": 0,
                        "diff": "16.4K",
                        "lstime": "0"
                    }
                ]
            }
        """.trimIndent()

        val pools = AntminerJsonParsers.parsePools(json)
        assertEquals(3, pools.size)
        assertEquals("Primary", pools[0].priorityLabel)
        assertEquals("stratum+tcp://btc.viabtc.io:3333", pools[0].url)
        assertEquals("banishwor.002", pools[0].user)
        assertEquals(2162L, pools[0].acceptedShares)
        assertEquals(7L, pools[0].rejectedShares)
        assertEquals("262K", pools[0].diff)
        assertEquals("0:00:28", pools[0].lastShareTimeString)

        assertEquals("Backup 1", pools[1].priorityLabel)
        assertEquals("Backup 2", pools[2].priorityLabel)
    }

    @Test
    fun parseWarnings_realHardwarePayload() {
        val json = """{"STATUS": {"STATUS": "S", "when": 1788078974, "Msg": "warning", "api_version": "1.0.0"}, "INFO": {"miner_version": "uart_trans.1.3", "CompileTime": "Tue Jun 24 16:41:27 CST 2025", "type": "Antminer S19k Pro"}, "error_message": ""}"""
        val warnings = AntminerJsonParsers.parseWarnings(json)
        assertFalse(warnings.hasWarnings)
        assertTrue(warnings.activeWarnings.isEmpty())
    }

    @Test
    fun parseChart_realHardwarePayload() {
        val json = """
            {
                "STATUS": {"STATUS": "S", "when": 1788078974, "Msg": "rate", "api_version": "1.0.0"},
                "INFO": {"miner_version": "uart_trans.1.3", "CompileTime": "Tue Jun 24 16:41:27 CST 2025", "type": "Antminer S19k Pro"},
                "RATE": [{
                    "unit": "GH/s",
                    "xAxis": ["15min", "30min", "45min"],
                    "series": [
                        {"name": "chain0", "data": [41217, 40627, 40943]},
                        {"name": "chain1", "data": [40926, 40663, 40490]},
                        {"name": "chain2", "data": [40921, 40491, 41059]}
                    ]
                }]
            }
        """.trimIndent()

        val chart = AntminerJsonParsers.parseChart(json)
        assertEquals(3, chart.timePoints.size)
        assertEquals("15min", chart.timePoints[0].timeLabel)
        // 41217 + 40926 + 40921 = 123064 GH/s -> 123.06 TH/s
        assertEquals(123.06, chart.timePoints[0].totalHashrateTh, 0.01)
    }
}
