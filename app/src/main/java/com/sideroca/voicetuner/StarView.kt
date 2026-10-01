package com.sideroca.voicetuner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * 收藏星：**自绘**。
 * 之前用 ImageView + imageTintList / setColorFilter，在真机上反复不生效（星星一直是灰的），
 * 索性改成自绘：颜色由这里直接画出来，不经过任何 tint/filter 环节，行为 100% 确定。
 *
 * 未收藏 = 描边（颜色 = 旁边文字色）；已收藏 = 亮黄渐变（#F8E97F → #FACF26）+ 同色系深一档描边。
 */
class StarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 是否已收藏 */
    var filled = false
        set(v) {
            field = v
            invalidate()
        }

    /** 未收藏时的描边色（外部按主题设置，跟旁边文字同色） */
    var colorSolid = Color.GRAY
        set(v) {
            field = v
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var built = false

    private val colorLight = 0xFFF8E97F.toInt()
    private val colorDeep = 0xFFFACF26.toInt()
    private val colorEdge = 0xFFE0BC22.toInt()

    private fun build() {
        path.reset()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val outer = minOf(w, h) / 2f * 0.98f
        val inner = outer * 0.46f          // A 档：内径比 0.46
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) outer else inner
            val ang = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (rad * Math.cos(ang)).toFloat()
            val y = cy + (rad * Math.sin(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        built = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        build()
    }

    override fun onDraw(canvas: Canvas) {
        if (!built) build()
        if (path.isEmpty) return

        if (filled) {
            // 已收藏：亮黄渐变实心 + 深一档描边
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat() * 0.35f, height.toFloat(),
                colorLight, colorDeep, Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * resources.displayMetrics.density
            paint.color = colorEdge
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
        } else {
            // 未收藏：只描边，内部透明（露出卡片/壁纸）
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * resources.displayMetrics.density
            paint.color = colorSolid
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
        }
    }
}
