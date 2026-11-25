package com.nova.app

import android.app.Activity
import android.content.Context
import android.view.View

/**
 * Multilingual UI & Language Intelligence (English, Urdu RTL, Hindi).
 * Handles RTL layout mirroring for Urdu and automatic script/dialect detection.
 */
object I18n {
    enum class Lang(val code: String, val label: String, val isRtl: Boolean) {
        EN("en", "English", false),
        UR("ur", "اردو (RTL)", true),
        HI("hi", "हिन्दी", false)
    }

    fun current(ctx: Context): Lang {
        val code = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("ui_lang", "en") ?: "en"
        return Lang.values().firstOrNull { it.code == code } ?: Lang.EN
    }

    fun setLang(ctx: Context, lang: Lang) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putString("ui_lang", lang.code).apply()
    }

    fun applyDirection(act: Activity) {
        val rtl = current(act).isRtl
        try {
            act.window.decorView.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        } catch (_: Throwable) {}
    }

    fun fontScale(ctx: Context): Float =
        if (ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("acc_large_text", false)) 1.24f else 1.0f

    fun highContrast(ctx: Context): Boolean =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("acc_high_contrast", false)

    /** Feature 24: Detect language of user text (Urdu script, Hindi script, Roman Urdu/Hinglish, English). */
    fun detectLanguage(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "English"
        var arabic = 0
        var devanagari = 0
        var latin = 0
        for (ch in t) {
            when (ch.code) {
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF -> arabic++
                in 0x0900..0x097F -> devanagari++
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> latin++
            }
        }
        if (arabic > devanagari && arabic >= 2) return "Urdu"
        if (devanagari > arabic && devanagari >= 2) return "Hindi"
        val low = " ${t.lowercase()} "
        val romanUrdu = listOf(
            " kya ", " hai ", " hain ", " karo ", " kar ", " batao ", " mera ", " meri ",
            " mujhe ", " aap ", " tum ", " yaad ", " namaz ", " mausam ", " gaana ", " chalao ",
            " kholo ", " bhejo ", " kahan ", " kab ", " kyun ", " theek ", " shukriya ", " salam "
        )
        if (romanUrdu.any { it in low }) return "Roman Urdu"
        return if (latin > 0) "English" else "English"
    }

    /** Feature 5: Normalize regional Urdu/Punjabi/Saraiki colloquial phrasing into clean intent tokens. */
    fun normalizeUrduDialect(raw: String): String {
        var s = raw.trim()
        val replacements = listOf(
            "کرو" to "karo", "کھولو" to "kholo", "چلاؤ" to "chalao", "بھیجو" to "bhejo",
            "دکھاؤ" to "dikhao", "بتاؤ" to "batao", "بند کرو" to "band karo",
            "ki haal" to "kya haal", "kithe" to "kahan", "kithay" to "kahan",
            "kiddan" to "kaise", "tusin" to "aap", "tusi" to "aap", "assi" to "hum",
            "saada" to "hamara", "maida" to "mera", "meda" to "mera", "tuhada" to "aapka",
            "chalaye" to "chalao", "laa de" to "laga do", "vekh" to "dekho", "sunao" to "batao",
            "wajan" to "volume", "awaaz" to "volume", "batti" to "torch", "channan" to "torch"
        )
        for ((from, to) in replacements) {
            s = s.replace(from, to, ignoreCase = true)
        }
        return s
    }

    private val strings: Map<String, Triple<String, String, String>> = mapOf(
        "hub_title" to Triple("NOVA Command Hub", "نووا کمانڈ ہب", "नोवा कमांड हब"),
        "hub_sub" to Triple("All AI, Voice, Security, Islamic & System Tools", "تمام اے آئی، آواز، سیکیورٹی اور سسٹم ٹولز", "सभी एआई, वॉइस, सुरक्षा और सिस्टम टूल"),
        "tap_speak" to Triple("Tap to speak", "بولنے کے لیے دبائیں", "बोलने के लिए दबाएं"),
        "listening" to Triple("Listening... tap to stop", "سن رہی ہوں... روکنے کے لیے دبائیں", "सुन रही हूँ... रोकने के लिए दबाएं"),
        "voice_mode" to Triple("Voice Mode", "وائس موڈ", "वॉइस मोड"),
        "text_mode" to Triple("Text Mode ✓", "ٹیکسٹ موڈ ✓", "टेक्स्ट मोड ✓"),
        "settings" to Triple("Settings", "سیٹنگز", "सेटिंग्स"),
        "hub_btn" to Triple("NOVA Hub", "نووا ہب", "नोवा हब"),
        "sos" to Triple("Emergency SOS", "ایمرجنسی ایس او ایس", "इमरजेंसी एसओएस"),
        "qibla" to Triple("Qibla & Namaz", "قبلہ اور نماز", "क़िबला और नमाज़"),
        "search_hint" to Triple("Search 98 tools & toggles...", "98 ٹولز اور سیٹنگز تلاش کریں...", "98 टूल और सेटिंग खोजें..."),
        "weather" to Triple("Weather", "موسم", "मौसम"),
        "time" to Triple("Time", "وقت", "समय"),
        "azan" to Triple("Azan", "اذان", "अज़ान"),
        "music" to Triple("Music", "موسیقی", "संगीत"),
        "reminder" to Triple("Reminder", "یاددہانی", "रिमाइंडर"),
        "ready" to Triple("Ready", "تیار", "तैयार"),
        "type_hint" to Triple("Write a message to NOVA...", "نووا کو پیغام لکھیں...", "नोवा को संदेश लिखें..."),
        "cat_all" to Triple("All", "سب", "सभी"),
        "cat_voice" to Triple("Voice & AI", "آواز اور اے آئی", "वॉइस और एआई"),
        "cat_prod" to Triple("Productivity", "پروڈکٹیوٹی", "प्रोडक्टिविटी"),
        "cat_sec" to Triple("Security & Privacy", "سیکیورٹی و پرائیویسی", "सुरक्षा व गोपनीयता"),
        "cat_islamic" to Triple("Islamic & Life", "اسلامی و طرزِ زندگی", "इस्लामिक व जीवन"),
        "cat_sys" to Triple("System & Tools", "سسٹم اور ٹولز", "सिस्टम व टूल")
    )

    fun t(ctx: Context, key: String): String {
        val tr = strings[key] ?: return key
        return when (current(ctx)) {
            Lang.EN -> tr.first
            Lang.UR -> tr.second
            Lang.HI -> tr.third
        }
    }
}
