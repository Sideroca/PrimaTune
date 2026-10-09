package com.sideroca.voicetuner

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **全屏响铃页**（用户 2026-10-08 要）：到点弹满屏 —— 大字时间 ＋「谁在说话」＋ 停止 / 贪睡 5 分钟。
 * 由 `AlarmService` 起（或通知的**全屏意图**拉起），锁屏上也显示、并点亮屏幕。
 * 「返回」＝ 贪睡（防手滑把闹钟按掉；想真停就用「停止」）。
 */
class AlarmRingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            findViewById<TextView>(R.id.tvRingTime)?.text = fmtNow("HH:mm")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Loc.apply(this)
        // 锁屏可见 + 亮屏（API 27+ 用新接口，26 退回窗口 flag）
        @Suppress("DEPRECATION")
        run {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        setContentView(R.layout.activity_alarm_ring)

        val c = Skin.colors(this)
        Skin.applyWindow(this, c)
        Skin.apply(window.decorView, c)

        val store = Store(this)
        val cfg = AlarmCfg.from(store.alarmJson)
        val who = cfg.voiceName.ifBlank { getString(R.string.voice_custom) }
        findViewById<TextView>(R.id.tvRingWho).text = getString(R.string.alarm_ring_who, who)
        findViewById<TextView>(R.id.tvRingDate).text = fmtNow(getString(R.string.date_fmt))
        findViewById<TextView>(R.id.tvRingTime).text = fmtNow("HH:mm")
        findViewById<TextView>(R.id.tvRingSnooze).text = getString(R.string.alarm_ring_snooze)
        findViewById<TextView>(R.id.tvRingStop).apply {
            text = getString(R.string.alarm_ring_stop)
            setTextColor(0xFFFFFFFF.toInt())      // 与主页「生成」一致：主行动按钮统一白字
        }

        findViewById<TextView>(R.id.tvRingStop).setOnClickListener { fire("dismiss"); finish() }
        findViewById<TextView>(R.id.tvRingSnooze).setOnClickListener { fire("snooze"); finish() }
    }

    private fun fire(action: String) {
        runCatching {
            startService(Intent(this, AlarmService::class.java).putExtra(AlarmService.EXTRA, action))
        }
    }

    private fun fmtNow(pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

    @Deprecated("按「返回」= 贪睡：手滑也不会把闹钟按掉")
    override fun onBackPressed() {
        fire("snooze")
        finish()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}
