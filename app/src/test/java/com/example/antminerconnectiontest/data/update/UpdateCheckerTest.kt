package com.example.antminerconnectiontest.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    // --- Parsing -------------------------------------------------------

    @Test
    fun parseVersion_handlesTagsAndNames() {
        assertEquals(listOf(0, 1, 4), UpdateChecker.parseVersion("v0.1.4"))
        assertEquals(listOf(0, 1, 4), UpdateChecker.parseVersion("0.1.4"))
        assertEquals(listOf(0, 1, 3), UpdateChecker.parseVersion("v0.1.3-reskin"))
        assertEquals(listOf(0, 1, 1), UpdateChecker.parseVersion("v0.1.1-fixes"))
        assertEquals(listOf(0, 2), UpdateChecker.parseVersion("v0.2"))
        assertEquals(listOf(1, 0), UpdateChecker.parseVersion("1.0"))
        assertEquals(listOf(0, 1, 10), UpdateChecker.parseVersion("  v0.1.10  "))
    }

    @Test
    fun parseVersion_rejectsGarbage() {
        assertNull(UpdateChecker.parseVersion(null))
        assertNull(UpdateChecker.parseVersion(""))
        assertNull(UpdateChecker.parseVersion("   "))
        assertNull(UpdateChecker.parseVersion("reskin"))
        assertNull(UpdateChecker.parseVersion("v1.beta.2"))
        assertNull(UpdateChecker.parseVersion("v0..1"))
        assertNull(UpdateChecker.parseVersion("v0.1.x"))
    }

    // --- Comparison ------------------------------------------------------

    @Test
    fun isNewerVersion_basicCases() {
        assertTrue(UpdateChecker.isNewerVersion("v0.1.5", "0.1.4"))
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0", "0.1.4"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.4", "0.1.4"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.3-reskin", "0.1.4"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.3", "0.1.4"))
    }

    @Test
    fun isNewerVersion_comparesNumericallyNotLexicographically() {
        // String comparison would say "0.1.9" > "0.1.10"; numeric wins.
        assertTrue(UpdateChecker.isNewerVersion("v0.1.10", "0.1.9"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.9", "0.1.10"))
    }

    @Test
    fun isNewerVersion_suffixAndMissingSegments() {
        // Same base version with a suffix is NOT newer.
        assertFalse(UpdateChecker.isNewerVersion("v0.1.4-beta", "0.1.4"))
        // Missing segments count as 0.
        assertTrue(UpdateChecker.isNewerVersion("v0.2", "0.1.9"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1", "0.1.0"))
        // Unparseable input is never "newer".
        assertFalse(UpdateChecker.isNewerVersion("garbage", "0.1.4"))
        assertFalse(UpdateChecker.isNewerVersion("v0.2.0", "garbage"))
    }

    // --- Selection -------------------------------------------------------

    private fun release(
        tag: String,
        apkUrl: String? = "https://example.com/$tag.apk",
        draft: Boolean = false,
        body: String = "Release notes for $tag"
    ) = ReleaseInfo(
        tag = tag,
        name = tag,
        body = body,
        htmlUrl = "https://github.com/banishwor/antminer-android/releases/tag/$tag",
        apkDownloadUrl = apkUrl,
        isDraft = draft
    )

    @Test
    fun selectUpdate_picksHighestNewerPrerelease() {
        val releases = listOf(
            release("v0.1.3-reskin"),
            release("v0.1.5"),
            release("v0.1.4"),
            release("v0.1.6")
        )
        val update = UpdateChecker.selectUpdate(releases, installedVersionName = "0.1.4")

        assertNotNull(update)
        assertEquals("0.1.6", update!!.versionName)
        assertEquals("v0.1.6", update.tag)
        assertEquals("https://example.com/v0.1.6.apk", update.apkDownloadUrl)
    }

    @Test
    fun selectUpdate_noneWhenUpToDate() {
        val releases = listOf(release("v0.1.3-reskin"), release("v0.1.4"))
        assertNull(UpdateChecker.selectUpdate(releases, installedVersionName = "0.1.4"))
        assertNull(UpdateChecker.selectUpdate(emptyList(), installedVersionName = "0.1.4"))
    }

    @Test
    fun selectUpdate_skipsDraftsAndReleasesWithoutApk() {
        val releases = listOf(
            release("v0.2.0", draft = true),
            release("v0.1.9", apkUrl = null),
            release("v0.1.5")
        )
        val update = UpdateChecker.selectUpdate(releases, installedVersionName = "0.1.4")

        assertNotNull(update)
        assertEquals("v0.1.5", update!!.tag)
    }

    @Test
    fun selectUpdate_ignoresUnparseableTags() {
        val releases = listOf(release("nightly"), release("v0.1.5"))
        val update = UpdateChecker.selectUpdate(releases, installedVersionName = "0.1.4")
        assertEquals("v0.1.5", update!!.tag)
    }

    // --- GitHub JSON parsing --------------------------------------------

    @Test
    fun parseReleases_readsTagDraftAndApkAsset() {
        val json = """
            [
              {
                "tag_name": "v0.1.5",
                "name": "v0.1.5",
                "body": "What's new\n- Stuff",
                "html_url": "https://github.com/banishwor/antminer-android/releases/tag/v0.1.5",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
                  {"name": "Antminer-v0.1.5-debug.apk", "browser_download_url": "https://example.com/app.apk"}
                ]
              },
              {
                "tag_name": "v0.1.4",
                "name": "v0.1.4",
                "body": "",
                "html_url": "https://github.com/banishwor/antminer-android/releases/tag/v0.1.4",
                "draft": true,
                "prerelease": true,
                "assets": []
              }
            ]
        """.trimIndent()

        val releases = UpdateChecker.parseReleases(json)

        assertEquals(2, releases.size)
        assertEquals("v0.1.5", releases[0].tag)
        assertFalse(releases[0].isDraft)
        assertEquals("https://example.com/app.apk", releases[0].apkDownloadUrl)
        assertTrue(releases[1].isDraft)
        assertNull(releases[1].apkDownloadUrl)

        // End to end: the parsed list yields the prerelease as an update.
        val update = UpdateChecker.selectUpdate(releases, installedVersionName = "0.1.4")
        assertNotNull(update)
        assertEquals("0.1.5", update!!.versionName)
    }
}
