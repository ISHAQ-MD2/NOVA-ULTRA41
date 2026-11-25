package com.nova.app

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * NOVA Sunset / Pastel Peach Main Workspace:
 * - 4-Tab Workspace via floating curved SunsetNavBar: Home, Chat, Mic (center), Settings, Updates
 * - Preserves 100% of existing features: LiveService voice, Text/Code/Hosting Chat, Audio Share,
 *   Camera Vision, 98 Hub, Browser, Downloads, Backups, Wallpapers, Macros, Terminal, Reminders.
 */
class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var rootFrame: FrameLayout
    private lateinit var navBar: SunsetNavBar

    // Pages
    private lateinit var pageHome: LinearLayout
    private lateinit var pageChat: LinearLayout
    private lateinit var pageSettingsContainer: FrameLayout
    private lateinit var pageUpdates: LinearLayout

    // Home Views
    private lateinit var greetingTv: TextView
    private lateinit var orbView: SunsetOrbView
    private lateinit var statusTv: TextView
    private lateinit var muteNovaPill: TextView
    private lateinit var muteMicPill: TextView
    private lateinit var cardWeatherTv: TextView
    private lateinit var cardWeatherSub: TextView
    private lateinit var cardTimeTv: TextView
    private lateinit var cardAzanTv: TextView
    private lateinit var cardAzanSub: TextView
    private lateinit var cardMusicTv: TextView
    private lateinit var cardMusicSub: TextView
    private lateinit var cardRemTv: TextView

    // Chat Views
    private lateinit var chatScroll: ScrollView
    private lateinit var chatList: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var speakReplyChip: TextView

    // Updates Feed
    private lateinit var updatesFeedList: LinearLayout

    // Drawer
    private lateinit var drawer: FrameLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var drawerList: LinearLayout

    private var currentTab = 0
    private var active = false
    private var autoTried = false
    private var speakReplies = false
    private var activeAudioPlayer: MediaPlayer? = null
    private val textTurns = ArrayList<Pair<String, String>>()
    private val h = Handler(Looper.getMainLooper())
    private var tickRun: Runnable? = null
    private var tickN = 0
    private var fileMode = "decode"

    private val REQ_FILE = 25; private val REQ_WALL = 24; private val REQ_CAM = 21
    private val REQ_PICK = 22; private val REQ_CAM_PERM = 23; private val REQ_MEDIA = 40

    private val feedListener: (List<WallBus.FeedEvt>) -> Unit = { list ->
        h.post {
            navBar.hasUpdatesDot = list.isNotEmpty() && currentTab != 3
            if (::updatesFeedList.isInitialized) renderUpdatesFeed(list)
        }
    }

    private fun dp(x: Int) = SunsetUiHelper.dp(this, x)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun sysDim(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
    private fun statusBarH() = sysDim("status_bar_height")
    private fun navBarH() = sysDim("navigation_bar_height")
    private fun fmtTime(ms: Long, pattern: String = "h:mm a"): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Karachi") }.format(Date(ms))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = SunsetColors.PEACH_BOTTOM
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        prefs = getSharedPreferences("nova", MODE_PRIVATE)
        I18n.applyDirection(this)
        Memory.init(this); ChatLog.init(this); ClipStore.start(this); TaskStore.purgeExpired(this); Prayer.scheduleNext(this)
        Thread { try { Memory.pruneChat(applicationContext, 3) } catch (_: Exception) {} }.start()

        rootFrame = FrameLayout(this).apply {
            background = SunsetUiHelper.pageBg()
        }

        pageHome = buildHomePage()
        pageChat = buildChatPage()
        pageSettingsContainer = FrameLayout(this)
        pageUpdates = buildUpdatesPage()

        val pagesHolder = FrameLayout(this).apply {
            setPadding(0, statusBarH(), 0, 0)
            addView(pageHome, FrameLayout.LayoutParams(-1, -1))
            addView(pageChat, FrameLayout.LayoutParams(-1, -1))
            addView(pageSettingsContainer, FrameLayout.LayoutParams(-1, -1))
            addView(pageUpdates, FrameLayout.LayoutParams(-1, -1))
        }
        rootFrame.addView(pagesHolder, FrameLayout.LayoutParams(-1, -1))

        navBar = SunsetNavBar(this).apply {
            onTabSelected = { idx -> switchTab(idx) }
            onMicClick = { toggle() }
        }
        rootFrame.addView(navBar, FrameLayout.LayoutParams(-1, dp(96), Gravity.BOTTOM).apply {
            bottomMargin = navBarH()
        })

        // Side Drawer
        drawer = FrameLayout(this).apply { visibility = View.GONE }
        drawer.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#88000000"))
            setOnClickListener { closeDrawer() }
        }, FrameLayout.LayoutParams(-1, -1))
        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF8F3"))
            isClickable = true
        }
        drawerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pw = minOf(dp(320), (resources.displayMetrics.widthPixels * 0.84f).toInt())
        drawer.addView(drawerPanel, FrameLayout.LayoutParams(pw, -1, Gravity.START))
        rootFrame.addView(drawer, FrameLayout.LayoutParams(-1, -1))

        setContentView(rootFrame)
        buildDrawer()
        rebuildSettingsPage()
        switchTab(0)

        val saved = prefs.getInt("vol", 100)
        Vol.boost = if (saved > 100) saved / 100f else 1f
        ClipWatch.autoStart(this)
        askStartupPerms()
        if (prefs.getBoolean("robot", true) && Settings.canDrawOverlays(this)) RobotPet.show(this)
        handleDeepLink(intent)
    }

    // ==================== TAB 0: HOME ====================
    private fun buildHomePage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(96) + navBarH())
        }

        // Top Header: Menu | NOVA + Greeting | Browser + Hub
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        val menuBtn = SunsetUiHelper.circleIconBtn(this, "menu", "Open navigation menu") { openDrawer() }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        titleCol.addView(TextView(this).apply {
            text = "NOVA"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.22f
            setTextColor(SunsetColors.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })
        greetingTv = TextView(this).apply {
            text = "Ready for ${Personas.userName(this@MainActivity)}"
            textSize = 12f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            gravity = Gravity.CENTER
        }
        titleCol.addView(greetingTv)
        val browserBtn = SunsetUiHelper.circleIconBtn(this, "cloud", "Open NOVA Browser") { NovaBrowser.openUi(this) }
        val hubBtn = SunsetUiHelper.circleIconBtn(this, "grid", "Open NOVA 98 Tools Hub") {
            startActivity(Intent(this, NovaHubActivity::class.java))
        }
        top.addView(menuBtn, lp(dp(48), dp(48)))
        top.addView(titleCol, lp(0, -2, 1f))
        top.addView(browserBtn, lp(dp(48), dp(48)).apply { rightMargin = dp(6) })
        top.addView(hubBtn, lp(dp(48), dp(48)))
        page.addView(top, lp(-1, -2))

        // Center Glowing Particle Orb
        val orbContainer = FrameLayout(this).apply {
            setPadding(dp(16), 0, dp(16), 0)
        }
        orbView = SunsetOrbView(this).apply {
            setOnClickListener { toggle() }
        }
        orbContainer.addView(orbView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        page.addView(orbContainer, lp(-1, 0, 1f))

        // Live Status Pill
        statusTv = TextView(this).apply {
            text = "Ready"
            textSize = 13f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(20), dp(2), dp(20), dp(8))
        }
        page.addView(statusTv, lp(-1, -2))

        // Quick Controls Strip (Camera Vision | Mute NOVA | Mute Mic | Downloads)
        val ctrlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), dp(10))
        }
        fun pillBtn(label: String, desc: String, act: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            contentDescription = desc
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = SunsetUiHelper.glassCard(this@MainActivity, 20)
            setOnClickListener { act() }
        }
        val camPill = pillBtn("📷 Vision", "Camera Vision") { takePhoto() }
        muteNovaPill = pillBtn("🔊 Voice ON", "Mute NOVA speaker") {
            Vol.speakerMuted = !Vol.speakerMuted
            refreshMute()
        }
        muteMicPill = pillBtn("🎙 Mic ON", "Mute Microphone") {
            val m = !Vol.micMuted
            val c = LiveService.liveClient
            if (c != null) c.setMicMuted(m) else Vol.micMuted = m
            refreshMute()
        }
        val dlPill = pillBtn("⬇ Downloads", "Open Downloads Manager") {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        for (p in listOf(camPill, muteNovaPill, muteMicPill, dlPill)) {
            ctrlRow.addView(p, lp(-2, dp(48)).apply { rightMargin = dp(6) })
        }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(ctrlRow)
        }, lp(-1, -2))

        // Sunset Info Telemetry Cards Row
        val cardsRow = LinearLayout(this).apply { setPadding(dp(14), 0, dp(14), dp(4)) }
        val wPair = buildSunsetInfoCard("Mausam", "--") { weatherTap() }
        cardWeatherSub = wPair.second; cardWeatherTv = wPair.third
        val tPair = buildSunsetInfoCard("Time", "--") { }
        cardTimeTv = tPair.third
        val aPair = buildSunsetInfoCard("Azan", "--") { SunsetDialogs.azanDialog(this) }
        cardAzanSub = aPair.second; cardAzanTv = aPair.third
        val mPair = buildSunsetInfoCard("Music", "Play") { musicTap() }.also {
            it.first.setOnLongClickListener { musicDialog(); true }
        }
        cardMusicSub = mPair.second; cardMusicTv = mPair.third
        val rPair = buildSunsetInfoCard("Reminders", "--") {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
        cardRemTv = rPair.third
        for (card in listOf(wPair.first, tPair.first, aPair.first, mPair.first, rPair.first)) {
            cardsRow.addView(card, lp(dp(106), dp(78)).apply { rightMargin = dp(10) })
        }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(cardsRow)
        }, lp(-1, -2))

        return page
    }

    private fun buildSunsetInfoCard(label: String, value: String, onClick: () -> Unit): Triple<LinearLayout, TextView, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = SunsetUiHelper.glassCard(this@MainActivity, 18)
            contentDescription = label
            setOnClickListener { onClick() }
        }
        val lbl = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(SunsetColors.CORAL)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val valTv = TextView(this).apply {
            text = value
            textSize = 14.5f
            setTextColor(SunsetColors.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(4), 0, 0)
        }
        box.addView(lbl)
        box.addView(valTv)
        return Triple(box, lbl, valTv)
    }

    // ==================== TAB 1: CHAT ====================
    private fun buildChatPage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(98) + navBarH())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "Chat with NOVA"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        titleCol.addView(TextView(this).apply {
            text = "Text, Code Studio, Offline Commands & Voice Notes"
            textSize = 12f
            setTextColor(SunsetColors.TEXT_SECONDARY)
        })
        speakReplyChip = TextView(this).apply {
            text = "🔊 Speak: OFF"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.CORAL)
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = SunsetUiHelper.outlinePill(this@MainActivity, 20, SunsetColors.CORAL)
            contentDescription = "Toggle spoken replies in chat"
            setOnClickListener {
                speakReplies = !speakReplies
                text = if (speakReplies) "🔊 Speak: ON" else "🔊 Speak: OFF"
            }
        }
        val clearBtn = TextView(this).apply {
            text = "Clear"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = SunsetUiHelper.glassCard(this@MainActivity, 20)
            contentDescription = "Clear chat messages"
            setOnClickListener {
                textTurns.clear()
                chatList.removeAllViews()
                addTextBubble("nova", "Salam ${Personas.userName(this@MainActivity)}! Ask anything, build code, or say 'Nova, show source code'.")
            }
        }
        header.addView(titleCol, lp(0, -2, 1f))
        header.addView(speakReplyChip, lp(-2, dp(48)).apply { rightMargin = dp(6) })
        header.addView(clearBtn, lp(-2, dp(48)))
        page.addView(header)

        chatList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        chatScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            background = SunsetUiHelper.glassCard(this@MainActivity, 22)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(chatList)
        }
        page.addView(chatScroll, lp(-1, 0, 1f).apply { bottomMargin = dp(10) })

        addTextBubble("nova", "Salam ${Personas.userName(this)}! Main NOVA hoon. Likhen ya mic button tap karein.")

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chatInput = EditText(this).apply {
            hint = I18n.t(this@MainActivity, "type_hint")
            setHintTextColor(SunsetColors.TEXT_MUTED)
            setTextColor(SunsetColors.TEXT_PRIMARY)
            textSize = 14.5f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = SunsetUiHelper.glassCard(this@MainActivity, 24)
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false }
        }
        val sendBtn = Button(this).apply {
            text = "Send"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false
            minimumHeight = dp(48)
            background = SunsetUiHelper.gradientPill(this@MainActivity, 24)
            contentDescription = "Send chat message"
            setOnClickListener { sendTyped() }
        }
        inputRow.addView(chatInput, lp(0, dp(48), 1f).apply { rightMargin = dp(8) })
        inputRow.addView(sendBtn, lp(-2, dp(48)))
        page.addView(inputRow, lp(-1, -2))
        return page
    }

    // ==================== TAB 3: UPDATES ====================
    private fun buildUpdatesPage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(100) + navBarH())
        }
        page.addView(TextView(this).apply {
            text = "Updates & Tools"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        page.addView(TextView(this).apply {
            text = "Downloads, Browser, Backups & Live Neural Log"
            textSize = 13f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(2), 0, dp(12))
        })

        // Quick Shortcut Grid Row
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun quickTile(title: String, sub: String, act: () -> Unit): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = SunsetUiHelper.glassCard(this@MainActivity, 18)
            contentDescription = title
            setOnClickListener { act() }
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(SunsetColors.TEXT_PRIMARY)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub; textSize = 11.5f; setTextColor(SunsetColors.CORAL); setPadding(0, dp(3), 0, 0)
            })
        }
        shortcuts.addView(quickTile("⬇ Downloads", "/NOVA-FILES/download") {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }, lp(0, -2, 1f).apply { rightMargin = dp(8) })
        shortcuts.addView(quickTile("🌐 NOVA Browser", "View Source & HLS") {
            NovaBrowser.openUi(this)
        }, lp(0, -2, 1f).apply { leftMargin = dp(8) })
        page.addView(shortcuts, lp(-1, -2).apply { bottomMargin = dp(10) })

        val shortcuts2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shortcuts2.addView(quickTile("📦 Code Backups", "Zip Projects & Exports") {
            startActivity(Intent(this, BackupsActivity::class.java))
        }, lp(0, -2, 1f).apply { rightMargin = dp(8) })
        shortcuts2.addView(quickTile("🧰 98 Command Hub", "Security, Song & Quran") {
            startActivity(Intent(this, NovaHubActivity::class.java))
        }, lp(0, -2, 1f).apply { leftMargin = dp(8) })
        page.addView(shortcuts2, lp(-1, -2).apply { bottomMargin = dp(14) })

        page.addView(TextView(this).apply {
            text = "LIVE ACTIVITY FEED"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(dp(4), 0, 0, dp(8))
        })

        updatesFeedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply {
            background = SunsetUiHelper.glassCard(this@MainActivity, 20)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(updatesFeedList)
        }
        page.addView(sv, lp(-1, 0, 1f))
        renderUpdatesFeed(WallBus.feedSnapshot())
        return page
    }

    private fun renderUpdatesFeed(entries: List<WallBus.FeedEvt>) {
        updatesFeedList.removeAllViews()
        if (entries.isEmpty()) {
            updatesFeedList.addView(TextView(this).apply {
                text = "No recent events yet. Voice actions, tool executions, and browser downloads appear here in real time."
                textSize = 13f
                setTextColor(SunsetColors.TEXT_SECONDARY)
            })
            return
        }
        for (e in entries.reversed()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = e.tag
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(SunsetColors.CORAL)
            })
            row.addView(TextView(this).apply {
                text = e.text
                textSize = 13f
                setTextColor(SunsetColors.TEXT_PRIMARY)
            })
            updatesFeedList.addView(row)
            updatesFeedList.addView(View(this).apply { setBackgroundColor(SunsetColors.DIVIDER) }, lp(-1, dp(1)))
        }
    }

    private fun switchTab(idx: Int) {
        currentTab = idx
        navBar.selectedTab = idx
        pageHome.visibility = if (idx == 0) View.VISIBLE else View.GONE
        pageChat.visibility = if (idx == 1) View.VISIBLE else View.GONE
        pageSettingsContainer.visibility = if (idx == 2) View.VISIBLE else View.GONE
        pageUpdates.visibility = if (idx == 3) View.VISIBLE else View.GONE
        if (idx == 3) navBar.hasUpdatesDot = false
        if (idx == 2) rebuildSettingsPage()
    }

    private fun rebuildSettingsPage() {
        pageSettingsContainer.removeAllViews()
        val sv = SunsetSettingsUi.build(this, object : SunsetSettingsUi.Callbacks {
            override fun onCallAssistant() { toggle() }
            override fun onOpenTheme() {
                SunsetDialogs.themeDialog(
                    this@MainActivity, Themes.current(this@MainActivity).accent, prefs,
                    onApplyTheme = { orbView.invalidate() },
                    onPickWall = { pickWallpaper() },
                    onSetStatic = { flag -> setStaticWallpaper(flag) },
                    onSetLive = { setLiveWallpaper() }
                )
            }
            override fun onOpenCodeStudio() { SunsetDialogs.codeStudioDialog(this@MainActivity, h) { s -> statusTv.text = s } }
            override fun onOpenMemories() { SunsetDialogs.memoriesDialog(this@MainActivity, h) { s -> statusTv.text = s } }
            override fun onOpenOverlayOptions() { toggleBubble() }
            override fun onOpenPrivacyShield() { settingsDialog() }
            override fun onOpenLegacySettings() { settingsDialog() }
            override fun onRefreshAll() { refreshAllUi() }
        })
        pageSettingsContainer.addView(sv, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshAllUi() {
        greetingTv.text = "Ready for ${Personas.userName(this)}"
        orbView.invalidate()
        rebuildSettingsPage()
    }

    // ==================== DRAWER ====================
    private fun buildDrawer() {
        drawerPanel.removeAllViews(); drawerList.removeAllViews()
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), statusBarH() + dp(18), dp(14), dp(16))
        }
        val nm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nm.addView(TextView(this).apply {
            text = "NOVA"
            textSize = 24f
            setTextColor(SunsetColors.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        nm.addView(TextView(this).apply {
            text = "${Personas.userName(this@MainActivity)} • X32 Team"
            textSize = 11.5f
            letterSpacing = 0.08f
            setTextColor(SunsetColors.CORAL)
        })
        val close = SunsetUiHelper.circleIconBtn(this, "close", "Close drawer") { closeDrawer() }
        head.addView(nm, lp(0, -2, 1f))
        head.addView(close, lp(dp(48), dp(48)))
        drawerPanel.addView(head, lp(-1, -2))

        val rows = listOf<Triple<String, String, () -> Unit>>(
            Triple("grid", I18n.t(this, "hub_btn") + " (98 Tools)", { closeDrawer(); startActivity(Intent(this, NovaHubActivity::class.java)) }),
            Triple("person", "AI Persona & Voice", { closeDrawer(); startActivity(Intent(this, PersonaActivity::class.java)) }),
            Triple("shield", "Owner Voice Biometrics", { closeDrawer(); startActivity(Intent(this, VoiceBiometricsActivity::class.java)) }),
            Triple("cloud", "NOVA Browser (View Source)", { closeDrawer(); NovaBrowser.openUi(this) }),
            Triple("download", "Downloads Manager", { closeDrawer(); startActivity(Intent(this, DownloadsActivity::class.java)) }),
            Triple("bolt", "Code Backups (Zip)", { closeDrawer(); startActivity(Intent(this, BackupsActivity::class.java)) }),
            Triple("music", "Video Player (URL)", { closeDrawer(); SunsetDialogs.videoUrlDialog(this) }),
            Triple("music", "Song Search & Analysis", { closeDrawer(); NovaHubToolsUi.showSongAnalyzer(this, SunsetColors.CORAL) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) } }),
            Triple("moon", I18n.t(this, "qibla") + " & Shan-e-Nuzool", { closeDrawer(); NovaHubToolsUi.showIslamicHub(this, SunsetColors.CORAL) }),
            Triple("tune", "Terminal (Termux)", { closeDrawer(); SunsetDialogs.terminalDialog(this, SunsetColors.CORAL) }),
            Triple("bolt", "NOVA Code Studio", { closeDrawer(); SunsetDialogs.codeStudioDialog(this, h) { statusTv.text = it } }),
            Triple("lock", "Decode / Encrypt Vault", { closeDrawer(); SunsetDialogs.vaultMenu(this, { statusTv.text = it }) { mode -> fileMode = mode; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_FILE) } }),
            Triple("bell", "Reminders & Namaz Alarms", { closeDrawer(); startActivity(Intent(this, RemindersActivity::class.java)) }),
            Triple("grid", "Neural Memory Bank", { closeDrawer(); SunsetDialogs.memoriesDialog(this, h) { statusTv.text = it } }),
            Triple("chat", "Chat History", { closeDrawer(); SunsetDialogs.chatHistoryDialog(this, h) { statusTv.text = it } }),
            Triple("close", "Failed Tasks", { closeDrawer(); SunsetDialogs.failedTasksDialog(this) { statusTv.text = it } }),
            Triple("sun", "Animation Studio", { closeDrawer(); SunsetDialogs.animStudio(this, Themes.current(this).accent, prefs) { statusTv.text = it } }),
            Triple("grid", "Macros", { closeDrawer(); SunsetDialogs.macrosDialog(this) { statusTv.text = it } }),
            Triple("palette", "Wallpaper Widgets (32)", { closeDrawer(); SunsetDialogs.themeDialog(this, Themes.current(this).accent, prefs, { orbView.invalidate() }, { pickWallpaper() }, { setStaticWallpaper(it) }, { setLiveWallpaper() }) }),
            Triple("person", "Contacts & Profile", { closeDrawer(); startActivity(Intent(this, ProfileActivity::class.java)) }),
            Triple("tune", "All System Settings", { closeDrawer(); settingsDialog() })
        )
        for ((ic, label, act) in rows) {
            val r = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(20), dp(12), dp(16), dp(12))
                setOnClickListener { act() }
            }
            r.addView(IconView(this).apply { icon = ic; tint = SunsetColors.CORAL }, lp(dp(22), dp(22)))
            r.addView(TextView(this).apply {
                text = label; textSize = 15f; setTextColor(SunsetColors.TEXT_PRIMARY); setPadding(dp(16), 0, 0, 0)
            }, lp(0, -2, 1f))
            r.addView(IconView(this).apply { icon = "chevron"; tint = SunsetColors.TEXT_MUTED }, lp(dp(16), dp(16)))
            drawerList.addView(r, lp(-1, -2))
            drawerList.addView(View(this).apply { setBackgroundColor(SunsetColors.DIVIDER) }, lp(-1, 1).apply { leftMargin = dp(18); rightMargin = dp(18) })
        }
        drawerPanel.addView(ScrollView(this).apply { addView(drawerList) }, lp(-1, 0, 1f))
    }

    private fun openDrawer() {
        buildDrawer()
        drawer.visibility = View.VISIBLE; drawerPanel.translationX = -dp(340).toFloat()
        ObjectAnimator.ofFloat(drawerPanel, "translationX", -dp(340).toFloat(), 0f).setDuration(220).start()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        ObjectAnimator.ofFloat(drawerPanel, "translationX", 0f, -dp(340).toFloat()).setDuration(180).start()
        h.postDelayed({ drawer.visibility = View.GONE }, 190)
    }

    @Deprecated("simple")
    override fun onBackPressed() {
        when {
            drawer.visibility == View.VISIBLE -> closeDrawer()
            currentTab != 0 -> switchTab(0)
            else -> super.onBackPressed()
        }
    }

    // ==================== CARDS / TELEMETRY ====================
    private fun updateCards(all: Boolean = false) {
        val now = System.currentTimeMillis()
        cardTimeTv.text = fmtTime(now)
        val mp = MusicPlayer.current
        cardMusicSub.text = if (mp != null) mp.title.take(12) else "Music"
        cardMusicTv.text = if (MusicPlayer.isPlaying()) "Pause" else "Play"
        if (all || tickN % 15 == 0) {
            val nx = Prayer.nextPrayer(now)
            cardAzanSub.text = nx?.first ?: "Azan"
            cardAzanTv.text = if (nx != null) fmtTime(nx.second) else "--"
            val ts = TaskStore.all(this).map { it.optLong("at") }.filter { it > now } +
                ReminderStore.all(this).filter { it.enabled && it.nextTriggerMs > now }.map { it.nextTriggerMs }
            cardRemTv.text = if (ts.isEmpty()) "None" else fmtTime(ts.minOrNull() ?: 0L)
        }
        val w = Weather.cached(this)
        if (w != null) {
            cardWeatherSub.text = w.second
            cardWeatherTv.text = w.first
        }
    }

    private fun weatherTap() {
        statusTv.text = "Fetching weather..."
        Weather.refresh(this, true) {
            updateCards(true)
            statusTv.text = Weather.cached(this)?.let { "Weather: ${it.first}, ${it.second}" } ?: "Weather unavailable"
        }
    }

    private fun musicTap() {
        when {
            MusicPlayer.isPlaying() -> statusTv.text = MusicPlayer.pause()
            MusicPlayer.current != null -> statusTv.text = MusicPlayer.resume()
            else -> {
                statusTv.text = "Searching songs..."
                Thread { val r = MusicPlayer.playQuery(this, "", "random"); h.post { statusTv.text = r } }.start()
            }
        }
        updateCards()
    }

    private fun musicDialog() {
        val items = listOf<Pair<String, () -> Unit>>(
            "🔍 Identify / Analyze Playing Song" to { NovaHubToolsUi.showSongAnalyzer(this, SunsetColors.CORAL) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) } },
            "▶ Shuffle Phone Songs" to { Thread { val r = MusicPlayer.playQuery(this, "", "random"); h.post { statusTv.text = r } }.start() },
            "⏯ Pause / Resume" to { statusTv.text = if (MusicPlayer.isPlaying()) MusicPlayer.pause() else MusicPlayer.resume() },
            "⏭ Next" to { statusTv.text = MusicPlayer.next() },
            "⏮ Previous" to { statusTv.text = MusicPlayer.prev() },
            "⏹ Stop" to { statusTv.text = MusicPlayer.stop() }
        )
        AlertDialog.Builder(this).setTitle("🎵 Music Controls").setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
    }

    // ==================== CHAT / AUDIO SHARE ====================
    private fun addTextBubble(role: String, text: String): TextView {
        val isUser = role == "user"
        val wrap = LinearLayout(this).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (isUser) Color.WHITE else SunsetColors.TEXT_PRIMARY)
            setTextIsSelectable(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = if (isUser) {
                SunsetUiHelper.gradientPill(this@MainActivity, 18)
            } else {
                SunsetUiHelper.glassCard(this@MainActivity, 18, bg = Color.WHITE, stroke = SunsetColors.GLASS_STROKE)
            }
        }
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
        wrap.addView(tv, lp(-2, -2))
        chatList.addView(wrap, lp(-1, -2))
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun sendTyped() {
        val raw = chatInput.text.toString().trim()
        if (raw.isEmpty()) return
        val t = NovaRepo.expandText(this, raw)
        chatInput.setText("")
        if (active && currentTab == 0) {
            LiveService.sendText(t)
            return
        }
        switchTab(1)
        sendToTextChat(t)
    }

    private fun sendToTextChat(prompt: String) {
        addTextBubble("user", prompt)
        textTurns.add("user" to prompt)
        ChatLog.add("user", prompt)
        val replyView = addTextBubble("nova", "Thinking...")
        statusTv.text = "Generating reply..."

        Thread {
            val replyText = try {
                val low = prompt.lowercase()
                if (low.contains("show source code") || low.contains("view source") || low.contains("page source")) {
                    VoiceIntelligence.offlineReply(applicationContext, prompt)
                } else if (!Net.online(applicationContext) || Keys.active(applicationContext).isBlank()) {
                    VoiceIntelligence.offlineReply(applicationContext, prompt)
                } else when {
                    AiRouter.isHostingRequest(prompt) -> AiRouter.openHosting(applicationContext, prompt)
                    AiRouter.isCodeRequest(prompt) -> {
                        val res = AiRouter.routeCodeTask(applicationContext, prompt) { p -> h.post { statusTv.text = p } }
                        if (res.ok) "${res.message}\nBackup: ${res.backupDir}\nZip: ${res.zipPath}\n\n${res.code.take(1500)}" else res.message
                    }
                    else -> callGeminiTextOnly(prompt)
                }
            } catch (_: Throwable) {
                VoiceIntelligence.offlineReply(applicationContext, prompt)
            }
            textTurns.add("model" to replyText)
            ChatLog.add("nova", replyText)
            h.post {
                replyView.text = replyText
                statusTv.text = I18n.t(this@MainActivity, "ready")
                chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
            }
            if (speakReplies && !Vol.speakerMuted && replyText.isNotBlank()) {
                VoiceEngine.speakBlocking(applicationContext, replyText.take(600))
            }
        }.start()
    }

    private fun callGeminiTextOnly(prompt: String): String {
        val detectedLang = I18n.detectLanguage(prompt)
        val normalized = I18n.normalizeUrduDialect(prompt)
        val persona = Personas.current(this)
        val user = Personas.userName(this)
        val sys = persona.prompt(user) +
            "\nReply in the user's detected language ($detectedLang). Dialect normalization: $normalized"
        val contents = JSONArray()
        for ((role, txt) in textTurns.takeLast(12)) {
            val r = if (role == "user") "user" else "model"
            contents.put(JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", txt))))
        }
        if (contents.length() == 0) contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
        val res = GeminiRest.post(applicationContext, body)
        if (!res.ok) return VoiceIntelligence.offlineReply(applicationContext, prompt)
        return try {
            val parts = JSONObject(res.text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            buildString { if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "") ?: "") }.ifBlank { "Done." }
        } catch (_: Exception) { res.text.take(1200) }
    }

    private fun handleSharedAudio(uri: Uri, rawMime: String) {
        switchTab(1)
        statusTv.text = "Loading shared voice note..."
        Thread {
            try {
                val rawType = (contentResolver.getType(uri) ?: rawMime).ifBlank { "audio/ogg" }
                val resolvedMime = if (rawType.equals("application/ogg", ignoreCase = true)) "audio/ogg" else rawType
                val ext = when {
                    resolvedMime.contains("mp4") || resolvedMime.contains("m4a") -> "m4a"
                    resolvedMime.contains("mpeg") || resolvedMime.contains("mp3") -> "mp3"
                    resolvedMime.contains("wav") -> "wav"
                    else -> "ogg"
                }
                var displayName = "voice_note.$ext"
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && idx >= 0) displayName = c.getString(idx) ?: displayName
                    }
                } catch (_: Exception) {}
                val localFile = File(cacheDir, "shared_audio_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(uri)?.use { inp -> localFile.outputStream().use { out -> inp.copyTo(out) } }
                h.post {
                    addAudioCard(displayName, localFile)
                    val replyView = addTextBubble("nova", "Transcribing shared voice note...")
                    Thread {
                        val b64 = Base64.encodeToString(localFile.readBytes(), Base64.NO_WRAP)
                        val body = JSONObject().put("contents", JSONArray().put(
                            JSONObject().put("role", "user").put("parts", JSONArray()
                                .put(JSONObject().put("text", "Transcribe this WhatsApp/shared voice message and reply helpfully in Urdu/Hinglish."))
                                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", resolvedMime).put("data", b64))))
                        ))
                        val res = GeminiRest.post(applicationContext, body)
                        val ans = if (res.ok) {
                            val parts = JSONObject(res.text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                            buildString { if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "") ?: "") }.ifBlank { res.text }
                        } else res.text
                        h.post { replyView.text = ans; statusTv.text = "Audio processed"; chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) } }
                    }.start()
                }
            } catch (e: Throwable) {
                h.post { statusTv.text = "Audio share failed: ${e.message}" }
            }
        }.start()
    }

    private fun addAudioCard(title: String, audioFile: File) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = SunsetUiHelper.glassCard(this@MainActivity, 14, SunsetColors.CORAL_BG, SunsetColors.CORAL)
        }
        val playBtn = Button(this).apply {
            text = "▶ Play"; setTextColor(Color.WHITE); isAllCaps = false; minimumHeight = dp(48)
            background = SunsetUiHelper.gradientPill(this@MainActivity, 12)
        }
        playBtn.setOnClickListener {
            val cur = activeAudioPlayer
            if (cur != null && cur.isPlaying) {
                try { cur.stop(); cur.release() } catch (_: Exception) {}
                activeAudioPlayer = null; playBtn.text = "▶ Play"; return@setOnClickListener
            }
            try {
                activeAudioPlayer?.release()
                activeAudioPlayer = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(audioFile.absolutePath)
                    setOnCompletionListener { playBtn.text = "▶ Play"; try { release() } catch (_: Exception) {}; activeAudioPlayer = null }
                    prepare(); start()
                }
                playBtn.text = "⏹ Stop"
            } catch (e: Exception) { statusTv.text = "Audio error: ${e.message}" }
        }
        card.addView(playBtn, lp(-2, dp(48)))
        card.addView(TextView(this).apply {
            text = "🎙 $title (${maxOf(1L, audioFile.length() / 1024)} KB)"
            setTextColor(SunsetColors.TEXT_PRIMARY); textSize = 13f; setPadding(dp(10), 0, 0, 0)
        }, lp(0, -2, 1f))
        chatList.addView(card, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ==================== LEGACY SETTINGS PANEL & OVERLAY CONTROLS ====================
    private fun settingsDialog() {
        fun flip(k: String, def: Boolean) { prefs.edit().putBoolean(k, !prefs.getBoolean(k, def)).apply() }
        val accent = Themes.current(this).accent
        val sections = listOf(
            SSection("NOVA CONTROL", listOf(
                SItem("mic", "Only NOVA mode", { "Respond when 'NOVA' wake word heard" }, null, { startMode(true) }),
                SItem("bolt", "Always listen mode", { "Stay continuously live" }, null, { startMode(false) }),
                SItem("close", "Off NOVA", { "Stop background voice session" }, null, { stopLive(); statusTv.text = "Stopped" }),
                SItem("vol_up", "Volume Boost", { "0-200% voice boost" }, null, { volumeDialog() })
            )),
            SSection("NOVA MODULES & TOOLS", FeatureUi.items(this, statusTv, h) { settingsDialog() }),
            SSection("SECURITY & SYSTEM", listOf(
                SItem("lock", "Screen Lock PIN", { if (Unlocker.hasPin(this)) "PIN saved ✅" else "No PIN set" }, null, { pinDialog() }),
                SItem("moon", "Azan Alerts", { "Scheduled Namaz reminders" }, { prefs.getBoolean("azan", true) }, {
                    val v = !prefs.getBoolean("azan", true); prefs.edit().putBoolean("azan", v).apply()
                    if (v) Prayer.scheduleNext(this) else Prayer.cancelAll(this)
                }),
                SItem("bolt", "Auto-skip video ads", { "Skip player ad button" }, { prefs.getBoolean("adskip", true) }, { flip("adskip", true) }),
                SItem("mic", "Auto start on launch", { "Start listening automatically" }, { prefs.getBoolean("auto", false) }, { flip("auto", false) }),
                SItem("camera", "Send Gallery Photo to Live AI", { "Analyze image in Live session" }, null, { pickPhoto() }),
                SItem("folder", "All Files Storage Permission", { "Required for direct NOVA-FILES access" }, null, { storagePerm() }),
                SItem("tune", "System Setup", { "API key, Accessibility & Termux" }, null, { startActivity(Intent(this, SetupActivity::class.java)) })
            ))
        )
        SettingsPanel.show(this, accent, sections)
    }

    private fun toggleBubble() {
        val opts = arrayOf(
            "Floating Button: ${if (prefs.getBoolean("bubble", true)) "ON" else "OFF"}",
            "Companion Overlay Pet: ${if (prefs.getBoolean("robot", true)) "ON" else "OFF"}"
        )
        AlertDialog.Builder(this)
            .setTitle("Overlay Avatars")
            .setItems(opts) { _, which ->
                if (which == 0) {
                    val v = !prefs.getBoolean("bubble", true)
                    prefs.edit().putBoolean("bubble", v).apply()
                } else {
                    val v = !prefs.getBoolean("robot", true)
                    RobotPet.setEnabled(this, v)
                }
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                statusTv.text = "Overlay updated (restart NOVA Live to apply)"
            }
            .setNegativeButton("Close", null).show()
    }

    private fun volumeDialog() {
        val bar = SeekBar(this).apply {
            max = 200; progress = prefs.getInt("vol", 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) { applyVol(p); prefs.edit().putInt("vol", p).apply() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        AlertDialog.Builder(this).setTitle("NOVA Volume (100+ = extra boost)")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(16), dp(20), dp(8)); addView(bar, lp(-1, -2)) })
            .setPositiveButton("OK", null).show()
    }

    private fun pinDialog() {
        val et = EditText(this).apply {
            hint = if (Unlocker.hasPin(this@MainActivity)) "PIN saved (empty to remove)" else "Device PIN"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Screen Lock PIN")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(10), dp(20), 0); addView(et, lp(-1, -2)) })
            .setPositiveButton("Save") { _, _ -> Unlocker.savePin(this, et.text.toString().trim()); statusTv.text = "PIN updated" }
            .setNegativeButton("Cancel", null).show()
    }

    private fun storagePerm() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else statusTv.text = "Storage permission granted"
    }

    private fun applyVol(p: Int) {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val f = if (p <= 100) p / 100f else 1f
        for (st in intArrayOf(android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.STREAM_MUSIC)) {
            try { am.setStreamVolume(st, Math.round(am.getStreamMaxVolume(st) * f), 0) } catch (_: Exception) {}
        }
        Vol.boost = if (p > 100) p / 100f else 1f
    }

    // ==================== WALLPAPER / CAMERA / FILE PICKERS ====================
    private fun pickWallpaper() {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_WALL) }
        catch (_: Exception) { statusTv.text = "Gallery not opened" }
    }

    private fun setLiveWallpaper() {
        try {
            startActivity(Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, android.content.ComponentName(this, NovaWallpaperService::class.java)))
        } catch (_: Exception) {
            try { startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (_: Exception) {}
        }
    }

    private fun setStaticWallpaper(flag: Int) {
        statusTv.text = "Applying wallpaper..."
        try {
            val dm = resources.displayMetrics; val w = dm.widthPixels; val hh = dm.heightPixels
            val v = CharacterView(this).apply { wallpaperMode = true; setTheme(Themes.current(this@MainActivity)); applyPrefs(); forceNoClock = true }
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY)); v.layout(0, 0, w, hh)
            val bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp))
            Thread { val msg = WallpaperWidgets.applySystemWallpaper(applicationContext, bmp, flag == 3, flag == 2); h.post { statusTv.text = msg } }.start()
        } catch (e: Throwable) { statusTv.text = "Wallpaper error: ${e.message}" }
    }

    private fun camFile() = File(cacheDir, "cam.jpg")
    private fun takePhoto() {
        if (!active) { statusTv.text = "Start NOVA Mic first for Camera Vision"; return }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAM_PERM); return }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fp", camFile())
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION), REQ_CAM)
        } catch (e: Exception) { statusTv.text = "Camera error: ${e.message}" }
    }
    private fun pickPhoto() {
        if (!active) { statusTv.text = "Start NOVA Mic first"; return }
        try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PICK) } catch (_: Exception) {}
    }

    @Deprecated("simple")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        if (req == REQ_FILE) { val u = data?.data ?: return; SunsetDialogs.handlePickedFile(this, u, fileMode, h) { statusTv.text = it }; return }
        if (req == REQ_WALL) {
            val u = data?.data ?: return
            Thread {
                val t = Themes.addCustom(applicationContext, u)
                runOnUiThread { if (t != null) { Themes.set(this, t.id); statusTv.text = "Wallpaper added: ${t.name}" } }
            }.start(); return
        }
        val uri: Uri? = if (req == REQ_CAM) Uri.fromFile(camFile()) else data?.data
        if (uri != null) Thread {
            val jpg = Media.jpegFromUri(this, uri)
            runOnUiThread { statusTv.text = if (jpg != null) { LiveService.sendPhoto(jpg); "Photo sent to Live AI" } else "Failed to read photo" }
        }.start()
    }

    // ==================== LIFECYCLE / LIVE SESSION ====================
    private fun askStartupPerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (need.isNotEmpty() && !prefs.getBoolean("asked_media", false)) { prefs.edit().putBoolean("asked_media", true).apply(); requestPermissions(need.toTypedArray(), REQ_MEDIA) }
    }

    override fun onResume() {
        super.onResume()
        WallBus.addFeedListener(feedListener)
        active = LiveService.isRunning()
        if (active) statusTv.text = LiveService.lastStatus
        LiveService.onStatus = { statusTv.text = it }
        LiveService.onSpeaking = { orbView.speaking = it }
        LiveService.onEnd = { active = false; orbView.speaking = false; paint() }
        refreshMute(); paint(); updateCards(true); refreshAllUi()
        Weather.refresh(this) { updateCards(true) }
        tickRun?.let { h.removeCallbacks(it) }
        tickRun = object : Runnable {
            override fun run() {
                tickN++; updateCards()
                if (tickN % 900 == 0) Weather.refresh(this@MainActivity) { updateCards(true) }
                h.postDelayed(this, 1000)
            }
        }
        h.post(tickRun!!)
        if (!active && !autoTried && prefs.getBoolean("auto", false) &&
            (prefs.getString("k", "") ?: "").isNotEmpty() && SetupActivity.accessibilityOn(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) { autoTried = true; start() }
    }

    override fun onPause() {
        WallBus.removeFeedListener(feedListener)
        tickRun?.let { h.removeCallbacks(it) }
        LiveService.onStatus = null; LiveService.onSpeaking = null; LiveService.onEnd = null
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) try { ClipStore.readNow(this)?.let { ClipStore.add(this, it) } } catch (_: Exception) {}
    }

    private fun refreshMute() {
        muteNovaPill.text = if (Vol.speakerMuted) "🔇 Voice OFF" else "🔊 Voice ON"
        muteNovaPill.setTextColor(if (Vol.speakerMuted) SunsetColors.PINK else SunsetColors.TEXT_PRIMARY)
        muteMicPill.text = if (Vol.micMuted) "🎙 Mic MUTED" else "🎙 Mic ON"
        muteMicPill.setTextColor(if (Vol.micMuted) SunsetColors.PINK else SunsetColors.TEXT_PRIMARY)
    }

    private fun paint() {
        orbView.active = active
        navBar.micActive = active
    }

    private fun toggle() { if (active) { stopLive(); statusTv.text = "Stopped" } else start() }

    private fun start() {
        if (!Net.online(this)) { statusTv.text = "No internet connection"; return }
        if ((prefs.getString("k", "") ?: "").isEmpty()) {
            statusTv.text = "Add Gemini API key in Setup first"
            startActivity(Intent(this, SetupActivity::class.java)); return
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) { requestPermissions(need.toTypedArray(), 1); return }
        if (!SetupActivity.accessibilityOn(this)) {
            statusTv.text = "Enable NOVA Accessibility in Setup first"
            startActivity(Intent(this, SetupActivity::class.java)); return
        }
        active = true
        statusTv.text = "Connecting..."
        startForegroundService(Intent(this, LiveService::class.java))
        paint()
    }

    private fun startMode(wake: Boolean) {
        prefs.edit().putBoolean("wake", wake).putBoolean("college", prefs.getBoolean("college", false) && wake).apply()
        if (LiveService.isRunning()) { stopLive(); h.postDelayed({ start() }, 700) } else start()
    }

    private fun stopLive() {
        try { LiveService.liveClient?.stop() } catch (_: Exception) {}
        try { LiveService.instance?.stopSelf() } catch (_: Exception) {}
        stopService(Intent(this, LiveService::class.java))
        active = false; orbView.speaking = false; paint()
    }

    override fun onDestroy() {
        try { activeAudioPlayer?.release() } catch (_: Exception) {}
        activeAudioPlayer = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent); setIntent(intent); handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val action = intent.action; val mime = intent.type ?: ""
        if (action == Intent.ACTION_SEND && (mime.startsWith("audio/") || mime.equals("application/ogg", ignoreCase = true))) {
            val streamUri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            if (streamUri != null) { handleSharedAudio(streamUri, mime); return }
        }
        val req = ChromeBridge.parseIncomingIntent(intent) ?: return
        val prompt = ChromeBridge.buildAssistantPrompt(req)
        switchTab(1)
        if (action == Intent.ACTION_SEND && mime == "text/plain" && prompt.isNotBlank()) {
            sendToTextChat(prompt); statusTv.text = "Shared text sent to Chat"; return
        }
        if (prompt.isNotBlank()) { chatInput.setText(prompt); chatInput.setSelection(prompt.length) }
        chatInput.post {
            chatInput.requestFocus()
            try { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT) } catch (_: Exception) {}
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == REQ_MEDIA) return
        if (code == REQ_CAM_PERM) { if (res.firstOrNull() == PackageManager.PERMISSION_GRANTED) takePhoto(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else statusTv.text = "Microphone permission required"
    }
}
