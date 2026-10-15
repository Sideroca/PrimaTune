package com.sideroca.voicetuner

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 闹钟到点 → ①**先**把通知 + 全屏响铃页挂出去 ②起前台服务播放。
 *
 * 2026-10-15 用户定调：**不再用系统铃声兜底**——服务起不来时只记日志 + 留通知，
 * 「如果这个闹钟不是我想要的音色，我宁可它不播放」。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("alarm_action") ?: return
        // ①-0 **第一件事就写诊断**：能写出来 = 广播送到了、系统把进程拉起来了；
        //      到点没响、这里也没记录 = 闹钟被系统/ROM 清掉了（App 侧无解，见设置→闹钟权限体检）。
        AlarmService.logStatus(context, "接收器：收到「$action」广播 ✅")
        if (action == "ring") {
            // ① 先挂通知：即使服务起不来（国产 ROM 后台限制），用户也看得到、点得到
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(AlarmService.NOTI_RING, AlarmService.buildRingNoti(context, AlarmService.ringWho(context)))
            }
            // ② 唤醒锁：到点后 CPU 别睡回去
            AlarmService.wakeUp(context)
        }
        // ③ 起前台服务（`setAlarmClock` 给了"可启动前台服务"豁免）
        val i = Intent(context, AlarmService::class.java).putExtra(AlarmService.EXTRA, action)
        try {
            ContextCompat.startForegroundService(context, i)
        } catch (e: Exception) {
            // ④ 后台不许起前台服务 → **不再用系统铃声顶替**（用户 2026-10-15：宁可不出声也不播错的）。
            //    只记日志 + 保留已挂出的通知，让人知道"这次没出声、原因是服务没起来"。
            AlarmService.logStatus(context, "服务起不来（${e.javaClass.simpleName}: ${e.message}）→ 本次不出声")
            if (action != "ring") runCatching { context.startService(i) }
        }
    }
}

/**
 * 开机 / 应用更新 / **系统时间或时区被改**后重新排闹钟（否则重启即失效、改时间后不准）。
 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { AlarmScheduler.apply(context) }
    }
}
