package com.sideroca.voicetuner

/**
 * 阿里云百炼（DashScope）TTS 模型目录 —— P0：把百炼内部的家族扩起来。
 * 每系列取**最近两代**（稳定版 + 最新快照）。
 *
 * transport：
 *  - "ws"   → SpeechSynthesizer WebSocket（CosyVoice / Qwen-Audio-TTS，现有实现）
 *  - "http" → multimodal-generation HTTP 一次返回 base64 音频（Qwen-TTS / Qwen3-TTS-*）
 */
object TtsModels {

    data class M(val id: String, val label: String, val family: String, val transport: String)

    val all: List<M> = listOf(
        M("cosyvoice-v3.5-plus", "现用 · 复刻音色", "CosyVoice", "ws"),
        M("cosyvoice-v3-flash", "快速版", "CosyVoice", "ws"),

        M("qwen3-tts-flash", "稳定版（= 2025-11-27）", "Qwen3-TTS-Flash", "http"),
        M("qwen3-tts-flash-2025-11-27", "快照 2025-11-27", "Qwen3-TTS-Flash", "http"),

        M("qwen3-tts-instruct-flash", "支持 instructions 风格指令", "Qwen3-TTS-Instruct", "http"),
        M("qwen3-tts-instruct-flash-2026-01-26", "快照 2026-01-26", "Qwen3-TTS-Instruct", "http"),

        M("qwen-tts-latest", "最新（= 2025-05-22）", "Qwen-TTS", "http"),
        M("qwen-tts", "稳定（= 2025-04-10）", "Qwen-TTS", "http"),

        M("qwen-audio-3.1-tts-flash", "3.1 快速版", "Qwen-Audio-TTS", "ws"),
        M("qwen-audio-3.0-tts-plus", "3.0 高质版", "Qwen-Audio-TTS", "ws")
    )

    fun byId(id: String?): M? = if (id.isNullOrBlank()) null else all.firstOrNull { it.id == id }

    /** 自动补全：空串给全部；否则按 模型 id / 系列名 / 说明 做前缀或包含匹配 */
    fun match(prefix: String): List<M> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return all
        return all.filter {
            it.id.lowercase().startsWith(p) ||
                it.family.lowercase().contains(p) ||
                it.label.lowercase().contains(p)
        }
    }

    /** 下拉里显示的文字：id + 说明 */
    fun display(m: M): String = m.id + "    " + m.family + " · " + m.label

    /** 从下拉文字里取回模型 id */
    fun idOf(text: String): String = text.substringBefore("    ").trim()

    // ---------- P0.5：参数适用性（界面据此置灰；后续升级为 ParamSpec 全量驱动） ----------

    /**
     * 该项参数是否适用（判定依据 = 厂商 + 模型，均在数据里，不写死在界面）。
     * - 非百炼厂商（OpenAI 系 / 自定义渠道）：只吃 文本 / 音色 / 模型（+ 可选风格指令）
     * - 百炼：千问系走 HTTP，只有 text / voice / language_type[/instructions]；CosyVoice 与 Qwen-Audio-TTS 走 ws，参数齐全
     */
    fun supports(providerId: String?, modelId: String?, param: String): Boolean {
        val prov = TtsProviders.byId(providerId)
        val viaBailian = prov == null || prov.shape == "dashscope"
        if (!viaBailian) {
            return when (prov?.shape) {
                "minimax" -> param == "rate" || param == "pitch" || param == "volume"   // voice_setting
                "system" -> param == "rate" || param == "pitch"                          // setSpeechRate/setPitch
                "openai", "xai" -> param == "instruction" || param == "rate"             // instructions + speed
                else -> false
            }
        }
        val m = byId(modelId) ?: return true          // 未知模型（复刻 id 等）不误伤
        return when (param) {
            "rate", "pitch", "volume", "seed", "hotfix", "extra", "ssml", "langhints" ->
                m.transport == "ws"
            else -> true
        }
    }

    /** 不适用时的说明文案（空串 = 全部适用） */
    fun unsupportedNote(providerId: String?, modelId: String?): String {
        val prov = TtsProviders.byId(providerId)
        val viaBailian = prov == null || prov.shape == "dashscope"
        if (!viaBailian) {
            val mini = prov?.shape == "minimax"
            return if (mini)
                "「MiniMax」支持 文本 / 音色 / 模型 + 语速 / 音调 / 音量；种子、SSML、高级参数不适用（已置灰）"
            else
                "「" + (prov?.name ?: "该厂商") + "」只吃 文本 / 音色 / 模型（可选风格指令）—— " +
                    "语速、音调、音量、种子、SSML 与高级参数都不适用，已置灰"
        }
        val m = byId(modelId) ?: return ""
        return if (m.transport == "http")
            "当前模型（" + m.family + "）不支持 语速 / 音调 / 音量 / 种子 —— 想调风格请用「风格指令」（instructions）"
        else ""
    }
}
