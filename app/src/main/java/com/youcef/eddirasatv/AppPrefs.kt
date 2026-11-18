package com.youcef.eddirasatv

import android.content.Context

object AppPrefs {
    private const val PREFS = "eddirasa_prefs"
    private const val KEY_PDF_RTL = "pdf_rtl"
    private const val KEY_PDF_ZOOM = "pdf_zoom"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pdfRtl(context: Context): Boolean = prefs(context).getBoolean(KEY_PDF_RTL, true)

    fun setPdfRtl(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PDF_RTL, value).apply()
    }

    fun pdfZoom(context: Context): Int =
        prefs(context).getInt(KEY_PDF_ZOOM, 0).coerceIn(0, 2)

    fun setPdfZoom(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_PDF_ZOOM, value.coerceIn(0, 2)).apply()
    }
}
