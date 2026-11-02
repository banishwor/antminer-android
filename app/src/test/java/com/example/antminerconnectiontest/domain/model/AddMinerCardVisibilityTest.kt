package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v0.2.3 rule for Home's "Add a miner" card: shown ONLY when
 * there is no miner at all — no saved local profiles and no merged
 * fleet entries (local or cloud). See [AddMinerCardVisibility].
 */
class AddMinerCardVisibilityTest {

    @Test
    fun emptyFleetAndNoProfiles_showsCard() {
        assertTrue(AddMinerCardVisibility.shouldShow(savedProfileCount = 0, mergedFleetCount = 0))
    }

    @Test
    fun localMinerOnly_hidesCard() {
        // A saved local profile that is also in the merged fleet.
        assertFalse(AddMinerCardVisibility.shouldShow(savedProfileCount = 1, mergedFleetCount = 1))
    }

    @Test
    fun localProfileNotYetInFleet_hidesCard() {
        // A saved local profile exists even before its first fleet entry.
        assertFalse(AddMinerCardVisibility.shouldShow(savedProfileCount = 1, mergedFleetCount = 0))
    }

    @Test
    fun cloudMinerOnly_hidesCard() {
        // Cloud-only viewer: no local profiles, but the fleet has a
        // remote machine — the card must stay hidden.
        assertFalse(AddMinerCardVisibility.shouldShow(savedProfileCount = 0, mergedFleetCount = 1))
    }

    @Test
    fun manyMiners_hidesCard() {
        assertFalse(AddMinerCardVisibility.shouldShow(savedProfileCount = 2, mergedFleetCount = 5))
    }
}
