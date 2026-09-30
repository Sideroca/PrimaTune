package com.sideroca.voicetuner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 二次截取·取景控件（移植自闪译 CropView 的**极简版**）：
 * 固定取景框（比例 = 目标屏幕比例）+ 图片"填满"初始放置 + 拖动 / 双指缩放 + 框外压暗；
 * [cropped] 输出框内那一块。
 * 闪译里的"映射剪影"(mapEnabled/mapAlpha) 与旋转后的取景恢复(pendingFit) 本版**未移植**，
 * 需要时再补。
 */
class CropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bmp: Bitmap? = null
    private var aspect = 0.474f          // 取景框 宽/高
    private var sc = 1f
    private var minSc = 1f
    private var tx = 0f
    private var ty = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastDist = 0f

    private val pImg = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pDim = Paint().apply { color = 0xA6000000.toInt() }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
    }
    private val frame = RectF()

    fun setImage(b: Bitmap, frameAspect: Float) {
        bmp = b
        aspect = if (frameAspect > 0.1f) frameAspect else 0.474f
        post { fit() }
    }

    /** 取景框 + 初始"填满" */
    private fun fit() {
        val b = bmp ?: return
        if (width <= 0 || height <= 0) return
        val d = resources.displayMetrics.density
        val pad = 14f * d
        val fw = width - pad * 2
        val fh = fw / aspect
        val cy = height / 2f
        frame.set(pad, cy - fh / 2f, width - pad, cy + fh / 2f)
        minSc = max(frame.width() / b.width, frame.height() / b.height)
        sc = minSc
        tx = frame.centerX() - b.width * sc / 2f
        ty = frame.centerY() - b.height * sc / 2f
        clamp()
        pEdge.strokeWidth = 3f * d
        invalidate()
    }

    private fun clamp() {
        val b = bmp ?: return
        sc = max(sc, minSc)
        val w = b.width * sc
        val h = b.height * sc
        tx = min(tx, frame.left)
        tx = max(tx, frame.right - w)
        ty = min(ty, frame.top)
        ty = max(ty, frame.bottom - h)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        if (frame.width() <= 0f) return
        canvas.drawBitmap(b, tx, ty, pImg)
        // 框外压暗：画四块，避免"整屏蒙层"把框内也压暗
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, frame.top, pDim)
        canvas.drawRect(0f, frame.bottom, w, h, pDim)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, pDim)
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, pDim)
        canvas.drawRect(frame, pEdge)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastDist = dist(e)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val nd = dist(e)
                    if (lastDist > 0f && nd > 0f) {
                        val k = nd / lastDist
                        val old = sc
                        sc = (sc * k).coerceIn(minSc, minSc * 8f)
                        val f = sc / old
                        val mx = (e.getX(0) + e.getX(1)) / 2f
                        val my = (e.getY(0) + e.getY(1)) / 2f
                        tx = mx - (mx - tx) * f
                        ty = my - (my - ty) * f
                    }
                    lastDist = nd
                } else {
                    tx += e.x - lastX
                    ty += e.y - lastY
                    lastX = e.x; lastY = e.y
                }
                clamp(); invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastDist = 0f
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun dist(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = (e.getX(0) - e.getX(1)).toDouble()
        val dy = (e.getY(0) - e.getY(1)).toDouble()
        return kotlin.math.hypot(dx, dy).toFloat()
    }

    /** 输出：取景框内那一块（从原图裁） */
    fun cropped(): Bitmap? {
        val b = bmp ?: return null
        if (frame.width() <= 0f || sc <= 0f) return null
        val x = ((frame.left - tx) / sc).toInt().coerceIn(0, max(0, b.width - 1))
        val y = ((frame.top - ty) / sc).toInt().coerceIn(0, max(0, b.height - 1))
        val w = (frame.width() / sc).toInt().coerceIn(1, b.width - x)
        val h = (frame.height() / sc).toInt().coerceIn(1, b.height - y)
        return try {
            Bitmap.createBitmap(b, x, y, w, h)
        } catch (e: Exception) {
            null
        }
    }
}
