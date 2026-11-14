package com.youcef.eddirasatv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var cx: Float = 0f
        private set
    var cy: Float = 0f
        private set

    private val radius = 16f * resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius - border.strokeWidth / 2f, border)
    }

    fun moveTo(x: Float, y: Float) {
        cx = x
        cy = y
        invalidate()
    }
}
