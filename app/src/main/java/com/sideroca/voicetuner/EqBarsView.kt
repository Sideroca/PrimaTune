package com.sideroca.voicetuner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 坞里「音色」的图标：三条彩色音柱（均衡器 / 声波条）。
 *
 * 动效：切到该页时 start() —— 三根柱子各自独立地长短起伏（相位错开，不齐步），
 * 用一条 0→1 的线性循环驱动 sin(2πt + 相位)，首尾天生无缝；离开该页 stop() 回到静态形状。
 * （这套做法就是 iOS 语音备忘录"录音中"、Spotify 正在播放、Discord 说话指示用的那类）
 */
class EqBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val colors = intArrayOf(
        0xFF57B06C.toInt(),   // 绿
        0xFF3E9BB5.toInt(),   // 青
        0xFFF2C33C.toInt()    // 黄
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** 静态时三根的长短（未动画也像音柱） */
    private val base = floatArrayOf(0.52f, 0.92f, 0.38f)

    /** 三根错开相位，避免"齐步走" */
    private val phases = floatArrayOf(0.00f, 0.42f, 0.78f)

    private var t = 0f
    private var anim: ValueAnimator? = null

    private fun ratio(i: Int): Float {
        if (anim == null) return base[i]
        val s = kotlin.math.sin((t + phases[i]) * 2.0 * Math.PI).toFloat()
        return (base[i] + s * 0.32f).coerceIn(0.16f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val barW = w / 8.6f
        val xs = floatArrayOf(w * 0.24f, w * 0.5f, w * 0.76f)
        for (i in 0 until 3) {
            val bh = h * ratio(i)
            val top = (h - bh) / 2f
            paint.color = colors[i]
            canvas.drawRoundRect(
                xs[i] - barW / 2f, top, xs[i] + barW / 2f, top + bh,
                barW / 2f, barW / 2f, paint
            )
        }
    }

    /** 切到本页：开始起伏 */
    fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 离开本页：停下（回到静态形状） */
    fun stop() {
        anim?.cancel()
        anim = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
