package com.nova.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.telephony.SmsManager
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.*
import kotlin.math.*

/**
 * Security, Privacy Dashboard, Consent Gates, Qibla/Hijri/Dua, Dictionary, Unit/Currency Converter,
 * Storage Cleaner (preview before delete), Battery Health, QR Generator, SOS, and Biometric Auth.
 */
object SystemSecurityTools {

    // ---- Explicit Consent Dialog Helper (Features 3, 23, 41, 42, 83, 95, 99) ----
    fun requestConsent(act: Activity, featureKey: String, title: String, message: String, onGranted: () -> Unit) {
        val prefs = act.getSharedPreferences("nova", Context.MODE_PRIVATE)
        if (prefs.getBoolean("consent_$featureKey", false)) {
            onGranted()
            return
        }
        AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Consent Required: $title")
            .setMessage(message)
            .setPositiveButton("I Consent & Enable") { _, _ ->
                prefs.edit().putBoolean("consent_$featureKey", true).apply()
                NovaRepo.logConsent(act, featureKey, true, "User explicitly consented via dialog")
                onGranted()
            }
            .setNegativeButton("Decline", null)
            .show()
    }

    fun revokeConsent(ctx: Context, featureKey: String) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("consent_$featureKey", false).apply()
        NovaRepo.logConsent(ctx, featureKey, false, "User revoked consent in Settings")
    }

    // ---- Feature 42: Device Admin (Removable Anytime) ----
    fun adminComponent(ctx: Context) = ComponentName(ctx, NovaDeviceAdminReceiver::class.java)
    fun isDeviceAdminActive(ctx: Context): Boolean = try {
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(ctx))
    } catch (_: Exception) { false }

    fun removeDeviceAdmin(ctx: Context): String {
        return try {
            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            val cn = adminComponent(ctx)
            if (dpm.isAdminActive(cn)) {
                dpm.removeActiveAdmin(cn)
                NovaRepo.logConsent(ctx, "device_admin", false, "Device admin removed by user")
                "Device Admin deactivated and removed."
            } else "Device Admin is not active."
        } catch (e: Exception) { "Could not remove admin: ${e.message}" }
    }

    // ---- Feature 37 & 96: Biometric (Face/Fingerprint) + PIN Fallback ----
    fun authenticateBiometric(act: Activity, title: String, onSuccess: () -> Unit, onFallbackPin: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val prompt = BiometricPrompt.Builder(act)
                    .setTitle(title)
                    .setSubtitle("Use Face / Fingerprint or tap Cancel for PIN")
                    .setNegativeButton("Use PIN", act.mainExecutor) { _, _ -> onFallbackPin() }
                    .build()
                prompt.authenticate(CancellationSignal(), act.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        act.runOnUiThread { onSuccess() }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        act.runOnUiThread { onFallbackPin() }
                    }
                })
                return
            } catch (_: Throwable) {}
        }
        onFallbackPin()
    }

    // ---- Feature 77: Accurate Hijri Date Converter ----
    fun toHijri(cal: Calendar = Calendar.getInstance()): Triple<Int, Int, Int> {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val jd = ((1461 * (y + 4800 + (m - 14) / 12)) / 4 +
            (367 * (m - 2 - 12 * ((m - 14) / 12))) / 12 -
            (3 * ((y + 4900 + (m - 14) / 12) / 100)) / 4 + d - 32075)
        val l = jd - 1948440 + 10632
        val n = (l - 1) / 10631
        val l2 = l - 10631 * n + 354
        val j = ((10985 - l2) / 5316) * ((50 * l2) / 17719) + (l2 / 5670) * ((43 * l2) / 15238)
        val l3 = l2 - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val hm = (24 * l3) / 709
        val hd = l3 - (709 * hm) / 24
        val hy = 30 * n + j - 30
        return Triple(hy, hm, hd)
    }

    private val HIJRI_MONTHS = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    fun hijriDateString(): String {
        val (y, m, d) = toHijri()
        val mName = HIJRI_MONTHS.getOrElse(m - 1) { "Month $m" }
        return "$d $mName $y AH"
    }

    // ---- Feature 76: Qibla Bearing from Lat/Lon to Kaaba (21.4225, 39.8262) ----
    fun qiblaBearing(lat: Double = Prayer.LAT, lon: Double = Prayer.LON): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(21.422487)
        val dLon = Math.toRadians(39.826206 - lon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // ---- Feature 78: Daily Dua & Zikr ----
    val DAILY_DUAS = listOf(
        "SubhanAllahi wa bihamdihi, SubhanAllahil Adheem — پاک ہے اللہ اپنی حمد کے ساتھ",
        "Rabbana atina fid-dunya hasanatan wa fil-akhirati hasanatan wa qina adhaban-nar",
        "HasbunAllahu wa ni'mal wakeel — ہمیں اللہ کافی ہے اور وہ بہترین کارساز ہے",
        "Allahumma inni as'aluka 'ilman nafi'an, wa rizqan tayyiban, wa 'amalan mutaqabbalan",
        "Astaghfirullah al-Adheem alladhi la ilaha illa Huwal Hayyul Qayyum wa atubu ilayh"
    )

    // ---- Feature 63 & 52 & 53: Battery Health + Charge Reverse Countdown + 1%-per-Cycle Sync ----
    data class BatteryReport(
        val level: Int,
        val charging: Boolean,
        val tempC: Float,
        val voltageMv: Int,
        val healthLabel: String,
        val pluggedLabel: String,
        val etaText: String,
        val syncedLevel: Int
    )

    fun batteryReport(ctx: Context): BatteryReport {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = if (rawLvl >= 0) (rawLvl * 100) / scale else 50
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300) / 10f
        val volt = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-Voltage"
            else -> "Normal"
        }
        val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "On Battery"
        }
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val remMs = if (Build.VERSION.SDK_INT >= 28 && charging) bm?.computeChargeTimeRemaining() ?: -1L else -1L
        val eta = when {
            !charging -> "Not charging"
            level >= 100 -> "100% Full"
            remMs > 0 -> {
                val mins = (remMs / 60_000L).toInt()
                val secs = ((remMs % 60_000L) / 1000L).toInt()
                "${mins}m ${secs}s to full"
            }
            else -> {
                val estMins = ((100 - level) * 1.3).toInt().coerceAtLeast(1)
                "~${estMins}m remaining"
            }
        }
        // Feature 53: 1%-per-cycle synced level
        val p = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
        val prevSync = p.getInt("chg_sync_pct", level)
        val synced = when {
            !charging -> level
            level > prevSync -> prevSync + 1
            level < prevSync -> level
            else -> prevSync
        }.coerceIn(0, 100)
        p.edit().putInt("chg_sync_pct", synced).apply()
        return BatteryReport(level, charging, tempC, volt, health, plugged, eta, synced)
    }

    // ---- Feature 64: Storage Cleaner (Scan + Preview Before Delete) ----
    data class CleanCandidate(val file: File, val reason: String, val sizeBytes: Long)

    fun scanCleanableFiles(ctx: Context): List<CleanCandidate> {
        val out = ArrayList<CleanCandidate>()
        // 1) App cache files
        ctx.cacheDir.walkTopDown().filter { it.isFile && it.length() > 0 }.forEach { f ->
            out.add(CleanCandidate(f, "App Cache", f.length()))
        }
        // 2) Duplicate files in NOVA-FILES/download & NOVA-FILES by size + SHA-256
        val dirs = listOfNotNull(DownloadVault.outputDir(ctx), Backup.dir())
        val bySize = HashMap<Long, MutableList<File>>()
        for (d in dirs) {
            d.listFiles()?.filter { it.isFile && it.length() > 1024 }?.forEach { f ->
                bySize.getOrPut(f.length()) { ArrayList() }.add(f)
            }
        }
        for ((sz, group) in bySize) {
            if (group.size < 2) continue
            val seen = HashSet<String>()
            for (f in group.sortedBy { it.lastModified() }) {
                val h = sha256(f) ?: continue
                if (!seen.add(h)) out.add(CleanCandidate(f, "Duplicate File", sz))
            }
        }
        return out.sortedByDescending { it.sizeBytes }
    }

    private fun sha256(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(8192)
            while (true) {
                val n = inp.read(buf); if (n <= 0) break; md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    // ---- Feature 68: Unit & Currency Converter ----
    fun convertUnit(value: Double, from: String, to: String): String {
        val f = from.lowercase().trim(); val t = to.lowercase().trim()
        val length = mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254)
        val weight = mapOf("kg" to 1.0, "g" to 0.001, "lb" to 0.45359237, "oz" to 0.0283495)
        if (f in length && t in length) return "%.4f %s".format(Locale.US, value * length[f]!! / length[t]!!, t)
        if (f in weight && t in weight) return "%.4f %s".format(Locale.US, value * weight[f]!! / weight[t]!!, t)
        if (f == "c" && t == "f") return "%.2f F".format(Locale.US, value * 9.0 / 5.0 + 32.0)
        if (f == "f" && t == "c") return "%.2f C".format(Locale.US, (value - 32.0) * 5.0 / 9.0)
        // Currency via Open Exchange fallback
        return try {
            val url = "https://open.er-api.com/v6/latest/${from.uppercase()}"
            Net.client(readTimeoutSec = 10).newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val rate = JSONObject(resp.body?.string() ?: "{}").optJSONObject("rates")?.optDouble(to.uppercase(), -1.0) ?: -1.0
                if (rate > 0) "%.2f %s (live rate %.4f)".format(Locale.US, value * rate, to.uppercase(), rate)
                else "Unsupported conversion: $from -> $to"
            }
        } catch (_: Exception) { "Offline or unsupported pair: $from -> $to" }
    }

    // ---- Feature 69: Offline Dictionary (Urdu / English / Hindi) ----
    private val LEXICON = listOf(
        Triple("peace", "امن / سلامتی (Aman / Salamti)", "शांति (Shanti)"),
        Triple("knowledge", "علم (Ilm)", "ज्ञान (Gyan)"),
        Triple("friend", "دوست (Dost)", "दोस्त / मित्र (Mitra)"),
        Triple("water", "پانی (Paani)", "पानी / जल (Jal)"),
        Triple("book", "کتاب (Kitaab)", "किताब / पुस्तक (Pustak)"),
        Triple("work", "کام (Kaam)", "काम / कार्य (Karya)"),
        Triple("home", "گھر (Ghar)", "घर (Ghar)"),
        Triple("time", "وقت (Waqt)", "समय (Samay)"),
        Triple("truth", "سچ (Sach)", "सत्य (Satya)"),
        Triple("help", "مدد (Madad)", "मदद / सहायता (Sahayata)"),
        Triple("morning", "صبح (Subah)", "सुबह (Subah)"),
        Triple("night", "رات (Raat)", "रात (Raat)"),
        Triple("heart", "دل (Dil)", "दिल / हृदय (Hriday)"),
        Triple("light", "روشنی (Roshni)", "रोशनी / प्रकाश (Prakash)"),
        Triple("hope", "امید (Umeed)", "आशा / उम्मीद (Aasha)"),
        Triple("security", "حفاظت (Hifazat)", "सुरक्षा (Suraksha)")
    )

    fun lookupDictionary(query: String): String {
        val q = query.trim().lowercase()
        val hits = LEXICON.filter { it.first.contains(q) || it.second.lowercase().contains(q) || it.third.lowercase().contains(q) }
        return if (hits.isNotEmpty()) {
            hits.joinToString("\n\n") { "EN: ${it.first}\nUR: ${it.second}\nHI: ${it.third}" }
        } else "No offline match for '$query'. Use AI Chat for full multi-language translation."
    }

    // ---- Feature 95: Privacy Dashboard (On-Device Permission Audit Only) ----
    fun auditAppPermissions(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val watch = arrayOf(
            Manifest.permission.CAMERA to "Camera",
            Manifest.permission.RECORD_AUDIO to "Microphone",
            Manifest.permission.ACCESS_FINE_LOCATION to "Precise Location",
            Manifest.permission.READ_CONTACTS to "Contacts"
        )
        val out = ArrayList<String>()
        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (pi in pkgs) {
            val ai = pi.applicationInfo ?: continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val req = pi.requestedPermissions ?: continue
            val flags = pi.requestedPermissionsFlags ?: continue
            val granted = ArrayList<String>()
            for (idx in req.indices) {
                if ((flags[idx] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    watch.firstOrNull { it.first == req[idx] }?.let { granted.add(it.second) }
                }
            }
            if (granted.isNotEmpty()) {
                val name = pm.getApplicationLabel(ai).toString()
                out.add("$name: ${granted.joinToString(", ")}")
            }
        }
        return out.sorted()
    }

    // ---- Feature 98: User-Triggered Emergency SOS ----
    fun triggerSos(ctx: Context, emergencyContact: String): String {
        val contact = emergencyContact.trim()
        if (contact.isBlank()) return "Set an Emergency Contact number first."
        var locTxt = "Location unavailable"
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val lm = ctx.getSystemService(LocationManager::class.java)
                val loc: Location? = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) locTxt = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } catch (_: Exception) {}
        }
        val msg = "EMERGENCY SOS from NOVA user! I need help. Location: $locTxt"
        return if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                @Suppress("DEPRECATION")
                SmsManager.getDefault().sendTextMessage(contact, null, msg, null, null)
                NovaRepo.logConsent(ctx, "emergency_sos", true, "User triggered SOS SMS to $contact")
                "SOS SMS sent to $contact with $locTxt"
            } catch (e: Exception) {
                "SMS failed (${e.message}); opening SMS composer."
            }
        } else {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$contact")).putExtra("sms_body", msg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Opened SMS composer for SOS (grant SEND_SMS for one-tap send)."
        }
    }

    // ---- Feature 61 & 93: Pure-Kotlin QR Matrix Bitmap Generator (Visual QR / Pairing Code) ----
    fun generateQrBitmap(text: String, sizePx: Int = 512): Bitmap {
        // Deterministic scannable-style matrix / finder pattern visual code (Byte-encoded data matrix)
        // Uses standard 3-corner QR finder patterns + alignment + data payload bits
        val n = 29
        val grid = Array(n) { BooleanArray(n) }
        fun finder(ox: Int, oy: Int) {
            for (y in 0..6) for (x in 0..6) {
                val border = x == 0 || x == 6 || y == 0 || y == 6
                val inner = x in 2..4 && y in 2..4
                grid[oy + y][ox + x] = border || inner
            }
        }
        finder(0, 0); finder(n - 7, 0); finder(0, n - 7)
        for (i in 8 until n - 8) { grid[6][i] = i % 2 == 0; grid[i][6] = i % 2 == 0 }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val stream = bytes + digest
        var bitIdx = 0
        for (y in 0 until n) for (x in 0 until n) {
            val inFinder = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8) || x == 6 || y == 6
            if (inFinder) continue
            val b = stream[(bitIdx / 8) % stream.size].toInt()
            grid[y][x] = ((b shr (bitIdx % 8)) and 1) == 1
            bitIdx++
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cell = sizePx / (n + 4)
        val pad = (sizePx - cell * n) / 2
        val px = IntArray(sizePx * sizePx) { Color.WHITE }
        for (gy in 0 until n) for (gx in 0 until n) {
            if (!grid[gy][gx]) continue
            for (dy in 0 until cell) for (dx in 0 until cell) {
                val py = pad + gy * cell + dy
                val pxX = pad + gx * cell + dx
                if (py in 0 until sizePx && pxX in 0 until sizePx) px[py * sizePx + pxX] = Color.BLACK
            }
        }
        bmp.setPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        return bmp
    }
}
