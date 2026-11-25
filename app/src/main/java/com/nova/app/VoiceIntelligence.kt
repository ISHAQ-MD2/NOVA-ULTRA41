package com.nova.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Build
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Voice & Audio Intelligence Engine:
 * - Offline conversation & voice commands (including "Nova, show source code")
 * - Consent-gated self-voice profile playback with audio watermark & audit log
 * - Noise suppression & echo cancellation during recording
 * - Real-time pitch/tone/speed/emotion/stress/anomaly analysis
 * - 3-step Voice Biometric enrollment, verification & live speaker gating
 * - Song recognition & musical analysis
 */
object VoiceIntelligence {
    data class AudioMetrics(
        val rmsDb: Double,
        val pitchHz: Double,
        val speechRateEst: Double,
        val moodLabel: String,
        val anomalyNote: String,
        val spectrumBands: DoubleArray
    )

    data class SongAnalysis(
        val songName: String,
        val artist: String,
        val album: String,
        val genre: String,
        val releaseDate: String,
        val details: String
    )

    private const val SAMPLE_RATE = 16000

    fun hasMicPerm(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Feature 2 + Part 3 Voice Command: Offline conversation & fast local command routing. */
    fun offlineReply(ctx: Context, rawText: String): String {
        val norm = I18n.normalizeUrduDialect(rawText).lowercase()
        val lang = I18n.detectLanguage(rawText)
        return when {
            ("source code" in norm || "view source" in norm || "page source" in norm) -> {
                NovaBrowser.init(ctx)
                val res = NovaBrowser.openViewSource()
                try {
                    ctx.startActivity(
                        Intent(ctx, BrowserActivity::class.java)
                            .putExtra("view_source", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                } catch (_: Exception) {}
                "Opening page source code in NOVA Browser. $res"
            }
            "time" in norm || "waqt" in norm || "بجے" in rawText ->
                "Abhi waqt ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())} hai."
            "namaz" in norm || "azan" in norm || "prayer" in norm -> Prayer.todayText()
            "battery" in norm -> Tools.execute(ctx, "get_battery", JSONObject())
            "salam" in norm || "hello" in norm || "hi" in norm ->
                if (lang.contains("Urdu")) "Walaikum Assalam! Main NOVA hoon (Offline Mode). Kaise madad karun?"
                else "Hello! NOVA is ready in Offline Mode. How can I assist?"
            "hijri" in norm || "islamic date" in norm -> SystemSecurityTools.hijriDateString()
            "joke" in norm -> "Ek developer ne fridge mein code rakha... taake bugs freeze ho jayein!"
            else -> {
                val mem = Memory.search(ctx, rawText)
                if (mem.isNotEmpty()) "Offline Memory se mila:\n" + mem.take(3).joinToString("\n")
                else "Offline Mode active hai. Aap time, namaz, battery, notes, reminders, view source ya phone controls chala sakte hain."
            }
        }
    }

    /** Feature 3 & 23: Consent-gated self-voice profile directory & audit watermark log. */
    fun hasVoiceConsent(ctx: Context): Boolean =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("voice_clone_consent", false)

    fun grantVoiceConsent(ctx: Context, ownerName: String) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_clone_consent", true)
            .putString("voice_clone_owner", ownerName.ifBlank { "Device Owner" })
            .apply()
        NovaRepo.logConsent(ctx, "voice_clone_and_change", true, "Self-voice verified consent granted by $ownerName")
    }

    fun revokeVoiceConsent(ctx: Context) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("voice_clone_consent", false).apply()
        NovaRepo.logConsent(ctx, "voice_clone_and_change", false, "Voice cloning/changing consent revoked")
    }

    /** Record clean audio with NoiseSuppressor & AcousticEchoCanceler (Feature 18). */
    fun recordWithNoiseSuppression(ctx: Context, outFile: File, durationMs: Long = 3500L): AudioMetrics? {
        if (!hasMicPerm(ctx)) return null
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
        var ns: NoiseSuppressor? = null
        var aec: AcousticEchoCanceler? = null
        val allSamples = ArrayList<Short>()
        return try {
            val useNs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("feat_noise_suppress", true)
            if (useNs && NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
            if (useNs && AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
            rec.startRecording()
            val buf = ShortArray(minBuf / 2)
            val end = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) for (i in 0 until n) allSamples.add(buf[i])
            }
            rec.stop()
            val arr = allSamples.toShortArray()
            writeWavWithWatermark(outFile, arr, SAMPLE_RATE, "NOVA-USER-CONSENTED-AUDIO")
            analyzePcm(ctx, arr, SAMPLE_RATE)
        } catch (_: Throwable) {
            null
        } finally {
            try { ns?.release() } catch (_: Throwable) {}
            try { aec?.release() } catch (_: Throwable) {}
            try { rec.release() } catch (_: Throwable) {}
        }
    }

    /** Embeds a readable RIFF INFO watermark chunk ('ICMT') inside the WAV file + audit entry. */
    private fun writeWavWithWatermark(file: File, pcm: ShortArray, sampleRate: Int, watermark: String) {
        val dataBytes = pcm.size * 2
        val wmBytes = watermark.toByteArray(Charsets.UTF_8)
        val wmPad = wmBytes.size + (wmBytes.size % 2)
        val listChunkSize = 4 + 8 + wmPad
        val totalSize = 36 + dataBytes + 8 + listChunkSize
        FileOutputStream(file).use { fos ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray())
            hdr.putInt(totalSize)
            hdr.put("WAVEfmt ".toByteArray())
            hdr.putInt(16)
            hdr.putShort(1)
            hdr.putShort(1)
            hdr.putInt(sampleRate)
            hdr.putInt(sampleRate * 2)
            hdr.putShort(2)
            hdr.putShort(16)
            hdr.put("data".toByteArray())
            hdr.putInt(dataBytes)
            fos.write(hdr.array())
            val dataBuf = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) dataBuf.putShort(s)
            fos.write(dataBuf.array())
            val meta = ByteBuffer.allocate(8 + listChunkSize).order(ByteOrder.LITTLE_ENDIAN)
            meta.put("LIST".toByteArray())
            meta.putInt(listChunkSize)
            meta.put("INFO".toByteArray())
            meta.put("ICMT".toByteArray())
            meta.putInt(wmBytes.size)
            meta.put(wmBytes)
            if (wmBytes.size % 2 == 1) meta.put(0.toByte())
            fos.write(meta.array())
        }
    }

    /** Features 13, 16, 21, 22: Analyze pitch, RMS energy, speaking speed, mood/stress & anomaly. */
    fun analyzePcm(ctx: Context, samples: ShortArray, sampleRate: Int = SAMPLE_RATE): AudioMetrics {
        if (samples.isEmpty()) return AudioMetrics(-60.0, 0.0, 0.0, "Silent", "None", DoubleArray(8))
        var sumSq = 0.0
        var zcr = 0
        var bursts = 0
        var inVoice = false
        val win = sampleRate / 20
        var winSum = 0.0
        var winCount = 0
        for (i in samples.indices) {
            val v = samples[i].toDouble()
            sumSq += v * v
            if (i > 0 && ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0))) zcr++
            winSum += abs(v)
            winCount++
            if (winCount >= win) {
                val avg = winSum / winCount
                if (avg > 900 && !inVoice) { bursts++; inVoice = true }
                else if (avg < 450) inVoice = false
                winSum = 0.0; winCount = 0
            }
        }
        val rms = sqrt(sumSq / samples.size)
        val db = if (rms > 1.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -60.0
        val durSec = samples.size.toDouble() / sampleRate
        val pitchHz = ((zcr / 2.0) / durSec).coerceIn(60.0, 480.0)
        val rate = (bursts / durSec.coerceAtLeast(0.5))
        val bands = computeBands(samples)
        val mood = when {
            db < -42.0 -> "Quiet / Calm"
            pitchHz > 235 && rate > 3.2 -> "Excited / High Stress"
            pitchHz > 205 -> "Energetic / Alert"
            rate < 1.3 -> "Calm / Thoughtful"
            else -> "Balanced / Normal"
        }
        val basePitch = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getFloat("voice_base_pitch", 0f)
        val anomaly = if (basePitch > 40f && abs(pitchHz - basePitch) > basePitch * 0.45) {
            "Pitch shift detected (${pitchHz.toInt()} Hz vs baseline ${basePitch.toInt()} Hz)"
        } else "Matches baseline profile"
        return AudioMetrics(db, pitchHz, rate, mood, anomaly, bands)
    }

    private fun computeBands(samples: ShortArray): DoubleArray {
        val bands = DoubleArray(8)
        val step = (samples.size / 8).coerceAtLeast(1)
        var maxVal = 1.0
        for (b in 0 until 8) {
            var e = 0.0
            val start = b * step
            val end = minOf(samples.size, start + step)
            for (i in start until end) e += abs(samples[i].toDouble())
            bands[b] = e / (end - start).coerceAtLeast(1)
            if (bands[b] > maxVal) maxVal = bands[b]
        }
        for (b in 0 until 8) bands[b] = bands[b] / maxVal
        return bands
    }

    // ---- Biometric Preferences & 3-Step Enrollment / Live Verification ----

    fun isBiometricEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("vb_enabled", false)

    fun setBiometricEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("vb_enabled", on).apply()
    }

    fun getStrictness(ctx: Context): String =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("vb_strictness", "balanced") ?: "balanced"

    fun setStrictness(ctx: Context, mode: String) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putString("vb_strictness", mode).apply()
    }

    fun strictnessThreshold(ctx: Context): Double = when (getStrictness(ctx).lowercase()) {
        "strict" -> 0.86
        "relaxed" -> 0.68
        else -> 0.78
    }

    fun getSampleCount(ctx: Context): Int =
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getInt("vb_sample_count", 0).coerceIn(0, 3)

    fun isVoiceEnrolled(ctx: Context): Boolean =
        (ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("voice_print_vec", "") ?: "").split(",").size == 8

    fun resetBiometrics(ctx: Context) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit()
            .putBoolean("vb_enabled", false)
            .putInt("vb_sample_count", 0)
            .remove("voice_print_vec")
            .remove("voice_base_pitch")
            .remove("vb_sample_0")
            .remove("vb_sample_1")
            .remove("vb_sample_2")
            .apply()
    }

    fun recordTrainingSample(ctx: Context, stepIndex: Int): Pair<Boolean, String> {
        val idx = stepIndex.coerceIn(0, 2)
        val f = File(ctx.filesDir, "voice_profiles/sample_${idx + 1}.wav").apply { parentFile?.mkdirs() }
        val m = recordWithNoiseSuppression(ctx, f, 2600L) ?: return false to "Microphone permission required"
        if (m.rmsDb < -46.0) return false to "Voice too quiet — speak 'Hey Nova, wake up' clearly"
        val prefs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("vb_sample_$idx", m.spectrumBands.joinToString(","))
            .putFloat("vb_pitch_$idx", m.pitchHz.toFloat())
            .apply()
        val vecs = (0..2).mapNotNull { i ->
            val raw = prefs.getString("vb_sample_$i", "") ?: ""
            val arr = raw.split(",").mapNotNull { it.toDoubleOrNull() }
            if (arr.size == 8) arr else null
        }
        val avgVec = DoubleArray(8)
        for (v in vecs) for (b in 0 until 8) avgVec[b] += v[b] / vecs.size
        val pitches = (0..2).map { prefs.getFloat("vb_pitch_$it", 0f) }.filter { it > 0f }
        val avgPitch = if (pitches.isNotEmpty()) pitches.average().toFloat() else m.pitchHz.toFloat()
        val count = maxOf(getSampleCount(ctx), idx + 1).coerceAtMost(3)
        prefs.edit()
            .putInt("vb_sample_count", count)
            .putFloat("voice_base_pitch", avgPitch)
            .putString("voice_print_vec", avgVec.joinToString(","))
            .apply()
        if (count >= 3) {
            setBiometricEnabled(ctx, true)
            NovaRepo.logConsent(ctx, "voice_biometric", true, "Completed 3-step voiceprint training (${avgPitch.toInt()} Hz)")
        }
        return true to "Sample ${idx + 1} captured (${m.pitchHz.toInt()} Hz)"
    }

    fun enrollVoicePrint(ctx: Context): String {
        val (ok, msg) = recordTrainingSample(ctx, 0)
        return if (ok) "Voiceprint updated: $msg" else msg
    }

    fun verifyVoicePrint(ctx: Context): Pair<Boolean, String> {
        val raw = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("voice_print_vec", "") ?: ""
        if (raw.isBlank()) return false to "No voiceprint enrolled yet — record Sample 1–3 first"
        val enrolled = raw.split(",").mapNotNull { it.toDoubleOrNull() }.toDoubleArray()
        if (enrolled.size != 8) return false to "Invalid voiceprint"
        val tmp = File(ctx.cacheDir, "verify_voice.wav")
        val m = recordWithNoiseSuppression(ctx, tmp, 2500L) ?: return false to "Mic read failed"
        if (m.rmsDb < -46.0) return false to "No speech detected — speak clearly into the mic"
        val sim = cosineSimilarity(enrolled, m.spectrumBands)
        val ok = sim >= strictnessThreshold(ctx)
        val pct = (sim * 100).toInt().coerceIn(0, 100)
        return ok to if (ok) "OWNER VERIFIED ($pct% match)" else "OTHER SPEAKER BLOCKED ($pct% match)"
    }

    /** Fast check on streaming PCM16 bytes from LiveClient mic buffer. */
    fun verifyPcmBuffer(ctx: Context, pcmBytes: ByteArray, len: Int): Pair<Boolean, Int> {
        val raw = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("voice_print_vec", "") ?: ""
        val enrolled = raw.split(",").mapNotNull { it.toDoubleOrNull() }.toDoubleArray()
        if (enrolled.size != 8 || len < 64) return true to 100
        val count = len / 2
        val shorts = ShortArray(count)
        var i = 0
        var s = 0
        while (s < count && i + 1 < len) {
            shorts[s] = (((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF))).toShort()
            s++; i += 2
        }
        val bands = computeBands(shorts)
        val sim = cosineSimilarity(enrolled, bands)
        val pct = (sim * 100).toInt().coerceIn(0, 100)
        return (sim >= strictnessThreshold(ctx)) to pct
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in 0 until minOf(a.size, b.size)) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (sqrt(na) * sqrt(nb)).coerceAtLeast(1e-6)
    }

    /** Feature 3, 15, 20, 23: Play consented self-voice recording with context pitch/speed modulation & watermark audit. */
    fun playModifiedVoice(ctx: Context, wavFile: File, contextPreset: String): String {
        if (!hasVoiceConsent(ctx)) return "Consent required: verify self-voice consent first."
        if (!wavFile.exists()) return "Record your voice sample first."
        val (pitch, speed) = when (contextPreset.lowercase()) {
            "deep", "formal" -> 0.84f to 0.96f
            "bright", "energetic" -> 1.18f to 1.08f
            "soft", "calm" -> 0.94f to 0.90f
            "robot", "persona" -> 0.72f to 1.02f
            else -> 1.0f to 1.0f
        }
        NovaRepo.logConsent(ctx, "voice_playback_modified", true, "Preset=$contextPreset pitch=$pitch speed=$speed file=${wavFile.name}")
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(wavFile.absolutePath)
            mp.prepare()
            if (Build.VERSION.SDK_INT >= 23) {
                mp.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(speed)
            }
            mp.setOnCompletionListener { it.release() }
            mp.start()
            "Playing watermarked voice ($contextPreset)"
        } catch (e: Exception) {
            "Playback error: ${e.message}"
        }
    }

    /** Feature 16 Extra: Analyze currently playing or mic-captured song and search song name + details. */
    fun analyzeAndIdentifySong(ctx: Context, queryHint: String = "", listenMic: Boolean = false): SongAnalysis {
        var detectedTitle = queryHint.trim()
        var audioNote = ""
        val currentTrack = MusicPlayer.current
        if (detectedTitle.isBlank() && currentTrack != null) {
            detectedTitle = currentTrack.title
            audioNote = "Matched active NOVA track (${currentTrack.durMs / 1000}s). "
        }
        if (listenMic && hasMicPerm(ctx)) {
            val snap = File(ctx.cacheDir, "song_snap.wav")
            val m = recordWithNoiseSuppression(ctx, snap, 3000L)
            if (m != null) {
                audioNote += "Captured audio: ${m.rmsDb.toInt()} dBFS, peak freq ~${m.pitchHz.toInt()} Hz, energy ${m.moodLabel}. "
                if (detectedTitle.isBlank() && Keys.active(ctx).isNotBlank() && snap.exists()) {
                    val aiGuess = identifyAudioWithGemini(ctx, snap)
                    if (aiGuess.isNotBlank()) detectedTitle = aiGuess
                }
            }
        }
        val cleanQuery = detectedTitle
            .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]|official|video|audio|mp3|4k|hd", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[_-]+"), " ")
            .trim()
        if (cleanQuery.isBlank()) {
            return SongAnalysis(
                songName = "No song hint captured",
                artist = "Unknown",
                album = "-",
                genre = "-",
                releaseDate = "-",
                details = "${audioNote}Play a song in NOVA Music Player or type/hum lyrics or song keywords to search."
            )
        }
        try {
            val url = "https://itunes.apple.com/search?term=" + Uri.encode(cleanQuery) + "&media=music&limit=1"
            val req = Request.Builder().url(url).build()
            Net.client(readTimeoutSec = 15).newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("results")
                    if (arr != null && arr.length() > 0) {
                        val r = arr.getJSONObject(0)
                        val track = r.optString("trackName", cleanQuery)
                        val artist = r.optString("artistName", "Unknown Artist")
                        val album = r.optString("collectionName", "Single")
                        val genre = r.optString("primaryGenreName", "Music")
                        val date = r.optString("releaseDate", "").take(10)
                        val durSec = r.optLong("trackTimeMillis", 0L) / 1000L
                        val extraAi = if (Keys.active(ctx).isNotBlank()) {
                            val prompt = "Give a short 3-line musical & mood analysis (tempo feel, genre vibe, meaning) for the song '$track' by '$artist'."
                            val g = GeminiRest.post(ctx, JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))))
                            if (g.ok) extractText(g.text) else ""
                        } else ""
                        return SongAnalysis(
                            songName = track,
                            artist = artist,
                            album = album,
                            genre = genre,
                            releaseDate = date.ifBlank { "-" },
                            details = "${audioNote}Duration: ${durSec / 60}m ${durSec % 60}s\n$extraAi".trim()
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return SongAnalysis(
            songName = cleanQuery,
            artist = "Local / Unindexed",
            album = "-",
            genre = "-",
            releaseDate = "-",
            details = "${audioNote}No online catalog match found; showing local track analysis."
        )
    }

    private fun identifyAudioWithGemini(ctx: Context, wavFile: File): String {
        return try {
            val b64 = android.util.Base64.encodeToString(wavFile.readBytes().take(512 * 1024).toByteArray(), android.util.Base64.NO_WRAP)
            val body = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray()
                    .put(JSONObject().put("text", "Listen to this audio clip. If you recognize the song name and artist, reply ONLY with 'Song Name - Artist'. If unclear, reply empty."))
                    .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "audio/wav").put("data", b64))))
            ))
            val res = GeminiRest.post(ctx, body)
            if (res.ok) extractText(res.text).lines().firstOrNull()?.take(80) ?: "" else ""
        } catch (_: Exception) { "" }
    }

    /** Feature 17 & 30: Summarize voice note / meeting transcript. */
    fun summarizeTranscript(ctx: Context, transcript: String): String {
        if (transcript.isBlank()) return "Empty transcript."
        if (Keys.active(ctx).isNotBlank() && Net.online(ctx)) {
            val prompt = "Summarize this voice note / meeting into: 1) Key Points, 2) Action Items, 3) Decisions. Keep it concise:\n\n${transcript.take(10000)}"
            val res = GeminiRest.post(ctx, JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))))
            if (res.ok) return extractText(res.text)
        }
        val sents = transcript.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.length > 15 }
        return if (sents.isEmpty()) transcript.take(280) else "Summary:\n• " + sents.take(4).joinToString("\n• ")
    }

    private fun extractText(raw: String): String = try {
        val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        buildString {
            if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "") ?: "")
        }.ifBlank { raw.take(600) }
    } catch (_: Exception) { raw.take(600) }
}
