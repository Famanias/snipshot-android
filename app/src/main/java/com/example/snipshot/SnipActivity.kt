package com.example.snipshot

import android.app.Activity
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import android.util.Log
import androidx.annotation.RequiresApi

class SnipActivity : Activity() {

    private val REQUEST_MEDIA_PROJECTION = 1001
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SnipActivity", "Launching screen capture permission dialog")

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Start foreground service FIRST
                val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                startForegroundService(serviceIntent)

                // Then get media projection
                mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                startSnipping()
            } else {
                Log.d("SnipActivity", "User clicked DENY on screen capture permission")
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startSnipping() {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WindowManager::class.java)

        // Modern way to get display metrics
        windowManager.currentWindowMetrics.bounds.let { bounds ->
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
        }

        // For densityDpi, we still need to use the older method
        windowManager.defaultDisplay?.getMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SnipShotDisplay",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader?.surface,
            null,
            null
        )

        Log.d("SnipActivity", "Screen capture started (VirtualDisplay created)")
        Toast.makeText(this, "Snipping started", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SnipActivity", "Cleaning up resources (VirtualDisplay, ImageReader)")
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}