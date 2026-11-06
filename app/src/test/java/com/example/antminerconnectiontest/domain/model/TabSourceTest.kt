package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The v0.5.1 tab-source rule: the Hardware / Pools / Logs tabs follow
 * the MACHINE, not the selection path. Callers resolve the selected
 * machine's merged fleet entry — reached via its cloud snapshot OR via
 * its saved local profile — and feed [TabSource.decide] that entry's
 * facts, so the cases below are stated per machine, not per tap path.
 * See [TabSource].
 */
class TabSourceTest {

    @Test
    fun profileSelected_snapshotExists_localOffline_isCloud() {
        // The bug: the machine was selected through its saved local
        // profile (selectedRemoteMiner == null), the phone is away
        // from its LAN so no live session is online — but the merged
        // entry carries a fresh cloud snapshot. Tabs show the snapshot.
        assertEquals(
            TabDataSource.CLOUD,
            TabSource.decide(liveSessionOnline = false, hasSnapshot = true)
        )
    }

    @Test
    fun profileSelected_liveSessionOnline_isLive() {
        // On the miner's own Wi-Fi with its live session up, the tabs
        // show the LIVE machine — a snapshot existing as well does not
        // demote it.
        assertEquals(
            TabDataSource.LIVE,
            TabSource.decide(liveSessionOnline = true, hasSnapshot = true)
        )
        assertEquals(
            TabDataSource.LIVE,
            TabSource.decide(liveSessionOnline = true, hasSnapshot = false)
        )
    }

    @Test
    fun snapshotSelected_isCloud() {
        // The pre-fix cloud behaviour, preserved: a machine selected
        // through its snapshot (no live session for it) shows cloud data.
        assertEquals(
            TabDataSource.CLOUD,
            TabSource.decide(liveSessionOnline = false, hasSnapshot = true)
        )
    }

    @Test
    fun noSnapshot_localOffline_isLocalPath() {
        // No snapshot to fall back to: the local path exactly as
        // before — connect attempt, error card + Retry when it fails.
        assertEquals(
            TabDataSource.LOCAL,
            TabSource.decide(liveSessionOnline = false, hasSnapshot = false)
        )
    }
}
