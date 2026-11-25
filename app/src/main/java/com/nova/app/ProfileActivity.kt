package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

/** Description (apna naam + dost ke naam/number), country code, yaaddaasht aur scheduled tasks. */
class ProfileActivity : Activity() {
    private lateinit var box: LinearLayout
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Memory.init(this)
        val prefs = getSharedPreferences("nova", MODE_PRIVATE)
        val sv = ScrollView(this); sv.background = SunsetUiHelper.pageBg()
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(40)) }
        sv.addView(box); setContentView(sv)

        head("1) Description (NOVA always reads this)")
        text("Apna naam, dost ke naam aur number yahan likho - har dost ek line mein. Jaise:\n" +
            "Mera naam ISHAQ hai, number 923141613159\n" +
            "Sajid (dost, WhatsApp Business) 0311 7654321\n" +
            "Ammi 0345 1112223 - subah 8 baje yaad dilana\n" +
            "Phir bolo: \"Sajid ko message karo kahan ho\" - NOVA yahan se number dhoondh leti hai, warna phone contacts mein.")
        val desc = EditText(this).apply {
            setTextColor(SunsetColors.TEXT_PRIMARY); setHintTextColor(SunsetColors.TEXT_MUTED); hint = "Mera naam ISHAQ...\nSajid 03xx..."
            background = SunsetUiHelper.glassCard(this@ProfileActivity, 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 8; gravity = Gravity.TOP; setText(prefs.getString("desc", "") ?: "")
        }
        box.addView(desc)
        head("Country code (0 se shuru numbers ke liye)")
        val cc = EditText(this).apply {
            setTextColor(SunsetColors.TEXT_PRIMARY); inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine()
            background = SunsetUiHelper.glassCard(this@ProfileActivity, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setText(prefs.getString("cc", "92") ?: "92")
        }
        box.addView(cc)
        btn("💾 Save") {
            prefs.edit().putString("desc", desc.text.toString()).putString("cc", cc.text.toString().filter { it.isDigit() }.ifEmpty { "92" }).apply()
            Backup.saveProfile(this)
            Toast.makeText(this, "Saved - NOVA ko dobara Start karo taake naya description le", Toast.LENGTH_LONG).show()
        }

        head("2) Scheduled tasks / reminders")
        val tasks = TaskStore.all(this)
        text(if (tasks.isEmpty()) "Koi nahi. Bolo: \"12:30 par Sajid ko message karna ki kahan ho\"." else tasks.joinToString("\n") { TaskStore.describe(it) })

        head("3) Yaaddaasht (aakhri baatein)")
        head("🧠 NOVA ko ye yaad hai (pasand, dost, notes)")
        text(Memory.notes(this).takeLast(80).reversed().joinToString("\n").ifEmpty { "Abhi kuch save nahi. NOVA se bolo: 'yaad rakhna, mujhe aam pasand hai'." })
        text("Memory size: ${Memory.sizeBytes(this) / 1024} KB (limit 30 MB) | Backup: /sdcard/NOVA")
        text(Memory.recent(this, 40).reversed().joinToString("\n").ifEmpty { "Abhi khali hai." })
        btn("🗑 Yaaddaasht saaf karo") {
            AlertDialog.Builder(this).setMessage("Saari baat-cheet aur notes delete kar dun?")
                .setPositiveButton("Haan") { _, _ -> Memory.clearAll(this); recreate() }.setNegativeButton("Nahi", null).show()
        }
    }

    private fun head(t: String) = box.addView(TextView(this).apply { text = t; textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(SunsetColors.CORAL); setPadding(0, dp(18), 0, dp(6)) })
    private fun text(t: String) = box.addView(TextView(this).apply { text = t; setTextColor(SunsetColors.TEXT_SECONDARY) })
    private fun btn(t: String, a: () -> Unit) = box.addView(Button(this).apply {
        text = t; setTextColor(Color.WHITE); isAllCaps = false; minimumHeight = dp(48)
        background = SunsetUiHelper.gradientPill(this@ProfileActivity, 22)
        setOnClickListener { a() }
    }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8); bottomMargin = dp(8) })
}
