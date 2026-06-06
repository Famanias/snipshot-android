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

        btnSaveToAccount.visibility = if (isLocal) View.VISIBLE else View.GONE

        if (isLocal) {
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

        btnSaveToAccount.setOnClickListener {
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
}
