package com.example.snipshot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import com.example.snipshot.R
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import com.example.snipshot.utils.StorageManager
import com.example.snipshot.api.ApiClient
import com.example.snipshot.SnipShotApp
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SnipOverlayActivity : Activity() {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDrawing = false
    private lateinit var imageView: ImageView
    private lateinit var drawingView: DrawingView
    private lateinit var progressBar: ProgressBar
    private var screenshotBitmap: Bitmap? = null
    private var screenshotPath: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Connection timeout
        .readTimeout(60, TimeUnit.SECONDS)   // Read timeout
        .writeTimeout(60, TimeUnit.SECONDS)  // Write timeout
        .build()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

        setContentView(R.layout.activity_snip_overlay)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        imageView = findViewById(R.id.snip_preview)
        drawingView = findViewById(R.id.drawing_view)
        progressBar = findViewById(R.id.ocr_progress)

        // Get the screenshot path from the Intent
        screenshotPath = intent.getStringExtra("screenshot_path")
        if (screenshotPath.isNullOrEmpty()) {
            Log.e("SnipOverlayActivity", "Failed to receive screenshot path")
            Toast.makeText(this, "Failed to load screenshot", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load the bitmap from file
        try {
            screenshotBitmap = BitmapFactory.decodeFile(screenshotPath)
            if (screenshotBitmap == null) {
                throw Exception("Bitmap decoding failed")
            }
        } catch (e: Exception) {
            Log.e("SnipOverlayActivity", "Error loading screenshot: ${e.message}")
            Toast.makeText(this, "Failed to load screenshot", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Display the screenshot in the ImageView
        imageView.setImageBitmap(screenshotBitmap)
        imageView.visibility = View.VISIBLE

        // Set up drawing view for selection rectangle
        drawingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isDrawing = true
                    drawingView.setDrawingCoordinates(startX, startY, endX, endY, isDrawing)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDrawing) {
                        endX = event.x
                        endY = event.y
                        drawingView.setDrawingCoordinates(startX, startY, endX, endY, isDrawing)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDrawing) {
                        endX = event.x
                        endY = event.y
                        isDrawing = false
                        drawingView.setDrawingCoordinates(startX, startY, endX, endY, isDrawing)
                        captureSnip()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun captureSnip() {
        screenshotBitmap?.let { bitmap ->
            // Normalize coordinates for cropping
            val left = minOf(startX, endX).toInt()
            val top = minOf(startY, endY).toInt()
            val width = (maxOf(startX, endX) - minOf(startX, endX)).toInt()
            val height = (maxOf(startY, endY) - minOf(startY, endY)).toInt()

            if (width <= 0 || height <= 0) {
                Toast.makeText(this, "Please select a valid area", Toast.LENGTH_SHORT).show()
                return
            }

            // Single active translation check
            if (TranslationService.isRunning) {
                Toast.makeText(this, "A translation is already in progress. Please wait for it to complete.", Toast.LENGTH_LONG).show()
                return
            }

            // Crop the bitmap to the selected region
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                left.coerceAtLeast(0),
                top.coerceAtLeast(0),
                width.coerceAtMost(bitmap.width - left),
                height.coerceAtMost(bitmap.height - top)
            )

            // Save the cropped bitmap locally for history
            saveBitmap(croppedBitmap)
            
            // Save to temp file for service transfer
            val tempFile = try {
                val dir = cacheDir
                val file = File(dir, "temp_snip_${System.currentTimeMillis()}.png")
                java.io.FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file
            } catch (e: Exception) {
                Log.e("SnipOverlayActivity", "Failed to save temp snip", e)
                Toast.makeText(this, "Failed to prepare image for translation", Toast.LENGTH_SHORT).show()
                return
            }

            val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)
            val savedMode = prefs.getString("translation_mode", TranslationMode.MODE_2_SIMPLE_OCR.name) ?: TranslationMode.MODE_2_SIMPLE_OCR.name
            val targetLanguage = prefs.getString("target_language", "en") ?: "en"

            val serviceIntent = Intent(this, TranslationService::class.java).apply {
                putExtra(TranslationService.EXTRA_TEMP_IMAGE_PATH, tempFile.absolutePath)
                putExtra(TranslationService.EXTRA_TRANSLATION_MODE, savedMode)
                putExtra(TranslationService.EXTRA_TARGET_LANGUAGE, targetLanguage)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            finish()
        } ?: run {
            Log.e("SnipOverlayActivity", "Screenshot Bitmap is null")
            Toast.makeText(this, "Failed to capture snip", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "SnipShot_$timeStamp.jpg"
        val contentValues = ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnipShot")
        }

        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Toast.makeText(this, "Snip saved to Pictures/SnipShot", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e("SnipOverlayActivity", "Failed to create media store entry")
            Toast.makeText(this, "Failed to save snip", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotBitmap?.recycle()
        screenshotPath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                Log.e("SnipOverlayActivity", "Error deleting temp file: ${e.message}")
            }
        }
    }
}