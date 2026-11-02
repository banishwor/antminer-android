package com.example.antminerconnectiontest.data.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DailyLogAnalysisPipelineTest {

    private fun resolveSampleFile(filename: String): File {
        val direct = File("logs examples", filename)
        if (direct.exists()) return direct
        val parent = File("../logs examples", filename)
        if (parent.exists()) return parent
        return direct
    }

    @Test
    fun testExtractionAndAnalysis_singleDaySample() {
        val sampleTar = resolveSampleFile("antminer_log-2026-09-15.tar")
        assertTrue("Sample TAR file must exist: ${sampleTar.absolutePath}", sampleTar.exists())

        val tempDir = File.createTempFile("test_tar_extract_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // 1. Test TAR Extraction
            TarExtractor.extractTar(sampleTar, tempDir)
            val extractedFiles = tempDir.walkTopDown().toList()
            assertTrue("Extracted files should not be empty", extractedFiles.size > 5)

            // 2. Test Daily Log Analysis
            val analysis = DailyLogAnalyzer.analyzeSingleDate(
                extractedRoot = tempDir,
                minerId = "test_miner",
                rawDateId = "/2026-09/15",
                dateStr = "2026-09-15"
            )

            assertNotNull("Analysis result should not be null", analysis)
            assertEquals("2026-09-15", analysis.date)
            assertEquals("/2026-09/15", analysis.rawDateId)
            assertEquals("Antminer S19k Pro", analysis.minerType)
            assertEquals("DGAHGMUBEABJC00B5", analysis.minerSn)

            // Verify temperature metrics
            assertTrue("Max chip temp should be positive", analysis.maxTemperature > 0)
            assertTrue("Max PCB temp should be positive", analysis.maxPcbTemperature > 0)
            assertTrue("Max chip temp should be realistic (e.g. 50-100C)", analysis.maxTemperature in 50..100)
            assertTrue("Max PCB temp should be realistic (e.g. 40-90C)", analysis.maxPcbTemperature in 40..90)
            // Verify per-chain temperatures
            assertTrue("Per-chain temperatures should not be empty", analysis.chainMaxTemperatures.isNotEmpty())
            analysis.chainMaxTemperatures.forEach { (chain, temp) ->
                assertTrue("Chain $chain max chip temp should be > 0 (was $temp)", temp > 0)
            }

            // Verify hashrate and records
            assertTrue("Average 30m hashrate should be > 0", analysis.avgHashrateTh > 0.0)
            assertTrue("Temperature record count should be > 0", analysis.temperatureRecordCount > 0)

            // Verify formatted human readable date helper
            assertEquals("15 September 2026", DailyLogAnalyzer.formatHumanReadableDate("2026-09-15"))
        } finally {
            tempDir.deleteRecursively()
            assertFalse("Temp dir should be deleted", tempDir.exists())
        }
    }

    @Test
    fun testExtractionAndAnalysis_multiDaySample() {
        val multiDayTar = resolveSampleFile("antminer_log_2026-09-15_2026-09-16.tar")
        if (!multiDayTar.exists()) return

        val tempDir = File.createTempFile("test_tar_multi_", "").apply {
            delete()
            mkdirs()
        }

        try {
            TarExtractor.extractTar(multiDayTar, tempDir)

            val analysis15 = DailyLogAnalyzer.analyzeSingleDate(
                extractedRoot = tempDir,
                minerId = "test_miner",
                rawDateId = "/2026-09/15",
                dateStr = "2026-09-15"
            )
            val analysis16 = DailyLogAnalyzer.analyzeSingleDate(
                extractedRoot = tempDir,
                minerId = "test_miner",
                rawDateId = "/2026-09/16",
                dateStr = "2026-09-16"
            )

            assertEquals("2026-09-15", analysis15.date)
            assertEquals("2026-09-16", analysis16.date)
            assertTrue("Day 15 records > 0", analysis15.temperatureRecordCount > 0)
            assertTrue("Day 16 records > 0", analysis16.temperatureRecordCount > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
