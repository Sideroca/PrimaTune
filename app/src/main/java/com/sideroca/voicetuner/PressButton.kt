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
 * 外观：半透明键面 + 白描边 + 顶部内高光 —— 背景能透出来（薄、平、亮）。
 * 手感：按下 90ms 沉入 5dp、内阴影加深；松手 150ms 弹回（带一点过冲）。
 * 颜色由外部显式 setColors() 给（不参与 Skin 的"角色"体系）。
 */
class PressButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var label: String = "保存全部设置"

    /** 形状/质感：随配色成套（《夕汀前端规范》①-1.1 + 〇-8） */
    private var radiusDp = 11f
    private var elevDp = 0f
    var solidMode = false
        private set

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

    /** 由页面显式上色 + 上形状（颜色一律走主题槽位，见《夕汀前端规范》〇-6） */
    fun applyTheme(
        faceColor: Int, strokeColor: Int, inkColor: Int,
        radiusDp: Float, elevDp: Float, solid: Boolean
    ) {
        this.faceColor = faceColor
        this.strokeColor = strokeColor
        this.inkColor = inkColor
        this.radiusDp = radiusDp
        this.elevDp = elevDp
        this.solidMode = solid
        invalidate()
    }

    fun setColors(faceColor: Int, strokeColor: Int, inkColor: Int) =
        applyTheme(faceColor, strokeColor, inkColor, radiusDp, elevDp, solidMode)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val off = press * sinkPx
        val r = radiusDp * density
        rect.set(0f, off, w, h + off)

        // 投影（elev > 0 才画；纯描边质感 = 0）
        if (elevDp > 0f) {
            inner.color = 0x14000000
            val so = (0.6f * elevDp + 1f) * density
            canvas.drawRoundRect(rect.left, rect.top + so, rect.right, rect.bottom + so, r, r, inner)
        }

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
