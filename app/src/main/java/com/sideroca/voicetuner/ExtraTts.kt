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

    /** 把「额外参数（JSON）」合并进请求体；**不覆盖**已存在字段（避免把 text/model 之类冲掉） */
    fun mergeInto(target: JSONObject, extra: JSONObject?) {
        extra ?: return
        val it = extra.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!target.has(k)) target.put(k, extra.get(k))
        }
    }

    // ------------------------------------------------------------------ ElevenLabs
    fun eleven(
        baseUrl: String, apiKey: String, voice: String, model: String,
        text: String, stability: Double, similarity: Double, cb: SynthCallback,
        rate: Double = 1.0, seed: Int? = null
    ): Cancellable {
        val v = voice.ifBlank { "21m00Tcm4TlvDq8ikWAM" }        // 官方公开示例音色 Rachel
        val url = baseUrl.trimEnd('/') + "/v1/text-to-speech/" + v + "?output_format=pcm_24000"   // 它没有 wav 容器 → 要裸 PCM，本地套头（无损）"
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", model.ifBlank { "eleven_multilingual_v2" })
            // 官方：指定 seed 会尽量确定性复现（顶层字段，不在 voice_settings 里）
            seed?.let { put("seed", it) }
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarity)
                // 官方 speed 只认 0.7~1.2，超出会自动夹到边界
                if (kotlin.math.abs(rate - 1.0) > 0.001) put("speed", rate.coerceIn(0.7, 1.2))
            })
        }
        // 返回的是 24k 裸 PCM → 本地套 44 字节 WAV 头
        return post(url, mapOf("xi-api-key" to apiKey), body, cb, wrapPcm = true)
    }

    // ------------------------------------------------------------------ MiniMax
    /** 从用户写的「情绪/风格指令」里认出 MiniMax 官方支持的 8 种情绪（认不出就不发） */
    private fun emotionOf(instruction: String?): String? {
        val t = instruction.orEmpty()
        if (t.isBlank()) return null
        val map = listOf(
            "happy" to listOf("高兴", "开心", "愉悦", "欢快", "happy"),
            "sad" to listOf("悲伤", "难过", "哀伤", "sad"),
            "angry" to listOf("愤怒", "生气", "愤怒", "angry"),
            "fearful" to listOf("害怕", "恐惧", "fearful"),
            "disgusted" to listOf("厌恶", "disgusted"),
            "surprised" to listOf("惊讶", "surprised"),
            "calm" to listOf("中性", "平静", "冷静", "calm"),
            "fluent" to listOf("生动", "流畅", "fluent"),
            "whisper" to listOf("低语", "耳语", "whisper")
        )
        for ((k, words) in map) if (words.any { t.contains(it, ignoreCase = true) }) return k
        return null
    }

    fun minimax(
        baseUrl: String, apiKey: String, groupId: String, model: String, voice: String,
        text: String, speed: Double, pitch: Double, volume: Int, cb: SynthCallback,
        instruction: String? = null
    ): Cancellable {
        val g = if (groupId.isBlank()) "" else "?GroupId=" + groupId
        val url = baseUrl.trimEnd('/') + "/t2a_v2" + g
        val body = JSONObject().apply {
            put("model", model.ifBlank { "speech-2.8-hd" })
            put("text", text)
            put("stream", false)
            put("voice_setting", JSONObject().apply {
                put("voice_id", voice.ifBlank { "male-qn-qingse" })
                put("speed", speed)                 // 官方 0.5~2
                put("pitch", ((pitch - 1.0) * 12.0).coerceIn(-12.0, 12.0))  // 界面倍率 → 官方 -12~12 半音
                // ⚠️ 官方 vol 范围是 (0,10]、默认 1.0 —— 以前直接把界面 0~100 发过去（默认 50 = 正常值 50 倍）
                put("vol", ((volume.coerceIn(0, 100)) / 50.0).coerceIn(0.1, 10.0))
                // 情绪：官方 8 种（高兴/悲伤/愤怒/害怕/厌恶/惊讶/中性/生动/低语），从「情绪/风格指令」里认出来
                emotionOf(instruction)?.let { put("emotion", it) }
            })
            put("audio_setting", JSONObject().apply {
                put("format", "mp3")     // TODO 待核：MiniMax 是否支持 wav/pcm（支持再改无损）
                put("sample_rate", 32000)
            })
        }
        return post(url, mapOf("Authorization" to "Bearer " + apiKey), body, cb, hexIn = "data.audio")
    }

    // ------------------------------------------------------------------ Fish Audio
    fun fish(
        baseUrl: String, apiKey: String, voice: String, text: String, cb: SynthCallback,
        model: String = "", rate: Double = 1.0, volume: Int = 50, extra: JSONObject? = null,
        instruction: String? = null, seed: Int? = null
    ): Cancellable {
        val url = baseUrl.trimEnd('/') + "/v1/tts"
        // 官方 prosody：speed 0.5~2.0（正好对上我们的语速 0.5~2.0）、volume -20~20（我们的 0~100 线性映射）
        val sp = rate.coerceIn(0.5, 2.0)
        val vol = (volume.coerceIn(0, 100) / 100.0) * 40.0 - 20.0
        // Fish 没有独立的"指令"字段：把自然语言提示写成括号提示拼到文本最前面
        //   S2 / S2.1 → [方括号]（可自由自然语言）；S1（老模型）→ (圆括号) 且只认固定标签
        val cue = instruction?.trim().orEmpty()
        val bodyText = if (cue.isEmpty()) text else {
            val b = if (model.trim().lowercase().startsWith("s1")) "(" + cue + ")" else "[" + cue + "]"
            b + " " + text
        }
        val body = JSONObject().apply {
            put("text", bodyText)
            put("format", "wav")     // 官方支持 wav，无损
            if (voice.isNotBlank()) put("reference_id", voice)
            if (kotlin.math.abs(sp - 1.0) > 0.001 || kotlin.math.abs(vol) > 0.001) {
                put("prosody", JSONObject().apply {
                    put("speed", sp)
                    put("volume", vol)
                })
            }
            // 种子：官方参数表里没有，但填了我们就发（服务认就用；不认一般会忽略）
            seed?.let { put("seed", it) }
            // temperature / top_p / repetition_penalty / normalize 等高级参数：写进「额外参数」即可
            mergeInto(this, extra)
        }
        // 【实测】不带 model header 会走付费模型并返回 402；免费档必须显式带这个 header
        val m = model.ifBlank { "s2.1-pro-free" }
        return post(url, mapOf("Authorization" to "Bearer " + apiKey, "model" to m), body, cb)
    }

    // ------------------------------------------------------------------ Fish Audio 建音色
    /**
     * 【实测流程】POST {base}/model · multipart：
     *   type=tts（必填）· title · voices=@参考音频(wav) · train_mode=fast · visibility=private
     * 返回 JSON 的 _id 即新音色 id（32 位十六进制），之后合成时当 reference_id 用。
     */
    fun fishCreateVoice(
        baseUrl: String, apiKey: String, wav: ByteArray, title: String, visibility: String
    ): String {
        val boundary = "----vt" + System.currentTimeMillis()
        val bos = java.io.ByteArrayOutputStream()
        fun w(str: String) = bos.write(str.toByteArray(Charsets.UTF_8))
        fun field(name: String, value: String) {
            w("--" + boundary + "\r\n")
            w("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
            w(value + "\r\n")
        }
        field("type", "tts")
        field("title", title)
        field("train_mode", "fast")
        field("visibility", visibility)
        w("--" + boundary + "\r\n")
        w("Content-Disposition: form-data; name=\"voices\"; filename=\"sample.wav\"\r\n")
        w("Content-Type: audio/wav\r\n\r\n")
        bos.write(wav)
        w("\r\n--" + boundary + "--\r\n")

        val conn = (java.net.URL(baseUrl.trimEnd('/') + "/model").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer " + apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        }
        conn.outputStream.use { it.write(bos.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Fish 建音色返回 " + code + "：" + text.take(300))
        val id = JSONObject(text).optString("_id")
        if (id.isBlank()) throw IllegalStateException("Fish 建音色没返回音色 id：" + text.take(300))
        return id
    }

    // ------------------------------------------------------------------ 公共 POST
    private fun post(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        cb: SynthCallback,
        hexIn: String? = null,
        wrapPcm: Boolean = false
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
                var audio: ByteArray = if (hexIn != null) {
                    val hex = jsonPath(String(raw, Charsets.UTF_8), hexIn)
                    if (hex.isBlank()) {
                        cb.onError("返回里没找到音频（路径 " + hexIn + "）")
                        return@Thread
                    }
                    hexToBytes(hex)
                } else raw
                // 裸 PCM（24k/16bit/mono）→ 套 WAV 头（已是 RIFF 容器则跳过）
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
