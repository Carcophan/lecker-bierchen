package com.picscan.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageUtils {
    /**
     * Maximum dimension (width or height) in pixels recommended for Gemini Multimodal Vision.
     * 1024px preserves high-detail text (labels, ABV%, ingredients, barcodes) while
     * keeping token usage optimal and reducing upload bandwidth by >90% compared to raw camera captures.
     */
    const val MAX_IMAGE_DIMENSION = 1024

    /**
     * Default maximum dimension for images stored in Firestore documents to ensure low document
     * size (typically 40-70 KB compressed JPEG) while maintaining crisp UI presentation.
     */
    const val DEFAULT_FIRESTORE_IMAGE_DIMENSION = 800

    /**
     * Scales down a [Bitmap] so its largest dimension is at most [maxDimension],
     * strictly preserving the original aspect ratio.
     *
     * If the bitmap is already within the bounds, the original [bitmap] instance is returned.
     */
    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int = MAX_IMAGE_DIMENSION): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)

        if (maxSide <= maxDimension || maxSide <= 0) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxSide
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Compresses and encodes a [Bitmap] to a Base64 JPEG string suitable for Firestore document storage.
     */
    fun bitmapToBase64(
        bitmap: Bitmap,
        maxDimension: Int = DEFAULT_FIRESTORE_IMAGE_DIMENSION,
        quality: Int = 75
    ): String {
        val scaled = scaleBitmapDown(bitmap, maxDimension)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Reads an image from [file], downscales it to [maxDimension], and encodes it to Base64 JPEG.
     */
    fun fileToBase64(
        file: File,
        maxDimension: Int = DEFAULT_FIRESTORE_IMAGE_DIMENSION,
        quality: Int = 75
    ): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            bitmapToBase64(bitmap, maxDimension, quality)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a Base64 string to a raw [ByteArray].
     */
    fun base64ToByteArray(base64: String): ByteArray? {
        if (base64.isBlank()) return null
        return try {
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            Base64.decode(cleanBase64, Base64.DEFAULT)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a Base64 string into a [Bitmap].
     */
    fun base64ToBitmap(base64: String): Bitmap? {
        val bytes = base64ToByteArray(base64) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a Base64 string and writes it to [targetFile].
     * Creates parent directories if needed.
     */
    fun saveBase64ToFile(base64: String, targetFile: File): Boolean {
        val bytes = base64ToByteArray(base64) ?: return false
        return try {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}

