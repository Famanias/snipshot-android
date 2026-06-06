package com.example.snipshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.snipshot.utils.ImageQualityChecker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageQualityCheckerTest {

    @Test
    fun testRejectsMinWidth() {
        val bitmap = Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888)
        val result = ImageQualityChecker.analyze(bitmap)
        assertTrue(result.reject)
        assertEquals("Selection is too small. Please select a larger area.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testRejectsMinHeight() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val result = ImageQualityChecker.analyze(bitmap)
        assertTrue(result.reject)
        assertEquals("Selection is too small. Please select a larger area.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testRejectsMinArea() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val result = ImageQualityChecker.analyze(bitmap)
        assertTrue(result.reject)
        assertEquals("Selection is too small. Please select a larger area.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testAcceptsNarrowButTallArea() {
        val bitmap = Bitmap.createBitmap(60, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
        }
        canvas.drawColor(Color.WHITE)
        for (y in 0 until 300 step 10) {
            canvas.drawRect(0f, y.toFloat(), 60f, (y + 5).toFloat(), paint)
        }

        val result = ImageQualityChecker.analyze(bitmap)
        assertFalse(result.reject)
        assertFalse(result.warn)
        assertNull(result.reason)
        bitmap.recycle()
    }

    @Test
    fun testWarnsOnSolidWhite() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val result = ImageQualityChecker.analyze(bitmap)
        assertFalse(result.reject)
        assertTrue(result.warn)
        assertEquals("Selection appears to be completely blank.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testWarnsOnSolidBlack() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val result = ImageQualityChecker.analyze(bitmap)
        assertFalse(result.reject)
        assertTrue(result.warn)
        assertEquals("Selection appears to be completely blank.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testPrecedenceRuleBlankBeforeLowDetail() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.GRAY)

        val result = ImageQualityChecker.analyze(bitmap)
        assertFalse(result.reject)
        assertTrue(result.warn)
        assertEquals("Selection appears to be completely blank.", result.reason)
        bitmap.recycle()
    }

    @Test
    fun testAcceptsNormalHighContrastContent() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        canvas.drawText("Hello World", 20f, 100f, paint)

        val result = ImageQualityChecker.analyze(bitmap)
        assertFalse(result.reject)
        assertFalse(result.warn)
        assertNull(result.reason)
        bitmap.recycle()
    }
}
