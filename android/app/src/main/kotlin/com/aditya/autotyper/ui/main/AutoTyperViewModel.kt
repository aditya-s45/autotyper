package com.aditya.autotyper.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.autotyper.discovery.AutoTyperDiscovery
import com.aditya.autotyper.discovery.DiscoveredDevice
import com.aditya.autotyper.discovery.DiscoveryStatus
import com.aditya.autotyper.network.AutoTyperClient
import com.aditya.autotyper.network.CompanionSyncState
import com.aditya.autotyper.network.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AutoTyperViewModel(application: Application) : AndroidViewModel(application) {
    val discovery = AutoTyperDiscovery(application)
    val client = AutoTyperClient()

    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val syncState: StateFlow<CompanionSyncState> = client.syncState
    val discoveryStatus: StateFlow<DiscoveryStatus> = discovery.discoveryStatus
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discovery.discoveredDevices

    init {
        // Automatically connect to the first verified laptop discovered
        viewModelScope.launch {
            discovery.discoveredDevices.collect { devices ->
                if (client.connectionState.value !is ConnectionState.Connected && devices.isNotEmpty()) {
                    val target = devices.first()
                    client.connect(target.host, target.wsPort, target.wsPath, target.name)
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

    fun punch(
        code: String,
        wpm: Int,
        focusDelay: Int,
        smartIndent: Boolean,
        oopsMode: Boolean,
        humanJitter: Boolean,
        fastPaste: Boolean
    ) {
        client.sendPunch(code, wpm, focusDelay, smartIndent, oopsMode, humanJitter, fastPaste)
    }

    fun stop() {
        client.sendStop()
    }

    fun updateCode(code: String) {
        client.sendUpdateCode(code)
    }

    fun updateSettings(
        wpm: Int,
        focusDelay: Int,
        smartIndent: Boolean,
        oopsMode: Boolean,
        humanJitter: Boolean,
        fastPaste: Boolean
    ) {
        client.sendUpdateSettings(wpm, focusDelay, smartIndent, oopsMode, humanJitter, fastPaste)
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stopDiscovery()
        client.disconnect()
    }
}
