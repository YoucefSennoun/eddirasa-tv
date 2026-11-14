package com.youcef.eddirasatv

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class HomeActivity : AppCompatActivity() {

    private lateinit var continueBlock: View
    private lateinit var continueRow: LinearLayout
    private lateinit var recentRow: LinearLayout
    private lateinit var favoritesRow: LinearLayout
    private lateinit var gridContainer: LinearLayout
    private var firstTile: View? = null

    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SearchActivity.EXTRA_QUERY_URL)?.let { url ->
                    openUrl(url, getString(R.string.section_search))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_home)

        continueBlock = findViewById(R.id.continueBlock)
        continueRow = findViewById(R.id.continueRow)
        recentRow = findViewById(R.id.recentRow)
        favoritesRow = findViewById(R.id.favoritesRow)
        gridContainer = findViewById(R.id.gridContainer)

        buildGrid()

        if (!AppPrefs.helpShown(this)) {
            AppPrefs.setHelpShown(this)
            window.decorView.post { UiDialogs.showHelp(this) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshContinue()
        refreshRecent()
        refreshFavorites()
        if (currentFocus == null) {
            firstTile?.requestFocus()
        }
    }

    private fun buildGrid() {
        Sections.all.chunked(COLUMNS).forEach { rowSections ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            rowSections.forEach { section ->
                val title = getString(section.titleRes)
                val tile = layoutInflater.inflate(R.layout.item_tile, row, false)
                tile.findViewById<TextView>(R.id.tileBadge).text = section.badge
                tile.findViewById<TextView>(R.id.tileTitle).text = title
                tile.contentDescription = title

                val lp = LinearLayout.LayoutParams(0, dp(140), 1f)
                lp.marginStart = dp(6)
                lp.marginEnd = dp(6)
                tile.layoutParams = lp

                tile.setOnClickListener {
                    if (section.titleRes == R.string.section_search) {
                        searchLauncher.launch(SearchActivity.intent(this))
                    } else {
                        openUrl(section.url, title)
                    }
                }
                attachFocusEffect(tile)
                row.addView(tile)

                if (firstTile == null) firstTile = tile
            }

            gridContainer.addView(row)
        }
    }

    private fun refreshContinue() {
        continueRow.removeAllViews()
        val last = AppPrefs.lastPage(this)
        if (last == null) {
            continueBlock.visibility = View.GONE
            return
        }
        continueBlock.visibility = View.VISIBLE
        val label = last.title.ifBlank { last.url }
        val tile = layoutInflater.inflate(R.layout.item_tile, continueRow, false)
        tile.findViewById<TextView>(R.id.tileBadge).text = "▶"
        tile.findViewById<TextView>(R.id.tileTitle).text = label
        tile.contentDescription = label
        tile.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(120)
        )
        tile.setOnClickListener { openUrl(last.url, label) }
        attachFocusEffect(tile)
        continueRow.addView(tile)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event?.repeatCount == 0) {
            showHomeMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        UiDialogs.confirmExit(this)
    }

    private fun showHomeMenu() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_home_menu)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.45f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val settings = dialog.findViewById<TextView>(R.id.homeMenuSettings)
        val favorites = dialog.findViewById<TextView>(R.id.homeMenuFavorites)
        val help = dialog.findViewById<TextView>(R.id.homeMenuHelp)

        settings.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        favorites.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
        help.setOnClickListener {
            dialog.dismiss()
            UiDialogs.showHelp(this)
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        dialog.show()
        settings.requestFocus()
    }

    private fun refreshRecent() {
        recentRow.removeAllViews()
        val items = AppPrefs.recent(this)
        if (items.isEmpty()) {
            recentRow.addView(emptyHint(R.string.empty_recent))
            return
        }
        items.forEach { entry ->
            recentRow.addView(buildChip(entry, recentRow))
        }
    }

    private fun refreshFavorites() {
        favoritesRow.removeAllViews()
        val items = AppPrefs.favorites(this)
        if (items.isEmpty()) {
            favoritesRow.addView(emptyHint(R.string.empty_favorites))
            return
        }
        items.take(MAX_ROW_ITEMS).forEach { entry ->
            favoritesRow.addView(buildChip(entry, favoritesRow))
        }
    }

    private fun buildChip(entry: AppPrefs.Entry, parent: LinearLayout): TextView {
        val chip = layoutInflater.inflate(R.layout.item_chip, parent, false) as TextView
        val label = entry.title.ifBlank { entry.url }
        chip.text = label
        chip.contentDescription = label

        val lp = LinearLayout.LayoutParams(0, dp(76), 1f)
        lp.marginStart = dp(5)
        lp.marginEnd = dp(5)
        chip.layoutParams = lp

        chip.setOnClickListener { openUrl(entry.url, label) }
        attachFocusEffect(chip)
        return chip
    }

    private fun emptyHint(textRes: Int): TextView =
        TextView(this).apply {
            setText(textRes)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 22f
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }

    private fun attachFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
            v.elevation = if (hasFocus) dp(12).toFloat() else 0f
        }
    }

    private fun openUrl(url: String, title: String) {
        startActivity(BrowserActivity.intent(this, url, title))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLUMNS = 4
        private const val MAX_ROW_ITEMS = 5
    }
}
