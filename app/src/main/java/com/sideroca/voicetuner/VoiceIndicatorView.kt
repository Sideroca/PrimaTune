package com.sideroca.voicetuner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * 「当前音色」指示符：一条心电线条。
 *  - amp   幅度：0 = 平线，1 = 满幅波形
 *  - inset 两端烧掉的比例：0 = 全长，0.5 = 烧到中心
 *
 * 退场（playOut）：amp 1→0（平掉）→ inset 0→0.5（两端像绳子被点燃，向中间烧）→ 余烬熄灭
 * 入场（playIn） ：inset 0.5→0（从中心长出直线）→ amp 0→1（活起来）
 *
 * 颜色固定为亮蓝（浅/深主题下都稳），不参与换肤。
 */
class VoiceIndicatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var amp = 1f
        set(v) { field = v; invalidate() }

    var inset = 0f
        set(v) { field = v; invalidate() }

    /** 指示符颜色（外观可选项，默认极光绿） */
    var indicatorColor = 0xFF2FE39B.toInt()
        set(v) {
            field = v
            if (visibility != INVISIBLE) line.color = v
            invalidate()
        }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF2FE39B.toInt()
    }
    private val ember = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** 归一化的心电形状（样式 A「深心电」）：x ∈ [-1,1] 为半宽比例，y 为半高比例 */
    private val xs = floatArrayOf(-1f, -0.42f, -0.24f, -0.06f, 0.10f, 0.28f, 0.48f, 1f)
    private val ys = floatArrayOf(0f, 0f, -0.36f, -1f, 1f, -0.26f, 0f, 0f)

    private val path = Path()

    private fun stop() {
        anim?.cancel()
        anim = null
    }

    private var anim: Animator? = null
    /** 生成中：一直在动 */
    private var busy = false
    private var phase = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val halfW = w / 2f * 0.94f * (1f - 2f * inset)
        val halfH = h / 2f * 0.62f * amp
        val ph = phase * (2.0 * Math.PI).toFloat()
        if (busy) {
            // 「生成中」＝ G · 干涉波：两条**反相**正弦交叉流动；整数个波长 → 走完一圈无缝循环
            // （用户 2026-10-05 选定 G；静态/入场/退场仍是下面那条"心电"折线）
            val d = resources.displayMetrics.density
            line.strokeWidth = 1.15f * d
            drawWave(canvas, cx, cy, halfW, halfH, ph, indicatorColor, 255)
            drawWave(canvas, cx, cy, halfW, halfH, ph + Math.PI.toFloat(), indicatorColor, 105)
        } else {
            line.strokeWidth = 1.3f * resources.displayMetrics.density
            path.reset()
            for (i in xs.indices) {
                val px = cx + xs[i] * halfW
                val py = cy + ys[i] * halfH
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, line)
        }
        // 正在燃烧的两端：一点余烬
        if (inset > 0.01f && inset < 0.49f) {
            for (s in intArrayOf(-1, 1)) {
                val x = cx + s * halfW
                ember.color = 0xFFBFE8FF.toInt(); ember.alpha = 70
                canvas.drawCircle(x, cy, line.strokeWidth * 1.8f, ember)
                ember.color = 0xFFFFFFFF.toInt(); ember.alpha = 150
                canvas.drawCircle(x, cy, line.strokeWidth * 0.7f, ember)
            }
        }
    }

    /** 画一条正弦（G·干涉波用）。offset 传 π 即为反相的一条。 */
    private fun drawWave(canvas: Canvas, cx: Float, cy: Float, halfW: Float, halfH: Float, offset: Float, col: Int, a: Int) {
        path.reset()
        val n = 48
        for (i in 0..n) {
            val u = i.toFloat() / n
            val x = cx - halfW + 2f * halfW * u
            // 24dp 下 1 个波长最清楚；整数 → 无缝
            val y = cy + halfH * kotlin.math.sin((2.0 * Math.PI * u).toFloat() + offset)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        line.color = col
        line.alpha = a
        canvas.drawPath(path, line)
        line.alpha = 255
    }

    /** 生成中：一直动（连续摆动，无限循环） */
    fun startBusy() {
        stop()
        visibility = VISIBLE
        alpha = 1f
        inset = 0f
        amp = 1f
        line.color = indicatorColor
        busy = true
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800          // 一圈 1.8s（用户 2026-10-05：原 1.2s 太快）
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }
        anim = a
        a.start()
    }

    /** 停止"生成中"的摆动（不改变可见性） */
    fun stopBusy() {
        busy = false
        stop()
        invalidate()
    }

    /** 静态显示（不播动画） */
    fun showStatic() {
        busy = false
        stop()
        visibility = VISIBLE
        alpha = 1f
        inset = 0f
        amp = 1f
        line.color = indicatorColor
    }

    /** 退场：平掉 → 两端烧向中心 → 熄灭。
     *  注意：退场**直接用雾蓝灰**（不做「本色 → 灰」的颜色过渡）。 */
    fun playOut(onEnd: (() -> Unit)? = null) {
        busy = false
        stop()
        visibility = VISIBLE
        alpha = 1f
        amp = 1f
        inset = 0f
        line.color = OUT_COLOR
        val flatten = ValueAnimator.ofFloat(1f, 0f).setDuration(140)
        flatten.interpolator = DecelerateInterpolator()
        flatten.addUpdateListener { amp = it.animatedValue as Float }
        val burn = ValueAnimator.ofFloat(0f, 0.5f).setDuration(210)
        burn.interpolator = LinearInterpolator()
        burn.addUpdateListener { inset = it.animatedValue as Float }
        val out = ValueAnimator.ofFloat(1f, 0f).setDuration(90)
        out.addUpdateListener { alpha = it.animatedValue as Float }
        val set = AnimatorSet()
        set.playSequentially(flatten, burn, out)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = INVISIBLE
                onEnd?.invoke()
            }
        })
        anim = set
        set.start()
    }

    /** 入场：从中心长出 → 活起来 */
    fun playIn() {
        busy = false
        stop()
        visibility = VISIBLE
        alpha = 0f
        inset = 0.5f
        amp = 0f
        line.color = indicatorColor
        val fade = ValueAnimator.ofFloat(0f, 1f).setDuration(120)
        fade.addUpdateListener { alpha = it.animatedValue as Float }
        val grow = ValueAnimator.ofFloat(0.5f, 0f).setDuration(190)
        grow.interpolator = DecelerateInterpolator()
        grow.addUpdateListener { inset = it.animatedValue as Float }
        val wake = ValueAnimator.ofFloat(0f, 1f).setDuration(260)
        wake.interpolator = DecelerateInterpolator()
        wake.addUpdateListener { amp = it.animatedValue as Float }
        val head = AnimatorSet()
        head.playTogether(fade, grow)
        val all = AnimatorSet()
        all.playSequentially(head, wake)
        anim = all
        all.start()
    }

    companion object {
        /** 退场（"即将消失"）统一用它：雾蓝灰 —— 直接就是它，不做"本色 → 灰"的过渡 */
        private val OUT_COLOR = 0xFF8AA0B6.toInt()
    }
}
