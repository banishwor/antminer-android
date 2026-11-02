package com.example.antminerconnectiontest.ui

/** Top-level destinations of the app shell. */
enum class MainRoute {
    /** Scan / manual add-miner setup flow, opened explicitly from inside the shell. */
    SETUP,

    /**
     * The one and only main screen: Scaffold + bottom navigation
     * (Home / Hardware / Pools / Logs / Device) with the single Home.
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
 * directly in the main shell. The add-miner setup flow is the only
 * other destination, and it is entered only by an explicit tap
 * ("Add Miner" / "Add Unit" / a needs-password card) and left via its
 * back action or a completed connect, both of which clear the flag.
 * Because the flag is only ever set by those explicit actions, letting
 * it win over an active profile is safe: a connected user adding
 * another miner sees setup, and exits straight back to their shell.
 */
object MainShellRouting {
    fun route(isLocalSetupOpen: Boolean): MainRoute =
        if (isLocalSetupOpen) MainRoute.SETUP else MainRoute.MAIN_SHELL
}
