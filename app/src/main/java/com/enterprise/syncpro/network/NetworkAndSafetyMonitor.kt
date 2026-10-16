package com.enterprise.syncpro.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

data class DiscoveredHubEndpoint(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val isMeshOrWan: Boolean = false
)

data class HardwareSafetyStatus(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = true,
    val temperatureCelsius: Float = 28.0f,
    val availableStorageBytes: Long = 10L * 1024 * 1024 * 1024,
    val isThermalTripped: Boolean = false,
    val isLowBatteryTripped: Boolean = false,
    val isLowStorageTripped: Boolean = false
) {
    val isSafeToSync: Boolean
        get() = !isThermalTripped && !isLowBatteryTripped && !isLowStorageTripped
}

class NetworkAndSafetyMonitor private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredHubs = MutableStateFlow<List<DiscoveredHubEndpoint>>(emptyList())
    val discoveredHubs: StateFlow<List<DiscoveredHubEndpoint>> = _discoveredHubs.asStateFlow()

    private val _localLanIp = MutableStateFlow("0.0.0.0")
    val localLanIp: StateFlow<String> = _localLanIp.asStateFlow()

    private val _tailscaleIp = MutableStateFlow<String?>(null)
    val tailscaleIp: StateFlow<String?> = _tailscaleIp.asStateFlow()

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid: StateFlow<String?> = _currentSsid.asStateFlow()

    private val _isOnWifi = MutableStateFlow(false)
    val isOnWifi: StateFlow<Boolean> = _isOnWifi.asStateFlow()

    private val _networkHandoverEpoch = MutableStateFlow(0L)
    val networkHandoverEpoch: StateFlow<Long> = _networkHandoverEpoch.asStateFlow()

    private val _safetyStatus = MutableStateFlow(HardwareSafetyStatus())
    val safetyStatus: StateFlow<HardwareSafetyStatus> = _safetyStatus.asStateFlow()

    init {
        registerNetworkCallback()
        scope.launch {
            refreshNetworkInterfaces()
            evaluateHardwareSafetyGuards()
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        refreshNetworkInterfaces()
                        _networkHandoverEpoch.value = System.currentTimeMillis()
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    scope.launch {
                        _isOnWifi.value = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                            val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                                _currentSsid.value = ssid
                            }
                        }
                        refreshNetworkInterfaces()
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch {
                        refreshNetworkInterfaces()
                        _networkHandoverEpoch.value = System.currentTimeMillis()
                    }
                }
            }
        )
    }

    /**
     * Scans all active network interfaces on Dispatchers.IO for LAN IPv4 (192.168.x.x / 10.x.x.x)
     * and Tailscale / ZeroTier Mesh VPN interfaces (100.x.x.x / tailscale0 / tun0 / zt0).
     */
    suspend fun refreshNetworkInterfaces() = withContext(Dispatchers.IO) {
        runCatching {
            var foundLan = "0.0.0.0"
            var foundTailscale: String? = null

            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val firstOctet = ip.substringBefore('.').toIntOrNull() ?: 0
                        if (firstOctet == 100 || name.contains("tailscale") || name.contains("zt") || name.contains("tun")) {
                            foundTailscale = ip
                        } else if (foundLan == "0.0.0.0") {
                            foundLan = ip
                        }
                    }
                }
            }
            _localLanIp.value = foundLan
            _tailscaleIp.value = foundTailscale

            // Fallback SSID lookup via WifiManager
            @Suppress("DEPRECATION")
            val legacySsid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
            if (!legacySsid.isNullOrBlank() && legacySsid != "<unknown ssid>") {
                _currentSsid.value = legacySsid
            }
        }
    }

    /**
     * Evaluates Thermal (> 45.0 C), Battery (< 15% unplugged), and Storage (< 1GB free) safety guards.
     */
    suspend fun evaluateHardwareSafetyGuards(): HardwareSafetyStatus = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
        val tempCelsius = tempTenths / 10.0f

        val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val availableBytes = statFs.availableBytes

        val thermalTripped = tempCelsius > 45.0f
        val lowBatteryTripped = !isCharging && batteryPct < 15
        val lowStorageTripped = availableBytes < ONE_GIGABYTE_BYTES

        val result = HardwareSafetyStatus(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            temperatureCelsius = tempCelsius,
            availableStorageBytes = availableBytes,
            isThermalTripped = thermalTripped,
            isLowBatteryTripped = lowBatteryTripped,
            isLowStorageTripped = lowStorageTripped
        )
        _safetyStatus.value = result
        result
    }

    /**
     * Checks whether automated background sync is allowed under Trusted Wi-Fi SSID Whitelisting.
     */
    fun isTrustedWifiAllowed(restrictEnabled: Boolean, trustedSsids: Set<String>): Boolean {
        if (!restrictEnabled || trustedSsids.isEmpty()) return true
        if (!_isOnWifi.value) return false
        val activeSsid = _currentSsid.value?.removeSurrounding("\"") ?: return false
        return trustedSsids.any { it.equals(activeSsid, ignoreCase = true) }
    }

    /**
     * Registers `_syncvault._tcp.` mDNS service on the Secondary Hub.
     */
    fun startMdnsHubBroadcast(port: Int, hubDeviceId: String) {
        stopMdnsHubBroadcast()
        runCatching {
            multicastLock = wifiManager.createMulticastLock("SyncProMdnsLock").apply {
                setReferenceCounted(true)
                acquire()
            }
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "SyncProHub_${hubDeviceId.take(6)}"
                serviceType = MDNS_SERVICE_TYPE
                setPort(port)
            }
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    fun stopMdnsHubBroadcast() {
        runCatching {
            registrationListener?.let { nsdManager.unregisterService(it) }
        }
        registrationListener = null
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
    }

    /**
     * Discovers `_syncvault._tcp.` Secondary Hubs on local Wi-Fi.
     */
    fun startMdnsDiscovery() {
        stopMdnsDiscovery()
        runCatching {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("SyncProDiscoveryLock").apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld == false) multicastLock?.acquire()

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("_syncvault._tcp")) {
                        resolveDiscoveredService(service)
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    _discoveredHubs.value = _discoveredHubs.value.filterNot {
                        it.serviceName == service.serviceName
                    }
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            nsdManager.discoverServices(MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    private fun resolveDiscoveredService(service: NsdServiceInfo) {
        runCatching {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val hostIp = resolvedInfo.host?.hostAddress ?: return
                    val endpoint = DiscoveredHubEndpoint(
                        serviceName = resolvedInfo.serviceName,
                        hostAddress = hostIp,
                        port = resolvedInfo.port,
                        isMeshOrWan = hostIp.startsWith("100.")
                    )
                    val updated = (_discoveredHubs.value + endpoint)
                        .distinctBy { "${it.hostAddress}:${it.port}" }
                    _discoveredHubs.value = updated
                }
            })
        }
    }

    fun stopMdnsDiscovery() {
        runCatching {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        }
        discoveryListener = null
    }

    companion object {
        const val MDNS_SERVICE_TYPE = "_syncvault._tcp."
        const val DEFAULT_HUB_PORT = 49210
        const val ONE_GIGABYTE_BYTES = 1024L * 1024L * 1024L

        @Volatile
        private var INSTANCE: NetworkAndSafetyMonitor? = null

        fun getInstance(context: Context): NetworkAndSafetyMonitor {
            return INSTANCE ?: synchronized(this) {
                val instance = NetworkAndSafetyMonitor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}