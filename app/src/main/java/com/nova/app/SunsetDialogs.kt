package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.File

/**
 * Modular dialogs helper extracted from MainActivity to preserve all power tools
 * (Animation Studio, Wallpaper Widgets, Macros, Termux Terminal, Code Studio,
 * Decode/Encrypt Vault, Voice Studio, Memories, Chat History, Failed Tasks).
 */
object SunsetDialogs {
    private fun dp(ctx: Context, x: Int) = SunsetUiHelper.dp(ctx, x)
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun dlg(ctx: Context) = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun chip(ctx: Context, accent: Int, text: String, selected: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTextColor(if (selected) Color.BLACK else Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 18).toFloat()
            if (selected) setColor(accent) else {
                setColor(Color.parseColor("#1A1422"))
                setStroke(dp(ctx, 1), accent)
            }
        }
        setOnClickListener { onClick() }
    }

    private fun circleBitmap(src: Bitmap?, ring: Int, selected: Boolean): Bitmap {
        val s = 160
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (src != null) {
            val path = Path()
            path.addCircle(s / 2f, s / 2f, s / 2f - 6, Path.Direction.CW)
            c.save(); c.clipPath(path); c.drawBitmap(src, null, RectF(0f, 0f, s.toFloat(), s.toFloat()), p); c.restore()
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = if (selected) 8f else 4f; p.color = ring
        c.drawCircle(s / 2f, s / 2f, s / 2f - 5, p)
        return out
    }

    fun themeDialog(
        ctx: Activity,
        accent: Int,
        prefs: SharedPreferences,
        onApplyTheme: () -> Unit,
        onPickWall: () -> Unit,
        onSetStatic: (Int) -> Unit,
        onSetLive: () -> Unit
    ) {
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6)) }
        list.addView(TextView(ctx).apply { text = "Background Blur"; setTextColor(Color.WHITE); textSize = 14f; setPadding(dp(ctx, 6), dp(ctx, 6), 0, 0) })
        list.addView(SeekBar(ctx).apply {
            max = 100; progress = prefs.getInt("bgblur", 30)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, pr: Int, fromUser: Boolean) {
                    if (fromUser) prefs.edit().putInt("bgblur", pr).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }, lp(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        val homeLockSwitch = Switch(ctx).apply {
            text = "Apply to Home + Lock Screen"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            isChecked = WallpaperWidgets.applyHomeLockPref(ctx)
            minimumHeight = dp(ctx, 48)
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            setOnCheckedChangeListener { _, checked -> WallpaperWidgets.setHomeLockPref(ctx, checked) }
        }
        list.addView(homeLockSwitch, lp(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        val actionRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        actionRow.addView(chip(ctx, accent, "Apply Wallpaper Now", true) {
            onSetStatic(if (WallpaperWidgets.applyHomeLockPref(ctx)) 3 else 1)
        }.apply { minimumHeight = dp(ctx, 48); gravity = Gravity.CENTER }, lp(-2, dp(ctx, 48)).apply { rightMargin = dp(ctx, 8) })
        actionRow.addView(chip(ctx, accent, "Live Wallpaper", false) { onSetLive() }.apply { minimumHeight = dp(ctx, 48); gravity = Gravity.CENTER }, lp(-2, dp(ctx, 48)))
        list.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(actionRow) }, lp(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        val allThemes = Themes.all(ctx)
        val dialog = dlg(ctx).setTitle("Wallpaper Widgets (${allThemes.size})").setView(ScrollView(ctx).apply { addView(list) }).setNegativeButton("Close", null).create()
        val add = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
            background = GradientDrawable().apply { cornerRadius = dp(ctx, 22).toFloat(); setColor(Color.parseColor("#14101C")); setStroke(dp(ctx, 2), accent) }
            setOnClickListener { dialog.dismiss(); onPickWall() }
        }
        add.addView(IconView(ctx).apply { icon = "plus"; tint = accent }, lp(dp(ctx, 28), dp(ctx, 28)))
        add.addView(TextView(ctx).apply { text = "Add Custom Background from Gallery"; setTextColor(Color.WHITE); textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(ctx, 14), 0, 0, 0) }, lp(0, -2, 1f))
        list.addView(add, lp(-1, -2).apply { topMargin = dp(ctx, 4); bottomMargin = dp(ctx, 10) })

        val cur = Themes.current(ctx).id
        for (th in allThemes) {
            val sel = th.id == cur
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                background = GradientDrawable().apply { cornerRadius = dp(ctx, 22).toFloat(); setColor(Color.parseColor("#14101C")); setStroke(dp(ctx, if (sel) 2 else 1), if (sel) th.accent else Color.parseColor("#33FFFFFF")) }
                setOnClickListener {
                    Themes.set(ctx, th.id); onApplyTheme()
                    onSetStatic(if (WallpaperWidgets.applyHomeLockPref(ctx)) 3 else 1)
                    dialog.dismiss()
                }
                if (th.custom) setOnLongClickListener {
                    dlg(ctx).setMessage("Delete '${th.name}'?").setPositiveButton("Delete") { _, _ -> Themes.deleteCustom(ctx, th.id); onApplyTheme(); dialog.dismiss() }.setNegativeButton("No", null).show(); true
                }
            }
            val iv = ImageView(ctx).apply {
                setImageBitmap(circleBitmap(Themes.thumb(ctx, th), th.accent, sel))
                contentDescription = "${th.name} wallpaper widget preview"
            }
            val tx = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 14), 0, 0, 0) }
            tx.addView(TextView(ctx).apply { text = th.name; setTextColor(Color.WHITE); textSize = 15.5f; typeface = Typeface.DEFAULT_BOLD })
            tx.addView(TextView(ctx).apply { text = "${th.category} · " + if (sel) "Selected ✓" else if (th.custom) "Tap = apply · Long-press = delete" else "Tap to preview & apply"; setTextColor(if (sel) th.accent else Color.parseColor("#8C9AB0")); textSize = 12f })
            row.addView(iv, lp(dp(ctx, 58), dp(ctx, 58))); row.addView(tx, lp(0, -2, 1f))
            list.addView(row, lp(-1, -2).apply { bottomMargin = dp(ctx, 8) })
        }
        dialog.show()
    }

    fun memoriesDialog(ctx: Activity, h: Handler, onStatus: (String) -> Unit) {
        val notes = Memory.notes(ctx).map { it.substringAfter("|").trim() }
        val msg = if (notes.isEmpty()) "Abhi koi yaad ki hui baat nahi.\n\n'Sikhao' dabao, ya NOVA se bolo:\n'yaad rakho ki mujhe coding pasand hai'"
        else notes.takeLast(40).reversed().joinToString("\n\n") { "• $it" }
        dlg(ctx).setTitle("🧠 Memories").setMessage(msg)
            .setPositiveButton("Sikhao") { _, _ -> teachDialog(ctx, onStatus) }
            .setNeutralButton("Chat saaf") { _, _ -> Thread { val n = Memory.pruneChat(ctx.applicationContext, 0); h.post { onStatus("$n chat lines saaf (yaad ki baatein mehfooz)") } }.start() }
            .setNegativeButton("Close", null).show()
    }

    private fun teachDialog(ctx: Activity, onStatus: (String) -> Unit) {
        val et = EditText(ctx).apply { hint = "Jo yaad rakhwana hai likho"; minLines = 2; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val box = LinearLayout(ctx).apply { setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), 0); addView(et, lp(-1, -2)) }
        dlg(ctx).setTitle("NOVA ko sikhao").setView(box).setPositiveButton("Yaad rakho") { _, _ ->
            val t = et.text.toString().trim()
            if (t.isNotEmpty()) { Memory.note(ctx, t); onStatus("Yaad kar liya: $t") }
        }.setNegativeButton("Cancel", null).show()
    }

    fun animStudio(ctx: Activity, accent: Int, prefs: SharedPreferences, onStatus: (String) -> Unit) {
        val studio = android.app.Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0A0710")) }
        val prev = CharacterView(ctx).apply { wallpaperMode = true; setTheme(Themes.current(ctx)); applyPrefs() }
        root.addView(prev, lp(-1, (ctx.resources.displayMetrics.heightPixels * 0.38f).toInt()))
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 24)) }
        root.addView(ScrollView(ctx).apply { addView(body) }, lp(-1, 0, 1f))
        fun refresh() { prev.applyPrefs() }
        fun head(t: String) = body.addView(TextView(ctx).apply { text = t; setTextColor(accent); textSize = 13f; letterSpacing = 0.2f; typeface = Typeface.MONOSPACE; setPadding(0, dp(ctx, 14), 0, dp(ctx, 4)) })
        fun slider(label: String, start: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
            val tv = TextView(ctx).apply { text = "$label: $start"; setTextColor(Color.WHITE); textSize = 13f }
            body.addView(tv)
            body.addView(SeekBar(ctx).apply {
                this.max = max - min; progress = start - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, pr: Int, fromUser: Boolean) { if (fromUser) { tv.text = "$label: ${pr + min}"; onChange(pr + min) } }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }
        fun choice(title: String, opts: List<String>, cur: Int, onPick: (Int) -> Unit) {
            body.addView(TextView(ctx).apply { text = title; setTextColor(Color.WHITE); textSize = 13f; setPadding(0, dp(ctx, 6), 0, dp(ctx, 2)) })
            val row = LinearLayout(ctx)
            val chips = ArrayList<TextView>()
            fun paintAll(sel: Int) { chips.forEachIndexed { i, c -> c.setTextColor(if (i == sel) Color.BLACK else Color.WHITE); c.background = GradientDrawable().apply { cornerRadius = dp(ctx, 18).toFloat(); if (i == sel) setColor(accent) else { setColor(Color.parseColor("#1A1422")); setStroke(dp(ctx, 1), accent) } } } }
            opts.forEachIndexed { i, o -> val c = chip(ctx, accent, o, i == cur) { paintAll(i); onPick(i); refresh() }; chips.add(c); row.addView(c, lp(-2, -2).apply { rightMargin = dp(ctx, 6) }) }
            body.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }

        head("PRESETS")
        val presetRow = LinearLayout(ctx)
        presetRow.addView(chip(ctx, accent, "Theme default", Anim.current(ctx) == null) { Anim.setCurrent(ctx, null); studio.dismiss(); animStudio(ctx, accent, prefs, onStatus) }, lp(-2, -2).apply { rightMargin = dp(ctx, 6) })
        for ((n, l) in Anim.BUILTIN + Anim.userPresets(ctx)) presetRow.addView(chip(ctx, accent, n, false) { Anim.setCurrent(ctx, l); studio.dismiss(); animStudio(ctx, accent, prefs, onStatus) }, lp(-2, -2).apply { rightMargin = dp(ctx, 6) })
        body.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(presetRow) })

        val base = Anim.current(ctx) ?: Anim.BUILTIN.values.first()
        val ls = ArrayList<Layer>(); for (i in 0 until 3) ls.add(base.getOrNull(i) ?: Layer(0, 0, 100, 100, 100, 0))
        fun commit() { Anim.setCurrent(ctx, ls.filter { it.count > 0 }); refresh() }
        for (i in 0 until 3) {
            head("LAYER ${i + 1}")
            choice("Type", Anim.KINDS, ls[i].kind) { k -> ls[i] = Layer(k, maxOf(ls[i].count, 12), ls[i].speed, ls[i].size, ls[i].alpha, ls[i].color); commit() }
            slider("Count (0 = band)", ls[i].count, 0, 90) { v -> ls[i] = Layer(ls[i].kind, v, ls[i].speed, ls[i].size, ls[i].alpha, ls[i].color); commit() }
            slider("Speed %", ls[i].speed, 20, 250) { v -> ls[i] = Layer(ls[i].kind, ls[i].count, v, ls[i].size, ls[i].alpha, ls[i].color); commit() }
            slider("Size %", ls[i].size, 40, 250) { v -> ls[i] = Layer(ls[i].kind, ls[i].count, ls[i].speed, v, ls[i].alpha, ls[i].color); commit() }
            slider("Alpha %", ls[i].alpha, 20, 100) { v -> ls[i] = Layer(ls[i].kind, ls[i].count, ls[i].speed, ls[i].size, v, ls[i].color); commit() }
        }

        head("DEPTH CLOCK")
        choice("Style", listOf("Off", "Stacked", "Outline", "Wide"), prefs.getInt("clock_style", 1)) { prefs.edit().putInt("clock_style", it).apply() }
        choice("Live wallpaper rings", listOf("ON", "OFF"), if (prefs.getBoolean("wp_hud", true)) 0 else 1) { prefs.edit().putBoolean("wp_hud", it == 0).apply() }

        val btn = LinearLayout(ctx)
        btn.addView(chip(ctx, accent, "💾 Save Preset", false) {
            val et = EditText(ctx).apply { hint = "Preset ka naam"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            dlg(ctx).setTitle("Preset save").setView(LinearLayout(ctx).apply { setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0); addView(et, lp(-1, -2)) })
                .setPositiveButton("Save") { _, _ -> val n = et.text.toString().trim(); if (n.isNotEmpty()) { Anim.savePreset(ctx, n, ls.filter { it.count > 0 }); onStatus("Preset '$n' save") } }.setNegativeButton("Cancel", null).show()
        }, lp(-2, -2).apply { rightMargin = dp(ctx, 8) })
        btn.addView(chip(ctx, accent, "✅ Close", true) { studio.dismiss() }, lp(-2, -2))
        body.addView(btn)
        studio.setContentView(root); studio.show()
    }

    fun macrosDialog(ctx: Activity, onStatus: (String) -> Unit) {
        val ms = Macros.all(ctx)
        val items = ArrayList<String>(); ms.forEach { items.add("▶ ${it.name} (${it.triggers.joinToString(", ")})") }
        items.add("➕ Naya macro banao"); items.add("❓ Commands help")
        dlg(ctx).setTitle("🧩 Macros").setItems(items.toTypedArray()) { _, i ->
            when { i < ms.size -> macroEdit(ctx, ms[i], onStatus); i == ms.size -> macroEdit(ctx, null, onStatus); else -> commandsHelp(ctx) }
        }.setNegativeButton("Close", null).show()
    }

    private fun macroEdit(ctx: Activity, m: Macros.M?, onStatus: (String) -> Unit) {
        val name = EditText(ctx).apply { hint = "Macro naam"; setText(m?.name ?: ""); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val trig = EditText(ctx).apply { hint = "Triggers (comma separated)"; setText(m?.triggers?.joinToString(", ") ?: ""); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val steps = EditText(ctx).apply { hint = "open_app WhatsApp\nwait 2\nspeak Salam"; setText(m?.steps?.joinToString("\n") ?: ""); minLines = 6; gravity = Gravity.TOP; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); typeface = Typeface.MONOSPACE }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), 0); addView(name); addView(trig); addView(steps) }
        dlg(ctx).setTitle(if (m == null) "Naya macro" else "Macro edit").setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) { Macros.save(ctx, m?.name, n, trig.text.toString(), steps.text.toString()); onStatus("Macro '$n' saved") }
            }
            .setNeutralButton("Test") { _, _ ->
                val n = name.text.toString().trim()
                Macros.save(ctx, m?.name, n, trig.text.toString(), steps.text.toString())
                Thread { val r = Macros.run(ctx.applicationContext, n); ctx.runOnUiThread { onStatus(r) } }.start()
            }.setNegativeButton("Cancel", null).show()
    }

    fun commandsHelp(ctx: Activity) {
        val txt = "OFFLINE/ONLINE VOICE COMMANDS:\n" +
            "• 'Nova, show source code' -> Opens WebView page source\n" +
            "• 'torch on/off', 'volume kam/zyada', 'time kya hai', 'battery'\n" +
            "• 'X ko call karo', 'X ko message karo: ...', 'X ko voice message karo'\n" +
            "• '10 minute baad yaad dilao ...', 'kal 5 baje calendar mein meeting'\n" +
            "• 'youtube par ... search karo', 'WhatsApp kholo'\n\n" + Macros.help(ctx)
        val tv = TextView(ctx).apply { text = txt; setTextColor(Color.parseColor("#E8EEF7")); textSize = 12f; typeface = Typeface.MONOSPACE; setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10)); setTextIsSelectable(true) }
        dlg(ctx).setTitle("❓ Commands & Macro help").setView(ScrollView(ctx).apply { addView(tv) }).setPositiveButton("OK", null).show()
    }

    fun failedTasksDialog(ctx: Activity, onStatus: (String) -> Unit) {
        val fl = FailedTasks.all(ctx).reversed()
        val items = ArrayList<String>()
        if (PendingTasks.count(ctx) > 0) items.add("⏳ ${PendingTasks.count(ctx)} kaam intezaar mein")
        fl.forEach { items.add("❌ " + it.optString("text").take(60) + "\n    " + it.optString("reason").take(70)) }
        if (items.isEmpty()) { dlg(ctx).setTitle("Failed tasks").setMessage("Koi failed task nahi 👍").setPositiveButton("OK", null).show(); return }
        val offset = if (PendingTasks.count(ctx) > 0) 1 else 0
        dlg(ctx).setTitle("❌ Failed tasks").setItems(items.toTypedArray()) { _, i ->
            if (offset == 1 && i == 0) { PendingTasks.runNext(ctx); onStatus("Ruka kaam shuru"); return@setItems }
            val t = fl[i - offset]
            dlg(ctx).setMessage(t.optString("text") + "\n\n" + t.optString("reason"))
                .setPositiveButton("Retry") { _, _ -> FailedTasks.retry(ctx, t.optInt("id")); onStatus("Retrying...") }
                .setNegativeButton("Delete") { _, _ -> FailedTasks.remove(ctx, t.optInt("id")) }
                .setNeutralButton("Close", null).show()
        }.setNegativeButton("Close", null).show()
    }

    fun videoUrlDialog(ctx: Activity) {
        val et = EditText(ctx).apply { hint = "Enter MP4, WebM, or .m3u8 URL"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine() }
        val box = LinearLayout(ctx).apply { setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), 0); addView(et, lp(-1, -2)) }
        dlg(ctx).setTitle("Custom Video Player").setView(box).setPositiveButton("Play") { _, _ ->
            val u = et.text.toString().trim()
            if (u.isNotBlank()) ctx.startActivity(Intent(ctx, VideoPlayerActivity::class.java).putExtra("url", u))
        }.setNegativeButton("Cancel", null).show()
    }

    fun terminalDialog(ctx: Activity, accent: Int) {
        val d = android.app.Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(ctx, 8), dp(ctx, 24), dp(ctx, 8), dp(ctx, 8)) }
        val log = TextView(ctx).apply { setTextColor(Color.parseColor("#7CFF9B")); typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true); text = "NOVA Terminal (Termux)\n" }
        val sv = ScrollView(ctx).apply { addView(log) }
        root.addView(sv, lp(-1, 0, 1f))
        val chips = LinearLayout(ctx)
        val defaults = listOf("pkg update -y", "python3 --version", "ls", "pwd", "df -h")
        val input = EditText(ctx).apply { hint = "command, e.g. pkg update -y"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(); typeface = Typeface.MONOSPACE }
        fun refreshChips(prefix: String = "") {
            chips.removeAllViews()
            val hist = (NovaRepo.getTermuxHistory(ctx, prefix) + defaults).distinct().take(12)
            for (c in hist) chips.addView(chip(ctx, accent, c, false) { input.setText(c); input.setSelection(c.length) }, lp(-2, -2).apply { rightMargin = dp(ctx, 6) })
        }
        refreshChips()
        fun runCmd(cmd: String, visible: Boolean) {
            if (cmd.isBlank()) return
            NovaRepo.recordTermuxCmd(ctx, cmd)
            refreshChips()
            log.append("\n\$ $cmd\n⏳ running...\n")
            Thread { val r = try { TermuxBridge.run(ctx.applicationContext, cmd, visible) } catch (e: Throwable) { "error: ${e.message}" }; ctx.runOnUiThread { log.append("$r\n"); sv.fullScroll(View.FOCUS_DOWN) } }.start()
        }
        root.addView(HorizontalScrollView(ctx).apply { addView(chips) }, lp(-1, -2))
        root.addView(input, lp(-1, -2))
        val row = LinearLayout(ctx)
        row.addView(chip(ctx, accent, "▶ Run", true) { runCmd(input.text.toString().trim(), false); input.setText("") }, lp(-2, -2).apply { rightMargin = dp(ctx, 6) })
        row.addView(chip(ctx, accent, "🪟 Termux", false) { runCmd(input.text.toString().trim(), true) }, lp(-2, -2).apply { rightMargin = dp(ctx, 6) })
        row.addView(chip(ctx, accent, "✖ Close", false) { d.dismiss() }, lp(-2, -2))
        root.addView(HorizontalScrollView(ctx).apply { addView(row) }, lp(-1, -2))
        d.setContentView(root); d.show()
    }

    fun codeStudioDialog(ctx: Activity, h: Handler, onStatus: (String) -> Unit) {
        val et = EditText(ctx).apply { hint = "What should NOVA Studio build?"; minLines = 3; gravity = Gravity.TOP; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val who = EditText(ctx).apply { hint = "WhatsApp recipient (optional)"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), 0); addView(et); addView(who) }
        dlg(ctx).setTitle("💻 NOVA Studio").setView(box)
            .setPositiveButton("Build + Run") { _, _ ->
                val task = et.text.toString().trim(); val to = who.text.toString().trim()
                if (task.isEmpty()) return@setPositiveButton
                onStatus("NOVA Studio started...")
                Thread {
                    val o = try { CodeStudio.auto(ctx.applicationContext, task) { p -> h.post { onStatus(p) } } } catch (e: Throwable) { CodeStudio.Out(false, "", "", "error: ${e.message}", "") }
                    var sent = ""
                    if (to.isNotEmpty() && o.ok) sent = Tools.execute(ctx.applicationContext, "whatsapp_send", JSONObject().put("who", to).put("message", if (o.link.isNotEmpty()) o.link else o.log.take(500)))
                    h.post {
                        onStatus(if (o.ok) "Build succeeded ✅" else "Build failed ❌")
                        val tv = TextView(ctx).apply {
                            text = (if (o.link.isNotEmpty()) "LINK:\n${o.link}\n\n" else "") + (if (sent.isNotEmpty()) "Sent: $sent\n\n" else "") + "OUTPUT:\n${o.log.take(3000)}\n\nCODE:\n${o.code.take(5000)}"
                            setTextColor(Color.parseColor("#E8EEF7")); textSize = 12f; typeface = Typeface.MONOSPACE; setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10)); setTextIsSelectable(true)
                        }
                        val resDlg = dlg(ctx).setTitle(if (o.ok) "✅ Code Ready" else "❌ Error").setView(ScrollView(ctx).apply { addView(tv) }).setNegativeButton("Close", null)
                        if (o.link.isNotEmpty()) resDlg.setPositiveButton("Open") { _, _ -> try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(o.link))) } catch (_: Exception) {} }
                        resDlg.setNeutralButton("Copy") { _, _ -> ClipStore.putOnClipboard(ctx, o.code); onStatus("Code copied") }.show()
                    }
                }.start()
            }.setNegativeButton("Close", null).show()
    }

    fun vaultMenu(ctx: Activity, onStatus: (String) -> Unit, pickFile: (String) -> Unit) {
        val items = arrayOf("📋 Paste & Decode", "📁 Decode File / Zip", "🔒 Encrypt File", "🔓 Decrypt File (.nova)")
        dlg(ctx).setTitle("🔐 Decode / Encrypt").setItems(items) { _, i ->
            if (i == 0) {
                val et = EditText(ctx).apply { hint = "Paste encoded text"; minLines = 6; gravity = Gravity.TOP; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); typeface = Typeface.MONOSPACE; textSize = 11f }
                dlg(ctx).setTitle("📋 Code decode").setView(ScrollView(ctx).apply { addView(et) }).setPositiveButton("Decode") { _, _ ->
                    Thread {
                        val r = try { Decoder.unwrap(et.text.toString()) } catch (e: Throwable) { Decoder.Result("", emptyList(), "error: ${e.message}") }
                        ctx.runOnUiThread {
                            val tv = TextView(ctx).apply { text = r.text.take(15000); setTextColor(Color.WHITE); typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10)) }
                            dlg(ctx).setTitle("Decoded").setView(ScrollView(ctx).apply { addView(tv) }).setPositiveButton("Copy") { _, _ -> ClipStore.putOnClipboard(ctx, r.text); onStatus("Copied") }.setNegativeButton("Close", null).show()
                        }
                    }.start()
                }.setNegativeButton("Cancel", null).show()
            } else {
                pickFile(arrayOf("", "decode", "enc", "dec")[i])
            }
        }.setNegativeButton("Close", null).show()
    }

    fun handlePickedFile(ctx: Activity, uri: Uri, fileMode: String, h: Handler, onStatus: (String) -> Unit) {
        val name = try { ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null } } catch (_: Exception) { null } ?: "file"
        if (fileMode == "decode") {
            onStatus("Decoding...")
            Thread {
                val msg = try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val (outName, data, note) = Decoder.processFile(name, bytes)
                    if (data.isNotEmpty()) { val f = File(Backup.sub("decoded") ?: ctx.filesDir, outName); f.writeBytes(data); "$note Saved: ${f.path}" } else note
                } catch (e: Throwable) { "Failed: ${e.message}" }
                h.post { onStatus(msg); dlg(ctx).setTitle("Decode Result").setMessage(msg).setPositiveButton("OK", null).show() }
            }.start(); return
        }
        val et = EditText(ctx).apply { hint = "Password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        dlg(ctx).setTitle(if (fileMode == "enc") "🔒 Encrypt: $name" else "🔓 Decrypt: $name").setView(LinearLayout(ctx).apply { setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0); addView(et, lp(-1, -2)) })
            .setPositiveButton("OK") { _, _ ->
                val pw = et.text.toString()
                if (pw.length < 4) { onStatus("Password min 4 chars"); return@setPositiveButton }
                Thread {
                    val msg = try {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        val dir = Backup.sub("vault") ?: ctx.filesDir
                        if (fileMode == "enc") { val f = File(dir, "$name.nova"); f.writeBytes(Vault.encrypt(bytes, pw)); "Encrypted: ${f.path}" }
                        else { val out = Vault.decrypt(bytes, pw); if (out == null) "Wrong password" else { val f = File(dir, name.removeSuffix(".nova").ifEmpty { "decrypted" }); f.writeBytes(out); "Decrypted: ${f.path}" } }
                    } catch (e: Throwable) { "Error: ${e.message}" }
                    h.post { onStatus(msg); dlg(ctx).setTitle("Vault").setMessage(msg).setPositiveButton("OK", null).show() }
                }.start()
            }.setNegativeButton("Cancel", null).show()
    }

    fun chatHistoryDialog(ctx: Activity, h: Handler, onStatus: (String) -> Unit) {
        val lines = ChatLog.last(ctx, 300)
        val tv = TextView(ctx).apply {
            text = if (lines.isEmpty()) "No chat history yet." else lines.joinToString("\n\n")
            setTextColor(Color.parseColor("#E8EEF7")); textSize = 12f; typeface = Typeface.MONOSPACE; setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10)); setTextIsSelectable(true)
        }
        dlg(ctx).setTitle("💬 Chat History (${ChatLog.count(ctx)})").setView(ScrollView(ctx).apply { addView(tv) })
            .setPositiveButton("Export") { _, _ -> Thread { val r = Backup.backupToDownloads(ctx.applicationContext); h.post { onStatus(r) } }.start() }
            .setNegativeButton("Close", null).show()
    }

    fun azanDialog(ctx: Activity) {
        dlg(ctx).setTitle("🕌 Namaz / Azan Times").setMessage(Prayer.todayText()).setPositiveButton("OK", null).show()
    }

    fun clipDialog(ctx: Activity, onStatus: (String) -> Unit) {
        ClipStore.saveCurrent(ctx)
        val l = ClipStore.all(ctx).takeLast(30).reversed()
        if (l.isEmpty()) { dlg(ctx).setTitle("📋 NOVA Clipboard").setMessage("Empty.").setPositiveButton("OK", null).show(); return }
        dlg(ctx).setTitle("📋 NOVA Clipboard (tap = copy)").setItems(l.map { ClipStore.describe(it) }.toTypedArray()) { _, i ->
            ClipStore.putOnClipboard(ctx, l[i].optString("text")); onStatus("Copied to clipboard")
        }.setNeutralButton("Clear") { _, _ -> File(ctx.filesDir, "clips.json").delete(); Backup.replace("clips.json", "[]") }.show()
    }
}
