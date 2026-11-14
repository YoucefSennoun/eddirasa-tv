package com.youcef.eddirasatv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.sqrt

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var pageView: PdfPageView
    private lateinit var loadingText: TextView
    private lateinit var pageCounter: TextView
    private lateinit var errorOverlay: View
    private lateinit var retryButton: TextView

    private lateinit var renderThread: HandlerThread
    private lateinit var renderHandler: Handler

    private var renderer: PdfRenderer? = null
    private var pageCount = 0
    private var pageIndex = 0
    private var zoomLevel = 0
    private var rendering = false
    private var generation = 0
    private var closed = false
    private var filePath: String? = null
    private var rtlPages = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pdf)

        pageView = findViewById(R.id.pdfPageView)
        loadingText = findViewById(R.id.pdfLoadingText)
        pageCounter = findViewById(R.id.pdfPageCounter)
        errorOverlay = findViewById(R.id.pdfErrorOverlay)
        retryButton = findViewById(R.id.pdfRetryButton)

        filePath = intent.getStringExtra(EXTRA_PDF_PATH)
        rtlPages = AppPrefs.pdfRtl(this)
        zoomLevel = AppPrefs.pdfZoom(this)

        renderThread = HandlerThread("pdf-render").also { it.start() }
        renderHandler = Handler(renderThread.looper)

        retryButton.setOnClickListener { openDocument() }

        pageView.post { openDocument() }
    }

    private fun openDocument() {
        val path = filePath ?: return
        closeRenderer()
        hideError()
        try {
            val descriptor = ParcelFileDescriptor.open(
                File(path),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val opened = PdfRenderer(descriptor)
            renderer = opened
            pageCount = opened.pageCount
            pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (pageCount == 0) {
                showError()
                return
            }
            renderPage(pageIndex)
        } catch (e: Exception) {
            showError()
        }
    }

    private fun renderPage(index: Int) {
        val current = renderer ?: return
        if (closed || pageCount == 0) return

        val widthPx = pageView.width
        if (widthPx <= 0) {
            pageView.post { renderPage(index) }
            return
        }

        val request = ++generation
        rendering = true
        loadingText.visibility = View.VISIBLE

        renderHandler.post {
            if (closed || request != generation) return@post

            var page: PdfRenderer.Page? = null
            try {
                page = current.openPage(index)
                val pageWidth = page.width
                val pageHeight = page.height
                if (pageWidth <= 0 || pageHeight <= 0) {
                    throw IllegalStateException("empty page")
                }

                val fitScale = widthPx.toFloat() / pageWidth.toFloat()
                val scale = when (zoomLevel) {
                    0 -> fitScale
                    1 -> fitScale * 1.5f
                    else -> fitScale * 2f
                }

                var targetWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
                var targetHeight = (pageHeight * scale).toInt().coerceAtLeast(1)
                val maxPixels = 12_000_000.0
                val pixels = targetWidth.toDouble() * targetHeight.toDouble()
                if (pixels > maxPixels) {
                    val factor = sqrt(maxPixels / pixels)
                    targetWidth = (targetWidth * factor).toInt().coerceAtLeast(1)
                    targetHeight = (targetHeight * factor).toInt().coerceAtLeast(1)
                }

                val matrix = Matrix().apply { postScale(scale, scale) }
                var bitmap: Bitmap? = null
                try {
                    val rgb = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.RGB_565
                    )
                    bitmap = rgb
                    Canvas(rgb).drawColor(Color.WHITE)
                    page.render(
                        rgb,
                        null,
                        matrix,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                } catch (e: Exception) {
                    bitmap?.recycle()
                    val argb = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap = argb
                    Canvas(argb).drawColor(Color.WHITE)
                    page.render(
                        argb,
                        null,
                        matrix,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                }
                val rendered = bitmap ?: throw IllegalStateException("render failed")

                if (closed || request != generation) {
                    rendered.recycle()
                    return@post
                }

                runOnUiThread {
                    if (closed || request != generation) {
                        rendered.recycle()
                        return@runOnUiThread
                    }
                    pageView.setPage(rendered)
                    pageCounter.text = getString(
                        R.string.pdf_page_counter,
                        index + 1,
                        pageCount
                    )
                    pageCounter.visibility = View.VISIBLE
                    loadingText.visibility = View.GONE
                    rendering = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (request == generation && !closed) {
                        rendering = false
                        showError()
                    }
                }
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun turnPage(delta: Int) {
        if (closed || rendering || pageCount == 0) return
        val next = (pageIndex + delta).coerceIn(0, pageCount - 1)
        if (next == pageIndex) return
        pageIndex = next
        renderPage(pageIndex)
    }

    private fun scrollPage(direction: Int) {
        val step = 90f * resources.displayMetrics.density
        pageView.scrollByPixels(0f, if (direction < 0) -step else step)
    }

    private fun cycleZoom() {
        if (closed || rendering) return
        zoomLevel = (zoomLevel + 1) % ZOOM_LEVELS
        AppPrefs.setPdfZoom(this, zoomLevel)
        renderPage(pageIndex)
    }

    private fun showError() {
        loadingText.visibility = View.GONE
        pageCounter.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
        retryButton.requestFocus()
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    private fun showPdfMenu() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_pdf_menu)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val minWidth = (320 * resources.displayMetrics.density).toInt()
            val width = (resources.displayMetrics.widthPixels * 0.5f)
                .toInt().coerceAtLeast(minWidth)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val directionItem = dialog.findViewById<TextView>(R.id.pdfMenuDirection)
        directionItem.text = getString(
            if (rtlPages) R.string.menu_pdf_direction_rtl
            else R.string.menu_pdf_direction_ltr
        )

        fun action(block: () -> Unit): View.OnClickListener = View.OnClickListener {
            dialog.dismiss()
            block()
        }

        dialog.findViewById<TextView>(R.id.pdfMenuZoom).setOnClickListener(action {
            cycleZoom()
        })
        directionItem.setOnClickListener(action {
            rtlPages = !rtlPages
            AppPrefs.setPdfRtl(this, rtlPages)
        })
        dialog.findViewById<TextView>(R.id.pdfMenuJump).setOnClickListener(action {
            showPageJump()
        })
        dialog.findViewById<TextView>(R.id.pdfMenuClose).setOnClickListener(action {
            finish()
        })

        dialog.show()
        dialog.findViewById<TextView>(R.id.pdfMenuZoom).requestFocus()
    }

    private fun showPageJump() {
        if (pageCount <= 0) return
        val dialog = Dialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ContextCompat.getDrawable(context, R.drawable.dialog_bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this).apply { addView(container) }

        var currentButton: TextView? = null
        var row: LinearLayout? = null
        for (number in 1..pageCount) {
            if ((number - 1) % JUMP_COLUMNS == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                container.addView(row)
            }
            val button = TextView(this).apply {
                text = number.toString()
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.text_light))
                textSize = 20f
                background = ContextCompat.getDrawable(context, R.drawable.chip_bg)
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dp(56),
                    1f
                ).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    dialog.dismiss()
                    pageIndex = number - 1
                    renderPage(pageIndex)
                }
            }
            row?.addView(button)
            if (number - 1 == pageIndex) currentButton = button
        }

        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val minWidth = (360 * resources.displayMetrics.density).toInt()
            val width = (resources.displayMetrics.widthPixels * 0.6f)
                .toInt().coerceAtLeast(minWidth)
            val height = (resources.displayMetrics.heightPixels * 0.7f).toInt()
            setLayout(width, height)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        dialog.show()
        currentButton?.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (errorOverlay.visibility == View.VISIBLE) {
            return super.dispatchKeyEvent(event)
        }

        val code = event.keyCode
        val handled = code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            code == KeyEvent.KEYCODE_MENU ||
            code == KeyEvent.KEYCODE_CHANNEL_UP ||
            code == KeyEvent.KEYCODE_CHANNEL_DOWN ||
            code == KeyEvent.KEYCODE_PAGE_UP ||
            code == KeyEvent.KEYCODE_PAGE_DOWN

        if (!handled) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) return true

        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                turnPage(if (rtlPages) -1 else 1)
            KeyEvent.KEYCODE_DPAD_LEFT ->
                turnPage(if (rtlPages) 1 else -1)
            KeyEvent.KEYCODE_DPAD_UP -> scrollPage(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> scrollPage(1)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> cycleZoom()
            KeyEvent.KEYCODE_MENU -> showPdfMenu()
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> turnPage(-1)
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> turnPage(1)
        }
        return true
    }

    override fun onDestroy() {
        closed = true
        generation++
        closeRenderer()
        renderThread.quitSafely()
        pageView.clear()
        super.onDestroy()
    }

    private fun closeRenderer() {
        renderer?.let {
            try {
                it.close()
            } catch (ignored: Exception) {
            }
        }
        renderer = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        private const val ZOOM_LEVELS = 3
        private const val JUMP_COLUMNS = 6

        fun intent(context: Context, path: String): Intent =
            Intent(context, PdfViewerActivity::class.java)
                .putExtra(EXTRA_PDF_PATH, path)
    }
}
