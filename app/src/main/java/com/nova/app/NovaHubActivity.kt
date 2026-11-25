package com.nova.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

/**
 * NOVA Command Hub:
 * - Redesigned glassmorphic dark HUD interface
 * - Optional App Lock gate (#96: PIN / Biometric)
 * - Multilingual Switcher: English / Urdu (RTL) / Hindi + Accessibility Scaling (#89)
 * - Category Filter + Live Search across all tools & Feature #100 toggles
 * - Explicit consent gates before enabling sensitive toggles (#3, #23, #41, #42, #83, #95, #99)
 */
class NovaHubActivity : Activity() {
    companion object {
        private const val REQ_MIC = 510
        private const val REQ_REC = 511
    }

    private var accent = Color.parseColor("#00E5FF")
    private var selectedCat = "All"
    private var searchQuery = ""
    private lateinit var rootList: LinearLayout
    private lateinit var searchEt: EditText
    private var unlocked = false

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        I18n.applyDirection(this)
        accent = Themes.current(this).accent

        val prefs = getSharedPreferences("nova", MODE_PRIVATE)
        if (prefs.getBoolean("feat_app_lock", false) && !unlocked) {
            showAppLockGate()
        } else {
            unlocked = true
            buildHubUi()
        }
    }

    private fun showAppLockGate() {
        SystemSecurityTools.authenticateBiometric(
            this,
            "Unlock NOVA Hub",
            onSuccess = { unlocked = true; buildHubUi() },
            onFallbackPin = {
                val et = EditText(this).apply {
                    hint = "Enter NOVA PIN (default 1234 if unset)"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
                }
                android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("🔒 NOVA App Lock")
                    .setView(et)
                    .setCancelable(false)
                    .setPositiveButton("Unlock") { _, _ ->
                        val entered = et.text.toString().trim()
                        val saved = SecurePrefs.getString(this, "hub_pin", "1234").ifBlank { "1234" }
                        if (entered == saved) {
                            unlocked = true; buildHubUi()
                        } else {
                            Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .setNegativeButton("Exit") { _, _ -> finish() }
                    .show()
            }
        )
    }

    private fun cardBg(strokeCol: Int = accent, fillHex: String = "#101626"): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (I18n.highContrast(this@NovaHubActivity)) Color.BLACK else Color.parseColor(fillHex))
            setStroke(dp(if (I18n.highContrast(this@NovaHubActivity)) 2 else 1), strokeCol)
        }

    private fun buildHubUi() {
        val scale = I18n.fontScale(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (I18n.highContrast(this@NovaHubActivity)) Color.BLACK else Color.parseColor("#060913"))
            setPadding(dp(12), dp(14), dp(12), dp(12))
        }

        // Top Bar: Back | Title | Language Switcher
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val backBtn = Button(this).apply {
            text = "←"; contentDescription = "Back to NOVA"
            setTextColor(Color.WHITE); textSize = 18f; minimumWidth = dp(48); minimumHeight = dp(48)
            background = cardBg()
            setOnClickListener { finish() }
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(10), 0) }
        titleCol.addView(TextView(this).apply {
            text = I18n.t(this@NovaHubActivity, "hub_title")
            textSize = 19f * scale; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        titleCol.addView(TextView(this).apply {
            text = I18n.t(this@NovaHubActivity, "hub_sub")
            textSize = 11.5f * scale; setTextColor(Color.parseColor("#8FA8C8"))
        })
        val langBtn = Button(this).apply {
            text = I18n.current(this@NovaHubActivity).name
            contentDescription = "Switch UI language"
            setTextColor(Color.BLACK); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; minimumHeight = dp(48)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(accent) }
            setOnClickListener {
                val langs = I18n.Lang.values()
                val next = langs[(I18n.current(this@NovaHubActivity).ordinal + 1) % langs.size]
                I18n.setLang(this@NovaHubActivity, next)
                recreate()
            }
        }
        header.addView(backBtn, lp(dp(48), dp(48)))
        header.addView(titleCol, lp(0, -2, 1f))
        header.addView(langBtn, lp(-2, dp(48)))
        root.addView(header)

        // Search input
        searchEt = EditText(this).apply {
            hint = I18n.t(this@NovaHubActivity, "search_hint")
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#778CA8"))
            textSize = 14f * scale; setSingleLine()
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = cardBg(Color.parseColor("#4400E5FF"), "#0E1424")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                    renderContent()
                }
            })
        }
        root.addView(searchEt, lp(-1, -2).apply { topMargin = dp(10) })

        // Category filter pills
        val cats = listOf("All", "Voice & AI", "Productivity", "Security & Privacy", "Islamic & Life", "System & Tools")
        val catRow = LinearLayout(this)
        fun refreshCatChips() {
            catRow.removeAllViews()
            for (c in cats) {
                val sel = c == selectedCat
                val tv = TextView(this).apply {
                    text = c; textSize = 12f * scale; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (sel) Color.BLACK else Color.WHITE)
                    setPadding(dp(14), dp(8), dp(14), dp(8)); gravity = Gravity.CENTER; minimumHeight = dp(40)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        if (sel) setColor(accent) else { setColor(Color.parseColor("#121A2B")); setStroke(dp(1), Color.parseColor("#44FFFFFF")) }
                    }
                    setOnClickListener { selectedCat = c; refreshCatChips(); renderContent() }
                }
                catRow.addView(tv, lp(-2, -2).apply { rightMargin = dp(6) })
            }
        }
        refreshCatChips()
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(catRow) }, lp(-1, -2).apply { topMargin = dp(8) })

        rootList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(rootList) }, lp(-1, 0, 1f).apply { topMargin = dp(8) })
        setContentView(root)
        renderContent()
    }

    private fun renderContent() {
        rootList.removeAllViews()
        val scale = I18n.fontScale(this)

        // Interactive Tool Launchers
        val tools = listOf(
            Triple("🎵 Song Search & Analysis", "Identify playing song or search lyrics/audio") { NovaHubToolsUi.showSongAnalyzer(this, accent) { reqMic() } },
            Triple("🎙 Voice Studio & Biometrics", "Consent voice clone, noise suppression, mood & pitch") { NovaHubToolsUi.showVoiceStudio(this, accent) { reqMic() } },
            Triple("🕌 Quran, Qibla & Hijri", "Shan-e-Nuzool lookup, live compass & daily Dua") { NovaHubToolsUi.showIslamicHub(this, accent) },
            Triple("🎓 AI Tutor, News & Summary", "Lessons, morning news presenter, clipboard summary") { NovaHubToolsUi.showAiProductivityDialog(this, accent) },
            Triple("📈 Portfolio & Trading Signals", "Live crypto tracker & informational alerts only") { NovaHubToolsUi.showPortfolioDialog(this, accent) },
            Triple("💸 Expense Tracker", "Track daily PKR/USD expenses locally") { NovaHubToolsUi.showExpensesDialog(this, accent) },
            Triple("🍅 Pomodoro Focus Timer", "25/5 focus timer with voice completion alert") { NovaHubToolsUi.showPomodoroDialog(this, accent) },
            Triple("🔄 Converter & Dictionary", "Units, live currency FX & EN/UR/HI dictionary") { NovaHubToolsUi.showConverterAndDictDialog(this, accent) },
            Triple("🔐 Encrypted Diary", "AES-GCM encrypted private journal") { NovaHubSecUi.showNotesAndDiary(this, accent, true) },
            Triple("📝 Smart Notes", "Local SQLite searchable notes") { NovaHubSecUi.showNotesAndDiary(this, accent, false) },
            Triple("☁ Vault & Cloud Backup", "Password .nova backups, Drive/Dropbox & Passwords") { NovaHubSecUi.showVaultAndCloudBackup(this, accent) },
            Triple("✂ Snippets & Urdu Helper", "Text expander (/sig), quick templates & Roman Urdu") { NovaHubSecUi.showSnippetsAndUrduTools(this, accent) },
            Triple("🧹 Storage Cleaner (Preview)", "Scan cache & duplicate files before deleting") { NovaHubSecUi.showStorageCleaner(this, accent) },
            Triple("🩺 Health, Battery & Charging", "Battery ETA, steps, app usage & self-diagnostics") { NovaHubSecUi.showSystemHealthDialog(this, accent) },
            Triple("📱 QR & Multi-Device Pair", "Generate QR matrices & pairing payloads") { NovaHubSecUi.showQrGenerator(this, accent) },
            Triple("✏ Screenshot Annotator", "Draw annotations & save PNG") { NovaHubSecUi.showScreenshotAnnotator(this, accent) },
            Triple("🛡 Security, Privacy & SOS", "Privacy audit, removable Admin, SOS & Bug Report") { NovaHubSecUi.showSecurityAndPrivacyDialog(this, accent, REQ_REC) }
        )

        if (searchQuery.isEmpty() && selectedCat == "All") {
            rootList.addView(TextView(this).apply {
                text = "QUICK LAUNCHERS"
                textSize = 12f * scale; setTextColor(accent); typeface = Typeface.MONOSPACE
                setPadding(dp(4), dp(4), 0, dp(6))
            })
            var idx = 0
            while (idx < tools.size) {
                val row = LinearLayout(this)
                for (col in 0..1) {
                    if (idx + col < tools.size) {
                        val (t, sub, act) = tools[idx + col]
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
                            background = cardBg(Color.parseColor("#5500E5FF"), "#10182B")
                            minimumHeight = dp(76)
                            setOnClickListener { act() }
                        }
                        card.addView(TextView(this).apply { text = t; setTextColor(Color.WHITE); textSize = 13f * scale; typeface = Typeface.DEFAULT_BOLD })
                        card.addView(TextView(this).apply { text = sub; setTextColor(Color.parseColor("#90A8C8")); textSize = 11f * scale; setPadding(0, dp(3), 0, 0) })
                        row.addView(card, lp(0, -2, 1f).apply { rightMargin = if (col == 0) dp(6) else 0 })
                    }
                }
                rootList.addView(row, lp(-1, -2).apply { bottomMargin = dp(8) })
                idx += 2
            }
        }

        // Feature #100: All-Module Searchable Toggles
        val filtered = FeatureRegistry.MODULES.filter { m ->
            (selectedCat == "All" || m.category == selectedCat) &&
                (searchQuery.isEmpty() || m.title.lowercase().contains(searchQuery) || m.desc.lowercase().contains(searchQuery) || "#${m.id}".contains(searchQuery))
        }
        rootList.addView(TextView(this).apply {
            text = "ALL FEATURE MODULES (${filtered.size})"
            textSize = 12f * scale; setTextColor(accent); typeface = Typeface.MONOSPACE
            setPadding(dp(4), dp(10), 0, dp(6))
        })

        for (m in filtered) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
                background = cardBg(Color.parseColor("#33FFFFFF"), "#0D1322")
            }
            val txtCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            txtCol.addView(TextView(this).apply {
                text = "#${m.id} ${m.title}"
                setTextColor(Color.WHITE); textSize = 13.5f * scale; typeface = Typeface.DEFAULT_BOLD
            })
            txtCol.addView(TextView(this).apply {
                text = "${m.category} • ${m.desc}"
                setTextColor(Color.parseColor("#92A6C4")); textSize = 11.5f * scale
            })
            val sw = Switch(this).apply {
                isChecked = FeatureRegistry.isEnabled(this@NovaHubActivity, m)
                contentDescription = "Toggle ${m.title}"
                minimumWidth = dp(48); minimumHeight = dp(48)
                setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked && m.requiresConsent && !getSharedPreferences("nova", MODE_PRIVATE).getBoolean(m.key, false)) {
                        buttonView.isChecked = false
                        SystemSecurityTools.requestConsent(this@NovaHubActivity, m.key.removePrefix("consent_"), m.title,
                            "Enable ${m.title}? ${m.desc}. You can revoke consent anytime here.") {
                            FeatureRegistry.setEnabled(this@NovaHubActivity, m, true)
                            buttonView.isChecked = true
                        }
                    } else {
                        FeatureRegistry.setEnabled(this@NovaHubActivity, m, isChecked)
                        if (m.key == "acc_large_text") recreate()
                    }
                }
            }
            row.addView(txtCol, lp(0, -2, 1f))
            row.addView(sw, lp(-2, -2))
            rootList.addView(row, lp(-1, -2).apply { bottomMargin = dp(6) })
        }
    }

    private fun reqMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    @Deprecated("simple")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_REC && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, ScreenRecordService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show()
        }
    }
}
