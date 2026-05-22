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

        var isLocal = intent.getBooleanExtra("is_local", false)
        var imageId = intent.getIntExtra("image_id", -1)
        val filename = intent.getStringExtra("filename") ?: "Unknown"
        var pathOrUrl = intent.getStringExtra("path_or_url")
        val localFilePath: String? = if (isLocal) pathOrUrl else null

        tvFilename.text = filename

        if (isLocal) {
            btnOpenBrowser.visibility = View.GONE
            val file = File(pathOrUrl ?: "")
            if (file.exists()) {
                photoView.load(file)
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            photoView.load(pathOrUrl)
            
            btnOpenBrowser.setOnClickListener {
                val currentPathOrUrl = pathOrUrl
                if (currentPathOrUrl != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentPathOrUrl))
                    startActivity(browserIntent)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ApiClient.uploadFlow.collect { event ->
                    if (event is UploadEvent.Success && event.filename == filename) {
                        isLocal = false
                        imageId = event.imageId
                        pathOrUrl = event.publicUrl
                        btnOpenBrowser.visibility = View.VISIBLE
                        btnOpenBrowser.setOnClickListener {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.publicUrl))
                            startActivity(browserIntent)
                        }
                    } else if (event is UploadEvent.Failure && event.filename == filename) {
                        Toast.makeText(this@ImageDetailActivity, "Cloud sync failed: ${event.error}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnDelete.setOnClickListener {
            if (isLocal) {
                val file = File(pathOrUrl ?: "")
                if (file.delete()) {
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
