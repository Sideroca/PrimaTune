package com.sideroca.voicetuner

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 时间显示的统一入口（2026-10-20）。
 *
 * 记录本身存的是 epoch ms（`Take.createdAt`），排序跨年永远正确；
 * 但显示如果固定 `MM-dd HH:mm`，到 2027 年时 2026 的旧记录只剩「12-31」，
 * 会和 2027 的「12-31」混淆。这里按“是否当年”自动补年份：
 *
 *  - 当年：`10-20 23:35`（保持紧凑）
 *  - 非当年：`2026-12-31 23:59`（一眼知道是哪年）
 *
 * 日志用 [log]，固定带年，避免闹钟/启动诊断跨年对不上。
 */
object TimeFmt {

    /** 记录卡 / 记录列表用的时间。 */
    fun record(ms: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            "MM-dd HH:mm"
        } else {
            "yyyy-MM-dd HH:mm"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    /** 日志 / 诊断用：固定 `yyyy-MM-dd HH:mm:ss`。 */
    fun log(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    /** 计划时间 / 排期展示用：固定带年、不带秒。 */
    fun full(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
