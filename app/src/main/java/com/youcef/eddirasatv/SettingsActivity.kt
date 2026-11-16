package com.youcef.eddirasatv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var textSizeRow: TextView
    private lateinit var pdfZoomRow: TextView
    private lateinit var pdfDirectionRow: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_settings)

        textSizeRow = findViewById(R.id.settingsTextSize)
        pdfZoomRow = findViewById(R.id.settingsPdfZoom)
        pdfDirectionRow = findViewById(R.id.settingsPdfDirection)
        val clearCacheRow = findViewById<TextView>(R.id.settingsClearCache)
        val clearHistoryRow = findViewById<TextView>(R.id.settingsClearHistory)

        bind(textSizeRow) { delta -> cycleTextSize(delta) }
        bind(pdfZoomRow) { delta -> cyclePdfZoom(delta) }
        bind(pdfDirectionRow) { delta -> togglePdfDirection(delta) }

        clearCacheRow.setOnClickListener { clearCaches() }
        clearHistoryRow.setOnClickListener {
            AppPrefs.clearHistory(this)
            Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
            refresh()
        }

        refresh()
        textSizeRow.requestFocus()
    }

    private fun bind(row: TextView, adjust: (Int) -> Unit) {
        row.setOnClickListener { adjust(1) }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        adjust(1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        adjust(-1)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    private fun cycleTextSize(delta: Int) {
        val levels = TEXT_ZOOMS
        val current = AppPrefs.textZoom(this)
        var index = levels.indexOfFirst { it >= current }
        if (index < 0) index = levels.size - 1
        val next = (index + delta + levels.size) % levels.size
        AppPrefs.setTextZoom(this, levels[next])
        refresh()
    }

    private fun cyclePdfZoom(delta: Int) {
        val next = (AppPrefs.pdfZoom(this) + delta + PDF_ZOOM_COUNT) % PDF_ZOOM_COUNT
        AppPrefs.setPdfZoom(this, next)
        refresh()
    }

    private fun togglePdfDirection(delta: Int) {
        if (delta == 0) return
        AppPrefs.setPdfRtl(this, !AppPrefs.pdfRtl(this))
        refresh()
    }

    private fun refresh() {
        textSizeRow.text = getString(R.string.settings_text_size, AppPrefs.textZoom(this))
        pdfZoomRow.text = getString(
            R.string.settings_pdf_zoom,
            getString(pdfZoomLabel(AppPrefs.pdfZoom(this)))
        )
        pdfDirectionRow.text = getString(
            R.string.settings_pdf_direction,
            getString(if (AppPrefs.pdfRtl(this)) R.string.pdf_dir_rtl else R.string.pdf_dir_ltr)
        )
    }

    private fun pdfZoomLabel(value: Int): Int = when (value) {
        1 -> R.string.zoom_150
        2 -> R.string.zoom_200
        else -> R.string.zoom_fit
    }

    private fun clearCaches() {
        try {
            File(cacheDir, "pdf").deleteRecursively()
            File(cacheDir, "WebView").deleteRecursively()
            File(File(applicationInfo.dataDir, "app_webview"), "Default/HTTP Cache")
                .deleteRecursively()
            Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
        } catch (ignored: Exception) {
            Toast.makeText(this, R.string.error_message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TEXT_ZOOMS = intArrayOf(100, 115, 135, 150, 175, 200)
        private const val PDF_ZOOM_COUNT = 3
    }
}
