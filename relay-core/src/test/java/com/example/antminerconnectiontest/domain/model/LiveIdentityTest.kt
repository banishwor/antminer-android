package com.example.antminerconnectiontest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-identity proof (v0.5.3): a live reading may be attributed to a
 * profile only when the answering miner's own pool worker matches the
 * profile's expected worker. The cross-site case throughout: the saved
 * Sabaltongba profile banishwor.001 points at 192.168.1.2, but at
 * Bishambhor's site the machine answering at 192.168.1.2 is
 * bishambhor.001.
 */
class LiveIdentityTest {

    private fun pool(user: String, index: Int = 0) = MinerPool(
        index = index,
        url = "stratum+tcp://pool.example:443",
        user = user,
        status = "Alive",
        priority = index,
        acceptedShares = 100,
        rejectedShares = 0
    )

    @Test
    fun poolWorker_firstNonBlankUserWins() {
        assertEquals(
            "banishwor.001",
            LiveIdentity.poolWorker(listOf(pool(""), pool("  banishwor.001 "), pool("other.001", 2)))
        )
        assertNull(LiveIdentity.poolWorker(emptyList()))
        assertNull(LiveIdentity.poolWorker(listOf(pool(""), pool("   "))))
    }

    @Test
    fun decide_matchingWorker_isVerified_caseAndWhitespaceInsensitive() {
        val verdict = LiveIdentity.decide(" Banishwor.001 ", listOf(pool("banishwor.001")))
        assertEquals(LiveIdentity.Decision.VERIFIED, verdict.decision)
        assertTrue(verdict.isProven)
        assertEquals("banishwor.001", verdict.actualWorker)
    }

    @Test
    fun decide_differentKnownWorker_isMismatch_banishworVsBishambhor() {
        // The miner at 192.168.1.2 proves it is bishambhor.001 while the
        // profile expects banishwor.001: the reading belongs to another
        // machine and must never be attributed to this profile.
        val verdict = LiveIdentity.decide("banishwor.001", listOf(pool("bishambhor.001")))
        assertEquals(LiveIdentity.Decision.MISMATCH, verdict.decision)
        assertFalse(verdict.isProven)
        assertEquals("bishambhor.001", verdict.actualWorker)
    }

    @Test
    fun decide_knownWorkerButPoolsUnreadable_isUnverified() {
        val verdict = LiveIdentity.decide("banishwor.001", emptyList())
        assertEquals(LiveIdentity.Decision.UNVERIFIED, verdict.decision)
        assertFalse(verdict.isProven)
        assertNull(verdict.actualWorker)
    }

    @Test
    fun decide_blankExpectedWorker_isLearn_andCarriesTheProvenWorker() {
        val verdict = LiveIdentity.decide("", listOf(pool("bishambhor.001")))
        assertEquals(LiveIdentity.Decision.LEARN, verdict.decision)
        assertTrue(verdict.isProven)
        assertEquals("bishambhor.001", verdict.actualWorker)

        // Nothing readable yet: still the learn case, nothing to learn.
        val empty = LiveIdentity.decide(null, emptyList())
        assertEquals(LiveIdentity.Decision.LEARN, empty.decision)
        assertNull(empty.actualWorker)
    }
}
