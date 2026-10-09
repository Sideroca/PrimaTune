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

    const val LEAD_MS = 3L * 60 * 60 * 1000      // 提前 3 小时预生成
    private const val RC_PREGEN = 7101
    private const val RC_RING = 7102
    private const val RC_SNOOZE = 7103
    private const val RC_TEST = 7104

    fun apply(ctx: Context) {
        val cfg = AlarmCfg.from(Store(ctx).alarmJson)
        cancelAll(ctx)
        if (!cfg.enabled) { AlarmService.logStatus(ctx, "调度：闹钟未启用 → 已取消全部"); return }
        val now = System.currentTimeMillis()
        val ring = nextTrigger(cfg, now)
        val pregen = maxOf(now + 60_000, ring - LEAD_MS)
        setAlarmClock(ctx, RC_PREGEN, "pregen", pregen)
        setAlarmClock(ctx, RC_RING, "ring", ring)
        AlarmService.logStatus(
            ctx,
            "调度：响铃=${fmt(ring)} 预生成=${fmt(pregen)} 精确闹钟=${canExact(ctx)} 重复=${cfg.repeat}"
        )
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
        listOf(RC_PREGEN to "pregen", RC_RING to "ring", RC_SNOOZE to "ring").forEach { (rc, act) ->
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
