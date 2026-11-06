package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The v0.5.2 hero-source rule: the Home hero follows the MACHINE, not
 * the selection path — the Home twin of the v0.5.1 tab fix. Callers
 * resolve the selected machine's merged fleet entry and feed
 * [HeroDetailChoice.choose] that entry's facts, so the cases below are
 * stated per machine, not per tap path. See [HeroDetailChoice].
 */
class HeroDetailChoiceTest {

    @Test
    fun coldOpen_profileSelected_snapshotPresent_noLiveSession_isProfileSnapshot() {
        // THE bug (user screenshot, v0.5.1, 19:00 IST, on 5G): the app
        // cold-opens with banishwor.002 restored as the active saved
        // profile (no explicit snapshot selection), no live session —
        // the phone is on mobile data — but the merged fleet entry
        // already carries the machine's fresh cloud snapshot
        // (123.5 TH/s, "Updated just now"). The hero must show the
        // snapshot (REMOTE), not the blank local path.
        assertEquals(
            HeroDetailKind.PROFILE_SNAPSHOT,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = true,
                profileLiveOnline = false,
                profileEntryHasSnapshot = true,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun profileSelected_liveSessionOnline_isProfileLocal() {
        // On the miner's own Wi-Fi with its live session up, live data
        // wins for the hero — an existing snapshot does not demote it.
        assertEquals(
            HeroDetailKind.PROFILE_LOCAL,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = true,
                profileLiveOnline = true,
                profileEntryHasSnapshot = true,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun profileSelected_noSnapshot_isProfileLocal() {
        // No snapshot to fall back to: the local path exactly as
        // before — connect attempt, and the "Could not connect" error
        // card when it fails and there is no data at all.
        assertEquals(
            HeroDetailKind.PROFILE_LOCAL,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = true,
                profileLiveOnline = false,
                profileEntryHasSnapshot = false,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun explicitSnapshot_notSuperseded_isExplicitSnapshot() {
        // The pre-fix snapshot behaviour, preserved (v0.2.1 re-derives
        // this selection from the merged fleet as it refreshes).
        assertEquals(
            HeroDetailKind.EXPLICIT_SNAPSHOT,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = true,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = true,
                profileLiveOnline = false,
                profileEntryHasSnapshot = true,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun explicitSnapshot_supersededByLiveLocal_isProfileLocal() {
        // The designed precedence (v0.1.9): the selected snapshot is
        // the SAME machine as the active profile and live local data
        // for it has arrived — the fresher local data wins the hero.
        assertEquals(
            HeroDetailKind.PROFILE_LOCAL,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = true,
                explicitSnapshotSupersededByLiveLocal = true,
                hasActiveProfile = true,
                profileLiveOnline = true,
                profileEntryHasSnapshot = true,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun noSelection_defaultRowSnapshot_isDefaultSnapshot() {
        assertEquals(
            HeroDetailKind.DEFAULT_SNAPSHOT,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = false,
                profileLiveOnline = false,
                profileEntryHasSnapshot = false,
                defaultEntryHasSnapshot = true,
                defaultEntryHasProfile = false
            )
        )
    }

    @Test
    fun noSelection_defaultRowLocalOnly_isDefaultLocal() {
        assertEquals(
            HeroDetailKind.DEFAULT_LOCAL,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = false,
                profileLiveOnline = false,
                profileEntryHasSnapshot = false,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = true
            )
        )
    }

    @Test
    fun emptyFleet_isNone() {
        assertEquals(
            HeroDetailKind.NONE,
            HeroDetailChoice.choose(
                hasExplicitSnapshot = false,
                explicitSnapshotSupersededByLiveLocal = false,
                hasActiveProfile = false,
                profileLiveOnline = false,
                profileEntryHasSnapshot = false,
                defaultEntryHasSnapshot = false,
                defaultEntryHasProfile = false
            )
        )
    }
}
