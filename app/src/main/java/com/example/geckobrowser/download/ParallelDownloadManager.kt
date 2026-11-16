package com.example.geckobrowser.download

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.*
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Range-based segmented downloader. Server must advertise byte ranges; otherwise a single stream is used. */
class ParallelDownloadManager(private val context: Context) {
    data class Download(val url: String, val fileName: String)
    private val paused = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    fun hasLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT > 28 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun cancel() { cancelled.set(true) }

    suspend fun download(item: Download, onProgress: (Long, Long) -> Unit): java.io.File = withContext(Dispatchers.IO) {
        require(hasLegacyStoragePermission()) { "WRITE_EXTERNAL_STORAGE permission is required on Android 9 when writing to public storage" }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val target = java.io.File(dir, item.fileName)
        val head = (URL(item.url).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; connectTimeout = 15000; readTimeout = 15000 }
        val length = head.getHeaderFieldLong("Content-Length", -1L)
        val ranges = head.getHeaderField("Accept-Ranges")?.equals("bytes", true) == true
        head.disconnect()
        if (length <= 0 || !ranges) return@withContext singleStream(item.url, target, onProgress)
        RandomAccessFile(target, "rw").use { it.setLength(length) }
        val workers = minOf(4, length.toInt().coerceAtLeast(1))
        val chunk = (length + workers - 1) / workers
        val done = java.util.concurrent.atomic.AtomicLong(0)
        coroutineScope {
            (0 until workers).map { index -> async(Dispatchers.IO) {
                val start = index * chunk; val end = minOf(length - 1, start + chunk - 1)
                if (start > end) return@async
                val conn = URL(item.url).openConnection() as HttpURLConnection
                conn.setRequestProperty("Range", "bytes=$start-$end"); conn.connectTimeout = 20000; conn.readTimeout = 30000
                try {
                    require(conn.responseCode == 206) { "Server did not honor HTTP Range requests" }
                    conn.inputStream.use { input -> RandomAccessFile(target, "rw").use { out ->
                        out.seek(start); val buffer = ByteArray(64 * 1024)
                        while (true) {
                            while (paused.get()) { ensureActive(); delay(200) }
                            ensureActive(); check(!cancelled.get()) { "Download cancelled" }
                            val n = input.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n); val total = done.addAndGet(n.toLong()); onProgress(total, length)
                        }
                    } }
                } finally { conn.disconnect() }
            } }.awaitAll()
        }
        target
    }

    private suspend fun singleStream(url: String, target: java.io.File, progress: (Long, Long) -> Unit): java.io.File {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000; conn.readTimeout = 30000
        try { conn.inputStream.use { input -> target.outputStream().use { out ->
            val total = conn.contentLengthLong; var read = 0L; val buf = ByteArray(64 * 1024)
            while (true) { while (paused.get()) delay(200); ensureActive(); check(!cancelled.get())
                val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); read += n; progress(read, total)
            }
        } } } finally { conn.disconnect() }
        return target
    }
}
