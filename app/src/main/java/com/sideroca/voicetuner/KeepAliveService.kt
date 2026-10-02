package com.sideroca.voicetuner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 后台保活：声音复刻 / 合成这类「要等十几秒到几十秒」的活，
 * 用**前台服务**把进程钉住 —— 否则切到后台后，系统会冻结进程或收走网络，
 * 表现就是「回到软件发现连接断了」。
 *
 * 只负责"活着"：真正的请求仍在原来的后台线程里跑，这个服务不碰业务。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "正在后台处理…"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, "后台任务", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                        enableVibration(false)
                    }
                )
            }
        }
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("玲珑调音")
            .setContentText(title)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
        startForeground(NOTI_ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CH = "vt_keepalive"
        private const val NOTI_ID = 1001
        private const val EXTRA_TITLE = "title"

        fun start(ctx: Context, title: String) {
            try {
                val i = Intent(ctx, KeepAliveService::class.java).putExtra(EXTRA_TITLE, title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                // 保活失败不该影响主流程
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
