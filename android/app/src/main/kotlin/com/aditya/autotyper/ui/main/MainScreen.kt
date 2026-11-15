package com.aditya.autotyper.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.autotyper.discovery.DiscoveredDevice
import com.aditya.autotyper.discovery.DiscoveryStatus
import com.aditya.autotyper.network.CompanionSyncState
import com.aditya.autotyper.network.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AutoTyperViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val discoveryStatus by viewModel.discoveryStatus.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()

    var manualIp by remember { mutableStateOf("127.0.0.1") }
    var manualPort by remember { mutableStateOf("8080") }
    var showManualSetup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⚡ AutoTyper",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (connectionState) {
                                is ConnectionState.Connected -> Color(0xFF10B981).copy(alpha = 0.2f)
                                is ConnectionState.Connecting -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else -> Color(0xFF6B7280).copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connected -> "ONLINE"
                                    is ConnectionState.Connecting -> "CONNECTING"
                                    else -> "OFFLINE"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> Color(0xFF10B981)
                                    is ConnectionState.Connecting -> Color(0xFFF59E0B)
                                    else -> Color(0xFF9CA3AF)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Connection Banner Card
            item {
                ConnectionStatusCard(
                    connectionState = connectionState,
                    discoveryStatus = discoveryStatus,
                    onRescan = { viewModel.startScan() },
                    onDisconnect = { viewModel.disconnect() }
                )
            }

            // 2. Discovered Devices (mDNS / ZeroConf)
            item {
                Text(
                    text = "DISCOVERED COMPANIONS (${discoveredDevices.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (discoveredDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                            Text(
                                "Scanning local network for KeyboardPunch...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Make sure your laptop and phone are on the same Wi-Fi / Hotspot",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(discoveredDevices) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        isConnected = connectionState is ConnectionState.Connected &&
                                (connectionState as ConnectionState.Connected).host == device.host,
                        onConnect = { viewModel.connectToDevice(device) }
                    )
                }
            }

            // 3. Companion Live Status & Typing Telemetry
            if (connectionState is ConnectionState.Connected) {
                item {
                    CompanionTelemetryCard(
                        syncState = syncState,
                        onStop = { viewModel.stop() }
                    )
                }
            }

            // 4. Manual IP / ADB Reverse Fallback
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Manual Connection / USB ADB",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "For isolated corporate Wi-Fi or USB tethering",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            TextButton(onClick = { showManualSetup = !showManualSetup }) {
                                Text(if (showManualSetup) "Hide" else "Configure")
                            }
                        }

                        AnimatedVisibility(visible = showManualSetup) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualIp,
                                        onValueChange = { manualIp = it },
                                        label = { Text("IP / Host") },
                                        modifier = Modifier.weight(2f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = manualPort,
                                        onValueChange = { manualPort = it },
                                        label = { Text("Port") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Button(
                                    onClick = {
                                        val portInt = manualPort.toIntOrNull() ?: 8080
                                        viewModel.manualConnect(manualIp, portInt)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Connect via IP")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    discoveryStatus: DiscoveryStatus,
    onRescan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> Color(0xFF064E3B).copy(alpha = 0.4f)
                is ConnectionState.Connecting -> Color(0xFF78350F).copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is ConnectionState.Connected -> Color(0xFF10B981)
                                    is ConnectionState.Connecting -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }
                            )
                    )
                    Column {
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "Connected to ${connectionState.deviceName}"
                                is ConnectionState.Connecting -> "Connecting to Companion..."
                                is ConnectionState.Error -> "Connection Issue"
                                else -> "Not Connected"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "WebSocket active on ${connectionState.host}:8080"
                                is ConnectionState.Connecting -> "Establishing real-time channel..."
                                is ConnectionState.Error -> connectionState.message
                                else -> "Waiting for auto-discovery signal..."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connectionState is ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRescan,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rescan Wi-Fi")
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceItem(
    device: DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                Color(0xFF10B981).copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${device.host}:${device.port}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981)
                ) {
                    Text(
                        "ACTIVE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
fun CompanionTelemetryCard(
    syncState: CompanionSyncState,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LAPTOP COMPANION STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (syncState.isTyping) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444)
                    ) {
                        Text(
                            "TYPING LIVE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                    ) {
                        Text(
                            "IDLE / READY",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            if (syncState.isTyping) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Progress: ${syncState.typedChars} / ${syncState.totalChars} chars",
                            fontSize = 12.sp
                        )
                        Text(
                            "${syncState.progressPercent.toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (syncState.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Emergency Stop Typing", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speed: ${syncState.wpm} WPM", fontSize = 13.sp)
                    Text("Delay: ${syncState.focusDelay}s", fontSize = 13.sp)
                    Text(
                        if (syncState.smartIndent) "Smart Indent: ON" else "Smart Indent: OFF",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
