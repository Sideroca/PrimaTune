package com.sideroca.voicetuner

/**
 * 内置音色目录（写死在代码里的 4 个）。
 * 主界面与设置页「音色」页共用这一份，避免两处各写一遍。
 * 元素 = Triple(显示名, 音色 id, 备注)
 */
/**
 * 音色 → 所属 TTS 厂商（2026-10-15）。
 *
 * 为什么需要：音色 ID 由各厂商自己签发，**跨厂商不通用**。
 * 「智能填参」按下时要能判断"我现在选的音色"和"我现在选的厂商"是否对得上。
 *
 * 判定顺序：
 *  1) 自建/复刻音色 → 读 `CustomVoice.providerId`（建的时候记下来的，最准）
 *  2) 前缀兜底：`cosyvoice-*` = 阿里云百炼（内置 4 个 + 百炼复刻的都是这个前缀）
 *  3) 认不出 → null（**不猜、不切**，免得把用户切到不能用的厂商）
 */
object VoiceProviders {
    const val BAILIAN = "aliyun-bailian"

    fun of(id: String?, customs: List<CustomVoice>): String? {
        if (id.isNullOrBlank()) return null
        customs.firstOrNull { it.id == id }?.providerId?.takeIf { it.isNotBlank() }?.let { return it }
        if (id.startsWith("cosyvoice-")) return BAILIAN
        return null
    }
}

object VoiceCatalog {
    val builtIn: List<Triple<String, String, String>> = listOf(
        Triple(
            "苏沐橙（成年·动画）",
            "cosyvoice-v3.5-plus-suchenga-aa83bcc828914d1bba289b7c6a41f21b",
            "来源：动画版原声"
        ),
        Triple(
            "苏沐橙（幼年·动画）",
            "cosyvoice-v3.5-plus-suchengy-a7b9c7381c8b4cce86f77a7e6c6b38c9",
            "来源：《巅峰荣耀》原声"
        ),
        Triple(
            "艾丽妮",
            "cosyvoice-v3.5-plus-ailini-e577e0e261a14032866623c65e4f8e2f",
            "明日方舟 · 任命助理"
        ),
        Triple(
            "艾雅法拉",
            "cosyvoice-v3.5-plus-eyjafjalla-ddd756ac929a420fbe35b31fc8120045",
            "明日方舟 · 报到 / 角色语音"
        )
    )
}
