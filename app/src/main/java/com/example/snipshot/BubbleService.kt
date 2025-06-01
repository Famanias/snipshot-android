package com.example.snipshot

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.util.Log
import java.lang.Math.abs

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isClick = false
    private val clickThreshold = 10 // Pixels threshold for movement to be considered a click

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, FrameLayout(this), false)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val bubble = bubbleView.findViewById<ImageView>(R.id.bubble_icon).apply {
            setOnClickListener {
                startSnipActivity()
            }
        }

        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("BubbleService", "ACTION_DOWN detected")
                    isClick = true
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("BubbleService", "ACTION_UP detected, isClick=$isClick")
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (isClick && deltaX < clickThreshold && deltaY < clickThreshold) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (deltaX > clickThreshold || deltaY > clickThreshold) {
                        isClick = false
                    }
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun startSnipActivity() {
        Intent(applicationContext, SnipActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(this)
            } catch (e: Exception) {
                Log.e("BubbleService", "Failed to start SnipActivity: ${e.message}", e)
                Toast.makeText(
                    applicationContext,
                    "Failed to start snipping: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}