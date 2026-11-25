package com.nova.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telecom.Call
import android.telecom.CallScreeningService
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File

/** Feature 56: Quick Settings Tile to open NOVA or NOVA Hub from notification shade. */
class NovaTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (LiveService.isRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "NOVA"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = android.app.PendingIntent.getActivity(this, 801, i, android.app.PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

/** Feature 42: Device Admin Receiver (strictly optional, removable anytime in NOVA Settings). */
class NovaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        NovaRepo.logConsent(context, "device_admin", true, "Device Admin enabled by user")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        NovaRepo.logConsent(context, "device_admin", false, "Device Admin disabled by user")
    }
}

/** Feature 36: Local Privacy DNS / Loopback Tracker Shield VPN Service for NOVA Browser & device. */
class NovaVpnService : VpnService() {
    companion object {
        @Volatile var running = false
        fun start(ctx: Context) {
            ctx.startService(Intent(ctx, NovaVpnService::class.java).setAction("START"))
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, NovaVpnService::class.java).setAction("STOP"))
        }
    }

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            try { tun?.close() } catch (_: Exception) {}
            tun = null
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY
        try {
            val builder = Builder()
                .setSession("NOVA Privacy Shield")
                .addAddress("10.111.222.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("9.9.9.9")
                .addAllowedApplication(packageName)
            tun = builder.establish()
            running = tun != null
        } catch (_: Throwable) {
            running = false
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { tun?.close() } catch (_: Exception) {}
        running = false
        super.onDestroy()
    }
}

/** Feature 84: Local Caller ID & Spam Blocker via Android CallScreeningService. */
class NovaCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val enabled = getSharedPreferences("nova", MODE_PRIVATE).getBoolean("feat_spam_blocker", false)
        val num = callDetails.handle?.schemeSpecificPart ?: ""
        val blockedRaw = getSharedPreferences("nova", MODE_PRIVATE).getString("spam_blocklist", "") ?: ""
        val blocked = blockedRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val isSpam = enabled && num.isNotBlank() && blocked.any { num.endsWith(it) || it == num }
        val resp = CallResponse.Builder()
            .setDisallowCall(isSpam)
            .setRejectCall(isSpam)
            .setSkipNotification(isSpam)
            .build()
        respondToCall(callDetails, resp)
    }
}

/** Feature 73: Screen Recorder Foreground Service (MediaProjection + user consent). */
class ScreenRecordService : android.app.Service() {
    companion object {
        @Volatile var recording = false
        @Volatile var lastPath = ""
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ScreenRecordService::class.java).setAction("STOP"))
        }
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }

        val chId = "nova_rec"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(chId, "Screen Recorder", NotificationManager.IMPORTANCE_LOW))
        val notif = Notification.Builder(this, chId)
            .setContentTitle("NOVA Screen Recorder")
            .setContentText("Recording screen (tap in NOVA Hub to stop)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(940, notif)

        try {
            val dm = resources.displayMetrics
            val w = minOf(dm.widthPixels, 1080)
            val h = minOf(dm.heightPixels, 1920)
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NOVA-Records").apply { mkdirs() }
            val out = File(dir, "nova_rec_${System.currentTimeMillis()}.mp4")
            lastPath = out.absolutePath

            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setOutputFile(out.absolutePath)
            mr.setVideoSize(w, h)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setVideoEncodingBitRate(4_000_000)
            mr.setVideoFrameRate(30)
            mr.prepare()
            recorder = mr

            val mpm = getSystemService(MediaProjectionManager::class.java)
            val mp = mpm.getMediaProjection(code, data)
            projection = mp
            virtualDisplay = mp?.createVirtualDisplay("nova_screen", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mr.surface, null, null)
            mr.start()
            recording = true
        } catch (_: Throwable) {
            cleanup()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun cleanup() {
        recording = false
        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        recorder = null; virtualDisplay = null; projection = null
    }
}

/** Features 52 & 53: Charging Broadcast Receiver + Advanced Charging Animation Activity. */
class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("nova", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("feat_charging_anim", false)) return
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            val i = Intent(context, ChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i) } catch (_: Throwable) {}
        }
    }
}

class ChargingActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var pctTv: TextView
    private lateinit var etaTv: TextView
    private lateinit var detailTv: TextView
    private lateinit var bar: ProgressBar

    private val tick = object : Runnable {
        override fun run() {
            val r = SystemSecurityTools.batteryReport(this@ChargingActivity)
            pctTv.text = "${r.syncedLevel}%"
            etaTv.text = "⏳ ${r.etaText}  •  ${r.pluggedLabel}"
            detailTv.text = "Raw: ${r.level}%  |  ${r.tempC}°C  |  ${r.voltageMv} mV  |  Health: ${r.healthLabel}"
            bar.progress = r.syncedLevel
            if (!r.charging) { finish(); return }
            h.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#060914"))
            setPadding(48, 48, 48, 48)
        }
        pctTv = TextView(this).apply {
            textSize = 54f; setTextColor(Color.parseColor("#00E5FF")); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        etaTv = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 16, 0, 12)
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
        }
        detailTv = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#9AA6B8")); gravity = Gravity.CENTER; setPadding(0, 18, 0, 28)
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            contentDescription = "Close charging screen"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply { setColor(Color.parseColor("#00E5FF")); cornerRadius = 24f }
            setOnClickListener { finish() }
        }
        root.addView(pctTv)
        root.addView(etaTv)
        root.addView(bar, LinearLayout.LayoutParams(-1, 24))
        root.addView(detailTv)
        root.addView(closeBtn)
        setContentView(root)
        h.post(tick)
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        super.onDestroy()
    }
}
