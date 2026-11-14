package com.youcef.eddirasatv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pageBitmap: Bitmap? = null
    private var offsetX = 0f
    private var offsetY = 0f

    fun setPage(bitmap: Bitmap) {
        pageBitmap?.recycle()
        pageBitmap = bitmap
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    fun clear() {
        pageBitmap?.recycle()
        pageBitmap = null
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    fun scrollByPixels(dx: Float, dy: Float) {
        val bitmap = pageBitmap ?: return
        val maxX = (bitmap.width - width).coerceAtLeast(0).toFloat()
        val maxY = (bitmap.height - height).coerceAtLeast(0).toFloat()
        offsetX = (offsetX + dx).coerceIn(0f, maxX)
        offsetY = (offsetY + dy).coerceIn(0f, maxY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageBitmap?.let { canvas.drawBitmap(it, -offsetX, -offsetY, null) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clear()
    }
}
