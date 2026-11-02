package com.example.antminerconnectiontest.domain.model

import com.example.antminerconnectiontest.ui.components.MinerDetailUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v0.2.0 strict-strip guard: Home's hero may only ever render the
 * [HeroSummary] projection. These tests pin the projection's field set
 * (no board / fan / pool / share / log data can leak onto Home) and its
 * mapping from the full detail model.
 */
class HomeModelsTest {

    private fun detail(
        health: MinerHealthFlags? = MinerHealthFlags(
            rateOk = true, networkOk = true, fansOk = true, tempOk = true
        ),
        isRemote: Boolean = false,
        lastUpdatedLabel: String? = null
    ) = MinerDetailUi(
        displayName = "Hall Miner",
        workerName = "banishwor.001",
        modelName = "Antminer S19k Pro",
        firmware = "FR-1.17",
        modelLine = "Antminer S19k Pro • FR-1.17",
        subLine = "192.168.1.2",
        isRemote = isRemote,
        isOnline = true,
        lastUpdatedLabel = lastUpdatedLabel,
        hashrateTh = 110.5,
        avgHashrateTh = 108.25,
        idealHashrateTh = 120.0,
        rate30mTh = 109.0,
        uptimeLabel = "2d 4h",
        hwErrors = 12L,
        statusText = "Online",
        chipTempC = 68,
        pcbTempC = 55,
        fanRpms = listOf(4320, 4260, 4380, 4200),
        maxFanRpm = 4380,
        health = health,
        poolUrl = "stratum+tcp://btc.viabtc.io:3333",
        poolWorker = "banishwor.001",
        poolStatus = "Alive",
        acceptedShares = 12_345L,
        rejectedShares = 67L,
        chains = listOf(
            RemoteChainSnapshot(
                chainIndex = 0,
                realHashrateTh = 36.8,
                idealHashrateTh = 40.0,
                asicCount = 88,
                frequencyMhz = 525.0,
                chipMaxTemp = 68,
                chipMinTemp = 60,
                pcbMaxTemp = 55,
                pcbMinTemp = 50,
                hwErrors = 4L,
                asicStatusString = "oooo"
            )
        )
    )

    @Test
    fun heroSummary_declaresOnlySummaryFields() {
        val fieldNames = HeroSummary::class.java.declaredFields
            .map { it.name.lowercase() }
            .filter { !it.startsWith("$") }
            .toSet()
        // The exact approved field set — adding a field here is a
        // design decision, not an accident.
        assertEquals(
            setOf(
                "displayname", "workername", "modelname", "isremote",
                "isonline", "updatedline", "statustext", "hashrateth",
                "avghashrateth", "uptimelabel", "health"
            ),
            fieldNames
        )
        // And nothing that smells like tab detail may ever appear.
        val forbidden = listOf("chain", "board", "fan", "pool", "share", "temp", "log")
        assertTrue(
            "hero fields leak tab detail: $fieldNames",
            fieldNames.none { name -> forbidden.any { name.contains(it) } }
        )
    }

    @Test
    fun heroSummary_mapsSummaryAndDropsDetail() {
        val summary = HeroSummary.from(detail())
        assertEquals("Hall Miner", summary.displayName)
        assertEquals("banishwor.001", summary.workerName)
        assertEquals("Antminer S19k Pro", summary.modelName)
        assertEquals(false, summary.isRemote)
        assertEquals(true, summary.isOnline)
        assertEquals("Online", summary.statusText)
        assertEquals(110.5, summary.hashrateTh, 0.001)
        assertEquals(108.25, summary.avgHashrateTh, 0.001)
        assertEquals("2d 4h", summary.uptimeLabel)
        assertEquals(HeroSummary.HealthState.HEALTHY, summary.health)
        // A live machine has no cloud "updated" line.
        assertNull(summary.updatedLine)
    }

    @Test
    fun heroSummary_remoteCarriesUpdatedLine() {
        val summary = HeroSummary.from(
            detail(isRemote = true, lastUpdatedLabel = "Updated 2 min ago · via relay")
        )
        assertEquals(true, summary.isRemote)
        assertEquals("Updated 2 min ago · via relay", summary.updatedLine)
    }

    @Test
    fun heroSummary_healthMapping() {
        assertEquals(
            HeroSummary.HealthState.UNKNOWN,
            HeroSummary.from(detail(health = null)).health
        )
        assertEquals(
            HeroSummary.HealthState.ISSUES,
            HeroSummary.from(
                detail(
                    health = MinerHealthFlags(
                        rateOk = true, networkOk = true, fansOk = false, tempOk = true
                    )
                )
            ).health
        )
    }
}
