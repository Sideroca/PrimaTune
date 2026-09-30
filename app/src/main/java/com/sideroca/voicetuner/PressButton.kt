package com.sideroca.voicetuner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 「透明凹陷」保存键。
 * 外观：半透明键面 + 白描边 + 顶部内高光 —— 背景能透出来（薄、平、亮；不发光、不扫光）。
 * 手感：按下 90ms 沉入 5dp、内阴影加深；松手 150ms 弹回（带一点过冲）。
 * 颜色由外部显式 setColors() 给（不参与 Skin 的"角色"体系）。
 */
class PressButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var label: String = "保存全部设置"

    private val density = resources.displayMetrics.density
    private val sinkPx = 5f * density

    private var press = 0f
    private var anim: ValueAnimator? = null

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var faceColor = 0xE6FFE0D2.toInt()
    private var strokeColor = 0xFFFFFFFF.toInt()
    private var inkColor = 0xFF7C3A2F.toInt()

    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        txt.textSize = 13.5f * density
        edge.strokeWidth = 1.5f * density
    }

    /** 由页面显式上色（浅/深主题各一套） */
    fun setColors(faceColor: Int, strokeColor: Int, inkColor: Int) {
        this.faceColor = faceColor
        this.strokeColor = strokeColor
        this.inkColor = inkColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val off = press * sinkPx
        val r = 11f * density
        rect.set(0f, off, w, h + off)

        face.color = faceColor
        canvas.drawRoundRect(rect, r, r, face)

        if (press > 0.01f) {
            inner.color = ((press * 0.28f * 255).toInt() shl 24) or 0x00504030
            canvas.drawRoundRect(rect, r, r, inner)
        }
        inner.color = 0x66FFFFFF
        canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.top + 1.5f * density, r, r, inner)

        edge.color = strokeColor
        canvas.drawRoundRect(rect, r, r, edge)

        txt.color = inkColor
        val cy = rect.centerY() - (txt.descent() + txt.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), cy, txt)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animateTo(1f, 90, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animateTo(0f, 150, true)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateTo(0f, 150, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun animateTo(target: Float, durMs: Long, overshoot: Boolean) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(press, target).apply {
            duration = durMs
            interpolator = if (overshoot) OvershootInterpolator(1.6f) else DecelerateInterpolator()
            addUpdateListener {
                press = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
