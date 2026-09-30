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
        cfg: TtsProviders.Cfg = TtsProviders.Cfg()
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
                        if (!instruction.isNullOrBlank()) put("instructions", instruction)
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

                // 返回形式：binary（直接是音频）/ base64:<json路径> / url（JSON 里给音频地址）
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
                    else -> raw
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
}
