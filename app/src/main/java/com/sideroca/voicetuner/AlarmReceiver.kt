package com.sideroca.voicetuner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 闹钟到点 → 拉起前台服务（精确闹钟带"可启动前台服务"豁免）。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("alarm_action") ?: return
        val i = Intent(context, AlarmService::class.java).putExtra("alarm_action", action)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }
}

/** 开机 / 应用更新后重新排闹钟（否则重启即失效）。 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { AlarmScheduler.apply(context) }
    }
}
