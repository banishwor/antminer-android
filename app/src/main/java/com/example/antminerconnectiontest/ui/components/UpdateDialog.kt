package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.antminerconnectiontest.data.update.AvailableUpdate
import com.example.antminerconnectiontest.data.update.UpdateDownloadState
import com.example.antminerconnectiontest.data.update.UpdateDownloader
import com.example.antminerconnectiontest.data.update.UpdateInstaller
import com.example.antminerconnectiontest.theme.panelShape

/**
 * The in-app update dialog (v0.4.0, Esei-style mechanics in the V3
 * design): version + release notes, then Download — the APK streams
 * into the app cache with a progress bar and a Cancel — then
 * Install Now, which hands the file to the system installer via
 * [UpdateInstaller] (including its unknown-sources detour). A failed
 * download offers a retry. Nothing here opens a browser; the
 * release page remains reachable from the failure state only.
 *
 * While a download is in flight the dialog cannot be dismissed by
 * tapping outside or pressing Back — Cancel is the way out, so a
 * stray tap never strands a half-downloaded update.
 */
@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleasePage: (String) -> Unit
) {
    val context = LocalContext.current
    val downloadState by UpdateDownloader.state.collectAsState()
    val downloading = downloadState is UpdateDownloadState.Downloading

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        shape = panelShape(),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Update available — v${update.versionName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = update.notes.ifBlank {
                        "A newer version of the app is ready to download."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (val state = downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloading… ${(state.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateDownloadState.Failed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { onOpenReleasePage(update.releaseUrl) }) {
                            Text("Open release page")
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (val state = downloadState) {
                is UpdateDownloadState.Idle -> {
                    Button(onClick = onDownload) {
                        Text("Download", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateDownloadState.Downloading -> {
                    TextButton(onClick = onCancelDownload) {
                        Text("Cancel download")
                    }
                }
                is UpdateDownloadState.Ready -> {
                    Button(
                        onClick = {
                            UpdateInstaller.installApk(context, state.file)
                            onResetDownload()
                        }
                    ) {
                        Text("Install Now", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateDownloadState.Failed -> {
                    Button(
                        onClick = {
                            onResetDownload()
                            onDownload()
                        }
                    ) {
                        Text("Try Again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            when (downloadState) {
                is UpdateDownloadState.Downloading -> Unit
                is UpdateDownloadState.Failed -> {
                    TextButton(onClick = { onResetDownload(); onDismiss() }) {
                        Text("Later")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                }
            }
        }
    )
}
