package com.enterprise.syncpro.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.data.VaultManager
import com.enterprise.syncpro.media.MediaAndQrEngine
import com.enterprise.syncpro.network.NetworkAndSafetyMonitor
import com.enterprise.syncpro.network.RelayTransferEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutomatedRelayWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val vault = VaultManager.getInstance(applicationContext)
        val networkMonitor = NetworkAndSafetyMonitor.getInstance(applicationContext)
        val relayEngine = RelayTransferEngine.getInstance(applicationContext)

        if (vault.deviceRole.value != DeviceRole.PRIMARY_SENDER) {
            return@withContext Result.success()
        }

        val safety = networkMonitor.evaluateHardwareSafetyGuards()
        if (!safety.isSafeToSync) {
            relayEngine.logEvent(
                "WARN",
                "WorkManager",
                "Skipped scheduled sync due to thermal/battery/storage safety switch."
            )
            return@withContext if (runAttemptCount < MAX_WORK_RETRIES) Result.retry() else Result.failure()
        }

        if (!networkMonitor.isTrustedWifiAllowed(vault.restrictToTrustedWifi.value, vault.trustedSsids.value)) {
            relayEngine.logEvent(
                "WARN",
                "WorkManager",
                "Skipped scheduled sync: Current Wi-Fi SSID is not in Trusted Whitelist."
            )
            return@withContext Result.success()
        }

        val token = vault.pairingToken.value ?: return@withContext Result.success()

        // Exponential Backoff Probe (Initial delay 5s -> 10s -> 20s, Max 3 retries)
        var hubReachable = false
        var attempt = 0
        var backoffDelayMs = INITIAL_BACKOFF_DELAY_MS

        while (attempt <= MAX_WORK_RETRIES && !hubReachable) {
            hubReachable = probeHubReachability(token.localIp, token.tailscaleOrWanHost, token.port)
            if (!hubReachable) {
                attempt++
                if (attempt <= MAX_WORK_RETRIES) {
                    relayEngine.logEvent(
                        "WARN",
                        "BackoffRetry",
                        "Hub busy/unreachable. Retry $attempt/$MAX_WORK_RETRIES in ${backoffDelayMs / 1000}s..."
                    )
                    delay(backoffDelayMs)
                    backoffDelayMs *= 2L
                }
            }
        }

        if (!hubReachable) {
            return@withContext if (runAttemptCount < MAX_WORK_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

        val tasks = MediaAndQrEngine.scanEnabledBucketsForRelay(
            context = applicationContext,
            enabledBuckets = vault.enabledBuckets.value,
            priorityWeight = vault.devicePriorityWeight.value
        )

        if (tasks.isNotEmpty()) {
            HubForegroundService.startOrUpdate(applicationContext)
            relayEngine.setPaused(false)
            relayEngine.enqueueTasks(tasks, isUrgentInstantUpload = false)
        }

        Result.success()
    }

    private suspend fun probeHubReachability(
        lanHost: String,
        wanOrTailscaleHost: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = listOf(lanHost, wanOrTailscaleHost).filter {
            it.isNotBlank() && it != "0.0.0.0"
        }
        for (host in candidates) {
            val reachable = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 4_000)
                    true
                }
            }.getOrDefault(false)
            if (reachable) return@withContext true
        }
        false
    }

    companion object {
        const val INITIAL_BACKOFF_DELAY_MS = 5_000L
        const val MAX_WORK_RETRIES = 3
    }
}

object SyncWorkers {

    private const val UNIQUE_DAILY_WORK_NAME = "SyncProDailyScheduledWork"
    private const val UNIQUE_IMMEDIATE_WORK_NAME = "SyncProImmediateBackoffWork"

    /**
     * Calculates exact millisecond target delay (initialDelayMillis) from current time
     * to the next occurrence of targetHour:targetMinute.
     */
    fun computeInitialDelayMillis(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, targetMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
    }

    fun scheduleDailyAutomatedSync(context: Context) {
        val vault = VaultManager.getInstance(context)
        val hour = vault.scheduleHour.value
        val minute = vault.scheduleMinute.value
        val initialDelayMillis = computeInitialDelayMillis(hour, minute)

        val periodicRequest = PeriodicWorkRequestBuilder<AutomatedRelayWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10_000L, // WorkManager OS minimum floor; 5s initial backoff runs inside doWork()
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun triggerImmediateBackoffWorker(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<AutomatedRelayWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10_000L,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}