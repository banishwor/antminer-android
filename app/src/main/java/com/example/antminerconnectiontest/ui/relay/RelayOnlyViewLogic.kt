package com.example.antminerconnectiontest.ui.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the relay-only status screen may show, decided purely so the
 * rules are unit-testable without Compose (v0.3.3).
 *
 * The rule that matters: while the persisted state has not loaded
 * yet ([isLoading] — see AppUiState.isInitialStateLoaded), the
 * screen shows skeleton panels and NONE of the state-claiming texts.
 * The pre-load defaults (relay off, no miners, no upload yet) are
 * indistinguishable from real ones if rendered directly, so on a
 * cold start the screen used to announce "Relay is off", "No local
 * miners saved" and "Not yet" before any data had arrived. Once the
 * first real state has landed, every text behaves exactly as before:
 * a genuinely off relay shows its banner, a genuinely empty phone
 * shows the empty-miners text, and no upload yet shows "Not yet".
 */
object RelayOnlyViewLogic {

    /** The visibility decision for one frame of the relay-only screen. */
    data class ViewState(
        /** Show grey skeleton panels instead of the real cards. */
        val showSkeletonPanels: Boolean,
        /**
         * The status word for the header chip ("Relaying" / "Relay
         * off" / "Not set up"), or null while loading — the chip then
         * names the mode only and claims no state.
         */
        val statusWord: String?,
        /** "Cloud relay is not set up on this phone…" note. */
        val showNotConfiguredNote: Boolean,
        /** "Relay is off — this phone's miners are not being uploaded…" banner. */
        val showRelayOffBanner: Boolean,
        /** "No local miners saved on this phone yet…" empty text. */
        val showEmptyMinersText: Boolean,
        /**
         * The "Last successful upload" value, or null while loading
         * (a skeleton bar takes its place). Null is never rendered as
         * "Not yet" during loading — that text is a real state, shown
         * only once loaded with no upload on record.
         */
        val lastUploadText: String?
    )

    fun viewState(
        isLoading: Boolean,
        cloudConfigured: Boolean,
        relayEnabled: Boolean,
        minersEmpty: Boolean,
        lastUploadAtMs: Long?,
        nowMs: Long
    ): ViewState {
        if (isLoading) {
            return ViewState(
                showSkeletonPanels = true,
                statusWord = null,
                showNotConfiguredNote = false,
                showRelayOffBanner = false,
                showEmptyMinersText = false,
                lastUploadText = null
            )
        }
        return ViewState(
            showSkeletonPanels = false,
            statusWord = when {
                !cloudConfigured -> "Not set up"
                relayEnabled -> "Relaying"
                else -> "Relay off"
            },
            showNotConfiguredNote = !cloudConfigured,
            showRelayOffBanner = cloudConfigured && !relayEnabled,
            showEmptyMinersText = minersEmpty,
            lastUploadText = formatLastUpload(lastUploadAtMs, nowMs)
        )
    }

    /** "Just now · 11:42 AM" / "12 min ago · 11:30 AM" / "Not yet". */
    fun formatLastUpload(lastUploadAtMs: Long?, nowMs: Long): String {
        if (lastUploadAtMs == null) return "Not yet"
        val ageMin = ((nowMs - lastUploadAtMs) / 60_000L).coerceAtLeast(0L)
        val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastUploadAtMs))
        return when {
            ageMin < 1L -> "Just now · $clock"
            ageMin < 60L -> "$ageMin min ago · $clock"
            ageMin < 60L * 24L -> "${ageMin / 60L} h ago · $clock"
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(lastUploadAtMs))
        }
    }
}
