package com.sideroca.voicetuner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat

/**
 * 内置图标样式的"素材合成"：背景层铺底 + 前景层盖上。
 * 设置页预览、以及「创建桌面入口 → 取景」当素材，都用这一处，避免两处各写一遍。
 */
object IconStyles {

    /**
     * 键的含义（2026-10-17 起）：
     *  - default / a → 芯片（默认 App 图标）
     *  - purple / d  → 紫罗兰（原「紫·默认」，改成可选样式）
     *  - b → 玫瑰；c → 调音台
     * 旧键 a 保留兼容（以前「芯片」的键）。
     */
    fun fgRes(key: String): Int = when (key) {
        "purple", "d" -> R.mipmap.ic_launcher_fg      // 原紫色 App 图标的前景（白框芯片）
        "b" -> R.drawable.icon_style_b_fg
        "c" -> R.drawable.icon_style_c_fg
        else -> R.drawable.icon_style_a_fg            // default / a / 未知 → 芯片
    }

    fun bgRes(key: String): Int = when (key) {
        "purple", "d" -> R.mipmap.ic_launcher_bg      // 紫色底
        "b" -> R.drawable.icon_style_b_bg
        "c" -> R.drawable.icon_style_c_bg
        else -> R.drawable.icon_style_a_bg            // default / a / 未知 → 芯片
    }

    fun compose(ctx: Context, key: String, size: Int, safeInsetFrac: Float = 0.10f): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        ContextCompat.getDrawable(ctx, bgRes(key))?.apply {
            setBounds(0, 0, size, size); draw(cv)
        }
        // 芯片原图四边自带 ~10% 留白；自适应图标前景还要再缩（XML 里 15%），
        // 设置页预览用 5% 就能在视觉上对上桌面实际大小。其他样式不额外缩。
        val inset = if (key == "default" || key == "a") (size * safeInsetFrac).toInt() else 0
        ContextCompat.getDrawable(ctx, fgRes(key))?.apply {
            setBounds(inset, inset, size - inset, size - inset); draw(cv)
        }
        return bmp
    }
}
