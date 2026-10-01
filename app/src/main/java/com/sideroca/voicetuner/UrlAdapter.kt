package com.sideroca.voicetuner

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView

/**
 * URL 下拉项适配器：条目若以 "★" 开头，表示"推荐"——
 * 左边显示地址，**最右边**显示「推荐」两个字（用主题主色）。
 * 其余条目就是普通一行地址。
 */
class UrlAdapter(
    ctx: Context,
    items: List<String>
) : ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, items.toMutableList()) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val raw = getItem(position).orEmpty()
        val recommended = raw.startsWith("★")
        val url = raw.removePrefix("★")
        val c = Skin.colors(context)

        val row = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val d = context.resources.displayMetrics.density
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        }
        row.removeAllViews()

        val left = TextView(context)
        left.text = url
        left.textSize = 13f
        left.setTextColor(c.txt)
        left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(left)

        if (recommended) {
            val tag = TextView(context)
            tag.text = "推荐"
            tag.textSize = 12f
            tag.typeface = Typeface.DEFAULT_BOLD
            tag.setTextColor(c.acc)
            tag.setPadding((8 * context.resources.displayMetrics.density).toInt(), 0, 0, 0)
            row.addView(tag)
        }
        return row
    }

    /** 取回干净地址（去掉 ★ 标记） */
    override fun getItem(position: Int): String = (super.getItem(position) ?: "").removePrefix("★")
}
