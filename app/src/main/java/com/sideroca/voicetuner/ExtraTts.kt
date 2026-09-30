package com.sideroca.voicetuner

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * P2：自有协议三家的客户端（ElevenLabs / MiniMax / Fish Audio）。
 * 端点与字段均由 curl 官方文档核准（2026-10-01）：
 *  - ElevenLabs: POST {base}/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128，头 xi-api-key
 *  - MiniMax   : POST {base}/t2a_v2?GroupId={groupId}，头 Authorization: Bearer，返回 **hex** 音频
 *  - Fish Audio: POST {base}/v1/tts，头 Authorization: Bearer，body 含 reference_id / format
 */
object ExtraTts {

    // ------------------------------------------------------------------ ElevenLabs
    fun eleven(
        baseUrl: String, apiKey: String, voice: String, model: String,
        text: String, stability: Double, similarity: Double, cb: SynthCallback
    ): Cancellable {
        val v = voice.ifBlank { "21m00Tcm4TlvDq8ikWAM" }        // 官方公开示例音色 Rachel
        val url = baseUrl.trimEnd('/') + "/v1/text-to-speech/" + v + "?output_format=mp3_44100_128"
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", model.ifBlank { "eleven_multilingual_v2" })
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarity)
            })
        }
        return post(url, mapOf("xi-api-key" to apiKey), body, cb)
    }

    // ------------------------------------------------------------------ MiniMax
    fun minimax(
        baseUrl: String, apiKey: String, groupId: String, model: String, voice: String,
        text: String, speed: Double, pitch: Double, volume: Int, cb: SynthCallback
    ): Cancellable {
        val g = if (groupId.isBlank()) "" else "?GroupId=" + groupId
        val url = baseUrl.trimEnd('/') + "/t2a_v2" + g
        val body = JSONObject().apply {
            put("model", model.ifBlank { "speech-2.8-hd" })
            put("text", text)
            put("stream", false)
            put("voice_setting", JSONObject().apply {
                put("voice_id", voice.ifBlank { "male-qn-qingse" })
                put("speed", speed)                 // 0.5~2
                put("pitch", (pitch - 1.0) * 12.0)  // 界面是 0.5~1.5 倍率，MiniMax 是 -12~12 半音
                put("vol", volume)                  // 0~100
            })
            put("audio_setting", JSONObject().apply {
                put("format", "mp3")
                put("sample_rate", 32000)
            })
        }
        return post(url, mapOf("Authorization" to "Bearer " + apiKey), body, cb, hexIn = "data.audio")
    }

    // ------------------------------------------------------------------ Fish Audio
    fun fish(
        baseUrl: String, apiKey: String, voice: String, text: String, cb: SynthCallback
    ): Cancellable {
        val url = baseUrl.trimEnd('/') + "/v1/tts"
        val body = JSONObject().apply {
            put("text", text)
            put("format", "mp3")
            if (voice.isNotBlank()) put("reference_id", voice)
        }
        return post(url, mapOf("Authorization" to "Bearer " + apiKey), body, cb)
    }

    // ------------------------------------------------------------------ 公共 POST
    private fun post(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        cb: SynthCallback,
        hexIn: String? = null
    ): Cancellable {
        var cancelled = false
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (!cancelled) cb.onError("返回 " + code + "：" + err.take(300))
                    return@Thread
                }
                val raw = conn.inputStream.use { it.readBytes() }
                if (cancelled) return@Thread
                val audio: ByteArray = if (hexIn != null) {
                    val hex = jsonPath(String(raw, Charsets.UTF_8), hexIn)
                    if (hex.isBlank()) {
                        cb.onError("返回里没找到音频（路径 " + hexIn + "）")
                        return@Thread
                    }
                    hexToBytes(hex)
                } else raw
                cb.onProgress(audio.size)
                cb.onFinished(audio)
            } catch (e: Exception) {
                if (!cancelled) cb.onError("调用失败：" + (e.message ?: e.javaClass.simpleName))
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

    /** MiniMax 返回的音频是十六进制字符串 */
    private fun hexToBytes(hex: String): ByteArray {
        val s = if (hex.length % 2 == 0) hex else hex.dropLast(1)
        val out = ByteArray(s.length / 2)
        var i = 0
        while (i < s.length) {
            out[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
