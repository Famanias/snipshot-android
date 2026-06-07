package com.example.snipshot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.snipshot.model.UploadEvent
import coil.load
import com.example.snipshot.api.ApiClient
import com.example.snipshot.ui.FolderPickerBottomSheet
import com.example.snipshot.utils.TranslationQueueManager
import com.example.snipshot.utils.TranslationTask
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.launch
import java.io.File

class ImageDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        val bottomBar = findViewById<View>(R.id.bottom_bar)
        val baseBottomPadding = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseBottomPadding + navBarInsets.bottom
            )
            insets
        }

        val photoView = findViewById<PhotoView>(R.id.photo_view)
        val tvFilename = findViewById<TextView>(R.id.tv_filename)
        val btnDelete = findViewById<ImageButton>(R.id.btn_delete)
        val btnOpenBrowser = findViewById<ImageButton>(R.id.btn_open_browser)
        val btnSaveToAccount = findViewById<ImageButton>(R.id.btn_save_to_account)

        var isLocal = intent.getBooleanExtra("is_local", false)
        var imageId = intent.getIntExtra("image_id", -1)
        val filename = intent.getStringExtra("filename") ?: "Unknown"
        var pathOrUrl = intent.getStringExtra("path_or_url")
        var storagePath = intent.getStringExtra("storage_path")
        val localFilePath: String? = if (isLocal) pathOrUrl else null

        var previewImageId: Int = -1
        var previewPublicUrl: String? = null
        var previewStoragePath: String? = null

        tvFilename.text = filename

        val isPreview = !isLocal && filename.startsWith("PREVIEW_")
        val isQueue = intent.getBooleanExtra("is_queue", false)
        val taskId = intent.getStringExtra("task_id")

        if (isQueue) {
            btnSaveToAccount.visibility = View.GONE
            btnOpenBrowser.visibility = View.GONE
        } else {
            btnSaveToAccount.visibility = if (isLocal || isPreview) View.VISIBLE else View.GONE
        }

        if (isQueue) {
            val file = File(pathOrUrl ?: "")
            if (file.exists()) {
                photoView.load(file)
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (isLocal) {
            btnOpenBrowser.visibility = View.GONE
            val file = File(pathOrUrl ?: "")
            if (file.exists()) {
                photoView.load(file) {
                    listener(
                        onStart = { request -> android.util.Log.d("CoilImageDetail", "Start loading local file: ${request.data}") },
                        onSuccess = { _, _ -> android.util.Log.d("CoilImageDetail", "Successfully loaded local file") },
                        onError = { _, result -> android.util.Log.e("CoilImageDetail", "Error loading local file: ${result.throwable.message}", result.throwable) }
                    )
                }
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val sPath = storagePath
            if (!sPath.isNullOrEmpty()) {
                lifecycleScope.launch {
                    val signedUrl = ApiClient.getSignedUrl(sPath)
                    android.util.Log.d("CoilImageDetail", "getSignedUrl result: $signedUrl")
                    if (signedUrl != null) {
                        photoView.load(signedUrl) {
                            listener(
                                onStart = { request -> android.util.Log.d("CoilImageDetail", "Start loading signed URL: ${request.data}") },
                                onSuccess = { _, _ -> android.util.Log.d("CoilImageDetail", "Successfully loaded signed URL") },
                                onError = { _, result -> android.util.Log.e("CoilImageDetail", "Error loading signed URL: ${result.throwable.message}", result.throwable) }
                            )
                        }
                        btnOpenBrowser.setOnClickListener {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl))
                            startActivity(browserIntent)
                        }
                    } else {
                        photoView.load(pathOrUrl) {
                            listener(
                                onStart = { request -> android.util.Log.d("CoilImageDetail", "Start loading fallback pathOrUrl: ${request.data}") },
                                onSuccess = { _, _ -> android.util.Log.d("CoilImageDetail", "Successfully loaded fallback pathOrUrl") },
                                onError = { _, result -> android.util.Log.e("CoilImageDetail", "Error loading fallback pathOrUrl: ${result.throwable.message}", result.throwable) }
                            )
                        }
                        btnOpenBrowser.setOnClickListener {
                            pathOrUrl?.let {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                startActivity(browserIntent)
                            }
                        }
                    }
                }
            } else {
                photoView.load(pathOrUrl) {
                    listener(
                        onStart = { request -> android.util.Log.d("CoilImageDetail", "Start loading pathOrUrl (no sPath): ${request.data}") },
                        onSuccess = { _, _ -> android.util.Log.d("CoilImageDetail", "Successfully loaded pathOrUrl (no sPath)") },
                        onError = { _, result -> android.util.Log.e("CoilImageDetail", "Error loading pathOrUrl (no sPath): ${result.throwable.message}", result.throwable) }
                    )
                }
                btnOpenBrowser.setOnClickListener {
                    pathOrUrl?.let {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                        startActivity(browserIntent)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ApiClient.uploadFlow.collect { event ->
                    if (event is UploadEvent.Success && event.filename == "PREVIEW_" + filename) {
                        previewImageId = event.imageId
                        previewPublicUrl = event.publicUrl
                        previewStoragePath = event.storagePath
                        if (isLocal) {
                            btnOpenBrowser.visibility = View.VISIBLE
                            btnOpenBrowser.setOnClickListener {
                                lifecycleScope.launch {
                                    val sPath = event.storagePath
                                    val urlToOpen = if (!sPath.isNullOrEmpty()) {
                                        ApiClient.getSignedUrl(sPath)
                                    } else {
                                        event.publicUrl
                                    }
                                    if (urlToOpen != null) {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                        startActivity(browserIntent)
                                    }
                                }
                            }
                        } else {
                            // If we already saved permanently, delete the preview immediately
                            SnipShotApp.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                ApiClient.deleteImage(event.imageId)
                            }
                        }
                    } else if (event is UploadEvent.Failure && event.filename == "PREVIEW_" + filename) {
                        if (isLocal) {
                            Toast.makeText(this@ImageDetailActivity, "Cloud sync failed: ${event.error}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        if (!isQueue) {
        btnSaveToAccount.setOnClickListener {
            if (isLocal) {
                if (!ApiClient.isLoggedIn()) {
                    Toast.makeText(this, "Please log in to save to your account", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    return@setOnClickListener
                }

                btnSaveToAccount.isEnabled = false
                val file = File(localFilePath ?: "")
                if (!file.exists()) {
                    Toast.makeText(this, "Local file not found", Toast.LENGTH_SHORT).show()
                    btnSaveToAccount.isEnabled = true
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val fileBytes = try {
                        file.readBytes()
                    } catch (e: Exception) {
                        Toast.makeText(this@ImageDetailActivity, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnSaveToAccount.isEnabled = true
                        return@launch
                    }

                    val uploadResult = ApiClient.uploadImage(
                        imageBytes = fileBytes,
                        filename = filename,
                        sourceLang = null,
                        targetLang = null
                    )

                    if (uploadResult.isSuccess) {
                        val uploadedObj = uploadResult.getOrNull()
                        val newImageId = uploadedObj?.optInt("id", -1) ?: -1
                        val newPublicUrl = uploadedObj?.optString("public_url")
                        val newStoragePath = uploadedObj?.optString("storage_path")

                        if (newImageId != -1 && (!newPublicUrl.isNullOrEmpty() || !newStoragePath.isNullOrEmpty())) {
                            isLocal = false
                            imageId = newImageId
                            pathOrUrl = newPublicUrl
                            storagePath = newStoragePath

                            btnSaveToAccount.visibility = View.GONE
                            btnOpenBrowser.visibility = View.VISIBLE
                            btnOpenBrowser.setOnClickListener {
                                lifecycleScope.launch {
                                    val sPath = storagePath
                                    val urlToOpen = if (!sPath.isNullOrEmpty()) {
                                        ApiClient.getSignedUrl(sPath)
                                    } else {
                                        pathOrUrl
                                    }
                                    if (urlToOpen != null) {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                        startActivity(browserIntent)
                                    }
                                }
                            }

                            Toast.makeText(this@ImageDetailActivity, "Saved to account!", Toast.LENGTH_SHORT).show()

                            if (previewImageId != -1) {
                                SnipShotApp.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    ApiClient.deleteImage(previewImageId)
                                }
                            }
                        } else {
                            Toast.makeText(this@ImageDetailActivity, "Upload succeeded but failed to parse response", Toast.LENGTH_LONG).show()
                            btnSaveToAccount.isEnabled = true
                        }
                    } else {
                        val errMsg = uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                        Toast.makeText(this@ImageDetailActivity, "Failed to save: $errMsg", Toast.LENGTH_LONG).show()
                        btnSaveToAccount.isEnabled = true
                    }
                }
            } else if (!isLocal && filename.startsWith("PREVIEW_")) {
                val popup = android.widget.PopupMenu(this, btnSaveToAccount)
                popup.menu.add(0, 1, 0, "Save to My Files")
                popup.menu.add(0, 2, 0, "Save to Folder")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            saveCloudPreview(imageId, filename, null, tvFilename, btnSaveToAccount)
                            true
                        }
                        2 -> {
                            val bottomSheet = FolderPickerBottomSheet { folderId ->
                                saveCloudPreview(imageId, filename, folderId, tvFilename, btnSaveToAccount)
                            }
                            bottomSheet.show(supportFragmentManager, "folder_picker")
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        btnDelete.setOnClickListener {
            if (isLocal) {
                val file = File(pathOrUrl ?: "")
                if (file.delete()) {
                    if (previewImageId != -1) {
                        SnipShotApp.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            ApiClient.deleteImage(previewImageId)
                        }
                    }
                    Toast.makeText(this, "Deleted locally", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            } else {
                lifecycleScope.launch {
                    var cloudDeleteSuccess = true
                    if (imageId != -1) {
                        val result = ApiClient.deleteImage(imageId)
                        cloudDeleteSuccess = result.isSuccess
                    }
                    
                    localFilePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    
                    if (cloudDeleteSuccess) {
                        Toast.makeText(this@ImageDetailActivity, "Deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ImageDetailActivity, "Failed to delete from cloud", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

        if (isQueue && taskId != null) {
            lifecycleScope.launch {
                TranslationQueueManager.tasks.collect { tasks ->
                    val task = tasks.find { it.id == taskId }
                    if (task == null) {
                        finish()
                        return@collect
                    }
                    
                    when (task.status) {
                        TranslationTask.Status.QUEUED -> {
                            tvFilename.text = "Queued (#${task.queuePosition})"
                            btnDelete.visibility = View.VISIBLE
                            btnDelete.setOnClickListener {
                                TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                Toast.makeText(this@ImageDetailActivity, "Translation cancelled", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            btnSaveToAccount.visibility = View.GONE
                        }
                        TranslationTask.Status.PREPARING -> {
                            tvFilename.text = "Preparing..."
                            btnDelete.visibility = View.GONE
                            btnSaveToAccount.visibility = View.GONE
                        }
                        TranslationTask.Status.TRANSLATING -> {
                            tvFilename.text = "Translating..."
                            btnDelete.visibility = View.GONE
                            btnSaveToAccount.visibility = View.GONE
                        }
                        TranslationTask.Status.UPLOADING -> {
                            tvFilename.text = "Uploading..."
                            btnDelete.visibility = View.GONE
                            btnSaveToAccount.visibility = View.GONE
                        }
                        TranslationTask.Status.FAILED -> {
                            tvFilename.text = "Failed: ${task.errorMessage ?: "Unknown error"}"
                            btnDelete.visibility = View.VISIBLE
                            btnDelete.setOnClickListener {
                                TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                Toast.makeText(this@ImageDetailActivity, "Removed from queue", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            
                            btnSaveToAccount.setImageResource(android.R.drawable.ic_menu_rotate)
                            btnSaveToAccount.visibility = View.VISIBLE
                            btnSaveToAccount.setOnClickListener {
                                TranslationQueueManager.updateTaskStatus(this@ImageDetailActivity, task.id, TranslationTask.Status.QUEUED)
                                val serviceIntent = Intent(this@ImageDetailActivity, TranslationService::class.java)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(serviceIntent)
                                } else {
                                    startService(serviceIntent)
                                }
                                Toast.makeText(this@ImageDetailActivity, "Retrying translation...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        TranslationTask.Status.COMPLETED -> {
                            tvFilename.text = "Completed"
                            btnDelete.visibility = View.VISIBLE
                            btnDelete.setOnClickListener {
                                if (task.completedImageId != null) {
                                    lifecycleScope.launch {
                                        ApiClient.deleteImage(task.completedImageId!!)
                                        TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                        Toast.makeText(this@ImageDetailActivity, "Deleted", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                } else {
                                    TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                    finish()
                                }
                            }
                            
                            btnSaveToAccount.setImageResource(android.R.drawable.ic_menu_save)
                            btnSaveToAccount.visibility = View.VISIBLE
                            btnSaveToAccount.setOnClickListener {
                                val popup = android.widget.PopupMenu(this@ImageDetailActivity, btnSaveToAccount)
                                popup.menu.add(0, 1, 0, "Save to My Files")
                                popup.menu.add(0, 2, 0, "Save to Folder")
                                popup.setOnMenuItemClickListener { menuItem ->
                                    val cleanName = task.completedStoragePath?.substringAfterLast("PREVIEW_") ?: "Translated_Image.png"
                                    when (menuItem.itemId) {
                                        1 -> {
                                            saveCloudPreview(task.completedImageId ?: -1, cleanName, null, tvFilename, btnSaveToAccount)
                                            TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                            finish()
                                            true
                                        }
                                        2 -> {
                                            val bottomSheet = FolderPickerBottomSheet { folderId ->
                                                saveCloudPreview(task.completedImageId ?: -1, cleanName, folderId, tvFilename, btnSaveToAccount)
                                                TranslationQueueManager.removeTask(this@ImageDetailActivity, task.id)
                                                finish()
                                            }
                                            bottomSheet.show(supportFragmentManager, "folder_picker")
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                popup.show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveCloudPreview(imageId: Int, filename: String, folderId: Int?, tvFilename: TextView, btnSave: ImageButton) {
        lifecycleScope.launch {
            val cleanName = filename.removePrefix("PREVIEW_")
            val result = ApiClient.saveImageToFolder(imageId, cleanName, folderId)
            if (result.isSuccess) {
                if (folderId == null) {
                    Toast.makeText(this@ImageDetailActivity, "Saved to My Files", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ImageDetailActivity, "Saved to folder", Toast.LENGTH_SHORT).show()
                }
                tvFilename.text = cleanName
                btnSave.visibility = View.GONE
            } else {
                Toast.makeText(this@ImageDetailActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
