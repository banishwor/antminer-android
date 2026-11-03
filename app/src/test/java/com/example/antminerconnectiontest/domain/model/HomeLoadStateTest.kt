package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v0.3.3 Home loading rule: until the first REAL fleet state has
 * loaded, Home is in the LOADING content state — skeletons only, and
 * in particular NO "Add a miner" card, because the fleet merely
 * *looks* empty before data arrives. Loaded + genuinely empty fleet
 * is the only state that may show the card (the v0.2.3 rule,
 * unchanged). See [HomeLoadState].
 */
class HomeLoadStateTest {

    // ── The loaded signal ────────────────────────────────────────

    @Test
    fun notLoaded_untilPersistedStateEmitted() {
        assertFalse(
            HomeLoadState.isLoaded(
                isInitialStateLoaded = false,
                cloudConfigured = false,
                hasCompletedFirstCloudFetch = false
            )
        )
        assertFalse(
            HomeLoadState.isLoaded(
                isInitialStateLoaded = false,
                cloudConfigured = true,
                hasCompletedFirstCloudFetch = true
            )
        )
    }

    @Test
    fun loaded_persistedStateAndNoCloudConfigured() {
        // No cloud configured → there is no fetch to wait for.
        assertTrue(
            HomeLoadState.isLoaded(
                isInitialStateLoaded = true,
                cloudConfigured = false,
                hasCompletedFirstCloudFetch = false
            )
        )
    }

    @Test
    fun notLoaded_cloudConfiguredButFirstFetchStillPending() {
        // A cloud-only viewer's fleet has simply not arrived yet.
        assertFalse(
            HomeLoadState.isLoaded(
                isInitialStateLoaded = true,
                cloudConfigured = true,
                hasCompletedFirstCloudFetch = false
            )
        )
    }

    @Test
    fun loaded_cloudConfiguredAndFirstFetchCompleted() {
        assertTrue(
            HomeLoadState.isLoaded(
                isInitialStateLoaded = true,
                cloudConfigured = true,
                hasCompletedFirstCloudFetch = true
            )
        )
    }

    // ── The content decision ─────────────────────────────────────

    @Test
    fun loading_emptyFleet_isLoading_neverEmpty() {
        // (a) Loading + apparently empty fleet → LOADING (skeletons),
        // NOT EMPTY — so the Add-a-miner card cannot appear.
        assertEquals(
            HomeContent.LOADING,
            HomeLoadState.content(isLoaded = false, savedProfileCount = 0, mergedFleetCount = 0)
        )
    }

    @Test
    fun loading_withMiners_isStillLoading() {
        assertEquals(
            HomeContent.LOADING,
            HomeLoadState.content(isLoaded = false, savedProfileCount = 2, mergedFleetCount = 2)
        )
    }

    @Test
    fun loaded_genuinelyEmptyFleet_isEmpty_addCardShows() {
        // (b) Loaded + completely empty fleet → EMPTY: the card shows.
        assertEquals(
            HomeContent.EMPTY,
            HomeLoadState.content(isLoaded = true, savedProfileCount = 0, mergedFleetCount = 0)
        )
    }

    @Test
    fun loaded_cloudOnlyFleet_isFleet() {
        // (c) Loaded + cloud machines only → normal hero/rows.
        assertEquals(
            HomeContent.FLEET,
            HomeLoadState.content(isLoaded = true, savedProfileCount = 0, mergedFleetCount = 3)
        )
    }

    @Test
    fun loaded_localFleet_isFleet() {
        assertEquals(
            HomeContent.FLEET,
            HomeLoadState.content(isLoaded = true, savedProfileCount = 1, mergedFleetCount = 1)
        )
        // A saved profile counts even before its first fleet entry.
        assertEquals(
            HomeContent.FLEET,
            HomeLoadState.content(isLoaded = true, savedProfileCount = 1, mergedFleetCount = 0)
        )
    }
}
