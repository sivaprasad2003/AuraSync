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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val statusBanner: String = "Engine Ready"
)

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

    var onLowStorageEmergencyCallback: (() -> Unit)? = null

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
            _telemetry.value = _telemetry.value.copy(
                queuedFileCount = priorityQueue.size,
                statusBanner = if (isUrgentInstantUpload) {
                    "Urgent Instant Upload Queued (${tasks.size} items)"
                } else {
                    "Queued ${priorityQueue.size} files for relay"
                }
            )
            ensureSenderWorkerRunning()
        }
    }

    /**
     * Starts the 24/7 Secondary Hub TCP Server on Dispatchers.IO.
     */
    fun startSecondaryHubServer(port: Int = NetworkAndSafetyMonitor.DEFAULT_HUB_PORT) {
        if (serverJob?.isActive == true) return
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
            if (parts.size < 6 || parts[0] != "PREFLIGHT") {
                writer.println("REJECT_MALFORMED")
                return@withContext
            }

            val sha256 = parts[1]
            val originalName = parts[2]
            val fileSize = parts[3].toLongOrNull() ?: 0L
            val sourceDevice = parts[4]
            val clientAuthTag = parts[5]

            // Hub Low-Storage Emergency Safeguard (< 1GB free)
            val safety = networkMonitor.evaluateHardwareSafetyGuards()
            if (safety.isLowStorageTripped || (safety.availableStorageBytes - fileSize) < NetworkAndSafetyMonitor.ONE_GIGABYTE_BYTES) {
                writer.println("REJECT_LOW_STORAGE")
                logEvent("ERROR", "StorageGuard", "Rejected $originalName: Hub free storage below 1GB threshold!")
                onLowStorageEmergencyCallback?.invoke()
                return@withContext
            }

            val secretHex = vault.getOrCreateHubSecretHex()
            val pinHash = vault.getPinHashOrDefault()
            val expectedTag = CryptoEngine.computeHandshakeAuthTag(secretHex, pinHash, sha256, fileSize)
            if (!expectedTag.equals(clientAuthTag, ignoreCase = true)) {
                writer.println("REJECT_AUTH")
                logEvent("SECURITY", "HubAuth", "Rejected unauthorized transfer from $sourceDevice")
                return@withContext
            }

            // Tier-2 Remote Pre-Flight Deduplication Check under Mutex
            val alreadyExists = hubHandshakeMutex.withLock {
                dao.isHashPresentOnHub(sha256)
            }
            if (alreadyExists) {
                writer.println("SKIP_DUPLICATE")
                _telemetry.value = _telemetry.value.copy(
                    skippedDuplicateFiles = _telemetry.value.skippedDuplicateFiles + 1,
                    statusBanner = "Deduplicated: $originalName already in Hub Vault"
                )
                return@withContext
            }

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
                            statusBanner = "Receiving $originalName from$sourceDevice"
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
                    hubHandshakeMutex.withLock {
                        dao.registerHubReceivedFile(
                            HubDeduplicationEntity(
                                sha256 = sha256,
                                savedUri = savedUri,
                                finalFileName = collisionSafeName,
                                fileSize = fileSize,
                                sourceDevice = sourceDevice,
                                receivedAt = System.currentTimeMillis()
                            )
                        )
                    }
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
                val safety = networkMonitor.evaluateHardwareSafetyGuards()
                if (!safety.isSafeToSync) {
                    val reason = when {
                        safety.isThermalTripped -> "Paused: Battery Temp ${safety.temperatureCelsius}°C exceeds 45°C"
                        safety.isLowBatteryTripped -> "Paused: Battery ${safety.batteryPercent}% unplugged (< 15%)"
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
                _telemetry.value = _telemetry.value.copy(queuedFileCount = priorityQueue.size)

                val outcome = executePrimaryFileRelay(task, token)
                if (!outcome) {
                    // Re-queue on transient network handover and wait briefly
                    priorityQueue.put(task)
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

        for ((host, port) in candidateHosts) {
            val handedOverEpochBefore = networkMonitor.networkHandoverEpoch.value
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
                    val deviceName = Build.MODEL.replace("|", "_")

                    // Tier 2: Pre-Flight Handshake
                    val handshakePayload = listOf(
                        "PREFLIGHT",
                        sha256,
                        task.fileName,
                        task.fileSize.toString(),
                        deviceName,
                        authTag
                    ).joinToString("|")
                    writer.println(handshakePayload)
                    val hubResponse = reader.readLine() ?: return@use false

                    when (hubResponse) {
                        "SKIP_DUPLICATE" -> {
                            dao.markLocalFileSynced(sha256)
                            _telemetry.value = _telemetry.value.copy(
                                skippedDuplicateFiles = _telemetry.value.skippedDuplicateFiles + 1,
                                statusBanner = "Tier-2 Hub Deduplicated: ${task.fileName}"
                            )
                            return@use true
                        }
                        "REJECT_LOW_STORAGE" -> {
                            setPaused(true)
                            onLowStorageEmergencyCallback?.invoke()
                            logEvent("ERROR", "HubStorage", "Hub storage < 1GB! Transfer paused.")
                            return@use false
                        }
                        "REJECT_AUTH" -> {
                            setPaused(true)
                            logEvent("SECURITY", "Handshake", "Hub rejected pairing token/PIN.")
                            return@use false
                        }
                        "PROCEED_STREAM" -> {
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
            }.getOrDefault(false)

            if (result) return@withContext true
        }
        false
    }

    /**
     * Bandwidth Throttling & Time-Based Rules:
     * Uncapped between 02:00 AM and 06:00 AM; capped to user-configured KB/s during daytime.
     */
    private suspend fun applyTimeBasedBandwidthThrottle(bytesSentSoFar: Long, transferStartMs: Long) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 2..5) return // Uncapped Night Window (02:00 AM - 05:59 AM)

        val limitKbps = vault.daytimeLimitKbps.value
        if (limitKbps <= 0 || limitKbps >= 100_000) return

        val allowedBytesPerMs = (limitKbps * 1024.0) / 1000.0
        val expectedElapsedMs = (bytesSentSoFar / allowedBytesPerMs).toLong()
        val actualElapsedMs = System.currentTimeMillis() - transferStartMs
        val sleepNeeded = expectedElapsedMs - actualElapsedMs
        if (sleepNeeded > 15L) {
            delay(sleepNeeded.coerceAtMost(500L))
        }
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