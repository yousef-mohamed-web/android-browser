package com.example.geckobrowser.download

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads only HTTPS resources into this app's private external-files directory.
 * Redirects are rejected so a remote server cannot silently downgrade or redirect
 * a download to an unexpected scheme/host.
 */
class ParallelDownloadManager(private val context: Context) {
    data class Download(val url: String, val fileName: String)
    private val paused = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun cancel() { cancelled.set(true) }

    private fun safeUrl(raw: String): URL {
        val parsed = URL(raw)
        require(parsed.protocol.equals("https", true)) { "Only HTTPS downloads are permitted" }
        require(parsed.userInfo == null) { "Credentials in download URLs are not permitted" }
        return parsed
    }

    private fun safeFileName(raw: String): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid download filename" }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid download filename" }
        return name.take(180)
    }

    private fun connection(url: URL, method: String = "GET"): HttpURLConnection {
        val conn = safeUrl(url.toString()).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept-Encoding", "identity")
        return conn
    }

    suspend fun download(item: Download, onProgress: (Long, Long) -> Unit): java.io.File = withContext(Dispatchers.IO) {
        cancelled.set(false)
        val source = safeUrl(item.url)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) check(dir.mkdirs()) { "Could not create private downloads directory" }
        val target = java.io.File(dir, safeFileName(item.fileName)).canonicalFile
        require(target.parentFile?.canonicalFile == dir.canonicalFile) { "Invalid download path" }

        val head = connection(source, "HEAD")
        val length: Long
        val ranges: Boolean
        try {
            val code = head.responseCode
            require(code in 200..299) { "Download server returned HTTP $code; redirects are not followed" }
            length = head.getHeaderFieldLong("Content-Length", -1L)
            ranges = head.getHeaderField("Accept-Ranges")?.equals("bytes", true) == true
        } finally { head.disconnect() }

        if (length <= 0 || !ranges) return@withContext singleStream(source, target, onProgress)
        require(length <= Int.MAX_VALUE.toLong() * 4L) { "Download is too large for segmented mode" }
        RandomAccessFile(target, "rw").use { it.setLength(length) }
        val workers = minOf(4, ((length + 1) / 2).toInt().coerceAtLeast(1))
        val chunk = (length + workers - 1) / workers
        val done = java.util.concurrent.atomic.AtomicLong(0)
        try {
            coroutineScope {
                (0 until workers).map { index -> async(Dispatchers.IO) {
                    val start = index * chunk
                    val end = minOf(length - 1, start + chunk - 1)
                    if (start > end) return@async
                    val conn = connection(source)
                    conn.setRequestProperty("Range", "bytes=$start-$end")
                    try {
                        require(conn.responseCode == 206) { "Server did not honor HTTP Range requests" }
                        val expected = end - start + 1
                        conn.inputStream.use { input -> RandomAccessFile(target, "rw").use { out ->
                            out.seek(start)
                            val buffer = ByteArray(64 * 1024)
                            var segmentRead = 0L
                            while (true) {
                                while (paused.get()) { currentCoroutineContext().ensureActive(); delay(200) }
                                currentCoroutineContext().ensureActive()
                                check(!cancelled.get()) { "Download cancelled" }
                                val n = input.read(buffer)
                                if (n < 0) break
                                segmentRead += n
                                require(segmentRead <= expected) { "Server sent more data than requested" }
                                out.write(buffer, 0, n)
                                onProgress(done.addAndGet(n.toLong()), length)
                            }
                            require(segmentRead == expected) { "Incomplete download segment" }
                        } }
                    } finally { conn.disconnect() }
                } }.awaitAll()
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        target
    }

    private suspend fun singleStream(url: URL, target: java.io.File, progress: (Long, Long) -> Unit): java.io.File {
        val conn = connection(url)
        try {
            val code = conn.responseCode
            require(code in 200..299) { "Download server returned HTTP $code; redirects are not followed" }
            conn.inputStream.use { input -> target.outputStream().use { out ->
                val total = conn.contentLengthLong
                var read = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    while (paused.get()) { currentCoroutineContext().ensureActive(); delay(200) }
                    currentCoroutineContext().ensureActive()
                    check(!cancelled.get()) { "Download cancelled" }
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    read += n
                    progress(read, total)
                }
            } }
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally { conn.disconnect() }
        return target
    }
}
