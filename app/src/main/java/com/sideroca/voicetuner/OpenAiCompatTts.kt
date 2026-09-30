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
        cb: SynthCallback
    ): Cancellable {
        var cancelled = false
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()

                val url: String
                val body: JSONObject
                if (provider.shape == "xai") {
                    url = provider.baseUrl.trimEnd('/') + "/tts"
                    body = JSONObject().apply {
                        put("text", text)
                        put("voice_id", voice.ifBlank { "eve" })
                        put("language", "auto")
                    }
                } else {
                    url = provider.baseUrl.trimEnd('/') + "/audio/speech"
                    body = JSONObject().apply {
                        put("model", model)
                        put("input", text)
                        put("voice", voice.ifBlank { "alloy" })
                        put("response_format", "mp3")
                        if (!instruction.isNullOrBlank()) put("instructions", instruction)
                    }
                }

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer " + apiKey)
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (!cancelled) cb.onError(provider.name + " 返回 " + code + "：" + err.take(300))
                    return@Thread
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                if (cancelled) return@Thread
                cb.onProgress(bytes.size)
                cb.onFinished(bytes)
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
}
