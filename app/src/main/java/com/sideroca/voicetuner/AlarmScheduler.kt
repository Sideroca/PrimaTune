package com.sideroca.voicetuner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** 闹钟调度：**预生成**（提前 3 小时）＋ **响铃**（准点）两条精确闹钟。 */
object AlarmScheduler {

    const val LEAD_MS = 3L * 60 * 60 * 1000      // 提前 3 小时预生成
    private const val RC_PREGEN = 7101
    private const val RC_RING = 7102
    private const val RC_SNOOZE = 7103

    fun apply(ctx: Context) {
        val cfg = AlarmCfg.from(Store(ctx).alarmJson)
        cancelAll(ctx)
        if (!cfg.enabled) return
        val now = System.currentTimeMillis()
        val ring = nextTrigger(cfg, now)
        val pregen = maxOf(now + 60_000, ring - LEAD_MS)
        set(ctx, RC_PREGEN, "pregen", pregen)
        set(ctx, RC_RING, "ring", ring)
    }

    fun snooze(ctx: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        set(ctx, RC_SNOOZE, "ring", at)
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
        if (cfg.repeat == "weekday") {
            while (c.timeInMillis <= now ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                c.add(Calendar.DAY_OF_MONTH, 1)
        } else if (c.timeInMillis <= now) {
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

    private fun set(ctx: Context, rc: Int, action: String, at: Long) {
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
