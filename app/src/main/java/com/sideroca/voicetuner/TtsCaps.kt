package com.sideroca.voicetuner

/**
 * **模型级**参数能力表（2026-10-15 新建）。
 *
 * 为什么要有它：以前"参数能不能用"只在**百炼**做到逐模型，其余十家是**按形态(shape)一刀切**
 * （`fish` 就全按 fish 判）——用户指出"每个厂商又有不同系列的模型"，这不对。
 *
 * 本表按 **(厂商, 模型)** 精确声明参数集合；查不到的再退到 **厂商级**，最后才回退老逻辑
 * （`TtsModels.supports`），所以**不会比现状更差**。
 *
 * 参数键与界面一致：rate(语速) / pitch(音调) / volume(音量) / seed(种子) /
 * instruction(情绪·风格指令) / langhints(语言提示) / hotfix(纠错表) / extra(额外参数) / ssml
 *
 * ⚠️ 每行都标了出处；`SRC_TODO` = **本轮没查到官方原文**（沿用现状，不是已核实）。
 */
object TtsCaps {

    private const val SRC_ALI = "阿里云百炼文档 + 本仓库既有核对结论"
    private const val SRC_OPENAI = "OpenAI 文档 audio/speech：gpt-4o-mini-tts 支持 instructions；speed 0.25~4"
    private const val SRC_11L = "ElevenLabs 文档 Models/TTS：**v3 不支持 speed**；audio tags 只在 v3/v4；SSML break 只在 v2 系"
    private const val SRC_MM = "MiniMax 文档 t2a_v2：voice_setting{speed,vol,pitch} + emotion + pronunciation_dict"
    private const val SRC_FISH = "Fish Audio 文档 TTS：prosody.speed 0.5~2 + volume；s1 用圆括号情绪标签，s2 系用方括号"
    private const val SRC_GROQ = "Groq 文档 TTS/Orpheus：english 支持 vocal directions，arabic-saudi 不支持"
    private const val SRC_GEMINI = "Gemini-TTS 文档：走 prompt/style + inline tags，无数值语速/音调"
    private const val SRC_TODO = "待核：本轮未查到官方原文，沿用现状"

    private fun p(vararg s: String) = s.toSet()

    private const val RATE = "rate"; private const val PITCH = "pitch"; private const val VOL = "volume"
    private const val SEED = "seed"; private const val INSTR = "instruction"; private const val LANG = "langhints"
    private const val HOT = "hotfix"; private const val EXTRA = "extra"; private const val SSML = "ssml"

    /** (厂商, 模型) → 支持的参数 */
    private val byModel: Map<String, Set<String>> = mapOf(
        // 阿里云百炼：ws 系全套；http 系只有语言提示，Instruct 版才有风格指令
        "aliyun-bailian/cosyvoice-v3.5-plus" to p(RATE, PITCH, VOL, SEED, INSTR, HOT, EXTRA, SSML, LANG),
        "aliyun-bailian/cosyvoice-v3-flash" to p(RATE, PITCH, VOL, SEED, INSTR, HOT, EXTRA, SSML, LANG),
        "aliyun-bailian/qwen-audio-3.1-tts-flash" to p(RATE, PITCH, VOL, SEED, INSTR, HOT, EXTRA, SSML, LANG),
        "aliyun-bailian/qwen-audio-3.0-tts-plus" to p(RATE, PITCH, VOL, SEED, INSTR, HOT, EXTRA, SSML, LANG),
        "aliyun-bailian/qwen3-tts-flash" to p(LANG, EXTRA),
        "aliyun-bailian/qwen3-tts-instruct-flash" to p(LANG, INSTR, EXTRA),
        "aliyun-bailian/qwen-tts-latest" to p(LANG, EXTRA),
        "aliyun-bailian/qwen-tts" to p(LANG, EXTRA),

        // OpenAI
        "openai/gpt-4o-mini-tts" to p(INSTR, RATE, EXTRA),

        // ElevenLabs（v3 无 speed）
        "elevenlabs/eleven_v3" to p(INSTR, SEED, EXTRA),
        "elevenlabs/eleven_v4" to p(INSTR, RATE, SEED, EXTRA),
        "elevenlabs/eleven_v4_turbo" to p(INSTR, RATE, SEED, EXTRA),

        // MiniMax
        "minimax/speech-2.8-hd" to p(RATE, VOL, PITCH, INSTR, EXTRA),
        "minimax/speech-2.8-turbo" to p(RATE, VOL, PITCH, INSTR, EXTRA),
        "minimax/speech-2.6-hd" to p(RATE, VOL, PITCH, INSTR, EXTRA),
        "minimax/speech-2.6-turbo" to p(RATE, VOL, PITCH, INSTR, EXTRA),

        // Fish Audio
        "fish/s2.1-pro-free" to p(RATE, VOL, INSTR, EXTRA),
        "fish/s2.1-pro" to p(RATE, VOL, INSTR, EXTRA),
        "fish/s2-pro" to p(RATE, VOL, INSTR, EXTRA),
        "fish/s1" to p(RATE, VOL, INSTR, EXTRA),

        // Groq（只有 orpheus 两个；只有 english 支持 vocal directions）
        "groq/canopylabs/orpheus-v1-english" to p(INSTR, EXTRA),
        "groq/canopylabs/orpheus-arabic-saudi" to p(EXTRA),

        // Gemini（prompt/style + inline tags）
        "gemini/gemini-2.5-flash-preview-tts" to p(INSTR, EXTRA),
        "gemini/gemini-2.5-pro-preview-tts" to p(INSTR, EXTRA)
    )

    /** 厂商级兜底（模型不在上表时用）——只写核实到厂商级的结论 */
    private val byVendor: Map<String, Set<String>> = mapOf(
        "openai" to p(INSTR, RATE, EXTRA),
        "groq" to p(EXTRA),
        "elevenlabs" to p(RATE, SEED, EXTRA),
        "minimax" to p(RATE, VOL, PITCH, INSTR, EXTRA),
        "fish" to p(RATE, VOL, INSTR, EXTRA),
        "gemini" to p(INSTR, EXTRA)
    )

    /** 逐家出处（给人看，也写进 README） */
    val sources: List<Pair<String, String>> = listOf(
        "阿里云百炼" to SRC_ALI,
        "OpenAI" to SRC_OPENAI,
        "ElevenLabs" to SRC_11L,
        "MiniMax" to SRC_MM,
        "Fish Audio" to SRC_FISH,
        "Groq" to SRC_GROQ,
        "Gemini" to SRC_GEMINI,
        "xAI / Step / MiMo / IndexTTS / 系统 / 自定义" to SRC_TODO
    )

    /** 该 (厂商,模型) 的已核能力集；null = 本表没有 → 调用方回退老逻辑 */
    fun of(providerId: String?, modelId: String?): Set<String>? {
        if (providerId.isNullOrBlank()) return null
        val m = modelId?.trim().orEmpty()
        if (m.isNotBlank()) {
            byModel["$providerId/$m"]?.let { return it }
            val base = m.replace(Regex("-\\d{4}-\\d{2}-\\d{2}$"), "")   // 带日期的快照
            if (base != m) byModel["$providerId/$base"]?.let { return it }
        }
        return byVendor[providerId]
    }

    /** 本表有没有这家/这个模型的已核数据 */
    fun verified(providerId: String?, modelId: String?): Boolean = of(providerId, modelId) != null
}
