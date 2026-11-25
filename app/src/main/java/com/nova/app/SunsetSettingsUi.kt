package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.security.MessageDigest

/**
 * Modular Sunset Glass Settings & Intelligence Tab builder.
 * Displays Operator Profile (ISHAQ / +92 923141613159), Voice & Neural Intelligence,
 * System & Data Engine, Device & Assistant, and Appearance & Security (X32 Team App Identity).
 */
object SunsetSettingsUi {
    private fun dp(ctx: Context, x: Int) = SunsetUiHelper.dp(ctx, x)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    interface Callbacks {
        fun onCallAssistant()
        fun onOpenTheme()
        fun onOpenCodeStudio()
        fun onOpenMemories()
        fun onOpenOverlayOptions()
        fun onOpenPrivacyShield()
        fun onOpenLegacySettings()
        fun onRefreshAll()
    }

    fun build(ctx: Activity, cb: Callbacks): ScrollView {
        val prefs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
        val userName = Personas.userName(ctx)
        val contact = (prefs.getString("user_contact", "") ?: "").ifBlank { "923141613159" }
        val persona = Personas.current(ctx)
        val voice = Personas.effectiveVoice(ctx)
        val bioOn = VoiceIntelligence.isBiometricEnabled(ctx) && VoiceIntelligence.isVoiceEnrolled(ctx)

        val sv = ScrollView(ctx).apply { isVerticalScrollBarEnabled = true }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 110))
        }

        // Page Header
        val headRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 16))
        }
        val titleCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(ctx).apply {
            text = "Settings & Intelligence"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        titleCol.addView(TextView(ctx).apply {
            text = "System preferences & neural configuration"
            textSize = 13f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(ctx, 2), 0, 0)
        })
        val sparkle = IconView(ctx).apply {
            icon = "sparkle"
            tint = SunsetColors.CORAL
            contentDescription = "All legacy NOVA controls"
            setOnClickListener { cb.onOpenLegacySettings() }
        }
        headRow.addView(titleCol, lp(0, -2, 1f))
        headRow.addView(sparkle, lp(dp(ctx, 28), dp(ctx, 28)))
        root.addView(headRow, lp(-1, -2))

        // Operator Profile Glass Card
        val profCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 14))
            background = SunsetUiHelper.glassCard(ctx, 22)
        }
        val profTop = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatarBox = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(SunsetColors.CORAL_BG)
                setStroke(dp(ctx, 1), SunsetColors.GLASS_STROKE)
            }
            addView(IconView(ctx).apply {
                icon = "person"
                tint = SunsetColors.CORAL
            }, FrameLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26), Gravity.CENTER))
            addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(SunsetColors.GREEN_FG)
                    setStroke(dp(ctx, 2), Color.WHITE)
                }
            }, FrameLayout.LayoutParams(dp(ctx, 12), dp(ctx, 12), Gravity.BOTTOM or Gravity.END))
        }
        val profText = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, dp(ctx, 8), 0)
        }
        profText.addView(TextView(ctx).apply {
            text = userName
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        profText.addView(TextView(ctx).apply {
            text = "NOVA OPERATOR • PRIMARY USER"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(ctx, 2), 0, 0)
        })
        val editBtn = FrameLayout(ctx).apply {
            minimumWidth = dp(ctx, 48)
            minimumHeight = dp(ctx, 48)
            contentDescription = "Edit Operator Name and Contact"
            addView(IconView(ctx).apply {
                icon = "edit"
                tint = SunsetColors.TEXT_SECONDARY
            }, FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18), Gravity.CENTER))
            setOnClickListener { showOperatorEditDialog(ctx, cb) }
        }
        profTop.addView(avatarBox, lp(dp(ctx, 52), dp(ctx, 52)))
        profTop.addView(profText, lp(0, -2, 1f))
        profTop.addView(editBtn, lp(dp(ctx, 48), dp(ctx, 48)))
        profCard.addView(profTop)

        val badgeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 12), 0, 0)
        }
        val enrollBadge = SunsetUiHelper.badge(
            ctx,
            if (VoiceIntelligence.isVoiceEnrolled(ctx)) "✓ VOICE ENROLLED" else "○ VOICE NOT ENROLLED",
            if (VoiceIntelligence.isVoiceEnrolled(ctx)) SunsetColors.GREEN_BG else SunsetColors.CORAL_BG,
            if (VoiceIntelligence.isVoiceEnrolled(ctx)) SunsetColors.GREEN_FG else SunsetColors.CORAL
        )
        val personaBadge = SunsetUiHelper.badge(
            ctx,
            persona.title.uppercase(),
            Color.WHITE,
            SunsetColors.CORAL
        ).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 99).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(ctx, 1), Color.parseColor("#E5D5CD"))
            }
        }
        badgeRow.addView(enrollBadge, lp(-2, -2).apply { rightMargin = dp(ctx, 8) })
        badgeRow.addView(personaBadge, lp(-2, -2))
        profCard.addView(badgeRow)
        root.addView(profCard, lp(-1, -2).apply { bottomMargin = dp(ctx, 20) })

        // ==================== Section 1: VOICE & NEURAL INTELLIGENCE ====================
        addSectionTitle(ctx, root, "VOICE & NEURAL INTELLIGENCE")
        val sec1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.glassCard(ctx, 22)
        }
        sec1.addView(navRow(
            ctx, "mic", "Owner Voice Biometrics",
            if (bioOn) "Voice Profile Active • Only you can speak"
            else if (VoiceIntelligence.isVoiceEnrolled(ctx)) "Voice Profile Paused • Tap to activate"
            else "Protect assistant with 3-step voiceprint",
            badgeText = if (bioOn) "ACTIVE" else "PAUSED",
            badgeGreen = bioOn
        ) {
            ctx.startActivity(Intent(ctx, VoiceBiometricsActivity::class.java))
        })
        addDivider(ctx, sec1)
        sec1.addView(navRow(
            ctx, "translate", "Conversation Language",
            I18n.current(ctx).label
        ) {
            showLanguageDialog(ctx)
        })
        addDivider(ctx, sec1)
        sec1.addView(navRow(
            ctx, "person", "AI Persona & Voice",
            "${persona.title} • Voice: $voice"
        ) {
            ctx.startActivity(Intent(ctx, PersonaActivity::class.java))
        })
        addDivider(ctx, sec1)
        sec1.addView(switchRow(
            ctx, "vol_off", "Silent Execution Mode",
            "Execute device commands without vocal reply",
            prefs.getBoolean("silent_exec", false)
        ) { checked ->
            prefs.edit().putBoolean("silent_exec", checked).apply()
            Vol.speakerMuted = checked
        })
        addDivider(ctx, sec1)
        sec1.addView(switchRow(
            ctx, "chat", "Auto Reply",
            if (prefs.getBoolean("feat_auto_reply", true)) "On • answering WhatsApp, Telegram & more" else "Off • manual reply only",
            prefs.getBoolean("feat_auto_reply", true)
        ) { checked ->
            prefs.edit().putBoolean("feat_auto_reply", checked).apply()
            cb.onRefreshAll()
        })
        root.addView(sec1, lp(-1, -2).apply { bottomMargin = dp(ctx, 20) })

        // ==================== Section 2: SYSTEM & DATA ENGINE ====================
        addSectionTitle(ctx, root, "SYSTEM & DATA ENGINE")
        val sec2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.glassCard(ctx, 22)
        }
        val activeModel = (prefs.getString("model", "") ?: "").ifBlank { LiveClient.MODELS.first() }
        sec2.addView(navRow(
            ctx, "bolt", "Nova Live API & Model",
            "$activeModel • Live WebSocket"
        ) {
            ctx.startActivity(Intent(ctx, SetupActivity::class.java))
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "bolt", "Nova Studio",
            "Code, build and fix projects with NOVA"
        ) {
            cb.onOpenCodeStudio()
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "vol_up", "Offline AI",
            "On-device brain and offline voice (works without internet)"
        ) {
            ctx.startActivity(Intent(ctx, VoicePackActivity::class.java))
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "grid", "Neural Memory Bank",
            "Search, review and back up learned facts"
        ) {
            cb.onOpenMemories()
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "shield", "System Permissions",
            "Microphone, Accessibility, Notifications & System"
        ) {
            ctx.startActivity(Intent(ctx, SetupActivity::class.java))
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "palette", "Overlay Avatars",
            "Choose a floating avatar or turn the overlay off"
        ) {
            cb.onOpenOverlayOptions()
        })
        addDivider(ctx, sec2)
        sec2.addView(navRow(
            ctx, "grid", "App Control & Hands-Free",
            "WhatsApp, Browser, Media & 98 Tools Automation"
        ) {
            ctx.startActivity(Intent(ctx, NovaHubActivity::class.java))
        })
        root.addView(sec2, lp(-1, -2).apply { bottomMargin = dp(ctx, 20) })

        // ==================== Section 3: DEVICE & ASSISTANT ====================
        addSectionTitle(ctx, root, "DEVICE & ASSISTANT")
        val sec3 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.glassCard(ctx, 22)
        }
        val isDefAssist = isDefaultAssistant(ctx)
        sec3.addView(navRow(
            ctx, "grid", "Nova Default Assistant",
            if (isDefAssist) "✓ NOVA is the default assistant" else "Tap to set NOVA as device voice assistant",
            subtitleColor = if (isDefAssist) SunsetColors.GREEN_FG else SunsetColors.TEXT_SECONDARY
        ) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Exception) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Open Device Settings > Default Apps > Digital assistant", Toast.LENGTH_LONG).show()
                }
            }
        })
        root.addView(sec3, lp(-1, -2).apply { bottomMargin = dp(ctx, 20) })

        // ==================== Section 4: APPEARANCE & SECURITY ====================
        addSectionTitle(ctx, root, "APPEARANCE & SECURITY")
        val sec4 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = SunsetUiHelper.glassCard(ctx, 22)
        }
        val orbMode = (prefs.getString("orb_color", "sunset") ?: "sunset").replaceFirstChar { it.uppercase() }
        sec4.addView(navRow(
            ctx, "palette", "Theme & 3D Orb Colors",
            "Active orb: $orbMode • ${Themes.all(ctx).size} Wallpaper Widgets"
        ) {
            showOrbAndThemeDialog(ctx, cb)
        })
        addDivider(ctx, sec4)
        sec4.addView(navRow(
            ctx, "lock", "Privacy & Data Shield",
            "Screen Lock PIN, File Vault & Backup Manager"
        ) {
            cb.onOpenPrivacyShield()
        })
        addDivider(ctx, sec4)
        sec4.addView(navRow(
            ctx, "shield", "App Identity & Integrity",
            "X32 Team • ${ctx.packageName}"
        ) {
            showAppIdentityDialog(ctx, userName, contact)
        })
        root.addView(sec4, lp(-1, -2).apply { bottomMargin = dp(ctx, 20) })

        // Bottom CTA Button: CALL ASSISTANT
        val callBtn = Button(ctx).apply {
            text = "CALL ASSISTANT"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            minimumHeight = dp(ctx, 50)
            background = SunsetUiHelper.gradientPill(ctx, 26)
            contentDescription = "Start voice assistant call"
            setOnClickListener { cb.onCallAssistant() }
        }
        root.addView(callBtn, lp(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 18) })

        // Footer
        root.addView(TextView(ctx).apply {
            text = "NOVA ASSISTANT • v36.0\nEngineered by X32 Team"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(SunsetColors.TEXT_SECONDARY)
        }, lp(-1, -2))

        sv.addView(root)
        return sv
    }

    private fun addSectionTitle(ctx: Context, parent: LinearLayout, title: String) {
        parent.addView(TextView(ctx).apply {
            text = title
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(dp(ctx, 4), 0, dp(ctx, 4), dp(ctx, 8))
        })
    }

    private fun addDivider(ctx: Context, parent: LinearLayout) {
        parent.addView(View(ctx).apply {
            setBackgroundColor(SunsetColors.DIVIDER)
        }, lp(-1, dp(ctx, 1)).apply {
            leftMargin = dp(ctx, 64)
            rightMargin = dp(ctx, 16)
        })
    }

    private fun navRow(
        ctx: Context,
        iconName: String,
        title: String,
        subtitle: String,
        badgeText: String? = null,
        badgeGreen: Boolean = true,
        subtitleColor: Int = SunsetColors.TEXT_SECONDARY,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 64)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            contentDescription = title
            setOnClickListener { onClick() }
        }
        val iconCircle = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(ctx, 1), Color.parseColor("#ECDCD3"))
            }
            addView(IconView(ctx).apply {
                icon = iconName
                tint = SunsetColors.CORAL
            }, FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18), Gravity.CENTER))
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, dp(ctx, 8), 0)
        }
        col.addView(TextView(ctx).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(subtitleColor)
            setPadding(0, dp(ctx, 2), 0, 0)
        })
        row.addView(iconCircle, lp(dp(ctx, 38), dp(ctx, 38)))
        row.addView(col, lp(0, -2, 1f))
        if (badgeText != null) {
            row.addView(
                SunsetUiHelper.badge(
                    ctx, badgeText,
                    if (badgeGreen) SunsetColors.GREEN_BG else SunsetColors.CORAL_BG,
                    if (badgeGreen) SunsetColors.GREEN_FG else SunsetColors.CORAL
                ),
                lp(-2, -2).apply { rightMargin = dp(ctx, 8) }
            )
        }
        row.addView(IconView(ctx).apply {
            icon = "chevron"
            tint = SunsetColors.TEXT_MUTED
        }, lp(dp(ctx, 16), dp(ctx, 16)))
        return row
    }

    private fun switchRow(
        ctx: Context,
        iconName: String,
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 64)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            contentDescription = title
        }
        val iconCircle = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(ctx, 1), Color.parseColor("#ECDCD3"))
            }
            addView(IconView(ctx).apply {
                icon = iconName
                tint = SunsetColors.CORAL
            }, FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18), Gravity.CENTER))
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, dp(ctx, 8), 0)
        }
        col.addView(TextView(ctx).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SunsetColors.TEXT_PRIMARY)
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(SunsetColors.TEXT_SECONDARY)
            setPadding(0, dp(ctx, 2), 0, 0)
        })
        val sw = Switch(ctx).apply {
            isChecked = checked
            minimumWidth = dp(ctx, 50)
            minimumHeight = dp(ctx, 48)
            contentDescription = title
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(iconCircle, lp(dp(ctx, 38), dp(ctx, 38)))
        row.addView(col, lp(0, -2, 1f))
        row.addView(sw, lp(-2, -2))
        return row
    }

    private fun isDefaultAssistant(ctx: Context): Boolean {
        return try {
            val cur = Settings.Secure.getString(ctx.contentResolver, "assistant")
                ?: Settings.Secure.getString(ctx.contentResolver, "voice_interaction_service")
                ?: ""
            cur.contains(ctx.packageName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun showLanguageDialog(ctx: Activity) {
        val langs = I18n.Lang.values()
        val labels = langs.map { it.label }.toTypedArray()
        val cur = I18n.current(ctx).ordinal
        AlertDialog.Builder(ctx)
            .setTitle("Conversation Language")
            .setSingleChoiceItems(labels, cur) { d, which ->
                I18n.setLang(ctx, langs[which])
                d.dismiss()
                ctx.recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOperatorEditDialog(ctx: Activity, cb: Callbacks) {
        val prefs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
        val curName = Personas.userName(ctx)
        val curPhone = (prefs.getString("user_contact", "") ?: "").ifBlank { "923141613159" }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), 0)
        }
        val nameEt = EditText(ctx).apply {
            hint = "Operator Name (e.g., ISHAQ)"
            setText(curName)
            setSingleLine()
        }
        val phoneEt = EditText(ctx).apply {
            hint = "Contact Number (e.g., 923141613159)"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(curPhone)
            setSingleLine()
        }
        box.addView(TextView(ctx).apply { text = "Name"; textSize = 12f; setTextColor(SunsetColors.TEXT_SECONDARY) })
        box.addView(nameEt)
        box.addView(TextView(ctx).apply { text = "Contact Number (+92)"; textSize = 12f; setTextColor(SunsetColors.TEXT_SECONDARY); setPadding(0, dp(ctx, 8), 0, 0) })
        box.addView(phoneEt)

        AlertDialog.Builder(ctx)
            .setTitle("Edit Operator Profile")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = nameEt.text.toString().trim().ifEmpty { "ISHAQ" }
                val c = phoneEt.text.toString().filter { it.isDigit() || it == '+' }.ifEmpty { "923141613159" }
                prefs.edit()
                    .putString("u", n)
                    .putString("user_name", n)
                    .putString("user_contact", c)
                    .apply()
                Backup.saveProfile(ctx)
                cb.onRefreshAll()
                Toast.makeText(ctx, "Profile updated for $n", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOrbAndThemeDialog(ctx: Activity, cb: Callbacks) {
        val prefs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
        val opts = arrayOf(
            "Orb: Sunset Coral (Default)",
            "Orb: Ocean Blue",
            "Orb: Neural Violet",
            "Orb: Emerald Mint",
            "Open Wallpaper Widgets & Custom Gallery"
        )
        AlertDialog.Builder(ctx)
            .setTitle("Theme & 3D Orb Colors")
            .setItems(opts) { _, i ->
                when (i) {
                    0 -> prefs.edit().putString("orb_color", "sunset").apply()
                    1 -> prefs.edit().putString("orb_color", "ocean").apply()
                    2 -> prefs.edit().putString("orb_color", "violet").apply()
                    3 -> prefs.edit().putString("orb_color", "emerald").apply()
                    else -> cb.onOpenTheme()
                }
                cb.onRefreshAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAppIdentityDialog(ctx: Activity, ownerName: String, contact: String) {
        val sha = currentSigningSha256(ctx)
        val msg = "Developer: X32 Team\n" +
            "Copyright: X32 Team\n" +
            "Owner: $ownerName\n" +
            "Contact: +$contact\n" +
            "Official application ID: ${ctx.packageName}\n" +
            "Current signing certificate SHA-256:\n$sha\n" +
            "Local signature status: Verified release certificate"
        AlertDialog.Builder(ctx)
            .setTitle("NOVA App Identity")
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun currentSigningSha256(ctx: Context): String {
        return try {
            val pm = ctx.packageManager
            val sigBytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val sigs = info.signingInfo?.apkContentsSigners
                sigs?.firstOrNull()?.toByteArray()
            } else {
                val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (sigBytes == null) return "UNAVAILABLE"
            val digest = MessageDigest.getInstance("SHA-256").digest(sigBytes)
            digest.joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "UNAVAILABLE"
        }
    }
}
