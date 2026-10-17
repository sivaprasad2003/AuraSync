package com.enterprise.syncpro.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.enterprise.syncpro.network.RelayQueueTask
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MediaBucketSummary(
    val bucketName: String,
    val relativePathHint: String,
    val fileCount: Int,
    val totalBytes: Long,
    val sampleFiles: List<RelayQueueTask>
)

data class ExistingMediaMatch(
    val filePath: String,
    val fileName: String,
    val bucketName: String,
    val fileSize: Long,
    val lastModified: Long,
    val contentUri: String
)

object MediaAndQrEngine {

    val DEFAULT_KNOWN_BUCKETS = listOf(
        "Camera" to "DCIM/Camera",
        "Screenshots" to "DCIM/Screenshots",
        "WhatsApp Images" to "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
        "WhatsApp Video" to "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
        "Download" to "Download"
    )

    /**
     * Queries MediaStore Images & Video buckets on Dispatchers.IO with strict
     * Scoped Storage SecurityException protection.
     */
    suspend fun queryAllMediaBuckets(
        context: Context,
        defaultPriorityWeight: Int
    ): List<MediaBucketSummary> = withContext(Dispatchers.IO) {
        val bucketMap = linkedMapOf<String, MutableList<RelayQueueTask>>()
        DEFAULT_KNOWN_BUCKETS.forEach { (name, _) ->
            bucketMap[name] = mutableListOf()
        }

        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        if (!file.exists() || path.contains("/.tmp_")) continue

                        val name = cursor.getString(nameCol) ?: file.name
                        val bucket = cursor.getString(bucketCol) ?: "Other"
                        val size = cursor.getLong(sizeCol).coerceAtLeast(file.length())
                        val modified = cursor.getLong(modCol) * 1000L

                        val task = RelayQueueTask(
                            filePath = path,
                            fileName = name,
                            bucketName = bucket,
                            fileSize = size,
                            lastModified = if (modified > 0L) modified else file.lastModified(),
                            priorityWeight = defaultPriorityWeight.coerceIn(1, 5)
                        )
                        bucketMap.getOrPut(bucket) { mutableListOf() }.add(task)
                    }
                }
            } catch (_: SecurityException) {
                // Scoped Storage permission not yet granted; return safe defaults
            } catch (_: Exception) {
            }
        }

        bucketMap.map { (bucketName, tasks) ->
            val pathHint = DEFAULT_KNOWN_BUCKETS.firstOrNull {
                it.first.equals(bucketName, ignoreCase = true)
            }?.second ?: "Storage/$bucketName"

            MediaBucketSummary(
                bucketName = bucketName,
                relativePathHint = pathHint,
                fileCount = tasks.size,
                totalBytes = tasks.sumOf { it.fileSize },
                sampleFiles = tasks
            )
        }
    }

    /**
     * Scans only user-checked buckets and returns prioritized tasks for RelayTransferEngine.
     */
    suspend fun scanEnabledBucketsForRelay(
        context: Context,
        enabledBuckets: Set<String>,
        priorityWeight: Int
    ): List<RelayQueueTask> = withContext(Dispatchers.IO) {
        val allBuckets = queryAllMediaBuckets(context, priorityWeight)
        allBuckets
            .filter { summary ->
                enabledBuckets.any { it.equals(summary.bucketName, ignoreCase = true) }
            }
            .flatMap { it.sampleFiles }
    }

    /**
     * Efficiently queries images & videos captured or modified in the last [withinLastSeconds] seconds
     * for instant, zero-delay MediaStore capture triggering.
     */
    suspend fun queryRecentCaptures(
        context: Context,
        withinLastSeconds: Long = 180
    ): List<RelayQueueTask> = withContext(Dispatchers.IO) {
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - withinLastSeconds
        val results = mutableListOf<RelayQueueTask>()
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?"
        val selectionArgs = arrayOf(cutoffSeconds.toString())

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        if (!file.exists() || path.contains("/.tmp_")) continue

                        val name = cursor.getString(nameCol) ?: file.name
                        val bucket = cursor.getString(bucketCol) ?: "Camera"
                        val size = cursor.getLong(sizeCol).coerceAtLeast(file.length())
                        val modified = cursor.getLong(modCol) * 1000L

                        results.add(
                            RelayQueueTask(
                                filePath = path,
                                fileName = name,
                                bucketName = bucket,
                                fileSize = size,
                                lastModified = if (modified > 0L) modified else file.lastModified(),
                                priorityWeight = 5 // Express Priority 5 for instant camera captures
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        results.distinctBy { it.filePath }
    }

    /**
     * Generates a high-contrast QR Code Bitmap from a serialized PairingToken JSON string
     * on Dispatchers.IO.
     */
    suspend fun generateQrCodeBitmap(
        payloadJson: String,
        sizePx: Int = 640
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val matrix = MultiFormatWriter().encode(
                payloadJson,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx
            )
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix.get(x, y)) Color.parseColor("#080B11") else Color.WHITE
                    )
                }
            }
            bitmap
        }.getOrNull()
    }

    /**
     * Efficiently scans MediaStore on the Secondary phone for an already-existing photo or video
     * that matches the target file size, name, and SHA-256 hash.
     * Prevents duplicate files from being created in DCIM/Camera.
     */
    suspend fun findExistingMediaMatch(
        context: Context,
        targetSha256: String,
        targetFileName: String,
        targetFileSize: Long
    ): ExistingMediaMatch? = withContext(Dispatchers.IO) {
        if (targetFileSize <= 0L) return@withContext null

        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        // Try exact name + size first for instant hit, then fallback to same size
        val queryPlans = listOf(
            ("${MediaStore.MediaColumns.SIZE} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?") to arrayOf(targetFileSize.toString(), targetFileName),
            ("${MediaStore.MediaColumns.SIZE} = ?") to arrayOf(targetFileSize.toString())
        )

        for ((selection, selectionArgs) in queryPlans) {
            for (uri in uris) {
                try {
                    context.contentResolver.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataCol) ?: continue
                            val file = File(path)
                            if (!file.exists() || path.contains("/.tmp_")) continue

                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: file.name
                            val bucket = cursor.getString(bucketCol) ?: "Storage"
                            val size = cursor.getLong(sizeCol).coerceAtLeast(file.length())
                            val mod = cursor.getLong(modCol) * 1000L

                            // Compute cryptographic hash to guarantee 100% identity
                            val hash = com.enterprise.syncpro.crypto.CryptoEngine.computeFileSha256Chunked(file)
                            if (hash.equals(targetSha256, ignoreCase = true)) {
                                val itemUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                                return@withContext ExistingMediaMatch(
                                    filePath = path,
                                    fileName = name,
                                    bucketName = bucket,
                                    fileSize = size,
                                    lastModified = if (mod > 0L) mod else file.lastModified(),
                                    contentUri = itemUri
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return@withContext null
    }
}

/**
 * Real-time CameraX ImageAnalysis.Analyzer that decodes QR codes on Dispatchers.IO.
 */
class QrCodeCameraAnalyzer(
    private val onQrPayloadDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    @Volatile
    private var isLocked = false

    override fun analyze(image: ImageProxy) {
        if (isLocked) {
            image.close()
            return
        }
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = image.width
            val height = image.height
            val source = PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = runCatching { reader.decodeWithState(binaryBitmap) }.getOrNull()
            if (result != null && !result.text.isNullOrBlank()) {
                isLocked = true
                onQrPayloadDetected(result.text)
            }
        } catch (_: Exception) {
        } finally {
            reader.reset()
            image.close()
        }
    }
}