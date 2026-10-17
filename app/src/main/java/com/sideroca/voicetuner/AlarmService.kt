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
 *  - `ring`  ：到点播放（按序、音量恒定）。**只播"当前设置的那段语音"**（用户 2026-10-15 定调）：
 *      ① 有匹配的预生成 → 直接播 AI 人声；
 *      ② 没有 → **不出声**，同时现场生成（≤60 秒），生成好立刻播；生成不出来就保持静音；
 *      （**不再**用系统铃声顶替——那是对 bug 的容忍，不是功能）；
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
        private const val CH = "vt_alarm"                 // 到点响铃用（HIGH：要弹全屏响铃页）
        /** 预生成等"幕后"通知用：LOW + 静音 + 不弹（用户 2026-10-16：生成时不该在屏幕前端弹东西） */
        private const val CH_QUIET = "vt_alarm_quiet"
        private const val NOTI_PREGEN = 7301
        const val NOTI_RING = 7302

        /** 生成窗口：没有可用语音时，现场生成这么久还没好 → **保持静音**（不再用系统铃声顶替） */
        private const val LIVE_GEN_LIMIT_MS = 60_000L   // 几十字稿 1 分钟内能成；超时＝失败

        /**
         * 预生成缓存的**配置指纹**。
         *
         * 为什么需要（用户 2026-10-14 报"改成爱布拉娜却还播迷迭香"）：
         * v1.6.4 起"预生成失败时保留旧语音"是为了"到点至少有声音"，但如果**用户换了音色而新一次预生成失败**
         * （最常见是音色与当前厂商不匹配），旧语音会一直留着 → 到点就播**上一个音色** ✗。
         * 现在给缓存盖一个指纹（音色/提示词/条数/字数下限/厂商/模型）；**指纹不符的旧缓存一律不播**。
         */
        private fun stampFile(ctx: Context) = File(Store(ctx).alarmDir, "cache.json")

        fun cacheMatches(ctx: Context): Boolean {
            val f = stampFile(ctx)
            if (!f.exists()) return false
            val cfg = AlarmCfg.from(Store(ctx).alarmJson)
            return runCatching {
                val o = org.json.JSONObject(f.readText())
                o.optString("voiceId") == cfg.voiceId &&
                    o.optString("prompt") == cfg.prompt &&
                    o.optInt("count") == cfg.count &&
                    o.optInt("minChars") == cfg.minChars &&
                    o.optString("providerId") == cfg.providerId &&
                    o.optString("model") == cfg.model
            }.getOrDefault(false)
        }

        private fun writeStamp(ctx: Context) {
            val cfg = AlarmCfg.from(Store(ctx).alarmJson)
            runCatching {
                stampFile(ctx).writeText(
                    org.json.JSONObject().apply {
                        put("voiceId", cfg.voiceId); put("prompt", cfg.prompt)
                        put("count", cfg.count); put("minChars", cfg.minChars)
                        put("providerId", cfg.providerId); put("model", cfg.model)
                    }.toString()
                )
            }
        }

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
         * 现场生成完成后的回调（响铃服务注册）。
         * 为什么要它：轮询最多等 60 秒，而 LLM(30s)+TTS(60s) 最坏能到 90 秒；
         * 只看轮询会出现「其实早就生成好了，但没人播」——生成线程结束时主动回调一次。
         */
        @Volatile private var genDoneCb: (() -> Unit)? = null

        private fun notifyGenDone() {
            val cb = genDoneCb
            genDoneCb = null
            cb?.let { runCatching { it() } }
        }

        /** 取消闹钟通知（停止/贪睡/打开 App 停止时都该清掉，避免点旧通知又拉起响铃页）。 */
        fun cancelRingNoti(ctx: Context) {
            runCatching {
                (ctx.getSystemService(NotificationManager::class.java))?.cancel(NOTI_RING)
            }
        }

        /**
         * **只停，不动排期**（"dismiss" 会做响完收尾、可能关掉一次性闹钟；这个不会）。
         * 主页 onResume / 响铃页「停止」/ 外部调用都走它。
         */
        fun stopIfRinging(ctx: Context): Boolean {
            if (!ringing) return false
            return runCatching {
                ctx.startService(Intent(ctx, AlarmService::class.java).putExtra(EXTRA, "stop"))
                stopDirect()
                cancelRingNoti(ctx)
                true
            }.getOrDefault(false)
        }

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
            // 2026-10-16：**清掉遗留频道**。历史上 KeepAliveService 换过 id（vt_keepalive → vt_keepalive2），
            // 旧的那个还躺在系统里（设置页里就是两条同名「后台任务」）。以前只在 KeepAliveService 启动时删，
            // 而它可能很久不启动 → 改成**任何服务起来时都删一次**（幂等）。
            runCatching { nm.deleteNotificationChannel("vt_keepalive") }
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, ctx.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true); enableVibration(true)
                    }
                )
            }
            if (nm.getNotificationChannel(CH_QUIET) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH_QUIET, ctx.getString(R.string.alarm_channel_quiet), NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)          // 静音
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
                .setContentIntent(full)          // 没给全屏权限时，点通知正文也能进响铃页
                .addAction(0, ctx.getString(R.string.alarm_ring_snooze), snooze)
                .addAction(0, ctx.getString(R.string.alarm_ring_stop), dismiss)
                .build()
        }

        /**
         * 停止（只清状态）。
         *
         * ⚠️ 2026-10-15 用户定调：**删掉一切"兜底发声"**——
         * 「如果这个闹钟不是我想要的音色，我宁可它不播放」。用系统铃声顶替 = 对 bug 的容忍，不再保留。
         */
        fun stopDirect() {
            ringing = false
            genDoneCb = null
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
                // 正在响铃时绝不让预生成插进来：它会 startFg 换成静音通知，还会删旧缓存
                if (ringing) {
                    status("pregen 跳过：正在响铃")
                    stopSelf(startId)
                } else {
                    startFg(NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    val sid = startId
                    Thread {
                        if (!generate()) runCatching {
                            (getSystemService(NotificationManager::class.java))?.notify(
                                NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_voice_fail)))
                        }
                        // 用 startId 停：如果期间又来了 "ring" 命令，startId 不匹配 → 服务不会被误停
                        stopSelf(sid)
                    }.start()
                }
            }
            "ring" -> {
                stopped = false
                startFg(NOTI_RING, buildRingNoti(this, ringWho(this)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                startRingFlow(isManualTest)
            }
            // 系统闹钟自检：走**真实链路**（AlarmManager → 广播 → 服务）响一声就停。
            // 不动真实排期、不弹响铃页、不触发"响完收尾"——只用来证明"系统会不会把进程拉起来"。
            "test" -> {
                stopped = false
                ringing = true
                startFg(NOTI_PREGEN, pregenNoti(getString(R.string.alarm_title), getString(R.string.alarm_selfcheck_running)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                status("自检：系统闹钟把进程拉起来了 ✅（这条就是「广播有没有送达」的铁证）")
                vol = 1f
                if (cachedFiles().isNotEmpty()) { files = cachedFiles(); idx = 0; playCurrent() }
                else status("自检：没有可用语音 → 不出声（自检只验「系统会不会把进程拉起来」）")
                handler.postDelayed({ stopPlayback(); stopSelf(startId) }, 12_000)
            }
            "snooze" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); cancelRingNoti(this); if (!isManualTest) AlarmScheduler.snooze(this, 5); stopSelf() }
            "dismiss" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); cancelRingNoti(this); if (!isManualTest) afterRing(); stopSelf() }
            // 「停止」：只停、**不动排期**（主页打开时 / 响铃页停止 / stopIfRinging 用）
            "stop" -> { stopped = true; ringing = false; stopPlayback(); stopDirect(); cancelRingNoti(this); stopSelf() }
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
        NotificationCompat.Builder(this, CH_QUIET)     // 幕后通知：静音、不弹
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
    private fun generate(live: Boolean = false): Boolean {
        if (generating) { status("pregen 跳过：已有一次生成在进行中（防互相删档）"); return false }
        generating = true
        try {
            val store = Store(this)
            val cfg = AlarmCfg.from(store.alarmJson)
            val dir = store.alarmDir
            val pid0 = cfg.providerId.ifBlank { store.providerId }
            val pname0 = TtsProviders.byId(pid0)?.name ?: pid0
            status("pregen 开始：LLM Key=${if (store.llmKey.isBlank()) "空!" else "有"}，音色=${if (cfg.voiceId.isBlank()) "空!" else cfg.voiceName + "(" + cfg.voiceId.take(14) + ")"}，条数=${cfg.count}，字数下限=${cfg.minChars}，厂商=$pname0($pid0)，模型=${cfg.model.ifBlank { store.lastModel }}")
            // 用户 2026-10-14：**每次生成都是一条全新的** —— 开工先把旧的全部清掉
            // （"过去生成的、没用上的，不要留在本地"）。生成失败就什么都不留（宁缺勿错）。
            dir.listFiles()?.filter { it.isFile && it.name != "status.txt" }?.forEach { it.delete() }
            val tmp = File(dir, "tmp").apply { mkdirs() }
            tmp.listFiles()?.forEach { it.delete() }
            if (store.llmKey.isBlank()) { status("pregen 失败：没填 LLM Key（设置 → 模型 → 润色）"); return false }
            if (cfg.voiceId.isBlank()) { status("pregen 失败：闹钟没选音色"); return false }
            // ⚠️ 秒失败：百炼音色配了非百炼厂商（Fish 等不认识百炼音色 id）——不要白等 TTS 超时
            if (pid0 != "aliyun-bailian" && cfg.voiceId in VoiceCatalog.builtIn.map { it.second }) {
                status("pregen 失败：音色「${cfg.voiceName}」是**百炼**音色，而当前 TTS 厂商是 $pname0($pid0) → 请把闹钟音色改选成 $pname0 的音色")
                return false
            }
            // 方案 B：**离响铃还 >3 分钟**才允许"30 秒失败后再给 90 秒"；到点现场生成（live）一律不重试
            val leftMs = if (live) 0L else (AlarmScheduler.nextTrigger(cfg, System.currentTimeMillis()) - System.currentTimeMillis())
            val msgs = writeMessages(store, cfg, allowLongRetry = !live && leftMs > 3 * 60_000L)
            if (msgs == null) { status("pregen 失败：LLM 没写出合格文案（或超时）"); return false }
            status("LLM 写出 ${msgs.size} 条（字数 ${msgs.map { charCount(it) }}）")
            // 2026-10-16 提速：多条**并行**合成（以前串行 → N 条＝N 倍等待）
            val errs = java.util.Collections.synchronizedList(ArrayList<String>())
            val done = CountDownLatch(msgs.size)
            msgs.forEachIndexed { i, m ->
                Thread {
                    // try/finally：万一 synthToFile 抛了，也要把闩放掉——否则主线程要白等到 180 秒
                    try {
                        // 预生成可慢（跟网络层 120s 对齐）；到点现场生成只给 60s（响铃不能干等）
                        val e = synthToFile(store, cfg, m, File(tmp, String.format("%02d.wav", i + 1)), if (live) 60 else 120)
                        if (e != null) errs.add("第 ${i + 1} 条：$e")
                        else status("TTS 第 ${i + 1} 条已存盘（tmp）")
                    } catch (t: Throwable) {
                        errs.add("第 ${i + 1} 条：异常 ${t.javaClass.simpleName}: ${t.message}")
                    } finally {
                        done.countDown()
                    }
                }.start()
            }
            done.await(180, TimeUnit.SECONDS)
            if (errs.isNotEmpty()) {
                val who = TtsProviders.byId(cfg.providerId.ifBlank { store.providerId })?.name ?: cfg.providerId
                status("pregen 失败：TTS 失败（厂商=$who，模型=${cfg.model.ifBlank { store.lastModel }}，音色=${cfg.voiceName}/${cfg.voiceId.take(14)}）→ ${errs.joinToString("；")}")
                return false
            }
            val made = tmp.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name } ?: emptyList()
            if (made.isEmpty()) { status("pregen 失败：没有产出可播的语音"); return false }
            // 全部成功 → 才清旧、换上新的（这一刻之前，旧缓存一直可播）
            dir.listFiles()?.filter { it.isFile && it.name != "status.txt" && it.name != "cache.json" }?.forEach { it.delete() }
            made.forEach { f -> f.renameTo(File(dir, f.name)) }
            writeStamp(this)                       // 盖指纹：之后"配置变了"的旧缓存不会被误播
            status("pregen 完成：共 ${made.size} 段")
            return true
        } catch (e: Exception) {
            status("pregen 异常：${e.javaClass.simpleName}: ${e.message}")
            return false
        } finally {
            generating = false
            notifyGenDone()          // 响铃时注册的“生成完成 → 立刻试播”回调（轮询超时也不怕）
        }
    }

    /**
     * 让 LLM 写 N 条（每条 ≥ minChars 字）。
     *
     * ⚠️ 2026-10-16 **提速**（用户："总觉得很慢"；日志里 00:33→00:35 那两分钟就卡在这一步）：
     *  ① **强制关思考**（`level = "off"`）——几十字的闹钟问候不需要推理；
     *     **只影响闹钟这一条链路**，用户在别处设的思考档位/温度一概不动；
     *  ② 送 `max_tokens`（没设或过大 → 400）——免得模型长篇大论；
     *  ③ **只给一次机会、超时 30 秒**（旧的是"90 秒 × 重试一次" ＝ 最坏 **3 分钟**）；
     *     字数不达标就**直接用** + 记一行日志，不再重跑（重跑往往还是那个长度）。
     */
    private fun writeMessages(store: Store, cfg: AlarmCfg, allowLongRetry: Boolean): List<String>? {
        val sys = "你是闹钟语音撰稿人。"
        val user = buildString {
            append("音色/角色：").append(cfg.voiceName.ifBlank { "未指定" }).append('\n')
            val role = store.rolePrompt(cfg.voiceId)
            if (role.isNotBlank()) append("角色提示词：").append(role).append('\n')
            if (cfg.prompt.isNotBlank()) append("要求：").append(cfg.prompt).append('\n')
            append("请写 ").append(cfg.count).append(" 条**互不相同**的闹钟问候/叮嘱，每条**不少于 ")
                .append(cfg.minChars).append(" 字**。")
            append("只输出 JSON 字符串数组，例如 [\"第一条\",\"第二条\"]，不要解释。")
        }
        val maxTok = if (store.llmMaxTokens in 1..400) store.llmMaxTokens else 400

        // 一次尝试：固定"关思考"（几十字的问候不需要推理），等 timeoutSec 秒
        fun once(timeoutSec: Int): List<String> {
            val latch = CountDownLatch(1)
            var out: String? = null
            LlmClient.ask(
                store.llmBaseUrl, store.llmKey, store.llmModel, sys, user,
                maxTok, store.llmTemp / 10.0, "off"
            ) { res, _ -> out = res; latch.countDown() }
            latch.await(timeoutSec.toLong(), TimeUnit.SECONDS)
            return parseMessages(out, cfg)
        }

        var list = once(30)
        if (list.isEmpty() && allowLongRetry) {
            // 方案 B（用户 2026-10-16 选）：**离响铃还早**时才再给一次 90 秒机会——
            // 服务商侧耗时波动很大（实测 3s / 9s / 70s / 116s），给慢的那次留活路；到点现场生成不重试。
            status("LLM 30 秒没回 → 离响铃还 >3 分钟，再给一次 90 秒机会")
            list = once(90)
        }
        if (list.isEmpty()) return null
        if (list.any { charCount(it) < cfg.minChars })
            status("LLM 文案偏短（${list.map { charCount(it) }}，要求 ≥${cfg.minChars}）→ 直接用，不再重跑")
        return list
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
    private fun synthToFile(store: Store, cfg: AlarmCfg, text: String, out: File, timeoutSec: Int = 120): String? {   // null=成功；否则=错误原因
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
        latch.await(timeoutSec.toLong(), TimeUnit.SECONDS)
        return if (ok) null else (err ?: "无响应/超时（${timeoutSec} 秒）")
    }

    // ---------------------------------------------------------------- 播放
    @Volatile private var stopped = false

    /**
     * 响铃：**只播"当前设置的那段语音"**（用户 2026-10-15：宁可静音，也不播错的）。
     *  - 有匹配的预生成 → 直接播 AI 人声；
     *  - 没有 → **不出声**，同时现场生成（LLM→TTS）；**60 秒内**成功 → 立刻播；生成不出来就保持静音。
     * 无论哪种情况，都先把**全屏响铃页 + 高优先级通知**挂出去（让人看得到、点得到）。
     */
    private fun startRingFlow(isManualTest: Boolean = false) {
        if (ringing) { status("响铃：已在响，忽略这次重复触发（防两个播放器叠在一起）"); return }
        ringing = true
        acquireWake()
        files = cachedFiles()
        vol = 1f                                      // 音量**恒定**（不做渐强）
        status("响铃：预生成文件 ${files.size} 段（生成中=$generating，手测=$isManualTest）")
        launchRingUi()
        if (files.isNotEmpty()) {
            updateRingNoti(ringWho(this))
            idx = 0; playCurrent(); return
        }

        // 没有可用语音：注册“生成完成 → 立刻试播”。
        // 为什么不能只靠 1.5 秒轮询：轮询最多等 60 秒，而现场生成最坏 LLM 30s + TTS 60s；
        // 会出现“其实早生成好了，但轮询已经放弃 → 一直不响，点通知才醒”。
        genDoneCb = {
            handler.post {
                if (!tryPlayReady("生成完成") && ringing && !stopped) {
                    updateRingNoti(getString(R.string.alarm_voice_fail))
                    status("响铃：生成结束但仍无可用语音 → 保持静音")
                }
            }
        }

        // ⚠️ 手动测试且没有可用语音 → **绝不要拿系统铃声冒充 AI 闹钟**
        if (isManualTest) {
            status("手测：没有可用的闹钟语音 → 只提示（先生成）")
            updateRingNoti(getString(R.string.alarm_test_no_voice))
            if (!generating) Thread { runCatching { generate(live = true) } }.start()
            pollForVoice(SystemClock.elapsedRealtime(), silent = true)
            return
        }
        // 没有缓存 → **不出声**（宁可静音也不播错的），等缓存出现再播——很可能是"保存闹钟那一刻的
        // 预生成"还在跑。生成完成会走上面的回调；轮询只是兜底。
        status("响铃：没有符合当前设置的语音 → **不出声**，现场生成中（最多 ${LIVE_GEN_LIMIT_MS / 1000} 秒）")
        updateRingNoti(getString(R.string.alarm_generating))
        if (!generating) Thread { runCatching { generate(live = true) } }.start()
        pollForVoice(SystemClock.elapsedRealtime(), silent = false)
    }

    /** 有可播语音就切过去（幂等：已在播则直接返回 true）。 */
    private fun tryPlayReady(reason: String): Boolean {
        if (stopped || !ringing) return false
        if (runCatching { player?.isPlaying == true }.getOrDefault(false)) return true
        val f = cachedFiles()
        if (f.isEmpty()) return false
        files = f
        idx = 0
        runCatching { player?.release() }
        player = null
        playCurrent()
        updateRingNoti(ringWho(this))
        status("响铃：$reason → 已自动播放（${f.size} 段）")
        return true
    }

    /** 轮询等"可播语音"出现：一出现就切换成 AI 人声；[silent] = 手测模式（失败也不出声）。 */
    private fun pollForVoice(t0: Long, silent: Boolean) {
        val poll = object : Runnable {
            override fun run() {
                if (stopped) return
                if (tryPlayReady("轮到语音")) return
                if (SystemClock.elapsedRealtime() - t0 < LIVE_GEN_LIMIT_MS) {
                    handler.postDelayed(this, 1500)
                } else {
                    status(
                        if (silent) "手测：等 ${LIVE_GEN_LIMIT_MS / 1000} 秒仍没有语音（本次不出声）"
                        else "响铃：等 ${LIVE_GEN_LIMIT_MS / 1000} 秒仍无语音 → **保持静音**（不再用系统铃声顶替）"
                    )
                    if (!silent) updateRingNoti(
                        getString(if (generating) R.string.alarm_generating else R.string.alarm_voice_fail)
                    )
                }
            }
        }
        handler.postDelayed(poll, 1500)
    }

    /** 可播的预生成语音（**只认真文件**：tmp/ 子目录不算、status.txt / cache.json 不算；**且指纹要对**）。 */
    private fun cachedFiles(): List<File> {
        if (!cacheMatches(this)) {
            if (Store(this).alarmDir.listFiles()?.any { it.isFile && it.name.endsWith(".wav") } == true) {
                status("响铃：缓存是**旧设置**生成的（音色/提示词等已改）→ 视为未生成，按新设置来")
            }
            return emptyList()
        }
        return Store(this).alarmDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.name != "status.txt" && it.name != "cache.json" }
            ?.sortedBy { it.name } ?: emptyList()
    }

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
            mp.setOnErrorListener { _, what, extra -> status("播放失败：what=$what extra=$extra → 不出声"); true }
            mp.prepare(); mp.start()
            player = mp
        }.onFailure { status("播放异常：${it.javaClass.simpleName}: ${it.message} → 不出声") }
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
        genDoneCb = null
        stopPlayback()
    }
}
