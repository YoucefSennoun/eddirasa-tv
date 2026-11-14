package com.youcef.eddirasatv

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class FavoritesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_list)

        findViewById<TextView>(R.id.listTitle).text = getString(R.string.favorites_title)
        container = findViewById(R.id.listContainer)
        hint = findViewById(R.id.listHint)
        hint.text = getString(R.string.favorites_hint)
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        container.removeAllViews()
        val items = AppPrefs.favorites(this)
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.favorites_empty)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 24f
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = false
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(dp(16), dp(18), dp(16), dp(18))
            }
            container.addView(empty)
            empty.requestFocus()
            return
        }

        items.forEach { entry ->
            val label = entry.title.ifBlank { entry.url }
            val row = layoutInflater.inflate(R.layout.item_chip, container, false) as TextView
            row.text = label
            row.contentDescription = label
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
            ).apply { bottomMargin = dp(8) }

            row.setOnClickListener {
                startActivity(BrowserActivity.intent(this, entry.url, label))
            }
            row.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN &&
                    event.repeatCount == 0
                ) {
                    AppPrefs.toggleFavorite(this, entry.url, label)
                    Toast.makeText(this, R.string.favorites_removed, Toast.LENGTH_SHORT).show()
                    rebuild()
                    true
                } else {
                    false
                }
            }
            container.addView(row)
        }

        if (container.childCount > 0) {
            container.getChildAt(0).requestFocus()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
