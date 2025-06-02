package com.example.snipshot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class DrawingView : View {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDrawing = false

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // 1-argument constructor (used when creating the view programmatically)
    constructor(context: Context) : super(context)

    // 2-argument constructor (required for XML inflation)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    // 3-argument constructor (optional, for default style attributes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Public setters to update coordinates from SnipOverlayActivity
    fun setDrawingCoordinates(startX: Float, startY: Float, endX: Float, endY: Float, isDrawing: Boolean) {
        this.startX = startX
        this.startY = startY
        this.endX = endX
        this.endY = endY
        this.isDrawing = isDrawing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isDrawing) {
            canvas.drawRect(startX, startY, endX, endY, paint)
        } else if (startX != endX && startY != endY) {
            canvas.drawRect(startX, startY, endX, endY, paint)
        }
    }
}