package com.aditya.autotyper.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val host: String, val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class CompanionSyncState(
    val code: String = "",
    val wpm: Int = 180,
    val focusDelay: Int = 3,
    val smartIndent: Boolean = true,
    val oopsMode: Boolean = false,
    val humanJitter: Boolean = false,
    val fastPaste: Boolean = false,
    val isTyping: Boolean = false,
    val typedChars: Int = 0,
    val totalChars: Int = 0,
    val progressPercent: Double = 0.0
)

class AutoTyperClient {
    private val tag = "AutoTyperClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket persistent connection
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _syncState = MutableStateFlow(CompanionSyncState())
    val syncState: StateFlow<CompanionSyncState> = _syncState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    var currentHost: String? = null
        private set
    var currentPort: Int = 8080
        private set

    fun connect(host: String, port: Int = 8080, path: String = "/ws", deviceName: String = "") {
        if (_connectionState.value is ConnectionState.Connected && currentHost == host && currentPort == port) {
            return
        }

        disconnect()
        currentHost = host
        currentPort = port
        _connectionState.value = ConnectionState.Connecting

        val wsUrl = "ws://$host:$port$path"
        Log.i(tag, "Connecting to companion at $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected successfully to $wsUrl")
                _connectionState.value = ConnectionState.Connected(
                    host = host,
                    deviceName = if (deviceName.isNotBlank()) deviceName else host
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closed: $code / $reason")
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val action = json.optString("action")

            when (action) {
                "sync_state" -> {
                    val current = _syncState.value
                    _syncState.value = current.copy(
                        code = json.optString("code", current.code),
                        wpm = json.optInt("wpm", current.wpm),
                        focusDelay = json.optInt("focus_delay", current.focusDelay),
                        smartIndent = json.optBoolean("smart_indent", current.smartIndent),
                        oopsMode = json.optBoolean("oops_mode", current.oopsMode),
                        humanJitter = json.optBoolean("human_jitter", current.humanJitter),
                        fastPaste = json.optBoolean("fast_paste", current.fastPaste),
                        isTyping = json.optBoolean("is_typing", current.isTyping),
                        typedChars = json.optInt("typed_chars", current.typedChars),
                        totalChars = json.optInt("total_chars", current.totalChars),
                        progressPercent = json.optDouble("progress_percent", current.progressPercent)
                    )
                }
                "progress" -> {
                    val current = _syncState.value
                    _syncState.value = current.copy(
                        isTyping = json.optBoolean("is_typing", true),
                        typedChars = json.optInt("typed_chars", current.typedChars),
                        totalChars = json.optInt("total_chars", current.totalChars),
                        progressPercent = json.optDouble("progress_percent", current.progressPercent),
                        wpm = json.optInt("wpm", current.wpm)
                    )
                }
                "punch_done" -> {
                    val current = _syncState.value
                    _syncState.value = current.copy(
                        isTyping = false,
                        progressPercent = 100.0
                    )
                    scope.launch { _eventFlow.emit("PUNCH_COMPLETE") }
                }
                "stopped" -> {
                    val current = _syncState.value
                    _syncState.value = current.copy(isTyping = false)
                    scope.launch { _eventFlow.emit("PUNCH_STOPPED") }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing incoming message: ${e.message}")
        }
    }

    fun sendPunch(
        code: String,
        wpm: Int,
        focusDelay: Int,
        smartIndent: Boolean,
        oopsMode: Boolean,
        humanJitter: Boolean,
        fastPaste: Boolean
    ): Boolean {
        val payload = JSONObject().apply {
            put("action", "punch")
            put("code", code)
            put("wpm", wpm)
            put("focus_delay", focusDelay)
            put("smart_indent", smartIndent)
            put("oops_mode", oopsMode)
            put("human_jitter", humanJitter)
            put("fast_paste", fastPaste)
        }
        return sendMessage(payload.toString())
    }

    fun sendStop(): Boolean {
        val payload = JSONObject().apply {
            put("action", "stop")
        }
        return sendMessage(payload.toString())
    }

    fun sendUpdateCode(code: String): Boolean {
        val payload = JSONObject().apply {
            put("action", "update_code")
            put("code", code)
        }
        return sendMessage(payload.toString())
    }

    fun sendUpdateSettings(
        wpm: Int,
        focusDelay: Int,
        smartIndent: Boolean,
        oopsMode: Boolean,
        humanJitter: Boolean,
        fastPaste: Boolean
    ): Boolean {
        val payload = JSONObject().apply {
            put("action", "update_settings")
            put("wpm", wpm)
            put("focus_delay", focusDelay)
            put("smart_indent", smartIndent)
            put("oops_mode", oopsMode)
            put("human_jitter", humanJitter)
            put("fast_paste", fastPaste)
        }
        return sendMessage(payload.toString())
    }

    private fun sendMessage(text: String): Boolean {
        val ws = webSocket
        if (ws != null) {
            return ws.send(text)
        }
        return false
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(tag, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
