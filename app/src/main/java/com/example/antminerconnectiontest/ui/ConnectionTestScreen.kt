package com.example.antminerconnectiontest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTestScreen(
    viewModel: ConnectionTestViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val isTesting = state.isTesting

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Antminer API Test",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
            // Description banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Verify HTTP Digest Authentication and inspect raw JSON data across all 6 official Antminer endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Connection Credentials Form Card
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Miner IP Address
                    OutlinedTextField(
                        value = state.ipAddress,
                        onValueChange = viewModel::onIpAddressChanged,
                        label = { Text("Miner IP Address") },
                        placeholder = { Text("192.168.1.2") },
                        leadingIcon = {
                            Icon(Icons.Default.Lan, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Username
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = { Text("Username") },
                        placeholder = { Text("root") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Password") },
                        placeholder = { Text("Enter miner password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = viewModel::togglePasswordVisibility) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.testAllEndpoints()
                            }
                        ),
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!state.generalError.isNullOrBlank()) {
                        Text(
                            text = state.generalError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // TEST ALL ENDPOINTS Action Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testAllEndpoints()
                        },
                        enabled = !isTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "TESTING ENDPOINTS...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Text(
                                "TEST ALL ENDPOINTS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Summary Banner (if finished)
            if (!state.summaryMessage.isNullOrBlank()) {
                val allSuccessful = state.endpointItems.all { it.status == EndpointStatus.SUCCESS }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allSuccessful) Color(0xFF1E392A) else Color(0xFF3E2D1A)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (allSuccessful) Color(0xFF4CAF50) else Color(0xFFFFB74D)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (allSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (allSuccessful) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.summaryMessage ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (allSuccessful) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        )
                    }
                }
            }

            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API Endpoints (6)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (isTesting && state.currentTestingPath != null) {
                    Text(
                        text = "Testing: ${state.currentTestingPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Endpoints list
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.endpointItems.forEach { item ->
                    EndpointItemCard(
                        item = item,
                        onToggleExpand = { viewModel.toggleEndpointExpanded(item.path) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EndpointItemCard(
    item: EndpointTestItem,
    onToggleExpand: () -> Unit
) {
    val borderColor = when (item.status) {
        EndpointStatus.UNTESTED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        EndpointStatus.TESTING -> MaterialTheme.colorScheme.primary
        EndpointStatus.SUCCESS -> Color(0xFF4CAF50)
        EndpointStatus.FAILURE -> MaterialTheme.colorScheme.error
    }

    val containerColor = when (item.status) {
        EndpointStatus.UNTESTED -> MaterialTheme.colorScheme.surface
        EndpointStatus.TESTING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        EndpointStatus.SUCCESS -> Color(0xFF14241B)
        EndpointStatus.FAILURE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Status Icon, Path, Status Info, Expand Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                when (item.status) {
                    EndpointStatus.UNTESTED -> {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Untested",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    EndpointStatus.TESTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    EndpointStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    EndpointStatus.FAILURE -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Path & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.path,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    when (item.status) {
                        EndpointStatus.UNTESTED -> {
                            Text(
                                text = "Pending test",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        EndpointStatus.TESTING -> {
                            Text(
                                text = "Requesting...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        EndpointStatus.SUCCESS -> {
                            Text(
                                text = "HTTP ${item.statusCode ?: 200} OK • ${item.durationMs ?: 0} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA5D6A7),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        EndpointStatus.FAILURE -> {
                            Text(
                                text = (if (item.statusCode != null) "HTTP ${item.statusCode} • " else "") +
                                        (item.errorMessage ?: "Failed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Expand / Collapse Chevron
                if (item.status == EndpointStatus.SUCCESS || item.status == EndpointStatus.FAILURE) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (item.isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            // Expandable Response Area
            AnimatedVisibility(
                visible = item.isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(
                        color = borderColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (item.status == EndpointStatus.SUCCESS && !item.formattedResponse.isNullOrBlank()) {
                        Text(
                            text = "Response Payload:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC8E6C9),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 260.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0C140F))
                                    .border(1.dp, Color(0xFF233F2E), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = item.formattedResponse,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }
                    } else if (item.status == EndpointStatus.FAILURE) {
                        Text(
                            text = "Error Details:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = (item.errorMessage.orEmpty() + "\n" + item.errorDetails.orEmpty()).trim(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
