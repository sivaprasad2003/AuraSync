package com.enterprise.syncpro.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.data.DiagnosticLogEntity
import com.enterprise.syncpro.data.HubDeduplicationEntity
import com.enterprise.syncpro.data.PairingToken
import com.enterprise.syncpro.data.SyncDatabase
import com.enterprise.syncpro.data.VaultManager
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

    init {
        HubForegroundService.startOrUpdate(appContext)
        SyncWorkers.scheduleDailyAutomatedSync(appContext)
        refreshAllState()
    }

    fun clearToastMessage() {
        _uiToastMessage.value = null
    }

    fun refreshAllState() {
        viewModelScope.launch(Dispatchers.IO) {
            networkMonitor.refreshNetworkInterfaces()
            networkMonitor.evaluateHardwareSafetyGuards()
            refreshMediaBuckets()
            regenerateHubPairingQr()
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

    fun verifyOrSetVaultPin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!vault.isPinConfigured.value) {
                val ok = vault.configurePin(pin)
                if (ok) {
                    _isVaultUnlocked.value = true
                    regenerateHubPairingQr()
                    _uiToastMessage.value = "6-Digit Vault PIN Configured & Encrypted"
                } else {
                    _uiToastMessage.value = "PIN must be exactly 6 digits"
                }
            } else {
                val verified = vault.verifyPin(pin)
                if (verified) {
                    _isVaultUnlocked.value = true
                    _uiToastMessage.value = "Cryptographic Vault Unlocked"
                } else {
                    _uiToastMessage.value = "Invalid 6-Digit PIN"
                }
            }
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
                _uiToastMessage.value = "Paired with ${token.hubDeviceName}!"
            } else {
                _uiToastMessage.value = "Invalid Sync Pro QR Pairing Token"
            }
        }
    }

    fun pairWithDiscoveredMdnsHub(endpoint: DiscoveredHubEndpoint, sharedSecretHex: String, pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pinHash = if (pin.length == 6) {
                com.enterprise.syncpro.crypto.CryptoEngine.sha256String("SYNCPRO_VAULT_SALT:$pin")
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
                e2eeSharedSecretHex = sharedSecretHex
            )
            vault.savePairingToken(token)
            _uiToastMessage.value = "Connected to mDNS Hub ${endpoint.serviceName}"
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
                _uiToastMessage.value = "No media files found in checked source folders"
            } else {
                relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = false)
                _uiToastMessage.value = "Enqueued ${tasks.size} files for SHA-256 deduplicated relay"
            }
        }
    }

    /**
     * Urgent Queue-Jumping (Instant Upload): Enqueues specific folder or file with Priority Weight 5.
     */
    fun triggerUrgentInstantUpload(tasks: List<RelayQueueTask>, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tasks.isEmpty()) {
                _uiToastMessage.value = "No files in $label to upload"
                return@launch
            }
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = true)
            relayEngine.logEvent(
                "INFO",
                "UrgentQueueJump",
                "Express Priority-5 upload triggered for $label (${tasks.size} items)"
            )
            _uiToastMessage.value = "Urgent Queue-Jump: Uploading ${tasks.size} items from $label immediately"
        }
    }

    fun togglePauseResume() {
        relayEngine.togglePause()
    }

    fun stopSync() {
        relayEngine.clearQueueAndStop()
        _uiToastMessage.value = "Sync Stopped & Upload Queue Cleared"
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
        }
    }

    fun updateDailySchedule(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDailySchedule(hour, minute)
            SyncWorkers.scheduleDailyAutomatedSync(appContext)
            val delayMin = SyncWorkers.computeInitialDelayMillis(hour, minute) / 60_000L
            relayEngine.logEvent(
                "INFO",
                "Scheduler",
                "Scheduled daily automated sync for %02d:%02d (in %d mins)".format(hour, minute, delayMin)
            )
            _uiToastMessage.value = "Daily Sync set for %02d:%02d (runs in %d min)".format(hour, minute, delayMin)
        }
    }

    fun updateDaytimeBandwidthLimit(kbps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDaytimeLimitKbps(kbps)
        }
    }

    fun updateDevicePriorityWeight(weight: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDevicePriorityWeight(weight)
            refreshMediaBuckets()
        }
    }

    fun toggleInstantCapture(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setInstantCaptureSync(enabled)
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