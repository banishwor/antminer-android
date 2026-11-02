package com.example.antminerconnectiontest.ui

/** Top-level destinations of the app shell. */
enum class MainRoute {
    /** Scan / manual add-miner setup flow, opened explicitly from inside the shell. */
    SETUP,

    /**
     * Relay-only status screen (v0.3.0): the whole app replaced by one
     * status screen for a phone left at a miner site. No bottom
     * navigation, no tabs, no other screens — the relay underneath
     * (background service, auto-connect, uploads) runs unchanged.
     */
    RELAY_ONLY,

    /**
     * The one and only main screen: Scaffold + bottom navigation
     * (Home / Hardware / Pools / Logs / Settings) with the single Home.
     */
    MAIN_SHELL
}

/**
 * Pure routing decision for the top level of the app, extracted so the
 * rules are unit-testable.
 *
 * v0.1.9 removed the start fork entirely: there is no first-open
 * "Remote View vs Start scan" question and no remote mode — every
 * install (fresh, returning, or migrated from a version that persisted
 * a start choice; the stored key is simply never read anymore) lands
 * directly in the main shell. The add-miner setup flow is entered only
 * by an explicit tap ("Add Miner" / "Add Unit" / a needs-password
 * card) and left via its back action or a completed connect, both of
 * which clear the flag. Because the flag is only ever set by those
 * explicit actions, letting it win over an active profile is safe: a
 * connected user adding another miner sees setup, and exits straight
 * back to their shell.
 *
 * v0.3.0 adds relay-only mode on top: when its persisted switch is on,
 * it wins over everything else — the phone shows only the relay
 * status screen. (Enabling it also closes the setup flow, so the two
 * flags can never fight; the precedence here is the belt-and-braces
 * rule for restored state.)
 */
object MainShellRouting {
    fun route(isLocalSetupOpen: Boolean, isRelayOnlyMode: Boolean = false): MainRoute =
        when {
            isRelayOnlyMode -> MainRoute.RELAY_ONLY
            isLocalSetupOpen -> MainRoute.SETUP
            else -> MainRoute.MAIN_SHELL
        }
}
