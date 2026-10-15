package com.sideroca.voicetuner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 闹钟调度：**预生成**（提前 3 小时）＋ **响铃**（准点）两条精确闹钟。
 *
 * ⚠️ **两条都用 `setAlarmClock`**（用户 2026-10-08 同意）：它同时给到
 * ①Doze 放行 ②**"可启动前台服务"豁免** ③状态栏闹钟图标 —— 这样**不开 App**、进程不在，
 * 预生成照样能跑、到点照样能播。代价：状态栏会**提前约 3 小时**出现闹钟图标。
 * （以前预生成走 `setExactAndAllowWhileIdle`，在国产 ROM 后台常被拦掉 → 只剩一条通知。）
 */
object AlarmScheduler {

    // 2026-10-15 用户：几十字的稿子，LLM→TTS→收回**一分钟内**就能完，不需要提前那么久。
    private const val LEAD_MIN = 2L
    const val LEAD_MS = LEAD_MIN * 60 * 1000

    private const val RC_PREGEN = 7101
    private const val RC_RING = 7102
    private const val RC_SNOOZE = 7103
    private const val RC_TEST = 7104

    /**
     * 诊断探针用：**这几条闹钟现在还在不在系统里**。
     * 用 `FLAG_NO_CREATE` 问系统"有没有这个 PendingIntent"——系统若把闹钟清掉了，这里就是 false。
     * （只读，不创建、不取消。）
     */
    fun pendingAlive(ctx: Context): Map<String, Boolean> = mapOf(
        "预生成(pregen)" to alive(ctx, RC_PREGEN, "pregen"),
        "响铃(ring)" to alive(ctx, RC_RING, "ring"),
        "贪睡(snooze)" to alive(ctx, RC_SNOOZE, "ring"),
        "自检(test)" to alive(ctx, RC_TEST, "test")
    )

    private fun alive(ctx: Context, rc: Int, action: String): Boolean = runCatching {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra("alarm_action", action)
        PendingIntent.getBroadcast(
            ctx, rc, i,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }.getOrDefault(false)

    fun apply(ctx: Context) {
        val cfg = AlarmCfg.from(Store(ctx).alarmJson)
        cancelAll(ctx)
        if (!cfg.enabled) { AlarmService.logStatus(ctx, "调度：闹钟未启用 → 已取消全部"); return }
        val now = System.currentTimeMillis()
        val ring = nextTrigger(cfg, now)
        setAlarmClock(ctx, RC_RING, "ring", ring)
        // 预生成**总是**排一次：时间 = 响铃前 LEAD_MS；若这个时刻已过（比如刚设一个 5 分钟后的闹钟）
        // → 立刻排（now+5s）。不再有"离得近就不排"那条分支——那等于把失败留给到点。
        val preAt = maxOf(ring - LEAD_MS, now + 5_000L)
        setAlarmClock(ctx, RC_PREGEN, "pregen", preAt)
        AlarmService.logStatus(ctx, "调度：响铃=${fmt(ring)} 预生成=${fmt(preAt)} 精确闹钟=${canExact(ctx)} 重复=${cfg.repeat}")
    }

    private fun fmt(t: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(t))

    /** `setAlarmClock`：最可靠的精确闹钟（Doze 放行 + 给"可启动前台服务"豁免 + 状态栏闹钟图标）。 */
    private fun setAlarmClock(ctx: Context, rc: Int, action: String, at: Long) {
        val p = pi(ctx, rc, action)
        try {
            val show = PendingIntent.getActivity(
                ctx, rc, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p)
        } catch (e: Exception) {
            setFallback(ctx, rc, action, at)          // 个别机型 / 未授权 → 退精确闹钟
        }
    }

    /** 「系统闹钟自检」：delayMs 后**走真实链路**响一声（AlarmManager → 广播 → 服务）。
     *  用来验"App 被划掉 / 进程不在时，系统还会不会把它拉起来"——现有"直接起服务"测不出这个。 */
    fun testSystemAlarm(ctx: Context, delayMs: Long) {
        AlarmService.logStatus(ctx, "自检：已排一个 ${delayMs / 1000} 秒后的系统闹钟（到点会写「接收器：…」）")
        setAlarmClock(ctx, RC_TEST, "test", System.currentTimeMillis() + delayMs)
    }

    fun snooze(ctx: Context, minutes: Int) {
        // 贪睡也走 setAlarmClock：锁屏/后台照样准点
        setAlarmClock(ctx, RC_SNOOZE, "ring", System.currentTimeMillis() + minutes * 60_000L)
    }

    private fun cancelAll(ctx: Context) {
        // ⚠️ RC_TEST 也要取消（用户 2026-10-14）："系统闹钟自检"排的是 15 秒后的闹钟，
        // 以前这里只取消 预生成/响铃/贪睡 三条 → **测试闹钟会留在系统里**，等你下次点「立即预生成」
        // 它到点就响 → 看着像"生成完自己播了"。
        listOf(RC_PREGEN to "pregen", RC_RING to "ring", RC_SNOOZE to "ring", RC_TEST to "test").forEach { (rc, act) ->
            runCatching { am(ctx).cancel(pi(ctx, rc, act)) }
        }
    }

    /** 下一次触发（一次性=今天/明天；每天；工作日=跳过周末） */
    fun nextTrigger(cfg: AlarmCfg, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, cfg.hour); set(Calendar.MINUTE, cfg.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // 一次性：若指定了日期，先落到那天
        if (cfg.repeat == "once" && cfg.date.isNotBlank()) {
            val p = cfg.date.split("-")
            if (p.size == 3) runCatching {
                c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
                c.set(Calendar.HOUR_OF_DAY, cfg.hour); c.set(Calendar.MINUTE, cfg.minute)
            }
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
        if (cfg.repeat == "weekday") {
            while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pi(ctx: Context, rc: Int, action: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra("alarm_action", action)
        return PendingIntent.getBroadcast(
            ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setFallback(ctx: Context, rc: Int, action: String, at: Long) {
        val p = pi(ctx, rc, action)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am(ctx).canScheduleExactAlarms())
                am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else
                am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: Exception) {
            runCatching { am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        }
    }
}
