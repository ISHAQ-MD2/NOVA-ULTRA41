package com.nova.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Advanced Downloads Manager:
 * - Saves to /storage/emulated/0/NOVA-FILES/download/ (Scoped Storage compatible)
 * - Displays Video Size (MB/GB), Real-time Speed (MB/s), and ETA remaining
 * - Parses HLS (.m3u8) master playlists to select 1080p/720p/480p/360p (defaults to Auto 480p/360p)
 * - Supports Partial Playback: Play button works even while download is still running
 */
class DownloadsActivity : Activity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var tvFolderPath: TextView
    private lateinit var tvSummary: TextView

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadVault.init(this)
        setContentView(R.layout.activity_downloads)

        listContainer = findViewById(R.id.listContainer)
        tvFolderPath = findViewById(R.id.tvFolderPath)
        tvSummary = findViewById(R.id.tvSummary)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddUrl).setOnClickListener { promptAddUrl() }
        findViewById<Button>(R.id.btnStoragePerm).setOnClickListener { ensureStorageAccess() }

        updateFolderLabel()
        ensureLegacyPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateFolderLabel()
        DownloadVault.onUpdate = { render() }
        render()
    }

    override fun onPause() {
        DownloadVault.onUpdate = null
        super.onPause()
    }

    private fun updateFolderLabel() {
        val actual = DownloadVault.outputDir(this).absolutePath
        tvFolderPath.text = "Path: $actual"
    }

    private fun ensureLegacyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                401
            )
        }
    }

    private fun ensureStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ensureLegacyPermissionIfNeeded()
            Toast.makeText(this, "Folder ready: ${DownloadVault.outputDir(this).absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptAddUrl() {
        val et = EditText(this).apply {
            hint = "Enter .mp4, .webm, or .m3u8 URL"
            setSingleLine()
            setTextColor(Color.parseColor("#181A1F"))
            setHintTextColor(Color.parseColor("#938C88"))
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(et, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Download Video / File")
            .setView(box)
            .setPositiveButton("Next") { _, _ ->
                val url = et.text.toString().trim()
                if (url.isNotEmpty()) inspectAndEnqueue(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun inspectAndEnqueue(url: String) {
        if (DownloadVault.isYouTubeUrl(url)) {
            val t = DownloadVault.enqueue(this, url)
            Toast.makeText(this, t.error, Toast.LENGTH_LONG).show()
            render()
            return
        }
        Toast.makeText(this, "Checking video size & qualities...", Toast.LENGTH_SHORT).show()
        DownloadVault.probeUrl(url) { probedBytes, qualities ->
            if (isFinishing || isDestroyed) return@probeUrl
            if (qualities.isNotEmpty()) {
                val autoPick = DownloadVault.pickAutoQuality(qualities)
                val labels = ArrayList<String>()
                labels.add("Auto (${autoPick?.label ?: "480p/360p"} - Recommended)")
                qualities.forEach { q ->
                    val bwStr = if (q.bandwidth > 0) " • ${q.bandwidth / 1000} kbps" else ""
                    labels.add("${q.label}$bwStr")
                }
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Select HLS Quality")
                    .setItems(labels.toTypedArray()) { _, idx ->
                        val chosen = if (idx == 0) autoPick else qualities.getOrNull(idx - 1)
                        val t = DownloadVault.enqueue(this, url, "", chosen, probedBytes)
                        Toast.makeText(this, "Downloading (${t.selectedQuality}): ${t.title}", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                val sizeLabel = if (probedBytes > 0) " (${DownloadVault.formatSize(probedBytes)})" else ""
                val t = DownloadVault.enqueue(this, url, "", null, probedBytes)
                Toast.makeText(this, "Download started$sizeLabel: ${t.title}", Toast.LENGTH_SHORT).show()
                render()
            }
        }
    }

    private fun showHlsQualityPicker(task: DownloadTask) {
        Toast.makeText(this, "Loading available resolutions...", Toast.LENGTH_SHORT).show()
        Thread {
            val list = if (task.availableQualities.isNotEmpty()) task.availableQualities
            else DownloadVault.parseHlsQualities(task.url)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (list.isEmpty()) {
                    Toast.makeText(this, "Single resolution stream", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                task.availableQualities = list
                val names = list.map { q ->
                    val bw = if (q.bandwidth > 0) " (${q.bandwidth / 1000} kbps)" else ""
                    "${q.label}$bw"
                }.toTypedArray()
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Switch Video Quality")
                    .setItems(names) { _, which ->
                        DownloadVault.changeHlsQuality(this, task.id, list[which])
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun render() {
        listContainer.removeAllViews()
        val list = DownloadVault.tasks.toList()
        val activeCount = list.count { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
        tvSummary.text = "${list.size} item(s) • $activeCount active"

        if (list.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No downloads yet.\nFiles save to:\n${DownloadVault.TARGET_DISPLAY_DIR}\n\nTap + URL or use NOVA Browser video sniffer."
                setTextColor(Color.parseColor("#6B6664"))
                textSize = 14f
                setPadding(dp(12), dp(40), dp(12), dp(12))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            listContainer.addView(emptyTv)
            return
        }

        val inflater = LayoutInflater.from(this)
        for (t in list) {
            val card = inflater.inflate(R.layout.item_download, listContainer, false)
            val tvTitle = card.findViewById<TextView>(R.id.tvItemTitle)
            val tvQualityBadge = card.findViewById<TextView>(R.id.tvQualityBadge)
            val tvStatusBadge = card.findViewById<TextView>(R.id.tvStatusBadge)
            val tvSizeInfo = card.findViewById<TextView>(R.id.tvSizeInfo)
            val rowSpeedEta = card.findViewById<LinearLayout>(R.id.rowSpeedEta)
            val tvSpeed = card.findViewById<TextView>(R.id.tvSpeed)
            val tvEta = card.findViewById<TextView>(R.id.tvEta)
            val tvError = card.findViewById<TextView>(R.id.tvError)
            val pb = card.findViewById<ProgressBar>(R.id.progressBar)
            val btnPlay = card.findViewById<Button>(R.id.btnPlay)
            val btnPauseResume = card.findViewById<Button>(R.id.btnPauseResume)
            val btnQuality = card.findViewById<Button>(R.id.btnQuality)
            val btnDelete = card.findViewById<Button>(R.id.btnDelete)

            tvTitle.text = t.title.ifBlank { t.url.take(55) }
            tvQualityBadge.text = if (t.type == DlType.HLS) t.selectedQuality.ifBlank { "HLS" } else "MP4/FILE"
            tvStatusBadge.text = t.status.name

            when (t.status) {
                DlStatus.DONE -> {
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    tvStatusBadge.setTextColor(Color.parseColor("#168A45"))
                }
                DlStatus.FAILED, DlStatus.UNSUPPORTED -> {
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_coral)
                    tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                }
                else -> {
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_coral)
                    tvStatusBadge.setTextColor(Color.parseColor("#FF643D"))
                }
            }

            val dlStr = DownloadVault.formatSize(t.downloadedBytes)
            val totalStr = if (t.totalBytes > 0L) DownloadVault.formatSize(t.totalBytes) else "Calculating..."
            tvSizeInfo.text = when {
                t.type == DlType.HLS && t.totalSegments > 0 ->
                    "$dlStr / ~$totalStr (${t.nextSegment}/${t.totalSegments} segs)"
                t.totalBytes > 0L -> "$dlStr / $totalStr"
                else -> dlStr
            }

            if (t.status == DlStatus.RUNNING) {
                rowSpeedEta.visibility = View.VISIBLE
                tvSpeed.text = "Speed: ${DownloadVault.formatSpeed(t.speedBytesPerSec)}"
                tvEta.text = "ETA: ${DownloadVault.formatEta(t.etaSeconds)}"
            } else if (t.status == DlStatus.QUEUED) {
                rowSpeedEta.visibility = View.VISIBLE
                tvSpeed.text = "Speed: Starting..."
                tvEta.text = "ETA: Calculating..."
            } else {
                rowSpeedEta.visibility = View.GONE
            }

            if (t.error.isNotBlank()) {
                tvError.visibility = View.VISIBLE
                tvError.text = t.error
            } else {
                tvError.visibility = View.GONE
            }

            pb.visibility = if (t.status == DlStatus.DONE) View.GONE else View.VISIBLE
            pb.progress = when {
                t.status == DlStatus.DONE -> 100
                t.type == DlType.HLS && t.totalSegments > 0 -> (t.nextSegment * 100 / t.totalSegments).coerceIn(0, 100)
                t.totalBytes > 0L -> ((t.downloadedBytes * 100L) / t.totalBytes).toInt().coerceIn(0, 100)
                else -> 0
            }

            val file = if (t.filePath.isNotBlank()) File(t.filePath) else null
            val hasPlayableBytes = file != null && file.exists() && file.length() >= 16_384L

            // Partial Playback supported whenever at least 16KB is on disk (even while RUNNING!)
            if (hasPlayableBytes) {
                btnPlay.visibility = View.VISIBLE
                val isPartial = t.status != DlStatus.DONE
                btnPlay.text = if (isPartial) "▶ Play Partial" else "▶ Play"
                btnPlay.setOnClickListener {
                    startActivity(
                        Intent(this, VideoPlayerActivity::class.java)
                            .putExtra("url", Uri.fromFile(file).toString())
                            .putExtra("title", t.title)
                            .putExtra("task_id", t.id)
                            .putExtra("is_partial", isPartial)
                    )
                }
            } else {
                btnPlay.visibility = View.GONE
            }

            when (t.status) {
                DlStatus.RUNNING, DlStatus.QUEUED -> {
                    btnPauseResume.visibility = View.VISIBLE
                    btnPauseResume.text = "Pause"
                    btnPauseResume.setOnClickListener { DownloadVault.pause(this, t.id) }
                }
                DlStatus.PAUSED, DlStatus.FAILED -> {
                    btnPauseResume.visibility = View.VISIBLE
                    btnPauseResume.text = "Resume"
                    btnPauseResume.setOnClickListener { DownloadVault.resume(this, t.id) }
                }
                else -> btnPauseResume.visibility = View.GONE
            }

            if (t.type == DlType.HLS && t.status != DlStatus.UNSUPPORTED) {
                btnQuality.visibility = View.VISIBLE
                btnQuality.setOnClickListener { showHlsQualityPicker(t) }
            } else {
                btnQuality.visibility = View.GONE
            }

            btnDelete.setOnClickListener { DownloadVault.delete(this, t.id, true) }
            listContainer.addView(card)
        }
    }
}
