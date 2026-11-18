package com.youcef.eddirasatv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object PdfOpener {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    private const val MAX_REDIRECTS = 8
    private const val MAX_CACHE_BYTES = 400L * 1024 * 1024

    private val mainHandler = Handler(Looper.getMainLooper())

    private class Canceller {
        @Volatile
        var cancelled = false

        @Volatile
        var connection: HttpURLConnection? = null
    }

    fun open(activity: Activity, url: String, displayName: String) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_download)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (activity.resources.displayMetrics.widthPixels * 0.5f).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val progressBar = dialog.findViewById<ProgressBar>(R.id.downloadProgress)
        val percentText = dialog.findViewById<TextView>(R.id.downloadPercent)
        progressBar.isIndeterminate = true
        dialog.show()

        val canceller = Canceller()
        dialog.setOnCancelListener {
            canceller.cancelled = true
            try {
                canceller.connection?.disconnect()
            } catch (ignored: Exception) {
            }
        }

        val thread = Thread {
            try {
                val file = download(activity, url, canceller) { percent ->
                    mainHandler.post {
                        if (dialog.isShowing) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = percent
                            percentText.text = "$percent%"
                        }
                    }
                }
                if (canceller.cancelled) return@Thread
                mainHandler.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialog.dismiss()
                        activity.startActivity(PdfViewerActivity.intent(activity, file.absolutePath))
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialog.dismiss()
                        if (!canceller.cancelled) {
                            Toast.makeText(activity, R.string.pdf_download_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun download(
        activity: Activity,
        url: String,
        canceller: Canceller,
        onProgress: (Int) -> Unit
    ): File {
        val dir = File(activity.cacheDir, "pdf").apply { mkdirs() }
        enforceCacheLimit(dir)
        val target = File(dir, hash(url) + ".pdf")
        if (target.exists() && target.length() > 0) return target

        val cookies = LinkedHashMap<String, String>()
        var currentUrl = url
        var lastError: Exception? = null

        for (hop in 0 until MAX_REDIRECTS) {
            if (canceller.cancelled) throw IOException("cancelled")
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/pdf,application/octet-stream,*/*")
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            canceller.connection = connection
            try {
                val code = connection.responseCode
                connection.headerFields?.forEach { (name, values) ->
                    if (name != null && name.equals("Set-Cookie", true) && values != null) {
                        for (value in values) storeCookie(cookies, value)
                    }
                }
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("redirect without location")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")

                val total = connection.contentLengthLong
                val temp = File(dir, target.name + ".part")
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastUpdate = 0L
                        while (true) {
                            if (canceller.cancelled) throw IOException("cancelled")
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200L) {
                                    lastUpdate = now
                                    onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                                }
                            }
                        }
                    }
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return target
            } catch (e: Exception) {
                lastError = e
            } finally {
                connection.disconnect()
                canceller.connection = null
            }
        }
        throw lastError ?: IOException("download failed")
    }

    private fun storeCookie(store: MutableMap<String, String>, header: String) {
        val pair = header.substringBefore(";")
        val eq = pair.indexOf('=')
        if (eq > 0) {
            store[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
        }
    }

    private fun enforceCacheLimit(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
