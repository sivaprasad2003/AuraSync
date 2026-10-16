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