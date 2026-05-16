package com.example.snipshot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.snipshot.api.ApiClient
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.launch
import java.io.File

class ImageDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        val photoView = findViewById<PhotoView>(R.id.photo_view)
        val tvFilename = findViewById<TextView>(R.id.tv_filename)
        val btnDelete = findViewById<ImageButton>(R.id.btn_delete)
        val btnOpenBrowser = findViewById<ImageButton>(R.id.btn_open_browser)

        val isLocal = intent.getBooleanExtra("is_local", false)
        val imageId = intent.getIntExtra("image_id", -1)
        val filename = intent.getStringExtra("filename") ?: "Unknown"
        val pathOrUrl = intent.getStringExtra("path_or_url")

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
                if (pathOrUrl != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pathOrUrl))
                    startActivity(browserIntent)
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
                if (imageId != -1) {
                    lifecycleScope.launch {
                        val result = ApiClient.deleteImage(imageId)
                        if (result.isSuccess) {
                            Toast.makeText(this@ImageDetailActivity, "Deleted from cloud", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@ImageDetailActivity, "Failed to delete from cloud", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
