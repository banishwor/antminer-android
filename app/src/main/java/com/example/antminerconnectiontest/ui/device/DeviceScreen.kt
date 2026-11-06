package com.example.antminerconnectiontest.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.CloudSyncConfig
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.components.IosSegmentedControl
import com.example.antminerconnectiontest.ui.components.IosSwitch
import com.example.antminerconnectiontest.ui.components.SourceChip

/**
 * The Device tab: born as "Option C — Minimal List" in v0.2.3 and
 * restyled to the V3 Grouped Sheets Settings in v0.4.0 (group labels,
 * panels, iOS switches and segmented controls) — the app's only
 * design since the Violet style was removed in v0.5.0:
 *
 *   header (title + version pill + refresh)
 *   → selected-machine block (when the selection isn't the active
 *     local profile)
 *   → MINERS: the permanent "Add a miner" entry (ALWAYS present,
 *     whatever the fleet state) + the saved miners on this phone
 *   → RELAY & CLOUD: the one Relay switch + expandable cloud setup
 *   → APPEARANCE: theme picker
 *   → APP: updates (manual check)
 *   → with an active local profile: hardware identity, network,
 *     firmware & OS, disconnect — as flat info lists.
 *
 * v0.2.4 (user's phone feedback on v0.2.3: the page read chunkier
 * than the mockup, and Relay took too much space): the Relay section
 * now starts COLLAPSED on every visit and its expanded form is one
 * short helper line + compact flat actions; the theme picker is a
 * flat text-option row (light tint on the selected option, no solid
 * fill); Check-for-updates / Test Connection / Save are flat text
 * actions; the selected-machine avatar is a tinted outlined chip
 * instead of a solid block.
 */
@Composable
fun DeviceScreen(
    // Nullable since v0.1.8: a Remote View starter with no local miner
    // connected still gets this tab (cloud & relay settings and App
    // Updates matter most to them). The miner-specific sections below
    // (saved-miner active state, hardware identity, network, firmware,
    // disconnect) render only when a profile is active.
    profile: MinerProfile?,
    savedProfiles: List<MinerProfile>,
    data: MinerDashboardData?,
    themeMode: ThemeMode,
    isRefreshing: Boolean,
    cloudConfig: CloudSyncConfig = CloudSyncConfig(),
    isTestingCloud: Boolean = false,
    cloudTestMessage: String? = null,
    isCloudConnected: Boolean = false,
    onManualRefresh: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSelectProfile: (MinerProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onUpdateNicknameAndLocation: (String, String, String) -> Unit = { _, _, _ -> },
    onSaveCloudConfig: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onTestCloudConnection: (String, String) -> Unit = { _, _ -> },
    updateCheckMessage: String? = null,
    isCheckingForUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onAddNewMiner: () -> Unit,
    onDisconnect: () -> Unit,
    /** Bottom dock clearance (v0.5.0): appended inside the scroll column. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    // Relay-only mode (v0.3.0): the switch that swaps the whole app
    // for the relay status screen on a phone left at a miner site.
    relayOnlyMode: Boolean = false,
    onSetRelayOnlyMode: (Boolean) -> Unit = {},
    // Selected-machine identity (v0.2.0): when the fleet selection is a
    // machine other than the active local profile (typically a cloud
    // machine), a compact identity block follows the selection here.
    // Null name = the selection IS the active profile, whose full
    // identity lists already render below.
    selectedMachineName: String? = null,
    selectedMachineWorker: String = "",
    selectedMachineModel: String = "",
    selectedMachineFirmware: String = "",
    selectedMachineSerial: String = "",
    selectedMachineIp: String = "",
    selectedMachineIsRemote: Boolean = false
) {
    val sys = data?.systemInfo
    var isMacMasked by remember { mutableStateOf(false) }
    var isSerialMasked by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<MinerProfile?>(null) }
    var editNicknameText by remember { mutableStateOf("") }
    var editLocationText by remember { mutableStateOf("") }

    // Cloud form states — one Relay switch (see CloudSyncConfig).
    var cloudUserInput by remember(cloudConfig) { mutableStateOf(cloudConfig.userName) }
    var relayEnabled by remember(cloudConfig) { mutableStateOf(cloudConfig.isSyncEnabled) }
    // Collapsed by default on EVERY visit (v0.2.4): plain remember, so
    // leaving the tab resets it — the expanded state is never kept.
    var isCloudExpanded by remember { mutableStateOf(false) }

    // Grouped Sheets (v0.4.0): this screen renders the V3 mockup's
    // Settings — group labels, panels, iOS switches and segmented
    // controls.
    val context = androidx.compose.ui.platform.LocalContext.current
    val installedVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header: title, version pill, refresh ─────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This phone, the app, and the selected machine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "v$installedVersion",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            IconButton(onClick = onManualRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Selected machine (identity follows the fleet selection) ──
        if (selectedMachineName != null) {
            Spacer(modifier = Modifier.height(18.dp))
            SettingsPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedMachineName.trim().firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedMachineName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        SourceChip(isRemote = selectedMachineIsRemote)
                    }
                    if (selectedMachineWorker.isNotBlank() &&
                        !selectedMachineWorker.equals(selectedMachineName, ignoreCase = true)
                    ) {
                        Text(
                            text = selectedMachineWorker,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (selectedMachineIsRemote) "Selected machine · via relay" else "Selected machine · on this phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedMachineModel.isNotBlank() || selectedMachineFirmware.isNotBlank() ||
                selectedMachineSerial.isNotBlank() || selectedMachineIp.isNotBlank()
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                if (selectedMachineModel.isNotBlank()) DeviceInfoRow("Miner Model", selectedMachineModel)
                if (selectedMachineFirmware.isNotBlank()) DeviceInfoRow("Firmware", selectedMachineFirmware)
                if (selectedMachineSerial.isNotBlank()) DeviceInfoRow("Serial Number", selectedMachineSerial)
                if (selectedMachineIp.isNotBlank()) DeviceInfoRow("IP Address", selectedMachineIp)
            }
            }
        }

        // ── Miners: Add a miner (ALWAYS) + saved miners ──────────────
        DeviceSectionLabel("Miners")
        // Add a miner lives here permanently (v0.2.3, user's words):
        // always present whatever the fleet state — it is an action,
        // not a setting. It opens the same add-miner flow as Home's
        // card and the fleet header's "+ Add Unit".
        SettingsPanel {
            DeviceListRow(
                icon = Icons.Default.Add,
                title = "Add a miner",
                subtitle = "Enter an IP — on the miner's Wi-Fi, known miners connect by themselves",
                onClick = onAddNewMiner,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        val profilesToShow = if (savedProfiles.isEmpty()) listOfNotNull(profile) else savedProfiles
        if (profilesToShow.isEmpty()) {
            Text(
                text = "No local miners saved on this phone yet. When you are on a miner's Wi-Fi, add one — it will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            SettingsPanel {
            profilesToShow.forEachIndexed { index, item ->
                val isActive = item.id == profile?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProfile(item) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceIconChip(
                        icon = Icons.Default.Speed,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.effectiveName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.host,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.location.isNotBlank() && item.location != "Default Location") {
                                Text(
                                    text = " • ${item.location}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            editingProfile = item
                            editNicknameText = item.nickname
                            editLocationText = item.location
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name/Location", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { onSelectProfile(item) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("SWITCH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { onDeleteProfile(item.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (index < profilesToShow.lastIndex) {
                    SettingsRowDivider()
                }
            }
            }
        }

        // ── Relay & Cloud ────────────────────────────────────────────
        DeviceSectionLabel("Relay & Cloud")
        SettingsPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isCloudExpanded = !isCloudExpanded }
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconChip(icon = Icons.Default.CloudSync)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cloud Farm Relay",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (relayEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (isCloudConnected) accentOk().copy(alpha = 0.2f) else accentWarn().copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isCloudConnected) "CONNECTED" else "SYNC ON",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudConnected) accentOk() else accentWarn(),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (cloudConfig.userName.isNotBlank()) {
                        "${cloudConfig.userName} · Remote multi-site monitoring via Google Sheets"
                    } else {
                        "Remote multi-site monitoring via Google Sheets"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // The ONE Relay switch: applies immediately.
            // ON = upload local miners under this user name AND keep
            // relaying 24/7 in the background. OFF = no uploads,
            // background relay stopped. Viewing remote machines works
            // either way.
            SettingsSwitch(
                checked = relayEnabled,
                onCheckedChange = { newValue ->
                    relayEnabled = newValue
                    onSaveCloudConfig(
                        cloudConfig.webhookUrl,
                        cloudUserInput.trim(),
                        newValue
                    )
                }
            )
            IconButton(onClick = { isCloudExpanded = !isCloudExpanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isCloudExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsRowDivider()
        // Relay only (v0.3.0): its own compact row UNDER the Relay
        // row, visible WITHOUT expanding the section — one switch +
        // one helper line. Flipping it swaps the whole app for the
        // relay status screen; the relay underneath (service,
        // auto-connect, uploads) does not change at all.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconChip(icon = Icons.Default.Upload)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Relay only",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This phone shows only the relay status screen. Relay & auto-connect keep running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingsSwitch(
                checked = relayOnlyMode,
                onCheckedChange = onSetRelayOnlyMode
            )
        }
        AnimatedVisibility(visible = isCloudExpanded) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cloudUserInput,
                    onValueChange = { cloudUserInput = it },
                    label = { Text("Your User Name") },
                    placeholder = { Text("Enter your user name") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth()
                )

                // One short helper line (v0.2.4) — replaces the two
                // long explainer paragraphs: uploads happen under this
                // name; relay runs 24/7 in the background when on;
                // watching remote miners works either way.
                Text(
                    text = "Uploads go out under this name; while Relay is on it keeps running 24/7 in the background. Watching remote miners works either way.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Test Result Badge
                if (!cloudTestMessage.isNullOrBlank()) {
                    Surface(
                        color = if (isCloudConnected) accentOk().copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCloudConnected) accentOk() else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCloudConnected) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isCloudConnected) accentOk() else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cloudTestMessage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Flat text actions (v0.2.4) — no outlined/solid boxes.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onTestCloudConnection(cloudConfig.webhookUrl, cloudUserInput.trim()) },
                        enabled = !isTestingCloud && cloudUserInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (isTestingCloud) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Connection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            onSaveCloudConfig(
                                cloudConfig.webhookUrl,
                                cloudUserInput.trim(),
                                relayEnabled
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
        // The mockup's state-aware note under the relay panel.
        Text(
            text = when {
                !relayEnabled ->
                    "Relay is off — nothing is uploaded from this phone. Watching remote miners still works."
                cloudConfig.userName.isBlank() ->
                    "Set your relay name above to start uploading."
                else ->
                    "Ready — relaying as ${cloudConfig.userName} · " +
                        "${profilesToShow.size} local ${if (profilesToShow.size == 1) "miner" else "miners"} uploading."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )

        // ── Appearance ───────────────────────────────────────────────
        // v0.5.0: no Style row anymore — Grouped Sheets is the only
        // design (the Violet style was removed). The Theme mode
        // picker below is unchanged: Dark / Light / System.
        DeviceSectionLabel("Appearance")
        SettingsPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconChip(icon = Icons.Default.Palette)
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Text(
                    text = "Theme",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grouped Sheets · AMOLED black in Dark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IosSegmentedControl(
            options = listOf("Dark", "Light", "System"),
            selectedIndex = when (themeMode) {
                ThemeMode.DARK -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.SYSTEM -> 2
            },
            onSelect = { index ->
                onSetThemeMode(
                    when (index) {
                        0 -> ThemeMode.DARK
                        1 -> ThemeMode.LIGHT
                        else -> ThemeMode.SYSTEM
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
        }
        Spacer(modifier = Modifier.height(2.dp))

        // ── App ──────────────────────────────────────────────────────
        DeviceSectionLabel("App")
        SettingsPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconChip(icon = Icons.Default.SystemUpdate)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App Updates",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Installed version $installedVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!updateCheckMessage.isNullOrBlank()) {
                    Text(
                        text = updateCheckMessage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Flat text action (v0.2.4) — no outlined box.
            TextButton(
                onClick = onCheckForUpdates,
                enabled = !isCheckingForUpdate,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (isCheckingForUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Checking...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Check for updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }

        // Miner-specific sections — only when a local miner is active.
        // A remote-only user has no miner identity to show here; the
        // miners, cloud/relay, theme and update sections above are
        // their Device tab.
        if (profile != null) {
            // ── Hardware Identity ──
            DeviceSectionLabel("Hardware Identity")
            SettingsPanel(contentPadding = PaddingValues(horizontal = 16.dp)) {
                DeviceInfoRow("Miner Model", sys?.minerType ?: profile.displayName)
                DeviceInfoDivider()
                DeviceInfoRow("Hostname", sys?.hostname ?: "Antminer")
                DeviceInfoDivider()

                val serial = sys?.serialNumber ?: ""
                val displaySerial = if (isSerialMasked && serial.length > 8) "${serial.take(4)}••••${serial.takeLast(4)}" else serial
                DeviceInfoRow(
                    label = "Serial Number",
                    value = displaySerial.ifBlank { "Not reported" },
                    onToggleMask = { isSerialMasked = !isSerialMasked },
                    isMasked = isSerialMasked
                )
                DeviceInfoDivider()

                val mac = sys?.mac ?: ""
                val displayMac = if (isMacMasked && mac.length > 8) "${mac.take(5)}••:••:${mac.takeLast(2)}" else mac
                DeviceInfoRow(
                    label = "MAC Address",
                    value = displayMac.ifBlank { "00:00:00:00:00:00" },
                    onToggleMask = { isMacMasked = !isMacMasked },
                    isMasked = isMacMasked
                )
            }

            // ── Network ──
            DeviceSectionLabel("Network Configuration")
            SettingsPanel(contentPadding = PaddingValues(horizontal = 16.dp)) {
                DeviceInfoRow("IP Address", sys?.ip?.ifBlank { profile.host } ?: profile.host)
                DeviceInfoDivider()
                DeviceInfoRow("Subnet Mask", sys?.netmask?.ifBlank { "255.255.255.0" } ?: "255.255.255.0")
                DeviceInfoDivider()
                DeviceInfoRow("Network Type", sys?.nettype ?: "DHCP")
                DeviceInfoDivider()
                DeviceInfoRow("Network Device", sys?.netdevice ?: "eth0")
            }

            // ── Firmware & OS ──
            DeviceSectionLabel("Firmware & OS")
            SettingsPanel(contentPadding = PaddingValues(horizontal = 16.dp)) {
                DeviceInfoRow("Filesystem Version", sys?.filesystemVersion ?: "FR-1.17(250624-S19k Pro)")
                DeviceInfoDivider()
                DeviceInfoRow("Kernel Version", sys?.kernelVersion ?: "Linux 4.9.113 SMP")
                DeviceInfoDivider()
                DeviceInfoRow("System Mode", sys?.systemMode ?: "GNU/Linux")
                DeviceInfoDivider()
                DeviceInfoRow("Firmware Type", sys?.firmwareType ?: "Release")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Disconnect Button
            OutlinedButton(
                onClick = onDisconnect,
                shape = panelShape(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                // 1 dp outline — that IS the M3 outlined-button
                // default border for an enabled button, spelled out.
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DISCONNECT FROM MINER", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        // Dock clearance (v0.5.0): the last rows can scroll fully
        // above the floating dock.
        Spacer(modifier = Modifier.height(bottomInset))
    }

    // Edit Nickname & Location Dialog
    val currentEdit = editingProfile
    if (currentEdit != null) {
        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = {
                Text("Edit Miner Identifier", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "IP: ${currentEdit.host}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = editNicknameText,
                        onValueChange = { editNicknameText = it },
                        label = { Text("Custom Nickname") },
                        placeholder = { Text("e.g. Warehouse S19k, Garage Unit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editLocationText,
                        onValueChange = { editLocationText = it },
                        label = { Text("Location / Farm Tag") },
                        placeholder = { Text("e.g. Home, Warehouse, Facility") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateNicknameAndLocation(currentEdit.id, editNicknameText.trim(), editLocationText.trim())
                        editingProfile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * A grouped panel wrapper (v0.4.0): a Surface in the panel colour
 * with the hairline border and the panel radius, content inset
 * 12 dp (16 dp for the dense info lists).
 */
@Composable
private fun SettingsPanel(
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = panelShape(),
        border = panelBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Divider between rows: the inset hairline of a grouped panel. */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 16.dp)
    )
}

/** The settings switch: the iOS pill. */
@Composable
private fun SettingsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
}

/** Section label: the mockup's group label — spec size and
 *  tracking, normal weight, indented to the panel content line. */
@Composable
private fun DeviceSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = GroupedSheetsSpec.GroupLabelTextSize,
        letterSpacing = GroupedSheetsSpec.GroupLabelLetterSpacing,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp)
    )
}

/** Row icon (Grouped Sheets): a plain glyph, no chip box. */
@Composable
private fun DeviceIconChip(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )
}

/** Option C flat list row: icon chip + title/subtitle + trailing slot. */
@Composable
private fun DeviceListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIconChip(icon = icon)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun DeviceInfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    onToggleMask: (() -> Unit)? = null,
    isMasked: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (onToggleMask != null) {
                IconButton(onClick = onToggleMask, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle mask",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
