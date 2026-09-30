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
 * 主题预览台：把「当前配色 + 壁纸 + 遮罩 + 卡片浓度」画成**贴近真实界面**的迷你屏。
 * - pageMode = 0：主界面（标题条 / 参数卡三行滑条 / 生成大按钮 / 本地记录卡）
 * - pageMode = 1：设置页（标题 / 配色卡若干行 + 色球 / 底部五格坞 / 保存条）
 * 只读主题语义槽位，换主题或拖滑条即刻重绘。
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

    private fun rr(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float) {
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, fill)
    }

    /** 一条"文字"占位条 */
    private fun bar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        rr(canvas, x, y, w, h, h / 2f)
    }

    private fun card(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, c: Skin.Colors) {
        fill.color = c.card
        fill.alpha = c.cardAlphaPct.coerceIn(30, 100) * 255 / 100
        rr(canvas, x, y, w, h, r)
        fill.alpha = 255
        stroke.color = c.line
        stroke.strokeWidth = 1.2f * resources.displayMetrics.density
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, stroke)
    }

    override fun onDraw(canvas: Canvas) {
        val c = col ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 10f * d

        // 屏底
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

        val pad = 9f * d
        val cw = w - pad * 2

        if (pageMode == 0) {
            // ---- 主界面 ----
            fill.color = c.txt
            bar(canvas, pad, pad + 2 * d, 34 * d, 9 * d)          // 标题
            // 参数卡
            val cy = pad + 18 * d
            val ch = 54 * d
            card(canvas, pad, cy, cw, ch, 7 * d, c)
            for (i in 0 until 3) {
                fill.color = c.dim
                bar(canvas, pad + 7 * d, cy + (6 + i * 14) * d, 16 * d, 5 * d)      // 标签
                fill.color = c.line
                bar(canvas, pad + 28 * d, cy + (7 + i * 14) * d, cw - 60 * d, 3 * d) // 轨道
                fill.color = c.acc
                val kx = pad + 28 * d + (cw - 60 * d) * (0.30f + i * 0.16f)
                canvas.drawCircle(kx, cy + (8.5f + i * 14) * d, 4 * d, fill)          // 滑钮
                fill.color = c.txt
                bar(canvas, w - pad - 16 * d, cy + (6 + i * 14) * d, 12 * d, 5 * d)   // 数值
            }
            // 生成大按钮
            val by = cy + ch + 9 * d
            fill.color = c.acc
            rr(canvas, pad, by, cw, 22 * d, 11 * d)
            fill.color = c.onAcc
            bar(canvas, w / 2 - 9 * d, by + 8 * d, 18 * d, 6 * d)
            // 本地记录卡
            val ry = by + 30 * d
            card(canvas, pad, ry, cw, h - ry - pad, 8 * d, c)
            fill.color = c.txt
            bar(canvas, pad + 7 * d, ry + 8 * d, cw * 0.62f, 6 * d)
            bar(canvas, pad + 7 * d, ry + 18 * d, cw * 0.42f, 6 * d)
            fill.color = c.dim
            bar(canvas, pad + 7 * d, ry + 29 * d, cw * 0.5f, 5 * d)
            fill.color = c.acc
            rr(canvas, pad + 7 * d, ry + 38 * d, 26 * d, 14 * d, 7 * d)
            fill.color = c.onAcc
            bar(canvas, pad + 12 * d, ry + 43 * d, 16 * d, 4 * d)
        } else {
            // ---- 设置页 ----
            fill.color = c.txt
            bar(canvas, pad, pad + 2 * d, 26 * d, 9 * d)          // 标题「设置」
            // 配色卡：4 行「名字 + 4 色球」
            val cy = pad + 17 * d
            card(canvas, pad, cy, cw, 62 * d, 8 * d, c)
            val dots = intArrayOf(0, 0, 0, 0)
            for (i in 0 until 4) {
                dots[0] = c.bg; dots[1] = c.card; dots[2] = c.acc; dots[3] = c.barBg
                fill.color = c.txt
                bar(canvas, pad + 7 * d, cy + (7 + i * 14) * d, cw * 0.45f, 5 * d)
                for (k in 0 until 4) {
                    fill.color = dots[k]
                    canvas.drawCircle(
                        pad + cw - (7 + (3 - k) * 9) * d,
                        cy + (9.5f + i * 14) * d, 3.6f * d, fill
                    )
                }
            }
            // 指示符色卡
            val iy = cy + 62 * d + 7 * d
            card(canvas, pad, iy, cw, 30 * d, 8 * d, c)
            fill.color = c.txt
            bar(canvas, pad + 7 * d, iy + 7 * d, 26 * d, 5 * d)
            fill.color = c.acc
            bar(canvas, pad + 7 * d, iy + 17 * d, cw * 0.55f, 5 * d)
            // 底部：五格坞 + 保存条
            val dy = h - 46 * d
            card(canvas, pad, dy, cw, 22 * d, 9 * d, c)
            val seg = cw / 5f
            for (i in 0 until 5) {
                val cx = pad + seg * i + seg / 2
                fill.color = if (i == 2) c.acc else c.dim
                canvas.drawCircle(cx, dy + 8 * d, 3.2f * d, fill)
                bar(canvas, cx - 7 * d, dy + 15 * d, 14 * d, 4 * d)
            }
            val sy = dy + 26 * d
            fill.color = c.acc
            fill.alpha = 56                                   // 玻璃感：accent @ 22%
            rr(canvas, pad, sy, cw, 14 * d, 7 * d)
            fill.alpha = 255
            stroke.color = c.acc
            stroke.alpha = 102
            rect.set(pad, sy, w - pad, sy + 14 * d)
            canvas.drawRoundRect(rect, 7 * d, 7 * d, stroke)
            stroke.alpha = 255
        }
    }
}
