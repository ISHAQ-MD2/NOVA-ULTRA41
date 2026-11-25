package com.nova.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.hypot

/**
 * Sunset Peach Voice Biometrics Security Screen:
 * 1) 3x3 Pattern Lock Gate ("Voice Biometrics locked" / "Create Pattern Lock")
 * 2) Unlocked Dashboard:
 *    - Master toggle switch
 *    - OWNER VOICE: ACTIVE / PAUSED card
 *    - 3-Step Phrase Enrollment ("Hey Nova, wake up", TAP & SPEAK PHRASE, Sample 1/2/3)
 *    - Live Recognition Test ("TEST VOICE MATCH (SPEAK NOW)")
 *    - Sensitivity / Strictness selector (Strict / Balanced / Relaxed)
 */
class VoiceBiometricsActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var lockView: LinearLayout
    private lateinit var dashView: LinearLayout

    private lateinit var lockTitleTv: TextView
    private lateinit var lockSubTv: TextView
    private lateinit var lockPatternView: PatternLockView

    private lateinit var masterSwitch: Switch
    private lateinit var statusDot: View
    private lateinit var statusTitleTv: TextView
    private lateinit var trainStepTv: TextView
    private lateinit var trainHintTv: TextView
    private lateinit var tapSpeakBtn: Button
    private val sampleDots = ArrayList<TextView>()
    private lateinit var testResultTv: TextView
    private lateinit var testBtn: Button
    private val strictnessRows = LinkedHashMap<String, View>()

    private var unlocked = false
    private var creatingPatternMode = false
    private var isRecording = false

    private fun dp(x: Int) = SunsetUiHelper.dp(this, x)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = SunsetColors.PEACH_TOP
        window.navigationBarColor = SunsetColors.PEACH_BOTTOM
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        I18n.applyDirection(this)
        prefs = getSharedPreferences("nova", MODE_PRIVATE)

        val root = FrameLayout(this).apply {
            background = SunsetUiHelper.pageBg()
        }

        lockView = buildLockScreen()
        dashView = buildDashboardScreen()

        root.addView(dashView, FrameLayout.LayoutParams(-1, -1))
        root.addView(lockView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val savedPattern = prefs.getString("vb_pattern", "") ?: ""
        if (savedPattern.isBlank()) {
            enterCreatePatternMode()
        } else {
            enterVerifyPatternMode()
        }
    }

    // ==================== 1) 3x3 Pattern Lock Gate ====================
    private fun buildLockScreen(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = SunsetUiHelper.pageBg()
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        lockTitleTv = TextView(this).apply {
            text = "Voice Biometrics locked"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        }
        lockSubTv = TextView(this).apply {
            text = "Draw your pattern to continue."
            textSize = 14.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
        lockPatternView = PatternLockView(this).apply {
            onPatternComplete = { seq -> handlePatternInput(seq) }
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(SunsetColors.CORAL)
            minimumHeight = dp(52)
            background = SunsetUiHelper.outlinePill(this@VoiceBiometricsActivity, 28, SunsetColors.TEXT_SECONDARY)
            contentDescription = "Cancel Voice Biometrics unlock"
            setOnClickListener { finish() }
        }
        wrap.addView(lockTitleTv, lp(-1, -2).apply { topMargin = dp(24) })
        wrap.addView(lockSubTv, lp(-1, -2))
        wrap.addView(lockPatternView, lp(dp(290), dp(290)).apply {
            topMargin = dp(8)
            bottomMargin = dp(28)
        })
        wrap.addView(cancelBtn, lp(-1, dp(52)).apply { topMargin = dp(12) })
        return wrap
    }

    private fun enterVerifyPatternMode() {
        creatingPatternMode = false
        unlocked = false
        lockTitleTv.text = "Voice Biometrics locked"
        lockSubTv.text = "Draw your pattern to continue."
        lockSubTv.setTextColor(SunsetColors.TEXT_SECONDARY)
        lockView.visibility = View.VISIBLE
        dashView.visibility = View.GONE
    }

    private fun enterCreatePatternMode() {
        creatingPatternMode = true
        unlocked = false
        lockTitleTv.text = "Create Pattern Lock"
        lockSubTv.text = "Draw a pattern (4+ dots) to protect Voice Biometrics."
        lockSubTv.setTextColor(SunsetColors.TEXT_SECONDARY)
        lockView.visibility = View.VISIBLE
        dashView.visibility = View.GONE
    }

    private fun handlePatternInput(seq: String) {
        if (creatingPatternMode) {
            if (seq.length < 4) {
                lockSubTv.text = "Connect at least 4 dots to secure biometrics."
                lockSubTv.setTextColor(SunsetColors.PINK)
                lockPatternView.showError()
                return
            }
            prefs.edit().putString("vb_pattern", Crypto.enc(seq)).apply()
            Toast.makeText(this, "Pattern lock saved", Toast.LENGTH_SHORT).show()
            unlockDashboard()
        } else {
            val storedEnc = prefs.getString("vb_pattern", "") ?: ""
            val stored = Crypto.dec(storedEnc).ifEmpty { storedEnc }
            if (seq == stored) {
                unlockDashboard()
            } else {
                lockSubTv.text = "Incorrect pattern — try again."
                lockSubTv.setTextColor(SunsetColors.PINK)
                lockPatternView.showError()
            }
        }
    }

    private fun unlockDashboard() {
        unlocked = true
        lockView.visibility = View.GONE
        dashView.visibility = View.VISIBLE
        refreshDashboardUi()
    }

    // ==================== 2) Unlocked Dashboard ====================
    private fun buildDashboardScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.pageBg()
            visibility = View.GONE
        }

        // Top Header: <- + VOICE BIOMETRICS + Master Switch
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(16), dp(10))
        }
        val backBtn = FrameLayout(this).apply {
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Back"
            addView(TextView(this@VoiceBiometricsActivity).apply {
                text = "←"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(SunsetColors.TEXT_PRIMARY)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            setOnClickListener { finish() }
        }
        val titleTv = TextView(this).apply {
            text = "VOICE BIOMETRICS"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(SunsetColors.TEXT_PRIMARY)
        }
        masterSwitch = Switch(this).apply {
            minimumWidth = dp(52)
            minimumHeight = dp(48)
            contentDescription = "Enable or pause Owner Voice Biometrics"
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !VoiceIntelligence.isVoiceEnrolled(this@VoiceBiometricsActivity)) {
                    isChecked.let {
                        this.isChecked = false
                        Toast.makeText(this@VoiceBiometricsActivity, "Record voice samples 1–3 first", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    VoiceIntelligence.setBiometricEnabled(this@VoiceBiometricsActivity, isChecked)
                    refreshDashboardUi()
                }
            }
        }
        header.addView(backBtn, lp(dp(48), dp(48)))
        header.addView(titleTv, lp(0, -2, 1f))
        header.addView(masterSwitch, lp(-2, -2))
        root.addView(header, lp(-1, -2))

        val sv = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }

        // Card 1: OWNER VOICE STATUS
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = SunsetUiHelper.glassCard(this@VoiceBiometricsActivity, 20)
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this)
        statusTitleTv = TextView(this).apply {
            text = "OWNER VOICE: PAUSED"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(SunsetColors.TEXT_PRIMARY)
            setPadding(dp(10), 0, 0, 0)
        }
        statusRow.addView(statusDot, lp(dp(11), dp(11)))
        statusRow.addView(statusTitleTv, lp(0, -2, 1f))
        statusCard.addView(statusRow)
        statusCard.addView(TextView(this).apply {
            text = "NOVA is calibrated to your voice. Any speech or commands from other people are automatically blocked."
            textSize = 13.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(8), 0, 0)
        })
        body.addView(statusCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        // Card 2: TRAIN STEP X OF 3
        val trainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = SunsetUiHelper.glassCard(this@VoiceBiometricsActivity, 20)
        }
        trainStepTv = TextView(this).apply {
            text = "TRAIN STEP 1 OF 3"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setTextColor(SunsetColors.CORAL)
        }
        trainCard.addView(trainStepTv)
        trainCard.addView(TextView(this).apply {
            text = "Say clearly into your phone:"
            textSize = 14f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(10), 0, dp(6))
        })
        trainCard.addView(TextView(this).apply {
            text = "\"Hey Nova, wake up\""
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(18))
        })
        tapSpeakBtn = Button(this).apply {
            text = "TAP & SPEAK PHRASE"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = true
            minimumHeight = dp(50)
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(SunsetColors.CORAL)
            }
            contentDescription = "Record training phrase Hey Nova wake up"
            setOnClickListener { startTrainingStep() }
        }
        trainCard.addView(tapSpeakBtn, lp(-2, dp(50)))

        trainHintTv = TextView(this).apply {
            text = "Tap to start recording sample 1"
            textSize = 13f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(14), 0, dp(14))
        }
        trainCard.addView(trainHintTv)

        val stepsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        sampleDots.clear()
        val sampleLabels = listOf("① Sample 1", "② Sample 2", "③ Sample 3")
        sampleLabels.forEachIndexed { idx, lbl ->
            val tv = TextView(this).apply {
                text = lbl
                textSize = 12.5f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { startTrainingStep(forceIndex = idx) }
            }
            sampleDots.add(tv)
            stepsRow.addView(tv, lp(-2, -2).apply { rightMargin = dp(4) })
        }
        trainCard.addView(stepsRow)
        body.addView(trainCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        // Card 3: LIVE RECOGNITION TEST
        val testCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = SunsetUiHelper.glassCard(this@VoiceBiometricsActivity, 20)
        }
        testCard.addView(TextView(this).apply {
            text = "LIVE RECOGNITION TEST"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(SunsetColors.CORAL)
        })
        testCard.addView(TextView(this).apply {
            text = "Test your voice or let a friend speak to verify that NOVA accepts you and denies them."
            textSize = 13.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(8), 0, dp(14))
        })
        testBtn = Button(this).apply {
            text = "TEST VOICE MATCH (SPEAK NOW)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.CORAL)
            minimumHeight = dp(50)
            background = SunsetUiHelper.outlinePill(this@VoiceBiometricsActivity, 26, SunsetColors.CORAL)
            contentDescription = "Test live voice biometric match"
            setOnClickListener { runLiveMatchTest() }
        }
        testCard.addView(testBtn, lp(-1, dp(50)))
        testResultTv = TextView(this).apply {
            text = ""
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        testCard.addView(testResultTv)
        body.addView(testCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        // Card 4: SENSITIVITY / STRICTNESS
        val sensCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = SunsetUiHelper.glassCard(this@VoiceBiometricsActivity, 20)
        }
        sensCard.addView(TextView(this).apply {
            text = "SENSITIVITY / STRICTNESS"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(SunsetColors.CORAL)
            setPadding(0, 0, 0, dp(10))
        })
        val options = listOf(
            "strict" to "Strict (High Security - Zero False Accepts)",
            "balanced" to "Balanced (Recommended - Optimal Everyday)",
            "relaxed" to "Relaxed (Easy Match - Even if noisy around)"
        )
        for ((key, label) in options) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(0, dp(8), 0, dp(8))
                contentDescription = label
                setOnClickListener {
                    VoiceIntelligence.setStrictness(this@VoiceBiometricsActivity, key)
                    refreshDashboardUi()
                }
            }
            val radioDot = View(this)
            val tv = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(SunsetColors.TEXT_PRIMARY)
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(radioDot, lp(dp(22), dp(22)))
            row.addView(tv, lp(0, -2, 1f))
            strictnessRows[key] = radioDot
            sensCard.addView(row)
        }
        body.addView(sensCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        // Utility row: Change Pattern & Reset Samples
        val utilRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val chgPattern = Button(this).apply {
            text = "Change Pattern"
            textSize = 13f
            isAllCaps = false
            setTextColor(SunsetColors.TEXT_PRIMARY)
            minimumHeight = dp(48)
            background = SunsetUiHelper.outlinePill(this@VoiceBiometricsActivity, 22, SunsetColors.GLASS_STROKE)
            setOnClickListener { enterCreatePatternMode() }
        }
        val resetBtn = Button(this).apply {
            text = "Reset Samples"
            textSize = 13f
            isAllCaps = false
            setTextColor(SunsetColors.PINK)
            minimumHeight = dp(48)
            background = SunsetUiHelper.outlinePill(this@VoiceBiometricsActivity, 22, SunsetColors.GLASS_STROKE)
            setOnClickListener {
                VoiceIntelligence.resetBiometrics(this@VoiceBiometricsActivity)
                testResultTv.visibility = View.GONE
                refreshDashboardUi()
                Toast.makeText(this@VoiceBiometricsActivity, "Voice samples cleared", Toast.LENGTH_SHORT).show()
            }
        }
        utilRow.addView(chgPattern, lp(0, dp(48), 1f).apply { rightMargin = dp(8) })
        utilRow.addView(resetBtn, lp(0, dp(48), 1f).apply { leftMargin = dp(8) })
        body.addView(utilRow, lp(-1, -2))

        sv.addView(body)
        root.addView(sv, lp(-1, 0, 1f))
        return root
    }

    private fun refreshDashboardUi() {
        val enabled = VoiceIntelligence.isBiometricEnabled(this) && VoiceIntelligence.isVoiceEnrolled(this)
        masterSwitch.isChecked = enabled
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) SunsetColors.GREEN_FG else SunsetColors.BLUE_FG)
        }
        statusTitleTv.text = if (enabled) "OWNER VOICE: ACTIVE" else "OWNER VOICE: PAUSED"

        val count = VoiceIntelligence.getSampleCount(this)
        val step = minOf(count + 1, 3)
        trainStepTv.text = if (count >= 3) "TRAINING COMPLETE (3 OF 3)" else "TRAIN STEP $step OF 3"
        if (!isRecording) {
            trainHintTv.text = when {
                count >= 3 -> "All 3 samples captured • Tap a sample below to re-record"
                else -> "Tap to start recording sample ${count + 1}"
            }
        }
        sampleDots.forEachIndexed { idx, tv ->
            val recorded = idx < count
            val current = idx == count && count < 3
            tv.setTextColor(when {
                recorded -> SunsetColors.GREEN_FG
                current -> SunsetColors.CORAL
                else -> SunsetColors.TEXT_MUTED
            })
            tv.typeface = if (recorded || current) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val curStrict = VoiceIntelligence.getStrictness(this)
        for ((k, dot) in strictnessRows) {
            val sel = (k == curStrict)
            if (sel) {
                val layer = android.graphics.drawable.LayerDrawable(arrayOf(
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                        setStroke(dp(2), SunsetColors.CORAL)
                    },
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(SunsetColors.CORAL)
                    }
                ))
                layer.setLayerInset(1, dp(5), dp(5), dp(5), dp(5))
                dot.background = layer
            } else {
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), SunsetColors.SLATE_DOT)
                }
            }
        }
    }

    private fun startTrainingStep(forceIndex: Int? = null) {
        if (isRecording) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 881)
            return
        }
        val idx = forceIndex ?: VoiceIntelligence.getSampleCount(this).coerceAtMost(2)
        isRecording = true
        tapSpeakBtn.isEnabled = false
        tapSpeakBtn.text = "LISTENING (2.6s)..."
        trainHintTv.text = "Speak clearly: \"Hey Nova, wake up\""
        Thread {
            val (ok, msg) = VoiceIntelligence.recordTrainingSample(applicationContext, idx)
            runOnUiThread {
                isRecording = false
                tapSpeakBtn.isEnabled = true
                tapSpeakBtn.text = "TAP & SPEAK PHRASE"
                trainHintTv.text = msg
                refreshDashboardUi()
                if (!ok) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun runLiveMatchTest() {
        if (isRecording) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 882)
            return
        }
        isRecording = true
        testBtn.isEnabled = false
        testBtn.text = "LISTENING (SPEAK NOW)..."
        testResultTv.visibility = View.VISIBLE
        testResultTv.setTextColor(SunsetColors.TEXT_SECONDARY)
        testResultTv.text = "Analyzing speaker voiceprint..."
        Thread {
            val (ok, msg) = VoiceIntelligence.verifyVoicePrint(applicationContext)
            runOnUiThread {
                isRecording = false
                testBtn.isEnabled = true
                testBtn.text = "TEST VOICE MATCH (SPEAK NOW)"
                testResultTv.text = msg
                testResultTv.setTextColor(if (ok) SunsetColors.GREEN_FG else SunsetColors.PINK)
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 881) startTrainingStep()
            else if (requestCode == 882) runLiveMatchTest()
        } else {
            Toast.makeText(this, "Microphone permission needed for Voice Biometrics", Toast.LENGTH_SHORT).show()
        }
    }

    /** Custom 3x3 tactile Pattern Lock grid matching the dark slate dots + coral connecting path. */
    private class PatternLockView(ctx: Context) : View(ctx) {
        var onPatternComplete: ((String) -> Unit)? = null
        private val d = ctx.resources.displayMetrics.density
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 4.5f * d
            color = SunsetColors.CORAL
        }
        private val selected = ArrayList<Int>()
        private val centers = Array(9) { PointF() }
        private var fingerX = -1f
        private var fingerY = -1f
        private var errorState = false

        init {
            contentDescription = "3 by 3 pattern lock grid"
        }

        fun showError() {
            errorState = true
            invalidate()
            postDelayed({
                errorState = false
                selected.clear()
                invalidate()
            }, 650L)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val stepX = w / 3f
            val stepY = h / 3f
            for (i in 0 until 9) {
                val col = i % 3
                val row = i / 3
                centers[i].set(stepX * (col + 0.5f), stepY * (row + 0.5f))
            }
        }

        override fun onDraw(c: Canvas) {
            if (selected.isNotEmpty()) {
                linePaint.color = if (errorState) SunsetColors.PINK else SunsetColors.CORAL
                val p = Path()
                p.moveTo(centers[selected[0]].x, centers[selected[0]].y)
                for (i in 1 until selected.size) {
                    p.lineTo(centers[selected[i]].x, centers[selected[i]].y)
                }
                if (fingerX >= 0f && fingerY >= 0f && !errorState) {
                    p.lineTo(fingerX, fingerY)
                }
                c.drawPath(p, linePaint)
            }

            val baseR = 8.5f * d
            for (i in 0 until 9) {
                val pt = centers[i]
                val isSel = i in selected
                if (isSel) {
                    dotPaint.style = Paint.Style.STROKE
                    dotPaint.strokeWidth = 2.5f * d
                    dotPaint.color = if (errorState) SunsetColors.PINK else SunsetColors.CORAL
                    c.drawCircle(pt.x, pt.y, baseR * 2.1f, dotPaint)
                }
                dotPaint.style = Paint.Style.FILL
                dotPaint.color = when {
                    isSel && errorState -> SunsetColors.PINK
                    isSel -> SunsetColors.CORAL
                    else -> SunsetColors.SLATE_DOT
                }
                c.drawCircle(pt.x, pt.y, baseR, dotPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    errorState = false
                    selected.clear()
                    fingerX = event.x
                    fingerY = event.y
                    hitDot(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    fingerX = event.x
                    fingerY = event.y
                    hitDot(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fingerX = -1f
                    fingerY = -1f
                    val seq = selected.joinToString("")
                    invalidate()
                    if (seq.isNotEmpty()) onPatternComplete?.invoke(seq)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun hitDot(x: Float, y: Float) {
            val hitR = 32f * d
            for (i in 0 until 9) {
                if (i !in selected && hypot(x - centers[i].x, y - centers[i].y) <= hitR) {
                    selected.add(i)
                }
            }
        }
    }
}
