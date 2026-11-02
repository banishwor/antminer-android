package com.example.antminerconnectiontest.data.cloud

import com.example.antminerconnectiontest.domain.model.CloudSyncResponse
import com.example.antminerconnectiontest.domain.model.RemoteMinerSnapshot
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
                        val obj = minersArray.getJSONObject(i)
                        minersList.add(
                            RemoteMinerSnapshot(
                                user = obj.optString("user", ""),
                                id = obj.optString("id", ""),
                                nickname = obj.optString("nickname", "Antminer"),
                                ip = obj.optString("ip", ""),
                                hashrate5s = obj.optDouble("hashrate5s", 0.0),
                                avgHashrate = obj.optDouble("avgHashrate", 0.0),
                                maxTemp = obj.optInt("maxTemp", 0),
                                maxFan = obj.optInt("maxFan", 0),
                                status = obj.optString("status", "Online"),
                                lastUpdated = obj.optString("lastUpdated", "")
                            )
                        )
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
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Uploads local miner snapshots to the Google Sheet.
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

                val payload = JSONObject()
                payload.put("user", userName.trim())

                val minersArray = JSONArray()
                localMiners.forEach { miner ->
                    val mObj = JSONObject()
                    mObj.put("id", miner.id)
                    mObj.put("nickname", miner.nickname)
                    mObj.put("ip", miner.ip)
                    mObj.put("hashrate5s", miner.hashrate5s)
                    mObj.put("avgHashrate", miner.avgHashrate)
                    mObj.put("maxTemp", miner.maxTemp)
                    mObj.put("maxFan", miner.maxFan)
                    mObj.put("status", miner.status)
                    minersArray.put(mObj)
                }
                payload.put("miners", minersArray)

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

                    val count = json.optInt("count", localMiners.size)
                    Result.success(count)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
