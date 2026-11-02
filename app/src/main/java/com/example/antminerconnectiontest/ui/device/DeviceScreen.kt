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
import com.example.antminerconnectiontest.ui.components.SourceChip

/**
 * The Device tab, redesigned as "Option C — Minimal List" in v0.2.3
 * (user-picked from the mockups): no cards — one flat list with
 * hairline dividers, generous rows and outlined icon chips, in the
 * Violet theme (Light + AMOLED Dark). Same functions as before, new
 * presentation:
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
    // Appearance style (v0.3.0): Violet (default) or Liquid Glass,
    // plus this device's glass capability tier for the helper line.
    appStyle: AppStyle = AppStyle.VIOLET,
    glassTier: GlassTier = GlassTier.LITE,
    onSetAppStyle: (AppStyle) -> Unit = {},
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
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "v$installedVersion",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedMachineName.trim().firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
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

        // ── Miners: Add a miner (ALWAYS) + saved miners ──────────────
        DeviceSectionLabel("Miners")
        // Add a miner lives here permanently (v0.2.3, user's words):
        // always present whatever the fleet state — it is an action,
        // not a setting. It opens the same add-miner flow as Home's
        // card and the fleet header's "+ Add Unit".
        DeviceListRow(
            icon = Icons.Default.Add,
            title = "Add a miner",
            subtitle = "Scan or enter an IP — on the miner's Wi-Fi, known miners connect by themselves",
            onClick = onAddNewMiner,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val profilesToShow = if (savedProfiles.isEmpty()) listOfNotNull(profile) else savedProfiles
        if (profilesToShow.isEmpty()) {
            Text(
                text = "No local miners saved on this phone yet. When you are on a miner's Wi-Fi, add one — it will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        } else {
            Text(
                text = "Saved on this phone (${profilesToShow.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
            )
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── Relay & Cloud ────────────────────────────────────────────
        DeviceSectionLabel("Relay & Cloud")
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
                            color = if (isCloudConnected) AntminerEmerald.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isCloudConnected) "CONNECTED" else "SYNC ON",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudConnected) AntminerEmerald else WarningAmber,
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
            Switch(
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
            Switch(
                checked = relayOnlyMode,
                onCheckedChange = onSetRelayOnlyMode
            )
        }
        Text(
            text = "Finish setup first — relay name and miners — then flip this on and leave the phone at the site.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
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
                        color = if (isCloudConnected) AntminerEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.error
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
                                tint = if (isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.error,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Appearance ───────────────────────────────────────────────
        DeviceSectionLabel("Appearance")
        // Style (v0.3.0): ONE compact row — label + two-option
        // selector — per the user's instruction that the style change
        // must not take much vertical space. The Dark/Light/System
        // mode picker below stays exactly as it was and applies to
        // whichever style is selected.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconChip(icon = Icons.Default.AutoAwesome)
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Style",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (appStyle) {
                        AppStyle.VIOLET -> "Violet — the solid theme"
                        AppStyle.LIQUID_GLASS ->
                            if (glassTier == GlassTier.LITE) {
                                "Liquid Glass — lighter glass on this phone (no live blur)"
                            } else {
                                "Liquid Glass — frosted panels over a wallpaper"
                            }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeOptionButton(
                    label = "Violet",
                    icon = Icons.Default.Palette,
                    isSelected = appStyle == AppStyle.VIOLET,
                    onClick = { onSetAppStyle(AppStyle.VIOLET) }
                )
                ThemeOptionButton(
                    label = "Glass",
                    icon = Icons.Default.BlurOn,
                    isSelected = appStyle == AppStyle.LIQUID_GLASS,
                    onClick = { onSetAppStyle(AppStyle.LIQUID_GLASS) }
                )
            }
        }
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
                    text = "${if (appStyle == AppStyle.LIQUID_GLASS) "Liquid Glass" else "Violet"} · AMOLED black in Dark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeOptionButton(
                label = "Dark",
                icon = Icons.Default.DarkMode,
                isSelected = themeMode == ThemeMode.DARK,
                onClick = { onSetThemeMode(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
            ThemeOptionButton(
                label = "Light",
                icon = Icons.Default.LightMode,
                isSelected = themeMode == ThemeMode.LIGHT,
                onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOptionButton(
                label = "System",
                icon = Icons.Default.SettingsBrightness,
                isSelected = themeMode == ThemeMode.SYSTEM,
                onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── App ──────────────────────────────────────────────────────
        DeviceSectionLabel("App")
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

        // Miner-specific sections — only when a local miner is active.
        // A remote-only user has no miner identity to show here; the
        // miners, cloud/relay, theme and update sections above are
        // their Device tab.
        if (profile != null) {
            // ── Hardware Identity ──
            DeviceSectionLabel("Hardware Identity")
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
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
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
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
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
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

/** Option C section label: small, uppercase, letterspaced, muted. */
@Composable
private fun DeviceSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp)
    )
}

/** Option C icon chip: transparent fill, hairline border, 34 dp. */
@Composable
private fun DeviceIconChip(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/**
 * Flat theme option (v0.2.4): no solid fill — the selected option is
 * a light accent tint with accent text; unselected options are plain
 * muted text on the page background.
 */
@Composable
private fun ThemeOptionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
