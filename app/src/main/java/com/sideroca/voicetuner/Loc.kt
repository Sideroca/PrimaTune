package com.sideroca.voicetuner

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 界面语言（**通用能力**，每个软件都该有）：
 * 跟随系统 / 中 / 英 / 日 / 韩 —— 走 AppCompat 的 **per-app locale**，切换后系统会自动重建界面。
 * 文案本身放在资源里：默认 `values` 为中文，其余为 `values-en` / `values-ja` / `values-ko`。
 */
object Loc {

    /** (显示名, 语言码)；语言码 "" = 跟随系统 */
    val OPTIONS = listOf(
        "跟随系统" to "",
        "中文" to "zh",
        "English" to "en",
        "日本語" to "ja",
        "한국어" to "ko"
    )

    fun current(ctx: Context): String = Store(ctx).lang

    /**
     * 应用保存的语言（AppCompat per-app locale；**变了**才会重建界面）。
     *
     * ⚠️ 2026-10-15：**值没变就不要再 set 一次** —— `setApplicationLocales` 每次调用都会
     * 触发一次语言核对/可能的界面重建，冷启动时它就在首帧之前 → 白屏翻倍。
     */
    fun apply(ctx: Context) {
        val code = current(ctx)
        val want = if (code.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(code)
        if (AppCompatDelegate.getApplicationLocales() == want) return      // 已经是这个语言 → 什么也不做
        AppCompatDelegate.setApplicationLocales(want)
    }

    /** 设置并立即生效 */
    fun set(ctx: Context, code: String) {
        Store(ctx).lang = code
        apply(ctx)
    }
}
