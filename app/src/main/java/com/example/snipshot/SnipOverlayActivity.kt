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

            // Crop the bitmap to the selected region
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                left.coerceAtLeast(0),
                top.coerceAtLeast(0),
                width.coerceAtMost(bitmap.width - left),
                height.coerceAtMost(bitmap.height - top)
            )

            // Save the bitmap and perform OCR
            saveBitmap(croppedBitmap)
            performOcrAndTranslate(croppedBitmap)
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

    private fun performOcrAndTranslate(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            try {
                // Convert bitmap to base64
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                Log.d("SnipOverlayActivity", "Sending OCR request with image size: ${imageBytes.size} bytes")

                // Get backend URL from BuildConfig
                val backendUrl = BuildConfig.BACKEND_URL

                // Perform OCR
                val ocrRequestBody = JSONObject().put("image_base64", base64Image).toString()
                    .toRequestBody("application/json".toMediaType())
                val ocrRequest = Request.Builder()
                    .url("$backendUrl/ocr")
                    .post(ocrRequestBody)
                    .build()
                val ocrResponse = withContext(Dispatchers.IO) { client.newCall(ocrRequest).execute() }
                if (!ocrResponse.isSuccessful) {
                    throw Exception("OCR failed: ${ocrResponse.message}")
                }
                val ocrData = JSONObject(ocrResponse.body?.string() ?: "{}")
                if (ocrData.has("error")) {
                    throw Exception(ocrData.getString("error"))
                }
                val items = ocrData.getJSONArray("items")
                val detectedLanguage = ocrData.getString("language")

                // Get target language from SharedPreferences
                val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)
                val targetLanguage = prefs.getString("target_language", "en") ?: "en"
                Log.d("SnipOverlayActivity", "Translating to target language: $targetLanguage")

                // Translate each item individually
                val extractedTexts = mutableListOf<String>()
                val boxes = mutableListOf<List<Int>>()
                val translations = mutableListOf<String>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val text = item.getString("text")
                    extractedTexts.add(text)
                    val bboxArr = item.getJSONArray("bbox")
                    val bbox = listOf(bboxArr.getInt(0), bboxArr.getInt(1), bboxArr.getInt(2), bboxArr.getInt(3))
                    boxes.add(bbox)

                    // Perform translation for this item
                    val translateRequestBody = JSONObject()
                        .put("text", text)
                        .put("target_lang", targetLanguage)
                        .toString()
                        .toRequestBody("application/json".toMediaType())
                    val translateRequest = Request.Builder()
                        .url("$backendUrl/translate")
                        .post(translateRequestBody)
                        .build()
                    val translateResponse = withContext(Dispatchers.IO) { client.newCall(translateRequest).execute() }
                    if (!translateResponse.isSuccessful) {
                        throw Exception("Translation failed for item $i: ${translateResponse.message}")
                    }
                    val translateData = JSONObject(translateResponse.body?.string() ?: "{}")
                    val translated = if (translateData.has("error")) {
                        "Translation error SnipOverlay: ${translateData.getString("error")}"
                    } else {
                        translateData.getString("translated_text")
                    }
                    translations.add(translated)
                }

                val extractedText = extractedTexts.joinToString("\n")
                val translatedText = translations.joinToString("\n")

                // Launch OverlayActivity
                val intent = Intent(this@SnipOverlayActivity, OverlayActivity::class.java).apply {
                    putExtra("image_bytes", imageBytes)
                    val overlaysArray = JSONArray()
                    for (j in 0 until boxes.size) {
                        val obj = JSONObject()
                        obj.put("bbox", JSONArray(boxes[j]))
                        obj.put("translated", translations[j])
                        overlaysArray.put(obj)
                    }
                    putExtra("overlays_json", overlaysArray.toString())
                    putExtra("extracted_text", extractedText)
                    putExtra("detected_language", detectedLanguage)
                    putExtra("translated_text", translatedText)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("SnipOverlayActivity", "Error in OCR/Translation: ${e.message}", e)
                Toast.makeText(this@SnipOverlayActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
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