package com.example.snipshot

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

class SnipActivity : Activity() {

    private val REQUEST_MEDIA_PROJECTION = 1001
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureService: ScreenCaptureService? = null
    private var isBound = false

    private var resultCode: Int = RESULT_CANCELED
    private var projectionData: Intent? = null

    private val serviceConnection = object : ServiceConnection {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("SnipActivity", "Service connected")
            captureService = (service as ScreenCaptureService.LocalBinder).getService()

            // Now safely obtain the MediaProjection and proceed
            projectionData?.let { data ->
                mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                captureService?.setMediaProjection(mediaProjection!!)
                startSnipping()
            } ?: run {
                Log.e("SnipActivity", "Projection data missing")
                Toast.makeText(this@SnipActivity, "Projection failed", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("SnipActivity", "Service disconnected")
            isBound = false
            captureService = null
        }
    }

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
                Log.d("SnipActivity", "User clicked ALLOW on screen capture permission")

                // Save the projection data and result code for later use
                this.resultCode = resultCode
                this.projectionData = data

                // Start the foreground service first
                val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                isBound = bindService(
                    serviceIntent,
                    serviceConnection,
                    BIND_AUTO_CREATE
                )

            } else {
                Log.d("SnipActivity", "User clicked DENY on screen capture permission")
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("SnipActivity", "MediaProjection stopped")
            cleanupResources()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startSnipping() {
        Log.d("SnipActivity", "Starting snipping process")
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WindowManager::class.java)

        windowManager.currentWindowMetrics.bounds.let { bounds ->
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
        }
        windowManager.defaultDisplay?.getMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        // Register callback before creating virtual display
        mediaProjection?.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))

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

        // Delay to ensure the VirtualDisplay has captured an image
        Handler(Looper.getMainLooper()).postDelayed({
            captureScreenshotAndStartOverlay()
        }, 500) // Delay of 500ms to allow the VirtualDisplay to render
    }

    private fun cleanupResources() {
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        virtualDisplay?.release()
        imageReader?.close()
    }

    private fun captureScreenshotAndStartOverlay() {
        imageReader?.acquireLatestImage()?.use { image ->
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height)
            bitmap.copyPixelsFromBuffer(buffer)

            startSnipOverlay(bitmap)
        } ?: run {
            Log.e("SnipActivity", "Failed to acquire image")
            Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startSnipOverlay(bitmap: Bitmap) {
        // Save bitmap to temporary file
        val file = File(cacheDir, "temp_screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val intent = Intent(this, SnipOverlayActivity::class.java).apply {
            putExtra("screenshot_path", file.absolutePath)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        virtualDisplay?.release()
        imageReader?.close()
        Log.d("SnipActivity", "Resources cleaned up")
    }
}