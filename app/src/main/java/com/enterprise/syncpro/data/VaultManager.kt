package com.enterprise.syncpro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.enterprise.syncpro.crypto.CryptoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class DeviceRole {
    PRIMARY_SENDER,
    SECONDARY_HUB
}

@Serializable
data class PairingToken(
    val hubDeviceId: String,
    val hubDeviceName: String,
    val localIp: String,
    val tailscaleOrWanHost: String,
    val port: Int,
    val pinHash: String,
    val e2eeSharedSecretHex: String,
    val issuedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExportedConfiguration(
    val role: String,
    val isDarkMode: Boolean,
    val pairedToken: PairingToken?,
    val enabledBuckets: Set<String>,
    val trustedSsids: Set<String>,
    val restrictToTrustedWifi: Boolean,
    val scheduleHour: Int,
    val scheduleMinute: Int,
    val daytimeLimitKbps: Int,
    val instantCaptureSync: Boolean,
    val devicePriorityWeight: Int
)

class VaultManager private constructor(context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "sync_pro_encrypted_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _deviceRole = MutableStateFlow(DeviceRole.PRIMARY_SENDER)
    val deviceRole: StateFlow<DeviceRole> = _deviceRole.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _showFirstLaunchOnboarding = MutableStateFlow(false)
    val showFirstLaunchOnboarding: StateFlow<Boolean> = _showFirstLaunchOnboarding.asStateFlow()

    private val _pairingToken = MutableStateFlow<PairingToken?>(null)
    val pairingToken: StateFlow<PairingToken?> = _pairingToken.asStateFlow()

    private val _enabledBuckets = MutableStateFlow<Set<String>>(
        setOf("Camera", "Screenshots", "WhatsApp Images", "WhatsApp Video", "Download")
    )
    val enabledBuckets: StateFlow<Set<String>> = _enabledBuckets.asStateFlow()

    private val _trustedSsids = MutableStateFlow<Set<String>>(emptySet())
    val trustedSsids: StateFlow<Set<String>> = _trustedSsids.asStateFlow()

    private val _restrictToTrustedWifi = MutableStateFlow(false)
    val restrictToTrustedWifi: StateFlow<Boolean> = _restrictToTrustedWifi.asStateFlow()

    private val _scheduleHour = MutableStateFlow(2)
    val scheduleHour: StateFlow<Int> = _scheduleHour.asStateFlow()

    private val _scheduleMinute = MutableStateFlow(0)
    val scheduleMinute: StateFlow<Int> = _scheduleMinute.asStateFlow()

    private val _daytimeLimitKbps = MutableStateFlow(2048) // Default 2 MB/s daytime
    val daytimeLimitKbps: StateFlow<Int> = _daytimeLimitKbps.asStateFlow()

    private val _instantCaptureSync = MutableStateFlow(true)
    val instantCaptureSync: StateFlow<Boolean> = _instantCaptureSync.asStateFlow()

    private val _devicePriorityWeight = MutableStateFlow(3) // 1..5 priority
    val devicePriorityWeight: StateFlow<Int> = _devicePriorityWeight.asStateFlow()

    private val _isPinConfigured = MutableStateFlow(false)
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()

    init {
        ioScope.launch {
            loadInitialVaultState()
        }
    }

    private suspend fun loadInitialVaultState() = withContext(Dispatchers.IO) {
        val roleStr = encryptedPrefs.getString(KEY_ROLE, DeviceRole.PRIMARY_SENDER.name)
        _deviceRole.value = runCatching { DeviceRole.valueOf(roleStr!!) }
            .getOrDefault(DeviceRole.PRIMARY_SENDER)

        _isDarkMode.value = encryptedPrefs.getBoolean(KEY_DARK_MODE, true)

        val hasCompletedOnboarding = encryptedPrefs.getBoolean(KEY_ONBOARDING_DONE, false)
        _showFirstLaunchOnboarding.value = !hasCompletedOnboarding

        val tokenJson = encryptedPrefs.getString(KEY_PAIRING_TOKEN, null)
        if (!tokenJson.isNullOrBlank()) {
            _pairingToken.value = runCatching {
                json.decodeFromString<PairingToken>(tokenJson)
            }.getOrNull()
        }

        val savedBuckets = encryptedPrefs.getStringSet(KEY_ENABLED_BUCKETS, null)
        if (savedBuckets != null) {
            _enabledBuckets.value = savedBuckets.toSet()
        }

        val savedSsids = encryptedPrefs.getStringSet(KEY_TRUSTED_SSIDS, null)
        if (savedSsids != null) {
            _trustedSsids.value = savedSsids.toSet()
        }

        _restrictToTrustedWifi.value = encryptedPrefs.getBoolean(KEY_RESTRICT_WIFI, false)
        _scheduleHour.value = encryptedPrefs.getInt(KEY_SCHEDULE_HOUR, 2)
        _scheduleMinute.value = encryptedPrefs.getInt(KEY_SCHEDULE_MINUTE, 0)
        _daytimeLimitKbps.value = encryptedPrefs.getInt(KEY_DAYTIME_KBPS, 2048)
        _instantCaptureSync.value = encryptedPrefs.getBoolean(KEY_INSTANT_CAPTURE, true)
        _devicePriorityWeight.value = encryptedPrefs.getInt(KEY_PRIORITY_WEIGHT, 3).coerceIn(1, 5)
        _isPinConfigured.value = !encryptedPrefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

        if (encryptedPrefs.getString(KEY_DEVICE_ID, null) == null) {
            encryptedPrefs.edit().putString(KEY_DEVICE_ID, UUID.randomUUID().toString()).apply()
        }
        if (encryptedPrefs.getString(KEY_E2EE_SECRET, null) == null) {
            encryptedPrefs.edit()
                .putString(KEY_E2EE_SECRET, CryptoEngine.generateRandomHexSecret(32))
                .apply()
        }
    }

    suspend fun getOrCreateDeviceId(): String = withContext(Dispatchers.IO) {
        var id = encryptedPrefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(KEY_DEVICE_ID, id).commit()
        }
        id
    }

    suspend fun getOrCreateHubSecretHex(): String = withContext(Dispatchers.IO) {
        var secret = encryptedPrefs.getString(KEY_E2EE_SECRET, null)
        if (secret.isNullOrBlank()) {
            secret = CryptoEngine.generateRandomHexSecret(32)
            encryptedPrefs.edit().putString(KEY_E2EE_SECRET, secret).commit()
        }
        secret
    }

    suspend fun setDeviceRole(role: DeviceRole) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_ROLE, role.name).commit()
        _deviceRole.value = role
    }

    suspend fun setDarkMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_DARK_MODE, enabled).commit()
        _isDarkMode.value = enabled
    }

    suspend fun completeFirstLaunchOnboarding() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).commit()
        _showFirstLaunchOnboarding.value = false
    }

    suspend fun configurePin(rawSixDigitPin: String): Boolean = withContext(Dispatchers.IO) {
        if (rawSixDigitPin.length != 6 || !rawSixDigitPin.all { it.isDigit() }) return@withContext false
        val hashed = CryptoEngine.sha256String("SYNCPRO_VAULT_SALT:$rawSixDigitPin")
        encryptedPrefs.edit().putString(KEY_PIN_HASH, hashed).commit()
        _isPinConfigured.value = true
        true
    }

    suspend fun verifyPin(rawSixDigitPin: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return@withContext false
        val candidateHash = CryptoEngine.sha256String("SYNCPRO_VAULT_SALT:$rawSixDigitPin")
        storedHash.equals(candidateHash, ignoreCase = true)
    }

    suspend fun getPinHashOrDefault(): String = withContext(Dispatchers.IO) {
        encryptedPrefs.getString(KEY_PIN_HASH, null)
            ?: CryptoEngine.sha256String("SYNCPRO_VAULT_SALT:000000")
    }

    suspend fun savePairingToken(token: PairingToken) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(token)
        encryptedPrefs.edit().putString(KEY_PAIRING_TOKEN, raw).commit()
        _pairingToken.value = token
    }

    fun encodePairingTokenToJson(token: PairingToken): String = json.encodeToString(token)

    fun decodePairingTokenFromJson(rawJson: String): PairingToken? = runCatching {
        json.decodeFromString<PairingToken>(rawJson)
    }.getOrNull()

    suspend fun toggleBucketFilter(bucketName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val updated = _enabledBuckets.value.toMutableSet()
        if (enabled) updated.add(bucketName) else updated.remove(bucketName)
        encryptedPrefs.edit().putStringSet(KEY_ENABLED_BUCKETS, updated).commit()
        _enabledBuckets.value = updated
    }

    suspend fun addTrustedSsid(ssid: String) = withContext(Dispatchers.IO) {
        val clean = ssid.trim().removeSurrounding("\"")
        if (clean.isEmpty()) return@withContext
        val updated = _trustedSsids.value.toMutableSet().apply { add(clean) }
        encryptedPrefs.edit().putStringSet(KEY_TRUSTED_SSIDS, updated).commit()
        _trustedSsids.value = updated
    }

    suspend fun removeTrustedSsid(ssid: String) = withContext(Dispatchers.IO) {
        val updated = _trustedSsids.value.toMutableSet().apply { remove(ssid) }
        encryptedPrefs.edit().putStringSet(KEY_TRUSTED_SSIDS, updated).commit()
        _trustedSsids.value = updated
    }

    suspend fun setRestrictToTrustedWifi(restrict: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_RESTRICT_WIFI, restrict).commit()
        _restrictToTrustedWifi.value = restrict
    }

    suspend fun setDailySchedule(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        encryptedPrefs.edit()
            .putInt(KEY_SCHEDULE_HOUR, h)
            .putInt(KEY_SCHEDULE_MINUTE, m)
            .commit()
        _scheduleHour.value = h
        _scheduleMinute.value = m
    }

    suspend fun setDaytimeLimitKbps(kbps: Int) = withContext(Dispatchers.IO) {
        val safeKbps = kbps.coerceAtLeast(256)
        encryptedPrefs.edit().putInt(KEY_DAYTIME_KBPS, safeKbps).commit()
        _daytimeLimitKbps.value = safeKbps
    }

    suspend fun setInstantCaptureSync(enabled: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_INSTANT_CAPTURE, enabled).commit()
        _instantCaptureSync.value = enabled
    }

    suspend fun setDevicePriorityWeight(weight: Int) = withContext(Dispatchers.IO) {
        val w = weight.coerceIn(1, 5)
        encryptedPrefs.edit().putInt(KEY_PRIORITY_WEIGHT, w).commit()
        _devicePriorityWeight.value = w
    }

    suspend fun exportConfigurationJson(): String = withContext(Dispatchers.IO) {
        val config = ExportedConfiguration(
            role = _deviceRole.value.name,
            isDarkMode = _isDarkMode.value,
            pairedToken = _pairingToken.value,
            enabledBuckets = _enabledBuckets.value,
            trustedSsids = _trustedSsids.value,
            restrictToTrustedWifi = _restrictToTrustedWifi.value,
            scheduleHour = _scheduleHour.value,
            scheduleMinute = _scheduleMinute.value,
            daytimeLimitKbps = _daytimeLimitKbps.value,
            instantCaptureSync = _instantCaptureSync.value,
            devicePriorityWeight = _devicePriorityWeight.value
        )
        json.encodeToString(config)
    }

    suspend fun importConfigurationJson(rawJson: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val config = json.decodeFromString<ExportedConfiguration>(rawJson)
            setDeviceRole(DeviceRole.valueOf(config.role))
            setDarkMode(config.isDarkMode)
            config.pairedToken?.let { savePairingToken(it) }
            encryptedPrefs.edit()
                .putStringSet(KEY_ENABLED_BUCKETS, config.enabledBuckets)
                .putStringSet(KEY_TRUSTED_SSIDS, config.trustedSsids)
                .putBoolean(KEY_RESTRICT_WIFI, config.restrictToTrustedWifi)
                .putInt(KEY_SCHEDULE_HOUR, config.scheduleHour.coerceIn(0, 23))
                .putInt(KEY_SCHEDULE_MINUTE, config.scheduleMinute.coerceIn(0, 59))
                .putInt(KEY_DAYTIME_KBPS, config.daytimeLimitKbps.coerceAtLeast(256))
                .putBoolean(KEY_INSTANT_CAPTURE, config.instantCaptureSync)
                .putInt(KEY_PRIORITY_WEIGHT, config.devicePriorityWeight.coerceIn(1, 5))
                .commit()
            loadInitialVaultState()
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_E2EE_SECRET = "e2ee_secret"
        private const val KEY_ROLE = "device_role"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_PAIRING_TOKEN = "pairing_token"
        private const val KEY_ENABLED_BUCKETS = "enabled_buckets"
        private const val KEY_TRUSTED_SSIDS = "trusted_ssids"
        private const val KEY_RESTRICT_WIFI = "restrict_wifi"
        private const val KEY_SCHEDULE_HOUR = "schedule_hour"
        private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
        private const val KEY_DAYTIME_KBPS = "daytime_kbps"
        private const val KEY_INSTANT_CAPTURE = "instant_capture"
        private const val KEY_PRIORITY_WEIGHT = "priority_weight"
        private const val KEY_PIN_HASH = "pin_hash"

        @Volatile
        private var INSTANCE: VaultManager? = null

        fun getInstance(context: Context): VaultManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VaultManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}