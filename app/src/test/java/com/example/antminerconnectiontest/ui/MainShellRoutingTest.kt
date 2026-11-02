package com.example.antminerconnectiontest.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The top-level routing rules (v0.1.8). The regression being pinned:
 * a Remote View starter with no local miner connected used to be
 * routed to a bare standalone remote screen with no bottom navigation
 * — they must land in the main shell like everyone else.
 */
class MainShellRoutingTest {

    @Test
    fun freshInstallAsksStartChoice() {
        assertEquals(
            MainRoute.START_CHOICE,
            MainShellRouting.route(
                hasSavedProfiles = false,
                startChoice = null,
                hasActiveProfile = false,
                isLocalSetupOpen = false
            )
        )
    }

    @Test
    fun remoteStarterWithoutLocalMinerGetsMainShell() {
        assertEquals(
            MainRoute.MAIN_SHELL,
            MainShellRouting.route(
                hasSavedProfiles = false,
                startChoice = StartChoice.REMOTE,
                hasActiveProfile = false,
                isLocalSetupOpen = false
            )
        )
    }

    @Test
    fun remoteStarterWithSavedProfilesButNoActiveMinerGetsMainShell() {
        assertEquals(
            MainRoute.MAIN_SHELL,
            MainShellRouting.route(
                hasSavedProfiles = true,
                startChoice = StartChoice.REMOTE,
                hasActiveProfile = false,
                isLocalSetupOpen = false
            )
        )
    }

    @Test
    fun remoteStarterAddingLocalMinerGoesToSetup() {
        assertEquals(
            MainRoute.SETUP,
            MainShellRouting.route(
                hasSavedProfiles = false,
                startChoice = StartChoice.REMOTE,
                hasActiveProfile = false,
                isLocalSetupOpen = true
            )
        )
    }

    @Test
    fun activeProfileAlwaysGetsMainShell() {
        // Even if a stale local-setup flag were set, a connected user
        // keeps the shell — the pre-v0.1.8 precedence.
        assertEquals(
            MainRoute.MAIN_SHELL,
            MainShellRouting.route(
                hasSavedProfiles = true,
                startChoice = StartChoice.SCAN,
                hasActiveProfile = true,
                isLocalSetupOpen = true
            )
        )
        assertEquals(
            MainRoute.MAIN_SHELL,
            MainShellRouting.route(
                hasSavedProfiles = true,
                startChoice = StartChoice.REMOTE,
                hasActiveProfile = true,
                isLocalSetupOpen = false
            )
        )
    }

    @Test
    fun scanStarterWithoutMinerStillGoesToSetup() {
        assertEquals(
            MainRoute.SETUP,
            MainShellRouting.route(
                hasSavedProfiles = false,
                startChoice = StartChoice.SCAN,
                hasActiveProfile = false,
                isLocalSetupOpen = false
            )
        )
    }

    @Test
    fun legacyProfilesWithoutChoiceOrActiveMinerGoToSetup() {
        assertEquals(
            MainRoute.SETUP,
            MainShellRouting.route(
                hasSavedProfiles = true,
                startChoice = null,
                hasActiveProfile = false,
                isLocalSetupOpen = false
            )
        )
    }
}
