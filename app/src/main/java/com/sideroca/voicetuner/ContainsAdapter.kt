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
) : ArrayAdapter<String>(ctx, android.R.layout.simple_dropdown_item_1line, items) {

    private val all: MutableList<String> = items.toMutableList()

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = constraint?.toString()?.trim()?.lowercase().orEmpty()
            val res = if (q.isEmpty()) all.toList() else all.filter { it.lowercase().contains(q) }
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
