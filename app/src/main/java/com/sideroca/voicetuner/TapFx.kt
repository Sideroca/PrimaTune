package com.sideroca.voicetuner

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 点按反馈三件套（《夕汀前端规范》⑦-4「该有的状态变化都要动效」）：
 *
 *  1) 按下水波 —— 给 View 的 **foreground** 装 RippleDrawable。
 *     刻意不碰 background：Skin 换肤靠 `background as? GradientDrawable` 识别角色，
 *     动 background 会把换肤弄坏；foreground 则完全不参与 Skin。
 *  2) 主行动「下沉」—— 按下整块下沉 1dp（90ms Decelerate），松手 150ms Overshoot 回弹
 *     （与 PressButton 保存键同一套手感）。
 *  3) 「点击留痕」—— 在卡片根 View 的 ViewOverlay 上迸 5 颗短寿命星点，扩散淡出后自清。
 *     不新增 View、不 requestLayout，符合〇-14。
 *
 * 用法：TapFx.press(btn, accent, root = cardRow)
 */
object TapFx {

    /** 普通按钮：水波 + 可选留痕；rootAtTouch 用于"父容器在绑定那一刻还没挂上"的场合 */
    fun press(
        v: View, accent: Int, root: View? = null, primary: Boolean = false,
        rootAtTouch: (() -> View?)? = null
    ) {
        press2(v, accent, root, primary, rootAtTouch)
    }

    private fun press2(
        v: View, accent: Int, root: View?, primary: Boolean, rootAtTouch: (() -> View?)?
    ) {
        installRipple(v, accent)
        val d = v.resources.displayMetrics.density
        val sink = 1f * d
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (primary) {
                        view.animate().translationY(sink).setDuration(90)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (primary) {
                        view.animate().translationY(0f).setDuration(150)
                            .setInterpolator(OvershootInterpolator(1.6f)).start()
                    }
                    val r = root ?: rootAtTouch?.invoke()
                    if (r != null) {
                        val a = IntArray(2); view.getLocationInWindow(a)
                        val b = IntArray(2); r.getLocationInWindow(b)
                        // ⚠️ 星点颜色**按下这一刻现取**（不用闭包里捕获的 accent）——否则换主题后它还是旧色
                        spark(r, a[0] - b[0] + e.x, a[1] - b[1] + e.y, Skin.colors(view.context).acc)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (primary) {
                        view.animate().translationY(0f).setDuration(150)
                            .setInterpolator(OvershootInterpolator(1.6f)).start()
                    }
                }
            }
            false
        }
    }

    private fun installRipple(v: View, accent: Int) {
        if (v.foreground != null) return
        retintRipple(v, accent)
    }

    /**
     * **重装**水波（即使已经有 foreground 也重装）——换肤时由 `Skin.apply` 调用，
     * 否则水波里写死的是"装上那一刻"的主题色，换主题后不会跟着变（用户 2026-10-06 报的 bug）。
     */
    fun retintRipple(v: View, accent: Int) {
        val color = (accent and 0x00FFFFFF) or (0x33 shl 24)          // accent @20%
        val mask = v.background?.constantState?.newDrawable()?.mutate() // 水波裁成按钮圆角形状
        v.foreground = RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }

    /**
     * 常驻留痕：整页**空白处**点一下也会迸星点（不影响滚动：位移超过阈值就不算点按）。
     * accent 用 lambda 传，换主题后颜色自动跟着变。
     */
    fun tapAnywhere(v: View, accent: () -> Int) {
        var downX = 0f; var downY = 0f
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                MotionEvent.ACTION_UP -> {
                    val slop = 22f * view.resources.displayMetrics.density
                    if (kotlin.math.hypot(e.x - downX, e.y - downY) < slop) {
                        spark(view, e.x, e.y, accent())
                    }
                }
            }
            false
        }
    }

    // ------------------------------------------------------------------ 留痕
    private fun spark(root: View, x: Float, y: Float, accent: Int) {
        if (root.width <= 0 || root.height <= 0) return
        val dr = SparkDrawable(root, x, y, accent)
        dr.setBounds(0, 0, root.width, root.height)
        root.overlay.add(dr)
    }

    private class SparkDrawable(
        private val root: View, private val cx: Float, private val cy: Float, accent: Int
    ) : Drawable() {

        private class P(val dx: Float, val dy: Float, val r0: Float, val star: Boolean, val rot: Float)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            color = accent
        }
        private val n = 5
        private val parts = ArrayList<P>(n)
        private var t = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f)

        init {
            val ang0 = Random.nextFloat() * 6.28f
            for (i in 0 until n) {
                val ang = ang0 + i * (6.28f / n) + (Random.nextFloat() - .5f) * .4f
                val dist = 7f + Random.nextFloat() * 9f          // dp 级位移，随密度缩放由调用方决定
                parts.add(P(cos(ang) * dist, sin(ang) * dist - 3f, 1.2f + Random.nextFloat(), i % 2 == 0, ang))
            }
            anim.duration = 260
            anim.addUpdateListener { t = it.animatedValue as Float; invalidateSelf() }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    root.overlay.remove(this@SparkDrawable)
                }
            })
            anim.start()
        }

        override fun draw(canvas: Canvas) {
            val k = t
            val a = ((1f - k) * 255).toInt().coerceIn(0, 255)
            if (a <= 0) return
            paint.alpha = a
            val d = root.resources.displayMetrics.density
            for (p in parts) {
                val x = cx + p.dx * d * k
                val y = cy + p.dy * d * k
                paint.strokeWidth = (1.6f * d) * (1f - k * 0.5f)
                if (p.star) {                                   // 小星：两笔交叉
                    val r = p.r0 * d * (1.1f - k * 0.4f) * 2.4f
                    val ca = cos(p.rot); val sa = sin(p.rot)
                    canvas.drawLine(x - r * ca, y - r * sa, x + r * ca, y + r * sa, paint)
                    canvas.drawLine(x + r * sa, y - r * ca, x - r * sa, y + r * ca, paint)
                } else {                                        // 光点
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, p.r0 * d * (1f - k * 0.55f), paint)
                    paint.style = Paint.Style.STROKE
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
