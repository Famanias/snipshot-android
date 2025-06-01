package com.example.snipshot

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
//import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the bubble layout using a dummy root view (FrameLayout) to resolve layout params
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, FrameLayout(this), false)

        // Set layout parameters for the overlay bubble
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Set initial position on screen
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        val bubble = bubbleView.findViewById<ImageView>(R.id.bubble_icon)

        // Set touch listener to enable drag and click
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(view: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isClick = true
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            view?.performClick()
                            val snipIntent = Intent(applicationContext, SnipActivity::class.java)
                            snipIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(snipIntent)
                        }
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        isClick = false
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                }
                return false
            }
        })

        // Add the bubble view to the window
        windowManager.addView(bubbleView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
