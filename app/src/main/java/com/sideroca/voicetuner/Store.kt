package com.sideroca.voicetuner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一条生成记录 */
data class Take(
    val id: String,
    val fileName: String,
    val voiceName: String,
    val voiceId: String,
    val text: String,
    val instruction: String,
    val rate: Double,
    val pitch: Double,
    val volume: Int,
    val seed: Int,
    val format: String,
    /** 这条记录用的模型（旧数据没有 → 空串） */
    val model: String,
    val durationMs: Long,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("voiceName", voiceName)
        put("voiceId", voiceId)
        put("text", text)
        put("instruction", instruction)
        put("rate", rate)
        put("pitch", pitch)
        put("volume", volume)
        put("seed", seed)
        put("format", format)
        put("model", model)
        put("durationMs", durationMs)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Take = Take(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            voiceName = o.optString("voiceName"),
            voiceId = o.optString("voiceId"),
            text = o.optString("text"),
            instruction = o.optString("instruction"),
            rate = o.optDouble("rate", 1.0),
            pitch = o.optDouble("pitch", 1.0),
            volume = o.optInt("volume", 50),
            seed = o.optInt("seed", 0),
            format = o.optString("format", "wav24"),
            model = o.optString("model", ""),
            durationMs = o.optLong("durationMs", 0),
            createdAt = o.optLong("createdAt", 0)
        )
    }
}

/** 自建复刻音色 */
data class CustomVoice(
    val id: String,
    val name: String,
    val prefix: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("prefix", prefix)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): CustomVoice = CustomVoice(
            id = o.optString("id"),
            name = o.optString("name"),
            prefix = o.optString("prefix"),
            createdAt = o.optLong("createdAt", 0)
        )
    }
}

/** 本机存储：设置 + 历史记录 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("vt", Context.MODE_PRIVATE)
    private val dir: File = File(context.filesDir, "history").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val voicesFile = File(context.filesDir, "custom_voices.json")

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) { prefs.edit().putString("apiKey", v).apply() }

    var workspace: String
        get() = prefs.getString("workspace", "ws-9y8n1gp7w6pg23tv") ?: "ws-9y8n1gp7w6pg23tv"
        set(v) { prefs.edit().putString("workspace", v).apply() }

    var lastModel: String
        get() = prefs.getString("model", "cosyvoice-v3.5-plus") ?: "cosyvoice-v3.5-plus"
        set(v) { prefs.edit().putString("model", v).apply() }

    // ---- v0.3：主题 / 壁纸 ----
    var themeId: String
        // 默认主题 = 茶文化 · 竹青（tea）；用户选过则以其为准
        get() = prefs.getString("themeId", "tea") ?: "tea"
        set(v) { prefs.edit().putString("themeId", v).apply() }

    /** 当前音色指示符颜色（外观可选项；默认极光绿 #2FE39B） */
    var indicatorColor: String
        get() = prefs.getString("indicatorColor", "#2FE39B") ?: "#2FE39B"
        set(v) { prefs.edit().putString("indicatorColor", v).apply() }

    /** 被隐藏的内置音色 id（内置音色写死在代码里，只能"隐藏"，可恢复） */
    var hiddenVoices: MutableSet<String>
        get() = (prefs.getStringSet("hiddenVoices", emptySet()) ?: emptySet()).toMutableSet()
        set(v) { prefs.edit().putStringSet("hiddenVoices", HashSet(v)).apply() }

    /** 收藏的合成记录（Take.id 集合） */
    var favTakes: MutableSet<String>
        get() = (prefs.getStringSet("favTakes", emptySet()) ?: emptySet()).toMutableSet()
        set(v) { prefs.edit().putStringSet("favTakes", HashSet(v)).apply() }

    // ---- P1：多厂商 ----

    /** 当前选中的厂商 id（默认阿里云百炼） */
    var providerId: String
        get() = prefs.getString("providerId", "aliyun-bailian") ?: "aliyun-bailian"
        set(v) { prefs.edit().putString("providerId", v).apply() }

    /** 每个厂商各自的 API Key（按 id 分开存，互不覆盖） */
    fun providerKey(id: String): String = prefs.getString("key_" + id, "") ?: ""

    fun setProviderKey(id: String, value: String) {
        prefs.edit().putString("key_" + id, value).apply()
    }

    /** 某厂商的 Base URL（默认用目录里的；「自定义渠道」由用户填） */
    fun providerBaseUrl(id: String): String {
        val custom = prefs.getString("base_" + id, "").orEmpty()
        if (custom.isNotBlank()) return custom
        return TtsProviders.byId(id)?.baseUrl.orEmpty()
    }

    fun setProviderBaseUrl(id: String, value: String) {
        prefs.edit().putString("base_" + id, value.trim()).apply()
    }

    var wpMain: String
        get() = prefs.getString("wpMain", "") ?: ""
        set(v) { prefs.edit().putString("wpMain", v).apply() }

    var wpPage: String
        get() = prefs.getString("wpPage", "") ?: ""
        set(v) { prefs.edit().putString("wpPage", v).apply() }

    var scrimMain: Int
        get() = prefs.getInt("scrimMain", 35)
        set(v) { prefs.edit().putInt("scrimMain", v).apply() }

    var scrimPage: Int
        get() = prefs.getInt("scrimPage", 35)
        set(v) { prefs.edit().putInt("scrimPage", v).apply() }

    var cardAlphaPct: Int
        get() = prefs.getInt("cardAlpha", 100)
        set(v) { prefs.edit().putInt("cardAlpha", v).apply() }

    fun newAudioFile(ext: String): File =
        File(dir, "vt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6) + "." + ext)

    fun fileOf(take: Take): File = File(dir, take.fileName)

    fun deleteFile(take: Take) {
        try {
            fileOf(take).delete()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 原子写：先写 .tmp → 旧文件备份为 .bak → 再替换；避免写一半被杀导致文件截断 */
    private fun writeAtomic(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (file.exists()) {
                try {
                    file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
                } catch (e: Exception) {
                    // 备份失败不影响主流程
                }
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 读文件；主文件读不出来时回退 .bak（都不可用返回 null） */
    private fun readTextResilient(file: File): String? {
        if (file.exists()) {
            try {
                return file.readText()
            } catch (e: Exception) {
                // 落到 .bak
            }
        }
        val bak = File(file.parentFile, file.name + ".bak")
        if (bak.exists()) {
            try {
                return bak.readText()
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    fun loadTakes(): MutableList<Take> {
        val list = mutableListOf<Take>()
        val raw = readTextResilient(indexFile) ?: return list
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(Take.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            // 解析失败：保留现场（.tmp/.bak 可人工恢复），不在此覆盖
        }
        return list
    }

    fun saveTakes(takes: List<Take>) {
        try {
            val arr = JSONArray()
            takes.forEach { arr.put(it.toJson()) }
            writeAtomic(indexFile, arr.toString())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun loadCustomVoices(): MutableList<CustomVoice> {
        val list = mutableListOf<CustomVoice>()
        val raw = readTextResilient(voicesFile) ?: return list
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(CustomVoice.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    fun saveCustomVoices(list: List<CustomVoice>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            writeAtomic(voicesFile, arr.toString())
        } catch (e: Exception) {
            // ignore
        }
    }
}
