package com.sideroca.voicetuner

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * 版式小工具：把**卡片里第一个子元素**的上边距清零。
 *
 * 为什么：VtCard 自己有 paddingTop，而"卡片里第一个标签"又带 `VtLabel` 的上边距 →
 * 两者**叠起来**才是内容到卡顶的距离（14dp 内距 + 10dp 标签距 = 24dp，看着"离卡边太远"）。
 * 清零后：**顶距只由卡片自己的 paddingTop 决定**（好调、可预期）；中间那些标签的上边距照旧。
 *
 * 识别"卡片"的办法：竖向 LinearLayout + 有背景 + `paddingTop` 等于给定值（＝VtCard 的值）。
 */
object LayoutFix {
    fun flattenCardFirstTop(root: View?, cardPadTopPx: Int) {
        val vg = root as? ViewGroup ?: return
        for (i in 0 until vg.childCount) {
            val ch = vg.getChildAt(i)
            if (ch is LinearLayout && ch.orientation == LinearLayout.VERTICAL &&
                ch.paddingTop == cardPadTopPx && ch.background != null
            ) {
                if (ch.childCount > 0) {
                    val first = ch.getChildAt(0)
                    (first.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                        if (lp.topMargin != 0) {
                            lp.topMargin = 0
                            first.layoutParams = lp
                        }
                    }
                }
            }
            flattenCardFirstTop(ch, cardPadTopPx)
        }
    }
}
