package com.example.antminerconnectiontest.ui

/** Top-level destinations of the app shell. */
enum class MainRoute {
    /** First open, nothing saved, no choice made yet — ask once. */
    START_CHOICE,

    /** Scan / add-miner setup flow. */
    SETUP,

    /**
     * The main Scaffold with the bottom navigation. Reached as soon as
     * a start choice exists — including a Remote View starter with no
     * local miner connected (v0.1.8: remote users get all five tabs;
     * before, they were stranded on a bare standalone remote screen
     * with no Home and no menus until a local miner was connected).
     */
    MAIN_SHELL
}

/**
 * Pure routing decision for the top level of the app, extracted so the
 * rules are unit-testable. Order matters:
 *
 * 1. No saved profiles and no start choice → ask the first-open
 *    question. Nothing scans or loads before the answer.
 * 2. An active local profile always lands in the main shell (the
 *    behaviour every connected user has had all along).
 * 3. The local setup flow wins over the shell for a user with no
 *    active profile — a Remote starter who tapped "Add Local Miner"
 *    is inside setup until they finish or exit it.
 * 4. A Remote starter (choice made, no local miner) lands in the main
 *    shell in remote mode — fleet on Home, honest placeholders on the
 *    other tabs, cloud settings on Device.
 * 5. Everyone else with no active profile (Scan starter who hasn't
 *    connected yet, legacy installs with profiles but no recorded
 *    choice) goes to setup, exactly as before v0.1.8.
 */
object MainShellRouting {
    fun route(
        hasSavedProfiles: Boolean,
        startChoice: StartChoice?,
        hasActiveProfile: Boolean,
        isLocalSetupOpen: Boolean
    ): MainRoute = when {
        !hasSavedProfiles && startChoice == null -> MainRoute.START_CHOICE
        hasActiveProfile -> MainRoute.MAIN_SHELL
        isLocalSetupOpen -> MainRoute.SETUP
        startChoice == StartChoice.REMOTE -> MainRoute.MAIN_SHELL
        else -> MainRoute.SETUP
    }
}
