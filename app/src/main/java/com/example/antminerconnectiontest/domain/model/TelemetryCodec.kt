package com.example.antminerconnectiontest.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The miner's self-reported health flags, as carried in the summary
 * response. True = that subsystem reports healthy.
 */
data class MinerHealthFlags(
    val rateOk: Boolean,
    val networkOk: Boolean,
    val fansOk: Boolean,
    val tempOk: Boolean
) {
    val allOk: Boolean get() = rateOk && networkOk && fansOk && tempOk
}

/**
 * One hash board's detail as relayed through the sheet (the decoded
 * form of one object in the chainsJson cell). Deliberately a SUMMARY:
 * the per-chip temperature arrays the local dashboard shows never
 * leave the miner's network — only per-chain max/min travel.
 */
data class RemoteChainSnapshot(
    val chainIndex: Int,
    val realHashrateTh: Double,
    val idealHashrateTh: Double,
    val frequencyMhz: Double,
    val asicCount: Int,
    val asicStatusString: String,
    val chipMaxTemp: Int,
    val chipMinTemp: Int,
    val pcbMaxTemp: Int,
    val pcbMinTemp: Int,
    val hwErrors: Long
) {
    /** Chips the board reports as failed ('x' in the ASIC status string). */
    val deadChips: Int get() = asicStatusString.count { it == 'x' || it == 'X' }

    /** Chips the board reports as missing ('-' in the ASIC status string). */
    val missingChips: Int get() = asicStatusString.count { it == '-' }
}

/** Local [ChainStats] → its relayed summary form (temp arrays dropped). */
fun ChainStats.toRemoteChain(): RemoteChainSnapshot = RemoteChainSnapshot(
    chainIndex = chainIndex,
    realHashrateTh = realHashrateTh,
    idealHashrateTh = idealHashrateTh,
    frequencyMhz = frequencyMhz,
    asicCount = asicCount,
    asicStatusString = asicStatusString,
    chipMaxTemp = chipMaxTemp,
    chipMinTemp = chipMinTemp,
    pcbMaxTemp = pcbMaxTemp,
    pcbMinTemp = pcbMinTemp,
    hwErrors = hwErrors
)

/**
 * Fills a snapshot's extended-telemetry fields (v0.1.6) from whatever
 * local data the caller actually measured. Anything not supplied
 * stays unknown (null/blank/empty) — this is the one place upload
 * snapshots gain telemetry, so the in-app sync and the background
 * relay service can never drift apart in what they send, and neither
 * can fabricate a reading for a miner it only knows by hashrate.
 *
 * [fallbackModel] is the profile's cached model string, used when no
 * live system info is available (e.g. a miner known only from the
 * hashrate race poller).
 */
fun RemoteMinerSnapshot.withTelemetry(
    summary: MinerSummary?,
    stats: MinerStats?,
    pools: List<MinerPool>,
    systemInfo: MinerSystemInfo?,
    fallbackModel: String = ""
): RemoteMinerSnapshot {
    val primaryPool = pools.firstOrNull { it.priority == 0 }
        ?: pools.minByOrNull { it.priority }
    return copy(
        model = systemInfo?.minerType?.takeIf { it.isNotBlank() } ?: fallbackModel,
        firmware = systemInfo?.systemVersion ?: "",
        uptimeSec = summary?.elapsedSeconds,
        // Prefer the stats endpoint's counter; the summary's hwAll is
        // the same miner-reported total when stats are unavailable.
        hwErrors = stats?.totalHwErrors ?: summary?.hwAll,
        hashrate30m = summary?.hashrate30m?.takeIf { it > 0.0 },
        idealHashrate = summary?.hashrateIdeal?.takeIf { it > 0.0 }
            ?: stats?.chains?.sumOf { it.idealHashrateTh }?.takeIf { it > 0.0 },
        maxChipTemp = stats?.maxChipTemp,
        maxPcbTemp = stats?.maxPcbTemp,
        fanRpms = stats?.fans?.sortedBy { it.fanIndex }?.map { it.rpm } ?: emptyList(),
        poolUrl = primaryPool?.url ?: "",
        poolWorker = primaryPool?.user ?: "",
        poolStatus = primaryPool?.status ?: "",
        acceptedShares = primaryPool?.acceptedShares,
        rejectedShares = primaryPool?.rejectedShares,
        health = summary?.let {
            MinerHealthFlags(
                rateOk = it.rateHealth,
                networkOk = it.networkHealth,
                fansOk = it.fansHealth,
                tempOk = it.tempHealth
            )
        },
        chains = stats?.chains?.map { it.toRemoteChain() } ?: emptyList()
    )
}

/**
 * Packed encodings for the extended sheet columns (v0.1.6). A Google
 * Sheet cell is a single string, so multi-value telemetry travels
 * packed and is decoded on the far side:
 *
 *  - fans:   comma-separated RPMs in fan order — "5910,5880,0,6020"
 *  - health: exactly 4 chars, '1' = healthy / '0' = not, in the order
 *            hashrate, network, fans, temperature — "1110"
 *  - chainsJson: a compact JSON array, one object per hash board:
 *            [{"i":1,"rate":40.5,"ideal":40.0,"freq":485,"asics":63,
 *              "status":"ooo…x…","chipMax":63,"chipMin":51,
 *              "pcbMax":41,"pcbMin":35,"hw":4}, …]
 *
 * Empty string always means "unknown" (a row written before the field
 * existed, or a miner that didn't report it). Decoders are tolerant:
 * malformed input decodes to empty/null rather than throwing, because
 * the sheet is user-editable and must never crash the reader.
 */
object TelemetryCodec {

    /** Below this RPM a fan counts as "slow" — the local alert threshold. */
    const val SLOW_FAN_RPM = 3000

    fun encodeFans(rpms: List<Int>): String = rpms.joinToString(",")

    fun decodeFans(packed: String?): List<Int> {
        val s = packed?.trim().orEmpty()
        if (s.isEmpty()) return emptyList()
        return s.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun encodeHealth(health: MinerHealthFlags): String = buildString(4) {
        append(if (health.rateOk) '1' else '0')
        append(if (health.networkOk) '1' else '0')
        append(if (health.fansOk) '1' else '0')
        append(if (health.tempOk) '1' else '0')
    }

    fun decodeHealth(packed: String?): MinerHealthFlags? {
        val s = packed?.trim().orEmpty()
        if (s.length != 4 || s.any { it != '0' && it != '1' }) return null
        return MinerHealthFlags(
            rateOk = s[0] == '1',
            networkOk = s[1] == '1',
            fansOk = s[2] == '1',
            tempOk = s[3] == '1'
        )
    }

    fun encodeChains(chains: List<RemoteChainSnapshot>): String {
        if (chains.isEmpty()) return ""
        val arr = JSONArray()
        for (c in chains) {
            arr.put(
                JSONObject()
                    .put("i", c.chainIndex)
                    .put("rate", c.realHashrateTh)
                    .put("ideal", c.idealHashrateTh)
                    .put("freq", c.frequencyMhz)
                    .put("asics", c.asicCount)
                    .put("status", c.asicStatusString)
                    .put("chipMax", c.chipMaxTemp)
                    .put("chipMin", c.chipMinTemp)
                    .put("pcbMax", c.pcbMaxTemp)
                    .put("pcbMin", c.pcbMinTemp)
                    .put("hw", c.hwErrors)
            )
        }
        return arr.toString()
    }

    fun decodeChains(packed: String?): List<RemoteChainSnapshot> {
        val s = packed?.trim().orEmpty()
        if (s.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                RemoteChainSnapshot(
                    chainIndex = o.optInt("i", idx + 1),
                    realHashrateTh = o.optDouble("rate", 0.0),
                    idealHashrateTh = o.optDouble("ideal", 0.0),
                    frequencyMhz = o.optDouble("freq", 0.0),
                    asicCount = o.optInt("asics", 0),
                    asicStatusString = o.optString("status", ""),
                    chipMaxTemp = o.optInt("chipMax", 0),
                    chipMinTemp = o.optInt("chipMin", 0),
                    pcbMaxTemp = o.optInt("pcbMax", 0),
                    pcbMinTemp = o.optInt("pcbMin", 0),
                    hwErrors = o.optLong("hw", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
