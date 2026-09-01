package com.picscan.app.util

import android.graphics.Bitmap
import kotlin.math.max

object ImageUtils {
    /**
     * Maximum dimension (width or height) in pixels recommended for Gemini Multimodal Vision.
     * 1024px preserves high-detail text (labels, ABV%, ingredients, barcodes) while
     * keeping token usage optimal and reducing upload bandwidth by >90% compared to raw camera captures.
     */
    const val MAX_IMAGE_DIMENSION = 1024

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
}
