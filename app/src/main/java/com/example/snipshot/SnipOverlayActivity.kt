package com.example.snipshot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnipOverlayActivity : Activity() {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDrawing = false
    private lateinit var imageView: ImageView
    private lateinit var drawingView: DrawingView
    private var screenshotBitmap: Bitmap? = null
    private var screenshotPath: String? = null
    private val backendUrl = "https://snipshot-backend.onrender.com"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

        setContentView(R.layout.activity_snip_overlay)

        imageView = findViewById(R.id.snip_preview)
        drawingView = findViewById(R.id.drawing_view)

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

            // Crop the bitmap to the selected region
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                left.coerceAtLeast(0),
                top.coerceAtLeast(0),
                width.coerceAtMost(bitmap.width - left),
                height.coerceAtMost(bitmap.height - top)
            )

            // Save the cropped bitmap locally
            saveBitmap(croppedBitmap)

            // Send cropped image to backend for OCR
            sendOcrRequest(croppedBitmap)
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

    private fun sendOcrRequest(bitmap: Bitmap) {
        // Convert bitmap to base64
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

        // Create JSON request body
        val requestBody = JSONObject().apply {
            put("image_base64", base64Image)
        }

        // Create Volley request
        val queue = Volley.newRequestQueue(this)
        val ocrRequest = JsonObjectRequest(
            Request.Method.POST, "$backendUrl/ocr", requestBody,
            { response ->
                if (response.has("error")) {
                    Toast.makeText(this, "OCR failed: ${response.getString("error")}", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val extractedText = response.getString("text")
                    val language = response.getString("language")
                    // Display the extracted text (e.g., in a Toast or dialog)
                    Toast.makeText(this, "Extracted text ($language): $extractedText", Toast.LENGTH_LONG).show()
                    // Optionally, send translation request
                    // sendTranslationRequest(extractedText, "en") // Uncomment to translate to English
                    finish()
                }
            },
            { error ->
                Log.e("SnipOverlayActivity", "OCR request failed: ${error.message}")
                Toast.makeText(this, "Failed to connect to OCR service", Toast.LENGTH_LONG).show()
                finish()
            }
        )

        queue.add(ocrRequest)
    }

    private fun sendTranslationRequest(text: String, targetLang: String) {
        // Create JSON request body
        val requestBody = JSONObject().apply {
            put("text", text)
            put("target_lang", targetLang)
        }

        // Create Volley request
        val queue = Volley.newRequestQueue(this)
        val translateRequest = JsonObjectRequest(
            Request.Method.POST, "$backendUrl/translate", requestBody,
            { response ->
                if (response.has("error")) {
                    Toast.makeText(this, "Translation failed: ${response.getString("error")}", Toast.LENGTH_LONG).show()
                } else {
                    val translatedText = response.getString("translated_text")
                    Toast.makeText(this, "Translated text ($targetLang): $translatedText", Toast.LENGTH_LONG).show()
                }
                finish()
            },
            { error ->
                Log.e("SnipOverlayActivity", "Translation request failed: ${error.message}")
                Toast.makeText(this, "Failed to connect to translation service", Toast.LENGTH_LONG).show()
                finish()
            }
        )

        queue.add(translateRequest)
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