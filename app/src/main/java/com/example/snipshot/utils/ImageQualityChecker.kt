package com.example.snipshot.utils

import android.graphics.Bitmap

data class QualityResult(
    val reject: Boolean,
    val warn: Boolean,
    val reason: String?
)

object ImageQualityChecker {
    private const val MIN_WIDTH = 60
    private const val MIN_HEIGHT = 60
    private const val MIN_AREA = 15000

    private const val BLANK_SAMPLE_GRID = 16        // 16x16 = 256 samples
    private const val BLANK_LUMINANCE_RANGE = 8.0
    private const val LOW_DETAIL_THRESHOLD = 1.2

    fun analyze(bitmap: Bitmap): QualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val area = width * height

        // 1. Size Validation (Reject)
        if (width < MIN_WIDTH || height < MIN_HEIGHT || area < MIN_AREA) {
            return QualityResult(
                reject = true,
                warn = false,
                reason = "Selection is too small. Please select a larger area."
            )
        }

        // 2. Blank Image Detection (Warn)
        var minLuminance = 255.0
        var maxLuminance = 0.0

        val stepX = (width - 1).coerceAtLeast(1).toDouble() / (BLANK_SAMPLE_GRID - 1).coerceAtLeast(1)
        val stepY = (height - 1).coerceAtLeast(1).toDouble() / (BLANK_SAMPLE_GRID - 1).coerceAtLeast(1)

        for (i in 0 until BLANK_SAMPLE_GRID) {
            for (j in 0 until BLANK_SAMPLE_GRID) {
                val x = (i * stepX).toInt().coerceAtMost(width - 1)
                val y = (j * stepY).toInt().coerceAtMost(height - 1)
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b

                if (luminance < minLuminance) minLuminance = luminance
                if (luminance > maxLuminance) maxLuminance = luminance
            }
        }

        if (maxLuminance - minLuminance < BLANK_LUMINANCE_RANGE) {
            return QualityResult(
                reject = false,
                warn = true,
                reason = "Selection appears to be completely blank."
            )
        }

        // 3. Low-Detail Detection (Warn)
        val targetSize = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        
        var totalDiff = 0.0
        val count = (targetSize - 1) * (targetSize - 1)
        val pixels = IntArray(targetSize * targetSize)
        scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        
        for (y in 0 until targetSize - 1) {
            for (x in 0 until targetSize - 1) {
                val idx = y * targetSize + x
                val c = pixels[idx]
                val r = pixels[idx + 1]
                val b = pixels[idx + targetSize]
                
                val valC = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF))
                val valR = (0.299 * ((r shr 16) and 0xFF) + 0.587 * ((r shr 8) and 0xFF) + 0.114 * (r and 0xFF))
                val valB = (0.299 * ((b shr 16) and 0xFF) + 0.587 * ((b shr 8) and 0xFF) + 0.114 * (b and 0xFF))
                
                val diffX = Math.abs(valC - valR)
                val diffY = Math.abs(valC - valB)
                
                totalDiff += (diffX + diffY)
            }
        }
        
        val avgDiff = totalDiff / (count * 2)
        scaled.recycle()

        if (avgDiff < LOW_DETAIL_THRESHOLD) {
            return QualityResult(
                reject = false,
                warn = true,
                reason = "Selection contains very little visible detail or text. Translation quality may be poor."
            )
        }

        // 4. Accept
        return QualityResult(
            reject = false,
            warn = false,
            reason = null
        )
    }
}
