package com.example.antminerconnectiontest.data.cloud

import com.example.antminerconnectiontest.domain.model.CloudSyncResponse
import com.example.antminerconnectiontest.domain.model.RelayUpload
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
import com.example.antminerconnectiontest.domain.model.TelemetryCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Handles HTTP requests to the Google Apps Script Webhook.
 */
class CloudSyncManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(SHARED_CLOUD_POOL)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        private val SHARED_CLOUD_POOL = okhttp3.ConnectionPool(4, 30L, TimeUnit.SECONDS)

        /**
         * The upload payload as plain data (serialised by [toJsonObject]),
         * kept separate from org.json so the wire contract is unit
         * testable. `"mode": "upsert"` declares the write semantics: the
         * endpoint replaces the (user, id) row instead of appending a
         * new one.
         */
        fun buildUploadPayloadData(
            userName: String,
            miners: List<RemoteMinerSnapshot>
        ): Map<String, Any> {
            val minerMaps = miners.map { miner ->
                val map = linkedMapOf<String, Any>(
                    "id" to miner.id,
                    "nickname" to miner.nickname,
                    "ip" to miner.ip,
                    "hashrate5s" to miner.hashrate5s,
                    "avgHashrate" to miner.avgHashrate,
                    "maxTemp" to miner.maxTemp,
                    "maxFan" to miner.maxFan,
                    "status" to miner.status
                )
                // Extended telemetry (v0.1.6): a key is sent only when
                // the reading was actually measured, so the endpoint
                // leaves the cell blank for unknown values instead of
                // the client inventing a 0 the far side would render
                // as real. Legacy maxTemp/maxFan above stay populated
                // exactly as before for older readers.
                if (miner.model.isNotBlank()) map["model"] = miner.model
                if (miner.firmware.isNotBlank()) map["firmware"] = miner.firmware
                miner.uptimeSec?.let { map["uptimeSec"] = it }
                miner.hwErrors?.let { map["hwErrors"] = it }
                miner.hashrate30m?.let { map["hashrate30m"] = it }
                miner.idealHashrate?.let { map["idealHashrate"] = it }
                miner.maxChipTemp?.let { map["maxChipTemp"] = it }
                miner.maxPcbTemp?.let { map["maxPcbTemp"] = it }
                if (miner.fanRpms.isNotEmpty()) map["fans"] = TelemetryCodec.encodeFans(miner.fanRpms)
                if (miner.poolUrl.isNotBlank()) map["poolUrl"] = miner.poolUrl
                if (miner.poolWorker.isNotBlank()) map["poolWorker"] = miner.poolWorker
                if (miner.poolStatus.isNotBlank()) map["poolStatus"] = miner.poolStatus
                miner.acceptedShares?.let { map["acceptedShares"] = it }
                miner.rejectedShares?.let { map["rejectedShares"] = it }
                miner.health?.let { map["health"] = TelemetryCodec.encodeHealth(it) }
                if (miner.chains.isNotEmpty()) map["chainsJson"] = TelemetryCodec.encodeChains(miner.chains)
                map
            }
            return linkedMapOf(
                "user" to userName.trim(),
                "mode" to "upsert",
                "miners" to minerMaps
            )
        }

        /**
         * Parses one miner row from the endpoint's JSON. Tolerant by
         * design — the endpoint passes sheet cells through, so numbers
         * may arrive as JSON numbers, numeric strings, or "" (an empty
         * cell), and rows written before v0.1.6 lack the extended keys
         * entirely. Every extended field falls back to unknown
         * (null/blank/empty) rather than a fabricated default.
         */
        fun parseRemoteMiner(obj: JSONObject): RemoteMinerSnapshot {
            return RemoteMinerSnapshot(
                user = obj.optString("user", ""),
                id = obj.optString("id", ""),
                nickname = obj.optString("nickname", "Antminer"),
                ip = obj.optString("ip", ""),
                hashrate5s = obj.optDouble("hashrate5s", 0.0),
                avgHashrate = obj.optDouble("avgHashrate", 0.0),
                maxTemp = obj.optInt("maxTemp", 0),
                maxFan = obj.optInt("maxFan", 0),
                status = obj.optString("status", "Online"),
                lastUpdated = obj.optString("lastUpdated", ""),
                model = obj.optString("model", ""),
                firmware = obj.optString("firmware", ""),
                uptimeSec = obj.optLongOrNull("uptimeSec"),
                hwErrors = obj.optLongOrNull("hwErrors"),
                hashrate30m = obj.optDoubleOrNull("hashrate30m"),
                idealHashrate = obj.optDoubleOrNull("idealHashrate"),
                maxChipTemp = obj.optIntOrNull("maxChipTemp"),
                maxPcbTemp = obj.optIntOrNull("maxPcbTemp"),
                fanRpms = TelemetryCodec.decodeFans(obj.optString("fans", "")),
                poolUrl = obj.optString("poolUrl", ""),
                poolWorker = obj.optString("poolWorker", ""),
                poolStatus = obj.optString("poolStatus", ""),
                acceptedShares = obj.optLongOrNull("acceptedShares"),
                rejectedShares = obj.optLongOrNull("rejectedShares"),
                health = TelemetryCodec.decodeHealth(obj.optString("health", "")),
                chains = TelemetryCodec.decodeChains(obj.optString("chainsJson", ""))
            )
        }

        /** Long value, or null when the key is absent/blank/non-numeric. */
        private fun JSONObject.optLongOrNull(name: String): Long? {
            if (!has(name) || isNull(name)) return null
            return when (val v = opt(name)) {
                is Number -> v.toLong()
                is String -> v.trim().toLongOrNull()
                else -> null
            }
        }

        /** Double value, or null when absent/blank/non-numeric/NaN. */
        private fun JSONObject.optDoubleOrNull(name: String): Double? {
            if (!has(name) || isNull(name)) return null
            return when (val v = opt(name)) {
                is Number -> v.toDouble().takeIf { !it.isNaN() }
                is String -> v.trim().toDoubleOrNull()?.takeIf { !it.isNaN() }
                else -> null
            }
        }

        private fun JSONObject.optIntOrNull(name: String): Int? =
            optLongOrNull(name)?.toInt()

        /** The delete-rows payload as plain data; see [deleteRemoteRows]. */
        fun buildDeletePayloadData(
            userName: String,
            ids: List<String>
        ): Map<String, Any> {
            return linkedMapOf(
                "user" to userName.trim(),
                "action" to "deleteRows",
                "ids" to ids
            )
        }

        private fun toJsonObject(data: Map<String, Any>): JSONObject {
            val obj = JSONObject()
            for ((key, value) in data) {
                obj.put(key, toJsonValue(value))
            }
            return obj
        }

        private fun toJsonValue(value: Any): Any {
            return when (value) {
                is Map<*, *> -> {
                    val obj = JSONObject()
                    for ((k, v) in value) {
                        if (k is String && v != null) obj.put(k, toJsonValue(v))
                    }
                    obj
                }
                is List<*> -> {
                    val arr = JSONArray()
                    for (item in value) {
                        if (item != null) arr.put(toJsonValue(item))
                    }
                    arr
                }
                else -> value
            }
        }
    }

    /**
     * Fetches permitted miners from the Google Sheet for the specified user.
     */
    suspend fun fetchRemoteMiners(webhookUrl: String, userName: String): Result<CloudSyncResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = webhookUrl.trim()
                if (cleanUrl.isBlank() || userName.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Webhook URL and User Name are required."))
                }

                val encodedUser = URLEncoder.encode(userName.trim(), "UTF-8")
                val finalUrl = if (cleanUrl.contains("?")) {
                    "$cleanUrl&user=$encodedUser"
                } else {
                    "$cleanUrl?user=$encodedUser"
                }

                val request = Request.Builder()
                    .url(finalUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status", "error")
                    if (status != "success") {
                        val msg = json.optString("message", "Unknown error from cloud script")
                        return@withContext Result.failure(Exception(msg))
                    }

                    val user = json.optString("user", userName)
                    val canView = json.optString("canView", "")
                    val minersArray = json.optJSONArray("miners") ?: JSONArray()
                    val minersList = mutableListOf<RemoteMinerSnapshot>()

                    for (i in 0 until minersArray.length()) {
                        minersList.add(parseRemoteMiner(minersArray.getJSONObject(i)))
                    }

                    Result.success(
                        CloudSyncResponse(
                            status = "success",
                            user = user,
                            canView = canView,
                            miners = minersList
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Uploads local miner snapshots to the Google Sheet.
     *
     * The batch passes through [RelayUpload.prepareSnapshots] first, so
     * no caller can ever write a row keyed by a per-device profile UUID
     * or a blank id — a machine is uploaded only under its worker
     * username. The payload declares `"mode": "upsert"`: one row per
     * (user, id), replaced on every write. Endpoint versions that ignore
     * the field behave as before; the reference script in
     * docs/apps-script/ implements it.
     */
    suspend fun uploadLocalMiners(
        webhookUrl: String,
        userName: String,
        localMiners: List<RemoteMinerSnapshot>
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = webhookUrl.trim()
                if (cleanUrl.isBlank() || userName.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Webhook URL and User Name are required."))
                }

                val prepared = RelayUpload.prepareSnapshots(localMiners)
                if (prepared.isEmpty()) {
                    // Nothing with a proven machine identity — posting an
                    // empty batch could only create junk, so skip the call.
                    return@withContext Result.success(0)
                }

                val payload = toJsonObject(buildUploadPayloadData(userName, prepared))

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status", "error")
                    if (status != "success") {
                        val msg = json.optString("message", "Upload rejected by script")
                        return@withContext Result.failure(Exception(msg))
                    }

                    val count = json.optInt("count", prepared.size)
                    Result.success(count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Asks the endpoint to delete sheet rows by id, scoped to [userName].
     * Used to heal legacy duplicates: rows a machine accumulated under a
     * per-device UUID before the client stopped creating them (selection
     * in [RelayUpload.legacyDuplicateIds]). Best-effort by design —
     * endpoint versions without delete support answer with an error or
     * ignore the action, and callers ignore the result; no new duplicates
     * are created either way, and the read side collapses what remains.
     */
    suspend fun deleteRemoteRows(
        webhookUrl: String,
        userName: String,
        ids: List<String>
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = webhookUrl.trim()
                if (cleanUrl.isBlank() || userName.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Webhook URL and User Name are required."))
                }
                if (ids.isEmpty()) {
                    return@withContext Result.success(0)
                }

                val payload = toJsonObject(buildDeletePayloadData(userName, ids))

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status", "error")
                    if (status != "success") {
                        val msg = json.optString("message", "Delete rejected by script")
                        return@withContext Result.failure(Exception(msg))
                    }

                    Result.success(json.optInt("count", ids.size))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
