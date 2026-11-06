package com.example.antminerconnectiontest.domain.model

/** Where the Hardware / Pools / Logs tabs get the selected machine's data. */
enum class TabDataSource {
    /** A live local session for the machine is online — tabs show live data. */
    LIVE,

    /** No live session, but a cloud relay snapshot exists — tabs show the snapshot. */
    CLOUD,

    /**
     * No live session and no snapshot — tabs take the local path exactly
     * as before (connect attempt, and the "Couldn't reach this miner"
     * error + Retry when that fails and there is no data at all).
     */
    LOCAL
}

/**
 * The tab-source decision (v0.5.1, user bug fix): the tabs follow the
 * MACHINE, never the way it was selected.
 *
 * Before this fix the tabs chose cloud content only when the machine
 * had been selected through its cloud snapshot
 * (`AppUiState.selectedRemoteMiner != null`). Selecting the same
 * machine through its saved local profile kept the tabs on the local
 * path, so away from the miner's LAN (mobile data, or any other Wi-Fi)
 * the user got a connect error — even though a fresh cloud snapshot
 * for the machine was already in the merged fleet.
 *
 * The rule, resolved per machine (callers first resolve the ONE merged
 * fleet entry for the machine the tabs are about, whichever selection
 * path led there, and feed in that entry's facts):
 * - a live local session for it is online → [TabDataSource.LIVE];
 * - otherwise, if a cloud snapshot exists for it → [TabDataSource.CLOUD];
 * - otherwise → [TabDataSource.LOCAL] (the pre-fix local behaviour).
 *
 * Pure, so the rule is unit-testable without Compose.
 */
object TabSource {

    fun decide(liveSessionOnline: Boolean, hasSnapshot: Boolean): TabDataSource = when {
        liveSessionOnline -> TabDataSource.LIVE
        hasSnapshot -> TabDataSource.CLOUD
        else -> TabDataSource.LOCAL
    }
}
