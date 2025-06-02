//package com.example.snipshot
//
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.graphics.*
//import android.os.Binder
//import android.os.IBinder
//import android.view.*
//import android.widget.Toast
//import kotlin.math.abs
//
//class SnippingOverlay : Service() {
//    private lateinit var windowManager: WindowManager
//    private lateinit var overlayView: View
//    private var startX = 0f
//    private var startY = 0f
//    private var endX = 0f
//    private var endY = 0f
//    private var isSelecting = false
//    private var captureService: ScreenCaptureService? = null
//
//    // Binder class for service connection
//    inner class LocalBinder : Binder() {
//        fun getService(): SnippingOverlay = this@SnippingOverlay
//    }
//
//    private val binder = LocalBinder()
//
//    override fun onBind(intent: Intent?): IBinder {
//        return binder
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        setupOverlayView()
//    }
//
//    private fun setupOverlayView() {
//        overlayView = object : View(this) {
//            private val paint = Paint().apply {
//                color = Color.argb(100, 0, 0, 255)
//                style = Paint.Style.FILL
//                strokeWidth = 5f
//            }
//
//            private val borderPaint = Paint().apply {
//                color = Color.BLUE
//                style = Paint.Style.STROKE
//                strokeWidth = 3f
//            }
//
//            override fun onDraw(canvas: Canvas) {
//                super.onDraw(canvas)
//                if (isSelecting) {
//                    val left = minOf(startX, endX)
//                    val top = minOf(startY, endY)
//                    val right = maxOf(startX, endX)
//                    val bottom = maxOf(startY, endY)
//
//                    // Draw semi-transparent overlay outside selection
//                    canvas.drawColor(Color.argb(100, 0, 0, 0))
//                    canvas.drawRect(left, top, right, bottom, paint)
//                    canvas.drawRect(left, top, right, bottom, borderPaint)
//                }
//            }
//        }
//
//        overlayView.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    startX = event.rawX
//                    startY = event.rawY
//                    endX = event.rawX
//                    endY = event.rawY
//                    isSelecting = true
//                    overlayView.invalidate()
//                    true
//                }
//                MotionEvent.ACTION_MOVE -> {
//                    endX = event.rawX
//                    endY = event.rawY
//                    overlayView.invalidate()
//                    true
//                }
//                MotionEvent.ACTION_UP -> {
//                    endX = event.rawX
//                    endY = event.rawY
//                    isSelecting = false
//
//                    val left = minOf(startX, endX).toInt()
//                    val top = minOf(startY, endY).toInt()
//                    val width = abs(endX - startX).toInt()
//                    val height = abs(endY - startY).toInt()
//
//                    if (width > 50 && height > 50) { // Minimum size threshold
//                        captureService?.captureRegion(left, top, width, height)
//                    } else {
//                        Toast.makeText(this@SnippingOverlay,
//                            "Selection too small", Toast.LENGTH_SHORT).show()
//                    }
//
//                    stopSelf()
//                    true
//                }
//                else -> false
//            }
//        }
//
//        val params = WindowManager.LayoutParams(
//            WindowManager.LayoutParams.MATCH_PARENT,
//            WindowManager.LayoutParams.MATCH_PARENT,
//            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
//                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
//                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//            PixelFormat.TRANSLUCENT
//        )
//
//        windowManager.addView(overlayView, params)
//    }
//
//    fun setCaptureService(service: ScreenCaptureService) {
//        this.captureService = service
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        windowManager.removeView(overlayView)
//    }
//}