package com.aditya.autotyper.share

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.aditya.autotyper.MainActivity
import com.aditya.autotyper.discovery.AutoTyperDiscovery
import com.aditya.autotyper.network.AutoTyperClient
import com.aditya.autotyper.network.ConnectionState
import com.aditya.autotyper.ui.theme.AutoTyperTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Dark Glassmorphism Palette
private val DarkBg = Color(0xFF090D16)
private val CardBg = Color(0xFF131B2E).copy(alpha = 0.95f)
private val CardBorder = Color(0xFF1E293B)
private val ElectricCyan = Color(0xFF06B6D4)
private val ElectricIndigo = Color(0xFF6366F1)
private val ElectricPurple = Color(0xFF8B5CF6)
private val EmeraldGreen = Color(0xFF10B981)
private val AmberOrange = Color(0xFFF59E0B)
private val CrimsonRed = Color(0xFFEF4444)

enum class SharePunchStep {
    COUNTDOWN,
    TYPING,
    COMPLETED,
    ERROR
}

class SharePunchActivity : ComponentActivity() {
    private lateinit var discovery: AutoTyperDiscovery
    private val client = AutoTyperClient()
    private var sharedText: String = ""
    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedText = extractSharedText(intent)
        if (sharedText.isBlank()) {
            Toast.makeText(this, "No text provided to punch", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        discovery = AutoTyperDiscovery(this)

        // Preferences
        val statePrefs = getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)
        val wpm = statePrefs.getInt("saved_wpm", 180)
        val focusDelaySec = statePrefs.getInt("saved_delay", 3).coerceAtLeast(1)
        val smartIndent = statePrefs.getBoolean("saved_smart_indent", true)
        val oopsMode = statePrefs.getBoolean("saved_oops_mode", false)
        val humanJitter = statePrefs.getBoolean("saved_human_jitter", false)
        val fastPaste = statePrefs.getBoolean("saved_fast_paste", false)

        // Connect via auto-discovery & fast-resume
        lifecycleScope.launch {
            discovery.discoveredDevices.collect { devices ->
                if (client.connectionState.value !is ConnectionState.Connected && devices.isNotEmpty()) {
                    val target = devices.first()
                    client.connect(target.host, target.wsPort, target.wsPath, target.name)
                }
            }
        }
        discovery.startDiscovery()

        setContent {
            AutoTyperTheme(darkTheme = true) {
                val connectionState by client.connectionState.collectAsState()
                val syncState by client.syncState.collectAsState()

                var currentStep by remember { mutableStateOf(SharePunchStep.COUNTDOWN) }
                var secondsLeft by remember { mutableStateOf(focusDelaySec) }

                fun triggerHaptic(durationMs: Long = 40) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                            vibratorManager?.defaultVibrator?.vibrate(
                                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(durationMs)
                        }
                    } catch (_: Exception) {}
                }

                fun punchDirectly() {
                    countdownJob?.cancel()
                    triggerHaptic(60)
                    currentStep = SharePunchStep.TYPING

                    client.sendPunch(
                        code = sharedText,
                        wpm = wpm,
                        focusDelay = 0,
                        smartIndent = smartIndent,
                        oopsMode = oopsMode,
                        humanJitter = humanJitter,
                        fastPaste = fastPaste
                    )
                }

                // Start countdown once connected (or run immediately if already connected)
                LaunchedEffect(connectionState) {
                    if (connectionState is ConnectionState.Connected && currentStep == SharePunchStep.COUNTDOWN && countdownJob == null) {
                        countdownJob = lifecycleScope.launch {
                            for (i in focusDelaySec downTo 1) {
                                secondsLeft = i
                                delay(1000)
                            }
                            punchDirectly()
                        }
                    }
                }

                // Listen for typing events
                LaunchedEffect(syncState.isTyping) {
                    if (currentStep == SharePunchStep.TYPING && !syncState.isTyping && syncState.progressPercent >= 99.0) {
                        currentStep = SharePunchStep.COMPLETED
                        triggerHaptic(80)
                        delay(1200)
                        finish()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SharePunchOverlayCard(
                        text = sharedText,
                        connectionState = connectionState,
                        currentStep = currentStep,
                        secondsLeft = secondsLeft,
                        syncProgress = syncState.progressPercent,
                        typedChars = syncState.typedChars,
                        totalChars = syncState.totalChars,
                        wpm = wpm,
                        onPunchNow = { punchDirectly() },
                        onAbort = {
                            countdownJob?.cancel()
                            client.sendStop()
                            finish()
                        },
                        onOpenFullEditor = {
                            val intent = Intent(this@SharePunchActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(Intent.EXTRA_TEXT, sharedText)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun extractSharedText(intent: Intent?): String {
        if (intent == null) return ""
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
            }
            else -> ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        discovery.stopDiscovery()
        client.disconnect()
    }
}

@Composable
fun SharePunchOverlayCard(
    text: String,
    connectionState: ConnectionState,
    currentStep: SharePunchStep,
    secondsLeft: Int,
    syncProgress: Double,
    typedChars: Int,
    totalChars: Int,
    wpm: Int,
    onPunchNow: () -> Unit,
    onAbort: () -> Unit,
    onOpenFullEditor: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚡", fontSize = 18.sp)
                    Text(
                        "Share to Punch",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (connectionState) {
                        is ConnectionState.Connected -> EmeraldGreen.copy(alpha = 0.2f)
                        is ConnectionState.Connecting -> AmberOrange.copy(alpha = 0.2f)
                        else -> Color.White.copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected -> connectionState.deviceName
                            is ConnectionState.Connecting -> "Connecting..."
                            else -> "Searching Wi-Fi..."
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

            // Shared Code Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = text.take(180).trim() + if (text.length > 180) "..." else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${text.length} chars • ~${String.format("%.1f", (text.length.toDouble() / (wpm * 5.0)) * 60.0)}s @ $wpm WPM",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            // Interactive Flow
            when (currentStep) {
                SharePunchStep.COUNTDOWN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (connectionState is ConnectionState.Connected) {
                            Text(
                                "Switch to target window on laptop!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberOrange
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$secondsLeft",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text("seconds remaining", fontSize = 13.sp, color = Color.Gray)
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                            Text(
                                "Connecting to laptop companion...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAbort,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = CrimsonRed)
                            }
                            Button(
                                onClick = onPunchNow,
                                enabled = connectionState is ConnectionState.Connected,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        brush = Brush.horizontalGradient(listOf(ElectricIndigo, ElectricPurple)),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Punch Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        TextButton(onClick = onOpenFullEditor) {
                            Text("Open in Full Editor ↗", fontSize = 12.sp, color = ElectricCyan)
                        }
                    }
                }

                SharePunchStep.TYPING -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Punching keystrokes live...", fontSize = 13.sp, color = Color.White)
                            Text("${syncProgress.toInt()}%", fontWeight = FontWeight.Bold, color = ElectricCyan)
                        }
                        LinearProgressIndicator(
                            progress = { (syncProgress / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CrimsonRed,
                            trackColor = Color.DarkGray
                        )
                        Button(
                            onClick = onAbort,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("STOP TYPING", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SharePunchStep.COMPLETED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✅", fontSize = 36.sp)
                        Text(
                            "Punched Successfully!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EmeraldGreen
                        )
                    }
                }

                SharePunchStep.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Connection Failed", fontWeight = FontWeight.Bold, color = CrimsonRed)
                        Button(onClick = onOpenFullEditor) {
                            Text("Open in App")
                        }
                    }
                }
            }
        }
    }
}
