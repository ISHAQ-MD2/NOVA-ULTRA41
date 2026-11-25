package com.nova.app

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Productivity, AI Learning, Quran & Shan-e-Nuzool, News Presenter, Portfolio & Signals,
 * Health/Wellbeing, Smart Home, Work/Gaming Modes, and Autonomous Self-Diagnostics.
 */
object ProductivityEngine {
    /** Feature 12: Quran Verse & Shan-e-Nuzool Lookup (live AlQuran Cloud API + Gemini Tafsir/Shan-e-Nuzool). */
    fun lookupQuran(ctx: Context, referenceOrTopic: String): String {
        val q = referenceOrTopic.trim().ifBlank { "2:255" }
        val refMatch = Regex("^(\\d{1,3})[:/](\\d{1,3})$").find(q)
        if (refMatch != null) {
            val surah = refMatch.groupValues[1]
            val ayah = refMatch.groupValues[2]
            try {
                val url = "https://api.alquran.cloud/v1/ayah/$surah:$ayah/editions/quran-uthmani,en.sahih,ur.jalandhry"
                Net.client(readTimeoutSec = 15).newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val data = JSONObject(resp.body?.string() ?: "{}").optJSONArray("data")
                        if (data != null && data.length() >= 3) {
                            val ar = data.getJSONObject(0).optString("text")
                            val surahName = data.getJSONObject(0).optJSONObject("surah")?.optString("englishName") ?: "Surah $surah"
                            val en = data.getJSONObject(1).optString("text")
                            val ur = data.getJSONObject(2).optString("text")
                            val shan = askGeminiShort(ctx, "Briefly state the authentic Shan-e-Nuzool (circumstances of revelation) and key lesson of Quran $surah:$ayah ($surahName) in clear Urdu/English.")
                            return "📖 $surahName ($surah:$ayah)\n\n$ar\n\nUrdu: $ur\n\nEnglish: $en\n\n📜 Shan-e-Nuzool / Context:\n$shan"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return askGeminiShort(ctx, "Provide the Quran verse (Arabic, Urdu, English translation) and authentic Shan-e-Nuzool for: $q")
    }

    /** Feature 25: Personalized AI Tutor with structured lesson + interactive quiz. */
    fun generateTutorLesson(ctx: Context, topic: String, level: String = "Beginner"): String {
        val t = topic.trim().ifBlank { "Android Kotlin Basics" }
        return askGeminiShort(
            ctx,
            "Act as a patient AI Tutor for '$t' ($level level). Output:\n1) Short clear explanation\n2) Practical example\n3) 3 multiple-choice quiz questions with answers at the bottom."
        )
    }

    /** Feature 28 & 46: Personalized News & Morning Top-5 News Presenter. */
    fun morningNewsBrief(ctx: Context, topic: String = ""): String {
        val raw = Tools.execute(ctx, "get_news", JSONObject().put("topic", topic).put("lang", "en"))
        val lines = raw.lines().filter { it.isNotBlank() }.take(5)
        if (lines.isEmpty()) return "Could not fetch news right now."
        val brief = "Good morning! Here are today's top ${lines.size} headlines:\n" +
            lines.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        return if (Keys.active(ctx).isNotBlank() && Net.online(ctx)) {
            askGeminiShort(ctx, "Rewrite these 5 headlines into a crisp 45-second morning news presenter script:\n$brief")
        } else brief
    }

    /** Feature 31: Summarize text/URL and copy summary to clipboard. */
    fun summarizeText(ctx: Context, input: String): String {
        val text = if (input.trim().startsWith("http")) {
            Tools.execute(ctx, "fetch_page", JSONObject().put("url", input.trim()))
        } else input
        return VoiceIntelligence.summarizeTranscript(ctx, text)
    }

    /** Feature 27, 45, 80: Voice-to-Action Mode Shortcuts (Work Mode, Gaming Mode, Sleep/DND Mode). */
    fun activateMode(ctx: Context, mode: String): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val hasDndPerm = nm?.isNotificationPolicyAccessGranted == true
        return when (mode.lowercase()) {
            "work" -> {
                if (hasDndPerm) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                if (TermuxBridge.installed(ctx)) {
                    try { ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it) } } catch (_: Exception) {}
                }
                "Work Mode active: Priority DND ${if (hasDndPerm) "enabled" else "(grant DND access in Settings)"} & Termux ready."
            }
            "gaming" -> {
                if (hasDndPerm) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("fast_mode", true).apply()
                "Gaming Mode ON: Alarms-only DND + Fast HUD mode enabled to reduce background overhead."
            }
            "sleep" -> {
                if (hasDndPerm) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                "Sleep Schedule DND active (alarms allowed)."
            }
            "normal" -> {
                if (hasDndPerm) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("fast_mode", false).apply()
                "Normal mode restored."
            }
            else -> "Unknown mode: $mode"
        }
    }

    /** Feature 44: Smart Home HTTP/Webhook Controller (user-configured device endpoints). */
    fun triggerSmartHome(ctx: Context, deviceName: String, url: String, payloadJson: String = "{}"): String {
        if (!url.startsWith("http")) return "Enter a valid http:// or https:// local webhook URL"
        return try {
            val req = Request.Builder().url(url)
                .post(payloadJson.toRequestBody("application/json".toMediaType()))
                .build()
            Net.client(readTimeoutSec = 10).newCall(req).execute().use { resp ->
                Memory.log("smarthome", "$deviceName -> HTTP ${resp.code}")
                "$deviceName triggered (HTTP ${resp.code})"
            }
        } catch (e: Exception) {
            "Smart home request failed: ${e.message}"
        }
    }

    /** Feature 47 & 48: Live Crypto Price & Portfolio PnL + Informational Alerts (NO auto-trading). */
    fun fetchCryptoPriceUsd(symbol: String): Double? {
        val sym = symbol.uppercase().trim().let { if (it.endsWith("USDT")) it else "${it}USDT" }
        return try {
            val req = Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=$sym").build()
            Net.client(readTimeoutSec = 10).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                JSONObject(resp.body?.string() ?: "{}").optString("price").toDoubleOrNull()
            }
        } catch (_: Exception) { null }
    }

    fun portfolioSummary(ctx: Context): String {
        val list = NovaRepo.getPortfolio(ctx)
        if (list.isEmpty()) return "Portfolio empty. Add BTC, ETH, or SOL to track prices & alerts."
        val sb = StringBuilder("📊 Portfolio (Informational Only — No Auto-Trading)\n\n")
        for (a in list) {
            val cur = fetchCryptoPriceUsd(a.symbol)
            if (cur != null) {
                val pnl = (cur - a.buyPrice) * a.qty
                val pct = if (a.buyPrice > 0) ((cur - a.buyPrice) / a.buyPrice) * 100.0 else 0.0
                val alert = when {
                    a.alertAbove > 0 && cur >= a.alertAbove -> " 🔔 ABOVE ${a.alertAbove}"
                    a.alertBelow > 0 && cur <= a.alertBelow -> " 🔔 BELOW ${a.alertBelow}"
                    else -> ""
                }
                sb.append("${a.symbol}: $${String.format(Locale.US, "%.2f", cur)} | Qty ${a.qty} | PnL ${String.format(Locale.US, "%+.2f", pnl)} (${String.format(Locale.US, "%+.1f", pct)}%)$alert\n")
            } else {
                sb.append("${a.symbol}: Qty ${a.qty} @ $${a.buyPrice} (offline/unavailable)\n")
            }
        }
        return sb.toString().trim()
    }

    /** Feature 49 & 65: Step Sensor + Digital Wellbeing App Usage Stats. */
    fun readStepCount(ctx: Context, onResult: (Int) -> Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) { onResult(-1); return }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                sm.unregisterListener(this)
                onResult(ev.values.firstOrNull()?.toInt() ?: 0)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun appUsageReport(ctx: Context): List<Pair<String, Long>> {
        if (Build.VERSION.SDK_INT < 22) return emptyList()
        return try {
            val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
            val end = System.currentTimeMillis()
            val start = end - 24L * 3600 * 1000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: emptyList()
            val pm = ctx.packageManager
            stats.filter { it.totalTimeInForeground > 60_000L }
                .sortedByDescending { it.totalTimeInForeground }
                .take(15)
                .map { st ->
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(st.packageName, 0)).toString() } catch (_: Exception) { st.packageName }
                    label to (st.totalTimeInForeground / 60_000L)
                }
        } catch (_: Exception) { emptyList() }
    }

    /** Feature 50: Autonomous Safe Problem Solver (diagnostics + safe recommendations). */
    fun runSelfDiagnostics(ctx: Context): String {
        val sb = StringBuilder("🩺 NOVA System Diagnostics\n\n")
        val crashFile = File(ctx.filesDir, "crash.log")
        val crashes = if (crashFile.exists()) crashFile.readLines().takeLast(5) else emptyList()
        sb.append("• Crash log entries: ${if (crashes.isEmpty()) "Clean (0 recent crashes)" else "${crashes.size} recent"}\n")
        val freeMb = ctx.filesDir.usableSpace / (1024 * 1024)
        sb.append("• Free internal storage: $freeMb MB ${if (freeMb < 500) "⚠ Low storage — run Storage Cleaner" else "✅ Healthy"}\n")
        val online = Net.online(ctx)
        sb.append("• Network connectivity: ${if (online) "✅ Online" else "❌ Offline"}\n")
        val keyOk = Keys.active(ctx).isNotBlank()
        sb.append("• AI Key status: ${if (keyOk) "✅ Configured" else "⚠ Not set (offline fallback active)"}\n")
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        sb.append("• App RAM usage: $usedMb MB / ${rt.maxMemory() / (1024 * 1024)} MB\n")
        if (crashes.isNotEmpty()) sb.append("\nLast crash snippet:\n${crashes.last().take(240)}")
        return sb.toString()
    }

    private fun askGeminiShort(ctx: Context, prompt: String): String {
        if (Keys.active(ctx).isBlank() || !Net.online(ctx)) {
            return "Offline / No Gemini API key configured. Configure your key in Setup for full AI answers."
        }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val res = GeminiRest.post(ctx, body)
        if (!res.ok) return res.text
        return try {
            val parts = JSONObject(res.text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            buildString { if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "") ?: "") }.ifBlank { res.text }
        } catch (_: Exception) { res.text }
    }
}
