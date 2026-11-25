package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Custom Media3 ExoPlayer Video Player supporting MP4, WebM, HLS (.m3u8),
 * and Progressive Partial Playback while a download is still running in DownloadVault.
 */
class VideoPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var controlsBar: LinearLayout
    private lateinit var titleTv: TextView
    private lateinit var partialBadge: TextView
    private lateinit var refreshBtn: Button
    private lateinit var speedBtn: Button
    private var mediaUrl: String = ""
    private var mediaTitle: String = "NOVA Player"
    private var downloadTaskId: String = ""
    private var isPartialMode: Boolean = false
    private var lastLoadedFileBytes: Long = 0L
    private var isFullscreen = false
    private var curSpeed = 1.0f
    private val h = Handler(Looper.getMainLooper())

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private val partialPollRun = object : Runnable {
        override fun run() {
            if (isPartialMode && downloadTaskId.isNotBlank()) {
                val task = DownloadVault.tasks.firstOrNull { it.id == downloadTaskId }
                if (task != null) {
                    val f = if (task.filePath.isNotBlank()) File(task.filePath) else null
                    val curLen = f?.length() ?: task.downloadedBytes
                    val sizeTxt = DownloadVault.formatSize(curLen)
                    if (task.status == DlStatus.DONE) {
                        isPartialMode = false
                        partialBadge.text = "COMPLETE • $sizeTxt"
                        partialBadge.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#168A45"))
                            cornerRadius = dp(10).toFloat()
                        }
                        if (curLen > lastLoadedFileBytes) refreshPartialSource(keepPlaying = true)
                    } else {
                        partialBadge.text = "LIVE PARTIAL • $sizeTxt"
                    }
                }
                h.postDelayed(this, 1500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaUrl = intent.getStringExtra("url") ?: intent.dataString ?: ""
        mediaTitle = intent.getStringExtra("title")?.ifBlank { null } ?: "NOVA Video Player"
        downloadTaskId = intent.getStringExtra("task_id") ?: ""
        isPartialMode = intent.getBooleanExtra("is_partial", false)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Top action bar
        topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(190, 14, 18, 28))
            setPadding(dp(8), dp(26), dp(8), dp(6))
        }
        val backBtn = pill("←") { finish() }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        titleTv = TextView(this).apply {
            text = mediaTitle
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        partialBadge = TextView(this).apply {
            text = "LIVE PARTIAL STREAM"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF643D"))
                cornerRadius = dp(10).toFloat()
            }
            visibility = if (isPartialMode) View.VISIBLE else View.GONE
        }
        titleCol.addView(titleTv)
        titleCol.addView(partialBadge, lp(-2, -2).apply { topMargin = dp(2) })

        val pipBtn = pill("PiP") { enterPip() }
        val fsBtn = pill("⛶") { toggleFullscreen() }
        topBar.addView(backBtn, lp(dp(48), dp(48)))
        topBar.addView(titleCol, lp(0, -2, 1f))
        topBar.addView(pipBtn, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        topBar.addView(fsBtn, lp(dp(48), dp(48)))

        controlsBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(190, 14, 18, 28))
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        refreshBtn = pill("↻ Buffer") { refreshPartialSource(keepPlaying = true) }
        if (isPartialMode) row.addView(refreshBtn, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("-20s") { seekBy(-20_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("-15s") { seekBy(-15_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("-10s") { seekBy(-10_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("+10s") { seekBy(10_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("+15s") { seekBy(15_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("+20s") { seekBy(20_000L) }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        speedBtn = pill("1.0x") { showSpeedDialog() }
        row.addView(speedBtn, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("Quality") { showQualityDialog() }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("Copy URL") { copyUrl() }, lp(-2, dp(48)).apply { rightMargin = dp(4) })
        row.addView(pill("Download") { startDownload() }, lp(-2, dp(48)))

        controlsBar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, lp(-1, -2))

        col.addView(topBar, lp(-1, -2))
        col.addView(controlsBar, lp(-1, -2))
        root.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        initPlayer()
        if (isPartialMode) h.post(partialPollRun)
    }

    private fun pill(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(10), 0, dp(10), 0)
        background = GradientDrawable().apply {
            setColor(Color.argb(180, 34, 42, 62))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#FF643D"))
        }
        setOnClickListener { onClick() }
    }

    private fun initPlayer() {
        if (mediaUrl.isBlank()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val ts = DefaultTrackSelector(this)
        trackSelector = ts
        val p = ExoPlayer.Builder(this).setTrackSelector(ts).build()
        player = p
        playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && isPartialMode && downloadTaskId.isNotBlank()) {
                    val task = DownloadVault.tasks.firstOrNull { it.id == downloadTaskId }
                    if (task != null && task.status == DlStatus.RUNNING) {
                        Toast.makeText(this@VideoPlayerActivity, "Loading newly downloaded segments...", Toast.LENGTH_SHORT).show()
                        h.postDelayed({ refreshPartialSource(keepPlaying = true) }, 900L)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isPartialMode) {
                    Toast.makeText(this@VideoPlayerActivity, "Buffering partial video... tap ↻ Buffer", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@VideoPlayerActivity, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        loadCurrentSource(seekMs = 0L, play = true)
    }

    private fun loadCurrentSource(seekMs: Long, play: Boolean) {
        val p = player ?: return
        val uri = Uri.parse(mediaUrl)
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            lastLoadedFileBytes = if (f.exists()) f.length() else 0L
        }
        val item = MediaItem.fromUri(uri)
        if (isPartialMode && (uri.scheme == "file" || uri.scheme == "content")) {
            val dsFactory = DefaultDataSource.Factory(this)
            val src = ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item)
            p.setMediaSource(src)
        } else {
            p.setMediaItem(item)
        }
        p.prepare()
        if (seekMs > 0L) p.seekTo(seekMs)
        p.playWhenReady = play
    }

    private fun refreshPartialSource(keepPlaying: Boolean) {
        val p = player ?: return
        val pos = p.currentPosition.coerceAtLeast(0L)
        loadCurrentSource(seekMs = pos, play = keepPlaying)
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val next = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(next)
    }

    private fun showSpeedDialog() {
        val labels = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "3.0x")
        val vals = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Playback Speed")
            .setItems(labels) { _, i ->
                curSpeed = vals[i]
                player?.setPlaybackSpeed(curSpeed)
                speedBtn.text = labels[i]
            }.show()
    }

    private fun showQualityDialog() {
        val p = player ?: return
        val ts = trackSelector ?: return
        val tracks = p.currentTracks
        val options = ArrayList<Pair<String, (() -> Unit)>>()
        options.add("Auto (Best adaptive)" to {
            ts.setParameters(ts.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_VIDEO))
            Toast.makeText(this, "Quality: Auto", Toast.LENGTH_SHORT).show()
        })
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                val mg = group.mediaTrackGroup
                for (i in 0 until mg.length) {
                    if (!group.isTrackSupported(i)) continue
                    val fmt = mg.getFormat(i)
                    val h = fmt.height
                    val bw = fmt.bitrate / 1000
                    val label = (if (h > 0) "${h}p" else "Stream $i") + (if (bw > 0) " (${bw} kbps)" else "")
                    options.add(label to {
                        val params = ts.buildUponParameters()
                            .setOverrideForType(TrackSelectionOverride(mg, listOf(i)))
                            .build()
                        ts.setParameters(params)
                        Toast.makeText(this, "Quality: $label", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
        if (options.size <= 1) {
            Toast.makeText(this, "Single quality stream", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Video Quality")
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> options[i].second() }
            .show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (isFullscreen) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else 0
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val fmt = player?.videoFormat
                val w = (fmt?.width ?: 16).coerceAtLeast(1)
                val h = (fmt?.height ?: 9).coerceAtLeast(1)
                val params = PictureInPictureParams.Builder().setAspectRatio(Rational(w, h)).build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "PiP unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val vis = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        topBar.visibility = vis
        controlsBar.visibility = vis
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) enterPip()
    }

    private fun copyUrl() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("video_url", mediaUrl))
            Toast.makeText(this, "Video URL copied", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun startDownload() {
        if (mediaUrl.startsWith("file://") || mediaUrl.startsWith("content://")) {
            Toast.makeText(this, "Already a local file", Toast.LENGTH_SHORT).show()
            return
        }
        val t = DownloadVault.enqueue(this, mediaUrl, mediaTitle)
        Toast.makeText(
            this,
            if (t.status == DlStatus.UNSUPPORTED) t.error else "Download started: ${t.title}",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        h.removeCallbacks(partialPollRun)
        player?.release()
        player = null
        super.onDestroy()
    }
}
