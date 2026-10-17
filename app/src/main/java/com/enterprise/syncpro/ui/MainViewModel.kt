package com.enterprise.syncpro.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.syncpro.data.ConnectedDeviceEntity
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.data.DiagnosticLogEntity
import com.enterprise.syncpro.data.HubDeduplicationEntity
import com.enterprise.syncpro.data.LocalFileIndexEntity
import com.enterprise.syncpro.data.PairingToken
import com.enterprise.syncpro.data.SyncDatabase
import com.enterprise.syncpro.data.VaultManager
import java.util.Locale
import com.enterprise.syncpro.media.MediaAndQrEngine
import com.enterprise.syncpro.media.MediaBucketSummary
import com.enterprise.syncpro.network.DiscoveredHubEndpoint
import com.enterprise.syncpro.network.HardwareSafetyStatus
import com.enterprise.syncpro.network.NetworkAndSafetyMonitor
import com.enterprise.syncpro.network.RelayQueueTask
import com.enterprise.syncpro.network.RelayTransferEngine
import com.enterprise.syncpro.network.TransferLiveTelemetry
import com.enterprise.syncpro.service.HubForegroundService
import com.enterprise.syncpro.service.SyncWorkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val vault = VaultManager.getInstance(appContext)
    private val db = SyncDatabase.getInstance(appContext)
    private val dao = db.syncDao()
    private val networkMonitor = NetworkAndSafetyMonitor.getInstance(appContext)
    private val relayEngine = RelayTransferEngine.getInstance(appContext)

    val deviceRole: StateFlow<DeviceRole> = vault.deviceRole
    val isDarkMode: StateFlow<Boolean> = vault.isDarkMode
    val showFirstLaunchOnboarding: StateFlow<Boolean> = vault.showFirstLaunchOnboarding
    val pairingToken: StateFlow<PairingToken?> = vault.pairingToken
    val enabledBuckets: StateFlow<Set<String>> = vault.enabledBuckets
    val trustedSsids: StateFlow<Set<String>> = vault.trustedSsids
    val restrictToTrustedWifi: StateFlow<Boolean> = vault.restrictToTrustedWifi
    val scheduleHour: StateFlow<Int> = vault.scheduleHour
    val scheduleMinute: StateFlow<Int> = vault.scheduleMinute
    val daytimeLimitKbps: StateFlow<Int> = vault.daytimeLimitKbps
    val instantCaptureSync: StateFlow<Boolean> = vault.instantCaptureSync
    val devicePriorityWeight: StateFlow<Int> = vault.devicePriorityWeight
    val isPinConfigured: StateFlow<Boolean> = vault.isPinConfigured

    val telemetry: StateFlow<TransferLiveTelemetry> = relayEngine.telemetry
    val safetyStatus: StateFlow<HardwareSafetyStatus> = networkMonitor.safetyStatus
    val uploadQueue: StateFlow<List<RelayQueueTask>> = relayEngine.uploadQueue
    val discoveredHubs: StateFlow<List<DiscoveredHubEndpoint>> = networkMonitor.discoveredHubs
    val localLanIp: StateFlow<String> = networkMonitor.localLanIp
    val tailscaleIp: StateFlow<String?> = networkMonitor.tailscaleIp
    val currentSsid: StateFlow<String?> = networkMonitor.currentSsid
    val isOnWifi: StateFlow<Boolean> = networkMonitor.isOnWifi

    val syncedLocalCount: StateFlow<Int> = dao.observeSyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val syncedLocalBytes: StateFlow<Long> = dao.observeTotalSyncedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val hubReceivedCount: StateFlow<Int> = dao.observeHubReceivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val hubReceivedBytes: StateFlow<Long> = dao.observeHubTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val recentHubFiles: StateFlow<List<HubDeduplicationEntity>> = dao.observeRecentHubFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntity>> = dao.observeDiagnosticLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentUploadedFiles: StateFlow<List<LocalFileIndexEntity>> = dao.observeRecentUploadedFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val connectedDevices: StateFlow<List<ConnectedDeviceEntity>> = dao.observeConnectedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tempCutoffCelsius: StateFlow<Float> = vault.tempCutoffCelsius
    val batteryCutoffPercent: StateFlow<Int> = vault.batteryCutoffPercent

    private val _mediaBuckets = MutableStateFlow<List<MediaBucketSummary>>(emptyList())
    val mediaBuckets: StateFlow<List<MediaBucketSummary>> = _mediaBuckets.asStateFlow()

    private val _hubQrBitmap = MutableStateFlow<Bitmap?>(null)
    val hubQrBitmap: StateFlow<Bitmap?> = _hubQrBitmap.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _exportedConfigJson = MutableStateFlow("")
    val exportedConfigJson: StateFlow<String> = _exportedConfigJson.asStateFlow()

    private val _uiToastMessage = MutableStateFlow<String?>(null)
    val uiToastMessage: StateFlow<String?> = _uiToastMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        if (vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
            relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
            HubForegroundService.startOrUpdate(appContext)
        } else {
            networkMonitor.startMdnsDiscovery()
        }
        SyncWorkers.scheduleDailyAutomatedSync(appContext)
        refreshAllState()
    }

    fun clearToastMessage() {
        _uiToastMessage.value = null
    }

    fun refreshAllState() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                networkMonitor.refreshNetworkInterfaces()
                networkMonitor.evaluateHardwareSafetyGuards(
                    tempCutoff = vault.tempCutoffCelsius.value,
                    batteryCutoff = vault.batteryCutoffPercent.value
                )
                refreshMediaBuckets()
                if (vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
                    regenerateHubPairingQr()
                    val hubId = vault.getOrCreateDeviceId()
                    val secretHex = vault.getOrCreateHubSecretHex()
                    val pinHash = vault.getPinHashOrDefault()
                    networkMonitor.startMdnsHubBroadcast(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT, hubId, secretHex, pinHash)
                    relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
                    HubForegroundService.startOrUpdate(appContext)
                } else {
                    networkMonitor.startMdnsDiscovery()
                }
                _uiToastMessage.value = "Telemetry, media index & network discovery refreshed"
            } finally {
                kotlinx.coroutines.delay(600)
                _isRefreshing.value = false
            }
        }
    }

    fun refreshMediaBuckets() {
        viewModelScope.launch(Dispatchers.IO) {
            val buckets = MediaAndQrEngine.queryAllMediaBuckets(
                appContext,
                vault.devicePriorityWeight.value
            )
            _mediaBuckets.value = buckets
        }
    }

    fun regenerateHubPairingQr(customWanOrTailscaleHost: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            networkMonitor.refreshNetworkInterfaces()
            val hubId = vault.getOrCreateDeviceId()
            val secretHex = vault.getOrCreateHubSecretHex()
            val pinHash = vault.getPinHashOrDefault()
            val lanIp = networkMonitor.localLanIp.value
            val wanHost = customWanOrTailscaleHost?.takeIf { it.isNotBlank() }
                ?: networkMonitor.tailscaleIp.value
                ?: lanIp

            val token = PairingToken(
                hubDeviceId = hubId,
                hubDeviceName = "${Build.MANUFACTURER} ${Build.MODEL} Hub",
                localIp = lanIp,
                tailscaleOrWanHost = wanHost,
                port = NetworkAndSafetyMonitor.DEFAULT_HUB_PORT,
                pinHash = pinHash,
                e2eeSharedSecretHex = secretHex
            )
            if (vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
                vault.savePairingToken(token)
            }
            val jsonPayload = vault.encodePairingTokenToJson(token)
            _hubQrBitmap.value = MediaAndQrEngine.generateQrCodeBitmap(jsonPayload)
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDeviceRole(role)
            if (role == DeviceRole.SECONDARY_HUB) {
                regenerateHubPairingQr()
                val hubId = vault.getOrCreateDeviceId()
                val secretHex = vault.getOrCreateHubSecretHex()
                val pinHash = vault.getPinHashOrDefault()
                networkMonitor.startMdnsHubBroadcast(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT, hubId, secretHex, pinHash)
                relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
                HubForegroundService.startOrUpdate(appContext)
            } else {
                relayEngine.stopSecondaryHubServer()
                networkMonitor.stopMdnsHubBroadcast()
                networkMonitor.startMdnsDiscovery()
            }
            relayEngine.logEvent("INFO", "RoleEngine", "Switched active role to ${role.name}")
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDarkMode(enabled)
        }
    }

    fun dismissFirstLaunchOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            vault.completeFirstLaunchOnboarding()
        }
    }

    fun unlockVaultViaBiometricSuccess() {
        _isVaultUnlocked.value = true
    }

    fun initializeVaultPin(pin: String, confirmPin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (pin.length != 6 || !pin.all(Char::isDigit)) {
                _uiToastMessage.value = "PIN must be exactly 6 digits"
                return@launch
            }
            if (pin != confirmPin) {
                _uiToastMessage.value = "PINs do not match"
                return@launch
            }
            val ok = vault.configurePin(pin)
            if (ok) {
                _isVaultUnlocked.value = true
                regenerateHubPairingQr()
                _uiToastMessage.value = "Vault PIN Set"
            } else {
                _uiToastMessage.value = "Failed to configure PIN"
            }
        }
    }

    fun verifyVaultPin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val verified = vault.verifyPin(pin)
            if (verified) {
                _isVaultUnlocked.value = true
                _uiToastMessage.value = "Vault Unlocked"
            } else {
                _uiToastMessage.value = "Incorrect PIN"
            }
        }
    }

    fun verifyOrSetVaultPin(pin: String) {
        if (!vault.isPinConfigured.value) {
            initializeVaultPin(pin, pin)
        } else {
            verifyVaultPin(pin)
        }
    }

    fun onQrPayloadScanned(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = vault.decodePairingTokenFromJson(rawJson)
            if (token != null) {
                vault.savePairingToken(token)
                relayEngine.logEvent(
                    "SECURITY",
                    "QRPairing",
                    "Paired with Hub ${token.hubDeviceName} (${token.localIp} / ${token.tailscaleOrWanHost})"
                )
                _uiToastMessage.value = "Paired with ${token.hubDeviceName}"
            } else {
                _uiToastMessage.value = "Invalid QR Code"
            }
        }
    }

    fun pairWithDiscoveredMdnsHub(endpoint: DiscoveredHubEndpoint, sharedSecretHex: String, pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveSecret = if (sharedSecretHex.isNotBlank()) {
                sharedSecretHex
            } else if (endpoint.secretHex.isNotBlank()) {
                endpoint.secretHex
            } else {
                pairingToken.value?.e2eeSharedSecretHex ?: ""
            }
            val pinHash = if (pin.length == 6) {
                com.enterprise.syncpro.crypto.CryptoEngine.sha256String("SYNCPRO_VAULT_SALT:$pin")
            } else if (endpoint.pinHash.isNotBlank()) {
                endpoint.pinHash
            } else {
                vault.getPinHashOrDefault()
            }
            val token = PairingToken(
                hubDeviceId = endpoint.serviceName,
                hubDeviceName = endpoint.serviceName,
                localIp = endpoint.hostAddress,
                tailscaleOrWanHost = networkMonitor.tailscaleIp.value ?: endpoint.hostAddress,
                port = endpoint.port,
                pinHash = pinHash,
                e2eeSharedSecretHex = effectiveSecret
            )
            vault.savePairingToken(token)
            _uiToastMessage.value = "Connected to ${endpoint.serviceName}"
        }
    }

    fun updatePairedHubHost(customHost: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = customHost.trim()
            if (trimmed.isBlank()) return@launch
            val current = vault.pairingToken.value
            if (current != null) {
                val updated = current.copy(
                    localIp = if (trimmed.contains('.')) trimmed else current.localIp,
                    tailscaleOrWanHost = trimmed
                )
                vault.savePairingToken(updated)
                _uiToastMessage.value = "Updated Hub Route: $trimmed"
            } else {
                val token = PairingToken(
                    hubDeviceId = "ManualHub",
                    hubDeviceName = "Secondary Hub ($trimmed)",
                    localIp = trimmed,
                    tailscaleOrWanHost = trimmed,
                    port = NetworkAndSafetyMonitor.DEFAULT_HUB_PORT,
                    pinHash = vault.getPinHashOrDefault(),
                    e2eeSharedSecretHex = vault.getOrCreateHubSecretHex()
                )
                vault.savePairingToken(token)
                _uiToastMessage.value = "Set Hub Route: $trimmed"
            }
        }
    }

    fun startFullBatchSyncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            relayEngine.setPaused(false)
            val tasks = MediaAndQrEngine.scanEnabledBucketsForRelay(
                appContext,
                vault.enabledBuckets.value,
                vault.devicePriorityWeight.value
            )
            if (tasks.isEmpty()) {
                _uiToastMessage.value = "No media files in folders"
            } else {
                relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = false)
                _uiToastMessage.value = "Queued ${tasks.size} files"
            }
        }
    }

    /**
     * Urgent Queue-Jumping (Instant Upload): Enqueues specific folder or file with Priority Weight 5.
     */
    fun triggerUrgentInstantUpload(tasks: List<RelayQueueTask>, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tasks.isEmpty()) {
                _uiToastMessage.value = "No files in $label"
                return@launch
            }
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = true)
            relayEngine.logEvent(
                "INFO",
                "UrgentQueueJump",
                "Express Priority-5 upload triggered for $label (${tasks.size} items)"
            )
            _uiToastMessage.value = "Express upload queued: ${tasks.size} items"
        }
    }

    fun togglePauseResume() {
        relayEngine.togglePause()
    }

    fun stopSync() {
        relayEngine.clearQueueAndStop()
        _uiToastMessage.value = "Sync Stopped"
    }

    fun removeFromQueue(filePath: String) {
        relayEngine.removeTaskFromQueue(filePath)
    }

    fun reprioritizeInQueue(filePath: String, newPriority: Int) {
        relayEngine.reprioritizeTask(filePath, newPriority)
    }

    fun toggleBucketSelection(bucketName: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.toggleBucketFilter(bucketName, enabled)
            if (!enabled) {
                relayEngine.removeTasksForBucket(bucketName)
            }
        }
    }

    fun format12HourTime(hour: Int, minute: Int): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.US, "%02d:%02d %s", h, minute, amPm)
    }

    fun updateDailySchedule(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDailySchedule(hour, minute)
            SyncWorkers.scheduleDailyAutomatedSync(appContext)
            val delayMin = SyncWorkers.computeInitialDelayMillis(hour, minute) / 60_000L
            val formatted = format12HourTime(hour, minute)
            relayEngine.logEvent(
                "INFO",
                "Scheduler",
                "Scheduled 12h automated sync for $formatted (in $delayMin mins)"
            )
            _uiToastMessage.value = "Sync set for $formatted (starts in $delayMin min)"
        }
    }

    fun updateDaytimeBandwidthLimit(kbps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDaytimeLimitKbps(kbps)
            val label = if (kbps <= 0) "Unlimited (Full Speed)" else "${kbps / 1024} MB/s"
            _uiToastMessage.value = "Bandwidth Limit: $label"
        }
    }

    fun setTempCutoff(temp: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setTempCutoff(temp)
            _uiToastMessage.value = "Thermal cutoff set to ${String.format(Locale.US, "%.1f", temp)}°C"
        }
    }

    fun setBatteryCutoff(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setBatteryCutoff(percent)
            _uiToastMessage.value = "Battery cutoff set to $percent%"
        }
    }

    fun updateDeviceBandwidth(deviceId: String, bandwidthLimitKbps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDeviceBandwidth(deviceId, bandwidthLimitKbps)
            val limitStr = if (bandwidthLimitKbps <= 0) "Unlimited" else "${bandwidthLimitKbps / 1024} MB/s"
            _uiToastMessage.value = "Allocated $limitStr bandwidth for device"
        }
    }

    fun triggerUploadOnlyThisFolder(tasks: List<RelayQueueTask>, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folderTasks = tasks.filter { it.bucketName.equals(folderName, ignoreCase = true) }
            if (folderTasks.isEmpty()) {
                _uiToastMessage.value = "No files in $folderName to upload"
                return@launch
            }
            relayEngine.clearQueueAndStop()
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(folderTasks, isUrgentInstantUpload = true)
            HubForegroundService.startOrUpdate(appContext)
            _uiToastMessage.value = "Uploading ${folderTasks.size} files from $folderName only"
        }
    }

    fun updateDevicePriorityWeight(weight: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDevicePriorityWeight(weight)
            refreshMediaBuckets()
            _uiToastMessage.value = "Device priority weight set to $weight"
        }
    }

    fun toggleInstantCapture(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setInstantCaptureSync(enabled)
            if (enabled) {
                HubForegroundService.startOrUpdate(appContext)
            }
            _uiToastMessage.value = if (enabled) "Instant Camera Capture: Active" else "Instant Camera Capture: Disabled"
        }
    }

    fun addTrustedSsid(ssid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.addTrustedSsid(ssid)
        }
    }

    fun removeTrustedSsid(ssid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.removeTrustedSsid(ssid)
        }
    }

    fun setRestrictToTrustedWifi(restrict: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setRestrictToTrustedWifi(restrict)
        }
    }

    fun prepareExportConfigJson() {
        viewModelScope.launch(Dispatchers.IO) {
            _exportedConfigJson.value = vault.exportConfigurationJson()
        }
    }

    fun importConfigJson(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = vault.importConfigurationJson(rawJson)
            if (ok) {
                SyncWorkers.scheduleDailyAutomatedSync(appContext)
                refreshAllState()
                _uiToastMessage.value = "Enterprise Configuration Imported Successfully"
            } else {
                _uiToastMessage.value = "Invalid JSON Configuration Payload"
            }
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearDiagnosticLogs()
        }
    }
}