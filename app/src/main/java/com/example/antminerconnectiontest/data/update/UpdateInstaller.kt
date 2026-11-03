package com.example.antminerconnectiontest.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded update APK to the system installer (v0.4.0),
 * Esei's flow: sideloaded apps need the per-app "install unknown
 * apps" blessing first — without it the installer intent silently
 * does nothing on most ROMs — so when the blessing is missing the
 * user is routed to that one switch, then taps Install again.
 */
object UpdateInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** The FileProvider authority declared in the manifest. */
    fun fileProviderAuthority(packageName: String): String = "$packageName.fileprovider"

    /**
     * Pure decision (unit-tested): the unknown-sources detour exists
     * from Android 8 (API 26) up, and only while the app cannot
     * request package installs.
     */
    fun needsUnknownSourcesPrompt(sdkInt: Int, canRequestPackageInstalls: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.O && !canRequestPackageInstalls

    fun unknownSourcesIntent(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            fileProviderAuthority(context.packageName),
            file
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, APK_MIME_TYPE)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Install [file], detouring through the unknown-sources settings
     * screen first when needed (Esei's flow). If that settings
     * screen itself cannot open, the installer is launched anyway —
     * it shows its own "not allowed" prompt with a way in.
     */
    fun installApk(context: Context, file: File) {
        if (needsUnknownSourcesPrompt(
                Build.VERSION.SDK_INT,
                context.packageManager.canRequestPackageInstalls()
            )
        ) {
            val opened = runCatching {
                context.startActivity(unknownSourcesIntent(context.packageName))
            }.isSuccess
            if (opened) return
        }
        runCatching { context.startActivity(installIntent(context, file)) }
    }
}
