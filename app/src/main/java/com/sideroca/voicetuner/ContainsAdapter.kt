package com.sideroca.voicetuner

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * 自动补全适配器：**包含匹配**（不区分大小写），而不是系统默认的"前缀匹配"。
 * 例：输入 "炼" 也能命中「阿里云百炼」；输入 "v4" 能命中 eleven_v4。
 */
class ContainsAdapter(
    ctx: Context,
    private val items: List<String>
) : ArrayAdapter<String>(ctx, android.R.layout.simple_dropdown_item_1line, items.toMutableList()) {
    // 注意：必须传"可变拷贝" —— ArrayAdapter(ctx,res,list) 会直接持有这个 List，
    // 若传进来的是不可变列表（如目录里的 listOf），Filter 里一 clear() 就 UnsupportedOperationException

    private val all: MutableList<String> = items.toMutableList()

    private fun norm(s: String?): String =
        s.orEmpty().lowercase().filter { !it.isWhitespace() }

    /**
     * 下拉每一项：**底色**由 `Skin.visit` 统一刷（AutoCompleteTextView 的 dropDownBackground），
     * 这里只管**字色** —— 不刷的话，浅色弹层上仍是主题的 `textColorPrimary`（近白）→ 看不见。
     * （2026-10-15：和 Skin 那处一起做，构成"下拉不再灰黑"的完整修复。）
     */
    override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
        val v = super.getDropDownView(position, convertView, parent)
        (v as? android.widget.TextView)?.let {
            it.setTextColor(Skin.colors(context).txt)
            Skin.setRole(it, "txt")
        }
        return v
    }

    /** 输入框里那一行文字也跟主题走 */
    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
        val v = super.getView(position, convertView, parent)
        (v as? android.widget.TextView)?.setTextColor(Skin.colors(context).txt)
        return v
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            // 归一化：小写 + 去掉所有空白 —— 手打 "fishaudio" 也能命中「Fish Audio    fish」
            val q = norm(constraint?.toString())
            val res = if (q.isEmpty()) all.toList() else all.filter { norm(it).contains(q) }
            return FilterResults().apply {
                values = res
                count = res.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            val list = results?.values as? List<String>
            if (list != null && list.isNotEmpty()) {
                addAll(list)
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }
    }
}
