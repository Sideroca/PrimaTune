package com.sideroca.voicetuner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 安全写入剪贴板。
 *
 * ⚠️ 背景：`ClipboardManager.setPrimaryClip` 走 Binder 事务，**上限约 1MB**；
 * 一旦塞入超长文本（实测 4.7MB）会抛 `TransactionTooLargeException` 且**直接闪退**。
 * 因此所有"复制"都必须走这里：① 先截断到安全长度；② 兜住一切异常。
 * 绝不因为一次复制把 App 干掉。
 */
object Clip {

    /** 字符数上限（UTF-16 约 200KB，远低于 Binder 的 ~1MB 上限） */
    private const val MAX_CHARS = 100_000

    /** 返回 true = 已写入剪贴板。超长会截断并提示。 */
    fun copy(ctx: Context, text: String, label: String = ""): Boolean {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val tooLong = text.length > MAX_CHARS
        val payload = if (tooLong) text.substring(0, MAX_CHARS) else text
        return try {
            cm.setPrimaryClip(ClipData.newPlainText(label, payload))
            Toast.makeText(
                ctx,
                if (tooLong) "内容过长，已复制前 " + MAX_CHARS + " 字" else "已复制",
                Toast.LENGTH_SHORT
            ).show()
            true
        } catch (e: Throwable) {
            Toast.makeText(ctx, "复制失败：内容过大", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
