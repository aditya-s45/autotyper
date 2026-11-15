package com.aditya.autotyper.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int = 8080,
    val wsPort: Int = 8080,
    val wsPath: String = "/ws",
    val isAutoConnected: Boolean = false
)

sealed class DiscoveryStatus {
    object Idle : DiscoveryStatus()
    object Searching : DiscoveryStatus()
    data class Found(val device: DiscoveredDevice) : DiscoveryStatus()
    data class Connected(val device: DiscoveredDevice) : DiscoveryStatus()
    data class Error(val message: String) : DiscoveryStatus()
}

class AutoTyperDiscovery(private val context: Context) {
    private val tag = "AutoTyperDiscovery"
    private val serviceType = "_autotyper._tcp."
    
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("autotyper_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val _discoveryStatus = MutableStateFlow<DiscoveryStatus>(DiscoveryStatus.Idle)
    val discoveryStatus: StateFlow<DiscoveryStatus> = _discoveryStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        _discoveryStatus.value = DiscoveryStatus.Searching
        _discoveredDevices.value = emptyList()

        // 1. Acquire Multicast Lock so Android Wi-Fi driver receives mDNS multicast packets
        try {
            multicastLock = wifiManager?.createMulticastLock("AutoTyperMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire multicast lock: ${e.message}")
        }

        // 2. Fast-Resume: Probe previously remembered device first for instant sub-second reconnect
        val cachedHost = prefs.getString("last_laptop_host", null)
        val cachedPort = prefs.getInt("last_laptop_port", 8080)
        if (!cachedHost.isNullOrBlank()) {
            scope.launch {
                pingDevice(cachedHost, cachedPort, isFastResume = true)
            }
        }

        // 3. Start NSD Discovery
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}, type: ${service.serviceType}")
                if (service.serviceType.contains("autotyper")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped: $serviceType")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed to start: Error code: $errorCode")
                _discoveryStatus.value = DiscoveryStatus.Error("NSD start failed ($errorCode)")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed to stop: Error code: $errorCode")
            }
        }

        discoveryListener = listener
        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to invoke discoverServices: ${e.message}")
            _discoveryStatus.value = DiscoveryStatus.Error(e.message ?: "Discovery error")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val host = resolvedService.host?.hostAddress ?: return
                val port = if (resolvedService.port > 0) resolvedService.port else 8080
                Log.d(tag, "Resolved ${resolvedService.serviceName} at $host:$port")

                scope.launch {
                    pingDevice(host, port, isFastResume = false)
                }
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception during resolveService: ${e.message}")
        }
    }

    suspend fun pingDevice(host: String, port: Int, isFastResume: Boolean) = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/ping"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status") == "ok") {
                        val hostname = json.optString("hostname", host)
                        val wsPort = json.optInt("ws_port", port)
                        val wsPath = json.optString("ws_path", "/ws")

                        val device = DiscoveredDevice(
                            name = hostname,
                            host = host,
                            port = port,
                            wsPort = wsPort,
                            wsPath = wsPath,
                            isAutoConnected = isFastResume
                        )

                        // Save in SharedPreferences for subsequent instant resume
                        prefs.edit()
                            .putString("last_laptop_host", host)
                            .putInt("last_laptop_port", port)
                            .putString("last_laptop_name", hostname)
                            .apply()

                        val currentList = _discoveredDevices.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.host == host }
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = device
                        } else {
                            currentList.add(device)
                        }
                        _discoveredDevices.value = currentList
                        _discoveryStatus.value = DiscoveryStatus.Found(device)
                        Log.i(tag, "AutoTyper companion verified at $host:$port ($hostname)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Ping to $host:$port failed: ${e.message}")
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        isDiscovering = false
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping NSD: ${e.message}")
        }
        discoveryListener = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing MulticastLock: ${e.message}")
        }
        multicastLock = null

        if (_discoveryStatus.value is DiscoveryStatus.Searching) {
            _discoveryStatus.value = DiscoveryStatus.Idle
        }
    }
}
