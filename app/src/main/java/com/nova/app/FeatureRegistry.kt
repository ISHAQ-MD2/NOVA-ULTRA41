package com.nova.app

import android.content.Context

data class FeatureModule(
    val id: Int,
    val key: String,
    val title: String,
    val category: String,
    val desc: String,
    val defaultOn: Boolean = true,
    val requiresConsent: Boolean = false
)

/**
 * Feature 100: Master Feature Registry with per-module toggles and consent metadata.
 * Note: Network packet sniffing (#7) and vulnerability scanning (#8) are intentionally omitted.
 */
object FeatureRegistry {
    val MODULES: List<FeatureModule> = listOf(
        // Core & Voice
        FeatureModule(1, "feat_clipboard", "Direct Clipboard & Link Paste", "Voice & AI", "Smart clipboard capture and instant link paste"),
        FeatureModule(2, "feat_offline_voice", "Offline Voice & Chat", "Voice & AI", "Offline TTS playback & local intent replies"),
        FeatureModule(3, "consent_voice_clone_and_change", "Self-Voice Cloning (Consent)", "Voice & AI", "Own-voice calibration with watermark & audit log", false, true),
        FeatureModule(4, "feat_calendar_email", "Calendar & Email Integration", "Productivity", "Read events and draft calendar/email actions"),
        FeatureModule(5, "feat_urdu_dialect", "Urdu / Punjabi / Saraiki NLP", "Voice & AI", "Regional dialect normalization + RTL UI"),
        FeatureModule(6, "feat_termux_hist", "Termux History & Autocomplete", "System & Tools", "Command history chips and auto-suggestions"),
        FeatureModule(9, "feat_termux_opt", "Termux Optimization Helper", "System & Tools", "Safe cache & environment checks in Termux"),
        FeatureModule(10, "feat_proj_mgr", "Coding Project Manager", "Productivity", "Organize AI code projects & GitHub export"),
        FeatureModule(11, "feat_code_deploy", "Code Gen & Deploy Helper", "Productivity", "Generate code and open hosting dashboards"),
        FeatureModule(12, "feat_quran", "Quran & Shan-e-Nuzool", "Islamic & Life", "Verse lookup with Urdu/English & Shan-e-Nuzool"),
        FeatureModule(13, "feat_voice_emotion", "Voice Mood Analysis (Opt-in)", "Voice & AI", "Local pitch/energy mood indicators", false),
        FeatureModule(14, "feat_voice_bio", "Voice Biometric Auth", "Security & Privacy", "Voiceprint verification with PIN fallback", false),
        FeatureModule(15, "feat_call_accent", "Voice Tone Presets", "Voice & AI", "Adjustable speech pitch/rate presets", false),
        FeatureModule(16, "feat_song_analysis", "Song Search & Audio Analysis", "Voice & AI", "Identify playing song, artist & musical details"),
        FeatureModule(17, "feat_voice_sum", "Voice Note Summarizer", "Voice & AI", "Summarize transcripts into key points"),
        FeatureModule(18, "feat_noise_suppress", "Noise Suppression (AudioFX)", "Voice & AI", "Hardware NoiseSuppressor & EchoCanceler"),
        FeatureModule(19, "feat_multi_voice", "Multilingual Voice Recognition", "Voice & AI", "English, Urdu & Hindi voice support"),
        FeatureModule(20, "feat_persona_voice", "Dynamic Voice Personas", "Voice & AI", "Switchable assistant voice profiles"),
        FeatureModule(21, "feat_voice_anomaly", "Voice Anomaly Detection", "Voice & AI", "Compare pitch shifts against baseline", false),
        FeatureModule(22, "feat_voice_speed", "Speech Speed Analysis", "Voice & AI", "Syllable rate & cadence metrics"),
        FeatureModule(23, "consent_voice_changer", "Context Voice Changer (Consent)", "Voice & AI", "Self-voice context presets with audit log", false, true),
        FeatureModule(24, "feat_lang_detect", "Auto Language Detection", "Voice & AI", "Detect Urdu/Hindi/English & reply in kind"),
        FeatureModule(25, "feat_ai_tutor", "Personalized AI Tutor", "Productivity", "Lessons and interactive quizzes"),

        // Productivity & System
        FeatureModule(26, "feat_ctx_memory", "Contextual Chat Memory", "Voice & AI", "Long-term notes & multi-turn recall"),
        FeatureModule(27, "feat_shortcuts", "Voice-to-Action Shortcuts", "Productivity", "Work, Gaming & Sleep automation modes"),
        FeatureModule(28, "feat_morning_news", "Morning AI News Presenter", "Productivity", "Top 5 daily headlines as spoken brief"),
        FeatureModule(29, "feat_chat_translate", "Chat Voice Translation", "Voice & AI", "Translate shared messages & voice notes"),
        FeatureModule(30, "feat_meeting_tx", "Meeting Transcription & Notes", "Productivity", "Structured meeting summary & actions"),
        FeatureModule(31, "feat_smart_sum", "Smart Summary to Clipboard", "Productivity", "Summarize URLs/docs and copy"),
        FeatureModule(32, "feat_acc_assist", "Account Autofill (Prompted)", "Productivity", "Encrypted autofill with confirmation"),
        FeatureModule(33, "feat_browser_back", "Browser Back -> Home Flow", "System & Tools", "Previous page first, then NOVA Home"),
        FeatureModule(34, "feat_video_click", "Browser Video Tap Quick-Sheet", "System & Tools", "Instant play/download sheet on video tap"),
        FeatureModule(35, "nb_adblock", "Non-Breaking AdBlock", "Security & Privacy", "Block tracker/ad hosts with site allowlist"),
        FeatureModule(36, "feat_browser_vpn", "Browser Privacy VPN Shield", "Security & Privacy", "Local DNS/tracker VPN tunnel for NOVA"),
        FeatureModule(37, "feat_bio_unlock", "Biometric / Face Unlock", "Security & Privacy", "BiometricPrompt with PIN fallback", false),
        FeatureModule(38, "feat_diary", "Encrypted Diary", "Security & Privacy", "AES-GCM encrypted personal diary entries"),
        FeatureModule(39, "feat_enc_backup", "Password-Encrypted Backups", "Security & Privacy", "Encrypt backup JSON with .nova vault"),
        FeatureModule(40, "feat_cloud_backup", "Cloud Backup Connectors", "Productivity", "Google Drive & Dropbox export shortcuts"),
        FeatureModule(41, "consent_anti_theft", "Anti-Theft Alert (Opt-in)", "Security & Privacy", "Optional motion/wrong-PIN alert + location", false, true),
        FeatureModule(42, "consent_device_admin", "Device Admin Lock (Removable)", "Security & Privacy", "Optional lock policy, removable anytime", false, true),
        FeatureModule(43, "feat_loc_tasks", "Location Task Automation", "Productivity", "Trigger reminders near saved coordinates", false),
        FeatureModule(44, "feat_smart_home", "Smart-Home Webhooks", "Productivity", "Trigger local smart-home HTTP endpoints"),
        FeatureModule(45, "feat_gaming_mode", "Gaming Mode", "System & Tools", "Alarms-only DND & fast HUD mode", false),
        FeatureModule(46, "feat_news_agg", "Personalized News Feed", "Productivity", "Topic-filtered headlines from Google News"),
        FeatureModule(47, "feat_portfolio", "Crypto & Stock Watchlist", "Productivity", "Live prices & custom threshold alerts"),
        FeatureModule(48, "feat_trading_sig", "Trading Signals (Info Only)", "Productivity", "EMA/RSI analysis, no auto-trading"),
        FeatureModule(49, "feat_health", "Health & Digital Wellbeing", "Islamic & Life", "Step counter & daily screen-time report"),
        FeatureModule(50, "feat_diag", "Autonomous Problem Solver", "System & Tools", "Crash, storage & network diagnostics"),

        // Extra Features (51 - 100)
        FeatureModule(51, "feat_lock_wp", "Lock-Screen-Only Wallpaper", "System & Tools", "Apply wallpaper specifically to Lock Screen"),
        FeatureModule(52, "feat_charging_anim", "Charging Reverse Countdown", "System & Tools", "Show charging ETA animation on plug-in", false),
        FeatureModule(53, "feat_charging_sync", "1%-Per-Cycle Charge Sync", "System & Tools", "Smooth 1% incremental charge sync"),
        FeatureModule(54, "feat_wp_variety", "32+ Live Wallpaper Themes", "System & Tools", "Cyber, gradient, starlight & matrix styles"),
        FeatureModule(55, "nb_night", "Per-App / Browser Dark Mode", "System & Tools", "Dark rendering toggle for web & UI"),
        FeatureModule(56, "feat_qs_tile", "Quick Settings Tile", "System & Tools", "Launch NOVA from Quick Settings shade"),
        FeatureModule(57, "bubble", "Floating Assistant Bubble", "System & Tools", "Overlay quick-access assistant bubble"),
        FeatureModule(58, "feat_notes", "Notes with Voice Input", "Productivity", "Save and search local SQLite notes"),
        FeatureModule(59, "feat_reminders", "Voice-Alert Reminders", "Productivity", "Scheduled exact-alarm voice reminders"),
        FeatureModule(60, "feat_pass_mgr", "Encrypted Password Vault", "Security & Privacy", "AES-256-GCM credential storage"),
        FeatureModule(61, "feat_qr", "QR Generator & Pairing", "System & Tools", "Generate QR matrices & share payloads"),
        FeatureModule(62, "feat_file_mgr", "Built-in File Manager", "System & Tools", "Browse NOVA-FILES & Downloads"),
        FeatureModule(63, "feat_battery_health", "Battery Health Monitor", "System & Tools", "Voltage, temp, health & ETA telemetry"),
        FeatureModule(64, "feat_cleaner", "Storage Cleaner (Preview)", "System & Tools", "Cache & duplicate finder with preview"),
        FeatureModule(65, "feat_usage_stats", "App Usage Statistics", "System & Tools", "24h foreground usage breakdown"),
        FeatureModule(66, "feat_pomodoro", "Pomodoro Focus Timer", "Productivity", "25m focus / 5m break productivity timer"),
        FeatureModule(67, "feat_expenses", "Expense Tracker", "Productivity", "Track spending with voice/text entry"),
        FeatureModule(68, "feat_converter", "Unit & Currency Converter", "Productivity", "Length, weight, temp & live FX rates"),
        FeatureModule(69, "feat_dictionary", "Offline EN/UR/HI Dictionary", "Productivity", "Trilingual offline lexicon lookup"),
        FeatureModule(70, "feat_cam_translate", "Camera OCR & Translation", "Voice & AI", "Translate captured photos via Gemini Vision"),
        FeatureModule(71, "feat_doc_scan", "Document Text Extractor", "Productivity", "Extract & summarize text from documents"),
        FeatureModule(72, "feat_shot_edit", "Screenshot Annotator", "System & Tools", "Draw/crop annotations on images"),
        FeatureModule(73, "feat_screen_rec", "Screen Recorder (Consent)", "System & Tools", "MediaProjection screen capture"),
        FeatureModule(74, "feat_voice_music", "Voice Music Control", "Voice & AI", "Play, pause, next, playlists by voice"),
        FeatureModule(75, "feat_audiobook", "Audiobook & Podcast Player", "Productivity", "Variable-speed local audio playback"),
        FeatureModule(76, "feat_qibla", "Prayer Times & Qibla Compass", "Islamic & Life", "Live sensor bearing toward Makkah"),
        FeatureModule(77, "feat_hijri", "Hijri Calendar", "Islamic & Life", "Islamic lunar date converter"),
        FeatureModule(78, "feat_dua", "Daily Dua & Zikr", "Islamic & Life", "Curated daily remembrance & reminders"),
        FeatureModule(79, "feat_weather", "Weather Alerts", "Islamic & Life", "Live Open-Meteo conditions & refresh"),
        FeatureModule(80, "feat_sleep_dnd", "Sleep & DND Automation", "Islamic & Life", "Quiet hours Do Not Disturb mode"),
        FeatureModule(81, "feat_templates", "Quick Reply Templates", "Productivity", "One-tap WhatsApp & SMS replies"),
        FeatureModule(82, "feat_auto_reply", "Driving / Meeting Auto-Reply", "Productivity", "DND & template responder mode", false),
        FeatureModule(83, "consent_call_rec", "Call Recording Notice (Consent)", "Security & Privacy", "Legal consent notice & visible toggle", false, true),
        FeatureModule(84, "feat_spam_blocker", "Caller ID & Spam Blocker", "Security & Privacy", "Local CallScreeningService blocklist", false),
        FeatureModule(85, "feat_clip_hist", "Clipboard History Manager", "Productivity", "Search & re-copy saved clips"),
        FeatureModule(86, "feat_snippets", "Text Snippets & Expander", "Productivity", "Expand shortcuts like /sig or /addr"),
        FeatureModule(87, "feat_urdu_kb", "Urdu Phonetic Helper", "Voice & AI", "Phonetic Roman-to-Urdu & RTL typing"),
        FeatureModule(88, "feat_offline_stt", "Offline Voice Typing", "Voice & AI", "Device speech recognizer fallback"),
        FeatureModule(89, "acc_large_text", "Accessibility: Large Text", "System & Tools", "Scale UI fonts + TalkBack descriptions", false),
        FeatureModule(90, "feat_reader_mode", "Web Reading Mode", "Productivity", "Clean distraction-free article reader"),
        FeatureModule(91, "feat_reading_list", "Bookmarks & Reading List", "Productivity", "Offline article storage & sync"),
        FeatureModule(92, "feat_drive_dropbox", "Drive / Dropbox Backup Sync", "Productivity", "Open & export backups to cloud apps"),
        FeatureModule(93, "feat_qr_pair", "Multi-Device QR Pairing", "System & Tools", "Pair companion devices via QR token"),
        FeatureModule(94, "feat_passkey", "Passkey / Credential Helper", "Security & Privacy", "Manage secure web login credentials"),
        FeatureModule(95, "consent_privacy_dash", "Privacy Permission Dashboard", "Security & Privacy", "Audit app permissions with consent", false, true),
        FeatureModule(96, "feat_app_lock", "App Lock (PIN / Biometric)", "Security & Privacy", "Require PIN or biometric to open Hub", false),
        FeatureModule(97, "feat_hidden_vault", "Encrypted Hidden File Vault", "Security & Privacy", "AES-encrypted private files (.nova)"),
        FeatureModule(98, "feat_sos", "Emergency SOS (User Triggered)", "Security & Privacy", "Manual SOS SMS + GPS location link"),
        FeatureModule(99, "consent_bug_report", "Bug Report & Logs (Consent)", "System & Tools", "Preview & export diagnostics with consent", false, true),
        FeatureModule(100, "feat_master_toggles", "All-Module Feature Toggles", "System & Tools", "Searchable control for every module")
    )

    fun isEnabled(ctx: Context, mod: FeatureModule): Boolean =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean(mod.key, mod.defaultOn)

    fun setEnabled(ctx: Context, mod: FeatureModule, on: Boolean) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean(mod.key, on).apply()
    }
}
