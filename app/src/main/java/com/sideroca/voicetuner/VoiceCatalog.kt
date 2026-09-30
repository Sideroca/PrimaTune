package com.sideroca.voicetuner

/**
 * 内置音色目录（写死在代码里的 4 个）。
 * 主界面与设置页「音色」页共用这一份，避免两处各写一遍。
 * 元素 = Triple(显示名, 音色 id, 备注)
 */
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
