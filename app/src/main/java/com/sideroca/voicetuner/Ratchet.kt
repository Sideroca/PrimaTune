package com.sideroca.voicetuner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import android.widget.SeekBar

/**
 * 棘轮卡位（从首页三根滑条抽出来，设置页也共用同一套手感）：
 *  · 拖动中：吸附到 step 的整数倍，每过一档给一次 CLOCK_TICK 轻震；
 *  · 松手时：用 140ms 动画吸附到最近档位（一点点阻尼/咔哒感），动画结束再刷一次值。
 *
 * 用法：Ratchet.attach(seekBar, step) { /* 用 seekBar.progress 更新 */ }
 */
object Ratchet {

    fun attach(sb: SeekBar, step: Int, onChanged: () -> Unit) {
        val s = step.coerceAtLeast(1)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val snapped = ((progress + s / 2) / s) * s
                if (snapped != progress) {
                    bar.progress = snapped          // 递归回调里 fromUser=false → 会直接返回
                    return
                }
                bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChanged()
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {
                val snapped = ((bar.progress + s / 2) / s) * s
                if (snapped != bar.progress) {
                    val a = ObjectAnimator.ofInt(bar, "progress", snapped).setDuration(140)
                    a.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { onChanged() }
                    })
                    a.start()
                    bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onChanged()
            }
        })
    }
}
