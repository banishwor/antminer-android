package com.example.antminerconnectiontest.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app update download (v0.4.0), modelled on Esei's DownloadState:
 * the selected release's APK streams into the app cache and the
 * dialog renders this state machine — nothing here opens a browser.
 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val fraction: Float) : UpdateDownloadState
    data class Ready(val file: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * The pure transition logic behind [UpdateDownloader], unit-tested
 * without Android or the network: progress only counts while a
 * download is in flight, cancel/reset always land back on Idle.
 */
class UpdateDownloadTracker {
    var state: UpdateDownloadState = UpdateDownloadState.Idle
        private set

    fun begin() {
        state = UpdateDownloadState.Downloading(0f)
    }

    fun progress(readBytes: Long, totalBytes: Long) {
        if (state is UpdateDownloadState.Downloading) {
            state = UpdateDownloadState.Downloading(UpdateFiles.fraction(readBytes, totalBytes))
        }
    }

    fun succeed(file: File) {
        state = UpdateDownloadState.Ready(file)
    }

    fun fail(message: String) {
        state = UpdateDownloadState.Failed(message)
    }

    fun cancel() {
        state = UpdateDownloadState.Idle
    }

    fun reset() {
        state = UpdateDownloadState.Idle
    }
}

/** Pure file helpers for the update cache (unit-tested). */
object UpdateFiles {

    const val CACHE_SUBDIR = "updates"

    /**
     * The cache file name for a version: "0.4.0" → "antminer-0.4.0.apk".
     * Anything that is not a letter, digit, dot or dash becomes an
     * underscore, so a hostile or odd version string can never turn
     * into a path.
     */
    fun apkFileName(versionName: String): String {
        val clean = versionName.trim().removePrefix("v").removePrefix("V")
            .map { c -> if (c.isLetterOrDigit() || c == '.' || c == '-') c else '_' }
            .joinToString("")
            .trim('.', '-')
        return "antminer-${clean.ifBlank { "update" }}.apk"
    }

    /** Download progress in 0f..1f; an unknown total (≤ 0) reads as 0. */
    fun fraction(readBytes: Long, totalBytes: Long): Float =
        if (totalBytes <= 0L) {
            0f
        } else {
            (readBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * Deletes every leftover file in the update cache and returns how
     * many were removed. Called at cold start (a half-downloaded APK
     * from a killed session is never resumed or offered) and before
     * each new download.
     */
    fun clearLeftovers(cacheDir: File): Int {
        val files = File(cacheDir, CACHE_SUBDIR).listFiles() ?: return 0
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }
}

/**
 * Streams the selected release's APK into `cacheDir/updates`,
 * Esei-style: cancellable, progress through [state], the finished
 * file kept until reset so "Install Now" needs no second download.
 */
object UpdateDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val tracker = UpdateDownloadTracker()

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeCall: Call? = null

    private fun publish() {
        _state.value = tracker.state
    }

    suspend fun download(context: Context, update: AvailableUpdate) {
        withContext(Dispatchers.IO) {
            cancelled = false
            tracker.begin()
            publish()
            runCatching {
                // Drop anything left over from an earlier attempt.
                UpdateFiles.clearLeftovers(context.cacheDir)
                val dir = File(context.cacheDir, UpdateFiles.CACHE_SUBDIR).apply { mkdirs() }
                val target = File(dir, UpdateFiles.apkFileName(update.versionName))

                val request = Request.Builder().url(update.apkDownloadUrl).build()
                val call = client.newCall(request)
                activeCall = call
                call.execute().use { response ->
                    check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                    val body = response.body ?: error("Empty download body")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var readTotal = 0L
                            while (true) {
                                if (cancelled) return@withContext
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                readTotal += read
                                tracker.progress(readTotal, total)
                                publish()
                            }
                        }
                    }
                }
                if (cancelled) return@withContext
                tracker.succeed(target)
                publish()
            }.onFailure { error ->
                if (cancelled) {
                    tracker.cancel()
                } else {
                    tracker.fail(error.message ?: "Download failed")
                }
                publish()
            }
            activeCall = null
        }
    }

    /** Stops an in-flight download; the stream loop sees the flag and bails. */
    fun cancel() {
        cancelled = true
        activeCall?.cancel()
        tracker.cancel()
        publish()
    }

    /** Back to square one after install or failure, so the dialog offers Download again. */
    fun reset() {
        tracker.reset()
        publish()
    }

    /** Cold-start hygiene: wipe leftover update files. Never throws. */
    fun clearCache(context: Context) {
        runCatching { UpdateFiles.clearLeftovers(context.cacheDir) }
    }
}
