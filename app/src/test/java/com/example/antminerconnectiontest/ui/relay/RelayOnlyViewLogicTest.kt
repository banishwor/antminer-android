package com.example.antminerconnectiontest.ui.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v0.3.3 relay-only loading rule: while the persisted state has
 * not loaded yet, the screen shows skeleton panels and NONE of the
 * state-claiming texts (relay-off banner, "No local miners saved…",
 * "Not yet"). Once loaded, the genuine states render exactly as
 * before. See [RelayOnlyViewLogic].
 */
class RelayOnlyViewLogicTest {

    private val now = 1_730_000_000_000L // fixed "now" for determinism

    @Test
    fun loading_showsSkeletons_andHidesEveryStateClaim() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = true,
            cloudConfigured = true,
            relayEnabled = false,
            minersEmpty = true,
            lastUploadAtMs = null,
            nowMs = now
        )
        assertTrue(visible.showSkeletonPanels)
        assertFalse(visible.showRelayOffBanner)
        assertFalse(visible.showNotConfiguredNote)
        assertFalse(visible.showEmptyMinersText)
        // No "Not yet": the upload value is a skeleton while loading.
        assertNull(visible.lastUploadText)
        // The chip names the mode only — it does not claim "Relay off".
        assertNull(visible.statusWord)
    }

    @Test
    fun loading_evenWithRealLookingValues_stillSkeletons() {
        // Loading is decided by the loaded signal alone, never by
        // whether the (pre-load default) values happen to look real.
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = true,
            cloudConfigured = true,
            relayEnabled = true,
            minersEmpty = false,
            lastUploadAtMs = now - 60_000L,
            nowMs = now
        )
        assertTrue(visible.showSkeletonPanels)
        assertNull(visible.statusWord)
        assertNull(visible.lastUploadText)
        assertFalse(visible.showRelayOffBanner)
        assertFalse(visible.showEmptyMinersText)
    }

    @Test
    fun loaded_relayOff_showsBanner() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = true,
            relayEnabled = false,
            minersEmpty = false,
            lastUploadAtMs = now - 5 * 60_000L,
            nowMs = now
        )
        assertFalse(visible.showSkeletonPanels)
        assertTrue(visible.showRelayOffBanner)
        assertFalse(visible.showNotConfiguredNote)
        assertEquals("Relay off", visible.statusWord)
    }

    @Test
    fun loaded_notConfigured_showsSetupNoteNotRelayOffBanner() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = false,
            relayEnabled = false,
            minersEmpty = true,
            lastUploadAtMs = null,
            nowMs = now
        )
        assertFalse(visible.showSkeletonPanels)
        assertTrue(visible.showNotConfiguredNote)
        assertFalse(visible.showRelayOffBanner)
        assertEquals("Not set up", visible.statusWord)
    }

    @Test
    fun loaded_noMiners_showsEmptyMinersText() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = true,
            relayEnabled = true,
            minersEmpty = true,
            lastUploadAtMs = null,
            nowMs = now
        )
        assertTrue(visible.showEmptyMinersText)
        assertFalse(visible.showRelayOffBanner)
    }

    @Test
    fun loaded_withMiners_hidesEmptyMinersText() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = true,
            relayEnabled = true,
            minersEmpty = false,
            lastUploadAtMs = now - 60_000L,
            nowMs = now
        )
        assertFalse(visible.showEmptyMinersText)
    }

    @Test
    fun loaded_relaying_statusWordAndRealValues() {
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = true,
            relayEnabled = true,
            minersEmpty = false,
            lastUploadAtMs = now - 30_000L,
            nowMs = now
        )
        assertFalse(visible.showSkeletonPanels)
        assertEquals("Relaying", visible.statusWord)
        assertFalse(visible.showRelayOffBanner)
        assertTrue(visible.lastUploadText!!.startsWith("Just now"))
    }

    @Test
    fun loaded_noUploadYet_showsNotYet() {
        // "Not yet" is a REAL state — it appears once loaded with no
        // upload on record (and only then; see the loading tests).
        val visible = RelayOnlyViewLogic.viewState(
            isLoading = false,
            cloudConfigured = true,
            relayEnabled = true,
            minersEmpty = false,
            lastUploadAtMs = null,
            nowMs = now
        )
        assertEquals("Not yet", visible.lastUploadText)
    }

    @Test
    fun formatLastUpload_relativeLabels() {
        assertEquals("Not yet", RelayOnlyViewLogic.formatLastUpload(null, now))
        assertTrue(
            RelayOnlyViewLogic.formatLastUpload(now - 30_000L, now).startsWith("Just now")
        )
        assertTrue(
            RelayOnlyViewLogic.formatLastUpload(now - 12 * 60_000L, now).startsWith("12 min ago")
        )
        assertTrue(
            RelayOnlyViewLogic.formatLastUpload(now - 2 * 3_600_000L, now).startsWith("2 h ago")
        )
        val old = RelayOnlyViewLogic.formatLastUpload(now - 3 * 24 * 3_600_000L, now)
        assertFalse(old.contains("ago"))
        assertFalse(old == "Not yet")
    }
}
