package com.nova.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream

/**
 * UI Dialogs for Notes/Diary, Passwords, Snippets/Clipboard/Urdu Helper,
 * Storage Cleaner (with Preview), Battery/Charging, Health/Diagnostics, Smart Home,
 * QR Code Generator, Screenshot Annotator, Screen Recorder, Privacy Dashboard,
 * Removable Device Admin, Anti-Theft, Call Screening, Emergency SOS & Bug Report.
 */
object NovaHubSecUi {
    private fun dp(ctx: Context, x: Int) = (x * ctx.resources.displayMetrics.density).toInt()
    private fun dlg(act: Activity) = AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun btn(ctx: Context, label: String, accent: Int, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label; isAllCaps = false; textSize = 13f * I18n.fontScale(ctx)
        setTextColor(Color.WHITE); minimumHeight = dp(ctx, 48)
        background = GradientDrawable().apply { cornerRadius = dp(ctx, 14).toFloat(); setColor(Color.parseColor("#161D30")); setStroke(dp(ctx, 1), accent) }
        setOnClickListener { onClick() }
    }

    // ---- Features 38 & 58: Notes & Encrypted Diary ----
    fun showNotesAndDiary(act: Activity, accent: Int, diaryMode: Boolean) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val titleEt = EditText(act).apply { hint = if (diaryMode) "Diary Title" else "Note Title"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val bodyEt = EditText(act).apply { hint = "Write here (AES-GCM encrypted if Diary)"; minLines = 3; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun reload() {
            listBox.removeAllViews()
            for (n in NovaRepo.getNotes(act, diaryMode)) {
                val tv = TextView(act).apply {
                    text = "${if (n.isEncrypted) "🔐" else "📝"} ${n.title}\n${n.body.take(240)}"
                    setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(act, 10), dp(act, 8), dp(act, 10), dp(act, 8))
                    background = GradientDrawable().apply { cornerRadius = dp(act, 12).toFloat(); setColor(Color.parseColor("#111726")) }
                    setOnLongClickListener { NovaRepo.deleteNote(act, n.id); reload(); true }
                }
                listBox.addView(tv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
            }
        }
        reload()
        box.addView(titleEt); box.addView(bodyEt)
        box.addView(btn(act, if (diaryMode) "🔐 Save Encrypted Diary" else "💾 Save Note", accent) {
            val b = bodyEt.text.toString().trim()
            if (b.isNotEmpty()) {
                NovaRepo.addNote(act, titleEt.text.toString(), b, diaryMode, encrypt = diaryMode)
                titleEt.setText(""); bodyEt.setText(""); reload()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(listBox)
        dlg(act).setTitle(if (diaryMode) "🔐 Encrypted Diary" else "📝 Notes (Long-press to delete)").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Features 39, 40, 60, 92, 94: Passwords, Encrypted Backups & Cloud Connectors ----
    fun showVaultAndCloudBackup(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val status = TextView(act).apply { setTextColor(Color.parseColor("#B5C7DF")); textSize = 13f }
        val pwEt = EditText(act).apply { hint = "Backup encryption password (min 4 chars)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        box.addView(pwEt)
        box.addView(btn(act, "🔒 Create Password-Encrypted Backup (.nova)", accent) {
            val pw = pwEt.text.toString()
            if (pw.length < 4) { status.text = "Password must be at least 4 characters."; return@btn }
            Thread {
                val json = Backup.export(act)
                val enc = Vault.encrypt(json.toByteArray(Charsets.UTF_8), pw)
                val dir = Backup.sub("backups") ?: act.filesDir
                val f = File(dir, "NOVA-backup-${System.currentTimeMillis() % 100000}.json.nova")
                f.writeBytes(enc)
                h.post { status.text = "Encrypted backup saved:\n${f.absolutePath}" }
            }.start()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(btn(act, "☁ Open Google Drive / Dropbox Export", accent) {
            val dir = Backup.sub("backups") ?: act.filesDir
            val f = File(dir, "NOVA-cloud-export.json").apply { writeText(Backup.export(act)) }
            val send = Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_TEXT, "NOVA Backup JSON (${f.name})")
            try { act.startActivity(Intent.createChooser(send, "Upload Backup to Drive / Dropbox")) } catch (_: Exception) {
                status.text = "Exported locally to ${f.absolutePath}"
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(btn(act, "🗝 View Encrypted Password Vault (${PasswordStore.list(act).size})", accent) {
            val creds = PasswordStore.list(act)
            val txt = if (creds.isEmpty()) "No saved logins yet." else creds.joinToString("\n") { "• ${it.host} (${it.user})" }
            dlg(act).setTitle("🗝 Password Vault").setMessage(txt).setPositiveButton("OK", null).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })
        dlg(act).setTitle("🔐 Vault & Cloud Backup").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Features 81, 85, 86, 87: Clipboard, Snippets, Urdu Phonetic & Quick Templates ----
    fun showSnippetsAndUrduTools(act: Activity, accent: Int) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val sc = EditText(act).apply { hint = "Shortcut (e.g. /sig or /salam)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val exp = EditText(act).apply { hint = "Expanded text / template"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val out = TextView(act).apply { setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(act, 8), 0, 0) }
        fun reload() {
            val list = NovaRepo.getSnippets(act)
            out.text = "Snippets:\n" + (if (list.isEmpty()) "(none yet — add /sig)" else list.joinToString("\n") { "${it.shortcut} -> ${it.expansion}" })
        }
        reload()
        box.addView(sc); box.addView(exp)
        box.addView(btn(act, "💾 Save Text Snippet / Quick Reply", accent) {
            if (sc.text.isNotBlank() && exp.text.isNotBlank()) {
                NovaRepo.saveSnippet(act, sc.text.toString(), exp.text.toString())
                sc.setText(""); exp.setText(""); reload()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(btn(act, "🌐 Convert Roman Urdu -> Urdu Script Preview", accent) {
            val norm = I18n.normalizeUrduDialect(exp.text.toString())
            ClipStore.putOnClipboard(act, norm)
            out.text = "Normalized & copied: $norm"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(out)
        dlg(act).setTitle("✂ Snippets, Templates & Urdu Helper").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Feature 64: Storage Cleaner with Explicit Preview Before Delete ----
    fun showStorageCleaner(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val candidates = ArrayList<SystemSecurityTools.CleanCandidate>()
        val checked = ArrayList<Boolean>()
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val summary = TextView(act).apply { text = "Scanning cache & duplicate files..."; setTextColor(Color.WHITE); textSize = 13f }
        val listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        box.addView(summary); box.addView(listBox)
        Thread {
            val found = SystemSecurityTools.scanCleanableFiles(act)
            h.post {
                candidates.clear(); candidates.addAll(found)
                checked.clear(); repeat(found.size) { checked.add(true) }
                val totalKb = found.sumOf { it.sizeBytes } / 1024
                summary.text = if (found.isEmpty()) "Storage is clean! 0 cache/duplicate items found."
                else "Preview (${found.size} files, $totalKb KB). Uncheck anything to keep:"
                listBox.removeAllViews()
                found.take(40).forEachIndexed { idx, item ->
                    val cb = CheckBox(act).apply {
                        text = "${item.reason}: ${item.file.name} (${maxOf(1L, item.sizeBytes / 1024)} KB)"
                        isChecked = true; setTextColor(Color.WHITE); textSize = 12f
                        setOnCheckedChangeListener { _, isCh -> checked[idx] = isCh }
                    }
                    listBox.addView(cb)
                }
            }
        }.start()
        dlg(act).setTitle("🧹 Storage Cleaner (Preview)").setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("Delete Selected") { _, _ ->
                var freed = 0L; var count = 0
                candidates.forEachIndexed { idx, c ->
                    if (idx < checked.size && checked[idx] && c.file.delete()) { freed += c.sizeBytes; count++ }
                }
                Toast.makeText(act, "Deleted $count items (${freed / 1024} KB freed)", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---- Features 4, 44, 49, 50, 52, 63, 65: Health, Battery, Diagnostics, Smart Home, Calendar ----
    fun showSystemHealthDialog(act: Activity, accent: Int) {
        val h = Handler(Looper.getMainLooper())
        val br = SystemSecurityTools.batteryReport(act)
        val diag = ProductivityEngine.runSelfDiagnostics(act)
        val usage = ProductivityEngine.appUsageReport(act)
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val tv = TextView(act).apply {
            text = "🔋 Battery: ${br.level}% (${br.pluggedLabel}, ${br.tempC}°C, ${br.healthLabel})\n⏳ ${br.etaText}\n\n$diag\n\n📱 24h Usage:\n" +
                if (usage.isEmpty()) "Grant Usage Access in Settings to view app screen time."
                else usage.take(8).joinToString("\n") { "• ${it.first}: ${it.second} min" }
            setTextColor(Color.WHITE); textSize = 12.5f * I18n.fontScale(act); setTextIsSelectable(true)
        }
        box.addView(tv)
        box.addView(btn(act, "⚡ Launch Charging Countdown Screen", accent) {
            act.startActivity(Intent(act, ChargingActivity::class.java))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })
        box.addView(btn(act, "👟 Read Step Counter Sensor", accent) {
            ProductivityEngine.readStepCount(act) { steps ->
                h.post { Toast.makeText(act, if (steps < 0) "No hardware step sensor" else "Steps since boot: $steps", Toast.LENGTH_SHORT).show() }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(btn(act, "📅 Quick Add Calendar Event", accent) {
            val ci = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, "NOVA Reminder")
            try { act.startActivity(ci) } catch (_: Exception) {}
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        dlg(act).setTitle("🩺 Health, Battery & Diagnostics").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    // ---- Features 61, 72, 73, 93: QR Code Generator, Screenshot Annotator, Screen Recorder ----
    fun showQrGenerator(act: Activity, accent: Int) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val et = EditText(act).apply { hint = "Text, Wi-Fi or Device Pairing URI"; setText("nova://pair?host=${android.os.Build.MODEL}"); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val iv = ImageView(act).apply { contentDescription = "Generated QR pairing matrix" }
        fun render() { iv.setImageBitmap(SystemSecurityTools.generateQrBitmap(et.text.toString().ifBlank { "NOVA" }, 460)) }
        render()
        box.addView(et, LinearLayout.LayoutParams(-1, -2))
        box.addView(btn(act, "⬛ Generate QR / Pairing Matrix", accent) { render() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })
        box.addView(iv, LinearLayout.LayoutParams(dp(act, 220), dp(act, 220)).apply { topMargin = dp(act, 10) })
        dlg(act).setTitle("📱 QR Generator & Multi-Device Pair").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }

    fun showScreenshotAnnotator(act: Activity, accent: Int) {
        val strokes = ArrayList<Pair<Path, Int>>()
        var curColor = Color.parseColor("#00E5FF")
        val canvasView = object : View(act) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND }
            private var curPath: Path? = null
            override fun onDraw(c: Canvas) {
                c.drawColor(Color.parseColor("#111726"))
                for ((path, col) in strokes) { p.color = col; c.drawPath(path, p) }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { curPath = Path().apply { moveTo(e.x, e.y) }; strokes.add(curPath!! to curColor) }
                    MotionEvent.ACTION_MOVE -> curPath?.lineTo(e.x, e.y)
                }
                invalidate(); return true
            }
        }
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8)) }
        box.addView(canvasView, LinearLayout.LayoutParams(-1, dp(act, 300)))
        val row = LinearLayout(act)
        row.addView(btn(act, "🎨 Red", accent) { curColor = Color.RED }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn(act, "🧹 Clear", accent) { strokes.clear(); canvasView.invalidate() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn(act, "💾 Save PNG", accent) {
            val bmp = Bitmap.createBitmap(canvasView.width.coerceAtLeast(100), canvasView.height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
            canvasView.draw(Canvas(bmp))
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NOVA-Annotated").apply { mkdirs() }
            val f = File(dir, "note_${System.currentTimeMillis() % 100000}.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            Toast.makeText(act, "Saved ${f.name}", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })
        dlg(act).setTitle("✏ Canvas Annotator").setView(box).setNegativeButton("Close", null).show()
    }

    // ---- Features 41, 42, 83, 84, 95, 98, 99: Consent-Gated Security, Privacy & SOS ----
    fun showSecurityAndPrivacyDialog(act: Activity, accent: Int, reqScreenRecCode: Int) {
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10)) }
        val info = TextView(act).apply {
            text = "Device Admin: ${if (SystemSecurityTools.isDeviceAdminActive(act)) "ACTIVE (removable below)" else "OFF"}\n" +
                "Privacy VPN: ${if (NovaVpnService.running) "ACTIVE" else "OFF"}\n" +
                "Screen Recorder: ${if (ScreenRecordService.recording) "RECORDING" else "IDLE"}"
            setTextColor(Color.parseColor("#B8CBE0")); textSize = 12.5f
        }
        box.addView(info)

        box.addView(btn(act, "🛡 Privacy Permission Dashboard (Consent)", accent) {
            SystemSecurityTools.requestConsent(act, "privacy_dash", "Privacy Permission Audit",
                "Run an on-device check of installed user apps with Camera, Mic, Location, and Contacts permissions. No data leaves your device.") {
                Thread {
                    val items = SystemSecurityTools.auditAppPermissions(act)
                    h.post {
                        dlg(act).setTitle("🛡 App Permission Audit (${items.size})")
                            .setMessage(if (items.isEmpty()) "No third-party apps with sensitive permissions." else items.joinToString("\n"))
                            .setPositiveButton("OK", null).show()
                    }
                }.start()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 8) })

        box.addView(btn(act, "🔑 Toggle / Remove Device Admin (#42)", accent) {
            if (SystemSecurityTools.isDeviceAdminActive(act)) {
                Toast.makeText(act, SystemSecurityTools.removeDeviceAdmin(act), Toast.LENGTH_LONG).show()
            } else {
                SystemSecurityTools.requestConsent(act, "device_admin", "Optional Device Admin",
                    "Allows optional screen-lock policies. You can deactivate & remove Device Admin at any time from this button.") {
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SystemSecurityTools.adminComponent(act))
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Optional NOVA security lock. Removable anytime.")
                    act.startActivity(i)
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        box.addView(btn(act, "🎥 Screen Recorder (Start / Stop)", accent) {
            if (ScreenRecordService.recording) {
                ScreenRecordService.stop(act)
                Toast.makeText(act, "Saved: ${ScreenRecordService.lastPath}", Toast.LENGTH_LONG).show()
            } else {
                val mpm = act.getSystemService(MediaProjectionManager::class.java)
                act.startActivityForResult(mpm.createScreenCaptureIntent(), reqScreenRecCode)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        box.addView(btn(act, "📞 Call Recording Consent Notice & Spam Blocklist", accent) {
            val prefs = act.getSharedPreferences("nova", Context.MODE_PRIVATE)
            val et = EditText(act).apply { hint = "Blocked numbers (one per line)"; setText(prefs.getString("spam_blocklist", "")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            dlg(act).setTitle("📞 Call Compliance & Spam Blocker")
                .setMessage("Call recording always requires compliance with local laws and participant consent. Add spam numbers below for local CallScreeningService blocking:")
                .setView(et)
                .setPositiveButton("Save & Consent") { _, _ ->
                    prefs.edit().putString("spam_blocklist", et.text.toString()).putBoolean("feat_spam_blocker", true).apply()
                    NovaRepo.logConsent(act, "call_rec", true, "Viewed legal notice & updated spam blocklist")
                }.setNegativeButton("Close", null).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        box.addView(btn(act, "🆘 Emergency SOS (User Triggered Only)", Color.parseColor("#FF4D5E")) {
            val prefs = act.getSharedPreferences("nova", Context.MODE_PRIVATE)
            val et = EditText(act).apply { hint = "Emergency contact phone number"; setText(prefs.getString("sos_contact", "")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            dlg(act).setTitle("🆘 Confirm Manual Emergency SOS").setMessage("Only send when YOU tap Send SOS Now:")
                .setView(et)
                .setPositiveButton("Send SOS Now") { _, _ ->
                    val num = et.text.toString().trim()
                    prefs.edit().putString("sos_contact", num).apply()
                    Toast.makeText(act, SystemSecurityTools.triggerSos(act, num), Toast.LENGTH_LONG).show()
                }.setNegativeButton("Cancel", null).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        box.addView(btn(act, "🐞 Bug Report & Consent Audit Log (#99)", accent) {
            val preview = ProductivityEngine.runSelfDiagnostics(act) + "\n\nRecent Consent Log:\n" +
                NovaRepo.getConsentLogs(act).take(5).joinToString("\n") { "${it.featureKey}: ${it.consented}" }
            dlg(act).setTitle("🐞 Preview Diagnostics Before Export").setMessage(preview)
                .setPositiveButton("Consent & Copy Report") { _, _ ->
                    NovaRepo.logConsent(act, "bug_report", true, "User previewed & copied diagnostics report")
                    ClipStore.putOnClipboard(act, preview)
                    Toast.makeText(act, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(act, 6) })

        dlg(act).setTitle("🛡 Security, Privacy & System").setView(ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
    }
}
