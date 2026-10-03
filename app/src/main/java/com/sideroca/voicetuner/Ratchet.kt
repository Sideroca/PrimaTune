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
                val snapped = ((progress + s / 2) / s) * s
                if (snapped != progress) {
                    // 吸附到整档：这次设置会递归回调一次（fromUser=false），由那一次负责刷新数字
                    bar.progress = snapped
                    return
                }
                // 关键：**值一旦对齐就立刻刷新数字**，不再区分 fromUser。
                // 之前 `if (!fromUser) return` 会让"吸附"那条路径跳过刷新 →
                // 数字只碰巧落在整档上时才更新，快拖到最左边时会"卡在原来的值"。
                if (fromUser) bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
