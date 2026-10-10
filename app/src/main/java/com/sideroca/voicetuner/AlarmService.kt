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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AI 闹钟前台服务：
 *  - `pregen`：**提前数小时**跑 —— LLM 写词 → 逐条 TTS（**存 WAV**）→ 落到 `filesDir/alarm/`；
 *  - `ring`  ：到点播放（按序、音量恒定）。**绝不静默**：
 *      ① 有预生成 → 直接播 AI 人声；
 *      ② 没预生成 → **立刻**响系统铃声，同时现场生成，**30 秒内**生成好就切成 AI 人声；
 *  - `snooze` / `dismiss`：通知按钮 / 响铃页按钮。
 *
 * 「不打开 App 就不响」的加固（用户 2026-10-08）：
 *  - 调度端（`AlarmScheduler`）**两条闹钟都用 `setAlarmClock`** → 都拿到 Doze 放行 + "可启动前台服务"豁免；
 *  - 接收器（`AlarmReceiver`）**先把通知 / 全屏响铃页挂出去**，再起服务；**服务起不来 → 接收器里直接播**；
 *  - 响铃页 `AlarmRingActivity` 走**全屏通知**（`setFullScreenIntent`），锁屏也能一眼看到。
 */
class AlarmService : Service() {

    companion object {
        const val EXTRA = "alarm_action"
        private const val CH = "vt_alarm"
        private const val NOTI_PREGEN = 7301
        const val NOTI_RING = 7302

        /** 生成窗口：无预生成时，现场生成这么久还没好就保持系统铃声 */
        private const val LIVE_GEN_LIMIT_MS = 30_000L

        /**
         * 是否已有一次预生成在跑。**跨服务实例互斥**——否则预生成（保存时那次）与响铃时那次会
         * 互相删档（用户 2026-10-10 报的"已准备 1 段却被删空"就是这个）。
         */
        @Volatile var generating = false

        /**
         * 是否**正在响铃**（服务播放 或 接收器直播）。
         * 用来做两件事：① 避免重复响（两个播放器叠在一起）；② 让"打开 App 就停止"成为可能
         * （用户 2026-10-10 报的"开了 App 也停不掉、划掉任务栏还在响"）。
         */
        @Volatile var ringing = false

        /**
         * **只停，不动排期**（"dismiss" 会做响完收尾、可能关掉一次性闹钟；这个不会）。
         * 主页 onResume / 响铃页「停止」/ 外部调用都走它。
         */
        fun stopIfRinging(ctx: Context): Boolean {
            if (!ringing && directPlayer == null) return false
            return runCatching {
                ctx.startService(Intent(ctx, AlarmService::class.java).putExtra(EXTRA, "stop"))
                stopDirect()
                (ctx.getSystemService(NotificationManager::class.java))?.cancel(NOTI_RING)
                true
            }.getOrDefault(false)
        }

        /** 接收器「直接播」用的静态播放器 + 唤醒锁（后台不给起前台服务时的最后一道兜底）。 */
        @Volatile private var directPlayer: MediaPlayer? = null
        @Volatile private var directWake: PowerManager.WakeLock? = null

        /** 抢一把短唤醒锁（30 分钟）：到点后 CPU 别睡回去。 */
        fun wakeUp(ctx: Context) {
            runCatching {
                if (directWake == null) {
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                    directWake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vt:alarm")
                        .apply { acquire(30 * 60 * 1000L) }
                }
            }
        }

        fun releaseWake() {
            runCatching { directWake?.let { if (it.isHeld) it.release() } }
            directWake = null
        }

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, ctx.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true); enableVibration(true)
                    }
                )
            }
        }

        /** 诊断日志（**静态**版：接收器/调度器也能写，用于判断"广播到底有没有送达"）。 */
        fun logStatus(ctx: Context, msg: String) {
            runCatching {
                val f = File(Store(ctx).alarmDir, "status.txt")
                if (f.length() > 20000) f.delete()
                f.appendText(SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()) + "  " + msg + "\n")
            }
        }

        /** 响铃时的"谁在说话"文案：**「音色名 向你问好」**（用户 2026-10-08：不要"AI 正在用…"那套）。 */
        fun ringWho(ctx: Context): String {
            val cfg = AlarmCfg.from(Store(ctx).alarmJson)
            val who = cfg.voiceName.ifBlank { ctx.getString(R.string.voice_custom) }
            return ctx.getString(R.string.alarm_ring_who, who)
        }

        /** 响铃通知：高优先级 + **全屏意图**（锁屏也弹响铃页）+ 贪睡 / 停止。 */
        fun buildRingNoti(ctx: Context, text: String): Notification {
            ensureChannel(ctx)
            val full = PendingIntent.getActivity(
                ctx, 7400, Intent(ctx, AlarmRingActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val snooze = PendingIntent.getService(
                ctx, 7401, Intent(ctx, AlarmService::class.java).putExtra(EXTRA, "snooze"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dismiss = PendingIntent.getService(
                ctx, 7402, Intent(ctx, AlarmService::class.java).putExtra(EXTRA, "dismiss"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(ctx.getString(R.string.alarm_ring_title))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .addAction(0, ctx.getString(R.string.alarm_ring_snooze), snooze)
                .addAction(0, ctx.getString(R.string.alarm_ring_stop), dismiss)
                .build()
        }

        /**
         * 后台**不给**起前台服务时的兜底：**接收器里直接播**系统铃声（闹钟声→铃声→通知声）。
         * 配合唤醒锁，短时间内进程不会被回收；响铃页 / 通知仍由接收器负责挂出。
         */
        fun playFallbackDirect(ctx: Context) {
            if (directPlayer != null) return
            wakeUp(ctx)
            ringing = true
            for (t in intArrayOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)) {
                val uri = RingtoneManager.getDefaultUri(t) ?: continue
                val ok = runCatching {
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    mp.setDataSource(ctx, uri)
                    mp.isLooping = true
                    mp.setVolume(1f, 1f)
                    mp.prepare(); mp.start()
                    directPlayer = mp
                }.isSuccess
                if (ok) break
            }
            // 兜底自停：最多响 5 分钟（万一没人接手停止，也**不会永远响下去**——
            // 用户 2026-10-10 报的"开 App、划掉任务栏都停不掉"就是这么来的）
            Handler(Looper.getMainLooper()).postDelayed({ stopDirect() }, 5 * 60_000L)
        }

        fun stopDirect() {
            runCatching { directPlayer?.stop() }
            runCatching { directPlayer?.release() }
            directPlayer = null
            ringing = false
            releaseWake()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var files: List<File> = emptyList()
    private var idx = 0
    private var vol = 0f
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA) ?: "ring"
        // 手动"测试响铃"（直接起服务那条）：响完**不要**做收尾（否则会误关掉真实的一次性闹钟）
        val isManualTest = intent?.getBooleanExtra("alarm_test", false) == true
        when (action) {
            "pregen" -> {
                startFg(NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                Thread {
                    if (!generate()) runCatching {
                        (getSystemService(NotificationManager::class.java))?.notify(
                            NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_voice_fail)))
                    }
                    stopSelf()
                }.start()
            }
            "ring" -> {
                stopped = false
                startFg(NOTI_RING, buildRingNoti(this, ringWho(this)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                startRingFlow()
            }
            // 系统闹钟自检：走**真实链路**（AlarmManager → 广播 → 服务）响一声就停。
            // 不动真实排期、不弹响铃页、不触发"响完收尾"——只用来证明"系统会不会把进程拉起来"。
            "test" -> {
                stopped = false
                ringing = true
                startFg(NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_selfcheck_running)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                status("自检：系统闹钟把进程拉起来了 ✅（这条就是「广播有没有送达」的铁证）")
                vol = 1f
                playFallback()
                handler.postDelayed({ stopPlayback(); stopSelf() }, 12_000)
            }
            "snooze" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); if (!isManualTest) AlarmScheduler.snooze(this, 5); stopSelf() }
            "dismiss" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); if (!isManualTest) afterRing(); stopSelf() }
            // 「停止」：只停、**不动排期**（主页打开时 / 响铃页停止 / stopIfRinging 用）
            "stop" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    /** 诊断日志：写到 alarm/status.txt（卡片里能看"上次结果"） */
    private fun status(msg: String) = logStatus(this, msg)

    // ---------------------------------------------------------------- 前台服务
    private fun startFg(id: Int, n: Notification, type: Int) {
        ensureChannel(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, id, n, type)
            else startForeground(id, n)
        }
    }

    private fun pregenNoti(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setShowWhen(false).build()

    private fun updateRingNoti(text: String) {
        runCatching {
            (getSystemService(NotificationManager::class.java))?.notify(NOTI_RING, buildRingNoti(this, text))
        }
    }

    // ---------------------------------------------------------------- 预生成
    /** 生成到 alarmDir；成功（≥1 段可播）返回 true。失败原因：缺 LLM Key / 没选音色 / LLM 或 TTS 出错。 */
    private fun generate(): Boolean {
        if (generating) { status("pregen 跳过：已有一次生成在进行中（防互相删档）"); return false }
        generating = true
        try {
            val store = Store(this)
            val cfg = AlarmCfg.from(store.alarmJson)
            val dir = store.alarmDir
            val pid0 = cfg.providerId.ifBlank { store.providerId }
            val pname0 = TtsProviders.byId(pid0)?.name ?: pid0
            status("pregen 开始：LLM Key=${if (store.llmKey.isBlank()) "空!" else "有"}，音色=${if (cfg.voiceId.isBlank()) "空!" else cfg.voiceName + "(" + cfg.voiceId.take(14) + ")"}，条数=${cfg.count}，字数下限=${cfg.minChars}，厂商=$pname0($pid0)，模型=${cfg.model.ifBlank { store.lastModel }}")
            // ⚠️ **不再一上来就删旧缓存**（用户 2026-10-10 报的 bug 根因）：
            //    旧代码在这里 dir.listFiles().forEach{delete()}，"设完闹钟马上到点"时旧语音被删、新的还没写出来
            //    → 到点缓存为空 → 只能响系统铃声。现在改成：先写 tmp/，**全部成功后再一次性替换**。
            val tmp = File(dir, "tmp").apply { mkdirs() }
            tmp.listFiles()?.forEach { it.delete() }
            if (store.llmKey.isBlank()) { status("pregen 失败：没填 LLM Key（设置 → 模型 → 润色）"); return false }
            if (cfg.voiceId.isBlank()) { status("pregen 失败：闹钟没选音色"); return false }
            // ⚠️ 秒失败：百炼音色配了非百炼厂商（Fish 等不认识百炼音色 id）——不要白等 TTS 超时
            if (pid0 != "aliyun-bailian" && cfg.voiceId in VoiceCatalog.builtIn.map { it.second }) {
                status("pregen 失败：音色「${cfg.voiceName}」是**百炼**音色，而当前 TTS 厂商是 $pname0($pid0) → 请把闹钟音色改选成 $pname0 的音色")
                return false
            }
            val msgs = writeMessages(store, cfg)
            if (msgs == null) { status("pregen 失败：LLM 没写出合格文案（或超时）"); return false }
            status("LLM 写出 ${msgs.size} 条（字数 ${msgs.map { charCount(it) }}）")
            var i = 0
            for (m in msgs) {
                i++
                val e = synthToFile(store, cfg, m, File(tmp, String.format("%02d.wav", i)))
                if (e != null) {
                    status("pregen 失败：第 $i 条 TTS 失败（厂商=${TtsProviders.byId(cfg.providerId.ifBlank { store.providerId })?.name ?: cfg.providerId}，模型=${cfg.model.ifBlank { store.lastModel }}，音色=${cfg.voiceName}/${cfg.voiceId.take(14)}）：$e")
                    return false
                }
                status("TTS 第 $i 条已存盘（tmp）")
            }
            val made = tmp.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name } ?: emptyList()
            if (made.isEmpty()) { status("pregen 失败：没有产出可播的语音"); return false }
            // 全部成功 → 才清旧、换上新的（这一刻之前，旧缓存一直可播）
            dir.listFiles()?.filter { it.isFile && it.name != "status.txt" }?.forEach { it.delete() }
            made.forEach { f -> f.renameTo(File(dir, f.name)) }
            status("pregen 完成：共 ${made.size} 段")
            return true
        } catch (e: Exception) {
            status("pregen 异常：${e.javaClass.simpleName}: ${e.message}")
            return false
        } finally {
            generating = false
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
    private fun synthToFile(store: Store, cfg: AlarmCfg, text: String, out: File): String? {   // null=成功；否则=错误原因
        val latch = CountDownLatch(1); var ok = false; var err: String? = null
        val req = SynthRequest(
            apiKey = "", workspace = store.workspace, model = cfg.model.ifBlank { store.lastModel }, voice = cfg.voiceId,
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
            override fun onError(message: String) { err = message; latch.countDown() }
        }
        runCatching { Synth.dispatch(this, store, req, null, "wav", DashScopeClient(), cb, cfg.providerId) }
            .onFailure { err = it.message }
        latch.await(60, TimeUnit.SECONDS)
        return if (ok) null else (err ?: "无响应/超时（60 秒）")
    }

    // ---------------------------------------------------------------- 播放
    @Volatile private var stopped = false

    /**
     * 响铃：**绝不静默**。
     *  - 有预生成 → 直接播 AI 人声；
     *  - 没预生成 → **立刻**响系统铃声，同时现场生成（LLM→TTS）；**30 秒内**成功 → 切成 AI 人声。
     * 先把**全屏响铃页 + 高优先级通知**挂出去（即播放链路出意外，用户也看得到、点得到）。
     */
    private fun startRingFlow() {
        if (ringing) { status("响铃：已在响，忽略这次重复触发（防两个播放器叠在一起）"); return }
        ringing = true
        acquireWake()
        files = cachedFiles()
        vol = 1f                                      // 音量**恒定**（不做渐强）
        status("响铃：预生成文件 ${files.size} 段（另有生成在进行中=$generating）")
        launchRingUi()
        if (files.isNotEmpty()) {
            updateRingNoti(ringWho(this))
            idx = 0; playCurrent(); return
        }
        // 没有缓存 → 先响系统铃声（绝不静默）；然后**轮询等缓存**——很可能是"保存闹钟那一刻的预生成"
        // 还在跑（旧代码把缓存删了在重做）。30 秒内一出现就切成 AI 人声；若压根没人生成，才自己起一次。
        status("响铃：无缓存 → 先响系统铃声；等预生成（最多 ${LIVE_GEN_LIMIT_MS / 1000} 秒）")
        playFallback()
        updateRingNoti(getString(R.string.alarm_voice_fail))
        val t0 = SystemClock.elapsedRealtime()
        if (!generating) Thread { runCatching { generate() } }.start()
        val poll = object : Runnable {
            override fun run() {
                if (stopped) return
                val f = cachedFiles()
                if (f.isNotEmpty()) {
                    files = f; idx = 0
                    runCatching { player?.release() }; player = null
                    playCurrent()
                    status("响铃：已切换为 AI 人声（${f.size} 段，等了 ${(SystemClock.elapsedRealtime() - t0) / 1000} 秒）")
                    updateRingNoti(ringWho(this@AlarmService))
                    return
                }
                if (SystemClock.elapsedRealtime() - t0 < LIVE_GEN_LIMIT_MS) handler.postDelayed(this, 1500)
                else status("响铃：等 ${LIVE_GEN_LIMIT_MS / 1000} 秒仍无语音 → 保持系统铃声")
            }
        }
        handler.postDelayed(poll, 1500)
    }

    /** 可播的预生成语音（**只认真文件**：tmp/ 子目录不算、status.txt 不算）。 */
    private fun cachedFiles(): List<File> =
        Store(this).alarmDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.name != "status.txt" }
            ?.sortedBy { it.name } ?: emptyList()

    /** 唤起全屏响铃页（后台起 Activity 可能被系统拒绝 → 失败就靠通知的"全屏意图"）。 */
    private fun launchRingUi() {
        val ok = runCatching {
            startActivity(Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        val fsi = android.os.Build.VERSION.SDK_INT < 34 ||
            (getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false)
        status("响铃页：直接拉起=${if (ok) "成功" else "被系统拦"}；全屏通知权限=${if (fsi) "已开" else "**未开**（安卓 14+ 默认不给，「闹钟权限体检」里可开）"}")
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
            if (ok) { status("兜底铃声已播（type=$t）"); return }
        }
        status("兜底铃声：系统没有可用的默认提示音 → 只能静默")
    }

    private fun stopPlayback() {
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
        stopped = true
        ringing = false
        stopPlayback()
    }
}
