package com.sideroca.voicetuner

/**
 * 厂商目录（P1）：内置几家 + 每家"形态(shape)"，形态决定用哪个客户端。
 *
 * shape:
 *  - "dashscope" → 阿里云百炼（内部再按模型分 ws / http，见 DashScopeClient + QwenTtsClient）
 *  - "openai"    → OpenAI 兼容：POST {base}/audio/speech，Bearer 鉴权，直接返回音频二进制
 *  - "xai"       → xAI：POST {base}/tts，参数名不同（text / voice_id / language）
 *
 * 模型清单只放**最近两代**（分系列则每系列两代）；未核实的用注释标明。
 */
object TtsProviders {

    data class P(
        val id: String,
        val name: String,
        val baseUrl: String,
        val shape: String,
        val keyHint: String,
        val models: List<String>
    )

    val all: List<P> = listOf(
        P(
            "aliyun-bailian", "阿里云百炼", "https://dashscope.aliyuncs.com", "dashscope", "sk-ws-…",
            TtsModels.all.map { it.id }
        ),
        P(
            "openai", "OpenAI", "https://api.openai.com/v1", "openai", "sk-…",
            listOf("gpt-4o-mini-tts")          // 官方 models 页已核实；tts-1 / tts-1-hd 属旧代
        ),
        P(
            "groq", "Groq", "https://api.groq.com/openai/v1", "openai", "gsk_…",
            listOf("canopylabs/orpheus-v1-english", "playai-tts")
        ),
        P(
            "step", "Step 阶跃星辰", "https://api.stepfun.com/v1", "openai", "…",
            listOf("stepaudio-3-tts")          // 官网 curl 已核实
        ),
        P(
            "xai", "xAI", "https://api.x.ai/v1", "xai", "xai-…",
            listOf("grok-tts")                 // 无独立 TTS 模型 id，随 grok-4.20 系列走
        ),

        P(
            "elevenlabs", "ElevenLabs", "https://api.elevenlabs.io", "elevenlabs", "xi-…",
            listOf("eleven_v4", "eleven_v4_turbo", "eleven_v3")      // 官方 openapi 已核实（最近两代）
        ),
        P(
            "minimax", "MiniMax", "https://api.minimax.io/v1", "minimax", "eyJ…",
            listOf("speech-2.8-hd", "speech-2.8-turbo", "speech-2.6-hd", "speech-2.6-turbo")
        ),
        P(
            "fish", "Fish Audio", "https://api.fish.audio", "fish", "…",
            listOf("s1")
        ),

        P(
            "gemini", "Gemini", "https://generativelanguage.googleapis.com", "gemini", "AIza…",
            listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts")
        ),
        P(
            "mimo", "MiMo 小米", "https://api.mimo.mi.com/v1", "mimo", "MIMO_API_KEY",
            listOf("MiMo-V2.5-TTS")          // 文档已核实模型名；端点与返回格式待真机验证
        ),
        P(
            "system", "系统 TTS（本地）", "", "system", "（无需 Key）",
            listOf("系统引擎")
        ),

        // 自定义渠道：Base URL 自己填，默认按 OpenAI 兼容形态（POST {base}/audio/speech）
        // 模型名自由输入（不预设清单）—— 做 TTS 的厂商多，随时可能出新家
        P(
            "custom", "自定义渠道", "", "openai", "你的服务商 Key",
            emptyList()
        )
    )

    /** 自定义渠道的请求配置（留空则按形态默认） */
    data class Cfg(
        val path: String = "",                 // 端点路径，空 = openai→/audio/speech，xai→/tts
        val auth: String = "",                 // 鉴权头模板，空 = "Authorization: Bearer {key}"
        val resp: String = "binary"            // binary | base64:<json路径> | url
    )

    fun byId(id: String?): P? = if (id.isNullOrBlank()) null else all.firstOrNull { it.id == id }

    /** 自动补全：空串给全部；否则按 名称 / id 前缀或包含匹配 */
    fun match(prefix: String): List<P> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return all
        return all.filter { it.name.lowercase().contains(p) || it.id.lowercase().startsWith(p) }
    }

    fun display(p: P): String = p.name + "    " + p.id

    fun idOf(text: String): String = text.substringBefore("    ").trim()

    /** 该厂家的模型清单（自动补全用） */
    fun modelsOf(providerId: String?): List<String> = byId(providerId)?.models ?: emptyList()

    /** 某厂商的模型下拉项文字：百炼带「系列 · 说明」，其他家只有 id */
    fun modelDisplays(providerId: String?): List<String> {
        val p = byId(providerId) ?: return emptyList()
        return if (p.shape == "dashscope")
            TtsModels.all.filter { it.id in p.models }.map { TtsModels.display(it) }
        else p.models
    }

    /** 从下拉文字取回模型 id（两种形态通用） */
    fun modelIdOf(text: String): String =
        if (text.contains("    ")) text.substringBefore("    ").trim() else text.trim()
}
