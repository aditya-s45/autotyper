package com.aditya.autotyper.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aditya.autotyper.MainActivity
import com.aditya.autotyper.discovery.AutoTyperDiscovery
import com.aditya.autotyper.network.AutoTyperClient
import com.aditya.autotyper.network.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var collapsedView: FrameLayout? = null
    private var expandedView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var discovery: AutoTyperDiscovery? = null
    private var client: AutoTyperClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isExpanded = false
    private var screenWidth = 1080

    companion object {
        const val CHANNEL_ID = "autotyper_bubble_channel"
        const val NOTIFICATION_ID = 2001
        var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        discovery = AutoTyperDiscovery(applicationContext)
        client = AutoTyperClient()

        createNotificationChannel()
        startServiceInForeground()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(metrics)
        screenWidth = metrics.widthPixels

        initFloatingBubble()
        discovery?.startDiscovery()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoTyper Stealth Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the 1-tap stealth floating punch bubble active over apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startServiceInForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setContentTitle("⚡ AutoTyper Stealth Bubble Active")
            .setContentText("Tap bubble to punch clipboard instantly into laptop")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dpToPx(70)
            y = dpToPx(200)
        }

        rootView = FrameLayout(this)

        buildCollapsedBubble()
        buildExpandedDrawer()

        rootView?.addView(collapsedView)
        rootView?.addView(expandedView)

        expandedView?.visibility = View.GONE
        collapsedView?.visibility = View.VISIBLE

        setupTouchListener()

        windowManager?.addView(rootView, layoutParams)
    }

    private fun buildCollapsedBubble() {
        collapsedView = FrameLayout(this).apply {
            val size = dpToPx(56)
            layoutParams = FrameLayout.LayoutParams(size, size)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#090D16"))
                setStroke(dpToPx(2), Color.parseColor("#06B6D4"))
            }
            background = bg
            elevation = dpToPx(8).toFloat()

            val text = TextView(this@FloatingBubbleService).apply {
                this.text = "⚡"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            val textParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            addView(text, textParams)
        }
    }

    private fun buildExpandedDrawer() {
        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val width = dpToPx(280)
            layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dpToPx(1), Color.parseColor("#1E293B"))
            }
            background = bg
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            elevation = dpToPx(16).toFloat()

            // Header Row: Title + Minimize + Close
            val headerRow = LinearLayout(this@FloatingBubbleService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                val title = TextView(this@FloatingBubbleService).apply {
                    text = "⚡ Stealth Punch"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(title)

                val minimizeBtn = TextView(this@FloatingBubbleService).apply {
                    text = " — "
                    setTextColor(Color.LTGRAY)
                    textSize = 16f
                    setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
                    setOnClickListener {
                        toggleExpandedState(false)
                    }
                }
                addView(minimizeBtn)

                val closeBtn = TextView(this@FloatingBubbleService).apply {
                    text = " ✕ "
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 14f
                    setPadding(dpToPx(6), dpToPx(4), dpToPx(4), dpToPx(4))
                    setOnClickListener {
                        stopSelf()
                    }
                }
                addView(closeBtn)
            }
            addView(headerRow)

            // Companion Status Row
            val statusRow = TextView(this@FloatingBubbleService).apply {
                text = "🟢 Fast-Resume Ready (ZeroConf)"
                textSize = 11f
                setTextColor(Color.parseColor("#10B981"))
                setPadding(0, dpToPx(4), 0, dpToPx(10))
            }
            addView(statusRow)

            // High-Impact Primary Action Button: "PUNCH CLIPBOARD NOW"
            val punchBtn = TextView(this@FloatingBubbleService).apply {
                text = "⚡ PUNCH CLIPBOARD"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER

                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(10).toFloat()
                    setColors(intArrayOf(Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
                background = btnBg
                val btnParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(42)
                ).apply {
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(8)
                }
                layoutParams = btnParams

                setOnClickListener {
                    triggerVibration(40)
                    punchFromClipboard()
                    toggleExpandedState(false)
                }
            }
            addView(punchBtn)

            // WPM Quick Presets Row
            val wpmLabel = TextView(this@FloatingBubbleService).apply {
                text = "SPEED PRESETS"
                textSize = 10f
                setTextColor(Color.parseColor("#06B6D4"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }
            addView(wpmLabel)

            val wpmScroll = HorizontalScrollView(this@FloatingBubbleService).apply {
                isHorizontalScrollBarEnabled = false
                val scrollParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8)
                }
                layoutParams = scrollParams

                val wpmContainer = LinearLayout(this@FloatingBubbleService).apply {
                    orientation = LinearLayout.HORIZONTAL

                    val prefs = getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)
                    val currentWpm = prefs.getInt("saved_wpm", 180)

                    listOf(60, 120, 180, 240, 320, 500).forEach { speed ->
                        val chip = TextView(this@FloatingBubbleService).apply {
                            text = "${speed}WPM"
                            textSize = 10f
                            setTextColor(if (speed == currentWpm) Color.WHITE else Color.LTGRAY)
                            val chipBg = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dpToPx(6).toFloat()
                                setColor(if (speed == currentWpm) Color.parseColor("#06B6D4") else Color.parseColor("#1E293B"))
                            }
                            background = chipBg
                            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                            val chipParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginEnd = dpToPx(6)
                            }
                            layoutParams = chipParams

                            setOnClickListener {
                                prefs.edit().putInt("saved_wpm", speed).apply()
                                Toast.makeText(this@FloatingBubbleService, "Speed: $speed WPM", Toast.LENGTH_SHORT).show()
                            }
                        }
                        addView(chip)
                    }
                }
                addView(wpmContainer)
            }
            addView(wpmScroll)

            // Emergency Stop Button
            val stopBtn = TextView(this@FloatingBubbleService).apply {
                text = "🛑 STOP TYPING ON LAPTOP"
                textSize = 11f
                setTextColor(Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER
                val stopBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#EF4444"))
                    setColor(Color.parseColor("#450A0A"))
                }
                background = stopBg
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(34)
                )
                layoutParams = p
                setOnClickListener {
                    triggerVibration(80)
                    client?.sendStop()
                    Toast.makeText(this@FloatingBubbleService, "Typing Aborted!", Toast.LENGTH_SHORT).show()
                }
            }
            addView(stopBtn)
        }
    }

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        collapsedView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    layoutParams?.x = initialX + dx
                    layoutParams?.y = initialY + dy
                    layoutParams?.let { windowManager?.updateViewLayout(rootView, it) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    val duration = System.currentTimeMillis() - touchStartTime

                    if (dx < 12 && dy < 12 && duration < 300) {
                        // Single Tap: Instant Stealth Punch
                        triggerVibration(40)
                        punchFromClipboard()
                    } else {
                        // Magnetic snap to nearest horizontal edge
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }

        // Long click to open expanded mini-drawer
        collapsedView?.setOnLongClickListener {
            triggerVibration(60)
            toggleExpandedState(true)
            true
        }
    }

    private fun snapToEdge() {
        val currentX = layoutParams?.x ?: return
        val bubbleWidth = dpToPx(56)
        val targetX = if (currentX + (bubbleWidth / 2) < screenWidth / 2) {
            dpToPx(12) // snap left
        } else {
            screenWidth - bubbleWidth - dpToPx(12) // snap right
        }
        layoutParams?.x = targetX
        layoutParams?.let { windowManager?.updateViewLayout(rootView, it) }
    }

    private fun toggleExpandedState(expand: Boolean) {
        isExpanded = expand
        if (expand) {
            collapsedView?.visibility = View.GONE
            expandedView?.visibility = View.VISIBLE
            // Center the drawer horizontally if needed
            val drawerWidth = dpToPx(280)
            layoutParams?.x = ((screenWidth - drawerWidth) / 2).coerceAtLeast(dpToPx(16))
            layoutParams?.let { windowManager?.updateViewLayout(rootView, it) }
        } else {
            expandedView?.visibility = View.GONE
            collapsedView?.visibility = View.VISIBLE
            snapToEdge()
        }
    }

    private fun punchFromClipboard() {
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
            val prefs = getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)
            val wpm = prefs.getInt("saved_wpm", 180)
            val delaySec = prefs.getInt("saved_delay", 3)
            val smartIndent = prefs.getBoolean("saved_smart_indent", true)
            val oopsMode = prefs.getBoolean("saved_oops_mode", false)
            val humanJitter = prefs.getBoolean("saved_human_jitter", false)
            val fastPaste = prefs.getBoolean("saved_fast_paste", false)

            val localDiscovery = discovery ?: AutoTyperDiscovery(applicationContext)
            val localClient = client ?: AutoTyperClient()

            // Connect using ZeroConf / Fast-Resume
            var connected = localClient.connectionState.value is ConnectionState.Connected
            if (!connected) {
                withTimeoutOrNull(2500) {
                    while (!connected) {
                        val devices = localDiscovery.discoveredDevices.value
                        if (devices.isNotEmpty()) {
                            val target = devices.first()
                            localClient.connect(target.host, target.wsPort, target.wsPath, target.name)
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
                Toast.makeText(applicationContext, "Laptop companion not connected on Wi-Fi", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Stealth countdown pulses
            if (delaySec > 0) {
                for (i in delaySec downTo 1) {
                    triggerVibration(35)
                    delay(1000)
                }
            }

            // Fire punch to laptop
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
        }
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        rootView?.let { windowManager?.removeView(it) }
        discovery?.stopDiscovery()
        client?.disconnect()
    }
}
