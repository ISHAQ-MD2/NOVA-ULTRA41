package com.nova.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite persistence layer (Clean Repository + MVVM state store) for NOVA modules:
 * notes/diary, expenses, clipboard history, snippets, reading list, portfolio alerts,
 * health telemetry, Termux command history, and explicit user consent audit logs.
 */
class NovaDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "nova_core.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, is_diary INTEGER, is_encrypted INTEGER, tags TEXT, created_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, currency TEXT, category TEXT, note TEXT, created_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_hist(id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT, note TEXT, pinned INTEGER, created_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS snippets(id INTEGER PRIMARY KEY AUTOINCREMENT, shortcut TEXT UNIQUE, expansion TEXT, created_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_list(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT UNIQUE, folder TEXT, summary TEXT, created_at INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS portfolio(id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT, asset_type TEXT, qty REAL, buy_price REAL, alert_above REAL, alert_below REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS health_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, date_key TEXT UNIQUE, steps INTEGER, active_min INTEGER, screen_min INTEGER, mood TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS consent_audit(id INTEGER PRIMARY KEY AUTOINCREMENT, feature_key TEXT, consented INTEGER, details TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS termux_history(id INTEGER PRIMARY KEY AUTOINCREMENT, cmd TEXT UNIQUE, uses INTEGER, last_used INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = onCreate(db)
}

data class NoteEntry(val id: Long, val title: String, val body: String, val isDiary: Boolean, val isEncrypted: Boolean, val tags: String, val createdAt: Long)
data class ExpenseEntry(val id: Long, val amount: Double, val currency: String, val category: String, val note: String, val createdAt: Long)
data class SnippetEntry(val id: Long, val shortcut: String, val expansion: String)
data class ReadingItem(val id: Long, val title: String, val url: String, val folder: String, val summary: String, val createdAt: Long)
data class PortfolioAsset(val id: Long, val symbol: String, val assetType: String, val qty: Double, val buyPrice: Double, val alertAbove: Double, val alertBelow: Double)
data class ConsentRecord(val id: Long, val featureKey: String, val consented: Boolean, val details: String, val ts: Long)

object NovaRepo {
    @Volatile private var dbHelper: NovaDb? = null
    private fun db(ctx: Context): SQLiteDatabase {
        val h = dbHelper ?: synchronized(this) {
            dbHelper ?: NovaDb(ctx).also { dbHelper = it }
        }
        return h.writableDatabase
    }

    // ---- Notes & Encrypted Diary ----
    fun addNote(ctx: Context, title: String, body: String, isDiary: Boolean = false, encrypt: Boolean = false, tags: String = ""): Long {
        val storedBody = if (encrypt) Crypto.enc(body) else body
        val cv = ContentValues().apply {
            put("title", title.ifBlank { if (isDiary) "Diary Entry" else "Note" })
            put("body", storedBody)
            put("is_diary", if (isDiary) 1 else 0)
            put("is_encrypted", if (encrypt) 1 else 0)
            put("tags", tags)
            put("created_at", System.currentTimeMillis())
        }
        return db(ctx).insert("notes", null, cv)
    }

    fun getNotes(ctx: Context, diaryOnly: Boolean = false): List<NoteEntry> {
        val out = ArrayList<NoteEntry>()
        val sel = if (diaryOnly) "is_diary=1" else "is_diary=0"
        db(ctx).rawQuery("SELECT id,title,body,is_diary,is_encrypted,tags,created_at FROM notes WHERE $sel ORDER BY created_at DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) {
                val enc = c.getInt(4) == 1
                val rawBody = c.getString(2) ?: ""
                val body = if (enc) Crypto.dec(rawBody).ifEmpty { "[Encrypted]" } else rawBody
                out.add(NoteEntry(c.getLong(0), c.getString(1) ?: "", body, c.getInt(3) == 1, enc, c.getString(5) ?: "", c.getLong(6)))
            }
        }
        return out
    }

    fun deleteNote(ctx: Context, id: Long) { db(ctx).delete("notes", "id=?", arrayOf(id.toString())) }

    // ---- Expenses ----
    fun addExpense(ctx: Context, amount: Double, currency: String, category: String, note: String): Long {
        val cv = ContentValues().apply {
            put("amount", amount); put("currency", currency); put("category", category); put("note", note); put("created_at", System.currentTimeMillis())
        }
        return db(ctx).insert("expenses", null, cv)
    }

    fun getExpenses(ctx: Context): List<ExpenseEntry> {
        val out = ArrayList<ExpenseEntry>()
        db(ctx).rawQuery("SELECT id,amount,currency,category,note,created_at FROM expenses ORDER BY created_at DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) out.add(ExpenseEntry(c.getLong(0), c.getDouble(1), c.getString(2) ?: "PKR", c.getString(3) ?: "General", c.getString(4) ?: "", c.getLong(5)))
        }
        return out
    }

    fun deleteExpense(ctx: Context, id: Long) { db(ctx).delete("expenses", "id=?", arrayOf(id.toString())) }

    // ---- Text Snippets ----
    fun saveSnippet(ctx: Context, shortcut: String, expansion: String) {
        val cv = ContentValues().apply {
            put("shortcut", shortcut.trim()); put("expansion", expansion); put("created_at", System.currentTimeMillis())
        }
        db(ctx).insertWithOnConflict("snippets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSnippets(ctx: Context): List<SnippetEntry> {
        val out = ArrayList<SnippetEntry>()
        db(ctx).rawQuery("SELECT id,shortcut,expansion FROM snippets ORDER BY shortcut ASC", null).use { c ->
            while (c.moveToNext()) out.add(SnippetEntry(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: ""))
        }
        return out
    }

    fun expandText(ctx: Context, input: String): String {
        var out = input
        for (s in getSnippets(ctx)) {
            if (s.shortcut.isNotBlank() && out.contains(s.shortcut)) out = out.replace(s.shortcut, s.expansion)
        }
        return out
    }

    // ---- Reading List ----
    fun saveReadingItem(ctx: Context, title: String, url: String, folder: String = "General", summary: String = "") {
        val cv = ContentValues().apply {
            put("title", title.ifBlank { url }); put("url", url); put("folder", folder); put("summary", summary); put("created_at", System.currentTimeMillis())
        }
        db(ctx).insertWithOnConflict("reading_list", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getReadingList(ctx: Context): List<ReadingItem> {
        val out = ArrayList<ReadingItem>()
        db(ctx).rawQuery("SELECT id,title,url,folder,summary,created_at FROM reading_list ORDER BY created_at DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) out.add(ReadingItem(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "General", c.getString(4) ?: "", c.getLong(5)))
        }
        return out
    }

    // ---- Portfolio & Price Alerts ----
    fun addPortfolio(ctx: Context, symbol: String, type: String, qty: Double, buyPrice: Double, above: Double, below: Double) {
        val cv = ContentValues().apply {
            put("symbol", symbol.uppercase().trim()); put("asset_type", type); put("qty", qty); put("buy_price", buyPrice); put("alert_above", above); put("alert_below", below)
        }
        db(ctx).insert("portfolio", null, cv)
    }

    fun getPortfolio(ctx: Context): List<PortfolioAsset> {
        val out = ArrayList<PortfolioAsset>()
        db(ctx).rawQuery("SELECT id,symbol,asset_type,qty,buy_price,alert_above,alert_below FROM portfolio ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out.add(PortfolioAsset(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "CRYPTO", c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6)))
        }
        return out
    }

    fun deletePortfolio(ctx: Context, id: Long) { db(ctx).delete("portfolio", "id=?", arrayOf(id.toString())) }

    // ---- Health Logs ----
    fun upsertHealth(ctx: Context, dateKey: String, steps: Int, activeMin: Int, screenMin: Int, mood: String) {
        val cv = ContentValues().apply {
            put("date_key", dateKey); put("steps", steps); put("active_min", activeMin); put("screen_min", screenMin); put("mood", mood)
        }
        db(ctx).insertWithOnConflict("health_logs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHealthLogs(ctx: Context): List<Map<String, String>> {
        val out = ArrayList<Map<String, String>>()
        db(ctx).rawQuery("SELECT date_key,steps,active_min,screen_min,mood FROM health_logs ORDER BY date_key DESC LIMIT 14", null).use { c ->
            while (c.moveToNext()) {
                out.add(mapOf("date" to (c.getString(0) ?: ""), "steps" to c.getInt(1).toString(), "active" to c.getInt(2).toString(), "screen" to c.getInt(3).toString(), "mood" to (c.getString(4) ?: "")))
            }
        }
        return out
    }

    // ---- Consent Audit Log (Features 3, 23, 41, 42, 83, 95, 99) ----
    fun logConsent(ctx: Context, featureKey: String, consented: Boolean, details: String) {
        val cv = ContentValues().apply {
            put("feature_key", featureKey); put("consented", if (consented) 1 else 0); put("details", details); put("ts", System.currentTimeMillis())
        }
        db(ctx).insert("consent_audit", null, cv)
        try { Backup.appendLine("consent_audit.log", "${java.util.Date()} [$featureKey] consented=$consented | $details") } catch (_: Exception) {}
    }

    fun getConsentLogs(ctx: Context): List<ConsentRecord> {
        val out = ArrayList<ConsentRecord>()
        db(ctx).rawQuery("SELECT id,feature_key,consented,details,ts FROM consent_audit ORDER BY ts DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) out.add(ConsentRecord(c.getLong(0), c.getString(1) ?: "", c.getInt(2) == 1, c.getString(3) ?: "", c.getLong(4)))
        }
        return out
    }

    // ---- Termux Command History (Feature 6) ----
    fun recordTermuxCmd(ctx: Context, cmd: String) {
        val clean = cmd.trim()
        if (clean.isEmpty()) return
        db(ctx).execSQL(
            "INSERT INTO termux_history(cmd,uses,last_used) VALUES(?,1,?) ON CONFLICT(cmd) DO UPDATE SET uses=uses+1,last_used=excluded.last_used",
            arrayOf(clean, System.currentTimeMillis())
        )
    }

    fun getTermuxHistory(ctx: Context, prefix: String = ""): List<String> {
        val out = ArrayList<String>()
        val q = if (prefix.isBlank()) {
            db(ctx).rawQuery("SELECT cmd FROM termux_history ORDER BY last_used DESC LIMIT 40", null)
        } else {
            db(ctx).rawQuery("SELECT cmd FROM termux_history WHERE cmd LIKE ? ORDER BY uses DESC, last_used DESC LIMIT 20", arrayOf("%$prefix%"))
        }
        q.use { c -> while (c.moveToNext()) out.add(c.getString(0) ?: "") }
        return out
    }
}
