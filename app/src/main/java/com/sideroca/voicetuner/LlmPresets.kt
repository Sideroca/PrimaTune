package com.sideroca.voicetuner

/**
 * 润色 / 翻译用的 LLM 厂商预设。
 * 「思考档位」的参数形状沿用闪译（SpeedTrans）已核对的结论，不重造轮子：
 *  - DashScope(百炼)：enable_thinking
 *  - DeepSeek：thinking{type} ＋ reasoning_effort
 *  - 豆包(火山方舟)：thinking{type}（disabled/auto/enabled）
 *  - GLM(智谱) / Kimi(月之暗面)：thinking{type}（disabled/enabled）
 *  - OpenAI：reasoning_effort
 * levels 里的 value 就是各家的真实档位值；空 = 该家没有思考档位/自定义。
 */
object LlmPresets {

    data class P(
        val id: String,
        val label: String,
        val url: String,
        val models: List<String>,
        val keys: List<String>,                 // 按 baseUrl 里的片段识别（忽略大小写）
        val levels: List<Pair<String, String>>  // (显示名, 值)
    )

    val all = listOf(
        P(
            "qwen", "千问 · 百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            listOf("qwen-mt-plus", "qwen-mt-flash", "qwen3.8-flash", "qwen3.8-max"),
            listOf("dashscope", "aliyun"),
            listOf("关" to "off", "开" to "on")
        ),
        P(
            "deepseek", "DeepSeek", "https://api.deepseek.com/v1",
            listOf("deepseek-v4.1-flash-expires-on-0910", "deepseek-flash", "deepseek-v4-pro"),
            listOf("deepseek"),
            listOf("关" to "off", "低" to "low", "高" to "high", "极高" to "max")
        ),
        P(
            "doubao", "豆包 · 火山方舟", "https://ark.cn-beijing.volces.com/api/v3",
            listOf("doubao-seed-2-1-turbo", "doubao-seed-2-1-pro"),
            listOf("volces.com"),
            listOf("关" to "off", "自动" to "auto", "开" to "on")
        ),
        P(
            "glm", "GLM · 智谱", "https://open.bigmodel.cn/api/paas/v4",
            listOf("glm-5.3-flash", "glm-5.3", "glm-4.5-flash"),
            listOf("bigmodel.cn"),
            listOf("关" to "off", "低" to "low", "高" to "high", "极高" to "max")
        ),
        P(
            "kimi", "Kimi · 月之暗面", "https://api.moonshot.cn/v1",
            listOf("kimi-k3", "kimi-k2.7-code", "kimi-k2.6"),
            listOf("moonshot"),
            listOf("关" to "off", "开" to "on")
        ),
        P(
            "openai", "ChatGPT · OpenAI", "https://api.openai.com/v1",
            listOf("gpt-5.2", "gpt-5.2-mini", "gpt-5"),
            listOf("openai.com"),
            listOf("低" to "min", "中" to "mid", "高" to "max")
        ),
        P("custom", "自定义", "", emptyList(), emptyList(), emptyList())
    )

    fun match(url: String): P? = all.firstOrNull { p -> p.keys.any { url.contains(it, true) } }
}
