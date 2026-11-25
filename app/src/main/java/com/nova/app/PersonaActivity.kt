package com.nova.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

/**
 * Sunset Peach AI Persona Selector Screen:
 * - Operator Profile summary card
 * - 4 Personas: Nova Pro (DEFAULT AI), Kritika (Girlfriend Mode), Nova Max (NEURAL CORE), Rocky (DARK CORE)
 * - Operator User Name field (default: ISHAQ)
 * - 5 Voice Tone radio options (Aoede, Kore, Fenrir, Puck, Charon)
 * - Gradient Activate Persona button
 */
class PersonaActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var profileSummaryTitle: TextView
    private lateinit var profileSummarySub: TextView
    private lateinit var nameEt: EditText

    private var selectedPersonaId = "nova_pro"
    private var selectedVoice = "Aoede"

    private val personaCards = LinkedHashMap<String, Pair<LinearLayout, View>>()
    private val voiceRows = LinkedHashMap<String, Pair<LinearLayout, View>>()

    private fun dp(x: Int) = SunsetUiHelper.dp(this, x)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = SunsetColors.PEACH_TOP
        window.navigationBarColor = SunsetColors.PEACH_BOTTOM
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        I18n.applyDirection(this)

        prefs = getSharedPreferences("nova", MODE_PRIVATE)
        val curPersona = Personas.current(this)
        selectedPersonaId = curPersona.id
        selectedVoice = Personas.effectiveVoice(this)
        val curUserName = Personas.userName(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.pageBg()
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }
        val backBtn = FrameLayout(this).apply {
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = "Back"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SunsetColors.GLASS_SOFT)
                setStroke(dp(1), SunsetColors.GLASS_STROKE)
            }
            addView(TextView(this@PersonaActivity).apply {
                text = "←"
                textSize = 20f
                setTextColor(SunsetColors.TEXT_PRIMARY)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            setOnClickListener { finish() }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titleCol.addView(TextView(this).apply {
            text = "AI Persona"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        titleCol.addView(TextView(this).apply {
            text = "Choose your companion personality"
            textSize = 13f
            setTextColor(SunsetColors.TEXT_SECONDARY)
        })
        topBar.addView(backBtn, lp(dp(48), dp(48)))
        topBar.addView(titleCol, lp(0, -2, 1f))
        root.addView(topBar, lp(-1, -2))

        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(20))
        }

        // YOUR OPERATOR PROFILE Card
        val opCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = SunsetUiHelper.glassCard(this@PersonaActivity, 20)
        }
        opCard.addView(TextView(this).apply {
            text = "YOUR OPERATOR PROFILE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(SunsetColors.CORAL)
        })
        profileSummaryTitle = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
            setPadding(0, dp(6), 0, dp(2))
        }
        profileSummarySub = TextView(this).apply {
            textSize = 13.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
        }
        opCard.addView(profileSummaryTitle)
        opCard.addView(profileSummarySub)
        content.addView(opCard, lp(-1, -2).apply { bottomMargin = dp(14) })

        // 4 Persona Cards ONLY
        for (persona in Personas.all) {
            val card = buildPersonaCard(persona)
            content.addView(card, lp(-1, -2).apply { bottomMargin = dp(12) })
        }

        // User Name Section
        content.addView(TextView(this).apply {
            text = "User Name (Default: Nova Max)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.PINK)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })
        val nameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = SunsetUiHelper.glassCard(this@PersonaActivity, 20)
        }
        nameCard.addView(TextView(this).apply {
            text = "How should this persona address you?"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(10))
        })
        nameCard.addView(TextView(this).apply {
            text = "Operator Name"
            textSize = 11.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(dp(4), 0, 0, dp(4))
        })
        nameEt = EditText(this).apply {
            setText(curUserName)
            hint = "ISHAQ"
            setTextColor(SunsetColors.TEXT_PRIMARY)
            setHintTextColor(SunsetColors.TEXT_MUTED)
            textSize = 15.5f
            setSingleLine()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#B6ACA7"))
            }
            contentDescription = "Operator name field"
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { updateProfileSummary() }
            })
        }
        nameCard.addView(nameEt, lp(-1, dp(50)))
        content.addView(nameCard, lp(-1, -2).apply { bottomMargin = dp(16) })

        // Voice Tone Section
        content.addView(TextView(this).apply {
            text = "Voice Tone"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.PINK)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })
        val voiceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = SunsetUiHelper.glassCard(this@PersonaActivity, 20)
        }
        Personas.VOICES.forEachIndexed { idx, (vName, vLabel) ->
            val vRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                contentDescription = vLabel
                setOnClickListener {
                    selectedVoice = vName
                    refreshSelection()
                }
            }
            val radioDot = View(this)
            val tv = TextView(this).apply {
                text = vLabel
                textSize = 14.5f
                setTextColor(SunsetColors.TEXT_PRIMARY)
                setPadding(dp(12), 0, 0, 0)
            }
            vRow.addView(radioDot, lp(dp(22), dp(22)))
            vRow.addView(tv, lp(0, -2, 1f))
            voiceRows[vName] = vRow to radioDot
            voiceCard.addView(vRow)
            if (idx < Personas.VOICES.lastIndex) {
                voiceCard.addView(View(this).apply {
                    setBackgroundColor(SunsetColors.DIVIDER)
                }, lp(-1, dp(1)))
            }
        }
        content.addView(voiceCard, lp(-1, -2).apply { bottomMargin = dp(16) })

        sv.addView(content)
        root.addView(sv, lp(-1, 0, 1f))

        // Bottom Floating Coral-Pink Pill Button: Activate Persona
        val bottomWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val activateBtn = Button(this).apply {
            text = "Activate Persona"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false
            minimumHeight = dp(54)
            background = SunsetUiHelper.gradientPill(this@PersonaActivity, 28)
            contentDescription = "Activate selected AI persona and voice tone"
            setOnClickListener { activateAndSave() }
        }
        bottomWrap.addView(activateBtn, lp(-1, dp(54)))
        root.addView(bottomWrap, lp(-1, -2))

        setContentView(root)
        refreshSelection()
    }

    private fun buildPersonaCard(persona: Persona): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = "${persona.title} ${persona.badge}"
            setOnClickListener {
                selectedPersonaId = persona.id
                selectedVoice = persona.defaultVoice
                refreshSelection()
            }
        }

        // Left square icon box
        val accentColor = Color.parseColor(persona.colorHex)
        val iconFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(SunsetColors.CORAL_BG)
                setStroke(dp(1), Color.parseColor("#FF997A"))
            }
            addView(IconView(this@PersonaActivity).apply {
                icon = persona.icon
                tint = accentColor
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        card.addView(iconFrame, lp(dp(44), dp(44)))

        // Middle details column
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(10), 0)
        }
        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headRow.addView(TextView(this).apply {
            text = persona.title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        val tagView = TextView(this).apply {
            text = persona.badge
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setTextColor(SunsetColors.BLUE_FG)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#D5DCE6"))
            }
        }
        headRow.addView(tagView, lp(-2, -2).apply { leftMargin = dp(8) })
        mid.addView(headRow)
        mid.addView(TextView(this).apply {
            text = persona.description
            textSize = 12.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })
        card.addView(mid, lp(0, -2, 1f))

        // Right radio indicator
        val radioDot = View(this)
        card.addView(radioDot, lp(dp(22), dp(22)))

        personaCards[persona.id] = card to radioDot
        return card
    }

    private fun styleRadioDot(dot: View, selected: Boolean) {
        if (selected) {
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
                setStroke(dp(2), SunsetColors.RADIO_OFF)
            }
        }
    }

    private fun refreshSelection() {
        for ((id, pair) in personaCards) {
            val (card, dot) = pair
            val sel = (id == selectedPersonaId)
            card.background = SunsetUiHelper.glassCard(
                this, 20,
                bg = SunsetColors.GLASS_BG,
                stroke = if (sel) SunsetColors.CORAL else SunsetColors.GLASS_STROKE
            )
            styleRadioDot(dot, sel)
        }
        for ((vName, pair) in voiceRows) {
            styleRadioDot(pair.second, vName.equals(selectedVoice, ignoreCase = true))
        }
        updateProfileSummary()
    }

    private fun updateProfileSummary() {
        val p = Personas.byId(selectedPersonaId)
        val uName = nameEt.text.toString().trim().ifEmpty { "ISHAQ" }
        profileSummaryTitle.text = "${p.title}  •  $selectedVoice"
        profileSummarySub.text = "Ready to help, $uName."
    }

    private fun activateAndSave() {
        val p = Personas.byId(selectedPersonaId)
        val uName = nameEt.text.toString().trim().ifEmpty { "ISHAQ" }
        prefs.edit()
            .putString("p", p.id)
            .putString("u", uName)
            .putString("user_name", uName)
            .putString("voice_tone", selectedVoice)
            .apply()
        Backup.saveProfile(this)
        Toast.makeText(this, "Activated ${p.title} ($selectedVoice) for $uName", Toast.LENGTH_SHORT).show()
        if (LiveService.isRunning()) {
            try {
                LiveService.liveClient?.stop()
                stopService(android.content.Intent(this, LiveService::class.java))
            } catch (_: Exception) {}
        }
        finish()
    }
}
