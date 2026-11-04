package com.example.antminerconnectiontest.domain.model

/**
 * Auto-connect of KNOWN miners (v0.1.9).
 *
 * When the phone joins a Wi-Fi network, the app tries to connect to the
 * miners it already knows — never a subnet scan. "Known" is exactly two
 * sources:
 *
 *  (a) saved profiles on this phone, and
 *  (b) machines in the cloud fleet fetched from the sheet — each row
 *      carries an IP and a worker identity, and the row's user is its
 *      sheet owner.
 *
 * A candidate is probed at its known IP with the saved profile's
 * credentials when one exists for that machine, otherwise the Antminer
 * default (root/root). The worker identity read back from the miner's
 * own pool config is VERIFIED against the candidate's known identity
 * before anything is saved: a matching machine becomes a normal local
 * miner; a different KNOWN fleet machine answering at that IP is claimed
 * instead; an unknown machine is never added (the known-only rule); a
 * machine that answers but rejects both credential sets is reported as
 * "needs password" so the user can type it once.
 *
 * Everything in this file is pure and unit-tested; the network probing
 * lives in the ViewModel (foreground) and the relay service
 * (background), which share these decisions so both paths behave
 * identically.
 */
data class AutoConnectCandidate(
    /** Normalised worker identity (trim + lowercase); "" when unknown (a saved profile that never learned its name). */
    val identity: String,
    /** Case-preserved worker identity for profile/sheet use; "" when unknown. */
    val identityRaw: String,
    /** Primary IP to probe: the saved profile's host wins, else the sheet row's IP. */
    val ip: String,
    /** Secondary IP to try when the primary fails (the other source's address — DHCP drift). */
    val alternateIp: String? = null,
    /** The saved profile for this machine, when one exists on this phone. */
    val savedProfile: MinerProfile? = null,
    /** The cloud fleet row for this machine, when it is in the sheet. */
    val snapshot: RemoteMinerSnapshot? = null,
    /** Sheet owner (the relay user whose row this is); "" for profile-only candidates. */
    val sheetOwner: String = ""
) {
    /** IPs to probe, in order, blanks and duplicates removed. */
    val probeIps: List<String>
        get() = listOfNotNull(ip, alternateIp).map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

/** Outcome of probing one candidate at one IP. */
enum class ProbeClass {
    /** The machine at the IP is the candidate's machine (or a saved profile whose identity is adopted from it). */
    MATCH,

    /** A DIFFERENT machine from the known fleet answered — claim that machine at this IP instead. */
    DIFFERENT_KNOWN_MACHINE,

    /** Something answered but it is not any known machine — never added (known-only rule). */
    UNKNOWN_MACHINE,

    /** A miner answered but rejected the saved and the default credentials — the user must type the password once. */
    NEEDS_PASSWORD,

    /** Nothing answered at the IP. */
    UNREACHABLE,

    /** A miner answered and authenticated, but its worker identity could not be read, so it cannot be verified — not added. */
    UNVERIFIED
}

/**
 * A known machine that is on this Wi-Fi but locked: it answered the
 * auto-connect probe and rejected every credential the phone has for it.
 * Surfaced on Home as a tappable card that opens the credential entry
 * prefilled with its IP; once the user connects it once, the saved
 * profile's credentials drive auto-connect from then on.
 */
data class NeedsPasswordMachine(
    /** Normalised worker identity when known (fleet machines), else "". */
    val identity: String,
    val displayName: String,
    val ip: String,
    /** Sheet owner for fleet machines; "" for a saved profile with a stale password. */
    val ownerUser: String = ""
) {
    /** Stable key for list state: identity when known, else the IP. */
    val key: String get() = identity.ifBlank { "ip:$ip" }
}

object AutoConnect {

    const val DEFAULT_USERNAME = "root"
    const val DEFAULT_PASSWORD = "root"

    /**
     * Builds the candidate set from saved profiles and the cloud fleet.
     *
     * Profiles and fleet rows describing the same machine (same worker
     * identity) collapse into ONE candidate carrying both sources — the
     * profile supplies credentials, the row supplies a fallback IP and
     * the sheet owner. Rows whose worker identity cannot be proven are
     * skipped: with no identity there is nothing to verify a probe
     * against, and the known-only rule forbids adding unverifiable
     * machines. The result is deduplicated by worker identity first,
     * then by IP (profile-backed candidates win an IP — they carry
     * credentials; a fleet machine displaced by an IP collision is still
     * reachable through classification, see
     * [ProbeClass.DIFFERENT_KNOWN_MACHINE]).
     *
     * Order is deterministic: profile-backed candidates in saved order,
     * then fleet-only candidates in sheet order.
     */
    fun buildCandidates(
        profiles: List<MinerProfile>,
        snapshots: List<RemoteMinerSnapshot>
    ): List<AutoConnectCandidate> {
        val fleet = MinerMerge.dedupeSnapshots(snapshots)

        // Fleet rows with a provable identity, keyed by it.
        val fleetByIdentity = LinkedHashMap<String, RemoteMinerSnapshot>()
        for (row in fleet) {
            val identity = MinerMerge.workerIdentity(row) ?: continue
            if (identity !in fleetByIdentity) fleetByIdentity[identity] = row
        }
        val claimedFleet = mutableSetOf<String>()

        val raw = mutableListOf<AutoConnectCandidate>()

        // (a) Saved profiles, each claiming its fleet row when it has one.
        for (profile in profiles) {
            val identity = MinerMerge.normalizeWorkerName(profile.workerName)
            val row = if (identity.isNotBlank()) fleetByIdentity[identity] else null
            if (row != null) claimedFleet += identity
            raw += AutoConnectCandidate(
                identity = identity,
                identityRaw = profile.workerName.trim(),
                ip = profile.host.trim(),
                alternateIp = row?.ip?.trim()?.takeIf { it.isNotBlank() && it != profile.host.trim() },
                savedProfile = profile,
                snapshot = row,
                sheetOwner = row?.user?.trim().orEmpty()
            )
        }

        // (b) Fleet machines with no saved profile on this phone.
        for ((identity, row) in fleetByIdentity) {
            if (identity in claimedFleet) continue
            raw += AutoConnectCandidate(
                identity = identity,
                identityRaw = RelayUpload.sheetIdForWorkerName(row.id)
                    ?: row.nickname.trim().substringBefore(" (").trim(),
                ip = row.ip.trim(),
                savedProfile = null,
                snapshot = row,
                sheetOwner = row.user.trim()
            )
        }

        // Dedupe by identity, then by IP — first candidate wins (the
        // list is already ordered profile-backed first).
        val seenIdentities = mutableSetOf<String>()
        val seenIps = mutableSetOf<String>()
        val out = mutableListOf<AutoConnectCandidate>()
        for (candidate in raw) {
            if (candidate.ip.isBlank() && candidate.alternateIp.isNullOrBlank()) continue
            if (candidate.identity.isNotBlank() && candidate.identity in seenIdentities) continue
            val primaryIp = candidate.probeIps.firstOrNull() ?: continue
            if (primaryIp in seenIps) continue
            if (candidate.identity.isNotBlank()) seenIdentities += candidate.identity
            seenIps += primaryIp
            out += candidate
        }
        return out
    }

    /**
     * Classifies a SUCCESSFUL authenticated probe (system info + pools
     * were read, so [resolvedIdentity] is the worker name from the
     * miner's own pool config, or null when pools could not be read).
     *
     * [knownIdentities] is every normalised identity in the candidate
     * set / fleet — it is what separates "a different machine we know"
     * from "a machine we have never seen".
     */
    fun classify(
        candidate: AutoConnectCandidate,
        resolvedIdentity: String?,
        knownIdentities: Set<String>
    ): ProbeClass {
        val resolved = MinerMerge.normalizeWorkerName(resolvedIdentity)
        if (resolved.isBlank()) {
            // Authenticated but identity unreadable. A saved profile IS
            // the known machine (its own credentials opened it), so the
            // probe still counts as a match and nothing changes hands;
            // a fleet-only candidate cannot be verified — never added.
            return if (candidate.savedProfile != null) ProbeClass.MATCH else ProbeClass.UNVERIFIED
        }
        if (candidate.identity.isBlank()) {
            // A saved profile that never learned its worker name adopts
            // the resolved one (the profile-manager merge keys on it).
            return ProbeClass.MATCH
        }
        if (resolved == candidate.identity) return ProbeClass.MATCH
        return if (resolved in knownIdentities) {
            ProbeClass.DIFFERENT_KNOWN_MACHINE
        } else {
            ProbeClass.UNKNOWN_MACHINE
        }
    }

    /**
     * Classifies a FAILED probe from the unauthenticated signature
     * check: [reachable] = something answered at the IP at all,
     * [isAntminer] = the answer looks like an Antminer (auth required or
     * confirmed signature) rather than some other device.
     */
    fun classifyFailure(reachable: Boolean, isAntminer: Boolean): ProbeClass = when {
        !reachable -> ProbeClass.UNREACHABLE
        isAntminer -> ProbeClass.NEEDS_PASSWORD
        else -> ProbeClass.UNKNOWN_MACHINE
    }
}
