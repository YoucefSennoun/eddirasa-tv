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

class HomeActivity : AppCompatActivity() {

    private lateinit var gridContainer: LinearLayout
    private var firstTile: View? = null

    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SearchActivity.EXTRA_QUERY_URL)?.let { url ->
                    openUrl(url)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_home)

        gridContainer = findViewById(R.id.gridContainer)
        buildGrid()

        if (!AppPrefs.helpShown(this)) {
            AppPrefs.setHelpShown(this)
            window.decorView.post { UiDialogs.showHelp(this) }
        }
    }

    override fun onResume() {
        super.onResume()
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
                ).apply { bottomMargin = dp(14) }
            }

            rowSections.forEach { section ->
                val title = getString(section.titleRes)
                val tile = layoutInflater.inflate(R.layout.item_tile, row, false)
                tile.findViewById<TextView>(R.id.tileBadge).text = section.badge
                tile.findViewById<TextView>(R.id.tileTitle).text = title
                tile.contentDescription = title

                val lp = LinearLayout.LayoutParams(0, dp(150), 1f)
                lp.marginStart = dp(7)
                lp.marginEnd = dp(7)
                tile.layoutParams = lp

                tile.setOnClickListener {
                    if (section.titleRes == R.string.section_search) {
                        searchLauncher.launch(SearchActivity.intent(this))
                    } else {
                        openUrl(section.url)
                    }
                }
                attachFocusEffect(tile)
                row.addView(tile)

                if (firstTile == null) firstTile = tile
            }

            gridContainer.addView(row)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event?.repeatCount == 0) {
            showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        UiDialogs.confirmExit(this)
    }

    private fun showMenu() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_home_menu)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.45f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.homeMenuSettings).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        dialog.findViewById<TextView>(R.id.homeMenuHelp).setOnClickListener {
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
        dialog.findViewById<TextView>(R.id.homeMenuSettings).requestFocus()
    }

    private fun attachFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
            v.elevation = if (hasFocus) dp(12).toFloat() else 0f
        }
    }

    private fun openUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url, ""))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLUMNS = 3
    }
}
