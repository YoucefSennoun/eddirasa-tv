package com.youcef.eddirasatv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AppPrefs {
    private const val PREFS = "eddirasa_prefs"
    private const val KEY_RECENT = "recent"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_TEXT_ZOOM = "text_zoom"
    private const val KEY_SEARCHES = "searches"
    private const val KEY_PDF_RTL = "pdf_rtl"
    private const val MAX_RECENT = 5
    private const val MAX_FAVORITES = 20
    private const val MAX_SEARCHES = 10

    const val DEFAULT_TEXT_ZOOM = 135
    const val MIN_TEXT_ZOOM = 80
    const val MAX_TEXT_ZOOM = 200

    data class Entry(val url: String, val title: String)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recent(context: Context): List<Entry> = read(context, KEY_RECENT)

    fun favorites(context: Context): List<Entry> = read(context, KEY_FAVORITES)

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

    fun pdfRtl(context: Context): Boolean = prefs(context).getBoolean(KEY_PDF_RTL, true)

    fun setPdfRtl(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PDF_RTL, value).apply()
    }

    fun addRecent(context: Context, url: String, title: String) {
        val items = recent(context).filterNot { it.url == url }.toMutableList()
        items.add(0, Entry(url, title))
        write(context, KEY_RECENT, items.take(MAX_RECENT))
    }

    fun isFavorite(context: Context, url: String): Boolean =
        favorites(context).any { it.url == url }

    fun toggleFavorite(context: Context, url: String, title: String): Boolean {
        val items = favorites(context).toMutableList()
        val existing = items.firstOrNull { it.url == url }
        val added: Boolean
        if (existing != null) {
            items.remove(existing)
            added = false
        } else {
            items.add(0, Entry(url, title))
            added = true
        }
        write(context, KEY_FAVORITES, items.take(MAX_FAVORITES))
        return added
    }

    private fun read(context: Context, key: String): List<Entry> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("url")
                if (url.isEmpty()) return@mapNotNull null
                Entry(url, obj.optString("title"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, key: String, items: List<Entry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(JSONObject().put("url", entry.url).put("title", entry.title))
        }
        prefs(context).edit().putString(key, array.toString()).apply()
    }
}
