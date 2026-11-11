package com.example.antminerconnectiontest.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.data.scanner.DiscoveryStatus
import com.example.antminerconnectiontest.data.scanner.DiscoveredMiner
import com.example.antminerconnectiontest.theme.*
import com.example.antminerconnectiontest.ui.AppUiState
import com.example.antminerconnectiontest.ui.DashboardViewModel
import com.example.antminerconnectiontest.ui.SetupFormState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: DashboardViewModel,
    uiState: AppUiState,
    /** Shows a way back to the main shell's Home. Always set since
     *  v0.1.9 — setup is only ever entered from inside the shell. */
    onExitSetup: (() -> Unit)? = null
) {
    val form = uiState.setupForm
    val scanner = uiState.scannerState
    val focusManager = LocalFocusManager.current
    var isManualExpanded by remember { mutableStateOf(false) }
    val isWifi = uiState.isWifiConnected
    var batchPassword by remember { mutableStateOf("root") }
    var isBatchPasswordVisible by remember { mutableStateOf(false) }

    // NO auto-scan on open: a subnet scan only starts from an explicit
    // user action — the Start Scan button below. (Auto-connect of KNOWN
    // miners is not a scan and lives elsewhere; this screen used to fire
    // a scan from a LaunchedEffect the moment it composed on Wi-Fi.)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ANTMINER MONITOR",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Local Hardware Discovery",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleThemeMode) {
                        Icon(
                            imageVector = if (uiState.themeMode == ThemeMode.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Back to Home (setup is a flow inside the one shell)
            if (onExitSetup != null) {
                TextButton(
                    onClick = onExitSetup,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back to Home", fontWeight = FontWeight.Bold)
                }
            }

            // Mobile Network Notice Banner (if not on Wi-Fi)
            if (!isWifi) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(accentWarn().copy(alpha = 0.6f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentWarn().copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = accentWarn(),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mobile Network Active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Local subnet scanning is disabled on mobile data. Connect to your local Wi-Fi to scan miners. Google Sheets Cloud Relay remains active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Scanner Status Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = if (isWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (!isWifi) "Scanner Standby (Wi-Fi Required)" else if (scanner.isScanning) "Scanning Subnet for Miners..." else if (scanner.scannedCount == 0) "Scanner Idle" else "Subnet Scan Finished",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (!isWifi) "Connect to Wi-Fi to scan local miners" else if (scanner.scannedCount == 0 && !scanner.isScanning) "Tap Start Scan to search this network" else "${scanner.scannedCount}/${scanner.totalCount.coerceAtLeast(254)} IPs scanned • ${scanner.discoveredMiners.size} Antminers found",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (scanner.isScanning) {
                            OutlinedButton(
                                onClick = viewModel::stopNetworkScan,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Stop", color = accentWarn(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = viewModel::startNetworkScan,
                                enabled = isWifi,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (scanner.scannedCount == 0) "Start Scan" else "Rescan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (scanner.isScanning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (scanner.totalCount > 0) scanner.scannedCount.toFloat() / scanner.totalCount else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    // "Find moved miners" (v0.5.4): the DHCP-recovery
                    // scan for KNOWN miners whose address changed. Runs
                    // only from this tap — never automatically.
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "A known miner moved to a new IP?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiState.movedMinerScanProgress
                                    ?: "Searches this Wi-Fi for your known miners and updates the one that moved. Unknown miners are ignored.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = viewModel::findMovedMiners,
                            enabled = isWifi && !uiState.isFindingMovedMiners,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (uiState.isFindingMovedMiners) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Searching…", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Find moved miners", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (uiState.movedMinerScanLines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            uiState.movedMinerScanLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Discovered Miners Section
            val unaddedMiners = scanner.discoveredMiners.filter { viewModel.findSavedProfileForMiner(it) == null }
            val alreadyAddedCount = scanner.discoveredMiners.size - unaddedMiners.size

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Antminers (${scanner.discoveredMiners.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (scanner.discoveredMiners.isNotEmpty()) {
                    Text(
                        text = "$alreadyAddedCount added • ${unaddedMiners.size} new",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Batch Connect All Card (if miners detected)
            if (scanner.discoveredMiners.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (unaddedMiners.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else accentOk().copy(alpha = 0.5f)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (unaddedMiners.isNotEmpty()) "Batch Setup (${unaddedMiners.size} new)" else "All Miners Added",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (unaddedMiners.isNotEmpty()) {
                                        "${scanner.discoveredMiners.size} detected on network • $alreadyAddedCount already in dashboard"
                                    } else {
                                        "All ${scanner.discoveredMiners.size} discovered miners are already saved."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (unaddedMiners.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentOk().copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✓ ALL SAVED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = accentOk(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (unaddedMiners.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = batchPassword,
                                    onValueChange = { batchPassword = it },
                                    label = { Text("Common Password", fontSize = 11.sp) },
                                    visualTransformation = if (isBatchPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isBatchPasswordVisible = !isBatchPasswordVisible }, modifier = Modifier.size(24.dp)) {
                                            Icon(
                                                imageVector = if (isBatchPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )

                                Button(
                                    onClick = { viewModel.connectAllDiscoveredMiners(pass = batchPassword) },
                                    enabled = !uiState.isBatchConnecting && isWifi,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    if (uiState.isBatchConnecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adding...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Connect All (${unaddedMiners.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (uiState.isBatchConnecting) {
                                LinearProgressIndicator(
                                    progress = { uiState.batchConnectProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                uiState.batchConnectMessage?.let { msg ->
                                    Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            if (scanner.discoveredMiners.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (scanner.isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Searching local network for Antminer hardware...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (!isWifi) "Connect to Wi-Fi to scan local miners" else if (scanner.scannedCount == 0) "Nothing scanned yet — tap Start Scan to search this Wi-Fi for Antminers." else "No miners detected on this subnet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scanner.discoveredMiners.forEach { miner ->
                        val savedProfile = viewModel.findSavedProfileForMiner(miner)
                        val isAlreadyAdded = savedProfile != null

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isAlreadyAdded) accentOk().copy(alpha = 0.5f)
                                    else if (miner.status == DiscoveryStatus.CONFIRMED_ANTMINER) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = miner.modelName ?: "Antminer S19k Pro",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isAlreadyAdded) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = accentOk().copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "✓ ADDED",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp,
                                                    color = accentOk(),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isAlreadyAdded && savedProfile?.nickname?.isNotBlank() == true) {
                                            "${miner.ip} • ${savedProfile.nickname}"
                                        } else {
                                            miner.ip
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = when {
                                            isAlreadyAdded -> if (savedProfile?.workerName?.isNotBlank() == true) "Worker: ${savedProfile.workerName}" else "● Active in Dashboard"
                                            miner.status == DiscoveryStatus.CONFIRMED_ANTMINER -> "● Confirmed Antminer"
                                            else -> "Authentication required"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isAlreadyAdded -> accentOk()
                                            miner.status == DiscoveryStatus.CONFIRMED_ANTMINER -> accentOk()
                                            else -> accentWarn()
                                        },
                                        fontSize = 10.sp
                                    )
                                }

                                if (isAlreadyAdded && savedProfile != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.openDiscoveredMiner(savedProfile, miner.ip) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("OPEN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.selectScannerMiner(miner) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("CONNECT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Manual IP Fallback (Expandable Card)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isManualExpanded = !isManualExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Enter IP Manually",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Connect to static IP, VLAN, or custom subnet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { isManualExpanded = !isManualExpanded }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isManualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = isManualExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            OutlinedTextField(
                                value = form.ip,
                                onValueChange = viewModel::onSetupIpChanged,
                                label = { Text("Miner IP Address") },
                                placeholder = { Text("192.168.1.2") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = form.username,
                                onValueChange = viewModel::onSetupUsernameChanged,
                                label = { Text("Username") },
                                placeholder = { Text("root") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = form.password,
                                onValueChange = viewModel::onSetupPasswordChanged,
                                label = { Text("Password") },
                                placeholder = { Text("Miner password") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                visualTransformation = if (form.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = viewModel::toggleSetupPasswordVisibility) {
                                        Icon(
                                            imageVector = if (form.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    viewModel.connectAndVerifyManual()
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!form.errorMessage.isNullOrBlank()) {
                                Text(text = form.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.connectAndVerifyManual()
                                },
                                enabled = !form.isVerifying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (form.isVerifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("VERIFYING MINER...")
                                } else {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("VERIFY & CONNECT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Connect Discovered Miner Dialog
        val selectedMiner = uiState.selectedScannerMiner
        if (selectedMiner != null) {
            AlertDialog(
                onDismissRequest = { viewModel.selectScannerMiner(null) },
                title = {
                    Text("Connect to ${selectedMiner.modelName ?: "Antminer"}", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("IP: ${selectedMiner.ip}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = uiState.scannerUsernameInput,
                            onValueChange = viewModel::onScannerUsernameChanged,
                            label = { Text("Username") },
                            placeholder = { Text("root") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.scannerPasswordInput,
                            onValueChange = viewModel::onScannerPasswordChanged,
                            label = { Text("Password") },
                            placeholder = { Text("Enter miner password") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            visualTransformation = if (uiState.isScannerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleScannerPasswordVisibility) {
                                    Icon(
                                        imageVector = if (uiState.isScannerPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!form.errorMessage.isNullOrBlank()) {
                            Text(text = form.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.connectDiscoveredMiner(selectedMiner, uiState.scannerUsernameInput, uiState.scannerPasswordInput) },
                        enabled = !form.isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (form.isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...")
                        } else {
                            Text("Verify & Connect", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.selectScannerMiner(null) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
