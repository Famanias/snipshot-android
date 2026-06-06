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

            saveBitmap(croppedBitmap)
            
            val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)
            val savedMode = prefs.getString("translation_mode", TranslationMode.MODE_2_SIMPLE_OCR.name)

            if (savedMode == TranslationMode.MODE_1_MANGA.name) {
                performMode1Manga(croppedBitmap)
            } else {
                performMode2OCR(croppedBitmap)
            }
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

    private fun performMode1Manga(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            val startTime = System.currentTimeMillis()
            try {
                val backendUrl = BuildConfig.TRANSLATOR_URL.trimEnd('/')
                val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)
                val targetLanguage = prefs.getString("target_language", "en") ?: "en"
                
                // Map language codes to 3-letter formats expected by manga translator if needed, or just send directly
                val targetLang3 = when(targetLanguage) {
                    "en" -> "ENG"
                    "ja" -> "JPN"
                    "ko" -> "KOR"
                    "zh_cn" -> "CHS"
                    "zh_tw" -> "CHT"
                    else -> "ENG"
                }

                val detectorSize = prefs.getInt("detector_size", 1536)
                val boxThreshold = prefs.getFloat("box_threshold", 0.7f).toDouble()
                val textThreshold = prefs.getFloat("text_threshold", 0.5f).toDouble()

                val configJson = JSONObject().apply {
                    put("translator", JSONObject().put("target_lang", targetLang3))
                    put("detector", JSONObject().apply {
                        put("detection_size", detectorSize)
                        put("box_threshold", boxThreshold)
                        put("text_threshold", textThreshold)
                    })
                }

                Log.d("TranslationPipeline", "Manga Mode (Mode 1) translation started")
                Log.d("TranslationPipeline", "Target Language: $targetLang3, config: $configJson")
                Log.d("TranslationPipeline", "Url: $backendUrl/translate/raw")

                // 1. Helper to build a fresh RequestBody on demand
                fun buildRequestBody(): okhttp3.RequestBody {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val imageBytes = stream.toByteArray()
                    Log.d("TranslationPipeline", "Request payload image size: ${imageBytes.size} bytes")
                    return okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("image", "snip.png", imageBytes.toRequestBody("image/png".toMediaType()))
                        .addFormDataPart("config", configJson.toString())
                        .build()
                }

                // 2. Helper to build a request with a fresh body and the current token
                fun buildRequest(): Request {
                    val builder = Request.Builder()
                        .url("$backendUrl/translate/raw")
                        .post(buildRequestBody())   // fresh body every time
                    ApiClient.accessToken?.let { token ->
                        builder.addHeader("Authorization", "Bearer $token")
                        Log.d("TranslationPipeline", "Authorization Bearer token attached")
                    }
                    return builder.build()
                }

                // 3. First attempt
                Log.d("TranslationPipeline", "Sending OkHttp request to Manga Translator...")
                var response = withContext(Dispatchers.IO) { client.newCall(buildRequest()).execute() }
                Log.d("TranslationPipeline", "Response code: ${response.code}, message: ${response.message}")

                // 4. On 401, refresh session and retry once
                if (response.code == 401) {
                    Log.d("TranslationPipeline", "Manga Mode request failed with 401. Refreshing auth session...")
                    val refreshResult = ApiClient.refreshSession()
                    val newAccessToken = refreshResult.getOrNull()?.optString("access_token")
                    if (!newAccessToken.isNullOrEmpty()) {
                        ApiClient.accessToken = newAccessToken
                        Log.d("TranslationPipeline", "Token refreshed successfully. Retrying request...")
                        response = withContext(Dispatchers.IO) {
                            client.newCall(buildRequest()).execute()
                        }
                        Log.d("TranslationPipeline", "Retry response code: ${response.code}, message: ${response.message}")
                    }

                    // 5. If retry also fails, redirect to login
                    if (!response.isSuccessful) {
                        Log.e("TranslationPipeline", "Retry failed with code ${response.code}. Redirecting to LoginActivity.")
                        navigateToLogin()
                        return@launch
                    }
                }
                
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("TranslationPipeline", "Manga Translation failed with HTTP ${response.code}: $errBody")
                    throw Exception("Manga Translation failed: ${response.message}")
                }

                val responseBytes = response.body?.bytes() ?: throw Exception("Empty response body")
                Log.d("TranslationPipeline", "Received translated image payload of size ${responseBytes.size} bytes")
                
                val decodedBitmap = BitmapFactory.decodeByteArray(responseBytes, 0, responseBytes.size)
                    ?: throw Exception("Failed to decode translated image bytes")

                val uniquePrefix = "Snip_${java.util.UUID.randomUUID().toString().take(8)}"
                val localFile = StorageManager.saveLocally(this@SnipOverlayActivity, decodedBitmap, uniquePrefix)
                    ?: throw Exception("Failed to save translated image locally")
                Log.d("TranslationPipeline", "Translated image successfully saved locally to ${localFile.absolutePath}")

                if (ApiClient.isLoggedIn()) {
                    val fileBytes = responseBytes
                    val filename = "PREVIEW_" + localFile.name
                    Log.d("TranslationPipeline", "User is logged in. Uploading translated preview file to cloud: $filename")
                    SnipShotApp.applicationScope.launch(Dispatchers.IO) {
                        val uploadResult = ApiClient.uploadImage(
                            imageBytes = fileBytes,
                            filename = filename,
                            sourceLang = null,
                            targetLang = targetLanguage
                        )
                        if (uploadResult.isSuccess) {
                            Log.d("TranslationPipeline", "Background cloud upload of preview succeeded")
                            // Check if local file was deleted by the user while the upload was in progress (TOCTOU gap)
                            if (!localFile.exists()) {
                                val uploadedObj = uploadResult.getOrNull()
                                val id = uploadedObj?.optInt("id", -1) ?: -1
                                if (id != -1) {
                                    Log.d("TranslationPipeline", "Local file was deleted during upload. Cleaning up cloud upload ID: $id")
                                    ApiClient.deleteImage(id)
                                }
                            }
                        } else {
                            val err = uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                            Log.e("TranslationPipeline", "Background cloud upload of preview failed: $err")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@SnipOverlayActivity,
                                    "Failed to sync with cloud: ${uploadResult.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                Log.d("TranslationPipeline", "Manga Mode translation completed successfully in ${duration}ms")

                val intent = Intent(this@SnipOverlayActivity, ImageDetailActivity::class.java).apply {
                    putExtra("is_local", true)
                    putExtra("path_or_url", localFile.absolutePath)
                    putExtra("filename", localFile.name)
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e("TranslationPipeline", "Error in Manga Translation: ${e.message}", e)
                Toast.makeText(this@SnipOverlayActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun performMode2OCR(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            val startTime = System.currentTimeMillis()
            try {
                // Convert bitmap to PNG bytes
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()

                // Get backend URL and settings
                val backendUrl = BuildConfig.SIMPLE_TRANSLATOR_URL.trimEnd('/')
                val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)
                val targetLanguage = prefs.getString("target_language", "en") ?: "en"

                Log.d("TranslationPipeline", "Simple OCR Mode (Mode 2) started")
                Log.d("TranslationPipeline", "Target Language: $targetLanguage")
                Log.d("TranslationPipeline", "Url: $backendUrl/translate-image")
                Log.d("TranslationPipeline", "Sending request with image payload size: ${imageBytes.size} bytes")

                // Create multipart request body
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart(
                        "image", 
                        "snip.png", 
                        imageBytes.toRequestBody("image/png".toMediaType())
                    )
                    .addFormDataPart("target_lang", targetLanguage)
                    .build()

                val request = Request.Builder()
                    .url("$backendUrl/translate-image")
                    .post(requestBody)
                    .build()

                Log.d("TranslationPipeline", "Executing simple translation request...")
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                Log.d("TranslationPipeline", "Response code: ${response.code}, message: ${response.message}")

                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("TranslationPipeline", "Simple Translation failed with HTTP ${response.code}: $errBody")
                    throw Exception("Translation request failed: ${response.message}")
                }

                val responseBodyStr = response.body?.string() ?: "{}"
                Log.d("TranslationPipeline", "Raw Response: $responseBodyStr")
                val responseJson = JSONObject(responseBodyStr)
                if (responseJson.has("error")) {
                    val errorMsg = responseJson.getString("error")
                    Log.e("TranslationPipeline", "Simple Translation returned application error: $errorMsg")
                    throw Exception(errorMsg)
                }

                // Extract fields returned by /translate-image
                val detectedLanguage = responseJson.optString("detected_language", "unknown")
                val extractedText = responseJson.optString("extracted_text", "")
                val translatedText = responseJson.optString("translated_text", "")
                val summary = responseJson.optString("summary", "")

                val duration = System.currentTimeMillis() - startTime
                Log.d("TranslationPipeline", "Simple OCR translation completed successfully in ${duration}ms")

                // Launch TranslateActivity directly
                val intent = Intent(this@SnipOverlayActivity, TranslateActivity::class.java).apply {
                    putExtra("detected_language", detectedLanguage)
                    putExtra("extracted_text", extractedText)
                    putExtra("translated_text", translatedText)
                    putExtra("summary", summary)
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e("TranslationPipeline", "Error in Simple Translation: ${e.message}", e)
                Toast.makeText(this@SnipOverlayActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
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