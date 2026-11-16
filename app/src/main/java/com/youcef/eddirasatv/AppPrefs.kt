package com.youcef.eddirasatv

import android.content.Context
import org.json.JSONArray

object AppPrefs {
    private const val PREFS = "eddirasa_prefs"
    private const val KEY_TEXT_ZOOM = "text_zoom"
    private const val KEY_SEARCHES = "searches"
    private const val KEY_PDF_RTL = "pdf_rtl"
    private const val KEY_PDF_ZOOM = "pdf_zoom"
    private const val KEY_HELP_SHOWN = "help_shown"
    private const val MAX_SEARCHES = 10

    const val DEFAULT_TEXT_ZOOM = 135
    const val MIN_TEXT_ZOOM = 80
    const val MAX_TEXT_ZOOM = 200

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun textZoom(context: Context): Int =
        prefs(context).getInt(KEY_TEXT_ZOOM, DEFAULT_TEXT_ZOOM)
            .coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM)

    fun setTextZoom(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_TEXT_ZOOM, value.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM))
            .apply()
    }

    fun searches(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SEARCHES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSearch(context: Context, query: String) {
        val items = searches(context).filterNot { it == query }.toMutableList()
        items.add(0, query)
        val array = JSONArray()
        items.take(MAX_SEARCHES).forEach { array.put(it) }
        prefs(context).edit().putString(KEY_SEARCHES, array.toString()).apply()
    }

    fun clearSearches(context: Context) {
        prefs(context).edit().remove(KEY_SEARCHES).apply()
    }

    fun pdfRtl(context: Context): Boolean = prefs(context).getBoolean(KEY_PDF_RTL, true)

    fun setPdfRtl(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PDF_RTL, value).apply()
    }

    fun pdfZoom(context: Context): Int =
        prefs(context).getInt(KEY_PDF_ZOOM, 0).coerceIn(0, 2)

    fun setPdfZoom(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_PDF_ZOOM, value.coerceIn(0, 2)).apply()
    }

    fun helpShown(context: Context): Boolean = prefs(context).getBoolean(KEY_HELP_SHOWN, false)

    fun setHelpShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_HELP_SHOWN, true).apply()
    }
}
