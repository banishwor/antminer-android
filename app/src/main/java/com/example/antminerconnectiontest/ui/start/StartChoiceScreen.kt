package com.example.antminerconnectiontest.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.theme.CyberCyan
import com.example.antminerconnectiontest.theme.ThemeMode
import com.example.antminerconnectiontest.ui.AppUiState
import com.example.antminerconnectiontest.ui.DashboardViewModel

/**
 * First-open screen, shown only when nothing is set up yet (no local
 * miners saved, no start choice made). The user picks where to START —
 * nothing is scanned or fetched locally until they choose "Start scan".
 * Either choice stays reversible from inside the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChoiceScreen(
    viewModel: DashboardViewModel,
    uiState: AppUiState
) {
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
                                text = "Welcome — how do you want to start?",
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
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pick a starting point. Nothing runs until you choose — and you can switch later: remote viewers can add local miners when they are on site, and local users can open Remote View any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StartOptionCard(
                icon = Icons.Default.Cloud,
                accent = CyberCyan,
                title = "Start as Remote View",
                description = "See all your miners from anywhere through Cloud Relay. No local network scan. If cloud relay is not set up yet, you will enter your user name next.",
                actionLabel = "GO REMOTE",
                onClick = viewModel::chooseRemoteStart
            )

            StartOptionCard(
                icon = Icons.Default.Radar,
                accent = MaterialTheme.colorScheme.primary,
                title = "Start Scan",
                description = "Search this Wi-Fi network for Antminers and add them to your dashboard. Best when you are at the miner location.",
                actionLabel = "SCAN THIS NETWORK",
                onClick = viewModel::chooseScanStart
            )

            Text(
                text = "This choice is only your starting point — it is asked once and both modes stay available afterwards.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StartOptionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(accent.copy(alpha = 0.55f))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
