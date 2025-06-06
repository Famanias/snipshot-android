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

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setDrawingCoordinates(startX: Float, startY: Float, endX: Float, endY: Float, isDrawing: Boolean) {
        this.startX = startX
        this.startY = startY
        this.endX = endX
        this.endY = endY
        this.isDrawing = isDrawing
        invalidate() // Request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isDrawing || (startX != endX && startY != endY)) {
            // Normalize coordinates to ensure correct rectangle drawing
            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}