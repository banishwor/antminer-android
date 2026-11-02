package com.example.antminerconnectiontest.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MinerDashboardData
import com.example.antminerconnectiontest.domain.model.MinerProfile
import com.example.antminerconnectiontest.theme.*

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
    cloudConfig: com.example.antminerconnectiontest.domain.model.CloudSyncConfig = com.example.antminerconnectiontest.domain.model.CloudSyncConfig(),
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
    onDisconnect: () -> Unit
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
    var isCloudExpanded by remember { mutableStateOf(cloudConfig.isSyncEnabled || cloudConfig.userName.isNotBlank()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Device & Settings",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "System details, theme, cloud sync, and saved miners",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // Appearance / Theme Settings Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "App Theme",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
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
            }
        }

        // Cloud Relay & Multi-Site Sync Card (Google Sheets)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (relayEnabled && isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.outline
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCloudExpanded = !isCloudExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (relayEnabled && isCloudConnected) AntminerEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (relayEnabled && isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Cloud Farm Relay",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
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
                                text = "Remote multi-site monitoring via Google Sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { isCloudExpanded = !isCloudExpanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isCloudExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = isCloudExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        OutlinedTextField(
                            value = cloudUserInput,
                            onValueChange = { cloudUserInput = it },
                            label = { Text("Your User Name") },
                            placeholder = { Text("Enter your user name") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Relaying uploads your local miners under this user name, so you can watch them from anywhere in Remote View. Other people who know the name could see your miners' hashrate, temperature and status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // The ONE Relay switch: applies immediately.
                        // ON = upload local miners under this user name
                        // AND keep relaying 24/7 in the background.
                        // OFF = no uploads, background relay stopped.
                        // Viewing remote machines works either way.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Relay",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "On: your local miners are uploaded under your user name and relaying keeps running 24/7 in the background, even when the app is closed. Off: nothing is uploaded. Watching remote miners works either way.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                        }

                        // Test Result Badge
                        if (!cloudTestMessage.isNullOrBlank()) {
                            Surface(
                                color = if (isCloudConnected) AntminerEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isCloudConnected) AntminerEmerald else MaterialTheme.colorScheme.error
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onTestCloudConnection(cloudConfig.webhookUrl, cloudUserInput.trim()) },
                                enabled = !isTestingCloud && cloudUserInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingCloud) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    onSaveCloudConfig(
                                        cloudConfig.webhookUrl,
                                        cloudUserInput.trim(),
                                        relayEnabled
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // App Updates Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Updates",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val installedVersion = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                            } catch (_: Exception) {
                                "?"
                            }
                        }
                        Text(
                            text = "Installed version $installedVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !isCheckingForUpdate,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCheckingForUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", fontSize = 11.sp)
                        } else {
                            Text("Check for updates", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!updateCheckMessage.isNullOrBlank()) {
                    Text(
                        text = updateCheckMessage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Saved Miners & Device Switcher Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Miners (${if (profile == null) savedProfiles.size else savedProfiles.size.coerceAtLeast(1)})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = onAddNewMiner,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Miner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                val profilesToShow = if (savedProfiles.isEmpty()) listOfNotNull(profile) else savedProfiles
                if (profilesToShow.isEmpty()) {
                    Text(
                        text = "No local miners saved on this phone yet. When you are on a miner's Wi-Fi, add one from Home — it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                profilesToShow.forEach { item ->
                    val isActive = item.id == profile?.id
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProfile(item) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
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
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Edit Nickname/Location Button
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
                                    OutlinedButton(
                                        onClick = { onSelectProfile(item) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("SWITCH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onDeleteProfile(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

        // Miner-specific sections — only when a local miner is active.
        // A remote-only user has no miner identity to show here; the
        // theme, cloud/relay and update sections above are their
        // Device tab.
        if (profile != null) {
        // Miner Hardware Identity Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Hardware Identity",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                DeviceInfoRow("Miner Model", sys?.minerType ?: profile.displayName)
                DeviceInfoRow("Hostname", sys?.hostname ?: "Antminer")

                val serial = sys?.serialNumber ?: ""
                val displaySerial = if (isSerialMasked && serial.length > 8) "${serial.take(4)}••••${serial.takeLast(4)}" else serial
                DeviceInfoRow(
                    label = "Serial Number",
                    value = displaySerial.ifBlank { "Not reported" },
                    onToggleMask = { isSerialMasked = !isSerialMasked },
                    isMasked = isSerialMasked
                )

                val mac = sys?.mac ?: ""
                val displayMac = if (isMacMasked && mac.length > 8) "${mac.take(5)}••:••:${mac.takeLast(2)}" else mac
                DeviceInfoRow(
                    label = "MAC Address",
                    value = displayMac.ifBlank { "00:00:00:00:00:00" },
                    onToggleMask = { isMacMasked = !isMacMasked },
                    isMasked = isMacMasked
                )
            }
        }

        // Network Information Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Network Configuration",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                DeviceInfoRow("IP Address", sys?.ip?.ifBlank { profile.host } ?: profile.host)
                DeviceInfoRow("Subnet Mask", sys?.netmask?.ifBlank { "255.255.255.0" } ?: "255.255.255.0")
                DeviceInfoRow("Network Type", sys?.nettype ?: "DHCP")
                DeviceInfoRow("Network Device", sys?.netdevice ?: "eth0")
            }
        }

        // Firmware & System Info Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Firmware & OS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                DeviceInfoRow("Filesystem Version", sys?.filesystemVersion ?: "FR-1.17(250624-S19k Pro)")
                DeviceInfoRow("Kernel Version", sys?.kernelVersion ?: "Linux 4.9.113 SMP")
                DeviceInfoRow("System Mode", sys?.systemMode ?: "GNU/Linux")
                DeviceInfoRow("Firmware Type", sys?.firmwareType ?: "Release")
            }
        }

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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.fillMaxWidth(),
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
