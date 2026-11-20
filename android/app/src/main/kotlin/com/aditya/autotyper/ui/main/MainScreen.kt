package com.aditya.autotyper.ui.main

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.autotyper.discovery.DiscoveredDevice
import com.aditya.autotyper.discovery.DiscoveryStatus
import com.aditya.autotyper.network.CompanionSyncState
import com.aditya.autotyper.network.ConnectionState
import com.aditya.autotyper.vault.VaultSnippet
import kotlin.math.roundToInt

// Custom Glassmorphic Dark Theme Palette
private val DarkBg = Color(0xFF090D16)
private val CardBg = Color(0xFF131B2E).copy(alpha = 0.85f)
private val CardBorder = Color(0xFF1E293B)
private val ElectricCyan = Color(0xFF06B6D4)
private val ElectricIndigo = Color(0xFF6366F1)
private val ElectricPurple = Color(0xFF8B5CF6)
private val EmeraldGreen = Color(0xFF10B981)
private val AmberOrange = Color(0xFFF59E0B)
private val CrimsonRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AutoTyperViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Connection & Companion States
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val discoveryStatus by viewModel.discoveryStatus.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()

    // Editor & Settings States
    val code by viewModel.code.collectAsStateWithLifecycle()
    val wpm by viewModel.wpm.collectAsStateWithLifecycle()
    val focusDelay by viewModel.focusDelay.collectAsStateWithLifecycle()
    val smartIndent by viewModel.smartIndent.collectAsStateWithLifecycle()
    val oopsMode by viewModel.oopsMode.collectAsStateWithLifecycle()
    val humanJitter by viewModel.humanJitter.collectAsStateWithLifecycle()
    val fastPaste by viewModel.fastPaste.collectAsStateWithLifecycle()

    // Snippets Vault State
    val vaultSnippets by viewModel.vaultSnippets.collectAsStateWithLifecycle()

    // Navigation & Tab State: 0 = Puncher/Editor, 1 = Snippets Vault
    var currentTab by remember { mutableIntStateOf(0) }
    var vaultSearchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf("All") }
    var showAddSnippetDialog by remember { mutableStateOf(false) }

    // Countdown & Modals
    val isCountingDown by viewModel.isCountingDown.collectAsStateWithLifecycle()
    val countdownRemaining by viewModel.countdownRemaining.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var showSnippetsDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("127.0.0.1") }
    var manualPort by remember { mutableStateOf("8080") }

    // Haptic feedback helper
    fun triggerHaptic(durationMs: Long = 40) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // Calculations for word/char count and typing time estimate
    val charCount = code.length
    val wordCount = if (code.isBlank()) 0 else code.trim().split(Regex("\\s+")).size
    val lineCount = if (code.isEmpty()) 0 else code.count { it == '\n' } + 1
    val estimatedSeconds = if (charCount == 0 || wpm == 0) 0.0 else (charCount.toDouble() / (wpm * 5.0)) * 60.0

    // Filtered Vault Snippets
    val filteredVaultSnippets = remember(vaultSnippets, vaultSearchQuery, selectedTagFilter) {
        vaultSnippets.filter { snippet ->
            val matchesQuery = vaultSearchQuery.isBlank() ||
                    snippet.title.contains(vaultSearchQuery, ignoreCase = true) ||
                    snippet.language.contains(vaultSearchQuery, ignoreCase = true) ||
                    snippet.tags.any { it.contains(vaultSearchQuery, ignoreCase = true) } ||
                    snippet.code.contains(vaultSearchQuery, ignoreCase = true)

            val matchesTag = when (selectedTagFilter) {
                "All" -> true
                "⭐ Favorites" -> snippet.isFavorite
                else -> snippet.tags.any { it.equals(selectedTagFilter, ignoreCase = true) }
            }

            matchesQuery && matchesTag
        }
    }

    val allVaultTags = remember(vaultSnippets) {
        listOf("All", "⭐ Favorites") + vaultSnippets.flatMap { it.tags }.distinct().sorted()
    }

    Scaffold(
        modifier = modifier.background(DarkBg),
        containerColor = DarkBg,
        topBar = {
            Column(modifier = Modifier.background(DarkBg)) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "⚡ AutoTyper",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (connectionState) {
                                    is ConnectionState.Connected -> EmeraldGreen.copy(alpha = 0.2f)
                                    is ConnectionState.Connecting -> AmberOrange.copy(alpha = 0.2f)
                                    else -> Color.White.copy(alpha = 0.1f)
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
                                        is ConnectionState.Connected -> EmeraldGreen
                                        is ConnectionState.Connecting -> AmberOrange
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Text("🔄", fontSize = 16.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg
                    )
                )

                // Top Segmented Mode Switcher: ⚡ Puncher vs 🗄️ Vault
                PrimaryTabRow(
                    selectedTabIndex = currentTab,
                    containerColor = DarkBg,
                    contentColor = ElectricCyan,
                    divider = { HorizontalDivider(color = CardBorder) }
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = {
                            triggerHaptic(20)
                            currentTab = 0
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⚡", fontSize = 14.sp)
                                Text(
                                    "PUNCHER",
                                    fontWeight = if (currentTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = {
                            triggerHaptic(20)
                            currentTab = 1
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🗄️", fontSize = 14.sp)
                                Text(
                                    "VAULT (${vaultSnippets.size})",
                                    fontWeight = if (currentTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            // High-Impact Sticky Punch Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent
            ) {
                if (syncState.isTyping) {
                    Button(
                        onClick = {
                            triggerHaptic(80)
                            viewModel.stop()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛑", fontSize = 20.sp)
                            Text(
                                "STOP TYPING",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                } else if (currentTab == 0) {
                    Button(
                        onClick = {
                            triggerHaptic(50)
                            viewModel.startPunchSequence()
                        },
                        enabled = code.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(ElectricIndigo, ElectricPurple)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 20.sp)
                            Text(
                                if (connectionState is ConnectionState.Connected) "PUNCH TO LAPTOP" else "PUNCH (AUTO-CONNECT)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Quick Action on Vault Tab: Add Custom Snippet
                    Button(
                        onClick = {
                            triggerHaptic(30)
                            showAddSnippetDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF0284C7), ElectricCyan)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("➕", fontSize = 18.sp)
                            Text(
                                "ADD SNIPPET TO VAULT",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentTab == 0) {
            // ══════════════════════════════════════════════════════════
            // TAB 0: PUNCHER / CODE EDITOR VIEW
            // ══════════════════════════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // 1. Connection Banner Card
                item {
                    ConnectionStatusBanner(
                        connectionState = connectionState,
                        discoveryStatus = discoveryStatus,
                        onRescan = { viewModel.startScan() },
                        onDisconnect = { viewModel.disconnect() },
                        onOpenManualIp = { showManualIpDialog = true }
                    )
                }

                // 2. Discovered Devices (mDNS / ZeroConf) if not connected
                if (connectionState !is ConnectionState.Connected && discoveredDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "DISCOVERED COMPANIONS (${discoveredDevices.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                    items(discoveredDevices) { device ->
                        DiscoveredDeviceItem(
                            device = device,
                            isConnected = false,
                            onConnect = { viewModel.connectToDevice(device) }
                        )
                    }
                }

                // 3. Companion Live Telemetry & Progress (when typing)
                if (syncState.isTyping) {
                    item {
                        LiveTypingTelemetryCard(
                            syncState = syncState,
                            onStop = { viewModel.stop() }
                        )
                    }
                }

                // 4. Code Editor Section
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CODE TO PUNCH",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = ElectricCyan
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Paste from clipboard
                                    SmallActionButton(label = "📋 Paste") {
                                        triggerHaptic(30)
                                        val text = clipboardManager.getText()?.text ?: ""
                                        if (text.isNotEmpty()) viewModel.setCode(text)
                                    }
                                    // Vault Tab quick switch
                                    SmallActionButton(label = "🗄️ Vault") {
                                        triggerHaptic(30)
                                        currentTab = 1
                                    }
                                    // Clear
                                    if (code.isNotEmpty()) {
                                        SmallActionButton(label = "🧹 Clear", isDanger = true) {
                                            triggerHaptic(30)
                                            viewModel.clearCode()
                                        }
                                    }
                                }
                            }

                            // Monospaced Code Textarea
                            OutlinedTextField(
                                value = code,
                                onValueChange = { viewModel.setCode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 320.dp),
                                placeholder = {
                                    Text(
                                        "Paste or write code here to punch into laptop...\n\ne.g. Python, Java, C++, SEB answers, Google Docs notes",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color.White
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg,
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = CardBorder
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Stats & Estimated Duration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$charCount chars • $wordCount words • $lineCount lines",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                if (charCount > 0) {
                                    Text(
                                        "~${String.format("%.1f", estimatedSeconds)}s at $wpm WPM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Speed (WPM) Controls
                item {
                    SpeedControlCard(
                        wpm = wpm,
                        onWpmChange = {
                            triggerHaptic(20)
                            viewModel.setWpm(it)
                        }
                    )
                }

                // 6. Focus Delay Controls
                item {
                    FocusDelayCard(
                        delaySec = focusDelay,
                        onDelayChange = {
                            triggerHaptic(20)
                            viewModel.setFocusDelay(it)
                        }
                    )
                }

                // 7. Powerful Superpower Toggles
                item {
                    SuperpowerTogglesCard(
                        smartIndent = smartIndent,
                        onSmartIndentChange = { viewModel.setSmartIndent(it) },
                        oopsMode = oopsMode,
                        onOopsModeChange = { viewModel.setOopsMode(it) },
                        humanJitter = humanJitter,
                        onHumanJitterChange = { viewModel.setHumanJitter(it) },
                        fastPaste = fastPaste,
                        onFastPasteChange = { viewModel.setFastPaste(it) }
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        } else {
            // ══════════════════════════════════════════════════════════
            // TAB 1: OFFLINE SNIPPETS VAULT VIEW
            // ══════════════════════════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = vaultSearchQuery,
                        onValueChange = { vaultSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Search snippets by title, tag, code...", fontSize = 13.sp, color = Color.Gray)
                        },
                        leadingIcon = {
                            Text("🔍", fontSize = 14.sp)
                        },
                        trailingIcon = {
                            if (vaultSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { vaultSearchQuery = "" }) {
                                    Text("✕", fontSize = 14.sp, color = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Filter Tags Horizontal Scroll
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allVaultTags.forEach { tag ->
                            val isSelected = selectedTagFilter == tag
                            Surface(
                                modifier = Modifier.clickable {
                                    triggerHaptic(20)
                                    selectedTagFilter = tag
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ElectricIndigo.copy(alpha = 0.35f) else CardBorder,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ElectricIndigo) else null
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Snippets Count Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OFFLINE TEMPLATES (${filteredVaultSnippets.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = "Zero Internet Required",
                            fontSize = 11.sp,
                            color = EmeraldGreen
                        )
                    }
                }

                // Empty State if no match
                if (filteredVaultSnippets.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🔎", fontSize = 36.sp)
                                Text(
                                    "No snippets found",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Try clearing the search or filter tag.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Snippets Cards
                    items(filteredVaultSnippets, key = { it.id }) { snippet ->
                        VaultSnippetCard(
                            snippet = snippet,
                            onPunch = {
                                triggerHaptic(50)
                                viewModel.punchCustomSnippet(snippet.code)
                            },
                            onLoad = {
                                triggerHaptic(30)
                                viewModel.setCode(snippet.code)
                                currentTab = 0
                            },
                            onToggleFavorite = {
                                triggerHaptic(20)
                                viewModel.toggleVaultFavorite(snippet.id)
                            },
                            onDelete = {
                                triggerHaptic(40)
                                viewModel.deleteVaultSnippet(snippet.id)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    // ─── Focus Countdown Overlay ──────────────────────────────
    if (isCountingDown) {
        CountdownOverlay(
            secondsRemaining = countdownRemaining,
            onCancel = {
                triggerHaptic(50)
                viewModel.cancelCountdown()
            }
        )
    }

    // ─── Add Custom Snippet Dialog ────────────────────────────
    if (showAddSnippetDialog) {
        AddSnippetDialog(
            onSave = { title, language, tags, codeText ->
                triggerHaptic(40)
                viewModel.addVaultSnippet(title, language, tags, codeText)
                showAddSnippetDialog = false
            },
            onDismiss = { showAddSnippetDialog = false }
        )
    }

    // ─── Manual IP / USB ADB Dialog ───────────────────────────
    if (showManualIpDialog) {
        ManualIpDialog(
            ip = manualIp,
            port = manualPort,
            onIpChange = { manualIp = it },
            onPortChange = { manualPort = it },
            onConnect = {
                val p = manualPort.toIntOrNull() ?: 8080
                viewModel.manualConnect(manualIp, p)
                showManualIpDialog = false
            },
            onDismiss = { showManualIpDialog = false }
        )
    }
}

// ─── Vault Snippet Card Component ─────────────────────────────

@Composable
fun VaultSnippetCard(
    snippet: VaultSnippet,
    onPunch: () -> Unit,
    onLoad: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Language chip, Title, Favorite & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (snippet.language.lowercase()) {
                            "c++", "cpp" -> ElectricCyan.copy(alpha = 0.2f)
                            "python", "py" -> EmeraldGreen.copy(alpha = 0.2f)
                            "sql" -> AmberOrange.copy(alpha = 0.2f)
                            "docker" -> ElectricIndigo.copy(alpha = 0.2f)
                            "bash", "shell" -> ElectricPurple.copy(alpha = 0.2f)
                            else -> Color.White.copy(alpha = 0.1f)
                        }
                    ) {
                        Text(
                            text = snippet.language,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (snippet.language.lowercase()) {
                                "c++", "cpp" -> ElectricCyan
                                "python", "py" -> EmeraldGreen
                                "sql" -> AmberOrange
                                "docker" -> ElectricIndigo
                                "bash", "shell" -> ElectricPurple
                                else -> Color.LightGray
                            }
                        )
                    }
                    Text(
                        text = snippet.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (snippet.isFavorite) "⭐" else "☆", fontSize = 16.sp)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🗑️", fontSize = 13.sp)
                    }
                }
            }

            // Tags row
            if (snippet.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    snippet.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // Code Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkBg)
                    .padding(10.dp)
            ) {
                Text(
                    text = snippet.code.trim(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPunch,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            brush = Brush.horizontalGradient(listOf(ElectricIndigo, ElectricPurple)),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚡", fontSize = 13.sp)
                        Text("PUNCH", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }

                OutlinedButton(
                    onClick = onLoad,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("✏️", fontSize = 12.sp)
                        Text("Load in Editor", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

// ─── Add Custom Snippet Dialog ────────────────────────────────

@Composable
fun AddSnippetDialog(
    onSave: (title: String, language: String, tags: List<String>, code: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("C++") }
    var tagsInput by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }

    val quickLangs = listOf("C++", "Python", "Java", "SQL", "Bash", "Other")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Add Snippet to Vault",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color.Gray, fontSize = 14.sp)
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Snippet Title", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Dijkstra Shortest Path", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Language Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickLangs.forEach { lang ->
                        Surface(
                            modifier = Modifier.clickable { language = lang },
                            shape = RoundedCornerShape(8.dp),
                            color = if (language == lang) ElectricCyan.copy(alpha = 0.3f) else CardBorder
                        ) {
                            Text(
                                text = lang,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = if (language == lang) FontWeight.Bold else FontWeight.Normal,
                                color = if (language == lang) ElectricCyan else Color.LightGray
                            )
                        }
                    }
                }

                // Tags Input
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (comma separated)", fontSize = 12.sp) },
                    placeholder = { Text("cp, graphs, interview", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Code Input
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = { Text("Code", fontSize = 12.sp) },
                    placeholder = { Text("Paste snippet code here...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedTags = tagsInput.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(title, language, parsedTags, codeText)
                        },
                        enabled = title.isNotBlank() && codeText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Save Snippet", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Sub-Components ───────────────────────────────────────────

@Composable
fun ConnectionStatusBanner(
    connectionState: ConnectionState,
    discoveryStatus: DiscoveryStatus,
    onRescan: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenManualIp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> Color(0xFF042F2E).copy(alpha = 0.7f)
                is ConnectionState.Connecting -> Color(0xFF451A03).copy(alpha = 0.7f)
                else -> CardBg
            }
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is ConnectionState.Connected -> EmeraldGreen
                                    is ConnectionState.Connecting -> AmberOrange
                                    else -> CrimsonRed
                                }
                            )
                    )
                    Column {
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "Connected to ${connectionState.deviceName}"
                                is ConnectionState.Connecting -> "Connecting to Companion..."
                                is ConnectionState.Error -> "Connection Problem"
                                else -> "Looking for Laptop Companion..."
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "ZeroConf active: ${connectionState.host}:8080"
                                is ConnectionState.Connecting -> "Handshaking via WebSocket..."
                                is ConnectionState.Error -> connectionState.message
                                else -> "Ensure phone & laptop are on same Wi-Fi / Hotspot"
                            },
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenManualIp) {
                    Text("Manual IP / ADB", fontSize = 12.sp, color = ElectricCyan)
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (connectionState is ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Disconnect", fontSize = 12.sp)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRescan,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Rescan", fontSize = 12.sp)
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) EmeraldGreen.copy(alpha = 0.15f) else CardBg
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
                    fontSize = 15.sp,
                    color = Color.White
                )
                Text(
                    text = "${device.host}:${device.port}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldGreen
                ) {
                    Text(
                        "CONNECTED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LiveTypingTelemetryCard(
    syncState: CompanionSyncState,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A).copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CrimsonRed)
                    )
                    Text(
                        "TYPING LIVE ON LAPTOP",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = CrimsonRed
                    )
                }
                Text(
                    "${syncState.progressPercent.toInt()}%",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            LinearProgressIndicator(
                progress = { (syncState.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CrimsonRed,
                trackColor = Color.DarkGray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Characters: ${syncState.typedChars} / ${syncState.totalChars}",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Text(
                    "Speed: ${syncState.wpm} WPM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberOrange
                )
            }
        }
    }
}

@Composable
fun SpeedControlCard(
    wpm: Int,
    onWpmChange: (Int) -> Unit
) {
    val speedTag = when {
        wpm <= 80 -> "🐢 Chill"
        wpm <= 200 -> "⚡ Normal"
        wpm <= 300 -> "🚀 Fast"
        wpm <= 450 -> "🔥 Pro Hacker"
        else -> "⚡ God Mode"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TYPING SPEED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElectricCyan
                    )
                    Text(
                        speedTag,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = { onWpmChange(wpm - 10) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CardBorder)
                    ) {
                        Text("-", fontSize = 18.sp, color = Color.White)
                    }
                    Text(
                        "$wpm WPM",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    FilledIconButton(
                        onClick = { onWpmChange(wpm + 10) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CardBorder)
                    ) {
                        Text("+", fontSize = 18.sp, color = Color.White)
                    }
                }
            }

            Slider(
                value = wpm.toFloat(),
                onValueChange = { onWpmChange(it.roundToInt()) },
                valueRange = 10f..500f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan
                )
            )

            // Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(60, 120, 180, 240, 320, 420, 500).forEach { preset ->
                    Surface(
                        modifier = Modifier.clickable { onWpmChange(preset) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (wpm == preset) ElectricCyan.copy(alpha = 0.25f) else CardBorder
                    ) {
                        Text(
                            text = "$preset WPM",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = if (wpm == preset) FontWeight.Bold else FontWeight.Normal,
                            color = if (wpm == preset) ElectricCyan else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FocusDelayCard(
    delaySec: Int,
    onDelayChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "FOCUS DELAY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AmberOrange
                    )
                    Text(
                        "Time to switch to laptop target editor",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = { onDelayChange(delaySec - 1) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CardBorder)
                    ) {
                        Text("-", fontSize = 18.sp, color = Color.White)
                    }
                    Text(
                        "${delaySec}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    FilledIconButton(
                        onClick = { onDelayChange(delaySec + 1) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CardBorder)
                    ) {
                        Text("+", fontSize = 18.sp, color = Color.White)
                    }
                }
            }

            Slider(
                value = delaySec.toFloat(),
                onValueChange = { onDelayChange(it.roundToInt()) },
                valueRange = 0f..30f,
                colors = SliderDefaults.colors(
                    thumbColor = AmberOrange,
                    activeTrackColor = AmberOrange
                )
            )
        }
    }
}

@Composable
fun SuperpowerTogglesCard(
    smartIndent: Boolean,
    onSmartIndentChange: (Boolean) -> Unit,
    oopsMode: Boolean,
    onOopsModeChange: (Boolean) -> Unit,
    humanJitter: Boolean,
    onHumanJitterChange: (Boolean) -> Unit,
    fastPaste: Boolean,
    onFastPasteChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "TYPING SUPERPOWERS",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElectricPurple
            )

            ToggleRow(
                title = "Smart IDE Auto-Indent",
                subtitle = "Eliminates duplicate tabs/indentation in IDEs, SEB, & online editors",
                checked = smartIndent,
                onCheckedChange = onSmartIndentChange
            )

            HorizontalDivider(color = CardBorder)

            ToggleRow(
                title = "Oops Mode (Human Typos)",
                subtitle = "Randomly types realistic typos and uses backspace to fix them",
                checked = oopsMode,
                onCheckedChange = onOopsModeChange
            )

            HorizontalDivider(color = CardBorder)

            ToggleRow(
                title = "Human Jitter",
                subtitle = "Varies keystroke intervals naturally like real fingers",
                checked = humanJitter,
                onCheckedChange = onHumanJitterChange
            )

            HorizontalDivider(color = CardBorder)

            ToggleRow(
                title = "Fast Paste (Clipboard)",
                subtitle = "Directly pastes via clipboard where typing isn't strictly required",
                checked = fastPaste,
                onCheckedChange = onFastPasteChange
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricCyan
            )
        )
    }
}

@Composable
fun CountdownOverlay(
    secondsRemaining: Int,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    "SWITCH TO TARGET WINDOW!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AmberOrange,
                    letterSpacing = 1.sp
                )

                // Big Animated Countdown Number
                Text(
                    text = "$secondsRemaining",
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    "Typing will begin in $secondsRemaining second${if (secondsRemaining == 1) "" else "s"}...",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("ABORT PUNCH", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ManualIpDialog(
    ip: String,
    port: String,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Manual IP Connection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    "Use this if Wi-Fi blocks mDNS discovery or if connecting via USB cable (ADB reverse)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = ip,
                    onValueChange = onIpChange,
                    label = { Text("Laptop IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port (default 8080)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SmallActionButton(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isDanger) CrimsonRed.copy(alpha = 0.2f) else CardBorder
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDanger) CrimsonRed else Color.LightGray
        )
    }
}
