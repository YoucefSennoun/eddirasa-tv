package com.youcef.eddirasatv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SearchActivity : AppCompatActivity() {

    private enum class LayoutMode { ARABIC, LATIN, DIGITS }

    private lateinit var queryText: TextView
    private lateinit var recentLabel: TextView
    private lateinit var recentScroll: HorizontalScrollView
    private lateinit var recentList: LinearLayout
    private lateinit var keyboardContainer: LinearLayout

    private val query = StringBuilder()
    private var layoutMode = LayoutMode.ARABIC
    private var languageKey: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_search)

        queryText = findViewById(R.id.queryText)
        recentLabel = findViewById(R.id.recentLabel)
        recentScroll = findViewById(R.id.recentScroll)
        recentList = findViewById(R.id.recentList)
        keyboardContainer = findViewById(R.id.keyboardContainer)

        renderQuery()
        buildKeyboard()
        renderRecent()
    }

    override fun onResume() {
        super.onResume()
        renderRecent()
        if (currentFocus == null) {
            (keyboardContainer.getChildAt(0) as? LinearLayout)
                ?.getChildAt(0)
                ?.requestFocus()
        }
    }

    private fun renderQuery() {
        val value = query.toString()
        if (value.isEmpty()) {
            queryText.text = getString(R.string.search_hint)
            queryText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            queryText.text = value
            queryText.setTextColor(ContextCompat.getColor(this, R.color.text_light))
        }
    }

    private fun renderRecent() {
        recentList.removeAllViews()
        val items = AppPrefs.searches(this)
        val visible = items.isNotEmpty()
        recentLabel.visibility = if (visible) View.VISIBLE else View.GONE
        recentScroll.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        items.forEach { value ->
            val chip = layoutInflater.inflate(R.layout.item_chip, recentList, false) as TextView
            chip.text = value
            chip.contentDescription = value
            chip.textSize = 20f
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(62)
            ).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            chip.setPadding(dp(18), 0, dp(18), 0)
            chip.setOnClickListener { performSearch(value) }
            recentList.addView(chip)
        }
    }

    private fun buildKeyboard() {
        keyboardContainer.removeAllViews()
        when (layoutMode) {
            LayoutMode.ARABIC -> {
                addRow(listOf("ا", "ب", "ت", "ث", "ج", "ح", "خ"))
                addRow(listOf("د", "ذ", "ر", "ز", "س", "ش", "ص"))
                addRow(listOf("ض", "ط", "ظ", "ع", "غ", "ف", "ق"))
                addRow(listOf("ك", "ل", "م", "ن", "ه", "و", "ي"))
                addRow(listOf("أ", "إ", "آ", "ة", "ى", "ئ", "ؤ", "ء"))
            }
            LayoutMode.LATIN -> {
                addRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
                addRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"))
                addRow(listOf("z", "x", "c", "v", "b", "n", "m", "'"))
            }
            LayoutMode.DIGITS -> {
                addRow(listOf("1", "2", "3", "4", "5"))
                addRow(listOf("6", "7", "8", "9", "0"))
                addRow(listOf(".", "-", "/", ":", "!"))
            }
        }
        addActionRow()
    }

    private fun addRow(labels: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = dp(6) }
        }
        labels.forEach { label ->
            row.addView(makeKey(label) { typeChar(label) })
        }
        keyboardContainer.addView(row)
    }

    private fun addActionRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val nextLabel = when (layoutMode) {
            LayoutMode.ARABIC -> "EN"
            LayoutMode.LATIN -> "123"
            LayoutMode.DIGITS -> "ع"
        }
        languageKey = makeKey(nextLabel, 0.8f) {
            layoutMode = when (layoutMode) {
                LayoutMode.ARABIC -> LayoutMode.LATIN
                LayoutMode.LATIN -> LayoutMode.DIGITS
                LayoutMode.DIGITS -> LayoutMode.ARABIC
            }
            buildKeyboard()
            languageKey?.requestFocus()
        }
        row.addView(languageKey)

        row.addView(makeKey(getString(R.string.search_space), 1.6f) { addSpace() })

        val deleteKey = makeKey(getString(R.string.search_delete), 1f) { deleteChar() }
        deleteKey.setOnLongClickListener {
            query.setLength(0)
            renderQuery()
            true
        }
        row.addView(deleteKey)

        row.addView(makeKey(getString(R.string.search_voice), 0.8f) { launchVoice() })
        row.addView(makeKey(getString(R.string.search_go), 1.4f, bold = true) {
            performSearch(query.toString())
        })

        keyboardContainer.addView(row)
    }

    private fun makeKey(
        label: String,
        weight: Float = 1f,
        bold: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = label
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.text_light))
        textSize = 20f
        setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        background = ContextCompat.getDrawable(context, R.drawable.chip_bg)
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
        setOnClickListener { onClick() }
    }

    private fun typeChar(value: String) {
        if (query.length >= MAX_QUERY_LENGTH) return
        query.append(value)
        renderQuery()
    }

    private fun addSpace() {
        if (query.isEmpty() || query.last() == ' ' || query.length >= MAX_QUERY_LENGTH) return
        query.append(' ')
        renderQuery()
    }

    private fun deleteChar() {
        if (query.isEmpty()) return
        query.setLength(query.length - 1)
        renderQuery()
    }

    private fun performSearch(raw: String) {
        val value = raw.trim()
        if (value.length < 2) {
            Toast.makeText(this, R.string.search_min_chars, Toast.LENGTH_SHORT).show()
            return
        }
        AppPrefs.addSearch(this, value)
        val url = "https://eddirasa.com/search/?q=" + Uri.encode(value)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_QUERY_URL, url))
        finish()
    }

    private fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            startActivityForResult(intent, REQ_VOICE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == Activity.RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                query.setLength(0)
                query.append(text.take(MAX_QUERY_LENGTH))
                renderQuery()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_QUERY_URL = "extra_query_url"
        private const val REQ_VOICE = 41
        private const val MAX_QUERY_LENGTH = 80

        fun intent(context: Context): Intent =
            Intent(context, SearchActivity::class.java)
    }
}
