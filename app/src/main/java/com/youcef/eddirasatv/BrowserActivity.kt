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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var cursorView: CursorView

    private var failedUrl: String? = null
    private var lastUrl: String? = null

    private var downloading = false
    private var downloadCancelled = false
    private var downloadDialog: Dialog? = null
    private var downloadConnection: HttpURLConnection? = null

    private val handler = Handler(Looper.getMainLooper())
    private var heldDirection = 0
    private var holdStart = 0L

    private val moveRunnable = object : Runnable {
        override fun run() {
            if (heldDirection == 0) return
            val elapsed = SystemClock.uptimeMillis() - holdStart
            val speed = (12f + (elapsed - 300L).coerceAtLeast(0L) / 40f).coerceAtMost(60f)
            step(heldDirection, speed)
            handler.postDelayed(this, 40L)
        }
    }

    private val hideRunnable = Runnable { cursorView.visibility = View.INVISIBLE }

    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SearchActivity.EXTRA_QUERY_URL)?.let { url ->
                    webView.loadUrl(url)
                    showCursor()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorOverlay = findViewById(R.id.errorOverlay)
        retryButton = findViewById(R.id.retryButton)
        errorMessage = findViewById(R.id.errorMessage)
        cursorView = findViewById(R.id.cursorView)

        configureWebView(webView)

        retryButton.setOnClickListener {
            hideError()
            webView.loadUrl(failedUrl ?: webView.url ?: Sections.BASE_URL)
        }

        webView.post {
            if (cursorView.cx == 0f && cursorView.cy == 0f &&
                webView.width > 0 && webView.height > 0
            ) {
                cursorView.moveTo(webView.width / 2f, webView.height / 2f)
            }
            showCursor()
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
                        if (isPdfUrl(uri)) {
                            startPdfDownload(uri.toString())
                            return true
                        }
                        return false
                    }
                }
                showExternalBlocked()
                return true
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
        }
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

    private fun showExternalBlocked() {
        Toast.makeText(this, R.string.external_link_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun showError(messageRes: Int) {
        stopMove()
        cursorView.visibility = View.INVISIBLE
        errorOverlay.visibility = View.VISIBLE
        errorMessage.setText(messageRes)
        retryButton.requestFocus()
    }

    private fun rebuildWebView(url: String) {
        stopMove()
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
        showCursor()
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

    private fun showCursor() {
        if (errorOverlay.visibility == View.VISIBLE) return
        cursorView.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 4000L)
    }

    private fun startMove(direction: Int) {
        if (heldDirection == direction) return
        heldDirection = direction
        holdStart = SystemClock.uptimeMillis()
        handler.removeCallbacks(moveRunnable)
        handler.post(moveRunnable)
    }

    private fun stopMove() {
        heldDirection = 0
        handler.removeCallbacks(moveRunnable)
    }

    private fun step(direction: Int, speed: Float) {
        val width = webView.width.toFloat()
        val height = webView.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val margin = 20f * resources.displayMetrics.density
        val previousX = cursorView.cx
        val previousY = cursorView.cy

        var nextX = previousX
        var nextY = previousY
        when (direction) {
            KeyEvent.KEYCODE_DPAD_LEFT -> nextX -= speed
            KeyEvent.KEYCODE_DPAD_RIGHT -> nextX += speed
            KeyEvent.KEYCODE_DPAD_UP -> nextY -= speed
            KeyEvent.KEYCODE_DPAD_DOWN -> nextY += speed
        }

        val clampedX = nextX.coerceIn(margin, (width - margin).coerceAtLeast(margin))
        val clampedY = nextY.coerceIn(margin, (height - margin).coerceAtLeast(margin))
        cursorView.moveTo(clampedX, clampedY)

        val scrollStep = (speed * 1.5f).toInt()
        if (direction == KeyEvent.KEYCODE_DPAD_UP && previousY <= margin + 0.5f) {
            webView.scrollBy(0, -scrollStep)
        } else if (direction == KeyEvent.KEYCODE_DPAD_DOWN &&
            previousY >= height - margin - 0.5f
        ) {
            webView.scrollBy(0, scrollStep)
        }

        scheduleHide()
    }

    private fun clickAtCursor() {
        val now = SystemClock.uptimeMillis()
        val x = cursorView.cx
        val y = cursorView.cy
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 50L, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Suppress("DEPRECATION")
    private fun snapCursor() {
        val factor = resources.displayMetrics.density * webView.scale
        if (factor <= 0f) return
        val cssX = cursorView.cx / factor
        val cssY = cursorView.cy / factor
        val script = """
            (function(){
              var x = %1${'$'}s, y = %2${'$'}s, off = 32;
              var points = [[x,y],[x+off,y],[x-off,y],[x,y+off],[x,y-off]];
              var sel = 'a,button,input,textarea,select,label,[role="button"],[onclick]';
              var best = null, bestDistance = 1e9;
              for (var i = 0; i < points.length; i++) {
                var el = document.elementFromPoint(points[i][0], points[i][1]);
                if (!el || !el.closest) continue;
                var target = el.closest(sel);
                if (!target) continue;
                var r = target.getBoundingClientRect();
                if (r.width <= 0 || r.height <= 0) continue;
                var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
                var d = Math.sqrt((cx - x) * (cx - x) + (cy - y) * (cy - y));
                if (d < bestDistance) { bestDistance = d; best = [cx, cy, d]; }
              }
              return best ? best.join(',') : '';
            })()
        """.trimIndent()

        webView.evaluateJavascript(String.format(Locale.US, script, cssX, cssY)) { result ->
            val raw = result?.trim()?.removeSurrounding("\"") ?: return@evaluateJavascript
            if (raw.isBlank()) return@evaluateJavascript
            val parts = raw.split(",")
            if (parts.size != 3) return@evaluateJavascript
            val centerX = parts[0].toFloatOrNull() ?: return@evaluateJavascript
            val centerY = parts[1].toFloatOrNull() ?: return@evaluateJavascript
            val distance = parts[2].toFloatOrNull() ?: return@evaluateJavascript

            val physicalX = centerX * factor
            val physicalY = centerY * factor
            val physicalDistance = distance * factor
            if (physicalDistance > 40f) return@evaluateJavascript

            val width = webView.width.toFloat()
            val height = webView.height.toFloat()
            if (width <= 0f || height <= 0f) return@evaluateJavascript
            val margin = 20f * resources.displayMetrics.density
            cursorView.moveTo(
                physicalX.coerceIn(margin, (width - margin).coerceAtLeast(margin)),
                physicalY.coerceIn(margin, (height - margin).coerceAtLeast(margin))
            )
        }
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

        val direction = directionOf(code)
        val isClick = code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER

        if (direction != 0) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    showCursor()
                    startMove(direction)
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    stopMove()
                    snapCursor()
                    scheduleHide()
                    return true
                }
            }
        }

        if (isClick) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    showCursor()
                    clickAtCursor()
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    scheduleHide()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun directionOf(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> code
        else -> 0
    }

    private fun showQuickMenu() {
        stopMove()
        handler.removeCallbacks(hideRunnable)
        cursorView.visibility = View.INVISIBLE

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

        val currentUrl = webView.url
        val favoriteItem = dialog.findViewById<TextView>(R.id.menuFavorite)
        val alreadyFavorite = currentUrl != null && AppPrefs.isFavorite(this, currentUrl)
        favoriteItem.text = getString(
            if (alreadyFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite
        )

        fun action(block: () -> Unit): View.OnClickListener = View.OnClickListener {
            dialog.dismiss()
            block()
        }

        dialog.findViewById<TextView>(R.id.menuHome).setOnClickListener(action {
            finish()
        })
        dialog.findViewById<TextView>(R.id.menuReload).setOnClickListener(action {
            webView.reload()
            showCursor()
        })
        dialog.findViewById<TextView>(R.id.menuZoomIn).setOnClickListener(action {
            changeTextZoom(10)
            showCursor()
        })
        dialog.findViewById<TextView>(R.id.menuZoomOut).setOnClickListener(action {
            changeTextZoom(-10)
            showCursor()
        })
        dialog.findViewById<TextView>(R.id.menuSearch).setOnClickListener(action {
            searchLauncher.launch(SearchActivity.intent(this))
        })
        favoriteItem.setOnClickListener(action {
            val url = webView.url
            if (url != null) {
                val title = webView.title?.takeIf { it.isNotBlank() } ?: url
                val added = AppPrefs.toggleFavorite(this, url, title)
                Toast.makeText(
                    this,
                    if (added) R.string.favorite_added else R.string.favorite_removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        dialog.findViewById<TextView>(R.id.menuExit).setOnClickListener(action {
            finishAffinity()
        })

        dialog.setOnDismissListener {
            if (!isFinishing) showCursor()
        }
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onPause() {
        stopMove()
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
