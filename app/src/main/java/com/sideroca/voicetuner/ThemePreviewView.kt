package com.sideroca.voicetuner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 主题预览台：把「当前配色 + 壁纸 + 遮罩 + 卡片浓度」实时画成一张迷你界面。
 * 只读主题的语义槽位（bg/card/line/accent/txt/dim），换主题或拖动滑条即刻重绘。
 */
class ThemePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 0 = 主界面，1 = 设置页 */
    var pageMode = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scrimP = Paint(Paint.ANTI_ALIAS_FLAG)

    private var col: Skin.Colors? = null
    private var wall: Bitmap? = null
    private var scrimPct = 35

    private val rect = RectF()
    private val clip = Path()

    fun setData(colors: Skin.Colors, wallpaper: Bitmap?, scrim: Int) {
        col = colors
        wall = wallpaper
        scrimPct = scrim
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = col ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 10f * d

        // 底色
        fill.color = c.bg
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, fill)

        // 壁纸 + 遮罩
        wall?.let { b ->
            canvas.save()
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawBitmap(b, null, RectF(0f, 0f, w, h), null)
            canvas.restore()
            val a = scrimPct.coerceIn(0, 80) * 255 / 100
            scrimP.color = Color.argb(a, Color.red(c.bg), Color.green(c.bg), Color.blue(c.bg))
            canvas.drawRoundRect(rect, r, r, scrimP)
        }

        // 标题条
        fill.color = c.acc
        canvas.drawRoundRect(RectF(10 * d, 12 * d, 56 * d, 20 * d), 4 * d, 4 * d, fill)

        // 两张卡（含卡片浓度）
        fill.color = c.card
        fill.alpha = c.cardAlphaPct.coerceIn(30, 100) * 255 / 100
        val card1 = RectF(8 * d, 30 * d, w - 8 * d, 64 * d)
        val card2 = RectF(8 * d, 70 * d, w - 8 * d, 96 * d)
        canvas.drawRoundRect(card1, 8 * d, 8 * d, fill)
        canvas.drawRoundRect(card2, 8 * d, 8 * d, fill)
        fill.alpha = 255
        stroke.color = c.line
        stroke.strokeWidth = 1.5f * d
        canvas.drawRoundRect(card1, 8 * d, 8 * d, stroke)
        canvas.drawRoundRect(card2, 8 * d, 8 * d, stroke)

        // 卡内"文字"条
        fill.color = c.txt
        canvas.drawRoundRect(RectF(14 * d, 38 * d, 64 * d, 44 * d), 3 * d, 3 * d, fill)
        fill.color = c.dim
        canvas.drawRoundRect(RectF(14 * d, 50 * d, 82 * d, 55 * d), 2.5f * d, 2.5f * d, fill)
        canvas.drawRoundRect(RectF(14 * d, 77 * d, 82 * d, 83 * d), 3 * d, 3 * d, fill)

        // 底部：设置页 = 主行动按钮；主界面 = 底部坞
        if (pageMode == 1) {
            fill.color = c.acc
            canvas.drawRoundRect(RectF(8 * d, h - 30 * d, w - 8 * d, h - 12 * d), 6 * d, 6 * d, fill)
        } else {
            fill.color = c.card
            fill.alpha = 235
            canvas.drawRoundRect(RectF(8 * d, h - 26 * d, w - 8 * d, h - 8 * d), 8 * d, 8 * d, fill)
            fill.alpha = 255
        }
    }
}
