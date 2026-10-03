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

    fun fgRes(key: String): Int = when (key) {
        "a" -> R.drawable.icon_style_a_fg
        "b" -> R.drawable.icon_style_b_fg
        "c" -> R.drawable.icon_style_c_fg
        else -> R.drawable.icon_style_d_fg
    }

    fun bgRes(key: String): Int = when (key) {
        "a" -> R.drawable.icon_style_a_bg
        "b" -> R.drawable.icon_style_b_bg
        "c" -> R.drawable.icon_style_c_bg
        else -> R.drawable.icon_style_d_bg
    }

    fun compose(ctx: Context, key: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        ContextCompat.getDrawable(ctx, bgRes(key))?.apply {
            setBounds(0, 0, size, size); draw(cv)
        }
        ContextCompat.getDrawable(ctx, fgRes(key))?.apply {
            setBounds(0, 0, size, size); draw(cv)
        }
        return bmp
    }
}
