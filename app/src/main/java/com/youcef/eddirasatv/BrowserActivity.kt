package com.youcef.eddirasatv

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorOverlay: View
    private lateinit var retryButton: TextView
    private lateinit var errorMessage: TextView
    private lateinit var fullscreenContainer: ViewGroup

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var failedUrl: String? = null
    private var lastUrl: String? = null

    private var downloading = false
    private var downloadCancelled = false
    private var downloadDialog: Dialog? = null
    private var downloadConnection: HttpURLConnection? = null

    private val handler = Handler(Looper.getMainLooper())

    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SearchActivity.EXTRA_QUERY_URL)?.let { url ->
                    webView.loadUrl(url)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorOverlay = findViewById(R.id.errorOverlay)
        retryButton = findViewById(R.id.retryButton)
        errorMessage = findViewById(R.id.errorMessage)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        configureWebView(webView)

        retryButton.setOnClickListener {
            hideError()
            webView.loadUrl(failedUrl ?: webView.url ?: Sections.BASE_URL)
        }

        val startUrl = intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { Sections.BASE_URL }
        webView.loadUrl(startUrl)
        webView.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        target.setBackgroundColor(ContextCompat.getColor(this, R.color.background_dark))
        target.settings.apply {
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

        target.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    if (isInternal(uri)) {
                        if (isPdfUrl(uri) && request.isForMainFrame) {
                            startPdfDownload(uri.toString())
                            return true
                        }
                        return false
                    }
                }
                showExternalBlocked()
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (isBlockedAdScript(url)) {
                    return WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null && url.startsWith("http")) lastUrl = url
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
                    injectNavigation()
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
                    showError(R.string.error_message)
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
                progressBar.visibility = View.GONE
                failedUrl = error?.url
                showError(R.string.error_ssl)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                if (view == null || isFinishing || isDestroyed) return true
                val url = lastUrl ?: view.url ?: Sections.BASE_URL
                rebuildWebView(url)
                return true
            }
        }

        target.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            handleDownload(url, contentDisposition, mimeType, contentLength)
        }

        target.webChromeClient = object : WebChromeClient() {
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
                            if (isPdfUrl(uri)) {
                                startPdfDownload(uri.toString())
                            } else {
                                webView.loadUrl(uri.toString())
                            }
                        } else {
                            showExternalBlocked()
                        }
                        popup.post { popup.destroy() }
                        return true
                    }
                }
                popup.setDownloadListener { url, _, disposition, mime, length ->
                    handleDownload(url, disposition, mime, length)
                    popup.post { popup.destroy() }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popup
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.INVISIBLE
                progressBar.visibility = View.GONE
                view.requestFocus()
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        customView = null
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.requestFocus()
        try {
            customViewCallback?.onCustomViewHidden()
        } catch (ignored: Exception) {
        }
        customViewCallback = null
    }

    private fun isPdfUrl(uri: Uri): Boolean =
        uri.path?.lowercase(Locale.US)?.endsWith(".pdf") == true

    private fun handleDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val isPdf = mimeType?.lowercase(Locale.US) == "application/pdf" ||
            contentDisposition?.lowercase(Locale.US)?.contains(".pdf") == true ||
            isPdfUrl(Uri.parse(url))
        if (isPdf) {
            startPdfDownload(url, contentLength)
        } else {
            Toast.makeText(this, R.string.pdf_download_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun startPdfDownload(url: String, contentLengthHint: Long = -1L) {
        if (downloading) return
        downloading = true
        downloadCancelled = false

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_download)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val minWidth = (320 * resources.displayMetrics.density).toInt()
            val width = (resources.displayMetrics.widthPixels * 0.5f)
                .toInt().coerceAtLeast(minWidth)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val progressBar = dialog.findViewById<ProgressBar>(R.id.downloadProgress)
        val percentText = dialog.findViewById<TextView>(R.id.downloadPercent)
        progressBar.isIndeterminate = contentLengthHint <= 0L

        dialog.setOnCancelListener {
            downloadCancelled = true
            downloadConnection?.disconnect()
        }
        dialog.show()
        downloadDialog = dialog

        val handlerToUi = Handler(Looper.getMainLooper())
        val userAgent = webView.settings.userAgentString
        val cookie = CookieManager.getInstance().getCookie(url)

        val thread = Thread {
            val dir = File(cacheDir, "pdf").apply { mkdirs() }
            val target = uniquePdfFile(dir, buildPdfFileName(url))
            var partial: File? = null
            var connection: HttpURLConnection? = null
            try {
                val temp = File(dir, target.name + ".part")
                partial = temp

                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0")
                    cookie?.let { setRequestProperty("Cookie", it) }
                }
                downloadConnection = connection

                val response = connection.responseCode
                if (response !in 200..299) throw IOException("HTTP $response")

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: contentLengthHint
                handlerToUi.post {
                    if (dialog.isShowing && total > 0) {
                        progressBar.isIndeterminate = false
                        progressBar.progress = 0
                    }
                }

                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastUpdate = 0L
                        while (true) {
                            if (downloadCancelled) throw InterruptedException("cancelled")
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val now = SystemClock.uptimeMillis()
                            if (now - lastUpdate > 250L && total > 0) {
                                lastUpdate = now
                                val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                                handlerToUi.post {
                                    if (dialog.isShowing) {
                                        progressBar.isIndeterminate = false
                                        progressBar.progress = percent
                                        percentText.text = "$percent%"
                                    }
                                }
                            }
                        }
                    }
                }

                if (downloadCancelled) throw InterruptedException("cancelled")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                handlerToUi.post {
                    if (!isFinishing && !isDestroyed) {
                        dialog.dismiss()
                        downloading = false
                        downloadDialog = null
                        enforcePdfCacheLimit(target)
                        startActivity(PdfViewerActivity.intent(this, target.absolutePath))
                    }
                }
            } catch (e: Exception) {
                partial?.delete()
                handlerToUi.post {
                    if (!isFinishing && !isDestroyed) {
                        dialog.dismiss()
                        downloading = false
                        downloadDialog = null
                        if (!downloadCancelled) {
                            Toast.makeText(
                                this,
                                R.string.pdf_download_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } finally {
                downloadConnection = null
                connection?.disconnect()
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun buildPdfFileName(url: String): String {
        val raw = Uri.decode(Uri.parse(url).lastPathSegment ?: "document")
        val cleaned = raw
            .substringBefore('?')
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .trim('_', '.', ' ')
        val base = cleaned.ifBlank { "document" }.take(80)
        return if (base.lowercase(Locale.US).endsWith(".pdf")) base else "$base.pdf"
    }

    private fun uniquePdfFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.removeSuffix(".pdf")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem-$index.pdf")
            index++
        }
        return candidate
    }

    private fun enforcePdfCacheLimit(keep: File? = null) {
        val dir = File(cacheDir, "pdf")
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "pdf" }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_PDF_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total > MAX_PDF_CACHE_BYTES && file != keep) {
                val length = file.length()
                if (file.delete()) total -= length
            }
        }
    }

    private fun isInternal(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "eddirasa.com" || host.endsWith(".eddirasa.com")
    }

    private fun isBlockedAdScript(url: String): Boolean =
        url.contains("app-promo.js", ignoreCase = true)

    private fun showExternalBlocked() {
        Toast.makeText(this, R.string.external_link_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun showError(messageRes: Int) {
        errorOverlay.visibility = View.VISIBLE
        errorMessage.setText(messageRes)
        retryButton.requestFocus()
    }

    private fun rebuildWebView(url: String) {
        handler.removeCallbacksAndMessages(null)
        val old = webView
        (old.parent as? ViewGroup)?.removeView(old)
        old.stopLoading()
        old.webChromeClient = null
        old.webViewClient = WebViewClient()
        try {
            old.destroy()
        } catch (ignored: Exception) {
        }

        val root = findViewById<ViewGroup>(R.id.browserRoot)
        val fresh = WebView(this)
        fresh.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(fresh, 0)
        webView = fresh
        configureWebView(fresh)
        fresh.loadUrl(url)
        fresh.requestFocus()
        Toast.makeText(this, R.string.page_recovered, Toast.LENGTH_SHORT).show()
    }

    private fun trimWebViewCacheIfNeeded() {
        if (isFinishing) return
        try {
            val targets = listOf(
                File(cacheDir, "WebView"),
                File(File(applicationInfo.dataDir, "app_webview"), "Default/HTTP Cache")
            )
            val total = targets.sumOf { directorySize(it) }
            if (total > MAX_WEBVIEW_CACHE_BYTES) webView.clearCache(true)
        } catch (ignored: Exception) {
        }
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    private fun injectNavigation() {
        webView.evaluateJavascript(WebNav.BLOCK_SCRIPT, null)
        webView.evaluateJavascript(
            WebNav.SCRIPT + "\ntry{window.__ed_first&&window.__ed_first();}catch(e){}",
            null
        )
    }

    private fun runJs(body: String) {
        webView.evaluateJavascript("try{$body}catch(e){}", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        if (code == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
            showQuickMenu()
            return true
        }

        if (errorOverlay.visibility == View.VISIBLE) {
            return super.dispatchKeyEvent(event)
        }

        val direction = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                runJs("window.__ed_move && window.__ed_move('$direction')")
            }
            return true
        }

        val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (isOk) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                runJs("window.__ed_click && window.__ed_click()")
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun showQuickMenu() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_menu)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val minWidth = (320 * resources.displayMetrics.density).toInt()
            val width = (resources.displayMetrics.widthPixels * 0.5f)
                .toInt().coerceAtLeast(minWidth)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        fun action(block: () -> Unit): View.OnClickListener = View.OnClickListener {
            dialog.dismiss()
            block()
        }

        dialog.findViewById<TextView>(R.id.menuHome).setOnClickListener(action {
            finish()
        })
        dialog.findViewById<TextView>(R.id.menuReload).setOnClickListener(action {
            webView.reload()
        })
        dialog.findViewById<TextView>(R.id.menuZoomIn).setOnClickListener(action {
            changeTextZoom(10)
        })
        dialog.findViewById<TextView>(R.id.menuZoomOut).setOnClickListener(action {
            changeTextZoom(-10)
        })
        dialog.findViewById<TextView>(R.id.menuSearch).setOnClickListener(action {
            searchLauncher.launch(SearchActivity.intent(this))
        })
        dialog.findViewById<TextView>(R.id.menuHelp).setOnClickListener(action {
            UiDialogs.showHelp(this)
        })
        dialog.findViewById<TextView>(R.id.menuExit).setOnClickListener(action {
            finishAffinity()
        })

        dialog.show()
        dialog.findViewById<TextView>(R.id.menuHome).requestFocus()
    }

    private fun changeTextZoom(delta: Int) {
        val next = AppPrefs.textZoom(this) + delta
        AppPrefs.setTextZoom(this, next)
        webView.settings.textZoom = AppPrefs.textZoom(this)
        Toast.makeText(
            this,
            getString(R.string.menu_zoom_value, AppPrefs.textZoom(this)),
            Toast.LENGTH_SHORT
        ).show()
    }

    @Deprecated("Deprecated in API 33, matches targetSdk 29 behaviour")
    override fun onBackPressed() {
        if (customView != null) {
            exitFullscreen()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onPause() {
        webView.onPause()
        trimWebViewCacheIfNeeded()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        downloadCancelled = true
        downloadConnection?.disconnect()
        downloadDialog?.dismiss()
        downloadDialog = null
        handler.removeCallbacksAndMessages(null)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val MAX_PDF_CACHE_BYTES = 150L * 1024L * 1024L
        private const val MAX_WEBVIEW_CACHE_BYTES = 100L * 1024L * 1024L
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, BrowserActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
