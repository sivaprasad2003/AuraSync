package com.enterprise.syncpro.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "local_file_index",
    indices = [
        Index(value = ["file_path", "file_size", "last_modified"], unique = true),
        Index(value = ["sha256"])
    ]
)
data class LocalFileIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "bucket_name") val bucketName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean,
    @ColumnInfo(name = "synced_timestamp") val syncedTimestamp: Long
)

@Entity(
    tableName = "hub_deduplication_registry",
    indices = [Index(value = ["sha256"], unique = true)]
)
data class HubDeduplicationEntity(
    @PrimaryKey
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "saved_uri") val savedUri: String,
    @ColumnInfo(name = "final_file_name") val finalFileName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "source_device") val sourceDevice: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "level") val level: String, // INFO, WARN, ERROR, SECURITY
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "message") val message: String
)

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey val deviceId: String,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "ip_address") val ipAddress: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "scheduled_sync") val scheduledSync: String = "02:00 AM",
    @ColumnInfo(name = "schedule_hour") val scheduleHour: Int = 2,
    @ColumnInfo(name = "schedule_minute") val scheduleMinute: Int = 0,
    @ColumnInfo(name = "bandwidth_limit_kbps") val bandwidthLimitKbps: Int = 0,
    @ColumnInfo(name = "throttle_start_hour") val throttleStartHour: Int = 2,
    @ColumnInfo(name = "throttle_start_minute") val throttleStartMinute: Int = 0,
    @ColumnInfo(name = "throttle_end_hour") val throttleEndHour: Int = 5,
    @ColumnInfo(name = "throttle_end_minute") val throttleEndMinute: Int = 0,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "Idle",
    @ColumnInfo(name = "traffic_bytes") val trafficBytes: Long = 0L,
    @ColumnInfo(name = "config_updated_at") val configUpdatedAt: Long = 0L,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long = 0L,
    @ColumnInfo(name = "last_seen") val lastSeen: Long = System.currentTimeMillis()
)

@Dao
interface SyncDao {
    // Tier 1 Fast-Path Local Index lookup
    @Query(
        """
        SELECT * FROM local_file_index 
        WHERE file_path = :path AND file_size = :size AND last_modified = :lastModified 
        LIMIT 1
        """
    )
    suspend fun findFastPathMatch(
        path: String,
        size: Long,
        lastModified: Long
    ): LocalFileIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalFileIndex(entity: LocalFileIndexEntity)

    @Query("UPDATE local_file_index SET is_synced = 1, synced_timestamp = :timestamp WHERE sha256 = :sha256")
    suspend fun markLocalFileSynced(sha256: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM local_file_index WHERE is_synced = 1")
    fun observeSyncedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM local_file_index WHERE is_synced = 1")
    fun observeTotalSyncedBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM local_file_index WHERE is_synced = 1")
    suspend fun getTotalSyncedBytes(): Long

    @Query("SELECT * FROM local_file_index WHERE is_synced = 1 ORDER BY synced_timestamp DESC LIMIT 50")
    fun observeRecentUploadedFiles(): Flow<List<LocalFileIndexEntity>>

    @Query("SELECT * FROM local_file_index WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findLocalFileByHash(sha256: String): LocalFileIndexEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM local_file_index WHERE sha256 = :sha256)")
    suspend fun isHashPresentLocally(sha256: String): Boolean

    // Tier 2 Remote Pre-Flight Handshake lookup on Secondary Hub
    @Query("SELECT EXISTS(SELECT 1 FROM hub_deduplication_registry WHERE sha256 = :sha256)")
    suspend fun isHashPresentOnHub(sha256: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registerHubReceivedFile(entity: HubDeduplicationEntity)

    @Query("SELECT COUNT(*) FROM hub_deduplication_registry")
    fun observeHubReceivedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM hub_deduplication_registry")
    fun observeHubTotalBytes(): Flow<Long>

    @Query("SELECT * FROM hub_deduplication_registry ORDER BY received_at DESC LIMIT 50")
    fun observeRecentHubFiles(): Flow<List<HubDeduplicationEntity>>

    // Multi-Device Peer Mesh Tracking
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnectedDevice(device: ConnectedDeviceEntity)

    @Query("SELECT * FROM connected_devices ORDER BY last_seen DESC")
    fun observeConnectedDevices(): Flow<List<ConnectedDeviceEntity>>

    @Query("SELECT * FROM connected_devices ORDER BY last_seen DESC")
    suspend fun getConnectedDevicesList(): List<ConnectedDeviceEntity>

    @Query("SELECT * FROM connected_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getConnectedDevice(deviceId: String): ConnectedDeviceEntity?

    @Query("SELECT * FROM connected_devices WHERE ip_address = :ip LIMIT 1")
    suspend fun getConnectedDeviceByIp(ip: String): ConnectedDeviceEntity?

    @Query("DELETE FROM connected_devices WHERE deviceId != :canonicalId AND ip_address = :ip AND ip_address != '' AND ip_address != 'Wi-Fi LAN' AND ip_address != '0.0.0.0'")
    suspend fun deleteDuplicatesByIp(canonicalId: String, ip: String)

    @Query("DELETE FROM connected_devices WHERE deviceId != :canonicalId AND device_name = :name AND device_name != '' AND device_name != 'Primary Sender'")
    suspend fun deleteDuplicatesByName(canonicalId: String, name: String)

    suspend fun deleteDuplicates(canonicalId: String, ip: String, name: String) {
        if (ip.isNotBlank() && ip != "Wi-Fi LAN" && ip != "0.0.0.0") {
            deleteDuplicatesByIp(canonicalId, ip)
        }
        if (name.isNotBlank() && name != "Primary Sender") {
            deleteDuplicatesByName(canonicalId, name)
        }
    }

    @Query("UPDATE connected_devices SET sync_status = :status, last_seen = :now WHERE deviceId = :deviceId")
    suspend fun updateDeviceStatus(deviceId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE connected_devices SET bandwidth_limit_kbps = :bandwidthLimitKbps WHERE deviceId = :deviceId")
    suspend fun updateDeviceBandwidth(deviceId: String, bandwidthLimitKbps: Int)

    @Query("UPDATE connected_devices SET bandwidth_limit_kbps = :bandwidthLimitKbps, schedule_hour = :scheduleHour, schedule_minute = :scheduleMinute, scheduled_sync = :scheduledSync, config_updated_at = :configUpdatedAt, last_seen = :now WHERE deviceId = :deviceId")
    suspend fun updateDeviceScheduleAndBandwidth(
        deviceId: String,
        bandwidthLimitKbps: Int,
        scheduleHour: Int,
        scheduleMinute: Int,
        scheduledSync: String,
        configUpdatedAt: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE connected_devices SET bandwidth_limit_kbps = :bandwidthLimitKbps, schedule_hour = :scheduleHour, schedule_minute = :scheduleMinute, scheduled_sync = :scheduledSync, config_updated_at = :configUpdatedAt, last_seen = :now")
    suspend fun updateAllDevicesScheduleAndBandwidth(
        bandwidthLimitKbps: Int,
        scheduleHour: Int,
        scheduleMinute: Int,
        scheduledSync: String,
        configUpdatedAt: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE connected_devices SET bandwidth_limit_kbps = :bandwidthLimitKbps, throttle_start_hour = :startHour, throttle_start_minute = :startMinute, throttle_end_hour = :endHour, throttle_end_minute = :endMinute WHERE deviceId = :deviceId")
    suspend fun updateDeviceSettings(
        deviceId: String,
        bandwidthLimitKbps: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    )

    @Query("UPDATE connected_devices SET traffic_bytes = traffic_bytes + :bytesAdded, last_seen = :now, sync_status = :status WHERE deviceId = :deviceId")
    suspend fun recordDeviceTraffic(deviceId: String, bytesAdded: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE connected_devices SET traffic_bytes = traffic_bytes + :bytesAdded, last_seen = :now, last_synced_at = :now, sync_status = :status WHERE deviceId = :deviceId")
    suspend fun recordDeviceTrafficAndSync(deviceId: String, bytesAdded: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE connected_devices SET last_synced_at = :now, sync_status = :status, last_seen = :now WHERE deviceId = :deviceId")
    suspend fun markDeviceSynced(deviceId: String, status: String = "Connected", now: Long = System.currentTimeMillis())

    @Query("DELETE FROM connected_devices WHERE deviceId = :deviceId")
    suspend fun removeConnectedDevice(deviceId: String)

    // Diagnostic Logging
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLogEntity)

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeDiagnosticLogs(): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearDiagnosticLogs()
}

@Database(
    entities = [
        LocalFileIndexEntity::class,
        HubDeduplicationEntity::class,
        DiagnosticLogEntity::class,
        ConnectedDeviceEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var INSTANCE: SyncDatabase? = null

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Version 6 added config_updated_at and last_synced_at to connected_devices
                // Safe no-op: columns already exist in schema from version 6 creation
                // If upgrading from exact version 5, add columns that might be missing
                runCatching {
                    db.execSQL("ALTER TABLE connected_devices ADD COLUMN config_updated_at INTEGER NOT NULL DEFAULT 0")
                }
                runCatching {
                    db.execSQL("ALTER TABLE connected_devices ADD COLUMN last_synced_at INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun getInstance(context: Context): SyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "sync_pro_enterprise.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}