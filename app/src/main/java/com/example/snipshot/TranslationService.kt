package com.example.snipshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.snipshot.api.ApiClient
import com.example.snipshot.utils.StorageManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.snipshot.utils.TranslationTask
import com.example.snipshot.utils.TranslationQueueManager

class TranslationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private var sessionSuccess = 0
    private var sessionFail = 0
    private val sessionTaskIds = mutableSetOf<String>()
    private var processingJob: Job? = null

    companion object {
        const val EXTRA_TEMP_IMAGE_PATHS = "temp_image_paths"
        const val EXTRA_TRANSLATION_MODE = "translation_mode"
        const val EXTRA_TARGET_LANGUAGE = "target_language"

        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_COMPLETE_ID = 1002
        private const val NOTIFICATION_FAILED_ID = 1003
        private const val CHANNEL_PROGRESS_ID = "TranslationProgressChannel"
        private const val CHANNEL_COMPLETE_ID = "TranslationCompleteChannel"

        @Volatile
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TranslationService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        startForegroundServiceWithNotification()

        synchronized(this) {
            if (processingJob == null || processingJob?.isActive == false) {
                processingJob = serviceScope.launch {
                    processQueue()
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        while (true) {
            val task = TranslationQueueManager.getNextQueuedTask(this) ?: break
            sessionTaskIds.add(task.id)

            val activeTasks = TranslationQueueManager.tasks.value
            val activeCount = activeTasks.count {
                it.status == TranslationTask.Status.QUEUED ||
                it.status == TranslationTask.Status.PREPARING ||
                it.status == TranslationTask.Status.TRANSLATING ||
                it.status == TranslationTask.Status.UPLOADING
            }
            updateProgressNotification(activeCount)

            try {
                processTranslation(task)
                sessionSuccess++
            } catch (e: Exception) {
                Log.e("TranslationService", "Failed to translate task ${task.id}", e)
                sessionFail++
                TranslationQueueManager.updateTaskStatus(this, task.id, TranslationTask.Status.FAILED, e.message ?: "Unknown error")
            }
        }

        postSessionCompletionNotification()

        sessionSuccess = 0
        sessionFail = 0
        sessionTaskIds.clear()
        isRunning = false
        stopForeground(true)
        stopSelf()
    }

    private fun updateProgressNotification(activeCount: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setContentTitle("Translating Images")
            .setContentText("Processing translations ($activeCount remaining)...")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun postSessionCompletionNotification() {
        val total = sessionTaskIds.size
        if (total <= 1) return

        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("select_tab", R.id.nav_recent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (sessionFail == 0) {
            NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
                .setContentTitle("Translation Complete")
                .setContentText("Successfully translated $sessionSuccess of $total images.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
                .setContentTitle("Translation Batch Completed")
                .setContentText("Completed $sessionSuccess/$total. $sessionFail failed.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_COMPLETE_ID, notification)
    }

    private fun startForegroundServiceWithNotification() {
        createNotificationChannels()

        val notification = NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setContentTitle("Translation")
            .setContentText("Translation in progress...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // 1. Progress Channel (Low importance: silent, no heads-up)
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS_ID,
            "Translation Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(progressChannel)

        // 2. Completion/Failure Channel (Default importance: sound, alert, visible in status bar)
        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE_ID,
            "Translation Completion",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(completeChannel)
    }

    private suspend fun processTranslation(task: TranslationTask) {
        if (task.mode == TranslationMode.MODE_1_MANGA.name) {
            performMode1Manga(task)
        } else {
            performMode2OCR(task)
        }
    }

    private suspend fun performMode1Manga(task: TranslationTask) {
        val backendUrl = BuildConfig.TRANSLATOR_URL.trimEnd('/')
        val prefs = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)

        val targetLang3 = when (task.targetLanguage) {
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
        TranslationQueueManager.updateTaskStatus(this, task.id, TranslationTask.Status.TRANSLATING)
        
        val bitmap = BitmapFactory.decodeFile(task.tempImagePath) ?: throw Exception("Failed to load snip image")

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

        fun buildRequest(): Request {
            val builder = Request.Builder()
                .url("$backendUrl/translate/raw")
                .post(buildRequestBody())
            ApiClient.accessToken?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
                Log.d("TranslationPipeline", "Authorization Bearer token attached")
            }
            return builder.build()
        }

        Log.d("TranslationPipeline", "Sending OkHttp request to Manga Translator...")
        var response = withContext(Dispatchers.IO) { client.newCall(buildRequest()).execute() }
        Log.d("TranslationPipeline", "Response code: ${response.code}, message: ${response.message}")

        if (response.code == 401) {
            response.close()
            Log.d("TranslationPipeline", "Manga Mode request failed with 401. Refreshing auth session...")
            val refreshResult = ApiClient.refreshSession()
            val newAccessToken = refreshResult.getOrNull()?.optString("access_token")
            if (!newAccessToken.isNullOrEmpty()) {
                ApiClient.accessToken = newAccessToken
                Log.d("TranslationPipeline", "Token refreshed successfully. Retrying request...")
                response = withContext(Dispatchers.IO) { client.newCall(buildRequest()).execute() }
                Log.d("TranslationPipeline", "Retry response code: ${response.code}, message: ${response.message}")
            } else {
                Log.e("TranslationPipeline", "Token refresh failed or returned empty.")
                throw Exception("Session expired. Please log in again.")
            }

            if (!response.isSuccessful) {
                Log.e("TranslationPipeline", "Retry failed with code ${response.code}.")
                throw Exception("Session expired. Please log in again.")
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
        val localFile = StorageManager.saveLocally(this, decodedBitmap, uniquePrefix)
            ?: throw Exception("Failed to save translated image locally")
        Log.d("TranslationPipeline", "Translated image successfully saved locally to ${localFile.absolutePath}")

        var uploadedImageId: Int? = null
        var uploadedUrl: String? = null
        var uploadedStoragePath: String? = null

        if (ApiClient.isLoggedIn()) {
            TranslationQueueManager.updateTaskStatus(this, task.id, TranslationTask.Status.UPLOADING)
            val fileBytes = responseBytes
            val filename = "PREVIEW_" + localFile.name
            Log.d("TranslationPipeline", "User is logged in. Uploading translated preview file to cloud: $filename")
            val uploadResult = ApiClient.uploadImage(
                imageBytes = fileBytes,
                filename = filename,
                sourceLang = null,
                targetLang = task.targetLanguage
            )
            if (uploadResult.isSuccess) {
                Log.d("TranslationPipeline", "Background cloud upload of preview succeeded")
                val uploadedObj = uploadResult.getOrNull()
                uploadedImageId = uploadedObj?.optInt("id", -1) ?: -1
                uploadedUrl = uploadedObj?.optString("public_url", "")
                uploadedStoragePath = uploadedObj?.optString("storage_path", "")

                if (!localFile.exists()) {
                    val id = uploadedImageId ?: -1
                    if (id != -1) {
                        Log.d("TranslationPipeline", "Local file was deleted during upload. Cleaning up cloud upload ID: $id")
                        ApiClient.deleteImage(id)
                    }
                }
            } else {
                val err = uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("TranslationPipeline", "Background cloud upload of preview failed: $err")
                throw Exception(err)
            }
        }

        TranslationQueueManager.markTaskCompleted(
            this,
            task.id,
            uploadedImageId,
            uploadedUrl,
            uploadedStoragePath
        )

        if (sessionTaskIds.size <= 1) {
            postMangaCompletionNotification(localFile)
        }
    }

    private suspend fun performMode2OCR(task: TranslationTask) {
        if (!ApiClient.isLoggedIn()) {
            throw Exception("Please log in to use OCR translation.")
        }
        val initialToken = ApiClient.accessToken ?: throw Exception("Please log in to use OCR translation.")

        TranslationQueueManager.updateTaskStatus(this, task.id, TranslationTask.Status.TRANSLATING)

        val bitmap = BitmapFactory.decodeFile(task.tempImagePath) ?: throw Exception("Failed to load snip image")
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()

        val backendUrl = BuildConfig.SIMPLE_TRANSLATOR_URL.trimEnd('/')

        Log.d("TranslationPipeline", "Simple OCR Mode (Mode 2) started")
        Log.d("TranslationPipeline", "Target Language: ${task.targetLanguage}")
        Log.d("TranslationPipeline", "Url: $backendUrl/translate-image")

        fun buildRequest(token: String): Request {
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "image", 
                    "snip.png", 
                    imageBytes.toRequestBody("image/png".toMediaType())
                )
                .addFormDataPart("target_lang", task.targetLanguage)
                .build()

            return Request.Builder()
                .url("$backendUrl/translate-image")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        Log.d("TranslationPipeline", "Executing simple translation request...")
        var response = withContext(Dispatchers.IO) { client.newCall(buildRequest(initialToken)).execute() }
        Log.d("TranslationPipeline", "Response code: ${response.code}, message: ${response.message}")

        if (response.code == 401) {
            response.close()
            Log.d("TranslationPipeline", "Simple OCR request failed with 401. Refreshing auth session...")
            val refreshResult = ApiClient.refreshSession()
            val newAccessToken = refreshResult.getOrNull()?.optString("access_token")
            if (!newAccessToken.isNullOrEmpty()) {
                ApiClient.accessToken = newAccessToken
                Log.d("TranslationPipeline", "Token refreshed successfully. Retrying request...")
                response = withContext(Dispatchers.IO) { client.newCall(buildRequest(newAccessToken)).execute() }
                Log.d("TranslationPipeline", "Retry response code: ${response.code}, message: ${response.message}")
            } else {
                Log.e("TranslationPipeline", "Token refresh failed or returned empty.")
                throw Exception("Session expired. Please log in again.")
            }

            if (!response.isSuccessful) {
                Log.e("TranslationPipeline", "Retry failed with code ${response.code}.")
                throw Exception("Session expired. Please log in again.")
            }
        }

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

        val detectedLanguage = responseJson.optString("detected_language", "unknown")
        val extractedText = responseJson.optString("extracted_text", "")
        val translatedText = responseJson.optString("translated_text", "")
        val summary = responseJson.optString("summary", "")

        TranslationQueueManager.markTaskCompleted(
            this,
            task.id,
            null,
            null,
            null
        )

        if (sessionTaskIds.size <= 1) {
            postOcrCompletionNotification(detectedLanguage, extractedText, translatedText, summary)
        }
    }

    private fun postMangaCompletionNotification(localFile: File) {
        val intent = Intent(this, ImageDetailActivity::class.java).apply {
            putExtra("is_local", true)
            putExtra("path_or_url", localFile.absolutePath)
            putExtra("filename", localFile.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setContentTitle("Manga Translation Complete")
            .setContentText("Tap to view the translated image")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_COMPLETE_ID, notification)
    }

    private fun postOcrCompletionNotification(
        detectedLanguage: String,
        extractedText: String,
        translatedText: String,
        summary: String
    ) {
        val intent = Intent(this, TranslateActivity::class.java).apply {
            putExtra("detected_language", detectedLanguage)
            putExtra("extracted_text", extractedText)
            putExtra("translated_text", translatedText)
            putExtra("summary", summary)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setContentTitle("OCR Translation Complete")
            .setContentText("Tap to view translation results")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_COMPLETE_ID, notification)
    }

    private fun postFailureNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setContentTitle("Translation Failed")
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_FAILED_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isRunning = false
        Log.d("TranslationService", "Service destroyed")
    }
}
