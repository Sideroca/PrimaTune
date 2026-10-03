package com.sideroca.voicetuner

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容 TTS 客户端（一次覆盖 OpenAI / Groq / Step）+ xAI 的分支。
 * - openai 形态：POST {base}/audio/speech  {model, input, voice, response_format} → 音频二进制
 * - xai 形态   ：POST {base}/tts          {text, voice_id, language}            → 音频二进制
 *
 * 注意：这几家**没有**语速/音调/音量/种子这类参数（OpenAI 系有 instructions），
 * 所以界面要靠 TtsModels.supports() 置灰，不硬塞假值。
 */
object OpenAiCompatTts {

    fun synthesize(
        provider: TtsProviders.P,
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        instruction: String?,
        cb: SynthCallback,
        cfg: TtsProviders.Cfg = TtsProviders.Cfg(),
        rate: Double = 1.0,                      // 语速：OpenAI 兼容端点是 speed
        volume: Int = 50,                        // 音量：目前只有阶跃用（扩展参数 volume）
        seed: Int? = null                        // 自定义渠道：填了才发（服务认就用）
    ): Cancellable {
        var cancelled = false
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()

                val url: String
                val body: JSONObject
                if (provider.shape == "xai") {
                    url = provider.baseUrl.trimEnd('/') + cfg.path.ifBlank { "/tts" }
                    body = JSONObject().apply {
                        put("text", text)
                        put("voice_id", voice.ifBlank { "eve" })
                        put("language", "auto")
                    }
                } else {
                    url = provider.baseUrl.trimEnd('/') + cfg.path.ifBlank { "/audio/speech" }
                    body = JSONObject().apply {
                        put("model", model)
                        put("input", text)
                        put("voice", voice.ifBlank { "alloy" })
                        put("response_format", "wav")     // 优先 wav；不支持的家会报错，届时再按需改 mp3
                        // 风格指令：OpenAI 系叫 instructions；阶跃（StepFun）叫 instruction
                        if (!instruction.isNullOrBlank()) {
                            if (provider.id == "step") put("instruction", instruction)
                            else put("instructions", instruction)
                        }
                        if (kotlin.math.abs(rate - 1.0) > 0.001) put("speed", rate)
                        // 阶跃的音量在扩展参数里（1.0 = 正常）；50 是默认值，没动就不发
                        if (provider.id == "step" && volume != 50) {
                            put("volume", (volume.coerceIn(0, 100) / 50.0).coerceIn(0.1, 3.0))
                        }
                        seed?.let { put("seed", it) }
                        ExtraTts.mergeInto(this, cfg.extra)
                    }
                }

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    // 鉴权头可自定义（有的服务用 x-api-key 之类）；{key} 会被替换成实际 Key
                    val auth = cfg.auth.ifBlank { "Authorization: Bearer {key}" }.replace("{key}", apiKey)
                    setRequestProperty(auth.substringBefore(":").trim(), auth.substringAfter(":").trim())
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (!cancelled) cb.onError(provider.name + " 返回 " + code + "：" + err.take(300))
                    return@Thread
                }
                val raw = conn.inputStream.use { it.readBytes() }
                if (cancelled) return@Thread

                // 返回形式由程序自动识别（界面上不再让用户选）：①直接是音频 ②JSON 里 base64 ③JSON 里给下载地址
                val audio: ByteArray = when {
                    cfg.resp == "url" -> {
                        val u = firstUrl(String(raw, Charsets.UTF_8))
                        if (u.isNullOrBlank()) {
                            cb.onError("返回里没找到音频 URL")
                            return@Thread
                        }
                        val c2 = (java.net.URL(u).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 90_000
                        }
                        c2.inputStream.use { it.readBytes() }
                    }
                    cfg.resp.startsWith("base64") -> {
                        val jp = cfg.resp.substringAfter(":", "").ifBlank { "output.audio.data" }
                        val b64 = jsonPath(String(raw, Charsets.UTF_8), jp)
                        if (b64.isBlank()) {
                            cb.onError("返回里没找到 base64 音频（路径 " + jp + "）")
                            return@Thread
                        }
                        java.util.Base64.getDecoder().decode(b64)
                    }
                    else -> {
                        // 不是音频容器 → 看看是不是 JSON，再决定取 base64 还是取 URL
                        val text = String(raw, Charsets.UTF_8)
                        val u = firstUrl(text)
                        val b64 = longestBase64(text)
                        when {
                            b64 != null -> java.util.Base64.getDecoder().decode(b64)
                            u != null -> {
                                val c3 = (java.net.URL(u).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 15_000
                                    readTimeout = 90_000
                                }
                                c3.inputStream.use { it.readBytes() }
                            }
                            else -> raw
                        }
                    }
                }
                cb.onProgress(audio.size)
                cb.onFinished(audio)
            } catch (e: Exception) {
                if (!cancelled) cb.onError(provider.name + " 调用失败：" + (e.message ?: e.javaClass.simpleName))
            }
        }
        th.start()
        return object : Cancellable {
            override fun cancel() {
                cancelled = true
                th.interrupt()
            }
        }
    }

    /** 按 "a.b.c" 取 JSON 里的字符串值（找不到返回空串） */
    private fun jsonPath(text: String, path: String): String {
        var cur: Any = try {
            JSONObject(text)
        } catch (e: Exception) {
            return ""
        }
        for (seg in path.split('.')) {
            cur = (cur as? JSONObject)?.opt(seg) ?: return ""
        }
        return cur as? String ?: ""
    }

    /** 递归找第一个 http(s) 开头的字符串（用于返回形式=url） */
    private fun firstUrl(text: String): String? {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        return firstUrl(o)
    }

    private fun firstUrl(o: JSONObject): String? {
        for (k in o.keys()) {
            when (val v = o.opt(k)) {
                is String -> if (v.startsWith("http")) return v
                is JSONObject -> firstUrl(v)?.let { return it }
            }
        }
        return null
    }

    /** 从 JSON 文本里找最长的一段 base64（有些厂商把音频塞在某个字段里） */
    private fun longestBase64(text: String): String? {
        val re = Regex("[A-Za-z0-9+/]{2000,}={0,2}")
        var best = ""
        for (m in re.findAll(text)) if (m.value.length > best.length) best = m.value
        return if (best.isEmpty()) null else best
    }
}
