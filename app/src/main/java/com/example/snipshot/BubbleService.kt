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
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mainBubbleView: View
    private var helpBubbleView: View? = null
    private var settingsBubbleView: View? = null
    private var snipBubbleView: View? = null
    private var areSubBubblesVisible = false

    // Store LayoutParams for each sub-bubble
    private var helpParams: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null
    private var snipParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isClick = false
    private val clickThreshold = 10 // Pixels threshold for movement to be considered a click
    private val bubbleSpacing = 100 // Spacing between bubbles in dp

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

        val mainBubble = mainBubbleView.findViewById<ImageView>(R.id.bubble_icon).apply {
            setImageResource(R.drawable.ic_main) // Set a custom icon for the main bubble
            setOnClickListener {
                toggleSubBubbles(mainParams)
            }
        }

        // Set touch listener for dragging the main bubble
        mainBubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("BubbleService", "Main Bubble: ACTION_DOWN detected")
                    isClick = true
                    initialX = mainParams.x
                    initialY = mainParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("BubbleService", "Main Bubble: ACTION_UP detected, isClick=$isClick")
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
                    mainParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    mainParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(mainBubbleView, mainParams)
                    // Update positions of sub-bubbles if they are visible
                    if (areSubBubblesVisible) {
                        updateSubBubblePositions(mainParams)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(mainBubbleView, mainParams)
    }

    private fun toggleSubBubbles(mainParams: WindowManager.LayoutParams) {
        if (areSubBubblesVisible) {
            // Hide sub-bubbles
            helpBubbleView?.let { windowManager.removeView(it) }
            settingsBubbleView?.let { windowManager.removeView(it) }
            snipBubbleView?.let { windowManager.removeView(it) }
            helpBubbleView = null
            settingsBubbleView = null
            snipBubbleView = null
            helpParams = null
            settingsParams = null
            snipParams = null
            areSubBubblesVisible = false
            Log.d("BubbleService", "Sub-bubbles hidden")
        } else {
            // Show sub-bubbles
            createSubBubbles(mainParams)
            areSubBubblesVisible = true
            Log.d("BubbleService", "Sub-bubbles shown")
        }
    }

    private fun createSubBubbles(mainParams: WindowManager.LayoutParams) {
        val bubbleHeight = 60 // Height of the bubble from bubble_icon.xml
        val totalOffsetSnip = bubbleHeight + bubbleSpacing
        val totalOffsetSettings = totalOffsetSnip + bubbleHeight + bubbleSpacing
        val totalOffsetHelp = totalOffsetSettings + bubbleHeight + bubbleSpacing

        // Snip Screen Bubble (bottom-most of the sub-bubbles)
        snipBubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_icon, FrameLayout(this), false)
        snipParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mainParams.x
            y = mainParams.y - totalOffsetSnip
        }
        snipBubbleView?.findViewById<ImageView>(R.id.bubble_icon)?.apply {
            setImageResource(R.drawable.ic_snip)
            setOnClickListener {
                startSnipActivity()
            }
        }
        windowManager.addView(snipBubbleView, snipParams)

        // Settings Bubble (middle of the sub-bubbles)
        settingsBubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_icon, FrameLayout(this), false)
        settingsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mainParams.x
            y = mainParams.y - totalOffsetSettings
        }
        settingsBubbleView?.findViewById<ImageView>(R.id.bubble_icon)?.apply {
            setImageResource(R.drawable.ic_settings)
            setOnClickListener {
                startActivity(Intent(applicationContext, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        windowManager.addView(settingsBubbleView, settingsParams)

        // Help Bubble (top-most of the sub-bubbles)
        helpBubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_icon, FrameLayout(this), false)
        helpParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mainParams.x
            y = mainParams.y - totalOffsetHelp
        }
        helpBubbleView?.findViewById<ImageView>(R.id.bubble_icon)?.apply {
            setImageResource(R.drawable.ic_help)
            setOnClickListener {
                startActivity(Intent(applicationContext, HelpActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        windowManager.addView(helpBubbleView, helpParams)
    }

    private fun updateSubBubblePositions(mainParams: WindowManager.LayoutParams) {
        val bubbleHeight = 60 // Height of the bubble from bubble_icon.xml
        val totalOffsetSnip = bubbleHeight + bubbleSpacing
        val totalOffsetSettings = totalOffsetSnip + bubbleHeight + bubbleSpacing
        val totalOffsetHelp = totalOffsetSettings + bubbleHeight + bubbleSpacing

        snipParams?.let {
            it.x = mainParams.x
            it.y = mainParams.y - totalOffsetSnip
            snipBubbleView?.let { view -> windowManager.updateViewLayout(view, it) }
        }
        settingsParams?.let {
            it.x = mainParams.x
            it.y = mainParams.y - totalOffsetSettings
            settingsBubbleView?.let { view -> windowManager.updateViewLayout(view, it) }
        }
        helpParams?.let {
            it.x = mainParams.x
            it.y = mainParams.y - totalOffsetHelp
            helpBubbleView?.let { view -> windowManager.updateViewLayout(view, it) }
        }
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
        helpBubbleView?.let { windowManager.removeView(it) }
        settingsBubbleView?.let { windowManager.removeView(it) }
        snipBubbleView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}