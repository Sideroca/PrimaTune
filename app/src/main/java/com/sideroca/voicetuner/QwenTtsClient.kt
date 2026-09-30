package com.sideroca.voicetuner

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * 千问 TTS 系（Qwen-TTS / Qwen3-TTS-Flash / Qwen3-TTS-Instruct-Flash）的 HTTP 通道。
 * 端点：POST https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
 * 返回：output.audio.data（base64；可能是 WAV 容器，也可能是裸 PCM → 裸的自动套 44 字节 WAV 头）
 *
 * 注意：这几个模型**不支持** 语速/音调/音量/种子（官方只有 text/voice/language_type[/instructions]），
 * 所以这里只传文本与音色，其余参数由界面置灰说明，不硬塞假值。
 */
object QwenTtsClient {

    private const val EP =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"

    fun synthesize(req: SynthRequest, cb: SynthCallback): Cancellable {
        var cancelled = false
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()
                val input = JSONObject().apply {
                    put("text", req.text)
                    put("voice", req.voice.ifBlank { "Cherry" })
                    val hint = req.languageHints?.firstOrNull().orEmpty().lowercase()
                    put(
                        "language_type", when {
                            hint.startsWith("zh") -> "Chinese"
                            hint.startsWith("en") -> "English"
                            hint.startsWith("ja") -> "Japanese"
                            hint.startsWith("ko") -> "Korean"
                            else -> "Auto"
                        }
                    )
                    if (!req.instruction.isNullOrBlank()) {
                        put("instructions", req.instruction)
                        put("optimize_instructions", true)
                    }
                }
                val body = JSONObject().apply {
                    put("model", req.model)
                    put("input", input)
                }

                val conn = (URL(EP).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer " + req.apiKey)
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (cancelled) return@Thread
                if (code !in 200..299) {
                    cb.onError("百炼返回 " + code + "：" + text.take(300))
                    return@Thread
                }
                val b64 = JSONObject(text)
                    .optJSONObject("output")?.optJSONObject("audio")?.optString("data").orEmpty()
                if (b64.isEmpty()) {
                    cb.onError("没拿到音频数据：" + text.take(300))
                    return@Thread
                }
                var raw = Base64.getDecoder().decode(b64)
                cb.onProgress(raw.size)
                if (!(raw.size > 12 && raw[0] == 0x52.toByte() && raw[1] == 0x49.toByte())) {
                    raw = wrapWav24kMono(raw)      // 裸 PCM → 套 WAV 头
                }
                if (!cancelled) cb.onFinished(raw)
            } catch (e: Exception) {
                if (!cancelled) cb.onError("千问 TTS 调用失败：" + (e.message ?: e.javaClass.simpleName))
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

    /** 裸 PCM（24000Hz / 16bit / 单声道）→ 标准 44 字节 WAV 头 */
    private fun wrapWav24kMono(pcm: ByteArray): ByteArray {
        val rate = 24000
        val ch = 1
        val bits = 16
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun le(v: Int, n: Int) {
            for (i in 0 until n) out.write((v shr (8 * i)) and 0xFF)
        }
        out.write("RIFF".toByteArray()); le(36 + pcm.size, 4); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le(16, 4); le(1, 2); le(ch, 2); le(rate, 4)
        le(rate * ch * bits / 8, 4); le(ch * bits / 8, 2); le(bits, 2)
        out.write("data".toByteArray()); le(pcm.size, 4); out.write(pcm)
        return out.toByteArray()
    }
}
