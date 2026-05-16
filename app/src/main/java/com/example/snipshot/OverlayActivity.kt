package com.example.snipshot

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.snipshot.api.ApiClient
import com.example.snipshot.ui.FolderPickerBottomSheet
import com.example.snipshot.utils.StorageManager
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class OverlayActivity : AppCompatActivity() {

    private lateinit var overlayImage: ImageView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var openTranslate: Button
    private lateinit var btnSave: Button
    private lateinit var overlays: List<OverlayItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay)

        overlayImage = findViewById<ImageView>(R.id.overlay_image)
        overlayContainer = findViewById<FrameLayout>(R.id.overlay_container)
        openTranslate = findViewById<Button>(R.id.open_translate)
        btnSave = findViewById<Button>(R.id.btn_save)

        // Support either a raw byte[] image (sent as "image_bytes") or a base64 string ("image_base64")
        val imageBytesFromIntent = intent.getByteArrayExtra("image_bytes")
        val base64Image = intent.getStringExtra("image_base64")

        val imageBytes: ByteArray? = when {
            imageBytesFromIntent != null -> imageBytesFromIntent
            base64Image != null -> Base64.decode(base64Image, Base64.DEFAULT)
            else -> null
        }

        // overlays JSON can be under a few different keys depending on sender
        val overlaysJson = intent.getStringExtra("overlays_json")
            ?: intent.getStringExtra("overlays")
            ?: intent.getStringExtra("overlaysJson")

        if (imageBytes == null) {
            Log.e("OverlayActivity", "Missing image data")
            finish()
            return
        }

        // Decode screenshot bytes
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        overlayImage.setImageBitmap(bitmap)

        // Parse overlays (if missing, treat as empty list)
        overlays = if (!overlaysJson.isNullOrEmpty()) {
            OverlayParser.parse(overlaysJson)
        } else {
            emptyList()
        }

        // Draw overlays
        overlayImage.post {
            overlays.forEach { item: OverlayItem ->
                val (x1, y1, x2, y2, text) = item
                val boxW = (x2 - x1).coerceAtLeast(1)
                val boxH = (y2 - y1).coerceAtLeast(1)

                val tv = TextView(this).apply {
                    this.text = text
                    setTextColor(Color.BLACK)
                    textSize = fitFontSize(text, boxW, boxH)
                    setBackgroundColor(Color.argb(180, 255, 255, 255))
                    // set padding (left, top, right, bottom)
                    setPadding(6, 6, 6, 6)
                    gravity = Gravity.CENTER
                }

                val params = FrameLayout.LayoutParams(boxW, boxH)
                // use margins to position the overlay box
                params.leftMargin = x1
                params.topMargin = y1
                overlayContainer.addView(tv, params)
            }
        }

        // Redirect to TranslateActivity when clicked
        openTranslate.setOnClickListener {
            val extracted = overlays.joinToString("\n") { it.text }
            val intent = Intent(this, TranslateActivity::class.java).apply {
                putExtra("extracted_text", extracted)
            }
            startActivity(intent)
        }

        btnSave.setOnClickListener {
            saveImage()
        }
    }

    private fun saveImage() {
        if (overlayImage.width == 0 || overlayImage.height == 0) return

        val bitmap = Bitmap.createBitmap(overlayImage.width, overlayImage.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        overlayImage.draw(canvas)
        overlayContainer.draw(canvas)

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
                Toast.makeText(this@OverlayActivity, "Saved to cloud", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@OverlayActivity, "Failed to upload to cloud", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fitFontSize(text: String, boxW: Int, boxH: Int): Float {
        // base size from box height, but also cap by box width so text doesn't overflow
        var size = (boxH * 0.5f).coerceAtLeast(12f).coerceAtMost(24f)
        // ensure font fits horizontally (very approximate)
        val maxByWidth = (boxW / 8f).coerceAtLeast(10f)
        size = size.coerceAtMost(maxByWidth)
        if (text.length > 50) size *= 0.8f
        return size
    }
}

// Simple data holder for overlay items
data class OverlayItem(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val text: String)

// Parser for overlay JSON produced by the OCR/translate backend.
object OverlayParser {
    fun parse(json: String): List<OverlayItem> {
        val out = mutableListOf<OverlayItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                // support both field names used across app: "bbox" or "box"
                val bbox = when {
                    obj.has("bbox") -> obj.getJSONArray("bbox")
                    obj.has("box") -> obj.getJSONArray("box")
                    else -> null
                }
                if (bbox != null && bbox.length() >= 4) {
                    val x1 = bbox.getInt(0)
                    val y1 = bbox.getInt(1)
                    val x2 = bbox.getInt(2)
                    val y2 = bbox.getInt(3)
                    val text = when {
                        obj.has("translated") -> obj.optString("translated")
                        obj.has("text") -> obj.optString("text")
                        else -> obj.optString("label", "")
                    }
                    out.add(OverlayItem(x1, y1, x2, y2, text))
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayParser", "Failed to parse overlays: ${e.message}")
        }
        return out
    }
}
