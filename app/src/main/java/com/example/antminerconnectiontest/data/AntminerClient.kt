package com.example.antminerconnectiontest.data

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Result representing the outcome of testing the connection to an Antminer.
 */
sealed interface ConnectionResult {
    data class Success(
        val statusCode: Int,
        val responseBody: String,
        val executionTimeMs: Long
    ) : ConnectionResult

    data class Failure(
        val errorType: ErrorType,
        val message: String,
        val statusCode: Int? = null,
        val details: String? = null
    ) : ConnectionResult
}

enum class ErrorType {
    INVALID_IP,
    AUTHENTICATION_FAILED,
    TIMEOUT,
    UNREACHABLE,
    HTTP_ERROR,
    UNEXPECTED_ERROR
}

/**
 * Networking client for communicating with Antminer hardware using HTTP Digest Authentication.
 *
 * Antminer S19 uses RFC 2617 Digest Authentication on /cgi-bin/ endpoints.
 * This client utilizes okhttp-digest (io.github.rburgst:okhttp-digest) to handle the
 * standard 401 challenge-response handshake without hardcoding any nonce, cnonce, or hashes.
 */
class AntminerClient {

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 3L
        private const val READ_TIMEOUT_SECONDS = 5L
        const val STATS_ENDPOINT_PATH = "/cgi-bin/stats.cgi"

        val STANDARD_ENDPOINTS = listOf(
            "/cgi-bin/get_system_info.cgi",
            "/cgi-bin/summary.cgi",
            "/cgi-bin/stats.cgi",
            "/cgi-bin/pools.cgi",
            "/cgi-bin/warning.cgi",
            "/cgi-bin/chart.cgi"
        )

        private val SHARED_CONNECTION_POOL = okhttp3.ConnectionPool(4, 30L, TimeUnit.SECONDS)
    }

    /**
     * Tests the connection and authentication against the default stats.cgi endpoint.
     */
    suspend fun testConnection(
        rawIp: String,
        username: String,
        password: String
    ): ConnectionResult = fetchEndpoint(
        rawIp = rawIp,
        username = username,
        password = password,
        endpointPath = STATS_ENDPOINT_PATH
    )

    /**
     * Fetches a specific CGI endpoint from the Antminer using HTTP Digest Authentication.
     * Reuses the exact working OkHttpClient and DigestAuthenticator flow.
     *
     * @param rawIp The IP address or hostname of the Antminer.
     * @param username Digest auth username (typically "root").
     * @param password Digest auth password.
     * @param endpointPath The relative path (e.g. "/cgi-bin/summary.cgi").
     * @return [ConnectionResult] indicating success with payload or failure with descriptive message.
     */
    suspend fun fetchEndpoint(
        rawIp: String,
        username: String,
        password: String,
        endpointPath: String
    ): ConnectionResult = withContext(Dispatchers.IO) {
        val cleanIp = rawIp.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        
        if (cleanIp.isBlank() || !isValidHostOrIp(cleanIp)) {
            return@withContext ConnectionResult.Failure(
                errorType = ErrorType.INVALID_IP,
                message = "Invalid IP address: '$rawIp'. Please enter a valid IPv4 address (e.g. 192.168.1.2) or local hostname."
            )
        }

        val cleanPath = if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"
        val targetUrl = "http://$cleanIp$cleanPath"
        val startTime = System.currentTimeMillis()


        try {
            // Configure HTTP Digest Authentication cache & authenticator
            val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
            val credentials = Credentials(username, password)
            val digestAuthenticator = DigestAuthenticator(credentials)
            val cachingDecorator = CachingAuthenticatorDecorator(digestAuthenticator, authCache)

            val client = OkHttpClient.Builder()
                .connectionPool(SHARED_CONNECTION_POOL)
                .authenticator(cachingDecorator)
                .addInterceptor(AuthenticationCacheInterceptor(authCache))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "AntminerConnectionTest/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val statusCode = response.code
                val responseBody = response.body?.string().orEmpty()

                when (statusCode) {
                    200 -> {
                        ConnectionResult.Success(
                            statusCode = statusCode,
                            responseBody = responseBody,
                            executionTimeMs = duration
                        )
                    }
                    401 -> {
                        ConnectionResult.Failure(
                            errorType = ErrorType.AUTHENTICATION_FAILED,
                            statusCode = statusCode,
                            message = "Authentication failed (HTTP 401 Unauthorized). Please check your username and password.",
                            details = "The miner rejected the Digest credentials provided."
                        )
                    }
                    403 -> {
                        ConnectionResult.Failure(
                            errorType = ErrorType.HTTP_ERROR,
                            statusCode = statusCode,
                            message = "Access forbidden (HTTP 403).",
                            details = responseBody.take(300)
                        )
                    }
                    404 -> {
                        ConnectionResult.Failure(
                            errorType = ErrorType.HTTP_ERROR,
                            statusCode = statusCode,
                            message = "Endpoint not found (HTTP 404). Endpoint '$cleanPath' may not be supported by this firmware.",
                            details = responseBody.take(300)
                        )
                    }
                    else -> {
                        ConnectionResult.Failure(
                            errorType = ErrorType.HTTP_ERROR,
                            statusCode = statusCode,
                            message = "HTTP error code: $statusCode (${response.message.ifBlank { "Error" }}).",
                            details = responseBody.take(300)
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            ConnectionResult.Failure(
                errorType = ErrorType.TIMEOUT,
                message = "Connection timed out. The miner at $cleanIp did not respond within ${CONNECT_TIMEOUT_SECONDS}s.",
                details = e.localizedMessage
            )
        } catch (e: ConnectException) {
            ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Cannot reach miner at $cleanIp. Ensure your Android device is on the same local Wi-Fi / subnet and the miner is powered on.",
                details = e.localizedMessage
            )
        } catch (e: NoRouteToHostException) {
            ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "No route to host ($cleanIp). The device is unreachable on this network.",
                details = e.localizedMessage
            )
        } catch (e: UnknownHostException) {
            ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Unknown host ($cleanIp). Check the IP address or DNS resolution.",
                details = e.localizedMessage
            )
        } catch (e: IOException) {
            ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Network I/O error: ${e.message ?: "Failed to connect"}",
                details = e.localizedMessage
            )
        } catch (e: Exception) {
            ConnectionResult.Failure(
                errorType = ErrorType.UNEXPECTED_ERROR,
                message = "Unexpected error: ${e.message ?: e.javaClass.simpleName}",
                details = e.stackTraceToString().take(500)
            )
        }
    }

    /**
     * Sends an authenticated POST request with a JSON body to the Antminer.
     * Used for write/action endpoints such as /cgi-bin/create_log_backup.cgi.
     */
    suspend fun postJsonEndpoint(
        rawIp: String,
        username: String,
        password: String,
        endpointPath: String,
        jsonBody: String
    ): ConnectionResult = withContext(Dispatchers.IO) {
        val cleanIp = rawIp.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

        if (cleanIp.isBlank() || !isValidHostOrIp(cleanIp)) {
            return@withContext ConnectionResult.Failure(
                errorType = ErrorType.INVALID_IP,
                message = "Invalid IP address: '$rawIp'."
            )
        }

        val cleanPath = if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"
        val targetUrl = "http://$cleanIp$cleanPath"
        val startTime = System.currentTimeMillis()

        try {
            val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
            val credentials = Credentials(username, password)
            val digestAuthenticator = DigestAuthenticator(credentials)
            val cachingDecorator = CachingAuthenticatorDecorator(digestAuthenticator, authCache)

            val client = OkHttpClient.Builder()
                .connectionPool(SHARED_CONNECTION_POOL)
                .authenticator(cachingDecorator)
                .addInterceptor(AuthenticationCacheInterceptor(authCache))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "AntminerConnectionTest/1.0 (Android)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val statusCode = response.code
                val responseBody = response.body?.string().orEmpty()

                when (statusCode) {
                    200 -> ConnectionResult.Success(statusCode, responseBody, duration)
                    401 -> ConnectionResult.Failure(
                        errorType = ErrorType.AUTHENTICATION_FAILED,
                        statusCode = statusCode,
                        message = "Authentication failed (HTTP 401 Unauthorized)."
                    )
                    else -> ConnectionResult.Failure(
                        errorType = ErrorType.HTTP_ERROR,
                        statusCode = statusCode,
                        message = "HTTP error $statusCode: $responseBody"
                    )
                }
            }
        } catch (e: Exception) {
            handleException(e, targetUrl, startTime)
        }
    }

    /**
     * Downloads a file/archive from the Antminer using Digest Authentication,
     * streaming directly to a local destination file.
     */
    suspend fun downloadFileEndpoint(
        rawIp: String,
        username: String,
        password: String,
        endpointPath: String,
        destinationFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanIp = rawIp.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

        if (cleanIp.isBlank() || !isValidHostOrIp(cleanIp)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid IP address: '$rawIp'"))
        }

        val cleanPath = if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"
        val targetUrl = "http://$cleanIp$cleanPath"

        try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) destinationFile.delete()

            val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
            val credentials = Credentials(username, password)
            val digestAuthenticator = DigestAuthenticator(credentials)
            val cachingDecorator = CachingAuthenticatorDecorator(digestAuthenticator, authCache)

            // Large archive download: 120s timeout
            val client = OkHttpClient.Builder()
                .connectionPool(SHARED_CONNECTION_POOL)
                .authenticator(cachingDecorator)
                .addInterceptor(AuthenticationCacheInterceptor(authCache))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "AntminerConnectionTest/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Failed to download $cleanPath"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body from $cleanPath"))
                val contentLength = body.contentLength()

                body.byteStream().use { inputStream ->
                    destinationFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesReadTotal = 0L
                        var read: Int

                        while (inputStream.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            outputStream.write(buffer, 0, read)
                            bytesReadTotal += read
                            onProgress(bytesReadTotal, contentLength)
                        }
                    }
                }

                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            Result.failure(e)
        }
    }

    private fun isValidHostOrIp(host: String): Boolean {
        // Regex for IPv4 with optional port or standard hostname
        val ipv4Pattern = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}(:\\d{1,5})?$")
        val hostPattern = Regex("^[a-zA-Z0-9.-]+(:\\d{1,5})?$")
        return ipv4Pattern.matches(host) || hostPattern.matches(host)
    }

    private fun handleException(e: Exception, targetUrl: String, startTime: Long): ConnectionResult {
        val cleanIp = targetUrl.removePrefix("http://").substringBefore('/')
        return when (e) {
            is SocketTimeoutException -> ConnectionResult.Failure(
                errorType = ErrorType.TIMEOUT,
                message = "Connection timed out connecting to $cleanIp.",
                details = e.localizedMessage
            )
            is ConnectException -> ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Cannot reach miner at $cleanIp.",
                details = e.localizedMessage
            )
            is NoRouteToHostException -> ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "No route to host ($cleanIp).",
                details = e.localizedMessage
            )
            is UnknownHostException -> ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Unknown host ($cleanIp).",
                details = e.localizedMessage
            )
            is IOException -> ConnectionResult.Failure(
                errorType = ErrorType.UNREACHABLE,
                message = "Network I/O error: ${e.message ?: "Failed to connect"}",
                details = e.localizedMessage
            )
            else -> ConnectionResult.Failure(
                errorType = ErrorType.UNEXPECTED_ERROR,
                message = "Unexpected error: ${e.message ?: e.javaClass.simpleName}",
                details = e.stackTraceToString().take(500)
            )
        }
    }
}
