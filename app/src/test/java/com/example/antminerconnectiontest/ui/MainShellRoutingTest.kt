package com.example.antminerconnectiontest.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The top-level routing rules (v0.1.9). The regression being pinned:
 * earlier versions forked on a persisted first-open choice — Remote
 * starters were stranded on a separate remote home with different UI.
 * There is now exactly one shell for everyone; the only other
 * destination is the explicitly-opened add-miner setup flow.
 */
class MainShellRoutingTest {

    @Test
    fun freshInstall_landsDirectlyInMainShell() {
        // No saved profiles, no cloud setup, no choice ever made: the
        // shell (with its in-Home setup content) is the first screen.
        assertEquals(MainRoute.MAIN_SHELL, MainShellRouting.route(isLocalSetupOpen = false))
    }

    @Test
    fun migratedRemoteOrScanStarter_landsInMainShell() {
        // A persisted start choice from an older version is never read
        // anymore, so routing cannot depend on it: same shell.
        assertEquals(MainRoute.MAIN_SHELL, MainShellRouting.route(isLocalSetupOpen = false))
    }

    @Test
    fun addMinerFlowOpen_goesToSetup() {
        assertEquals(MainRoute.SETUP, MainShellRouting.route(isLocalSetupOpen = true))
    }

    @Test
    fun setupClosed_returnsToMainShell() {
        // The flag is the whole decision: closed setup always means shell.
        assertEquals(MainRoute.MAIN_SHELL, MainShellRouting.route(isLocalSetupOpen = false))
    }
}
