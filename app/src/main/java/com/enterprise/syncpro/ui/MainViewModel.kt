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
import kotlin.math.log10
import kotlin.math.pow
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    fun getPinLockoutRemainingMs(): Long = vault.getPinLockoutRemainingMs()

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
    val throttleStartHour: StateFlow<Int> = vault.throttleStartHour
    val throttleStartMinute: StateFlow<Int> = vault.throttleStartMinute
    val throttleEndHour: StateFlow<Int> = vault.throttleEndHour
    val throttleEndMinute: StateFlow<Int> = vault.throttleEndMinute
    val storageCutoffGb: StateFlow<Float> = vault.storageCutoffGb

    private val _mediaBuckets = MutableStateFlow<List<MediaBucketSummary>>(emptyList())
    val mediaBuckets: StateFlow<List<MediaBucketSummary>> = _mediaBuckets.asStateFlow()

    private val _hubQrBitmap = MutableStateFlow<Bitmap?>(null)
    val hubQrBitmap: StateFlow<Bitmap?> = _hubQrBitmap.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _exportedConfigJson = MutableStateFlow("")
    val exportedConfigJson: StateFlow<String> = _exportedConfigJson.asStateFlow()

    private val _toastEvent = Channel<String>(Channel.BUFFERED)
    val toastFlow: Flow<String> = _toastEvent.receiveAsFlow()

    private val _myDeviceId = MutableStateFlow("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    val myFriendlyName: String = vault.getDeviceFriendlyName()

    fun postToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.send(msg)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        relayEngine.onSafetyAlertReceived = { alertText ->
            postToast(alertText)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _myDeviceId.value = vault.getOrCreateDeviceId()
        }
        if (vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
            relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
            HubForegroundService.startOrUpdate(appContext)
        } else {
            networkMonitor.startMdnsDiscovery()
        }
        SyncWorkers.scheduleDailyAutomatedSync(appContext)
        refreshAllState()
        refreshConnectedDevices()

        // Real-time peer registration heartbeat (runs every 8s while active)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(8000)
                if (vault.deviceRole.value == DeviceRole.PRIMARY_SENDER && vault.pairingToken.value != null) {
                    refreshConnectedDevices()
                }
            }
        }
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
                    networkMonitor.startMdnsHubBroadcast(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT, hubId)
                    relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
                    HubForegroundService.startOrUpdate(appContext)
                } else {
                    networkMonitor.startMdnsDiscovery()
                }
                refreshConnectedDevices()
                postToast("Telemetry, media index & network discovery refreshed")
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
                networkMonitor.startMdnsHubBroadcast(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT, hubId)
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

    fun lockVault() {
        _isVaultUnlocked.value = false
        postToast("Vault Locked")
    }

    fun initializeVaultPin(pin: String, confirmPin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (pin.length != 6 || !pin.all(Char::isDigit)) {
                postToast("PIN must be exactly 6 digits")
                return@launch
            }
            if (pin != confirmPin) {
                postToast("PINs do not match")
                return@launch
            }
            val ok = vault.configurePin(pin)
            if (ok) {
                _isVaultUnlocked.value = true
                regenerateHubPairingQr()
                postToast("Vault PIN Set")
            } else {
                postToast("Failed to configure PIN")
            }
        }
    }

    fun verifyVaultPin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val verified = vault.verifyPin(pin)
            if (verified) {
                _isVaultUnlocked.value = true
                postToast("Vault Unlocked")
            } else {
                postToast("Incorrect PIN")
            }
        }
    }

    fun verifyOrSetVaultPin(pin: String) {
        // Only use for verification of an already-configured PIN.
        // PIN initialization always requires separate confirm input from the UI.
        if (vault.isPinConfigured.value) {
            verifyVaultPin(pin)
        } else {
            // Should not reach here from UI — UI shows two separate fields for new PIN
            postToast("Please use the Set PIN form with confirmation")
        }
    }

    fun changeVaultPin(currentPin: String, newPin: String, confirmNewPin: String) {
        if (newPin != confirmNewPin) {
            postToast("New PIN and confirmation do not match")
            return
        }
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            postToast("PIN must be exactly 6 numeric digits")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = vault.changePin(currentPin, newPin)
            if (success) {
                postToast("Vault PIN updated successfully")
            } else {
                val lockoutMs = vault.getPinLockoutRemainingMs()
                if (lockoutMs > 0) {
                    postToast("Too many failed attempts. Locked for ${lockoutMs / 1000}s")
                } else {
                    postToast("Current PIN is incorrect")
                }
            }
        }
    }

    fun onQrPayloadScanned(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = vault.decodePairingTokenFromJson(rawJson)
            if (token != null) {
                vault.savePairingToken(token)
                refreshConnectedDevices()
                relayEngine.logEvent(
                    "SECURITY",
                    "QRPairing",
                    "Paired with Hub ${token.hubDeviceName} (${token.localIp} / ${token.tailscaleOrWanHost})"
                )
                postToast("Paired with ${token.hubDeviceName}")
            } else {
                postToast("Invalid QR Code")
            }
        }
    }

    fun pairWithDiscoveredMdnsHub(endpoint: DiscoveredHubEndpoint, sharedSecretHex: String, pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveSecret = if (sharedSecretHex.isNotBlank()) {
                sharedSecretHex
            } else {
                pairingToken.value?.e2eeSharedSecretHex ?: vault.getOrCreateHubSecretHex()
            }
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
                e2eeSharedSecretHex = effectiveSecret
            )
            vault.savePairingToken(token)
            refreshConnectedDevices()
            postToast("Connected to ${endpoint.serviceName}")
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
                refreshConnectedDevices()
                postToast("Updated Hub Route: $trimmed")
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
                refreshConnectedDevices()
                postToast("Set Hub Route: $trimmed")
            }
        }
    }

    fun startFullBatchSyncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val safety = networkMonitor.evaluateHardwareSafetyGuards(
                tempCutoff = vault.tempCutoffCelsius.value,
                batteryCutoff = vault.batteryCutoffPercent.value,
                storageCutoffGb = vault.storageCutoffGb.value
            )
            if (!safety.isSafeToSync) {
                val reason = when {
                    safety.isThermalTripped -> "Battery temperature (${String.format(Locale.US, "%.1f", safety.temperatureCelsius)}°C) exceeds cutoff (${vault.tempCutoffCelsius.value}°C)"
                    safety.isLowBatteryTripped -> "Battery (${safety.batteryPercent}%) is below cutoff (${vault.batteryCutoffPercent.value}%)"
                    else -> "Free storage (${formatBytes(safety.availableStorageBytes)}) is below cutoff (${vault.storageCutoffGb.value} GB)"
                }
                postToast("Sync blocked: $reason")
                val token = vault.pairingToken.value
                if (token != null) {
                    relayEngine.sendSafetyAlertToHub(
                        token = token,
                        deviceName = vault.getDeviceFriendlyName(),
                        reason = reason,
                        value = ""
                    )
                }
                return@launch
            }

            relayEngine.setPaused(false)
            val tasks = MediaAndQrEngine.scanEnabledBucketsForRelay(
                appContext,
                vault.enabledBuckets.value,
                vault.devicePriorityWeight.value
            )
            if (tasks.isEmpty()) {
                postToast("No media files in selected folders")
            } else {
                relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = false)
                refreshConnectedDevices()
                postToast("Queued ${tasks.size} files for sync")
            }
        }
    }

    /**
     * Urgent Queue-Jumping (Instant Upload): Enqueues specific folder or file with Priority Weight 5.
     */
    fun triggerUrgentInstantUpload(tasks: List<RelayQueueTask>, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tasks.isEmpty()) {
                postToast("No files in $label")
                return@launch
            }
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = true)
            refreshConnectedDevices()
            relayEngine.logEvent(
                "INFO",
                "UrgentQueueJump",
                "Express Priority-5 upload triggered for $label (${tasks.size} items)"
            )
            postToast("Express upload queued: ${tasks.size} items")
        }
    }

    fun togglePauseResume() {
        val isPaused = relayEngine.togglePause()
        postToast(if (isPaused) "Sync Paused" else "Sync Resumed")
    }

    fun stopSync() {
        relayEngine.clearQueueAndStop()
        postToast("Sync Stopped")
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
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val h = if (safeHour == 0) 12 else if (safeHour > 12) safeHour - 12 else safeHour
        val amPm = if (safeHour < 12) "AM" else "PM"
        return String.format(Locale.US, "%02d:%02d %s", h, safeMinute, amPm)
    }

    fun updateDailySchedule(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDailySchedule(hour, minute)
            SyncWorkers.scheduleDailyAutomatedSync(appContext, forceReenqueue = true)
            val delayMin = SyncWorkers.computeInitialDelayMillis(hour, minute) / 60_000L
            val formatted = format12HourTime(hour, minute)
            relayEngine.logEvent(
                "INFO",
                "Scheduler",
                "Scheduled 24h automated sync for $formatted (in $delayMin mins)"
            )
            val role = vault.deviceRole.value
            val token = vault.pairingToken.value
            val bandwidth = vault.daytimeLimitKbps.value
            val now = vault.configTimestamp.value

            // Global sync: update all devices in Room
            dao.updateAllDevicesScheduleAndBandwidth(bandwidth, hour, minute, formatted, now)

            if (role == DeviceRole.PRIMARY_SENDER && token != null) {
                relayEngine.syncDeviceConfigToHub(token, "ALL", bandwidth, hour, minute, now)
            }
            refreshConnectedDevices()
            postToast("Sync set for $formatted (starts in $delayMin min)")
        }
    }

    fun updateDaytimeBandwidthLimit(kbps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDaytimeLimitKbps(kbps)
            val label = if (kbps <= 0) "Unlimited (Full Speed)" else "${kbps / 1024} MB/s"
            val role = vault.deviceRole.value
            val token = vault.pairingToken.value
            val hour = vault.scheduleHour.value
            val minute = vault.scheduleMinute.value
            val formatted = format12HourTime(hour, minute)
            val now = vault.configTimestamp.value

            // Global sync: update all devices in Room
            dao.updateAllDevicesScheduleAndBandwidth(kbps, hour, minute, formatted, now)

            if (role == DeviceRole.PRIMARY_SENDER && token != null) {
                relayEngine.syncDeviceConfigToHub(token, "ALL", kbps, hour, minute, now)
            }
            refreshConnectedDevices()
            postToast("Bandwidth Limit: $label")
        }
    }

    fun setStorageCutoffGb(gb: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setStorageCutoffGb(gb)
            postToast("Storage cutoff threshold set to ${String.format(Locale.US, "%.1f", gb)} GB")
        }
    }

    fun updateBandwidthThrottleWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setThrottleWindow(startHour, startMinute, endHour, endMinute)
            val startStr = format12HourTime(startHour, startMinute)
            val endStr = format12HourTime(endHour, endMinute)
            postToast("Throttle Window: $startStr to $endStr")
        }
    }

    fun setTempCutoff(temp: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setTempCutoff(temp)
            postToast("Thermal cutoff set to ${String.format(Locale.US, "%.1f", temp)}°C")
        }
    }

    fun setBatteryCutoff(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setBatteryCutoff(percent)
            postToast("Battery cutoff set to $percent%")
        }
    }

    fun updateDeviceBandwidth(deviceId: String, bandwidthLimitKbps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDeviceBandwidth(deviceId, bandwidthLimitKbps)
            val limitStr = if (bandwidthLimitKbps <= 0) "Unlimited" else "${bandwidthLimitKbps / 1024} MB/s"
            postToast("Allocated $limitStr bandwidth for device")
        }
    }

    fun updateDeviceSettings(
        deviceId: String,
        bandwidthLimitKbps: Int,
        scheduleHour: Int,
        scheduleMinute: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val scheduleStr = format12HourTime(scheduleHour, scheduleMinute)
            
            // Global sync: update settings for all devices
            vault.setDaytimeLimitKbps(bandwidthLimitKbps)
            vault.setDailySchedule(scheduleHour, scheduleMinute)
            dao.updateAllDevicesScheduleAndBandwidth(bandwidthLimitKbps, scheduleHour, scheduleMinute, scheduleStr, now)

            val role = vault.deviceRole.value
            val token = vault.pairingToken.value

            if (role == DeviceRole.PRIMARY_SENDER && token != null) {
                relayEngine.syncDeviceConfigToHub(token, "ALL", bandwidthLimitKbps, scheduleHour, scheduleMinute, now)
                SyncWorkers.scheduleDailyAutomatedSync(appContext, forceReenqueue = false)
            } else if (role == DeviceRole.SECONDARY_HUB) {
                relayEngine.markHubDeviceConfigUpdated(deviceId, now)
            }
            refreshConnectedDevices()
            val limitStr = if (bandwidthLimitKbps <= 0) "Unlimited" else "${bandwidthLimitKbps / 1024f} MB/s"
            postToast("Updated $limitStr • Daily Sync at $scheduleStr")
        }
    }

    fun triggerUploadOnlyThisFolder(tasks: List<RelayQueueTask>, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folderTasks = tasks.filter { it.bucketName.equals(folderName, ignoreCase = true) }
            if (folderTasks.isEmpty()) {
                postToast("No files in $folderName to upload")
                return@launch
            }
            relayEngine.clearQueueAndStop()
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(folderTasks, isUrgentInstantUpload = true)
            HubForegroundService.startOrUpdate(appContext)
            postToast("Uploading ${folderTasks.size} files from $folderName only")
        }
    }

    fun updateDevicePriorityWeight(weight: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setDevicePriorityWeight(weight)
            refreshMediaBuckets()
            postToast("Device priority weight set to $weight")
        }
    }

    fun toggleInstantCapture(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.setInstantCaptureSync(enabled)
            if (enabled) {
                HubForegroundService.startOrUpdate(appContext)
            }
            postToast(if (enabled) "Instant Camera Capture: Active" else "Instant Camera Capture: Disabled")
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
                SyncWorkers.scheduleDailyAutomatedSync(appContext, forceReenqueue = true)
                refreshAllState()
                postToast("Enterprise Configuration Imported Successfully")
            } else {
                postToast("Invalid JSON Configuration Payload")
            }
        }
    }

    fun refreshConnectedDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            val role = vault.deviceRole.value
            val token = vault.pairingToken.value
            val myDeviceId = vault.getOrCreateDeviceId()
            val myFriendlyName = vault.getDeviceFriendlyName()
            val myScheduleHour = vault.scheduleHour.value
            val myScheduleMinute = vault.scheduleMinute.value
            val myBandwidth = vault.daytimeLimitKbps.value
            val myTraffic = dao.getTotalSyncedBytes()

            if (role == DeviceRole.PRIMARY_SENDER && token != null) {
                val hubEndpoint = if (token.localIp.isNotBlank() && token.localIp != "0.0.0.0") token.localIp else token.tailscaleOrWanHost
                val scheduleStr = format12HourTime(myScheduleHour, myScheduleMinute)
                dao.upsertConnectedDevice(
                    ConnectedDeviceEntity(
                        deviceId = token.hubDeviceId.ifEmpty { "hub_${token.localIp}" },
                        deviceName = token.hubDeviceName.ifEmpty { "AuraSync Hub" },
                        ipAddress = "$hubEndpoint:${token.port}",
                        role = "SECONDARY_HUB",
                        scheduledSync = scheduleStr,
                        scheduleHour = myScheduleHour,
                        scheduleMinute = myScheduleMinute,
                        bandwidthLimitKbps = myBandwidth,
                        syncStatus = "Connected (Hub)",
                        trafficBytes = myTraffic,
                        lastSeen = System.currentTimeMillis()
                    )
                )
                val peers = relayEngine.queryHubPeers(
                    token = token,
                    clientDeviceId = myDeviceId,
                    clientDeviceName = myFriendlyName,
                    scheduleHour = myScheduleHour,
                    scheduleMinute = myScheduleMinute,
                    bandwidthLimitKbps = myBandwidth,
                    trafficBytes = myTraffic,
                    clientConfigTimestamp = vault.configTimestamp.value
                )
                for (peer in peers) {
                    dao.upsertConnectedDevice(peer)
                    // If any peer on the Hub has newer settings, sync locally and globally
                    if (peer.configUpdatedAt > vault.configTimestamp.value) {
                        vault.setDaytimeLimitKbps(peer.bandwidthLimitKbps, updateTimestamp = false)
                        vault.setDailySchedule(peer.scheduleHour, peer.scheduleMinute, updateTimestamp = false)
                        vault.setConfigTimestamp(peer.configUpdatedAt)
                        SyncWorkers.scheduleDailyAutomatedSync(appContext, forceReenqueue = true)
                        dao.updateAllDevicesScheduleAndBandwidth(peer.bandwidthLimitKbps, peer.scheduleHour, peer.scheduleMinute, peer.scheduledSync, peer.configUpdatedAt)
                    }
                }
            }
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearDiagnosticLogs()
        }
    }
}