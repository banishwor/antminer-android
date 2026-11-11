package com.example.antminerconnectiontest.domain.model

/**
 * Identity proof for LIVE readings (v0.5.3).
 *
 * A private IP address is only an address hint: the same address
 * (192.168.1.2) routinely names DIFFERENT physical miners at different
 * sites. A live response may therefore be attributed to a saved profile
 * only when the miner that actually answered proves — through its own
 * pool configuration — that it IS the profile's machine. The proof is
 * the pool worker username, the same identity the cloud sheet uses
 * (see [MinerMerge]).
 *
 * Every live path applies this one decision — the dashboard refresh
 * (MinerRepository), the fleet race poller (MultiMinerPoller) and the
 * relay cycle (RelayEngine) — so no path can publish, display or upload
 * one machine's telemetry under another machine's identity.
 */
object LiveIdentity {

    enum class Decision {
        /** The answering miner's pool worker matches the expected identity. */
        VERIFIED,

        /** The answering miner proved a DIFFERENT worker identity. */
        MISMATCH,

        /**
         * An identity was expected but the answering miner's pool worker
         * could not be read (pools fetch failed or carried no user), so
         * ownership is unproven — the reading must not be trusted.
         */
        UNVERIFIED,

        /**
         * No identity was expected (the profile has not learned its
         * worker name yet): the answering miner's worker, when readable,
         * is the identity to learn.
         */
        LEARN
    }

    data class Verdict(
        val decision: Decision,
        /** The worker the answering miner proved, case-preserved; null when unreadable. */
        val actualWorker: String?
    ) {
        /** True when a reading may be attributed to the expected identity. */
        val isProven: Boolean
            get() = decision == Decision.VERIFIED || decision == Decision.LEARN
    }

    /**
     * The worker identity a pools response proves: the first non-blank
     * pool user, trimmed, or null when the response carries none.
     */
    fun poolWorker(pools: List<MinerPool>): String? =
        pools.firstOrNull { it.user.isNotBlank() }?.user?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Decides whether a live response fetched for a profile expecting
     * [expectedWorker] (blank = not learned yet) may be attributed to
     * that profile, given the pools the SAME endpoint returned.
     */
    fun decide(expectedWorker: String?, pools: List<MinerPool>): Verdict {
        val actual = poolWorker(pools)
        val expected = MinerMerge.normalizeWorkerName(expectedWorker)
        if (expected.isBlank()) return Verdict(Decision.LEARN, actual)
        if (actual == null) return Verdict(Decision.UNVERIFIED, null)
        return if (MinerMerge.normalizeWorkerName(actual) == expected) {
            Verdict(Decision.VERIFIED, actual)
        } else {
            Verdict(Decision.MISMATCH, actual)
        }
    }
}
