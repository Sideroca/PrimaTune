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
    private val base = floatArrayOf(0.50f, 0.86f, 0.68f)   // 中间最高、右边次之，整体更和谐（可按喜好微调）

    /** 三根错开相位，避免"齐步走" */
    private val phases = floatArrayOf(0.00f, 0.42f, 0.78f)

    private var t = 0f
    private var anim: ValueAnimator? = null

    private fun ratio(i: Int): Float {
        if (anim == null) return base[i]
        val s = kotlin.math.sin((t + phases[i]) * 2.0 * Math.PI).toFloat()
        // 包络：t=0 与 t=1 处为 0 —— 动画第一帧/最后一帧都等于静止形状，起止不跳变
        val env = kotlin.math.sin(Math.PI * t).toFloat()
        return (base[i] + s * 0.32f * env).coerceIn(0.16f, 1f)
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
            duration = 1500            // 只抖 1.5 秒（按用户要求：点进来动一下就好）
            repeatCount = 0            // 跑一遍就停，不再无限循环
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    anim = null        // 回到"静止形状"（三条长短不一的柱子）
                    invalidate()
                }
            })
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
