package com.example.antminerconnectiontest.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One GitHub release, reduced to what update selection needs.
 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val isDraft: Boolean
)

/**
 * A release the user should be offered: strictly newer than the
 * installed build and carrying a downloadable APK asset.
 */
data class AvailableUpdate(
    /** Parsed version from the tag, e.g. "0.1.5" for tag "v0.1.5". */
    val versionName: String,
    val tag: String,
    /** Short release notes (first lines of the release body). */
    val notes: String,
    val apkDownloadUrl: String,
    val releaseUrl: String
)

/**
 * Update checks against GitHub Releases for banishwor/antminer-android.
 *
 * These builds ship as PRERELEASES, so the release list is used as-is
 * (drafts excluded) rather than the /releases/latest endpoint, which
 * would never see them. Versions are compared by parsing the tag's
 * dotted numeric version ("v0.1.3-reskin" → 0.1.3) and comparing
 * segment by segment numerically — never lexicographically, so
 * 0.1.10 correctly beats 0.1.9. The release metadata carries no
 * versionCode, so the tag's versionName is the comparison basis; the
 * app sets versionName from the same release number, keeping the two
 * in lockstep.
 *
 * All failures are quiet: [fetchLatestUpdate] returns a failure Result
 * and callers show nothing for automatic checks.
 */
object UpdateChecker {

    const val RELEASES_API_URL =
        "https://api.github.com/repos/banishwor/antminer-android/releases?per_page=20"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Parses a dotted numeric version out of a tag or versionName:
     * "v0.1.3-reskin" → [0, 1, 3], "0.1.4" → [0, 1, 4], "v0.2" → [0, 2].
     * Returns null for anything that is not a clean dotted-number
     * version (blank, letters in a segment, empty segments).
     */
    fun parseVersion(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .trim()
        if (cleaned.isBlank()) return null
        val parts = cleaned.split('.')
        if (parts.any { part -> part.isBlank() || !part.all { it.isDigit() } }) return null
        return parts.map { it.toInt() }
    }

    /** Numeric segment-wise comparison; missing segments count as 0. */
    fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** True when [candidateTagOrName] parses to a version strictly newer than [installedVersionName]. */
    fun isNewerVersion(candidateTagOrName: String?, installedVersionName: String?): Boolean {
        val candidate = parseVersion(candidateTagOrName) ?: return false
        val installed = parseVersion(installedVersionName) ?: return false
        return compareVersions(candidate, installed) > 0
    }

    /**
     * Picks the update to offer from a release list: the highest
     * non-draft version strictly newer than the installed one, among
     * releases that carry an APK asset. Null when nothing qualifies.
     */
    fun selectUpdate(releases: List<ReleaseInfo>, installedVersionName: String): AvailableUpdate? {
        val installed = parseVersion(installedVersionName) ?: return null
        val best = releases
            .filter { !it.isDraft && it.apkDownloadUrl != null }
            .mapNotNull { release -> parseVersion(release.tag)?.let { release to it } }
            .filter { (_, version) -> compareVersions(version, installed) > 0 }
            .maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
            ?: return null
        val (release, version) = best
        return AvailableUpdate(
            versionName = version.joinToString("."),
            tag = release.tag,
            notes = shortenNotes(release.body),
            apkDownloadUrl = release.apkDownloadUrl!!,
            releaseUrl = release.htmlUrl
        )
    }

    /** First non-blank lines of the release body, capped for a dialog. */
    fun shortenNotes(body: String, maxChars: Int = 280): String {
        val cleaned = body.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString("\n")
            .trim()
        return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars).trimEnd() + "…"
    }

    fun releaseInfoFromJson(obj: JSONObject): ReleaseInfo {
        var apkUrl: String? = null
        val assets: JSONArray? = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "").ifBlank { null }
                    if (apkUrl != null) break
                }
            }
        }
        return ReleaseInfo(
            tag = obj.optString("tag_name", ""),
            name = obj.optString("name", ""),
            body = obj.optString("body", ""),
            htmlUrl = obj.optString("html_url", ""),
            apkDownloadUrl = apkUrl,
            isDraft = obj.optBoolean("draft", false)
        )
    }

    fun parseReleases(jsonBody: String): List<ReleaseInfo> {
        val array = JSONArray(jsonBody)
        val out = mutableListOf<ReleaseInfo>()
        for (i in 0 until array.length()) {
            out += releaseInfoFromJson(array.getJSONObject(i))
        }
        return out
    }

    /**
     * Fetches the release list (including prereleases) and selects the
     * update to offer for [installedVersionName]. Success(null) means
     * "checked, nothing newer". Any network/parse problem is a failure
     * Result — callers stay silent for automatic checks.
     */
    suspend fun fetchLatestUpdate(installedVersionName: String): Result<AvailableUpdate?> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AntminerAndroid")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body?.string().orEmpty()
                    Result.success(selectUpdate(parseReleases(body), installedVersionName))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
