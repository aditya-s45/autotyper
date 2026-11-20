package com.aditya.autotyper.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.autotyper.discovery.AutoTyperDiscovery
import com.aditya.autotyper.discovery.DiscoveredDevice
import com.aditya.autotyper.discovery.DiscoveryStatus
import com.aditya.autotyper.network.AutoTyperClient
import com.aditya.autotyper.network.CompanionSyncState
import com.aditya.autotyper.network.ConnectionState
import com.aditya.autotyper.vault.SnippetsVaultManager
import com.aditya.autotyper.vault.VaultSnippet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

data class CodeSnippet(
    val title: String,
    val language: String,
    val code: String
)

class AutoTyperViewModel(application: Application) : AndroidViewModel(application) {
    val discovery = AutoTyperDiscovery(application)
    val client = AutoTyperClient()
    val vaultManager = SnippetsVaultManager(application)

    private val prefs = application.getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)

    // Connection & Discovery
    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val syncState: StateFlow<CompanionSyncState> = client.syncState
    val discoveryStatus: StateFlow<DiscoveryStatus> = discovery.discoveryStatus
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discovery.discoveredDevices

    // Offline Snippets Vault
    val vaultSnippets: StateFlow<List<VaultSnippet>> = vaultManager.snippets

    // Editor & Typing Settings State
    private val _code = MutableStateFlow(prefs.getString("saved_code", "") ?: "")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _wpm = MutableStateFlow(prefs.getInt("saved_wpm", 180))
    val wpm: StateFlow<Int> = _wpm.asStateFlow()

    private val _focusDelay = MutableStateFlow(prefs.getInt("saved_delay", 3))
    val focusDelay: StateFlow<Int> = _focusDelay.asStateFlow()

    private val _smartIndent = MutableStateFlow(prefs.getBoolean("saved_smart_indent", true))
    val smartIndent: StateFlow<Boolean> = _smartIndent.asStateFlow()

    private val _oopsMode = MutableStateFlow(prefs.getBoolean("saved_oops_mode", false))
    val oopsMode: StateFlow<Boolean> = _oopsMode.asStateFlow()

    private val _humanJitter = MutableStateFlow(prefs.getBoolean("saved_human_jitter", false))
    val humanJitter: StateFlow<Boolean> = _humanJitter.asStateFlow()

    private val _fastPaste = MutableStateFlow(prefs.getBoolean("saved_fast_paste", false))
    val fastPaste: StateFlow<Boolean> = _fastPaste.asStateFlow()

    // Countdown Overlay State
    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown: StateFlow<Boolean> = _isCountingDown.asStateFlow()

    private val _countdownRemaining = MutableStateFlow(3)
    val countdownRemaining: StateFlow<Int> = _countdownRemaining.asStateFlow()

    private var countdownJob: Job? = null

    // History Snippets
    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    // Preset Templates
    val predefinedSnippets = listOf(
        CodeSnippet(
            title = "Python Solution",
            language = "Python",
            code = "def solve():\n    import sys\n    input = sys.stdin.read\n    data = input().split()\n    if not data: return\n    n = int(data[0])\n    print(f'Processed {n} items')\n\nif __name__ == '__main__':\n    solve()"
        ),
        CodeSnippet(
            title = "C++ Fast I/O",
            language = "C++",
            code = "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios_base::sync_with_stdio(false);\n    cin.tie(NULL);\n    int t;\n    if (cin >> t) {\n        while (t--) {\n            int n;\n            cin >> n;\n            cout << n << \"\\n\";\n        }\n    }\n    return 0;\n}"
        ),
        CodeSnippet(
            title = "Java Main Template",
            language = "Java",
            code = "import java.util.*;\n\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        System.out.println(\"Output: \" + n);\n    }\n}"
        ),
        CodeSnippet(
            title = "JavaScript Async Fetch",
            language = "JavaScript",
            code = "async function fetchData(url) {\n    try {\n        const res = await fetch(url);\n        const data = await res.json();\n        console.log(data);\n        return data;\n    } catch (err) {\n        console.error('Fetch error:', err);\n    }\n}"
        ),
        CodeSnippet(
            title = "SQL Analytics Query",
            language = "SQL",
            code = "SELECT \n    user_id,\n    COUNT(order_id) AS total_orders,\n    SUM(amount) AS total_spent\nFROM orders\nWHERE status = 'completed'\nGROUP BY user_id\nHAVING total_spent > 100\nORDER BY total_spent DESC;"
        )
    )

    init {
        // Auto-connect to first discovered laptop
        viewModelScope.launch {
            discovery.discoveredDevices.collect { devices ->
                if (client.connectionState.value !is ConnectionState.Connected && devices.isNotEmpty()) {
                    val target = devices.first()
                    client.connect(target.host, target.wsPort, target.wsPath, target.name)
                }
            }
        }

        // Sync companion state if companion connects with existing text
        viewModelScope.launch {
            client.syncState.collect { sync ->
                if (_code.value.isBlank() && sync.code.isNotBlank()) {
                    _code.value = sync.code
                }
            }
        }

        startScan()
    }

    fun startScan() {
        discovery.startDiscovery()
    }

    fun connectToDevice(device: DiscoveredDevice) {
        client.connect(device.host, device.wsPort, device.wsPath, device.name)
    }

    fun manualConnect(host: String, port: Int = 8080) {
        val cleanHost = host.trim()
        if (cleanHost.isNotEmpty()) {
            client.connect(cleanHost, port, "/ws", "Manual ($cleanHost)")
        }
    }

    fun disconnect() {
        client.disconnect()
    }

    // Setting modifiers with persistence
    fun setCode(newCode: String) {
        _code.value = newCode
        prefs.edit().putString("saved_code", newCode).apply()
        if (client.connectionState.value is ConnectionState.Connected) {
            client.sendUpdateCode(newCode)
        }
    }

    fun setWpm(newWpm: Int) {
        val clamped = newWpm.coerceIn(10, 500)
        _wpm.value = clamped
        prefs.edit().putInt("saved_wpm", clamped).apply()
        syncSettings()
    }

    fun setFocusDelay(seconds: Int) {
        val clamped = seconds.coerceIn(0, 60)
        _focusDelay.value = clamped
        prefs.edit().putInt("saved_delay", clamped).apply()
        syncSettings()
    }

    fun setSmartIndent(enabled: Boolean) {
        _smartIndent.value = enabled
        prefs.edit().putBoolean("saved_smart_indent", enabled).apply()
        syncSettings()
    }

    fun setOopsMode(enabled: Boolean) {
        _oopsMode.value = enabled
        prefs.edit().putBoolean("saved_oops_mode", enabled).apply()
        syncSettings()
    }

    fun setHumanJitter(enabled: Boolean) {
        _humanJitter.value = enabled
        prefs.edit().putBoolean("saved_human_jitter", enabled).apply()
        syncSettings()
    }

    fun setFastPaste(enabled: Boolean) {
        _fastPaste.value = enabled
        prefs.edit().putBoolean("saved_fast_paste", enabled).apply()
        syncSettings()
    }

    private fun syncSettings() {
        if (client.connectionState.value is ConnectionState.Connected) {
            client.sendUpdateSettings(
                wpm = _wpm.value,
                focusDelay = _focusDelay.value,
                smartIndent = _smartIndent.value,
                oopsMode = _oopsMode.value,
                humanJitter = _humanJitter.value,
                fastPaste = _fastPaste.value
            )
        }
    }

    // Punch with Focus Countdown
    fun startPunchSequence() {
        val text = _code.value
        if (text.isBlank()) return

        saveToHistory(text)

        val delaySec = _focusDelay.value
        if (delaySec <= 0) {
            executePunch()
            return
        }

        countdownJob?.cancel()
        _isCountingDown.value = true
        _countdownRemaining.value = delaySec

        countdownJob = viewModelScope.launch {
            for (i in delaySec downTo 1) {
                _countdownRemaining.value = i
                delay(1000)
            }
            _isCountingDown.value = false
            executePunch()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _isCountingDown.value = false
    }

    private fun executePunch() {
        client.sendPunch(
            code = _code.value,
            wpm = _wpm.value,
            focusDelay = 0, // delay already elapsed on phone
            smartIndent = _smartIndent.value,
            oopsMode = _oopsMode.value,
            humanJitter = _humanJitter.value,
            fastPaste = _fastPaste.value
        )
    }

    fun stop() {
        cancelCountdown()
        client.sendStop()
    }

    fun clearCode() {
        setCode("")
    }

    // History management
    private fun loadHistory(): List<String> {
        val raw = prefs.getString("code_history", "[]") ?: "[]"
        return try {
            val jsonArr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArr.length()) {
                list.add(jsonArr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToHistory(entry: String) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return
        val current = _history.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = current.take(10)
        _history.value = trimmedList

        val jsonArr = JSONArray(trimmedList)
        prefs.edit().putString("code_history", jsonArr.toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("code_history").apply()
    }

    // Vault actions
    fun addVaultSnippet(title: String, language: String, tags: List<String>, code: String) {
        vaultManager.addSnippet(title, language, tags, code)
    }

    fun deleteVaultSnippet(id: String) {
        vaultManager.deleteSnippet(id)
    }

    fun toggleVaultFavorite(id: String) {
        vaultManager.toggleFavorite(id)
    }

    fun punchCustomSnippet(snippetCode: String) {
        setCode(snippetCode)
        startPunchSequence()
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        discovery.stopDiscovery()
        client.disconnect()
    }
}
