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
import android.util.DisplayMetrics
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mainBubbleView: View
    private var closeIconView: ImageView? = null
    private var isDragging = false

    private var closeIconParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isClick = false
    private val clickThreshold = 10 // Pixels threshold for movement to be considered a click
    private val closeIconSize = 70 // Size of the close icon in dp

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create main bubble
        mainBubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_icon, FrameLayout(this), false)
        val mainParams = WindowManager.LayoutParams(
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

        // Create close icon
        createCloseIcon()

        val mainBubble = mainBubbleView.findViewById<ImageView>(R.id.bubble_icon).apply {
            setImageResource(R.drawable.ic_main) // Set a custom icon for the main bubble
            setOnClickListener {
                startSnipActivity()
            }
        }

        // Set touch listener for dragging the main bubble
        mainBubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    isClick = true
                    initialX = mainParams.x
                    initialY = mainParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        closeIconView?.animate()?.alpha(0f)?.setDuration(200)?.start()
                        checkCloseIcon(mainParams)
                    } else {
                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)
                        if (isClick && deltaX < clickThreshold && deltaY < clickThreshold) {
                            view.performClick()
                        }
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (deltaX > clickThreshold || deltaY > clickThreshold) {
                        isClick = false
                        if (!isDragging) {
                            isDragging = true
                            closeIconView?.animate()?.alpha(1f)?.setDuration(200)?.start()
                        }
                    }

                    mainParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    mainParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(mainBubbleView, mainParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(mainBubbleView, mainParams)
    }

    private fun createCloseIcon() {
        closeIconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            alpha = 0f // Initially invisible
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(closeIconSize),
                dpToPx(closeIconSize)
            )
        }

        closeIconParams = WindowManager.LayoutParams(
            dpToPx(closeIconSize),
            dpToPx(closeIconSize),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(50)
        }

        windowManager.addView(closeIconView, closeIconParams)
    }

    private fun checkCloseIcon(params: WindowManager.LayoutParams) {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels

        // Calculate bubble position
        val bubbleCenterX = params.x + dpToPx(30) // Half of bubble width
        val bubbleCenterY = params.y + dpToPx(30) // Half of bubble height

        // Calculate close icon position
        val closeIconCenterX = displayMetrics.widthPixels / 2
        val closeIconCenterY = screenHeight - dpToPx(50) - dpToPx(closeIconSize) / 2

        // Check if bubble center is within close icon bounds
        val distanceX = abs(bubbleCenterX - closeIconCenterX)
        val distanceY = abs(bubbleCenterY - closeIconCenterY)
        val threshold = dpToPx(closeIconSize) / 2 + dpToPx(30) // Bubble radius + some padding

        if (distanceX < threshold && distanceY < threshold) {
            stopSelf()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
        if (::mainBubbleView.isInitialized) {
            windowManager.removeView(mainBubbleView)
        }
        closeIconView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}