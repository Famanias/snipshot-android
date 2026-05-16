package com.example.snipshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.snipshot.api.ApiClient
import com.example.snipshot.ui.FolderPickerBottomSheet
import com.example.snipshot.utils.StorageManager
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MangaResultActivity : AppCompatActivity() {

    private lateinit var resultImage: PhotoView
    private lateinit var btnSave: Button
    private lateinit var btnClose: Button
    private var resultBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manga_result)

        resultImage = findViewById(R.id.result_image)
        btnSave = findViewById(R.id.btn_save)
        btnClose = findViewById(R.id.btn_close)

        val imageBytes = intent.getByteArrayExtra("image_bytes")

        if (imageBytes == null) {
            Log.e("MangaResultActivity", "Missing image data")
            finish()
            return
        }

        resultBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        resultImage.setImageBitmap(resultBitmap)

        btnSave.setOnClickListener {
            saveImage()
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun saveImage() {
        val bitmap = resultBitmap ?: return

        if (ApiClient.isLoggedIn()) {
            val bottomSheet = FolderPickerBottomSheet { folderId ->
                uploadToCloud(bitmap, folderId)
            }
            bottomSheet.show(supportFragmentManager, "FolderPicker")
        } else {
            val file = StorageManager.saveLocally(this, bitmap)
            if (file != null) {
                Toast.makeText(this, "Saved locally", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save locally", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadToCloud(bitmap: Bitmap, folderId: Int?) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageBytes = stream.toByteArray()
        val filename = "snipshot_${System.currentTimeMillis()}.png"

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        btnSave.isEnabled = false

        lifecycleScope.launch {
            val result = ApiClient.uploadImage(imageBytes, filename, folderId)
            btnSave.isEnabled = true
            if (result.isSuccess) {
                Toast.makeText(this@MangaResultActivity, "Saved to cloud", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MangaResultActivity, "Failed to upload to cloud", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resultBitmap?.recycle()
    }
}
