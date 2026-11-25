package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * Full-featured NOVA Browser Activity:
 * - Top bar with Incognito/Security lock badge, Reload, URL/Search (supports view-source:<url>), Tabs badge, Menu
 * - View Page Source bar (Copy Source, Search in Source, Exit Source)
 * - Compact Bottom Toolbar with View Source shortcut, Home, Back, Videos, Downloads, Menu
 * - HTTP unsafe warning banner & Find-in-page bar
 * - Floating "Videos (N)" sniffer button -> Play in VideoPlayerActivity / DownloadVault (with HLS Quality Picker)
 */
class BrowserActivity : Activity() {
    lateinit var webBox: FrameLayout
    private lateinit var rootFrame: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var sourceBar: LinearLayout
    private lateinit var unsafeBanner: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var urlBar: EditText
    private lateinit var secBadge: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tabBtn: TextView
    private lateinit var reloadBtn: TextView
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private lateinit var sourceShortcutBtn: TextView
    private lateinit var videoFloatBtn: Button
    private var customBox: FrameLayout? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var pendingUrl: String? = null
    private var lastCredPromptHost = ""

    private val bgCol = Color.parseColor("#0B1220")
    private val barCol = Color.parseColor("#141E33")
    private val accent = Color.parseColor("#FF643D")
    private val cyanAccent = Color.parseColor("#00E5FF")

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        NovaBrowser.init(this)
        window.statusBarColor = bgCol
        window.navigationBarColor = barCol
        buildUi()
        takeIntent(intent)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        takeIntent(i)
    }

    private fun takeIntent(i: Intent?) {
        if (i?.getBooleanExtra("view_source", false) == true) {
            val u = i.getStringExtra("url") ?: ""
            pendingUrl = if (u.isNotBlank()) "view-source:${u.removePrefix("view-source:")}" else "view-source-current"
            return
        }
        val u = i?.getStringExtra("url") ?: i?.dataString
        if (!u.isNullOrBlank()) pendingUrl = u
    }

    override fun onStart() {
        super.onStart()
        NovaBrowser.bindActivity(this)
        NovaBrowser.onChange = { refreshUi() }
        NovaBrowser.onCredsCaptured = { host, user, pass -> promptSavePassword(host, user, pass) }
        NovaBrowser.onVideoTapped = { url -> showVideoActionSheet(url) }
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        val u = pendingUrl
        pendingUrl = null
        when {
            u == "view-source-current" -> NovaBrowser.openViewSource()
            u != null -> NovaBrowser.routeUrl(u)
            else -> NovaBrowser.restoreTabsIfNeeded()
        }
        refreshUi()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            hideCustom()
            NovaBrowser.onChange = null
            NovaBrowser.onCredsCaptured = null
            NovaBrowser.onVideoTapped = null
            NovaBrowser.unbindActivity(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        NovaBrowser.unbindActivity(this)
        super.onDestroy()
    }

    @Deprecated("simple")
    override fun onBackPressed() {
        if (customView != null) { hideCustom(); return }
        if (findBar.visibility == View.VISIBLE) { toggleFindBar(false); return }
        val t = NovaBrowser.active
        if (t != null && t.isViewSource && t.sourceTargetUrl.isNotBlank()) {
            NovaBrowser.routeUrl(t.sourceTargetUrl)
            return
        }
        val w = t?.web
        when {
            w != null && w.canGoBack() -> w.goBack()
            t != null && t.url != BrowserScripts.HOME_URL && t.url.isNotBlank() -> NovaBrowser.nav("home")
            else -> finish()
        }
    }

    private fun glyph(t: String, size: Float = 18f, desc: String = t, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        contentDescription = desc
        setOnClickListener { onClick() }
    }

    private fun smallActionBtn(t: String, fill: Int, desc: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        contentDescription = desc
        setPadding(dp(12), dp(6), dp(12), dp(6))
        minimumHeight = dp(40)
        background = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat() }
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        rootFrame = FrameLayout(this).apply { setBackgroundColor(bgCol); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        topBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(barCol); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        backBtn = glyph("←", 18f, "Back") {
            val t = NovaBrowser.active
            if (t != null && t.isViewSource && t.sourceTargetUrl.isNotBlank()) {
                NovaBrowser.routeUrl(t.sourceTargetUrl)
            } else {
                val w = t?.web
                if (w != null && w.canGoBack()) w.goBack()
                else if (t != null && t.url != BrowserScripts.HOME_URL) NovaBrowser.nav("home")
            }
        }
        fwdBtn = glyph("→", 18f, "Forward") { NovaBrowser.active?.web?.let { if (it.canGoForward()) it.goForward() } }
        reloadBtn = glyph("↻", 18f, "Reload") {
            val t = NovaBrowser.active
            if (t != null) {
                if (t.loading) t.web.stopLoading()
                else if (t.isViewSource) NovaBrowser.openViewSource(t.sourceTargetUrl)
                else NovaBrowser.nav("reload")
            }
        }
        secBadge = glyph("🔒", 13f, "Site security") {
            val t = NovaBrowser.active
            if (t != null && PasswordStore.get(this, t.url) != null) {
                NovaBrowser.autofillSavedCreds(t)
                toast("Autofilled saved login for ${PasswordStore.hostOf(t.url)}")
            } else {
                val msg = when {
                    t?.isViewSource == true -> "Page Source Inspector (Syntax Highlighted)"
                    t?.incognito == true -> "Incognito Tab (no history or password saves)"
                    t?.insecureHttp == true -> "Not Secure: Plain HTTP connection"
                    else -> "Secure HTTPS / Local page"
                }
                toast(msg)
            }
        }
        urlBar = EditText(this).apply {
            setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#7F8CA3")); hint = "Search, URL, or view-source:"
            textSize = 14f; setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(10), 0, dp(10), 0)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1F2B45")); cornerRadius = dp(18).toFloat() }
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    NovaBrowser.userGo(v.text.toString())
                    v.clearFocus()
                    try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) } catch (_: Exception) {}
                    true
                } else false
            }
        }
        tabBtn = glyph("1", 13f, "Tabs") { tabsDialog() }.apply {
            background = GradientDrawable().apply { setStroke(dp(2), Color.WHITE); cornerRadius = dp(6).toFloat() }
        }
        val menuBtn = glyph("⋮", 22f, "Browser Menu") { menuDialog() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(34), dp(48)))
        topBar.addView(fwdBtn, LinearLayout.LayoutParams(dp(32), dp(48)))
        topBar.addView(reloadBtn, LinearLayout.LayoutParams(dp(34), dp(48)))
        topBar.addView(secBadge, LinearLayout.LayoutParams(dp(26), dp(48)))
        topBar.addView(urlBar, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(2); rightMargin = dp(6) })
        topBar.addView(tabBtn, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(6) })
        topBar.addView(menuBtn, LinearLayout.LayoutParams(dp(34), dp(48)))
        col.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // View Source Action Toolbar (Copy Source, Search in Source, Exit Source)
        sourceBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#16233B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        val copySourceBtn = smallActionBtn("📋 Copy Source", accent, "Copy page source code") {
            val raw = NovaBrowser.active?.rawSource.orEmpty()
            if (raw.isNotBlank()) {
                copy(raw)
                toast("Source code copied (${raw.length} chars)")
            } else toast("No source loaded yet")
        }
        val searchSourceBtn = smallActionBtn("🔍 Search in Source", Color.parseColor("#253759"), "Search inside page source") {
            toggleFindBar(true)
        }
        val exitSourceBtn = smallActionBtn("✕ Close", Color.parseColor("#2B2335"), "Exit source view") {
            val target = NovaBrowser.active?.sourceTargetUrl ?: NovaBrowser.home()
            NovaBrowser.routeUrl(target)
        }
        sourceBar.addView(copySourceBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        sourceBar.addView(searchSourceBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        sourceBar.addView(exitSourceBtn, LinearLayout.LayoutParams(-2, -2))
        col.addView(sourceBar, LinearLayout.LayoutParams(-1, -2))

        // HTTP Warning Banner
        unsafeBanner = TextView(this).apply {
            text = "⚠ Unsafe HTTP connection (not encrypted)"
            setBackgroundColor(Color.parseColor("#7F1D1D"))
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        col.addView(unsafeBanner, LinearLayout.LayoutParams(-1, -2))

        // Find in page / Search in Source bar
        findBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#182542"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        findInput = EditText(this).apply {
            hint = "Find on page or source..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine()
            textSize = 13f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    NovaBrowser.active?.web?.findAllAsync(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        findBar.addView(findInput, LinearLayout.LayoutParams(0, -2, 1f))
        findBar.addView(glyph("▲", 14f, "Previous match") { NovaBrowser.active?.web?.findNext(false) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        findBar.addView(glyph("▼", 14f, "Next match") { NovaBrowser.active?.web?.findNext(true) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        findBar.addView(glyph("✕", 16f, "Close find") { toggleFindBar(false) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        col.addView(findBar, LinearLayout.LayoutParams(-1, -2))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = false; progressTintList = ColorStateList.valueOf(accent); visibility = View.INVISIBLE
        }
        col.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val webContainer = FrameLayout(this)
        webBox = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        webContainer.addView(webBox, FrameLayout.LayoutParams(-1, -1))

        videoFloatBtn = Button(this).apply {
            text = "▶ Videos (0)"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(24).toFloat() }
            visibility = View.GONE
            setOnClickListener { sniffedVideosDialog() }
        }
        webContainer.addView(videoFloatBtn, FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(18); rightMargin = dp(14)
        })

        col.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        // Compact Bottom Shortcut Toolbar (Back, Home, View Source, Videos, Downloads, Menu)
        bottomBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(barCol)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val bHome = glyph("🏠", 17f, "Browser Home") { NovaBrowser.nav("home") }
        sourceShortcutBtn = glyph("</>", 14f, "View Page Source") {
            val t = NovaBrowser.active
            if (t != null && t.isViewSource) NovaBrowser.routeUrl(t.sourceTargetUrl)
            else NovaBrowser.openViewSource()
        }
        val bVids = glyph("🎬", 17f, "Detected Videos") { sniffedVideosDialog() }
        val bDls = glyph("⬇", 17f, "Downloads Manager") { startActivity(Intent(this, DownloadsActivity::class.java)) }
        val bMenu = glyph("☰", 17f, "More Menu") { menuDialog() }
        for (btn in listOf(bHome, sourceShortcutBtn, bVids, bDls, bMenu)) {
            bottomBar.addView(btn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        col.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        rootFrame.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootFrame)
    }

    private fun toggleFindBar(show: Boolean) {
        findBar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            findInput.setText("")
            NovaBrowser.active?.web?.clearMatches()
        } else {
            findInput.requestFocus()
        }
    }

    private fun refreshUi() {
        try {
            val t = NovaBrowser.active
            if (!urlBar.hasFocus()) urlBar.setText(t?.url ?: "")
            val loading = t?.loading == true
            progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
            progress.progress = t?.progress ?: 0
            tabBtn.text = NovaBrowser.tabs.size.toString()
            reloadBtn.text = if (loading) "✕" else "↻"
            sourceBar.visibility = if (t?.isViewSource == true) View.VISIBLE else View.GONE
            sourceShortcutBtn.setTextColor(if (t?.isViewSource == true) accent else Color.WHITE)
            unsafeBanner.visibility = if (t?.insecureHttp == true) View.VISIBLE else View.GONE
            topBar.setBackgroundColor(if (t?.incognito == true) Color.parseColor("#1D162B") else barCol)
            val hasCred = t != null && PasswordStore.get(this, t.url) != null
            secBadge.text = when {
                t?.isViewSource == true -> "</>"
                t?.incognito == true -> "🕵"
                t?.insecureHttp == true -> "⚠"
                hasCred -> "🔑"
                else -> "🔒"
            }
            val vids = t?.sniffedVideos?.size ?: 0
            videoFloatBtn.visibility = if (vids > 0) View.VISIBLE else View.GONE
            if (vids > 0) videoFloatBtn.text = "▶ Videos ($vids)"
            val w = t?.web
            backBtn.alpha = if ((t?.isViewSource == true) || (w != null && w.canGoBack())) 1f else 0.35f
            fwdBtn.alpha = if (w != null && w.canGoForward()) 1f else 0.35f
        } catch (_: Exception) {}
    }

    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun copy(s: String) {
        try { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("nova", s)); toast("Copied") } catch (_: Exception) {}
    }

    private fun promptSavePassword(host: String, user: String, pass: String) {
        if (lastCredPromptHost == "$host|$user") return
        lastCredPromptHost = "$host|$user"
        dlg().setTitle("Save password for $host?")
            .setMessage("Store encrypted login for ${user.ifBlank { host }} in NOVA Vault?")
            .setPositiveButton("Save") { _, _ ->
                PasswordStore.save(this, host, user, pass)
                toast("Password saved securely")
                refreshUi()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    // ------------------------------------------------------------------ menu
    private fun menuDialog() {
        val t = NovaBrowser.active
        val prefs = getSharedPreferences("nova", Context.MODE_PRIVATE)
        val floatOn = prefs.getBoolean("nb_float", false)
        val items = arrayOf(
            "➕ New Tab",
            "🕵 New Incognito Tab",
            "🗂 Tabs (${NovaBrowser.tabs.size})",
            "🏠 Home",
            "</> View Page Source",
            "📖 Reading Mode",
            "📑 Save to Reading List",
            "📚 Reading List & Bookmarks",
            "⭐ Bookmark Page",
            "🕘 History",
            "⬇ Downloads Manager",
            "🎬 Detected Videos (${t?.sniffedVideos?.size ?: 0})",
            "🔍 Find on Page",
            "🛡 AdBlock: ${if (NovaBrowser.adBlockEnabled()) "ON" else "OFF"}",
            "🔒 Privacy VPN Shield: ${if (NovaVpnService.running) "ON" else "OFF"}",
            "🌙 Night Mode: ${if (NovaBrowser.nightMode()) "ON" else "OFF"}",
            "🔠 Text Zoom: ${NovaBrowser.textZoom()}%",
            "🖥 Desktop Site: ${if (t?.desktop == true) "ON" else "OFF"}",
            "🔑 Saved Passwords (${PasswordStore.list(this).size})",
            "📺 Picture-in-Picture (PiP)",
            "🪟 Floating Window: ${if (floatOn) "ON" else "OFF"}",
            "👤 Profile: ${NovaBrowser.profileLabel()}",
            "🔗 Copy Page URL",
            "🌐 Open in Chrome",
            "▶ Open URL in External Player",
            "🧹 Clear Browser Data & Cookies",
            "✖ Close Browser"
        )
        dlg().setTitle("NOVA Browser").setItems(items) { _, i ->
            when (i) {
                0 -> NovaBrowser.newTab(NovaBrowser.home(), true, false)
                1 -> { NovaBrowser.newTab(NovaBrowser.home(), true, true); toast("Incognito tab opened") }
                2 -> tabsDialog()
                3 -> NovaBrowser.nav("home")
                4 -> NovaBrowser.openViewSource()
                5 -> openReadingMode()
                6 -> if (t != null && t.url.startsWith("http")) {
                    NovaRepo.saveReadingItem(this, t.title, t.url)
                    toast("Saved to Reading List")
                }
                7 -> readingAndBookmarksDialog()
                8 -> if (t != null) { NovaBrowser.addBookmark(t.title, t.url); toast("Bookmarked") }
                9 -> listDialog("History", NovaBrowser.history(), true)
                10 -> startActivity(Intent(this, DownloadsActivity::class.java))
                11 -> sniffedVideosDialog()
                12 -> toggleFindBar(true)
                13 -> { NovaBrowser.setAdBlock(!NovaBrowser.adBlockEnabled()); toast("AdBlock ${if (NovaBrowser.adBlockEnabled()) "ON" else "OFF"}") }
                14 -> togglePrivacyVpn()
                15 -> { NovaBrowser.setNightMode(!NovaBrowser.nightMode()); toast("Night Mode ${if (NovaBrowser.nightMode()) "ON" else "OFF"}") }
                16 -> zoomDialog()
                17 -> if (t != null) NovaBrowser.setDesktop(t, !t.desktop)
                18 -> passwordsDialog()
                19 -> enterPip()
                20 -> {
                    val msg = NovaBrowser.setFloatPref(this, !floatOn)
                    toast(msg)
                    if (!floatOn && NovaBrowser.overlayOk()) finish()
                }
                21 -> profileDialog()
                22 -> if (t != null) copy(t.url)
                23 -> {
                    val u = t?.url?.takeIf { it.isNotBlank() && it != BrowserScripts.HOME_URL && !t.isViewSource } ?: "https://www.google.com"
                    ChromeBridge.openInChrome(this, u)
                }
                24 -> {
                    val u = t?.sniffedVideos?.firstOrNull() ?: t?.url ?: ""
                    if (u.startsWith("http")) openExternalPlayer(u) else toast("No media URL found")
                }
                25 -> dlg().setMessage("Clear all saved logins, cookies, passwords, and history?")
                    .setPositiveButton("Clear") { _, _ -> NovaBrowser.clearAllData(); toast("Browser data cleared") }
                    .setNegativeButton("Cancel", null).show()
                26 -> { NovaBrowser.window("close"); finish() }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun showVideoActionSheet(vUrl: String) {
        val t = NovaBrowser.active
        dlg().setTitle("Video Detected").setMessage(vUrl.take(90))
            .setPositiveButton("▶ Play in NOVA Player") { _, _ ->
                startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra("url", vUrl).putExtra("title", t?.title ?: "Web Video"))
            }
            .setNeutralButton("⬇ Download") { _, _ -> startVideoDownload(vUrl, t?.title ?: "") }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startVideoDownload(vUrl: String, title: String) {
        if (vUrl.lowercase().contains(".m3u8")) {
            toast("Inspecting HLS stream qualities...")
            Thread {
                val variants = DownloadVault.parseHlsQualities(vUrl)
                runOnUiThread {
                    if (variants.size > 1) {
                        val labels = (listOf("Auto (360p/480p Recommended)") + variants.map { "${it.label} (${it.bandwidth / 1000} kbps)" }).toTypedArray()
                        dlg().setTitle("Select Video Quality")
                            .setItems(labels) { _, which ->
                                val q = if (which == 0) DownloadVault.pickAutoQuality(variants) else variants[which - 1]
                                val task = DownloadVault.enqueue(this, vUrl, title, q)
                                toast("Downloading (${q?.label ?: "Auto"}) -> ${DownloadVault.TARGET_DISPLAY_DIR}: ${task.title}")
                            }
                            .setNegativeButton("Cancel", null).show()
                    } else {
                        val task = DownloadVault.enqueue(this, vUrl, title)
                        toast(if (task.status == DlStatus.UNSUPPORTED) task.error else "Downloading: ${task.title}")
                    }
                }
            }.start()
        } else {
            val task = DownloadVault.enqueue(this, vUrl, title)
            toast(if (task.status == DlStatus.UNSUPPORTED) task.error else "Downloading: ${task.title}")
        }
    }

    private fun openReadingMode() {
        val w = NovaBrowser.active?.web ?: return
        w.evaluateJavascript(BrowserScripts.JS_READER_EXTRACT) { raw ->
            try {
                val clean = org.json.JSONTokener(raw ?: "{}").nextValue().toString()
                val j = org.json.JSONObject(clean)
                val title = j.optString("title", "Reader View")
                val body = j.optString("text", "").ifBlank { "No readable article text found on this page." }
                val tv = TextView(this).apply {
                    text = "$title\n\n$body"
                    setTextColor(Color.parseColor("#EFE9D8"))
                    textSize = 16.5f
                    setLineSpacing(6f, 1.2f)
                    setPadding(dp(18), dp(16), dp(18), dp(18))
                    setTextIsSelectable(true)
                }
                val sv = android.widget.ScrollView(this).apply { setBackgroundColor(Color.parseColor("#14161D")); addView(tv) }
                dlg().setTitle("📖 Reader Mode").setView(sv)
                    .setPositiveButton("Summarize & Copy") { _, _ ->
                        Thread {
                            val sum = ProductivityEngine.summarizeText(this, body)
                            runOnUiThread { copy(sum); toast("Summary copied to clipboard") }
                        }.start()
                    }
                    .setNeutralButton("Save Offline") { _, _ ->
                        NovaRepo.saveReadingItem(this, title, NovaBrowser.active?.url ?: "", "Reader", body.take(2000))
                        toast("Saved to Reading List")
                    }
                    .setNegativeButton("Close", null).show()
            } catch (_: Exception) { toast("Reader mode failed on this page") }
        }
    }

    private fun readingAndBookmarksDialog() {
        val rl = NovaRepo.getReadingList(this)
        val bm = NovaBrowser.bookmarks()
        val combined = ArrayList<Pair<String, String>>()
        rl.forEach { combined.add("📖 ${it.title}" to it.url) }
        bm.forEach { combined.add("⭐ ${it.first}" to it.second) }
        listDialog("Reading List & Bookmarks", combined, false)
    }

    private fun togglePrivacyVpn() {
        if (NovaVpnService.running) {
            NovaVpnService.stop(this)
            toast("Privacy VPN Shield OFF")
        } else {
            val prep = android.net.VpnService.prepare(this)
            if (prep != null) {
                try { startActivityForResult(prep, 912) } catch (_: Exception) { toast("VPN unavailable") }
            } else {
                NovaVpnService.start(this)
                toast("Privacy DNS/VPN Shield ON")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 912 && resultCode == RESULT_OK) {
            NovaVpnService.start(this)
            toast("Privacy DNS/VPN Shield ON")
        }
    }

    private fun sniffedVideosDialog() {
        val t = NovaBrowser.active
        val list = t?.sniffedVideos?.toList() ?: emptyList()
        if (list.isEmpty()) {
            toast("No playable video streams detected on this page yet.")
            return
        }
        val names = list.mapIndexed { idx, u -> "${idx + 1}. ${u.substringAfterLast('/').substringBefore('?').ifBlank { u }.take(46)}" }.toTypedArray()
        dlg().setTitle("Detected Videos (${list.size})").setItems(names) { _, which ->
            val vUrl = list[which]
            dlg().setTitle(vUrl.take(65)).setItems(arrayOf("▶ Play in NOVA Video Player", "⬇ Download Video", "🔗 Copy Video URL", "📺 Open in External Player")) { _, act ->
                when (act) {
                    0 -> startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra("url", vUrl).putExtra("title", t?.title ?: "Web Video"))
                    1 -> startVideoDownload(vUrl, t?.title ?: "")
                    2 -> copy(vUrl)
                    3 -> openExternalPlayer(vUrl)
                }
            }.show()
        }.setNegativeButton("Close", null).show()
    }

    private fun openExternalPlayer(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
            startActivity(Intent.createChooser(i, "Open with Video Player"))
        } catch (e: Exception) {
            toast("No external player available: ${e.message}")
        }
    }

    private fun zoomDialog() {
        val cur = NovaBrowser.textZoom()
        val tv = TextView(this).apply { text = "Text Zoom: $cur%"; setTextColor(Color.WHITE); textSize = 15f; setPadding(dp(20), dp(14), dp(20), dp(6)) }
        val bar = SeekBar(this).apply {
            max = 150
            progress = cur - 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val z = p + 50
                    tv.text = "Text Zoom: $z%"
                    if (fromUser) NovaBrowser.setTextZoom(z)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(tv); addView(bar) }
        dlg().setTitle("Page Text Zoom").setView(box).setPositiveButton("OK", null).show()
    }

    private fun passwordsDialog() {
        val creds = PasswordStore.list(this)
        if (creds.isEmpty()) {
            dlg().setTitle("Saved Passwords").setMessage("No passwords saved yet. Sign in to any website in a normal tab to save securely.").setPositiveButton("OK", null).show()
            return
        }
        val labels = creds.map { "${it.host} (${it.user.ifBlank { "user" }})" }.toTypedArray()
        dlg().setTitle("Saved Passwords (Encrypted)").setItems(labels) { _, i ->
            val c = creds[i]
            dlg().setTitle(c.host).setMessage("User: ${c.user}\nPassword: ${c.pass}")
                .setPositiveButton("Autofill Active Page") { _, _ -> NovaBrowser.active?.let { NovaBrowser.autofillSavedCreds(it) } }
                .setNeutralButton("Delete") { _, _ -> PasswordStore.remove(this, c.host); toast("Removed") }
                .setNegativeButton("Close", null).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun tabsDialog() {
        val tabs = NovaBrowser.tabs.toList()
        if (tabs.isEmpty()) { NovaBrowser.newTab(NovaBrowser.home(), true); return }
        val names = tabs.mapIndexed { i, t ->
            val badge = if (t.incognito) "[🕵] " else ""
            (if (t === NovaBrowser.active) "● " else "○ ") + "${i + 1}. $badge" + t.title.ifEmpty { t.url }.take(44)
        }.toTypedArray()
        dlg().setTitle("Tabs (${tabs.size})").setItems(names) { _, i -> NovaBrowser.setTabActive(tabs[i]) }
            .setPositiveButton("+ Tab") { _, _ -> NovaBrowser.newTab(NovaBrowser.home(), true, false) }
            .setNeutralButton("+ Incognito") { _, _ -> NovaBrowser.newTab(NovaBrowser.home(), true, true) }
            .setNegativeButton("Close Current") { _, _ ->
                NovaBrowser.active?.let { NovaBrowser.closeTab(it) }
                if (NovaBrowser.tabs.isEmpty()) NovaBrowser.newTab(NovaBrowser.home(), true)
            }.show()
    }

    private fun listDialog(title: String, items: List<Pair<String, String>>, canClear: Boolean) {
        if (items.isEmpty()) { dlg().setTitle(title).setMessage("Empty.").setPositiveButton("OK", null).show(); return }
        val names = items.map { (it.first.ifEmpty { it.second }).take(40) + "\n" + it.second.take(60) }.toTypedArray()
        val b = dlg().setTitle(title).setItems(names) { _, i -> NovaBrowser.userGo(items[i].second) }.setNegativeButton("Close", null)
        if (canClear) b.setNeutralButton("Clear") { _, _ -> NovaBrowser.clearHistory(); toast("History cleared") }
        b.show()
    }

    private fun profileDialog() {
        val opts = arrayOf("Personal Profile (your own accounts)", "Nova Profile (separate assistant accounts)")
        val cur = if (NovaBrowser.profileName() == "nova") 1 else 0
        dlg().setTitle("Browser Profile")
            .setSingleChoiceItems(opts, cur) { d, which ->
                val r = NovaBrowser.setProfile(if (which == 1) "nova" else "me")
                toast(r); d.dismiss()
                NovaBrowser.newTab(NovaBrowser.home(), true)
            }.setNegativeButton("Close", null).show()
    }

    fun onLongPress(hit: WebView.HitTestResult): Boolean {
        val extra = hit.extra ?: return false
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                dlg().setTitle(extra.take(70)).setItems(arrayOf("Open in New Tab", "Open in Incognito", "Open in Chrome", "Copy Link", "Download")) { _, i ->
                    when (i) {
                        0 -> NovaBrowser.newTab(extra, true, false)
                        1 -> NovaBrowser.newTab(extra, true, true)
                        2 -> ChromeBridge.openInChrome(this, extra)
                        3 -> copy(extra)
                        4 -> NovaBrowser.startDownload(extra, null, null, null, NovaBrowser.active?.url ?: "", NovaBrowser.active?.web)
                    }
                }.show()
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                dlg().setTitle("Image").setItems(arrayOf("Download Image", "Copy Image URL", "Open Image in New Tab")) { _, i ->
                    when (i) {
                        0 -> NovaBrowser.startDownload(extra, null, null, null, NovaBrowser.active?.url ?: "", NovaBrowser.active?.web)
                        1 -> copy(extra)
                        2 -> NovaBrowser.newTab(extra, true)
                    }
                }.show()
                true
            }
            else -> false
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
            catch (e: Exception) { toast("PiP unavailable: ${e.message}") }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        val vis = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        topBar.visibility = vis
        bottomBar.visibility = vis
        if (isInPictureInPictureMode) {
            unsafeBanner.visibility = View.GONE
            sourceBar.visibility = View.GONE
        }
    }

    fun showCustom(v: View, cb: WebChromeClient.CustomViewCallback?) {
        if (customView != null) { try { cb?.onCustomViewHidden() } catch (_: Exception) {}; return }
        customView = v; customCb = cb
        val box = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        box.addView(v, FrameLayout.LayoutParams(-1, -1))
        rootFrame.addView(box, FrameLayout.LayoutParams(-1, -1))
        customBox = box
        immersive(true)
    }

    fun hideCustom() {
        val b = customBox ?: return
        try { rootFrame.removeView(b) } catch (_: Exception) {}
        customBox = null; customView = null
        try { customCb?.onCustomViewHidden() } catch (_: Exception) {}
        customCb = null
        immersive(false)
    }

    @Suppress("DEPRECATION")
    private fun immersive(on: Boolean) {
        try {
            if (on) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = 0
            }
        } catch (_: Exception) {}
    }
}
