package com.youcef.eddirasatv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorOverlay: View
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: TextView
    private var failedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorOverlay = findViewById(R.id.errorOverlay)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)

        configureWebView()

        retryButton.setOnClickListener {
            hideError()
            webView.loadUrl(failedUrl ?: webView.url ?: Sections.BASE_URL)
        }

        val startUrl = intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { Sections.BASE_URL }
        webView.loadUrl(startUrl)
        webView.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.background_dark))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = AppPrefs.textZoom(this@BrowserActivity)
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if ((scheme == "http" || scheme == "https") && isInternal(uri)) {
                    return false
                }
                showExternalBlocked()
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hideError()
                progressBar.progress = 0
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                if (errorOverlay.visibility != View.VISIBLE && url != null &&
                    url.startsWith("http")
                ) {
                    val title = view?.title?.takeIf { it.isNotBlank() } ?: url
                    AppPrefs.addRecent(this@BrowserActivity, url, title)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    failedUrl = request.url.toString()
                    showError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val popup = WebView(this@BrowserActivity)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        popupView: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        if (isInternal(uri)) {
                            webView.loadUrl(uri.toString())
                        } else {
                            showExternalBlocked()
                        }
                        popup.post { popup.destroy() }
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popup
                resultMsg?.sendToTarget()
                return true
            }
        }
    }

    private fun isInternal(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "eddirasa.com" || host.endsWith(".eddirasa.com")
    }

    private fun showExternalBlocked() {
        Toast.makeText(this, R.string.external_link_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun showError() {
        errorOverlay.visibility = View.VISIBLE
        retryButton.requestFocus()
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
            showMenu()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showMenu() {
        val options = arrayOf(
            getString(R.string.menu_zoom_in),
            getString(R.string.menu_zoom_out),
            getString(R.string.menu_zoom_reset),
            getString(R.string.menu_reload),
            getString(R.string.menu_home)
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.menu_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> changeTextZoom(10)
                    1 -> changeTextZoom(-10)
                    2 -> {
                        AppPrefs.setTextZoom(this, AppPrefs.DEFAULT_TEXT_ZOOM)
                        applyTextZoom()
                    }
                    3 -> webView.reload()
                    4 -> finish()
                }
            }
            .create()
        dialog.show()
        dialog.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun changeTextZoom(delta: Int) {
        val next = AppPrefs.textZoom(this) + delta
        AppPrefs.setTextZoom(this, next)
        applyTextZoom()
        Toast.makeText(
            this,
            getString(R.string.menu_zoom_value, AppPrefs.textZoom(this)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun applyTextZoom() {
        webView.settings.textZoom = AppPrefs.textZoom(this)
    }

    @Deprecated("Deprecated in API 33, matches targetSdk 29 behaviour")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, BrowserActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
