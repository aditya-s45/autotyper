package com.aditya.autotyper.tile

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.aditya.autotyper.discovery.AutoTyperDiscovery
import com.aditya.autotyper.network.AutoTyperClient
import com.aditya.autotyper.network.ConnectionState
import kotlinx.coroutines.*

class PunchTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var discovery: AutoTyperDiscovery? = null
    private var client: AutoTyperClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(isPunching = false, customLabel = null)
    }

    override fun onClick() {
        super.onClick()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        } else ""

        if (text.isBlank()) {
            triggerVibration(100)
            Toast.makeText(this, "Clipboard is empty! Copy code first.", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            punchFromClipboard(text)
        }
    }

    private suspend fun punchFromClipboard(text: String) {
        val prefs = getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)
        val wpm = prefs.getInt("saved_wpm", 180)
        val delaySec = prefs.getInt("saved_delay", 3)
        val smartIndent = prefs.getBoolean("saved_smart_indent", true)
        val oopsMode = prefs.getBoolean("saved_oops_mode", false)
        val humanJitter = prefs.getBoolean("saved_human_jitter", false)
        val fastPaste = prefs.getBoolean("saved_fast_paste", false)

        updateTileState(isPunching = true, customLabel = "Connecting...")

        if (discovery == null) discovery = AutoTyperDiscovery(applicationContext)
        if (client == null) client = AutoTyperClient()

        val localDiscovery = discovery!!
        val localClient = client!!

        // Fast-Resume: AutoTyperDiscovery pings cached laptop IP immediately
        var connected = localClient.connectionState.value is ConnectionState.Connected
        if (!connected) {
            localDiscovery.startDiscovery()
            withTimeoutOrNull(3000) {
                while (!connected) {
                    val devices = localDiscovery.discoveredDevices.value
                    if (devices.isNotEmpty()) {
                        val target = devices.first()
                        localClient.connect(target.host, target.wsPort, target.wsPath, target.name)
                        // Wait for connection to establish
                        while (localClient.connectionState.value !is ConnectionState.Connected) {
                            delay(50)
                        }
                        connected = true
                        break
                    }
                    delay(100)
                }
            }
        }

        if (!connected && localClient.connectionState.value !is ConnectionState.Connected) {
            triggerVibration(200)
            updateTileState(isPunching = false, customLabel = "Laptop Offline")
            Toast.makeText(applicationContext, "Laptop companion not found on Wi-Fi", Toast.LENGTH_SHORT).show()
            delay(2000)
            updateTileState(isPunching = false, customLabel = null)
            return
        }

        // Focus Delay Countdown with stealth haptic pulses in your hand
        if (delaySec > 0) {
            for (i in delaySec downTo 1) {
                updateTileState(isPunching = true, customLabel = "Punching in ${i}s...")
                triggerVibration(40)
                delay(1000)
            }
        }

        // Fire Punch to Laptop
        updateTileState(isPunching = true, customLabel = "Punching...")
        triggerVibration(70)

        localClient.sendPunch(
            code = text,
            wpm = wpm,
            focusDelay = 0,
            smartIndent = smartIndent,
            oopsMode = oopsMode,
            humanJitter = humanJitter,
            fastPaste = fastPaste
        )

        Toast.makeText(applicationContext, "⚡ Punched ${text.length} chars to laptop!", Toast.LENGTH_SHORT).show()
        updateTileState(isPunching = false, customLabel = "✅ Punched!")

        delay(2000)
        updateTileState(isPunching = false, customLabel = null)
    }

    private fun updateTileState(isPunching: Boolean, customLabel: String?) {
        val tile = qsTile ?: return
        tile.state = if (isPunching) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = customLabel ?: "Punch Clipboard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPunching) "AutoTyper Active" else "Tap to type"
        }
        tile.updateTile()
    }

    private fun triggerVibration(durationMs: Long) {
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        discovery?.stopDiscovery()
        client?.disconnect()
    }
}
