package com.example.antminerconnectiontest.relay

import com.example.antminerconnectiontest.data.ConnectionResult
import com.example.antminerconnectiontest.data.ErrorType
import com.example.antminerconnectiontest.data.api.MinerReadException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.1.2 honest poll classification: a miner that answers but rejects
 * the login (HTTP 401) must never again be reported as "unreachable",
 * and the standby status line must be composed from the real counts.
 */
class MinerPollClassifierTest {

    // ---- failure classification ----

    @Test
    fun `authentication failure classifies as AUTH_FAILED`() {
        assertEquals(
            MinerPollState.AUTH_FAILED,
            MinerPollClassifier.stateForError(ErrorType.AUTHENTICATION_FAILED)
        )
    }

    @Test
    fun `timeout and unreachable classify as UNREACHABLE`() {
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForError(ErrorType.TIMEOUT)
        )
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForError(ErrorType.UNREACHABLE)
        )
    }

    @Test
    fun `other and missing errors classify as UNREACHABLE`() {
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForError(ErrorType.HTTP_ERROR)
        )
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForError(ErrorType.INVALID_IP)
        )
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForError(null)
        )
    }

    @Test
    fun `typed 401 exception from the read api classifies as AUTH_FAILED`() {
        val failure = ConnectionResult.Failure(
            errorType = ErrorType.AUTHENTICATION_FAILED,
            statusCode = 401,
            message = "Authentication failed (HTTP 401 Unauthorized)."
        )
        assertEquals(
            MinerPollState.AUTH_FAILED,
            MinerPollClassifier.stateForException(MinerReadException(failure))
        )
    }

    @Test
    fun `typed timeout exception classifies as UNREACHABLE`() {
        val failure = ConnectionResult.Failure(
            errorType = ErrorType.TIMEOUT,
            message = "Connection timed out."
        )
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForException(MinerReadException(failure))
        )
    }

    @Test
    fun `untyped or missing exception classifies as UNREACHABLE`() {
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForException(Exception("boom"))
        )
        assertEquals(
            MinerPollState.UNREACHABLE,
            MinerPollClassifier.stateForException(null)
        )
    }

    // ---- standby content composition ----

    @Test
    fun `standby content composes the real counts`() {
        assertEquals(
            "1 wrong password • 1 unreachable • Upload skipped",
            MinerPollClassifier.standbyContent(authFailed = 1, unreachable = 1, notHashing = 0)
        )
        assertEquals(
            "2 wrong password • 3 unreachable • 1 not hashing • Upload skipped",
            MinerPollClassifier.standbyContent(authFailed = 2, unreachable = 3, notHashing = 1)
        )
    }

    @Test
    fun `standby content with a single failure kind`() {
        assertEquals(
            "1 wrong password • Upload skipped",
            MinerPollClassifier.standbyContent(authFailed = 1, unreachable = 0, notHashing = 0)
        )
        assertEquals(
            "1 not hashing • Upload skipped",
            MinerPollClassifier.standbyContent(authFailed = 0, unreachable = 0, notHashing = 1)
        )
    }

    @Test
    fun `standby content with no failures keeps the generic line`() {
        assertEquals(
            "Local miners unreachable on current Wi-Fi • Upload skipped",
            MinerPollClassifier.standbyContent(authFailed = 0, unreachable = 0, notHashing = 0)
        )
    }

    // ---- extra note for the Local Only / Deferred lines ----

    @Test
    fun `extra note lists other failures`() {
        assertEquals(
            " • Also failing: 1 wrong password, 2 unreachable",
            MinerPollClassifier.extraNote(authFailed = 1, unreachable = 2, notHashing = 0)
        )
    }

    @Test
    fun `extra note is empty when nothing failed`() {
        assertEquals("", MinerPollClassifier.extraNote(authFailed = 0, unreachable = 0, notHashing = 0))
    }
}
