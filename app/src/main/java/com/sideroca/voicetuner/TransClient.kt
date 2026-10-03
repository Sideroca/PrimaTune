package com.sideroca.voicetuner

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 内置翻译（免费、免 Key、**不跳任何 App**、原地替换文本）。
 *
 * 两个免费源自动回退：
 *  ① Google 公开端点（质量更好；国内需能连 Google 的网络）
 *  ② MyMemory（免 Key、国内可达；实测中英日互译都通、也不拒成人向内容）
 * 谁通用谁。两个都失败才报错。
 *
 * 说明：都是**公开/非官方**接口（不注册、不审核），所以既不花钱也不"百无禁忌"受限；
 * 代价是可能哪天失效 —— 真失效告诉我换源即可。
 */
object TransClient {

    class Cancelled { @Volatile var cancelled = false }

    private val TARGETS = listOf(
        "中文" to "zh-CN", "英语" to "en", "日语" to "ja", "韩语" to "ko", "俄语" to "ru",
        "德语" to "de", "法语" to "fr", "西班牙语" to "es", "葡萄牙语" to "pt",
        "意大利语" to "it", "泰语" to "th", "越南语" to "vi", "印尼语" to "id", "阿拉伯语" to "ar"
    )

    fun targetNames(): List<String> = TARGETS.map { it.first }

    private fun codeOf(name: String): String =
        TARGETS.firstOrNull { it.first == name }?.second ?: "zh-CN"

    /**
     * @param lastGood 上次成功的源（"google" / "mm"），先试它 —— 免得每次都在被墙的那个上白等超时
     * @param onDone (译文, 错误, 用的哪个源)
     */
    fun translate(
        text: String, targetName: String, lastGood: String,
        onDone: (String?, String?, String?) -> Unit
    ): Cancelled {
        val c = Cancelled()
        Thread {
            val tc = codeOf(targetName)
            val order = if (lastGood == "mm") listOf("mm", "google") else listOf("google", "mm")
            for (src in order) {
                val out = if (src == "google") tryGoogle(text, tc) else tryMyMemory(text, tc)
                if (c.cancelled) return@Thread
                if (out != null) { onDone(out, null, src); return@Thread }
            }
            onDone(null, "两个免费翻译源都失败了（Google 需能连外网；MyMemory 可能超了当日免额度）", null)
        }.start()
        return c
    }

    /** ① Google 公开端点 */
    private fun tryGoogle(text: String, tc: String): String? = try {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" +
            URLEncoder.encode(tc, "UTF-8") + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
        val raw = get(url, 6000, 15000) ?: return null
        val arr = JSONArray(raw).optJSONArray(0) ?: return null
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.optJSONArray(i)?.optString(0).orEmpty())
        sb.toString().trim().ifBlank { null }
    } catch (e: Exception) {
        null
    }

    /** ② MyMemory（langpair=Autodetect|目标） */
    private fun tryMyMemory(text: String, tc: String): String? = try {
        val url = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(text, "UTF-8") +
            "&langpair=" + URLEncoder.encode("Autodetect|" + tc, "UTF-8")
        val raw = get(url, 6000, 20000) ?: return null
        val o = org.json.JSONObject(raw).optJSONObject("responseData") ?: return null
        o.optString("translatedText").trim().ifBlank { null }
    } catch (e: Exception) {
        null
    }

    private fun get(url: String, ct: Int, rt: Int): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = ct
            readTimeout = rt
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) return null
        // Google 被拦时会返回一坨 HTML（含 Sorry）——那不是译文
        if (raw.trimStart().startsWith("<")) return null
        return raw
    }
}
