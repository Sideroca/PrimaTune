package com.sideroca.voicetuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONArray
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * AI 闹钟前台服务：
 *  - `pregen`：**提前数小时**跑 —— LLM 写词 → 逐条 TTS（**存 WAV**）→ 落到 `filesDir/alarm/`；
 *  - `ring`  ：到点**只播放**（按序、音量渐强）；没有缓存 → 播**系统默认闹钟铃声**（兜底）；
 *  - `snooze`/`dismiss`：通知按钮。
 * 失败/无网一律兜底，**绝不静默不响**。
 */
class AlarmService : Service() {

    companion object {
        const val EXTRA = "alarm_action"
        private const val CH = "vt_alarm"
        private const val NOTI_PREGEN = 7301
        private const val NOTI_RING = 7302
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var files: List<File> = emptyList()
    private var idx = 0
    private var vol = 0f
    private var wake: PowerManager.WakeLock? = null
    private val ramp = object : Runnable {
        override fun run() {
            vol = min(1f, vol + 0.05f)
            runCatching { player?.setVolume(vol, vol) }
            if (vol < 1f) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA) ?: "ring"
        when (action) {
            "pregen" -> {
                startFg(NOTI_PREGEN, noti(getString(R.string.alarm_title), getString(R.string.alarm_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                Thread { pregen(); stopSelf() }.start()
            }
            "ring" -> {
                startFg(NOTI_RING, ringNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                startPlayback()
            }
            "snooze" -> { stopPlayback(); AlarmScheduler.snooze(this, 5); stopSelf() }
            "dismiss" -> { stopPlayback(); afterRing(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- 前台服务
    private fun ch() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "闹钟", NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(true); enableVibration(true)
                }
            )
        }
    }

    private fun startFg(id: Int, n: Notification, type: Int) {
        ch()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, id, n, type)
            else startForeground(id, n)
        }
    }

    private fun noti(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setShowWhen(false).build()

    private fun ringNotification(): Notification {
        val snooze = PendingIntent.getService(this, 7401,
            Intent(this, AlarmService::class.java).putExtra(EXTRA, "snooze"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getService(this, 7402,
            Intent(this, AlarmService::class.java).putExtra(EXTRA, "dismiss"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ 闹钟")
            .setContentText("AI 正在对你说早安")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(0, "贪睡 5 分钟", snooze)
            .addAction(0, "停止", dismiss)
            .build()
    }

    // ---------------------------------------------------------------- 预生成
    private fun pregen() {
        val store = Store(this)
        val cfg = AlarmCfg.from(store.alarmJson)
        val dir = store.alarmDir
        dir.listFiles()?.forEach { it.delete() }
        if (store.llmKey.isBlank() || cfg.voiceId.isBlank()) return      // 缺 Key/音色 → 留空 → 响铃时兜底
        val msgs = writeMessages(store, cfg) ?: return
        var i = 0
        for (m in msgs) {
            i++
            val out = File(dir, String.format("%02d.wav", i))
            if (!synthToFile(store, cfg, m, out)) return
        }
    }

    /** 让 LLM 写 N 条（每条 ≥ minChars 字）；不达标**重试一次** */
    private fun writeMessages(store: Store, cfg: AlarmCfg): List<String>? {
        repeat(2) { attempt ->
            val sys = "你是闹钟语音撰稿人。"
            val user = buildString {
                append("音色/角色：").append(cfg.voiceName.ifBlank { "未指定" }).append('\n')
                val role = store.rolePrompt(cfg.voiceId)
                if (role.isNotBlank()) append("角色提示词：").append(role).append('\n')
                if (cfg.prompt.isNotBlank()) append("要求：").append(cfg.prompt).append('\n')
                append("请写 ").append(cfg.count).append(" 条**互不相同**的闹钟问候/叮嘱，每条**不少于 ")
                    .append(cfg.minChars).append(" 字**。")
                if (attempt > 0) append("（上一次太短了，这次务必满足字数）")
                append("只输出 JSON 字符串数组，例如 [\"第一条\",\"第二条\"]，不要解释。")
            }
            val latch = CountDownLatch(1)
            var out: String? = null
            LlmClient.ask(
                store.llmBaseUrl, store.llmKey, store.llmModel, sys, user,
                store.llmMaxTokens, store.llmTemp / 10.0, store.llmLevel
            ) { res, _ -> out = res; latch.countDown() }
            latch.await(90, TimeUnit.SECONDS)
            val list = parseMessages(out, cfg)
            if (list.isNotEmpty() && list.all { charCount(it) >= cfg.minChars }) return list
        }
        return null
    }

    private fun parseMessages(raw: String?, cfg: AlarmCfg): List<String> {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return emptyList()
        val list = ArrayList<String>()
        val start = s.indexOf('['); val end = s.lastIndexOf(']')
        if (start >= 0 && end > start) {
            runCatching {
                val arr = JSONArray(s.substring(start, end + 1))
                for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { list.add(it) }
            }
        }
        if (list.isEmpty()) {
            s.split('\n').map { it.trim().trim('"', '-', ' ', '\u3000').replace(Regex("^\\d+[.、]\\s*"), "") }
                .filter { it.isNotBlank() }.forEach { list.add(it) }
        }
        return list.take(cfg.count.coerceIn(1, 9))
    }

    /** 把一条文本合成为 WAV 落到文件（阻塞等待回调） */
    private fun synthToFile(store: Store, cfg: AlarmCfg, text: String, out: File): Boolean {
        val latch = CountDownLatch(1); var ok = false
        val req = SynthRequest(
            apiKey = "", workspace = store.workspace, model = store.lastModel, voice = cfg.voiceId,
            text = text, instruction = null, rate = 1.0, pitch = 1.0, volume = 50, seed = 0,
            format = "wav", sampleRate = 24000, bitRate = null, languageHints = null,
            hotFixJson = null, extraJson = null, ssml = false
        )
        val cb = object : SynthCallback {
            override fun onConnected() {}
            override fun onStarted() {}
            override fun onProgress(receivedBytes: Int) {}
            override fun onFinished(audio: ByteArray) {
                runCatching {
                    val isWav = audio.size > 12 && audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte()
                    val f = if (isWav) out else File(out.parentFile, out.nameWithoutExtension + ".mp3")
                    f.writeBytes(audio); ok = true
                }
                latch.countDown()
            }
            override fun onError(message: String) { latch.countDown() }
        }
        runCatching { Synth.dispatch(this, store, req, null, "wav", DashScopeClient(), cb) }
        latch.await(120, TimeUnit.SECONDS)
        return ok && File(out.parentFile, out.nameWithoutExtension).let { base ->
            File(base.path + ".wav").exists() || File(base.path + ".mp3").exists()
        }
    }

    // ---------------------------------------------------------------- 播放
    private fun startPlayback() {
        acquireWake()
        val store = Store(this)
        files = store.alarmDir.listFiles()?.filter { it.length() > 0 }?.sortedBy { it.name } ?: emptyList()
        vol = 0f
        if (files.isEmpty()) playFallback() else { idx = 0; playCurrent() }
        handler.postDelayed(ramp, 1000)
    }

    private fun playCurrent() {
        runCatching {
            player?.release()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(files[idx].absolutePath)
            mp.isLooping = false
            mp.setVolume(vol, vol)
            mp.setOnCompletionListener { idx = (idx + 1) % files.size; playCurrent() }
            mp.setOnErrorListener { _, _, _ -> playFallback(); true }
            mp.prepare(); mp.start()
            player = mp
        }.onFailure { playFallback() }
    }

    private fun playFallback() {
        // 闹钟声 → 铃声 → 通知声，逐个试（有些设备没有默认"闹钟"音）
        for (t in intArrayOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)) {
            val uri = RingtoneManager.getDefaultUri(t) ?: continue
            val ok = runCatching {
                player?.release()
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setVolume(vol, vol)
                mp.prepare(); mp.start()
                player = mp
            }.isSuccess
            if (ok) return
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(ramp)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        wake = null
    }

    /** 响完之后的收尾：一次性→关闭；每天/工作日→排下一次（含下一次预生成） */
    private fun afterRing() {
        val store = Store(this)
        val cfg = AlarmCfg.from(store.alarmJson)
        if (cfg.repeat == "once") store.alarmJson = cfg.copy(enabled = false).toJson()
        AlarmScheduler.apply(this)
    }

    private fun acquireWake() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vt:alarm").apply { acquire(30 * 60 * 1000L) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}
