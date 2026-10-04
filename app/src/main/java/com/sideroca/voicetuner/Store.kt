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

    /**
     * 已从列表移除的内置音色 id。**首次运行（没存过这个键）默认把预设音色全部隐藏** ——
     * 对多数人来说那几个预设（苏沐橙/艾丽妮…）没有意义，不该一上手就铺满列表。
     * 想找回：用「自定义音色 ID」粘贴它的 ID 即可。
     */
    var hiddenVoices: MutableSet<String>
        get() = prefs.getStringSet("hiddenVoices", null)?.toMutableSet()
            ?: VoiceCatalog.builtIn.map { it.second }.toMutableSet()
        set(v) { prefs.edit().putStringSet("hiddenVoices", HashSet(v)).apply() }

    /**
     * 一次性把「预设音色」全部隐藏（新老用户各做一次，做完打标记）。
     * 为什么不在 getter 里默认：老用户本机已经存过 hiddenVoices，只靠默认值不会生效。
     */
    fun hidePresetVoicesOnce() {
        if (prefs.getBoolean("presetVoicesHiddenV1", false)) return
        val h = hiddenVoices
        VoiceCatalog.builtIn.forEach { h.add(it.second) }
        hiddenVoices = h
        prefs.edit().putBoolean("presetVoicesHiddenV1", true).apply()
    }

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

    /** 该厂商"用过/填过的密钥"历史（最近在前，最多留 12 个） */
    fun providerKeyHistory(id: String): List<String> =
        (prefs.getString("keys_" + id, "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addProviderKeyToHistory(id: String, key: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        val list = ArrayList(providerKeyHistory(id))
        list.remove(k)
        list.add(0, k)
        while (list.size > 12) list.removeAt(list.size - 1)
        prefs.edit().putString("keys_" + id, list.joinToString("\n")).apply()
    }

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

    /** 该厂商"用过/填过"的 Base URL 历史（最近在前，最多 10 个） */
    fun providerBaseUrlHistory(id: String): List<String> =
        (prefs.getString("bases_" + id, "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addProviderBaseUrlToHistory(id: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        val list = ArrayList(providerBaseUrlHistory(id))
        list.remove(u)
        list.add(0, u)
        while (list.size > 10) list.removeAt(list.size - 1)
        prefs.edit().putString("bases_" + id, list.joinToString("\n")).apply()
    }

    /** 自定义渠道：端点路径（空 = 按形态默认），如 /audio/speech 或 /v1/tts */
    fun providerPath(id: String): String = prefs.getString("path_" + id, "").orEmpty()

    fun setProviderPath(id: String, v: String) {
        prefs.edit().putString("path_" + id, v.trim()).apply()
    }

    /** 自定义渠道：鉴权头模板，如 "Authorization: Bearer {key}" 或 "x-api-key: {key}" */
    fun providerAuth(id: String): String = prefs.getString("auth_" + id, "").orEmpty()

    fun setProviderAuth(id: String, v: String) {
        prefs.edit().putString("auth_" + id, v.trim()).apply()
    }

    /** 自定义渠道：音频返回形式 —— binary | base64:<json路径> | url */
    fun providerResp(id: String): String = prefs.getString("resp_" + id, "binary") ?: "binary"

    fun setProviderResp(id: String, v: String) {
        prefs.edit().putString("resp_" + id, v).apply()
    }

    var wpMain: String
        get() = prefs.getString("wpMain", "") ?: ""
        set(v) { prefs.edit().putString("wpMain", v).apply() }

    var wpPage: String
        get() = prefs.getString("wpPage", "") ?: ""
        set(v) { prefs.edit().putString("wpPage", v).apply() }

    /** 壁纸取景的归一化参数（"nx,ny,nz"，空 = 未记录）——「可随时重裁」用 */
    var wpMainCrop: String
        get() = prefs.getString("wpMainCrop", "") ?: ""
        set(v) { prefs.edit().putString("wpMainCrop", v).apply() }

    var wpPageCrop: String
        get() = prefs.getString("wpPageCrop", "") ?: ""
        set(v) { prefs.edit().putString("wpPageCrop", v).apply() }

    var scrimMain: Int
        get() = prefs.getInt("scrimMain", 35)
        set(v) { prefs.edit().putInt("scrimMain", v).apply() }

    var scrimPage: Int
        get() = prefs.getInt("scrimPage", 35)
        set(v) { prefs.edit().putInt("scrimPage", v).apply() }

    var cardAlphaPct: Int
        get() = prefs.getInt("cardAlpha", 100)
        set(v) { prefs.edit().putInt("cardAlpha", v).apply() }

    /** 翻译目标语言（中文/英语/…） */
    var trTarget: String
        get() = prefs.getString("trTarget", "中文") ?: "中文"
        set(v) { prefs.edit().putString("trTarget", v).apply() }

    /** MyMemory 免费翻译的邮箱（可选）：带 `de` 参数后免额 5,000 → 50,000 字符/天 */
    var transEmail: String
        get() = prefs.getString("transEmail", "") ?: ""
        set(v) { prefs.edit().putString("transEmail", v.trim()).apply() }

    /** 翻译语言（逗号分隔、按顺序；默认「中文,英语」）—— 翻译按它决定方向与版本数 */
    var transLangs: String
        get() = prefs.getString("transLangs", "中文,英语") ?: "中文,英语"
        set(v) { prefs.edit().putString("transLangs", v).apply() }

    fun transLangList(): List<String> =
        transLangs.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** 音色「角色提示词」（按 voiceId 存）：给 AI 助手看的人设，用来增强润色 / 写台词。 */
    fun rolePrompt(voiceId: String): String =
        if (voiceId.isBlank()) "" else prefs.getString("role_" + voiceId, "") ?: ""

    fun setRolePrompt(voiceId: String, v: String) {
        if (voiceId.isBlank()) return
        prefs.edit().putString("role_" + voiceId, v.trim()).apply()
    }

    // ---- 提示词助手（给 LLM 发请求 → 自动挑厂商 / 写风格指令 / 生成纠错表）----
    var llmBaseUrl: String
        get() = prefs.getString("llmBaseUrl", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        set(v) { prefs.edit().putString("llmBaseUrl", v.trim()).apply() }

    var llmKey: String
        get() = prefs.getString("llmKey", "") ?: ""
        set(v) { prefs.edit().putString("llmKey", v).apply() }

    var llmModel: String
        get() = prefs.getString("llmModel", "deepseek-chat") ?: "deepseek-chat"
        set(v) { prefs.edit().putString("llmModel", v.trim()).apply() }

    /** 系统提示词（设置页里展示出来、可改） */
    var llmPrompt: String
        get() {
            val v = prefs.getString("llmPrompt", "") ?: ""
            // 旧版那条"几百字"的参数助手提示词迁移：命中旧标题 → 视为未自定义，回落到新的短默认
            return if (v.contains("TTS（语音合成）参数助手")) "" else v
        }
        set(v) { prefs.edit().putString("llmPrompt", v).apply() }

    /** token 预算（0 = 不限） */
    var llmMaxTokens: Int
        get() = prefs.getInt("llmMaxTokens", 0)
        set(v) { prefs.edit().putInt("llmMaxTokens", v).apply() }

    /** 思考档位（关/低/高/极高…；空 = 不发该参数） */
    var llmLevel: String
        get() = prefs.getString("llmLevel", "") ?: ""
        set(v) { prefs.edit().putString("llmLevel", v).apply() }

    /** 温度（-1 = 用默认） */
    var llmTemp: Int      // 存 0.1 为单位，避免 Float 精度问题
        get() = prefs.getInt("llmTemp", -10)
        set(v) { prefs.edit().putInt("llmTemp", v).apply() }

    /** 翻译用的系统提示词（空 = 用内置默认） */
    var transPrompt: String
        get() = prefs.getString("transPrompt", "") ?: ""
        set(v) { prefs.edit().putString("transPrompt", v).apply() }

    /** 用户补充说明（如"这个音色是《明日方舟》的艾雅法拉"） */
    var llmExtra: String
        get() = prefs.getString("llmExtra", "") ?: ""
        set(v) { prefs.edit().putString("llmExtra", v).apply() }

    /** 跨页回填：设置页点了「回填」→ 记下这条记录 id，主页 onResume 时取走并应用 */
    var pendingFillId: String
        get() = prefs.getString("pendingFillId", "") ?: ""
        set(v) { prefs.edit().putString("pendingFillId", v).apply() }

    /** 上次翻译成功的免费源（google / mm），下次优先用它 */
    var trLastSource: String
        get() = prefs.getString("trLastSource", "") ?: ""
        set(v) { prefs.edit().putString("trLastSource", v).apply() }

    /** 选过的 App 图标样式 key（default / a / b / c） */
    var appIconStyle: String
        get() = prefs.getString("appIconStyle", "default") ?: "default"
        set(v) { prefs.edit().putString("appIconStyle", v).apply() }

    /** 主界面「高级参数」是否展开（记忆上次状态） */
    var advExpanded: Boolean
        get() = prefs.getBoolean("advExpanded", false)
        set(v) { prefs.edit().putBoolean("advExpanded", v).apply() }

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

/** 导出 / 分享用的"好名字"：音色 + 正文片段 + 原扩展名（自动去掉文件名非法字符） */
fun niceFileName(take: Take): String {
    val ext = take.fileName.substringAfterLast('.', "wav")
    val voice = take.voiceName.ifBlank { "音色" }.trim()
    val snippet = take.text.replace(Regex("[\\s\\r\\n]+"), " ").trim().take(18)
    val raw = if (snippet.isEmpty()) voice else voice + "·" + snippet
    val safe = raw.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "语音" }
    return safe + "." + ext
}
