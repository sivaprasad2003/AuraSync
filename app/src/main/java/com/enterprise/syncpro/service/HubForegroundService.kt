package com.enterprise.syncpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.data.VaultManager
import com.enterprise.syncpro.media.MediaAndQrEngine
import com.enterprise.syncpro.network.NetworkAndSafetyMonitor
import com.enterprise.syncpro.network.RelayTransferEngine
import com.enterprise.syncpro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HubForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var vault: VaultManager
    private lateinit var relayEngine: RelayTransferEngine
    private lateinit var networkMonitor: NetworkAndSafetyMonitor
    private lateinit var notificationManager: NotificationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaObserver: ContentObserver? = null
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        vault = VaultManager.getInstance(applicationContext)
        relayEngine = RelayTransferEngine.getInstance(applicationContext)
        networkMonitor = NetworkAndSafetyMonitor.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        acquireHardwareLocks()
        registerMediaStoreInstantCaptureObserver()

        relayEngine.onLowStorageEmergencyCallback = {
            postLowStorageEmergencyNotification()
        }

        val initialNotification = buildTelemetryNotification(
            title = "Sync Pro 24/7 Engine Active",
            statusText = "Initializing P2P Relay & mDNS Discovery...",
            progress = 0,
            isIndeterminate = true,
            isPaused = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_FOREGROUND,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, initialNotification)
        }

        observeRoleAndTelemetry()
        startHardwareSafetyWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_IMMEDIATE_SCAN -> {
                serviceScope.launch {
                    val buckets = vault.enabledBuckets.value
                    val weight = vault.devicePriorityWeight.value
                    val tasks = MediaAndQrEngine.scanEnabledBucketsForRelay(
                        applicationContext,
                        buckets,
                        weight
                    )
                    relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = false)
                }
            }
        }
        return START_STICKY
    }

    private fun observeRoleAndTelemetry() {
        serviceScope.launch {
            vault.deviceRole.collectLatest { role ->
                if (role == DeviceRole.SECONDARY_HUB) {
                    networkMonitor.stopMdnsDiscovery()
                    relayEngine.startSecondaryHubServer(NetworkAndSafetyMonitor.DEFAULT_HUB_PORT)
                } else {
                    relayEngine.stopSecondaryHubServer()
                    networkMonitor.startMdnsDiscovery()
                }
            }
        }

        serviceScope.launch {
            relayEngine.telemetry.collectLatest { state ->
                val roleLabel = if (vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
                    "Secondary Hub (Receiver)"
                } else {
                    "Primary Node (Sender)"
                }

                val progressPercent = (state.activeFileProgress * 100f).roundToInt().coerceIn(0, 100)
                val speedFormatted = String.format("%.2f MB/s", state.currentSpeedMbps)
                val bodyText = if (state.isRunning && state.activeFileName != "Idle") {
                    "${state.activeFileName} • $speedFormatted • Queue: ${state.queuedFileCount}"
                } else {
                    "${state.statusBanner} • Completed: ${state.completedSessionFiles}"
                }

                val updatedNotification = buildTelemetryNotification(
                    title = "Sync Pro • $roleLabel",
                    statusText = bodyText,
                    progress = progressPercent,
                    isIndeterminate = false,
                    isPaused = state.isPaused
                )
                notificationManager.notify(NOTIFICATION_ID_FOREGROUND, updatedNotification)
            }
        }
    }

    private fun startHardwareSafetyWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                // Renew WakeLock lease safely before expiration
                if (wakeLock?.isHeld == false) {
                    runCatching { wakeLock?.acquire(12 * 60 * 60 * 1000L) }
                }
                if (wifiLock?.isHeld == false) {
                    runCatching { wifiLock?.acquire() }
                }
                val safety = networkMonitor.evaluateHardwareSafetyGuards()
                if (safety.isLowStorageTripped && vault.deviceRole.value == DeviceRole.SECONDARY_HUB) {
                    postLowStorageEmergencyNotification()
                }
                delay(15_000L)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireHardwareLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SyncPro::UnstoppableRelayWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(lockMode, "SyncPro::HighPerfWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun registerMediaStoreInstantCaptureObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!vault.instantCaptureSync.value) return
                if (vault.deviceRole.value != DeviceRole.PRIMARY_SENDER) return

                serviceScope.launch(Dispatchers.IO) {
                    val buckets = vault.enabledBuckets.value
                    val tasks = MediaAndQrEngine.scanEnabledBucketsForRelay(
                        context = applicationContext,
                        enabledBuckets = buckets,
                        priorityWeight = 5 // Instant capture gets priority weight 5
                    )
                    if (tasks.isNotEmpty()) {
                        relayEngine.enqueueTasks(tasks.take(5), isUrgentInstantUpload = true)
                    }
                }
            }
        }

        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            mediaObserver = observer
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val relayChannel = NotificationChannel(
                CHANNEL_ID_RELAY,
                "Sync Pro 24/7 Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live transfer telemetry and interactive relay controls"
                setShowBadge(false)
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "Sync Pro Storage & Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when Hub storage drops below 1GB or thermal limits trip"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(relayChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    private fun buildTelemetryNotification(
        title: String,
        statusText: String,
        progress: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val syncNowIntent = Intent(this, SyncNotificationReceiver::class.java).apply {
            action = SyncNotificationReceiver.ACTION_SYNC_NOW
        }
        val syncNowPending = PendingIntent.getBroadcast(
            this,
            101,
            syncNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, SyncNotificationReceiver::class.java).apply {
            action = SyncNotificationReceiver.ACTION_PAUSE_RESUME
        }
        val pauseResumePending = PendingIntent.getBroadcast(
            this,
            102,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseActionLabel = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID_RELAY)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, isIndeterminate)
            .addAction(android.R.drawable.ic_popup_sync, "Sync Now", syncNowPending)
            .addAction(android.R.drawable.ic_media_pause, pauseActionLabel, pauseResumePending)
            .build()
    }

    private fun postLowStorageEmergencyNotification() {
        val alert = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Emergency: Hub Free Storage Below 1 GB")
            .setContentText("Incoming media transfers are automatically blocked to prevent system corruption. Free up space on Secondary Hub.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Secondary Hub available storage has dropped below the 1 GB safety floor. " +
                            "Sync Pro has paused incoming payloads until Google Photos Free-Up-Space is run."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_EMERGENCY, alert)
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        mediaObserver?.let {
            runCatching { contentResolver.unregisterContentObserver(it) }
        }
        relayEngine.stopSecondaryHubServer()
        networkMonitor.stopMdnsDiscovery()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID_RELAY = "sync_pro_relay_channel"
        const val CHANNEL_ID_EMERGENCY = "sync_pro_emergency_channel"
        const val NOTIFICATION_ID_FOREGROUND = 4901
        const val NOTIFICATION_ID_EMERGENCY = 4902

        const val ACTION_START_SERVICE = "com.enterprise.syncpro.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.enterprise.syncpro.ACTION_STOP_SERVICE"
        const val ACTION_TRIGGER_IMMEDIATE_SCAN = "com.enterprise.syncpro.ACTION_TRIGGER_IMMEDIATE_SCAN"

        fun startOrUpdate(context: Context, action: String = ACTION_START_SERVICE) {
            val intent = Intent(context, HubForegroundService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

class SyncNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val relayEngine = RelayTransferEngine.getInstance(context)
        when (intent?.action) {
            ACTION_SYNC_NOW -> {
                relayEngine.setPaused(false)
                HubForegroundService.startOrUpdate(
                    context,
                    HubForegroundService.ACTION_TRIGGER_IMMEDIATE_SCAN
                )
            }
            ACTION_PAUSE_RESUME -> {
                relayEngine.togglePause()
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                HubForegroundService.startOrUpdate(context)
                SyncWorkers.scheduleDailyAutomatedSync(context)
            }
        }
    }

    companion object {
        const val ACTION_SYNC_NOW = "com.enterprise.syncpro.ACTION_SYNC_NOW"
        const val ACTION_PAUSE_RESUME = "com.enterprise.syncpro.ACTION_PAUSE_RESUME"
    }
}