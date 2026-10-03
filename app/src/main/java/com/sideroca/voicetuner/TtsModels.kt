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
            // 自定义渠道：端点由用户填，seed 也放行（服务支持就用；不支持会被忽略）
            if (prov?.id == "custom" && param == "seed") return true
            return when (prov?.shape) {
                // MiniMax：voice_setting 支持 speed/pitch/vol/**emotion**（emotion 由「情绪/风格指令」映射），无 seed
                "minimax" -> param == "rate" || param == "pitch" || param == "volume" || param == "instruction"
                "system" -> param == "rate" || param == "pitch"                          // setSpeechRate/setPitch
                // Fish：prosody(speed/volume) + [方括号]指令 + 额外参数；官方**没有 seed**，但留口子（填了才发）
                "fish" -> param == "rate" || param == "volume" || param == "extra" ||
                    param == "instruction" || param == "seed"
                "elevenlabs" -> param == "rate" || param == "seed"                        // voice_settings.speed + 顶层 seed
                "mimo" -> param == "seed" || param == "instruction"                       // 走 chat/completions：标准 seed + user 指令
                "openai", "xai" -> param == "instruction" || param == "rate" || param == "extra"
                else -> false
            }
        }
        val m = byId(modelId) ?: return true          // 未知模型（复刻 id 等）不误伤
        return when (param) {
            // 数值参数只有 ws 系（CosyVoice / Qwen-Audio-TTS）吃
            "rate", "pitch", "volume", "seed", "hotfix", "extra", "ssml" -> m.transport == "ws"
            // 风格指令：ws 系都支持；http 系只有 Instruct 版模型支持（别给普通 Qwen-TTS 放行，会被拒）
            "instruction" -> m.transport == "ws" || m.family.contains("Instruct", ignoreCase = true)
            // 其余（含 langhints）：http 系的 Qwen-TTS 也支持 language_type，别误伤
            else -> true
        }
    }

    /** 参数名 → 界面上的叫法（用于自动生成置灰说明） */
    private val paramNames = listOf(
        "rate" to "语速", "pitch" to "音调", "volume" to "音量", "seed" to "种子",
        "instruction" to "风格指令", "langhints" to "语言提示",
        "hotfix" to "纠错", "extra" to "额外参数", "ssml" to "SSML"
    )

    /**
     * 置灰说明：**由 [supports] 自动算出来**，只写"不支持什么"（不写长篇解释）。
     * 以后接新厂商只需改上面的表，这里不用动。
     */
    fun unsupportedNote(providerId: String?, modelId: String?): String {
        val no = ArrayList<String>()
        for ((k, label) in paramNames) if (!supports(providerId, modelId, k)) no.add(label)
        if (TtsProviders.byId(providerId)?.canClone != true) no.add("声音复刻")
        if (no.isEmpty()) return ""
        val who = TtsProviders.byId(providerId)?.name ?: "该厂商"
        val m = byId(modelId)
        val head = if (who == "阿里云百炼" && m != null) "当前模型" else who
        val list = if (no.size <= 6) no.joinToString("、")
                   else no.take(6).joinToString("、") + " 等 " + no.size + " 项"
        return head + " 不支持：" + list + "（已置灰）"
    }
}
