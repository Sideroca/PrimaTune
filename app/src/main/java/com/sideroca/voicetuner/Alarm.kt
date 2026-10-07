package com.sideroca.voicetuner

import org.json.JSONObject

/**
 * AI 闹钟（单闹钟 MVP）。
 * **提前数小时**预生成（LLM 写词 → TTS 合成 → 存 WAV），到点**只播放**。
 */
data class AlarmCfg(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    /** once（一次性） | daily（每天） | weekday（工作日） */
    val repeat: String = "once",
    val voiceId: String = "",
    val voiceName: String = "",
    /** 给该音色写的提示词 / 描述（人设、语气、要说什么） */
    val prompt: String = "",
    /** 字数下限（LLM 生成时要求、收到后校验） */
    val minChars: Int = 80,
    /** 生成几条（按顺序播放） */
    val count: Int = 1
) {
    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("hour", hour); put("minute", minute); put("repeat", repeat)
        put("voiceId", voiceId); put("voiceName", voiceName); put("prompt", prompt)
        put("minChars", minChars); put("count", count)
    }.toString()

    companion object {
        fun from(s: String?): AlarmCfg = try {
            val o = JSONObject(s ?: "")
            AlarmCfg(
                enabled = o.optBoolean("enabled", false),
                hour = o.optInt("hour", 7), minute = o.optInt("minute", 30),
                repeat = o.optString("repeat", "once"),
                voiceId = o.optString("voiceId"), voiceName = o.optString("voiceName"),
                prompt = o.optString("prompt"),
                minChars = o.optInt("minChars", 80), count = o.optInt("count", 1)
            )
        } catch (e: Exception) { AlarmCfg() }
    }
}
