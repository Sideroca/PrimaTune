package com.sideroca.voicetuner

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * P3：三家 —— 本地系统 TTS / Gemini / MiMo。
 *  - System TTS：Android 自带引擎，离线、免费；用 synthesizeToFile 落成 wav
 *  - Gemini    ：generateContent + responseModalities=[AUDIO]，返回 base64 **裸 PCM(24k/16bit/mono)** → 套 WAV 头
 *  - MiMo      ：messages 形状（官方文档：MiMo-V2.5-TTS，返回 BASE64）；**端点与返回格式待真机验证**
 */
object ExtraTts2 {

    // ------------------------------------------------------------ System TTS（本地）
    fun system(ctx: android.content.Context, text: String, rate: Double, pitch: Double, cb: SynthCallback): Cancellable {
        var cancelled = false
        var tts: TextToSpeech? = null
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()
                val ready = CountDownLatch(1)
                var ok = false
                val t = TextToSpeech(ctx) { st ->
                    ok = st == TextToSpeech.SUCCESS
                    ready.countDown()
                }
                tts = t
                if (!ready.await(8, TimeUnit.SECONDS) || !ok) {
                    cb.onError("系统 TTS 初始化失败（设备可能没装语音引擎）")
                    return@Thread
                }
                t.setSpeechRate(rate.toFloat())
                t.setPitch(pitch.toFloat())
                val f = File(ctx.cacheDir, "sys_tts_out.wav")
                if (f.exists()) f.delete()
                val done = CountDownLatch(1)
                var errMsg: String? = null
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        done.countDown()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        errMsg = "系统 TTS 合成失败"
                        done.countDown()
                    }
                })
                t.synthesizeToFile(text, Bundle(), f, "vt_sys")
                if (!done.await(90, TimeUnit.SECONDS)) {
                    cb.onError("系统 TTS 超时")
                    return@Thread
                }
                if (errMsg != null) {
                    cb.onError(errMsg!!)
                    return@Thread
                }
                val bytes = f.readBytes()
                if (cancelled) return@Thread
                cb.onProgress(bytes.size)
                cb.onFinished(bytes)
            } catch (e: Exception) {
                if (!cancelled) cb.onError("系统 TTS 出错：" + (e.message ?: e.javaClass.simpleName))
            } finally {
                try {
                    tts?.shutdown()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        th.start()
        return object : Cancellable {
            override fun cancel() {
                cancelled = true
                try {
                    tts?.stop()
                } catch (e: Exception) {
                    // ignore
                }
                th.interrupt()
            }
        }
    }

    // ------------------------------------------------------------ Gemini
    fun gemini(apiKey: String, model: String, voice: String, text: String, cb: SynthCallback): Cancellable {
        val m = model.ifBlank { "gemini-2.5-flash-preview-tts" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" + m +
            ":generateContent?key=" + apiKey
        val body = JSONObject().apply {
            put(
                "contents", JSONArray().put(
                    JSONObject().put(
                        "parts", JSONArray().put(JSONObject().put("text", text))
                    )
                )
            )
            put(
                "generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig", JSONObject().apply {
                            put(
                                "voiceConfig", JSONObject().apply {
                                    put(
                                        "prebuiltVoiceConfig", JSONObject().apply {
                                            put("voiceName", voice.ifBlank { "Kore" })
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
        return postJson(url, emptyMap(), body, cb, "candidates.0.content.parts.0.inlineData.data", wrapPcm = true)
    }

    // ------------------------------------------------------------ MiMo（messages 形状）
    fun mimo(baseUrl: String, apiKey: String, model: String, text: String, cb: SynthCallback): Cancellable {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", model.ifBlank { "MiMo-V2.5-TTS" })
            put(
                "messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    }
                )
            )
        }
        // 官方文档只说明"返回 base64"，具体字段名待验证 → 用"全 JSON 找最长 base64"兜底
        return postJson(url, mapOf("Authorization" to "Bearer " + apiKey), body, cb, null, wrapPcm = false)
    }

    // ------------------------------------------------------------ 公共
    private fun postJson(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        cb: SynthCallback,
        jsonPath: String?,
        wrapPcm: Boolean
    ): Cancellable {
        var cancelled = false
        val th = Thread {
            try {
                cb.onConnected()
                cb.onStarted()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 180_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes() } ?: ByteArray(0)
                if (cancelled) return@Thread
                if (code !in 200..299) {
                    cb.onError("返回 " + code + "：" + String(raw, Charsets.UTF_8).take(300))
                    return@Thread
                }
                val text = String(raw, Charsets.UTF_8)
                val b64 = if (jsonPath != null) pathOf(text, jsonPath) else longestBase64(text)
                if (b64.isBlank()) {
                    cb.onError("返回里没找到 base64 音频")
                    return@Thread
                }
                var audio = java.util.Base64.getDecoder().decode(b64)
                if (wrapPcm && !(audio.size > 12 && audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte())) {
                    audio = WavUtil.wrap24kMono(audio)
                }
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

    /** 支持 "a.b.0.c" 形式（含数组下标） */
    private fun pathOf(text: String, path: String): String {
        var cur: Any = try {
            JSONObject(text)
        } catch (e: Exception) {
            return ""
        }
        for (seg in path.split('.')) {
            cur = when (val c = cur) {
                is JSONObject -> c.opt(seg) ?: return ""
                is JSONArray -> {
                    val i = seg.toIntOrNull() ?: return ""
                    if (i < 0 || i >= c.length()) return ""
                    c.opt(i) ?: return ""
                }
                else -> return ""
            }
        }
        return cur as? String ?: ""
    }

    /** 兜底：全 JSON 里找最长的、像 base64 的字符串 */
    private fun longestBase64(text: String): String {
        var best = ""
        val re = Regex("[A-Za-z0-9+/]{2000,}={0,2}")
        for (m in re.findAll(text)) if (m.value.length > best.length) best = m.value
        return best
    }
}

/** 裸 PCM(24k/16bit/mono) → 标准 44 字节 WAV 头（Gemini 等用） */
object WavUtil {
    fun wrap24kMono(pcm: ByteArray): ByteArray {
        val rate = 24000
        val ch = 1
        val bits = 16
        val out = java.io.ByteArrayOutputStream(pcm.size + 44)
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
