package com.enterprise.syncpro.network

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.enterprise.syncpro.crypto.CryptoEngine
import com.enterprise.syncpro.data.DiagnosticLogEntity
import com.enterprise.syncpro.data.HubDeduplicationEntity
import com.enterprise.syncpro.data.LocalFileIndexEntity
import com.enterprise.syncpro.data.PairingToken
import com.enterprise.syncpro.data.SyncDatabase
import com.enterprise.syncpro.data.VaultManager
import com.enterprise.syncpro.media.MediaAndQrEngine
import com.enterprise.syncpro.service.HubForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Calendar
import java.util.concurrent.PriorityBlockingQueue

data class RelayQueueTask(
    val filePath: String,
    val fileName: String,
    val bucketName: String,
    val fileSize: Long,
    val lastModified: Long,
    val priorityWeight: Int, // 1..5 (5 = Urgent Instant Upload Queue-Jumper)
    val enqueuedAt: Long = System.nanoTime()
) : Comparable<RelayQueueTask> {
    override fun compareTo(other: RelayQueueTask): Int {
        val priorityDiff = other.priorityWeight.compareTo(this.priorityWeight)
        return if (priorityDiff != 0) priorityDiff else this.enqueuedAt.compareTo(other.enqueuedAt)
    }
}

data class TransferLiveTelemetry(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val activeFileName: String = "Idle",
    val activeFileProgress: Float = 0f,
    val currentSpeedMbps: Float = 0f,
    val queuedFileCount: Int = 0,
    val completedSessionFiles: Int = 0,
    val skippedDuplicateFiles: Int = 0,
    val activeEndpointRoute: String = "Standby",
    val statusBanner: String = "Engine Ready",
    val sourceDeviceName: String = "",
    val isHubBusyWithSync: Boolean = false
)

/**
 * Secondary Hub FIFO + Priority Queue Gate.
 * Sequences incoming client sync connections by priority weight (5..1) and FIFO arrival order.
 */
private class HubPriorityTransferGate {
    private val mutex = Mutex()
    private var activeTransferJobId: Long? = null
    private var activeDeviceName: String = ""
    private val waitingQueue = java.util.PriorityQueue<TransferTicket>()
    private var ticketSequence = 0L

    data class TransferTicket(
        val sequenceId: Long,
        val deviceId: String,
        val deviceName: String,
        val priorityWeight: Int,
        val deferred: kotlinx.coroutines.CompletableDeferred<Unit>
    ) : Comparable<TransferTicket> {
        override fun compareTo(other: TransferTicket): Int {
            val pDiff = other.priorityWeight.compareTo(this.priorityWeight)
            return if (pDiff != 0) pDiff else this.sequenceId.compareTo(other.sequenceId)
        }
    }

    suspend fun acquire(deviceId: String, deviceName: String, priorityWeight: Int): Long {
        val ticket: TransferTicket
        mutex.withLock {
            if (activeTransferJobId == null && waitingQueue.isEmpty()) {
                val jobId = ++ticketSequence
                activeTransferJobId = jobId
                activeDeviceName = deviceName
                return jobId
            }
            val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            ticket = TransferTicket(++ticketSequence, deviceId, deviceName, priorityWeight, deferred)
            waitingQueue.add(ticket)
        }
        ticket.deferred.await()
        return ticket.sequenceId
    }

    suspend fun release(jobId: Long) {
        mutex.withLock {
            if (activeTransferJobId == jobId) {
                activeTransferJobId = null
                activeDeviceName = ""
            }
            val next = waitingQueue.poll()
            if (next != null) {
                activeTransferJobId = next.sequenceId
                activeDeviceName = next.deviceName
                next.deferred.complete(Unit)
            }
        }
    }

    suspend fun isBusy(): Pair<Boolean, String> = mutex.withLock {
        (activeTransferJobId != null) to activeDeviceName
    }
}

class RelayTransferEngine private constructor(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = SyncDatabase.getInstance(context)
    private val dao = db.syncDao()
    private val vault = VaultManager.getInstance(context)
    private val networkMonitor = NetworkAndSafetyMonitor.getInstance(context)

    private val priorityQueue = PriorityBlockingQueue<RelayQueueTask>()
    private val hubHandshakeMutex = Mutex()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var senderConsumerJob: Job? = null

    private val _telemetry = MutableStateFlow(TransferLiveTelemetry())
    val telemetry: StateFlow<TransferLiveTelemetry> = _telemetry.asStateFlow()

    // Exposed live upload queue snapshot for the UI queue viewer
    private val _uploadQueue = MutableStateFlow<List<RelayQueueTask>>(emptyList())
    val uploadQueue: StateFlow<List<RelayQueueTask>> = _uploadQueue.asStateFlow()

    private val hubDeviceConfigTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun markHubDeviceConfigUpdated(deviceId: String, timestamp: Long = System.currentTimeMillis()) {
        hubDeviceConfigTimestamps[deviceId] = timestamp
    }

    var onLowStorageEmergencyCallback: (() -> Unit)? = null
    var onSafetyAlertReceived: ((String) -> Unit)? = null
    private val hubTransferGate = HubPriorityTransferGate()

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    init {
        ioScope.launch {
            vault.deviceRole.collectLatest { role ->
                if (role == com.enterprise.syncpro.data.DeviceRole.SECONDARY_HUB) {
                    startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
                } else {
                    stopSecondaryHubServer()
                }
            }
        }
    }

    fun setPaused(paused: Boolean) {
        _telemetry.value = _telemetry.value.copy(
            isPaused = paused,
            statusBanner = if (paused) "Paused by User / Safety Guard" else "Resuming Transfer Queue..."
        )
    }

    fun togglePause(): Boolean {
        val next = !_telemetry.value.isPaused
        setPaused(next)
        return next
    }

    /**
     * Enqueues a batch of scanned media files or an Urgent Queue-Jumping task (priority = 5).
     */
    fun enqueueTasks(tasks: List<RelayQueueTask>, isUrgentInstantUpload: Boolean = false) {
        ioScope.launch {
            for (task in tasks) {
                val effectiveTask = if (isUrgentInstantUpload) {
                    task.copy(priorityWeight = 5, enqueuedAt = System.nanoTime())
                } else {
                    task
                }
                priorityQueue.removeIf { it.filePath == effectiveTask.filePath }
                priorityQueue.put(effectiveTask)
            }
            val queueSnapshot = priorityQueue.toList().sortedDescending()
            _uploadQueue.value = queueSnapshot
            _telemetry.value = _telemetry.value.copy(
                queuedFileCount = priorityQueue.size,
                statusBanner = if (isUrgentInstantUpload) {
                    "Urgent Instant Upload Queued (${tasks.size} items)"
                } else {
                    "Queued ${priorityQueue.size} files for relay"
                }
            )
            ensureSenderWorkerRunning()
            HubForegroundService.startOrUpdate(context)
        }
    }

    /** Remove a specific task from the upload queue by file path. */
    fun removeTaskFromQueue(filePath: String) {
        ioScope.launch {
            priorityQueue.removeIf { it.filePath == filePath }
            _uploadQueue.value = priorityQueue.toList().sortedDescending()
            _telemetry.value = _telemetry.value.copy(queuedFileCount = priorityQueue.size)
        }
    }

    /** Remove all tasks belonging to an unselected bucket/folder from the upload queue. */
    fun removeTasksForBucket(bucketName: String) {
        ioScope.launch {
            val removedCount = priorityQueue.count { it.bucketName.equals(bucketName, ignoreCase = true) }
            if (removedCount > 0) {
                priorityQueue.removeIf { it.bucketName.equals(bucketName, ignoreCase = true) }
                _uploadQueue.value = priorityQueue.toList().sortedDescending()
                _telemetry.value = _telemetry.value.copy(queuedFileCount = priorityQueue.size)
            }
        }
    }

    /** Re-prioritize an existing task in the queue. */
    fun reprioritizeTask(filePath: String, newPriority: Int) {
        ioScope.launch {
            val existing = priorityQueue.find { it.filePath == filePath } ?: return@launch
            priorityQueue.removeIf { it.filePath == filePath }
            priorityQueue.put(existing.copy(priorityWeight = newPriority.coerceIn(1, 5), enqueuedAt = System.nanoTime()))
            _uploadQueue.value = priorityQueue.toList().sortedDescending()
            _telemetry.value = _telemetry.value.copy(queuedFileCount = priorityQueue.size)
        }
    }

    /**
     * Completely stops ongoing sync, cancels transfer workers, and purges the priority queue.
     */
    fun clearQueueAndStop() {
        ioScope.launch {
            priorityQueue.clear()
            _uploadQueue.value = emptyList()
            senderConsumerJob?.cancel()
            senderConsumerJob = null
            _telemetry.value = _telemetry.value.copy(
                isRunning = false,
                isPaused = false,
                activeFileName = "Idle",
                activeFileProgress = 0f,
                currentSpeedMbps = 0f,
                queuedFileCount = 0,
                statusBanner = "Sync Stopped & Queue Cleared"
            )
            logEvent("INFO", "RelayControl", "Sync manually stopped; upload queue cleared.")
            HubForegroundService.startOrUpdate(context, HubForegroundService.ACTION_STOP_SERVICE)
        }
    }

    /**
     * Starts the 24/7 Secondary Hub TCP Server on Dispatchers.IO.
     */
    fun startSecondaryHubServer(port: Int = NetworkAndSafetyMonitor.DEFAULT_HUB_PORT) {
        if (serverJob?.isActive == true && serverSocket?.isBound == true && serverSocket?.isClosed == false) return
        stopSecondaryHubServer()
        serverJob = ioScope.launch {
            runCatching {
                val deviceId = vault.getOrCreateDeviceId()
                networkMonitor.startMdnsHubBroadcast(port, deviceId)
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
                logEvent("INFO", "HubServer", "Secondary Hub listening on TCP port $port")
                _telemetry.value = _telemetry.value.copy(
                    isRunning = true,
                    activeEndpointRoute = "Hub Listening :$port",
                    statusBanner = "Hub Ready - Waiting for Primary Nodes"
                )

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleIncomingClientConnection(clientSocket)
                    }
                }
            }.onFailure { err ->
                logEvent("ERROR", "HubServer", "Server loop terminated: ${err.message}")
                _telemetry.value = _telemetry.value.copy(
                    isRunning = false,
                    statusBanner = "Hub Server Error: ${err.message}"
                )
            }
        }
    }

    fun stopSecondaryHubServer() {
        networkMonitor.stopMdnsHubBroadcast()
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    /**
     * Handles an incoming Primary Node connection on the Secondary Hub:
     * 1. Verifies Hub free storage >= 1GB.
     * 2. Authenticates HMAC token + checks Tier-2 SHA-256 deduplication in Room under Mutex lock.
     * 3. Streams E2EE decrypted payload in 64KB chunks to `.tmp_[sha256]_[filename]`.
     * 4. Verifies full SHA-256 checksum on disk before atomic MediaStore insertion into DCIM/Camera.
     */
    private suspend fun handleIncomingClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { client ->
            client.soTimeout = 30_000
            client.receiveBufferSize = CryptoEngine.CHUNK_SIZE_64KB
            val rawIn = client.getInputStream()
            val rawOut = client.getOutputStream()
            val reader = BufferedReader(InputStreamReader(rawIn, Charsets.UTF_8))
            val writer = PrintWriter(rawOut, true)

            val handshakeLine = reader.readLine() ?: return@withContext
            val parts = handshakeLine.split("|")
            if (parts[0] == "SAFETY_ALERT") {
                val senderName = parts.getOrNull(1) ?: "Primary Node"
                val reason = parts.getOrNull(2) ?: "Safety Limit Tripped"
                val alertText = "$senderName Alert: $reason".trim()
                onSafetyAlertReceived?.invoke(alertText)
                logEvent("WARN", "SafetyAlert", alertText)
                writer.println("ALERT_ACK")
                return@withContext
            }
            if (parts[0] == "UPDATE_DEVICE_CONFIG") {
                val targetDeviceId = parts.getOrNull(1) ?: ""
                val bandwidthKbps = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val scheduleHour = parts.getOrNull(3)?.toIntOrNull() ?: 2
                val scheduleMinute = parts.getOrNull(4)?.toIntOrNull() ?: 0
                val configTimestamp = parts.getOrNull(5)?.toLongOrNull() ?: System.currentTimeMillis()
                val scheduleStr = format12HourTime(scheduleHour, scheduleMinute)

                // Global update: sync settings across all devices
                dao.updateAllDevicesScheduleAndBandwidth(bandwidthKbps, scheduleHour, scheduleMinute, scheduleStr, configTimestamp)
                vault.setDaytimeLimitKbps(bandwidthKbps, updateTimestamp = false)
                vault.setDailySchedule(scheduleHour, scheduleMinute, updateTimestamp = false)
                vault.setConfigTimestamp(configTimestamp)

                writer.println("CONFIG_UPDATED")
                logEvent("INFO", "ConfigSync", "Global settings synchronized across all devices: cap=${bandwidthKbps}KB/s, schedule=$scheduleStr")
                return@withContext
            }
            if (parts[0] == "QUERY_HUB_PEERS" || parts[0] == "REGISTER_CLIENT") {
                val clientDeviceId = parts.getOrNull(1)
                if (!clientDeviceId.isNullOrBlank()) {
                    val clientDeviceName = parts.getOrNull(2)?.ifBlank { "Primary Sender" } ?: "Primary Sender"
                    val clientScheduleHour = parts.getOrNull(3)?.toIntOrNull() ?: 2
                    val clientScheduleMinute = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    val clientBandwidth = parts.getOrNull(5)?.toIntOrNull() ?: 0
                    val clientTraffic = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                    val clientConfigTs = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                    val clientIp = client.inetAddress?.hostAddress ?: "Wi-Fi LAN"

                    // Clean up any stale duplicate devices with the same IP or name
                    dao.deleteDuplicates(clientDeviceId, clientIp, clientDeviceName)

                    val existing = dao.getConnectedDevice(clientDeviceId) ?: dao.getConnectedDeviceByIp(clientIp)
                    val canonicalId = existing?.deviceId ?: clientDeviceId
                    val hubConfigTs = hubDeviceConfigTimestamps[canonicalId]
                        ?: (if (clientIp.isNotBlank()) hubDeviceConfigTimestamps[clientIp] else null)
                        ?: existing?.configUpdatedAt
                        ?: 0L

                    val scheduleHour: Int
                    val scheduleMinute: Int
                    val bandwidth: Int
                    val effectiveTs: Long

                    if (clientConfigTs >= hubConfigTs) {
                        scheduleHour = clientScheduleHour
                        scheduleMinute = clientScheduleMinute
                        bandwidth = clientBandwidth
                        effectiveTs = clientConfigTs
                        hubDeviceConfigTimestamps[canonicalId] = clientConfigTs
                        if (clientIp.isNotBlank()) hubDeviceConfigTimestamps[clientIp] = clientConfigTs
                    } else {
                        scheduleHour = existing?.scheduleHour ?: clientScheduleHour
                        scheduleMinute = existing?.scheduleMinute ?: clientScheduleMinute
                        bandwidth = existing?.bandwidthLimitKbps ?: clientBandwidth
                        effectiveTs = hubConfigTs
                    }
                    val scheduleStr = format12HourTime(scheduleHour, scheduleMinute)

                    dao.upsertConnectedDevice(
                        com.enterprise.syncpro.data.ConnectedDeviceEntity(
                            deviceId = canonicalId,
                            deviceName = clientDeviceName,
                            ipAddress = clientIp,
                            role = "PRIMARY_SENDER",
                            scheduledSync = scheduleStr,
                            scheduleHour = scheduleHour,
                            scheduleMinute = scheduleMinute,
                            bandwidthLimitKbps = bandwidth,
                            throttleStartHour = existing?.throttleStartHour ?: 2,
                            throttleStartMinute = existing?.throttleStartMinute ?: 0,
                            throttleEndHour = existing?.throttleEndHour ?: 5,
                            throttleEndMinute = existing?.throttleEndMinute ?: 0,
                            syncStatus = existing?.syncStatus ?: "Connected (Wi-Fi)",
                            trafficBytes = (existing?.trafficBytes ?: 0L).coerceAtLeast(clientTraffic),
                            configUpdatedAt = effectiveTs,
                            lastSeen = System.currentTimeMillis(),
                            lastSyncedAt = existing?.lastSyncedAt ?: 0L
                        )
                    )
                }
                val devices = dao.getConnectedDevicesList()
                val (isBusy, busyWithDevice) = hubTransferGate.isBusy()
                val hubSyncState = if (isBusy) "Syncing (with $busyWithDevice)" else "Free (Standby)"
                val devListPayload = devices.joinToString(";") { dev ->
                    val devTs = hubDeviceConfigTimestamps[dev.deviceId] ?: dev.configUpdatedAt
                    "${dev.deviceId}#${dev.deviceName}#${dev.ipAddress}#${dev.role}#${dev.scheduledSync}#${dev.bandwidthLimitKbps}#${dev.syncStatus}#${dev.trafficBytes}#${dev.lastSeen}#${dev.scheduleHour}#${dev.scheduleMinute}#$devTs#${dev.lastSyncedAt}"
                }
                writer.println("PEERS_RESULT|${devices.size}|$devListPayload|$hubSyncState")
                return@withContext
            }
            if (parts.size < 6 || parts[0] != "PREFLIGHT") {
                writer.println("REJECT_MALFORMED")
                return@withContext
            }

            val sha256 = parts[1]
            val originalName = parts[2]
            val fileSize = parts[3].toLongOrNull() ?: 0L
            val sourceDeviceId = parts[4]
            val clientAuthTag = parts[5]
            val sourceDeviceFriendlyName = parts.getOrNull(6) ?: sourceDeviceId
            val clientPriority = parts.getOrNull(7)?.toIntOrNull() ?: 3
            val clientIp = client.inetAddress?.hostAddress ?: "Wi-Fi LAN"

            // Hub Hardware Safety Safeguards (Thermal, Battery, and Editable Free Storage Cutoff)
            val safety = networkMonitor.evaluateHardwareSafetyGuards(
                tempCutoff = vault.tempCutoffCelsius.value,
                batteryCutoff = vault.batteryCutoffPercent.value,
                storageCutoffGb = vault.storageCutoffGb.value
            )
            val cutoffBytes = (vault.storageCutoffGb.value * 1024L * 1024L * 1024L).toLong()
            if (!safety.isSafeToSync || (safety.availableStorageBytes - fileSize) < cutoffBytes) {
                val reason = when {
                    safety.isThermalTripped -> "Hub temperature (${String.format(java.util.Locale.US, "%.1f", safety.temperatureCelsius)}°C) exceeds cutoff (${vault.tempCutoffCelsius.value}°C)"
                    safety.isLowBatteryTripped -> "Hub battery (${safety.batteryPercent}%) is below cutoff (${vault.batteryCutoffPercent.value}%)"
                    else -> "Hub storage (${formatBytes(safety.availableStorageBytes)}) is below cutoff (${vault.storageCutoffGb.value} GB)"
                }
                writer.println("REJECT_SAFETY_LIMIT|$reason")
                logEvent("ERROR", "StorageGuard", "Rejected $originalName: $reason")
                onLowStorageEmergencyCallback?.invoke()
                onSafetyAlertReceived?.invoke("Hub Safety Limit: Rejected incoming sync ($reason)")
                return@withContext
            }

            val secretHex = vault.getOrCreateHubSecretHex()
            val pinHash = vault.getPinHashOrDefault()
            val expectedTag = CryptoEngine.computeHandshakeAuthTag(secretHex, pinHash, sha256, fileSize)
            if (!expectedTag.equals(clientAuthTag, ignoreCase = true)) {
                writer.println("REJECT_AUTH")
                logEvent("SECURITY", "HubAuth", "Rejected unauthorized transfer from $sourceDeviceId")
                return@withContext
            }

            // Clean up any duplicate records for this device
            dao.deleteDuplicates(sourceDeviceId, clientIp, sourceDeviceFriendlyName)

            val existingDev = dao.getConnectedDevice(sourceDeviceId) ?: dao.getConnectedDeviceByIp(clientIp)
            val canonicalDeviceId = existingDev?.deviceId ?: sourceDeviceId
            val canonicalDeviceName = existingDev?.deviceName ?: sourceDeviceFriendlyName
            val devScheduleHour = existingDev?.scheduleHour ?: 2
            val devScheduleMinute = existingDev?.scheduleMinute ?: 0
            val devBandwidth = existingDev?.bandwidthLimitKbps ?: 0

            // Change state WITHIN THAT BOX to "Receiving $originalName"
            dao.upsertConnectedDevice(
                com.enterprise.syncpro.data.ConnectedDeviceEntity(
                    deviceId = canonicalDeviceId,
                    deviceName = canonicalDeviceName,
                    ipAddress = clientIp,
                    role = "PRIMARY_SENDER",
                    scheduledSync = existingDev?.scheduledSync ?: format12HourTime(devScheduleHour, devScheduleMinute),
                    scheduleHour = devScheduleHour,
                    scheduleMinute = devScheduleMinute,
                    bandwidthLimitKbps = devBandwidth,
                    throttleStartHour = existingDev?.throttleStartHour ?: 2,
                    throttleStartMinute = existingDev?.throttleStartMinute ?: 0,
                    throttleEndHour = existingDev?.throttleEndHour ?: 5,
                    throttleEndMinute = existingDev?.throttleEndMinute ?: 0,
                    syncStatus = "Receiving $originalName",
                    trafficBytes = (existingDev?.trafficBytes ?: 0L) + fileSize,
                    configUpdatedAt = existingDev?.configUpdatedAt ?: 0L,
                    lastSeen = System.currentTimeMillis(),
                    lastSyncedAt = existingDev?.lastSyncedAt ?: 0L
                )
            )

            // Tier-2 Remote Pre-Flight Deduplication Check under Mutex
            var alreadyExistsOnHub = hubHandshakeMutex.withLock {
                dao.isHashPresentOnHub(sha256)
            }

            // 1. Check if hash is already indexed in local file index
            if (!alreadyExistsOnHub) {
                val localEntity = dao.findLocalFileByHash(sha256)
                if (localEntity != null) {
                    hubHandshakeMutex.withLock {
                        dao.registerHubReceivedFile(
                            HubDeduplicationEntity(
                                sha256 = sha256,
                                savedUri = localEntity.filePath,
                                finalFileName = localEntity.fileName,
                                fileSize = localEntity.fileSize,
                                sourceDevice = canonicalDeviceName,
                                receivedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    alreadyExistsOnHub = true
                }
            }

            // 2. Check if the exact photo/video already exists in Secondary Phone's MediaStore
            if (!alreadyExistsOnHub) {
                val existingMatch = MediaAndQrEngine.findExistingMediaMatch(
                    context = context,
                    targetSha256 = sha256,
                    targetFileName = originalName,
                    targetFileSize = fileSize
                )
                if (existingMatch != null) {
                    hubHandshakeMutex.withLock {
                        dao.registerHubReceivedFile(
                            HubDeduplicationEntity(
                                sha256 = sha256,
                                savedUri = existingMatch.contentUri,
                                finalFileName = existingMatch.fileName,
                                fileSize = existingMatch.fileSize,
                                sourceDevice = canonicalDeviceName,
                                receivedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    dao.upsertLocalFileIndex(
                        LocalFileIndexEntity(
                            filePath = existingMatch.filePath,
                            fileName = existingMatch.fileName,
                            bucketName = existingMatch.bucketName,
                            fileSize = existingMatch.fileSize,
                            lastModified = existingMatch.lastModified,
                            sha256 = sha256,
                            isSynced = true,
                            syncedTimestamp = System.currentTimeMillis()
                        )
                    )
                    alreadyExistsOnHub = true
                    logEvent(
                        "INFO",
                        "Deduplication",
                        "Matched existing media on Secondary phone: ${existingMatch.filePath}. Kept in place without duplicate copy."
                    )
                }
            }

            if (alreadyExistsOnHub) {
                dao.updateDeviceStatus(canonicalDeviceId, "Connected (Wi-Fi)")
                writer.println("SKIP_DUPLICATE")
                _telemetry.value = _telemetry.value.copy(
                    skippedDuplicateFiles = _telemetry.value.skippedDuplicateFiles + 1,
                    statusBanner = "Deduplicated: $originalName already existed on Secondary Hub"
                )
                return@withContext
            }

            // Priority Queue & FIFO Gate: Acquire transfer permit before streaming
            val transferJobId = hubTransferGate.acquire(canonicalDeviceId, canonicalDeviceName, clientPriority)
            try {
                _telemetry.value = _telemetry.value.copy(
                    sourceDeviceName = canonicalDeviceName,
                    isHubBusyWithSync = true
                )
                writer.println("PROCEED_STREAM")

                // Atomic Temporary File Write (.tmp_[sha256]_[filename])
                val stagingDir = File(context.cacheDir, "hub_atomic_staging").apply { mkdirs() }
                val tempFile = File(stagingDir, ".tmp_${sha256.take(12)}_${ originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_") }")

                try {
                    val decryptedStream = CryptoEngine.wrapDecryptedInputStream(rawIn, secretHex, sha256)
                    val buffer = ByteArray(CryptoEngine.CHUNK_SIZE_64KB)
                    var totalReceived = 0L
                    val startTime = System.currentTimeMillis()

                    FileOutputStream(tempFile).use { fos ->
                        while (totalReceived < fileSize) {
                            val toRead = minOf(
                                CryptoEngine.CHUNK_SIZE_64KB.toLong(),
                                fileSize - totalReceived
                            ).toInt()
                            val readCount = decryptedStream.read(buffer, 0, toRead)
                            if (readCount == -1) break
                            fos.write(buffer, 0, readCount)
                            totalReceived += readCount

                            val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000f
                            val mbps = ((totalReceived / (1024f * 1024f)) / elapsedSec)
                            _telemetry.value = _telemetry.value.copy(
                                activeFileName = originalName,
                                activeFileProgress = (totalReceived.toFloat() / fileSize.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                currentSpeedMbps = mbps,
                                sourceDeviceName = canonicalDeviceName,
                                statusBanner = "Receiving $originalName from $canonicalDeviceName"
                            )
                        }
                        fos.fd.sync()
                    }

                    // Post-stream SHA-256 verification before MediaStore commit
                    val verifiedHash = CryptoEngine.computeFileSha256Chunked(tempFile)
                    if (!verifiedHash.equals(sha256, ignoreCase = true)) {
                        tempFile.delete()
                        writer.println("ERR_CHECKSUM_MISMATCH")
                        logEvent("ERROR", "Integrity", "Checksum mismatch for $originalName. Temp file purged.")
                        return@withContext
                    }

                    val collisionSafeName = CryptoEngine.buildCollisionSafeFileName(originalName, sha256)
                    val savedUri = commitVerifiedFileToMediaStoreDcim(tempFile, collisionSafeName)
                    tempFile.delete()

                    if (savedUri != null) {
                        val commitTs = System.currentTimeMillis()
                        hubHandshakeMutex.withLock {
                            dao.registerHubReceivedFile(
                                HubDeduplicationEntity(
                                    sha256 = sha256,
                                    savedUri = savedUri,
                                    finalFileName = collisionSafeName,
                                    fileSize = fileSize,
                                    sourceDevice = canonicalDeviceName,
                                    receivedAt = commitTs
                                )
                            )
                        }
                        dao.recordDeviceTrafficAndSync(canonicalDeviceId, fileSize, "Connected", commitTs)
                        writer.println("ACK_COMMITTED")
                        _telemetry.value = _telemetry.value.copy(
                            activeFileProgress = 1f,
                            completedSessionFiles = _telemetry.value.completedSessionFiles + 1,
                            statusBanner = "Saved to DCIM/Camera: $collisionSafeName"
                        )
                        logEvent("INFO", "HubCommit", "Atomically committed $collisionSafeName to DCIM/Camera")
                    } else {
                        writer.println("ERR_MEDIASTORE_COMMIT")
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    logEvent("ERROR", "HubReceive", "Interrupted stream for $originalName:${e.message}")
                }
            } finally {
                hubTransferGate.release(transferJobId)
                val (stillBusy, activeDevice) = hubTransferGate.isBusy()
                _telemetry.value = _telemetry.value.copy(
                    sourceDeviceName = activeDevice,
                    isHubBusyWithSync = stillBusy
                )
                dao.updateDeviceStatus(canonicalDeviceId, "Connected (Wi-Fi)")
            }
        }
    }

    /**
     * Atomically moves verified temporary payload into MediaStore DCIM/Camera
     * wrapped in Scoped Storage SecurityException protection.
     */
    private suspend fun commitVerifiedFileToMediaStoreDcim(
        verifiedTempFile: File,
        collisionSafeName: String
    ): String? = withContext(Dispatchers.IO) {
        val isVideo = collisionSafeName.substringAfterLast('.', "").lowercase() in
                setOf("mp4", "mov", "mkv", "webm", "3gp")
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        try {
            val resolver = context.contentResolver
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, collisionSafeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertedUri = resolver.insert(collection, values) ?: return@withContext null
            val buffer = ByteArray(CryptoEngine.CHUNK_SIZE_64KB)

            resolver.openOutputStream(insertedUri)?.use { outStream ->
                FileInputStream(verifiedTempFile).use { inStream ->
                    var read: Int
                    while (inStream.read(buffer, 0, CryptoEngine.CHUNK_SIZE_64KB).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                    outStream.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera/$collisionSafeName"),
                arrayOf(mimeType),
                null
            )
            insertedUri.toString()
        } catch (secEx: SecurityException) {
            logEvent("ERROR", "ScopedStorage", "SecurityException writing MediaStore: ${secEx.message}")
            null
        } catch (ex: Exception) {
            logEvent("ERROR", "MediaStore", "Failed committing $collisionSafeName:${ex.message}")
            null
        }
    }

    /**
     * Primary Node Consumer Loop: Pulls highest-priority tasks from PriorityBlockingQueue,
     * checks safety guards, performs Tier-1 + Tier-2 deduplication, and streams in 64KB chunks.
     */
    private fun ensureSenderWorkerRunning() {
        if (senderConsumerJob?.isActive == true) return
        senderConsumerJob = ioScope.launch {
            _telemetry.value = _telemetry.value.copy(isRunning = true)

            while (isActive && priorityQueue.isNotEmpty()) {
                // Respect user pause & hardware safety switches
                val safety = networkMonitor.evaluateHardwareSafetyGuards(
                    tempCutoff = vault.tempCutoffCelsius.value,
                    batteryCutoff = vault.batteryCutoffPercent.value
                )
                if (!safety.isSafeToSync) {
                    val reason = when {
                        safety.isThermalTripped -> "Paused: Battery Temp ${String.format("%.1f", safety.temperatureCelsius)}°C exceeds cutoff (${vault.tempCutoffCelsius.value}°C)"
                        safety.isLowBatteryTripped -> "Paused: Battery ${safety.batteryPercent}% unplugged (< ${vault.batteryCutoffPercent.value}%)"
                        else -> "Paused: Storage below 1GB safety threshold"
                    }
                    _telemetry.value = _telemetry.value.copy(isPaused = true, statusBanner = reason)
                    delay(5_000)
                    continue
                }

                // Check Trusted Wi-Fi SSID Whitelist
                val wifiAllowed = networkMonitor.isTrustedWifiAllowed(
                    vault.restrictToTrustedWifi.value,
                    vault.trustedSsids.value
                )
                if (!wifiAllowed) {
                    _telemetry.value = _telemetry.value.copy(
                        statusBanner = "Waiting for Trusted Wi-Fi SSID (${vault.trustedSsids.value.joinToString()})"
                    )
                    delay(5_000)
                    continue
                }

                if (_telemetry.value.isPaused) {
                    delay(1_500)
                    continue
                }

                val token = vault.pairingToken.value
                if (token == null) {
                    _telemetry.value = _telemetry.value.copy(
                        statusBanner = "No Secondary Hub Paired - Scan Hub QR Code in Network Tab"
                    )
                    delay(3_000)
                    continue
                }

                val task = priorityQueue.poll() ?: break
                _uploadQueue.value = priorityQueue.toList().sortedDescending()
                _telemetry.value = _telemetry.value.copy(queuedFileCount = priorityQueue.size)

                val outcome = executePrimaryFileRelay(task, token)
                if (!outcome) {
                    // Re-queue on transient network handover and wait briefly
                    priorityQueue.put(task)
                    _uploadQueue.value = priorityQueue.toList().sortedDescending()
                    delay(3_000)
                }
            }

            _telemetry.value = _telemetry.value.copy(
                isRunning = false,
                activeFileProgress = 0f,
                currentSpeedMbps = 0f,
                queuedFileCount = priorityQueue.size,
                statusBanner = "All Queued Media Relayed & Verified"
            )
        }
    }

    private suspend fun executePrimaryFileRelay(
        task: RelayQueueTask,
        token: PairingToken
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(task.filePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) return@withContext true

        // Tier 1: Local Fast-Path Room Lookup (path + size + lastModified)
        val cachedEntry = dao.findFastPathMatch(task.filePath, task.fileSize, task.lastModified)
        if (cachedEntry != null && cachedEntry.isSynced) {
            _telemetry.value = _telemetry.value.copy(
                skippedDuplicateFiles = _telemetry.value.skippedDuplicateFiles + 1,
                statusBanner = "Tier-1 Fast-Path Skip: ${task.fileName}"
            )
            return@withContext true
        }

        val sha256 = cachedEntry?.sha256 ?: CryptoEngine.computeFileSha256Chunked(sourceFile)
        dao.upsertLocalFileIndex(
            LocalFileIndexEntity(
                filePath = task.filePath,
                fileName = task.fileName,
                bucketName = task.bucketName,
                fileSize = task.fileSize,
                lastModified = task.lastModified,
                sha256 = sha256,
                isSynced = false,
                syncedTimestamp = 0L
            )
        )

        // Resolve endpoint: Try mDNS / LAN first, then seamlessly fallback to Tailscale / WAN
        val candidateHosts = buildList {
            networkMonitor.discoveredHubs.value.forEach { add(it.hostAddress to it.port) }
            if (token.localIp.isNotBlank() && token.localIp != "0.0.0.0") add(token.localIp to token.port)
            if (token.tailscaleOrWanHost.isNotBlank()) add(token.tailscaleOrWanHost to token.port)
        }.distinct()

        if (candidateHosts.isEmpty()) {
            _telemetry.value = _telemetry.value.copy(
                statusBanner = "No Hub IP found. Scan Hub QR Code or connect in Network tab."
            )
            return@withContext false
        }

        var lastErrorMsg = "Connection failed"
        for ((host, port) in candidateHosts) {
            val handedOverEpochBefore = networkMonitor.networkHandoverEpoch.value
            _telemetry.value = _telemetry.value.copy(
                statusBanner = "Connecting to Hub at $host:$port..."
            )
            val result = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 6_000)
                    socket.soTimeout = 30_000
                    socket.sendBufferSize = CryptoEngine.CHUNK_SIZE_64KB

                    val routeLabel = if (host.startsWith("100.")) "Tailscale Mesh ($host)" else "LAN/WAN ($host)"
                    _telemetry.value = _telemetry.value.copy(activeEndpointRoute = routeLabel)

                    val rawOut = socket.getOutputStream()
                    val rawIn = socket.getInputStream()
                    val writer = PrintWriter(rawOut, true)
                    val reader = BufferedReader(InputStreamReader(rawIn, Charsets.UTF_8))

                    val authTag = CryptoEngine.computeHandshakeAuthTag(
                        sharedSecretHex = token.e2eeSharedSecretHex,
                        pinHash = token.pinHash,
                        sha256 = sha256,
                        fileSize = task.fileSize
                    )
                    val clientDeviceId = vault.getOrCreateDeviceId()
                    val clientDeviceName = vault.getDeviceFriendlyName().replace("|", "_")

                    // Tier 2: Pre-Flight Handshake (includes device priority weight)
                    val handshakePayload = listOf(
                        "PREFLIGHT",
                        sha256,
                        task.fileName,
                        task.fileSize.toString(),
                        clientDeviceId,
                        authTag,
                        clientDeviceName,
                        task.priorityWeight.toString()
                    ).joinToString("|")
                    writer.println(handshakePayload)
                    val hubResponse = reader.readLine() ?: return@use false

                    when {
                        hubResponse.startsWith("REJECT_SAFETY_LIMIT") -> {
                            val reason = hubResponse.substringAfter("|", "Safety limit exceeded on Secondary Hub")
                            setPaused(true)
                            onSafetyAlertReceived?.invoke(reason)
                            _telemetry.value = _telemetry.value.copy(
                                statusBanner = "Paused: $reason"
                            )
                            logEvent("WARN", "SafetyLimit", "Hub rejected: $reason")
                            return@use false
                        }
                        hubResponse == "SKIP_DUPLICATE" -> {
                            dao.markLocalFileSynced(sha256)
                            dao.markDeviceSynced(token.hubDeviceId, "Connected", System.currentTimeMillis())
                            _telemetry.value = _telemetry.value.copy(
                                skippedDuplicateFiles = _telemetry.value.skippedDuplicateFiles + 1,
                                statusBanner = "Tier-2 Hub Deduplicated: ${task.fileName}"
                            )
                            return@use true
                        }
                        hubResponse == "REJECT_LOW_STORAGE" -> {
                            setPaused(true)
                            onLowStorageEmergencyCallback?.invoke()
                            _telemetry.value = _telemetry.value.copy(
                                statusBanner = "Hub Paused: Secondary Hub has < 1GB free storage."
                            )
                            logEvent("ERROR", "HubStorage", "Hub storage < 1GB! Transfer paused.")
                            return@use false
                        }
                        hubResponse == "REJECT_AUTH" -> {
                            setPaused(true)
                            _telemetry.value = _telemetry.value.copy(
                                statusBanner = "Auth Failed: Please re-scan Hub QR Code or Re-connect."
                            )
                            logEvent("SECURITY", "Handshake", "Hub rejected pairing token/PIN.")
                            return@use false
                        }
                        hubResponse == "PROCEED_STREAM" -> {
                            // Stream E2EE payload in strict 64KB chunks with Daytime/Nighttime throttling
                            val encryptedOut = CryptoEngine.wrapEncryptedOutputStream(
                                rawOut,
                                token.e2eeSharedSecretHex,
                                sha256
                            )
                            val buffer = ByteArray(CryptoEngine.CHUNK_SIZE_64KB)
                            var bytesSent = 0L
                            val startMs = System.currentTimeMillis()

                            FileInputStream(sourceFile).use { fis ->
                                var read: Int
                                while (fis.read(buffer, 0, CryptoEngine.CHUNK_SIZE_64KB).also { read = it } != -1) {
                                    // Detect live network switch mid-stream
                                    if (networkMonitor.networkHandoverEpoch.value != handedOverEpochBefore) {
                                        throw IllegalStateException("Live network handover detected; switching route")
                                    }
                                    encryptedOut.write(buffer, 0, read)
                                    bytesSent += read

                                    applyTimeBasedBandwidthThrottle(bytesSent, startMs)

                                    val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1L)) / 1000f
                                    val mbps = (bytesSent / (1024f * 1024f)) / elapsedSec
                                    _telemetry.value = _telemetry.value.copy(
                                        activeFileName = task.fileName,
                                        activeFileProgress = (bytesSent.toFloat() / task.fileSize.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                        currentSpeedMbps = mbps,
                                        statusBanner = "Relaying ${task.fileName} (Priority${task.priorityWeight})"
                                    )
                                }
                                encryptedOut.flush()
                            }

                            val commitAck = reader.readLine()
                            if (commitAck == "ACK_COMMITTED") {
                                dao.markLocalFileSynced(sha256)
                                dao.markDeviceSynced(token.hubDeviceId, "Connected", System.currentTimeMillis())
                                _telemetry.value = _telemetry.value.copy(
                                    completedSessionFiles = _telemetry.value.completedSessionFiles + 1,
                                    activeFileProgress = 1f,
                                    statusBanner = "Verified on Hub: ${task.fileName}"
                                )
                                return@use true
                            }
                            false
                        }
                        else -> false
                    }
                }
            }.onFailure { err ->
                lastErrorMsg = err.localizedMessage ?: err.javaClass.simpleName
                logEvent("WARN", "RelayTransfer", "Failed to connect to Hub at $host:$port: $lastErrorMsg")
            }.getOrDefault(false)

            if (result) return@withContext true
        }

        _telemetry.value = _telemetry.value.copy(
            activeEndpointRoute = "Unreachable",
            statusBanner = "Cannot reach Hub at ${candidateHosts.map { "${it.first}:${it.second}" }.joinToString()} ($lastErrorMsg). Retrying..."
        )
        false
    }

    /**
     * Bandwidth Throttling & Configurable Time-Based Rules:
     * Capped to user-configured KB/s during configured timer window; uncapped at full speed outside window.
     */
    private suspend fun applyTimeBasedBandwidthThrottle(bytesSentSoFar: Long, transferStartMs: Long) {
        val limitKbps = vault.daytimeLimitKbps.value
        if (limitKbps <= 0 || limitKbps >= 100_000) return

        val now = Calendar.getInstance()
        val currentMinutesOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = vault.throttleStartHour.value * 60 + vault.throttleStartMinute.value
        val endMinutes = vault.throttleEndHour.value * 60 + vault.throttleEndMinute.value

        val isThrottled = if (startMinutes <= endMinutes) {
            currentMinutesOfDay in startMinutes until endMinutes
        } else {
            // Window wraps around midnight (e.g. 20:00 to 06:00)
            currentMinutesOfDay >= startMinutes || currentMinutesOfDay < endMinutes
        }

        if (!isThrottled) return // Outside configured timer window: Uncapped Full Speed!

        val allowedBytesPerMs = (limitKbps * 1024.0) / 1000.0
        val expectedElapsedMs = (bytesSentSoFar / allowedBytesPerMs).toLong()
        val actualElapsedMs = System.currentTimeMillis() - transferStartMs
        val sleepNeeded = expectedElapsedMs - actualElapsedMs
        if (sleepNeeded > 15L) {
            delay(sleepNeeded.coerceAtMost(500L))
        }
    }

    private fun format12HourTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
    }

    /**
     * Registers this client and queries the Secondary Hub for its connected client peers and returns them.
     */
    suspend fun queryHubPeers(
        token: PairingToken,
        clientDeviceId: String = "",
        clientDeviceName: String = "",
        scheduleHour: Int = 2,
        scheduleMinute: Int = 0,
        bandwidthLimitKbps: Int = 0,
        trafficBytes: Long = 0L,
        clientConfigTimestamp: Long = 0L
    ): List<com.enterprise.syncpro.data.ConnectedDeviceEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<com.enterprise.syncpro.data.ConnectedDeviceEntity>()
        val candidates = listOf(token.localIp, token.tailscaleOrWanHost).filter { it.isNotBlank() && it != "0.0.0.0" }
        for (host in candidates) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = 3000
                    socket.connect(InetSocketAddress(host, token.port), 3000)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    writer.println("QUERY_HUB_PEERS|$clientDeviceId|$clientDeviceName|$scheduleHour|$scheduleMinute|$bandwidthLimitKbps|$trafficBytes|$clientConfigTimestamp")
                    val response = reader.readLine()
                    if (response != null && response.startsWith("PEERS_RESULT|")) {
                        val tokens = response.split("|")
                        if (tokens.size >= 3 && tokens[2].isNotEmpty()) {
                            val rawList = tokens[2].split(";")
                            for (item in rawList) {
                                val fields = item.split("#")
                                if (fields.size >= 8) {
                                    results.add(
                                        com.enterprise.syncpro.data.ConnectedDeviceEntity(
                                            deviceId = fields[0],
                                            deviceName = fields[1],
                                            ipAddress = fields[2],
                                            role = fields[3],
                                            scheduledSync = fields[4],
                                            bandwidthLimitKbps = fields[5].toIntOrNull() ?: 0,
                                            syncStatus = fields[6],
                                            trafficBytes = fields[7].toLongOrNull() ?: 0L,
                                            lastSeen = fields.getOrNull(8)?.toLongOrNull() ?: System.currentTimeMillis(),
                                            scheduleHour = fields.getOrNull(9)?.toIntOrNull() ?: 2,
                                            scheduleMinute = fields.getOrNull(10)?.toIntOrNull() ?: 0,
                                            configUpdatedAt = fields.getOrNull(11)?.toLongOrNull() ?: 0L,
                                            lastSyncedAt = fields.getOrNull(12)?.toLongOrNull() ?: 0L
                                        )
                                    )
                                }
                            }
                        }
                        val hubState = tokens.getOrNull(3)
                        if (!hubState.isNullOrBlank()) {
                            dao.updateDeviceStatus(token.hubDeviceId, hubState)
                        }
                        return@withContext results
                    }
                }
            } catch (e: Exception) {
                // Try next candidate endpoint
            }
        }
        results
    }

    /**
     * Sends a real-time safety limit alert over socket to notify the peer device.
     */
    suspend fun sendSafetyAlertToHub(
        token: PairingToken,
        deviceName: String,
        reason: String,
        value: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = listOf(token.localIp, token.tailscaleOrWanHost).filter { it.isNotBlank() && it != "0.0.0.0" }
        for (host in candidates) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = 3000
                    socket.connect(InetSocketAddress(host, token.port), 3000)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("SAFETY_ALERT|$deviceName|$reason|$value")
                    return@withContext true
                }
            } catch (e: Exception) {
                // Try next candidate
            }
        }
        false
    }

    /**
     * Synchronizes updated device bandwidth and schedule timer settings to the Secondary Hub over socket.
     */
    suspend fun syncDeviceConfigToHub(
        token: PairingToken,
        targetDeviceId: String,
        bandwidthLimitKbps: Int,
        scheduleHour: Int,
        scheduleMinute: Int,
        configTimestamp: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = listOf(token.localIp, token.tailscaleOrWanHost).filter { it.isNotBlank() && it != "0.0.0.0" }
        for (host in candidates) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = 3000
                    socket.connect(InetSocketAddress(host, token.port), 3000)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    writer.println("UPDATE_DEVICE_CONFIG|$targetDeviceId|$bandwidthLimitKbps|$scheduleHour|$scheduleMinute|$configTimestamp")
                    val resp = reader.readLine()
                    if (resp == "CONFIG_UPDATED") {
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                // Try next route
            }
        }
        false
    }

    suspend fun logEvent(level: String, tag: String, message: String) {
        withContext(Dispatchers.IO) {
            dao.insertLog(
                DiagnosticLogEntity(
                    level = level,
                    tag = tag,
                    message = message
                )
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RelayTransferEngine? = null

        fun getInstance(context: Context): RelayTransferEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = RelayTransferEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}