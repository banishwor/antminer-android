package com.example.antminerconnectiontest.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateDownloadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- Download state machine -------------------------------------

    @Test
    fun tracker_beginStartsAtZeroProgress() {
        val tracker = UpdateDownloadTracker()
        assertEquals(UpdateDownloadState.Idle, tracker.state)
        tracker.begin()
        val state = tracker.state
        assertTrue(state is UpdateDownloadState.Downloading)
        assertEquals(0f, (state as UpdateDownloadState.Downloading).fraction, 0.001f)
    }

    @Test
    fun tracker_progressOnlyCountsWhileDownloading() {
        val tracker = UpdateDownloadTracker()
        tracker.progress(50, 100)
        assertEquals(UpdateDownloadState.Idle, tracker.state)

        tracker.begin()
        tracker.progress(25, 100)
        assertEquals(0.25f, (tracker.state as UpdateDownloadState.Downloading).fraction, 0.001f)
        tracker.progress(100, 100)
        assertEquals(1f, (tracker.state as UpdateDownloadState.Downloading).fraction, 0.001f)
    }

    @Test
    fun tracker_succeedFailCancelReset() {
        val tracker = UpdateDownloadTracker()
        val file = File("antminer-0.4.0.apk")

        tracker.begin()
        tracker.succeed(file)
        assertEquals(UpdateDownloadState.Ready(file), tracker.state)

        tracker.begin()
        tracker.fail("HTTP 404")
        assertEquals(UpdateDownloadState.Failed("HTTP 404"), tracker.state)

        // Retry after failure starts a fresh download.
        tracker.begin()
        assertTrue(tracker.state is UpdateDownloadState.Downloading)

        tracker.cancel()
        assertEquals(UpdateDownloadState.Idle, tracker.state)

        tracker.begin()
        tracker.fail("boom")
        tracker.reset()
        assertEquals(UpdateDownloadState.Idle, tracker.state)
    }

    // --- Progress fraction ------------------------------------------

    @Test
    fun fraction_basicAndClamped() {
        assertEquals(0f, UpdateFiles.fraction(0, 100), 0.001f)
        assertEquals(0.5f, UpdateFiles.fraction(50, 100), 0.001f)
        assertEquals(1f, UpdateFiles.fraction(100, 100), 0.001f)
        assertEquals(1f, UpdateFiles.fraction(150, 100), 0.001f)
    }

    @Test
    fun fraction_unknownTotalReadsAsZero() {
        assertEquals(0f, UpdateFiles.fraction(10, 0), 0.001f)
        assertEquals(0f, UpdateFiles.fraction(10, -5), 0.001f)
        assertEquals(0f, UpdateFiles.fraction(0, 0), 0.001f)
    }

    // --- Cache file naming ------------------------------------------

    @Test
    fun apkFileName_fromVersion() {
        assertEquals("antminer-0.4.0.apk", UpdateFiles.apkFileName("0.4.0"))
        assertEquals("antminer-1.2.apk", UpdateFiles.apkFileName("v1.2"))
        assertEquals("antminer-1.2.apk", UpdateFiles.apkFileName("V1.2"))
    }

    @Test
    fun apkFileName_neverContainsPathSeparators() {
        val name = UpdateFiles.apkFileName("0.4/0 ../evil")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.startsWith("antminer-"))
        assertTrue(name.endsWith(".apk"))
        assertEquals("antminer-update.apk", UpdateFiles.apkFileName(""))
        assertEquals("antminer-update.apk", UpdateFiles.apkFileName("..."))
    }

    // --- Leftover cleanup -------------------------------------------

    @Test
    fun clearLeftovers_deletesCachedFiles() {
        val cacheDir = tempFolder.newFolder("cache")
        val updates = File(cacheDir, UpdateFiles.CACHE_SUBDIR).apply { mkdirs() }
        File(updates, "antminer-0.3.0.apk").writeText("old")
        File(updates, "partial.apk").writeText("half")

        assertEquals(2, UpdateFiles.clearLeftovers(cacheDir))
        assertEquals(0, updates.listFiles()?.size ?: 0)
        assertEquals(0, UpdateFiles.clearLeftovers(cacheDir))
    }

    @Test
    fun clearLeftovers_missingDirIsZero() {
        val cacheDir = tempFolder.newFolder("empty-cache")
        assertEquals(0, UpdateFiles.clearLeftovers(cacheDir))
    }

    // --- Install routing --------------------------------------------

    @Test
    fun needsUnknownSourcesPrompt_matrix() {
        // Pre-API 26 there is no per-app unknown-sources switch.
        assertFalse(UpdateInstaller.needsUnknownSourcesPrompt(23, canRequestPackageInstalls = false))
        assertFalse(UpdateInstaller.needsUnknownSourcesPrompt(25, canRequestPackageInstalls = false))
        // From API 26 the detour applies only while the blessing is missing.
        assertTrue(UpdateInstaller.needsUnknownSourcesPrompt(26, canRequestPackageInstalls = false))
        assertFalse(UpdateInstaller.needsUnknownSourcesPrompt(26, canRequestPackageInstalls = true))
        assertTrue(UpdateInstaller.needsUnknownSourcesPrompt(34, canRequestPackageInstalls = false))
        assertFalse(UpdateInstaller.needsUnknownSourcesPrompt(34, canRequestPackageInstalls = true))
    }

    @Test
    fun installer_constants() {
        assertEquals(
            "com.example.antminerconnectiontest.fileprovider",
            UpdateInstaller.fileProviderAuthority("com.example.antminerconnectiontest")
        )
        assertEquals(
            "application/vnd.android.package-archive",
            UpdateInstaller.APK_MIME_TYPE
        )
    }
}
