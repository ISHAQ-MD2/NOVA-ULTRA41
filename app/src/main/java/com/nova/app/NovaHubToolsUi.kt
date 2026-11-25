package com.nova.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * UI Dialogs for Voice Intelligence, Song Search & Analysis, Islamic Tools (Quran + Live Qibla Compass),
 * AI Learning/News/Translator, Portfolio Signals (Informational Only), Expenses, Pomodoro, Converter & Dictionary.
 */
object NovaHubToolsUi {
    private fun dp(ctx: Context, x: Int) = (x * ctx.resources.displayMetrics.density).toInt()
    private fun dlg(act: Activity) = AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun styledBtn(ctx: Context, label: String, accent: Int, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label; isAllCaps = false; textSize = 13f * I18n.fontScale(ctx)
        setTextColor(Color.WHITE); minimumHeight = dp(ctx, 48)
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        background = GradientDrawable().apply { cornerRadius = dp(ctx, 14).toFloat(); setColor(Color.parseColor("#161D30")); setStroke(dp(ctx, 1), accent) }
        setOnClickListener { onClick() }
    }

    // ---- Feature 16 Extra: Song Search & Audio Analysis ----
    fun showSongAnalyzer(act: Activity, accent: Int, onReqMic: () -> Unit) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val et = EditText(act).apply {
            hint = "Song title, lyrics, or leave blank for playing track"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setText(MusicPlayer.current?.title ?: "")
        }
        val out = TextView(act).apply {
            text = if (MusicPlayer.current != null) "Active track: ${MusicPlayer.current!!.title}" else "Tap Search or Listen via Mic to identify & analyze any song."
            setTextColor(Color.parseColor("#DCE6F5")); textSize = 13f * I18n.fontScale(act); setPadding(0, dp(act, 12), 0, 0)
            setTextIsSelectable(true)
        }
        val row = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        val h = Handler(Looper.getMainLooper())
        fun runSearch(listenMic: Boolean) {
            if (listenMic && !VoiceIntelligence.hasMicPerm(act)) { onReqMic(); return }
            out.text = if (listenMic) "Listening to song audio (3s) & searching..." else "Searching & analyzing song..."
            val q = et.text.toString()
            Thread {
                val res = VoiceIntelligence.analyzeAndIdentifySong(act, q, listenMic)
                h.post {
                    out.text = "🎵 Song: ${res.songName}\n👤 Artist: ${res.artist}\n💿 Album: ${res.album}\n🎼 Genre: ${res.genre}\n📅 Released: ${res.releaseDate}\n\n${res.details}"
                }
            }.start()
        }
        row.addView(styledBtn(act, "🔍 Analyze & Search", accent) { runSearch(false) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(act, 6) })
        row.addView(styledBtn(act, "🎙 Listen Mic", accent) { runSearch(true) }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(et)
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })
        box.addView(ScrollView(act).apply { addView(out) }, LinearLayout.LayoutParams(-1, dp(act, 240)))
        dlg(act).setTitle("🎵 Song Search & Analysis").setView(box).setNegativeButton("Close", null).show()
    }

    // ---- Features 2, 3, 13, 14, 15, 17, 18, 20, 21, 22, 23: Voice Studio ----
    fun showVoiceStudio(act: Activity, accent: Int, onReqMic: () -> Unit) {
        val h = Handler(Looper.getMainLooper())
        val sampleFile = File(act.filesDir, "voice_profiles/user_sample.wav").apply { parentFile?.mkdirs() }
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val statusTv = TextView(act).apply {
            val cOk = VoiceIntelligence.hasVoiceConsent(act)
            text = "Consent: ${if (cOk) "GRANTED ✅" else "NOT GRANTED (required for self-voice cloning/changing)"}\nNoise Suppression: ON"
            setTextColor(Color.parseColor("#DCE6F5")); textSize = 12.5f * I18n.fontScale(act)
        }
        val metricsTv = TextView(act).apply {
            text = "Record a sample to see Pitch (Hz), Energy (dBFS), Mood, Speech Speed & Anomaly checks."
            setTextColor(Color.parseColor("#9FB4CE")); textSize = 12.5f; setPadding(0, dp(act, 10), 0, dp(act, 10))
        }
        val presetSpin = Spinner(act).apply {
            adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, arrayOf("Normal", "Deep / Formal", "Bright / Energetic", "Soft / Calm", "Robot / Persona"))
        }
        box.addView(statusTv)
        box.addView(styledBtn(act, "✅ Grant / Revoke Self-Voice Consent", accent) {
            if (VoiceIntelligence.hasVoiceConsent(act)) {
                VoiceIntelligence.revokeVoiceConsent(act)
                statusTv.text = "Consent REVOKED."
            } else {
                SystemSecurityTools.requestConsent(act, "voice_clone_and_change", "Self-Voice Cloning & Changer",
                    "I confirm I am cloning/modifying ONLY my own voice or have explicit verified consent. Output WAVs carry a 'NOVA-USER-CONSENTED-AUDIO' watermark & audit log.") {
                    VoiceIntelligence.grantVoiceConsent(act, act.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("u", "Owner") ?: "Owner")
                    statusTv.text = "Consent GRANTED ✅ (Watermarked & Audited)"
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })

        box.addView(styledBtn(act, "🎙 Record Clean Sample (3.5s Noise-Suppressed)", accent) {
            if (!VoiceIntelligence.hasMicPerm(act)) { onReqMic(); return@styledBtn }
            metricsTv.text = "Recording 3.5s with NoiseSuppressor + EchoCanceler..."
            Thread {
                val m = VoiceIntelligence.recordWithNoiseSuppression(act, sampleFile, 3500L)
                h.post {
                    metricsTv.text = if (m == null) "Recording failed."
                    else "Pitch: ${m.pitchHz.toInt()} Hz | RMS: ${m.rmsDb.toInt()} dBFS\nRate: ${"%.1f".format(Locale.US, m.speechRateEst)} bursts/s\nMood: ${m.moodLabel}\nAnomaly: ${m.anomalyNote}"
                }
            }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        box.addView(presetSpin, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })
        box.addView(styledBtn(act, "▶ Play Context-Changed Own Voice", accent) {
            val preset = presetSpin.selectedItem.toString().substringBefore(" ").lowercase()
            metricsTv.text = VoiceIntelligence.playModifiedVoice(act, sampleFile, preset)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        val bioRow = LinearLayout(act)
        bioRow.addView(styledBtn(act, "🧬 Enroll Voiceprint", accent) {
            if (!VoiceIntelligence.hasMicPerm(act)) { onReqMic(); return@styledBtn }
            metricsTv.text = "Speak for 3s to enroll..."
            Thread { val r = VoiceIntelligence.enrollVoicePrint(act); h.post { metricsTv.text = r } }.start()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(act, 6) })
        bioRow.addView(styledBtn(act, "🔐 Verify Voice", accent) {
            if (!VoiceIntelligence.hasMicPerm(act)) { onReqMic(); return@styledBtn }
            metricsTv.text = "Speak for 2.5s to verify..."
            Thread { val (_, msg) = VoiceIntelligence.verifyVoicePrint(act); h.post { metricsTv.text = msg } }.start()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(bioRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(metricsTv)
        dlg(act).setTitle("🎙 Voice Intelligence Studio").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Features 12, 76, 77, 78: Islamic Hub (Quran & Shan-e-Nuzool + Live Qibla Compass + Hijri + Dua) ----
    class QiblaCompassView(ctx: Context, private val accent: Int) : View(ctx) {
        var headingDeg = 0f
        var qiblaDeg = SystemSecurityTools.qiblaBearing().toFloat()
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2f; val cy = h / 2f
            val r = minOf(w, h) * 0.42f
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#0D1526"); c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = accent; c.drawCircle(cx, cy, r, p)
            // North marker & Qibla needle
            val relQibla = Math.toRadians((qiblaDeg - headingDeg - 90f).toDouble())
            val qx = cx + (r * 0.78f * cos(relQibla)).toFloat()
            val qy = cy + (r * 0.78f * sin(relQibla)).toFloat()
            p.color = Color.parseColor("#00E676"); p.strokeWidth = 8f; c.drawLine(cx, cy, qx, qy, p)
            p.style = Paint.Style.FILL; c.drawCircle(qx, qy, 12f, p)
            val relNorth = Math.toRadians((-headingDeg - 90f).toDouble())
            p.color = Color.parseColor("#FF5252"); p.textSize = 26f; p.textAlign = Paint.Align.CENTER
            c.drawText("N", cx + (r * 0.86f * cos(relNorth)).toFloat(), cy + (r * 0.86f * sin(relNorth)).toFloat() + 9f, p)
            p.color = Color.WHITE; p.textSize = 28f
            c.drawText("🕋 ${qiblaDeg.toInt()}°", cx, cy + r + 34f, p)
        }
    }

    fun showIslamicHub(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val hijriTv = TextView(act).apply {
            text = "🌙 Hijri: ${SystemSecurityTools.hijriDateString()}\n🕌 Next Namaz: ${Prayer.todayText().lines().firstOrNull() ?: ""}"
            setTextColor(Color.parseColor("#80FFD4")); textSize = 13.5f * I18n.fontScale(act)
        }
        val compass = QiblaCompassView(act, accent)
        val sm = act.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rm = FloatArray(9); private val orient = FloatArray(3)
            override fun onSensorChanged(ev: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rm, ev.values)
                SensorManager.getOrientation(rm, orient)
                compass.headingDeg = Math.toDegrees(orient[0].toDouble()).toFloat()
                compass.invalidate()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        val qInput = EditText(act).apply { hint = "Surah:Ayah (e.g. 2:255 or 93:1) or topic"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val qOut = TextView(act).apply {
            text = "🤲 Daily Dua:\n${SystemSecurityTools.DAILY_DUAS.random()}"
            setTextColor(Color.WHITE); textSize = 13f * I18n.fontScale(act); setPadding(0, dp(act, 10), 0, 0); setTextIsSelectable(true)
        }
        box.addView(hijriTv)
        box.addView(compass, LinearLayout.LayoutParams(-1, dp(act, 190)))
        box.addView(qInput)
        box.addView(styledBtn(act, "📖 Lookup Quran + Shan-e-Nuzool", accent) {
            val q = qInput.text.toString().ifBlank { "2:255" }
            qOut.text = "Fetching Quran & Shan-e-Nuzool for $q..."
            Thread { val r = ProductivityEngine.lookupQuran(act, q); h.post { qOut.text = r } }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(styledBtn(act, "📿 Next Dua / Zikr", accent) {
            qOut.text = "🤲 Daily Dua:\n${SystemSecurityTools.DAILY_DUAS.random()}"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(qOut)
        val d = dlg(act).setTitle("🕌 Quran, Qibla & Hijri").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).create()
        d.setOnDismissListener { try { sm?.unregisterListener(listener) } catch (_: Exception) {} }
        d.show()
    }

    // ---- Features 25, 28, 29, 30, 31, 46: AI Tutor, News Presenter, Summarizer, Translator ----
    fun showAiProductivityDialog(act: Activity, accent: Int, initialTab: String = "tutor") {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val et = EditText(act).apply {
            hint = when (initialTab) {
                "news" -> "News topic (blank = top headlines)"
                "sum" -> "Paste article URL or long text to summarize & copy"
                else -> "Topic for AI Tutor / Meeting notes / Text to translate"
            }
            minLines = 3; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        val out = TextView(act).apply { setTextColor(Color.WHITE); textSize = 13f * I18n.fontScale(act); setPadding(0, dp(act, 10), 0, 0); setTextIsSelectable(true) }
        box.addView(et)
        box.addView(styledBtn(act, "🎓 AI Tutor Lesson + Quiz", accent) {
            out.text = "Preparing lesson & quiz..."
            Thread { val r = ProductivityEngine.generateTutorLesson(act, et.text.toString()); h.post { out.text = r } }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(styledBtn(act, "📰 Morning News Brief + Voice Speak", accent) {
            out.text = "Fetching Top 5 headlines..."
            Thread {
                val r = ProductivityEngine.morningNewsBrief(act, et.text.toString())
                h.post { out.text = r }
                if (!Vol.speakerMuted) VoiceEngine.speakBlocking(act.applicationContext, r.take(500))
            }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(styledBtn(act, "📝 Summarize & Copy to Clipboard", accent) {
            out.text = "Summarizing..."
            Thread {
                val r = ProductivityEngine.summarizeText(act, et.text.toString())
                ClipStore.putOnClipboard(act, r)
                h.post { out.text = "$r\n\n✅ Copied to clipboard!" }
            }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(out)
        dlg(act).setTitle("🧠 AI Learning, News & Summary").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Features 47, 48, 66, 67, 68, 69: Finance, Pomodoro, Converter, Dictionary ----
    fun showPortfolioDialog(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val sym = EditText(act).apply { hint = "Symbol (e.g. BTC, ETH, SOL)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val qty = EditText(act).apply { hint = "Quantity (e.g. 0.5)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val buy = EditText(act).apply { hint = "Buy price USD"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val out = TextView(act).apply { text = "Loading live Binance feed..."; setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(act, 10), 0, 0) }
        fun reload() {
            Thread { val r = ProductivityEngine.portfolioSummary(act); h.post { out.text = r } }.start()
        }
        reload()
        box.addView(sym); box.addView(qty); box.addView(buy)
        box.addView(styledBtn(act, "➕ Add Watchlist Asset", accent) {
            val s = sym.text.toString().trim()
            val q = qty.text.toString().toDoubleOrNull() ?: 1.0
            val b = buy.text.toString().toDoubleOrNull() ?: 0.0
            if (s.isNotEmpty()) { NovaRepo.addPortfolio(act, s, "CRYPTO", q, b, 0.0, 0.0); reload() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(out)
        dlg(act).setTitle("📈 Portfolio & Signals (Info Only)").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    fun showExpensesDialog(act: Activity, accent: Int) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val amt = EditText(act).apply { hint = "Amount (e.g. 450)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val cat = EditText(act).apply { hint = "Category (Food, Bills, Travel)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val out = TextView(act).apply { setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(act, 10), 0, 0) }
        fun refresh() {
            val items = NovaRepo.getExpenses(act)
            val total = items.sumOf { it.amount }
            out.text = "Total: %.2f PKR\n\n".format(Locale.US, total) + items.take(25).joinToString("\n") { "• ${it.category}: ${it.amount} ${it.currency}" }
        }
        refresh()
        box.addView(amt); box.addView(cat)
        box.addView(styledBtn(act, "💾 Add Expense", accent) {
            val a = amt.text.toString().toDoubleOrNull() ?: return@styledBtn
            NovaRepo.addExpense(act, a, "PKR", cat.text.toString().ifBlank { "General" }, "")
            amt.setText(""); refresh()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(out)
        dlg(act).setTitle("💸 Expense Tracker").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    fun showPomodoroDialog(act: Activity, accent: Int) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(act, 20), dp(act, 16), dp(act, 20), dp(act, 16)) }
        val tv = TextView(act).apply { text = "25:00"; textSize = 44f; setTextColor(accent); gravity = Gravity.CENTER }
        var timer: CountDownTimer? = null
        fun startMin(m: Int) {
            timer?.cancel()
            timer = object : CountDownTimer(m * 60_000L, 1000L) {
                override fun onTick(ms: Long) {
                    val s = ms / 1000
                    tv.text = "%02d:%02d".format(s / 60, s % 60)
                }
                override fun onFinish() {
                    tv.text = "Done!"
                    Thread { VoiceEngine.speakBlocking(act.applicationContext, "Pomodoro session complete. Take a break!") }.start()
                }
            }.start()
        }
        box.addView(tv)
        box.addView(styledBtn(act, "▶ Start 25m Focus", accent) { startMin(25) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 10) })
        box.addView(styledBtn(act, "☕ Start 5m Break", accent) { startMin(5) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        val d = dlg(act).setTitle("🍅 Pomodoro Focus Timer").setView(box).setNegativeButton("Close", null).create()
        d.setOnDismissListener { timer?.cancel() }
        d.show()
    }

    fun showConverterAndDictDialog(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val vEt = EditText(act).apply { hint = "Value (e.g. 100) or Dictionary Word"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val pairEt = EditText(act).apply { hint = "Units/Currency: e.g. USD to PKR, km to mi, c to f"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val out = TextView(act).apply { setTextColor(Color.WHITE); textSize = 13.5f; setPadding(0, dp(act, 10), 0, 0); setTextIsSelectable(true) }
        box.addView(vEt); box.addView(pairEt)
        box.addView(styledBtn(act, "🔄 Convert Unit / Currency", accent) {
            val v = vEt.text.toString().toDoubleOrNull() ?: 1.0
            val parts = pairEt.text.toString().lowercase().split("to", "->", " ").filter { it.isNotBlank() }
            if (parts.size < 2) { out.text = "Enter e.g. 'USD to PKR' or 'km to mi'"; return@styledBtn }
            out.text = "Converting..."
            Thread { val r = SystemSecurityTools.convertUnit(v, parts.first(), parts.last()); h.post { out.text = r } }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(styledBtn(act, "📚 Offline EN/UR/HI Dictionary", accent) {
            out.text = SystemSecurityTools.lookupDictionary(vEt.text.toString())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(out)
        dlg(act).setTitle("🔄 Converter & Dictionary").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }
}
