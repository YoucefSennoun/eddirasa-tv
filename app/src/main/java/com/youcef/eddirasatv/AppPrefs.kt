package com.youcef.eddirasatv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AppPrefs {
    private const val PREFS = "eddirasa_prefs"
    private const val KEY_RECENT = "recent"
    private const val KEY_FAVORITES = "favorites"
    private const val MAX_RECENT = 5
    private const val MAX_FAVORITES = 20

    data class Entry(val url: String, val title: String)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recent(context: Context): List<Entry> = read(context, KEY_RECENT)

    fun favorites(context: Context): List<Entry> = read(context, KEY_FAVORITES)

    fun addRecent(context: Context, url: String, title: String) {
        val items = recent(context).filterNot { it.url == url }.toMutableList()
        items.add(0, Entry(url, title))
        write(context, KEY_RECENT, items.take(MAX_RECENT))
    }

    fun isFavorite(context: Context, url: String): Boolean =
        favorites(context).any { it.url == url }

    fun toggleFavorite(context: Context, url: String, title: String) {
        val items = favorites(context).toMutableList()
        val existing = items.firstOrNull { it.url == url }
        if (existing != null) {
            items.remove(existing)
        } else {
            items.add(0, Entry(url, title))
        }
        write(context, KEY_FAVORITES, items.take(MAX_FAVORITES))
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
