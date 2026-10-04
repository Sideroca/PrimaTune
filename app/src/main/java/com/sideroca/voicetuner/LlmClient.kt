package com.sideroca.voicetuner

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 提示词助手：给一个 **OpenAI 兼容**的 LLM 发一条请求，
 * 让它按「待合成文本 + 音色身份 + 用户补充」给出：**该用哪家 TTS 厂商 + 自然语言风格/分割指令 + 纠错表**。
 *
 * 提示词是"系统提示词"，设置页里**展示出来、可改**；用户还能写一段补充说明。
 */
object LlmClient {

    class Cancelled { @Volatile var cancelled = false }

    /**
     * 默认系统提示词（可被设置里覆盖）。
     * 刻意**写短**：相信模型的遵循能力；提示词越长越容易互相打架、也越可能与"待合成文本"抢注意力。
     * 「当前厂商 + 它接受的参数」由 App 在 user 消息里**自动附带**（见 MainActivity.smartFill），
     * 所以这里不再让模型去猜 provider。
     */
    val DEFAULT_PROMPT: String = """
你是语音合成的风格导演。你会收到当前 TTS 厂商及其支持的参数、音色资料、可选补充说明，以及待合成文本。
请写**一句简短的自然语言指令**，告诉 TTS 用什么语气、情绪、语速和停顿来念这段文本；长句用标点断出呼吸感。
再给一张纠错表，纠正专名、多音字、生僻字的读音；没有就给空表。
只输出 JSON：
{"instruction":"简短的自然语言风格指令","hot_fix":{"replace":[{"错词":"正词"}]}}
""".trimIndent()

    /** 润色用的默认系统提示词 */
    val DEFAULT_POLISH_PROMPT: String = """
你是中文文稿润色师。把用户给的内容润色成**适合朗读**的稿子：
- 保留原意与全部信息，不要增删情节，不要加标题或解释；
- 口语化、断句自然，长句拆成短句；标点用于表达停顿与轻重；
- 去掉重复口水词、语病与拗口的书面语；数字/专名保留原样；
- 只输出润色后的正文，不要任何说明或引号。
""".trimIndent()

    /** 翻译用的默认系统提示词；{target} 会被替换成目标语言 */
    val DEFAULT_TRANS_PROMPT: String =
        "把用户给的内容翻译成 {target}。只输出译文，不要输出任何其他内容。"

    /** 发送：system = 系统提示词，user = 本次上下文；成功回调 (文本, null) */
    fun ask(
        baseUrl: String, key: String, model: String, system: String, user: String,
        maxTokens: Int = 0, temperature: Double = -1.0, level: String = "",
        onDone: (String?, String?) -> Unit
    ): Cancelled {
        val c = Cancelled()
        Thread {
            try {
                val url = baseUrl.trimEnd('/') + "/chat/completions"
                val body = JSONObject().apply {
                    put("model", model)
                    if (temperature >= 0.0) put("temperature", temperature) else put("temperature", 0.3)
                    // token 预算：老模型用 max_tokens；OpenAI 新系只认 max_completion_tokens —— 400 时自动回退
                    if (maxTokens > 0) put("max_tokens", maxTokens)
                    // 思考档位：按厂商/域名映射成各家真实参数（结论沿用闪译已核对的结果）
                    if (level.isNotBlank()) {
                        val u = baseUrl.lowercase()
                        when {
                            u.contains("dashscope") || u.contains("aliyun") ->
                                put("enable_thinking", level != "off")
                            u.contains("bigmodel.cn") || u.contains("moonshot") ->
                                put("thinking", JSONObject().put("type", if (level == "off") "disabled" else "enabled"))
                            u.contains("volces.com") ->
                                put("thinking", JSONObject().put("type", when (level) {
                                    "off" -> "disabled"; "on" -> "enabled"; else -> "auto"
                                }))
                            u.contains("deepseek") -> {
                                put("thinking", JSONObject().put("type", if (level == "off") "disabled" else "enabled"))
                                if (level != "off" && level != "on") put("reasoning_effort", level)
                            }
                            u.contains("openai.com") -> if (level != "off") put("reasoning_effort", level)
                        }
                    }
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", system) })
                        put(JSONObject().apply { put("role", "user"); put("content", user) })
                    })
                }
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 90_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer " + key)
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                var code = conn.responseCode
                var raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                // 容错：有的服务只认 max_completion_tokens（不认 max_tokens）→ 400 时换一个再试一次
                if (code == 400 && maxTokens > 0 && body.has("max_tokens")) {
                    body.remove("max_tokens")
                    body.put("max_completion_tokens", maxTokens)
                    val conn2 = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 90_000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer " + key)
                        setRequestProperty("Content-Type", "application/json")
                    }
                    conn2.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    code = conn2.responseCode
                    raw = (if (code in 200..299) conn2.inputStream else conn2.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (c.cancelled) return@Thread
                if (code !in 200..299) { onDone(null, "LLM 返回 " + code + "：" + raw.take(200)); return@Thread }
                val msg = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                if (msg.isBlank()) onDone(null, "LLM 没有返回内容") else onDone(msg, null)
            } catch (e: Exception) {
                if (!c.cancelled) onDone(null, e.message ?: "请求失败")
            }
        }.start()
        return c
    }
}
