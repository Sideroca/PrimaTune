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
                // ⚠️ 通道 ID 故意带了 `2`（用户 2026-10-14）：
                //    旧通道 `vt_keepalive` 已经在用户机上**创建过**了，而**通知通道的"重要级别"一经创建就不可改**
                //    （代码里改 IMPORTANCE 对老用户无效）→ 旧通道可能停在会"弹横幅"的档位，于是每次合成
                //    屏幕上方都弹一下 ✗。换一个新 ID = 新通道，才能真正变成"静音、不弹、只躺在通知栏" ✓。
                nm.createNotificationChannel(
                    NotificationChannel(CH, "后台任务", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)          // 静音
                    }
                )
            }
        }
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("玲珑调音")
            .setContentText(title)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)   // 最低：绝不出横幅
            .setSilent(true)                                // 再保险一次
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTI_ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CH = "vt_keepalive2"     // ← 见上文：换了新 ID，旧的不可改的通道不再复用
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
