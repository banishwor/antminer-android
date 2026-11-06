package com.example.antminerconnectiontest.relay

import com.example.antminerconnectiontest.data.ErrorType
import com.example.antminerconnectiontest.data.api.MinerReadException

/**
 * What one relay poll of one saved miner actually found (v0.1.2).
 *
 * Until now the engine silently skipped a miner whose summary fetch
 * failed, whatever the reason, and the cycle's fallback status claimed
 * the miners were "unreachable on current Wi-Fi" — so a miner with a
 * wrong saved password (HTTP 401 from a machine that answered fine)
 * was indistinguishable from a powered-off miner, in the engine status
 * and on Relay Lite's screen. These states are the honest split:
 *
 *  - [CONNECTED] — summary read OK and the machine is hashing.
 *  - [AUTH_FAILED] — the miner answered but rejected the login (401).
 *  - [UNREACHABLE] — no usable answer: timeout, refused, no route, or
 *    any other read failure.
 *  - [NOT_HASHING] — summary read OK but the hashrate is 0 (booted, no
 *    pool, tuning, board fault — reachable, just not mining).
 */
enum class MinerPollState {
    CONNECTED,
    AUTH_FAILED,
    UNREACHABLE,
    NOT_HASHING
}

/** One miner's latest poll outcome, as reported to [RelayStorage]. */
data class MinerPollOutcome(
    val state: MinerPollState,
    /** 5-second hashrate in TH/s; only meaningful for CONNECTED. */
    val hashrateTh: Double = 0.0,
    val atMs: Long = System.currentTimeMillis()
)

/**
 * Pure classification + status-text composition for poll failures.
 * Kept free of Android and I/O so it is unit-testable on the JVM.
 */
object MinerPollClassifier {

    /** The poll state for a failed read, from the client's typed error. */
    fun stateForError(errorType: ErrorType?): MinerPollState = when (errorType) {
        ErrorType.AUTHENTICATION_FAILED -> MinerPollState.AUTH_FAILED
        else -> MinerPollState.UNREACHABLE
    }

    /**
     * The poll state for a failed read [Result]'s exception: a 401
     * survives the read API as [MinerReadException]; anything else
     * (including an untyped failure) counts as unreachable.
     */
    fun stateForException(error: Throwable?): MinerPollState =
        stateForError((error as? MinerReadException)?.errorType)

    /** "1 wrong password", "2 unreachable", "1 not hashing" — non-zero counts only. */
    fun failureParts(authFailed: Int, unreachable: Int, notHashing: Int): List<String> =
        buildList {
            if (authFailed > 0) add("$authFailed wrong password")
            if (unreachable > 0) add("$unreachable unreachable")
            if (notHashing > 0) add("$notHashing not hashing")
        }

    /**
     * The standby cycle content when nothing could be uploaded: the
     * real failure counts, e.g. "1 wrong password • 1 unreachable •
     * Upload skipped". With no failures at all (e.g. no miners saved)
     * the historical generic line is kept.
     */
    fun standbyContent(authFailed: Int, unreachable: Int, notHashing: Int): String {
        val parts = failureParts(authFailed, unreachable, notHashing)
        return if (parts.isEmpty()) {
            "Local miners unreachable on current Wi-Fi • Upload skipped"
        } else {
            parts.joinToString(" • ") + " • Upload skipped"
        }
    }

    /**
     * A note appended to the Local Only / Upload Deferred status lines
     * when OTHER miners also failed this cycle, so those lines stay
     * truthful about the whole fleet. Empty when nothing failed.
     */
    fun extraNote(authFailed: Int, unreachable: Int, notHashing: Int): String {
        val parts = failureParts(authFailed, unreachable, notHashing)
        return if (parts.isEmpty()) {
            ""
        } else {
            " • Also failing: " + parts.joinToString(", ")
        }
    }
}
