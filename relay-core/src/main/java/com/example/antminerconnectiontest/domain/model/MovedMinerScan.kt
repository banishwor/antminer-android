package com.example.antminerconnectiontest.domain.model

/**
 * "Find moved miners" (v0.5.4): the pure matching rules behind the
 * user-tapped DHCP-recovery scan.
 *
 * A known miner's IP can drift (DHCP) until every address the phone
 * knows for it is dead. The recovery scan — started ONLY by an explicit
 * button tap in the app, never automatically — probes the current
 * Wi-Fi subnet and verifies each responding Antminer. This file decides
 * what a verified response means; it performs no I/O.
 *
 * The identity rule is the app's standing one: a machine IS its pool
 * worker name (normalised). An IP is only an address:
 * - a verified worker matching a saved profile moves THAT profile to
 *   the found address (or is a no-op when the address is unchanged);
 * - a verified worker matching a fleet-only row adopts that machine
 *   locally, exactly like auto-connect;
 * - a verified worker matching NOTHING known is ignored — the scan
 *   never adds unknown machines;
 * - a responder whose worker cannot be read proves nothing and is
 *   ignored for matching.
 */
object MovedMinerScan {

    /** A known machine the scan is looking for. */
    data class Target(
        /** Normalised worker identity. */
        val identity: String,
        /** Case-preserved worker identity for display. */
        val identityRaw: String,
        /** The address currently known for this machine ("" when none). */
        val storedIp: String,
        /** The saved local profile, when this phone has one. */
        val profile: MinerProfile? = null,
        /** The cloud fleet row, when the machine is in the sheet. */
        val snapshot: RemoteMinerSnapshot? = null
    )

    /** A miner that answered on the scanned subnet and authenticated. */
    data class VerifiedResponder(
        val ip: String,
        /** Worker name read from its pools; null when it could not be read. */
        val workerName: String?
    )

    /** What to do with one verified responder. */
    sealed interface Decision {
        /** Saved profile proved at a NEW address: move the profile there. */
        data class UpdateProfile(val target: Target, val newIp: String) : Decision

        /** Fleet-only machine proved here: adopt it like auto-connect does. */
        data class AdoptFleet(val target: Target, val ip: String) : Decision

        /** Saved profile proved at the address already stored: no write. */
        data class AlreadyCorrect(val target: Target) : Decision

        /** A real miner, but not one this phone knows: never saved. */
        data class IgnoredUnknown(val workerName: String) : Decision

        /** Authenticated, but its worker name could not be read: proves nothing. */
        data object IgnoredUnverified : Decision
    }

    /**
     * The scan's target set: every known worker identity from saved
     * profiles and fleet rows, minus the identities in [liveIdentities]
     * (already verified live at their stored address — nothing to find).
     * Profiles win over fleet rows for the same identity (they carry the
     * credentials and the user's nickname/location). Machines with no
     * provable worker identity can never be targets: there is nothing a
     * scan response could be matched against.
     */
    fun buildTargets(
        profiles: List<MinerProfile>,
        snapshots: List<RemoteMinerSnapshot>,
        liveIdentities: Set<String> = emptySet()
    ): List<Target> {
        val live = liveIdentities.map { it.trim().lowercase() }.toSet()
        val byIdentity = LinkedHashMap<String, Target>()

        for (profile in profiles) {
            val identity = MinerMerge.normalizeWorkerName(profile.workerName)
            if (identity.isBlank() || identity in live) continue
            if (identity !in byIdentity) {
                byIdentity[identity] = Target(
                    identity = identity,
                    identityRaw = profile.workerName.trim(),
                    storedIp = profile.host.trim(),
                    profile = profile
                )
            }
        }
        for (snapshot in MinerMerge.dedupeSnapshots(snapshots)) {
            val identity = MinerMerge.workerIdentity(snapshot) ?: continue
            if (identity in live) continue
            val existing = byIdentity[identity]
            if (existing == null) {
                byIdentity[identity] = Target(
                    identity = identity,
                    identityRaw = RelayUpload.sheetIdForWorkerName(snapshot.id)
                        ?: snapshot.nickname.trim().substringBefore(" (").trim(),
                    storedIp = snapshot.ip.trim(),
                    snapshot = snapshot
                )
            } else if (existing.snapshot == null) {
                byIdentity[identity] = existing.copy(snapshot = snapshot)
            }
        }
        return byIdentity.values.toList()
    }

    /** Decides the fate of one verified responder against the target set. */
    fun decide(targets: List<Target>, responder: VerifiedResponder): Decision {
        val worker = MinerMerge.normalizeWorkerName(responder.workerName)
        if (worker.isBlank()) return Decision.IgnoredUnverified

        val target = targets.firstOrNull { it.identity == worker }
            ?: return Decision.IgnoredUnknown(worker)

        val profile = target.profile
        return when {
            profile == null -> Decision.AdoptFleet(target, responder.ip.trim())
            responder.ip.trim() == profile.host.trim() -> Decision.AlreadyCorrect(target)
            else -> Decision.UpdateProfile(target, responder.ip.trim())
        }
    }
}
