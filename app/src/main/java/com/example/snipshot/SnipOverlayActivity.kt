package com.example.snipshot

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

        setContentView(R.layout.activity_snip_overlay)

        imageView = findViewById(R.id.snip_preview)
        drawingView = findViewById(R.id.drawing_view)
        val cancelButton = findViewById<Button>(R.id.cancel_button)
        val saveButton = findViewById<Button>(R.id.save_button)

        // Get the screenshot Bitmap from the Intent
        screenshotBitmap = intent.getParcelableExtra("screenshot") as? Bitmap
        if (screenshotBitmap == null) {
            Log.e("SnipOverlayActivity", "Failed to receive screenshot Bitmap")
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
                    }
                    true
                }
                else -> false
            }
        }

        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            captureSnip()
        }
    }

    private fun captureSnip() {
        screenshotBitmap?.let { bitmap ->
            val width = (if (endX > startX) endX - startX else startX - endX).toInt()
            val height = (if (endY > startY) endY - startY else startY - endY).toInt()
            val left = if (endX > startX) startX.toInt() else endX.toInt()
            val top = if (endY > startY) startY.toInt() else endY.toInt()

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
            finish()
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

    override fun onDestroy() {
        super.onDestroy()
        screenshotBitmap?.recycle() // Free up memory
    }
}