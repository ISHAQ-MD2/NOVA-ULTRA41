package com.nova.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.URLUtil
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

enum class DlStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, UNSUPPORTED }
enum class DlType { DIRECT, HLS }

data class HlsQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long,
    val uri: String
)

data class DownloadTask(
    val id: String,
    var url: String,
    var title: String,
    var filePath: String = "",
    var contentUri: String = "",
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var status: DlStatus = DlStatus.QUEUED,
    var type: DlType = DlType.DIRECT,
    var error: String = "",
    var nextSegment: Int = 0,
    var totalSegments: Int = 0,
    var selectedQuality: String = "Auto",
    var selectedStreamUrl: String = "",
    @Volatile var speedBytesPerSec: Long = 0L,
    @Volatile var etaSeconds: Long = -1L,
    @Volatile var availableQualities: List<HlsQuality> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Advanced Resumable Download Engine for NOVA:
 *  - Default output path: /storage/emulated/0/NOVA-FILES/download/ with Scoped Storage MediaStore fallback on Android 10+
 *  - Pre-fetch / live total size (MB/GB), real-time speed (MB/s), and ETA remaining calculation
 *  - HLS (.m3u8) Master Playlist parser (#EXT-X-STREAM-INF) with Auto 360p/480p default & manual resolution picker
 *  - Progressive flushing on disk so partial video files can be played immediately in VideoPlayerActivity while downloading
 *  - Explicitly rejects YouTube URLs as unsupported (no DRM circumvention)
 */
object DownloadVault {
    private const val PREF_FILE = "nova_downloads"
    private const val KEY_LIST = "tasks_v1"
    const val TARGET_DISPLAY_DIR = "/storage/emulated/0/NOVA-FILES/download/"

    val tasks = CopyOnWriteArrayList<DownloadTask>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val probePool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loaded = false
    @Volatile var onUpdate: (() -> Unit)? = null

    fun isYouTubeUrl(url: String): Boolean {
        val h = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        return h.contains("youtube.com") || h.contains("youtu.be") ||
            h.contains("youtube-nocookie.com") || h.contains("googlevideo.com")
    }

    /**
     * Resolves /storage/emulated/0/NOVA-FILES/download/ when permitted (Android <= 9 or MANAGE_EXTERNAL_STORAGE),
     * otherwise uses Scoped Storage Downloads/NOVA-FILES/download/ or app-scoped storage (with MediaStore export).
     */
    fun outputDir(ctx: Context): File {
        val primary = File(Environment.getExternalStorageDirectory(), "NOVA-FILES/download")
        if (canWriteDir(primary)) return primary

        val pubDl = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NOVA-FILES/download")
        if (canWriteDir(pubDl)) return pubDl

        return File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NOVA-FILES/download").apply { mkdirs() }
    }

    private fun canWriteDir(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            if (!dir.canWrite()) return false
            val probe = File(dir, ".nv_probe")
            FileOutputStream(probe).use { it.write(1) }
            probe.delete()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Copies completed file to Scoped Storage MediaStore Downloads/NOVA-FILES/download on Android 10+ when direct path wasn't writable. */
    private fun mirrorToMediaStoreIfNeeded(ctx: Context, file: File, mimeHint: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        val targetPrimaryPrefix = File(Environment.getExternalStorageDirectory(), "NOVA-FILES/download").absolutePath
        if (file.absolutePath.startsWith(targetPrimaryPrefix)) {
            try { MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), null, null) } catch (_: Throwable) {}
            return ""
        }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeHint.ifBlank { guessMime(file.name) })
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NOVA-FILES/download")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return ""
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            uri.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".ts", true) -> "video/mp2t"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".apk", true) -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val raw = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(KEY_LIST, "[]") ?: "[]"
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val st = try { DlStatus.valueOf(o.optString("status", "FAILED")) } catch (_: Exception) { DlStatus.FAILED }
                    val normalizedSt = if (st == DlStatus.RUNNING || st == DlStatus.QUEUED) DlStatus.PAUSED else st
                    tasks.add(
                        DownloadTask(
                            id = o.optString("id"),
                            url = o.optString("url"),
                            title = o.optString("title"),
                            filePath = o.optString("filePath"),
                            contentUri = o.optString("contentUri"),
                            totalBytes = o.optLong("totalBytes"),
                            downloadedBytes = o.optLong("downloadedBytes"),
                            status = normalizedSt,
                            type = try { DlType.valueOf(o.optString("type", "DIRECT")) } catch (_: Exception) { DlType.DIRECT },
                            error = o.optString("error"),
                            nextSegment = o.optInt("nextSegment"),
                            totalSegments = o.optInt("totalSegments"),
                            selectedQuality = o.optString("selectedQuality", "Auto"),
                            selectedStreamUrl = o.optString("selectedStreamUrl", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun persist(ctx: Context) {
        val arr = JSONArray()
        for (t in tasks.takeLast(100)) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("url", t.url)
                    .put("title", t.title)
                    .put("filePath", t.filePath)
                    .put("contentUri", t.contentUri)
                    .put("totalBytes", t.totalBytes)
                    .put("downloadedBytes", t.downloadedBytes)
                    .put("status", t.status.name)
                    .put("type", t.type.name)
                    .put("error", t.error)
                    .put("nextSegment", t.nextSegment)
                    .put("totalSegments", t.totalSegments)
                    .put("selectedQuality", t.selectedQuality)
                    .put("selectedStreamUrl", t.selectedStreamUrl)
                    .put("createdAt", t.createdAt)
            )
        }
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun notifyUi() {
        main.post { try { onUpdate?.invoke() } catch (_: Throwable) {} }
    }

    private fun sanitize(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimStart('.')
        return clean.ifEmpty { "nova_media_${System.currentTimeMillis() % 100000}.mp4" }.take(110)
    }

    private fun uniqueFile(dir: File, rawName: String): File {
        var f = File(dir, rawName)
        if (!f.exists()) return f
        val dot = rawName.lastIndexOf('.')
        val base = if (dot > 0) rawName.substring(0, dot) else rawName
        val ext = if (dot > 0) rawName.substring(dot) else ""
        var idx = 1
        while (f.exists() && idx < 500) {
            f = File(dir, "$base-$idx$ext")
            idx++
        }
        return f
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bps: Long): String {
        if (bps <= 0L) return "--"
        val mb = 1024.0 * 1024.0
        return if (bps >= mb) String.format(Locale.US, "%.1f MB/s", bps / mb)
        else String.format(Locale.US, "%.0f KB/s", bps / 1024.0)
    }

    fun formatEta(sec: Long): String {
        if (sec < 0L) return "Calculating..."
        if (sec < 60L) return "${sec.coerceAtLeast(1)}s remaining"
        val mins = sec / 60L
        val remS = sec % 60L
        if (mins < 60L) return if (mins == 1L) "1 min ${remS}s remaining" else "$mins mins remaining"
        val hrs = mins / 60L
        val remM = mins % 60L
        return "${hrs}h ${remM}m remaining"
    }

    /** Probes direct file size and/or HLS quality variants before starting download. */
    fun probeUrl(url: String, onDone: (totalBytes: Long, qualities: List<HlsQuality>) -> Unit) {
        probePool.execute {
            var size = -1L
            var quals = emptyList<HlsQuality>()
            try {
                if (url.lowercase().contains(".m3u8")) {
                    quals = parseHlsQualities(url)
                } else {
                    size = fetchContentLength(url)
                }
            } catch (_: Throwable) {}
            main.post { onDone(size, quals) }
        }
    }

    private fun fetchContentLength(url: String): Long {
        return try {
            val http = Net.client(readTimeoutSec = 15)
            val headReq = Request.Builder().url(url).head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                .build()
            http.newCall(headReq).execute().use { r ->
                val len = r.header("Content-Length")?.toLongOrNull() ?: -1L
                if (len > 0L) return len
            }
            val rangeReq = Request.Builder().url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                .build()
            http.newCall(rangeReq).execute().use { r ->
                val cr = r.header("Content-Range") ?: ""
                val total = Regex("/(\\d+)$").find(cr)?.groupValues?.get(1)?.toLongOrNull()
                total ?: (r.header("Content-Length")?.toLongOrNull() ?: -1L)
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    /** Parses #EXT-X-STREAM-INF lines from an HLS master playlist into selectable quality options. */
    fun parseHlsQualities(masterUrl: String): List<HlsQuality> {
        val http = Net.client(readTimeoutSec = 20)
        val req = Request.Builder().url(masterUrl).header("User-Agent", "Mozilla/5.0").build()
        val playlist = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            r.body?.string() ?: ""
        }
        if (!playlist.contains("#EXT-X-STREAM-INF")) return emptyList()
        val lines = playlist.lines()
        val found = ArrayList<HlsQuality>()
        val seenUri = HashSet<String>()
        var i = 0
        while (i < lines.size) {
            val ln = lines[i].trim()
            if (ln.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(ln)
                val w = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val h = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val bw = Regex("BANDWIDTH=(\\d+)").find(ln)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val nameAttr = Regex("""NAME=["']([^"']+)["']""").find(ln)?.groupValues?.get(1)
                var nextIdx = i + 1
                var nextUrl = ""
                while (nextIdx < lines.size) {
                    val cand = lines[nextIdx].trim()
                    if (cand.isNotEmpty() && !cand.startsWith("#")) {
                        nextUrl = URL(URL(masterUrl), cand).toString()
                        break
                    }
                    nextIdx++
                }
                if (nextUrl.isNotEmpty() && seenUri.add(nextUrl)) {
                    val label = when {
                        h > 0 -> "${h}p"
                        !nameAttr.isNullOrBlank() -> nameAttr
                        bw > 0 -> "${bw / 1000} kbps"
                        else -> "Stream ${found.size + 1}"
                    }
                    found.add(HlsQuality(label, w, h, bw, nextUrl))
                }
            }
            i++
        }
        return found.sortedWith(compareByDescending<HlsQuality> { it.height }.thenByDescending { it.bandwidth })
    }

    /** Auto-selects 480p or 360p by default if no preference is given, falling back to closest variant <= 480p or lowest. */
    fun pickAutoQuality(qualities: List<HlsQuality>): HlsQuality? {
        if (qualities.isEmpty()) return null
        qualities.firstOrNull { it.height in 460..490 }?.let { return it }
        qualities.firstOrNull { it.height in 340..380 }?.let { return it }
        qualities.filter { it.height in 1..480 }.maxByOrNull { it.height }?.let { return it }
        return qualities.minByOrNull { it.bandwidth } ?: qualities.first()
    }

    fun enqueue(
        ctx: Context,
        rawUrl: String,
        titleHint: String = "",
        chosenQuality: HlsQuality? = null,
        preProbedBytes: Long = 0L
    ): DownloadTask {
        init(ctx)
        val app = ctx.applicationContext
        val url = rawUrl.trim()
        if (isYouTubeUrl(url)) {
            val t = DownloadTask(
                id = UUID.randomUUID().toString(),
                url = url,
                title = titleHint.ifBlank { "YouTube Video" },
                status = DlStatus.UNSUPPORTED,
                error = "YouTube downloads are unsupported in this app."
            )
            tasks.add(0, t)
            persist(app)
            notifyUi()
            return t
        }
        val guessed = sanitize(titleHint.ifBlank { URLUtil.guessFileName(url, null, null) })
        val isHls = url.lowercase().contains(".m3u8")
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = url,
            title = guessed,
            totalBytes = preProbedBytes.coerceAtLeast(0L),
            status = DlStatus.QUEUED,
            type = if (isHls) DlType.HLS else DlType.DIRECT,
            selectedQuality = chosenQuality?.label ?: if (isHls) "Auto (480p/360p)" else "Direct",
            selectedStreamUrl = chosenQuality?.uri ?: ""
        )
        tasks.add(0, task)
        persist(app)
        notifyUi()
        startWorker(app, task)
        return task
    }

    fun changeHlsQuality(ctx: Context, id: String, quality: HlsQuality) {
        init(ctx)
        val app = ctx.applicationContext
        val t = tasks.firstOrNull { it.id == id } ?: return
        pausedIds.add(id)
        t.selectedQuality = quality.label
        t.selectedStreamUrl = quality.uri
        t.nextSegment = 0
        t.downloadedBytes = 0L
        if (t.filePath.isNotBlank()) try { File(t.filePath).delete() } catch (_: Exception) {}
        t.filePath = ""
        t.error = ""
        t.status = DlStatus.QUEUED
        persist(app); notifyUi()
        pausedIds.remove(id)
        startWorker(app, t)
    }

    fun pause(ctx: Context, id: String) {
        init(ctx)
        pausedIds.add(id)
        tasks.firstOrNull { it.id == id }?.let {
            if (it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED) {
                it.status = DlStatus.PAUSED
                it.speedBytesPerSec = 0L
                it.etaSeconds = -1L
                persist(ctx.applicationContext)
                notifyUi()
            }
        }
    }

    fun resume(ctx: Context, id: String) {
        init(ctx)
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.status == DlStatus.UNSUPPORTED || t.status == DlStatus.DONE) return
        pausedIds.remove(id)
        cancelledIds.remove(id)
        t.error = ""
        t.status = DlStatus.QUEUED
        persist(ctx.applicationContext)
        notifyUi()
        startWorker(ctx.applicationContext, t)
    }

    fun delete(ctx: Context, id: String, deleteFile: Boolean = true) {
        init(ctx)
        cancelledIds.add(id)
        pausedIds.add(id)
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (deleteFile && t.filePath.isNotBlank()) {
            try { File(t.filePath).delete() } catch (_: Exception) {}
        }
        if (deleteFile && t.contentUri.isNotBlank()) {
            try { ctx.contentResolver.delete(Uri.parse(t.contentUri), null, null) } catch (_: Exception) {}
        }
        tasks.remove(t)
        persist(ctx.applicationContext)
        notifyUi()
    }

    private fun startWorker(app: Context, task: DownloadTask) {
        pool.execute { runTask(app, task) }
    }

    private fun runTask(app: Context, task: DownloadTask) {
        try {
            if (isYouTubeUrl(task.url)) {
                task.status = DlStatus.UNSUPPORTED
                task.error = "YouTube downloads are unsupported in this app."
                persist(app); notifyUi(); return
            }
            pausedIds.remove(task.id)
            task.status = DlStatus.RUNNING
            task.error = ""
            persist(app); notifyUi()

            val resolvedUrl = maybeExtractPublicSocialVideo(task.url)
            if (resolvedUrl == null) {
                task.status = DlStatus.FAILED
                task.error = "Could not extract a public video stream (page may require login or DRM)."
                persist(app); notifyUi(); return
            }
            if (resolvedUrl != task.url) {
                task.url = resolvedUrl
                if (resolvedUrl.lowercase().contains(".m3u8")) task.type = DlType.HLS
            }

            if (task.type == DlType.HLS || task.url.lowercase().contains(".m3u8")) {
                task.type = DlType.HLS
                downloadHls(app, task)
            } else {
                if (task.totalBytes <= 0L) {
                    val probed = fetchContentLength(task.url)
                    if (probed > 0L) {
                        task.totalBytes = probed
                        notifyUi()
                    }
                }
                downloadDirect(app, task)
            }
        } catch (e: Exception) {
            if (task.id in cancelledIds) return
            task.speedBytesPerSec = 0L
            task.etaSeconds = -1L
            if (task.id in pausedIds) {
                task.status = DlStatus.PAUSED
            } else {
                task.status = DlStatus.FAILED
                task.error = e.message ?: "Download failed"
            }
            persist(app); notifyUi()
        }
    }

    private fun maybeExtractPublicSocialVideo(url: String): String? {
        val h = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        val isSocialPage = (h.contains("tiktok.com") || h.contains("instagram.com")) &&
            !url.lowercase().contains(".mp4") && !url.lowercase().contains(".m3u8")
        if (!isSocialPage) return url

        val http = Net.client(readTimeoutSec = 25)
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()
        http.newCall(req).execute().use { resp ->
            val ct = resp.header("Content-Type")?.lowercase() ?: ""
            if (ct.startsWith("video/")) return url
            val html = resp.body?.string() ?: ""
            val m1 = Regex("""<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video(?::secure_url)?["']""", RegexOption.IGNORE_CASE).find(html)
            val found = m1?.groupValues?.get(1)?.replace("&amp;", "&")
            if (!found.isNullOrBlank() && found.startsWith("http")) return found
        }
        return null
    }

    private fun downloadDirect(app: Context, task: DownloadTask) {
        val dir = outputDir(app)
        val targetFile = if (task.filePath.isNotBlank()) File(task.filePath) else uniqueFile(dir, sanitize(task.title)).also {
            task.filePath = it.absolutePath
        }
        val existing = if (targetFile.exists()) targetFile.length() else 0L
        task.downloadedBytes = existing

        val http = Net.client(readTimeoutSec = 60)
        val rb = Request.Builder().url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
        try {
            val ck = CookieManager.getInstance().getCookie(task.url)
            if (!ck.isNullOrBlank()) rb.header("Cookie", ck)
        } catch (_: Throwable) {}
        if (existing > 0L) rb.header("Range", "bytes=$existing-")

        http.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 416 && existing > 0L) {
                task.status = DlStatus.DONE
                task.totalBytes = existing
                task.speedBytesPerSec = 0L
                task.etaSeconds = 0L
                persist(app); notifyUi(); return
            }
            if (!resp.isSuccessful) {
                task.status = DlStatus.FAILED
                task.error = "HTTP ${resp.code}"
                persist(app); notifyUi(); return
            }
            val ct = resp.header("Content-Type")?.lowercase() ?: ""
            if (ct.contains("mpegurl") || ct.contains("m3u8")) {
                task.type = DlType.HLS
                resp.close()
                downloadHls(app, task)
                return
            }

            val append = resp.code == 206 && existing > 0L
            val contentLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val resolvedTotal = when {
                append && contentLen > 0 -> existing + contentLen
                contentLen > 0 -> contentLen
                else -> task.totalBytes
            }
            if (resolvedTotal > 0L) task.totalBytes = resolvedTotal
            if (!append && existing > 0L) task.downloadedBytes = 0L
            if (!task.title.contains('.') && ct.contains("video/mp4")) task.title += ".mp4"

            val body = resp.body ?: throw IllegalStateException("Empty body")
            val buf = ByteArray(16 * 1024)
            var lastUi = SystemClock.elapsedRealtime()
            var windowBytes = 0L
            var lastFlushBytes = 0L

            FileOutputStream(targetFile, append).use { fos ->
                body.byteStream().use { inp ->
                    while (true) {
                        if (task.id in cancelledIds || task.id in pausedIds) {
                            fos.flush()
                            task.status = DlStatus.PAUSED
                            task.speedBytesPerSec = 0L
                            task.etaSeconds = -1L
                            persist(app); notifyUi(); return
                        }
                        val n = inp.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        task.downloadedBytes += n
                        windowBytes += n
                        lastFlushBytes += n
                        if (lastFlushBytes >= 128 * 1024L) {
                            fos.flush()
                            lastFlushBytes = 0L
                        }
                        val now = SystemClock.elapsedRealtime()
                        val elapsedMs = now - lastUi
                        if (elapsedMs >= 500L) {
                            val instantBps = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                            task.speedBytesPerSec = if (task.speedBytesPerSec > 0L) {
                                ((task.speedBytesPerSec * 6L + instantBps * 4L) / 10L)
                            } else instantBps
                            task.etaSeconds = if (task.totalBytes > task.downloadedBytes && task.speedBytesPerSec > 0L) {
                                (task.totalBytes - task.downloadedBytes) / task.speedBytesPerSec
                            } else -1L
                            lastUi = now
                            windowBytes = 0L
                            persist(app); notifyUi()
                        }
                    }
                    fos.flush()
                }
            }
            task.totalBytes = targetFile.length()
            task.downloadedBytes = targetFile.length()
            task.speedBytesPerSec = 0L
            task.etaSeconds = 0L
            task.contentUri = mirrorToMediaStoreIfNeeded(app, targetFile, ct)
            task.status = DlStatus.DONE
            persist(app); notifyUi()
            WallBus.download("done: ${targetFile.name}")
        }
    }

    private fun downloadHls(app: Context, task: DownloadTask) {
        val dir = outputDir(app)
        val base = sanitize(task.title).substringBeforeLast('.') + ".ts"
        val targetFile = if (task.filePath.isNotBlank()) File(task.filePath) else uniqueFile(dir, base).also {
            task.filePath = it.absolutePath
        }
        val http = Net.client(readTimeoutSec = 30)
        fun fetchText(u: String): String {
            val req = Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HLS HTTP ${r.code}")
                return r.body?.string() ?: ""
            }
        }

        var playlistUrl = task.selectedStreamUrl.ifBlank { task.url }
        var playlist = fetchText(playlistUrl)

        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variants = parseHlsQualities(playlistUrl)
            task.availableQualities = variants
            val chosen = if (task.selectedStreamUrl.isNotBlank()) {
                variants.firstOrNull { it.uri == task.selectedStreamUrl }
            } else null ?: pickAutoQuality(variants)

            if (chosen != null) {
                task.selectedQuality = if (task.selectedStreamUrl.isBlank()) "Auto (${chosen.label})" else chosen.label
                task.selectedStreamUrl = chosen.uri
                playlistUrl = chosen.uri
                playlist = fetchText(playlistUrl)
            }
        }

        val segments = playlist.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { URL(URL(playlistUrl), it).toString() }
        if (segments.isEmpty()) throw IllegalStateException("No HLS segments found")

        task.totalSegments = segments.size
        val startIdx = task.nextSegment.coerceIn(0, segments.size)
        var lastUi = SystemClock.elapsedRealtime()
        var windowBytes = 0L

        FileOutputStream(targetFile, startIdx > 0).use { fos ->
            for (idx in startIdx until segments.size) {
                if (task.id in cancelledIds || task.id in pausedIds) {
                    fos.flush()
                    task.status = DlStatus.PAUSED
                    task.speedBytesPerSec = 0L
                    task.etaSeconds = -1L
                    persist(app); notifyUi(); return
                }
                val segReq = Request.Builder().url(segments[idx]).header("User-Agent", "Mozilla/5.0").build()
                http.newCall(segReq).execute().use { sr ->
                    if (!sr.isSuccessful) throw IllegalStateException("Segment ${idx + 1} HTTP ${sr.code}")
                    val bytes = sr.body?.bytes() ?: ByteArray(0)
                    fos.write(bytes)
                    fos.flush()
                    task.downloadedBytes += bytes.size
                    task.nextSegment = idx + 1
                    windowBytes += bytes.size

                    // Estimate total size dynamically from average segment size
                    if (task.nextSegment > 0) {
                        val avgSegSize = task.downloadedBytes / task.nextSegment
                        task.totalBytes = avgSegSize * task.totalSegments
                    }

                    val now = SystemClock.elapsedRealtime()
                    val elapsedMs = now - lastUi
                    if (elapsedMs >= 450L || idx == segments.lastIndex) {
                        val instantBps = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                        task.speedBytesPerSec = if (task.speedBytesPerSec > 0L) {
                            ((task.speedBytesPerSec * 6L + instantBps * 4L) / 10L)
                        } else instantBps
                        val remBytes = (task.totalBytes - task.downloadedBytes).coerceAtLeast(0L)
                        task.etaSeconds = if (task.speedBytesPerSec > 0L) remBytes / task.speedBytesPerSec else -1L
                        lastUi = now
                        windowBytes = 0L
                    }
                    persist(app); notifyUi()
                }
            }
        }
        task.totalBytes = targetFile.length()
        task.downloadedBytes = targetFile.length()
        task.speedBytesPerSec = 0L
        task.etaSeconds = 0L
        task.contentUri = mirrorToMediaStoreIfNeeded(app, targetFile, "video/mp2t")
        task.status = DlStatus.DONE
        persist(app); notifyUi()
        WallBus.download("done: ${targetFile.name}")
    }
}
