package com.sideroca.voicetuner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
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
    val createdAt: Long,
    /**
     * 这个音色**属于哪家 TTS 厂商**（TtsProviders.id）。
     * 2026-10-15 新增：音色 ID 是各厂商自己发的，跨厂商不通用；
     * 「智能填参」要用它判断"当前音色 ↔ 当前厂商"是否对得上，不对就自动切厂商。
     * 旧数据没有这个字段 → 空串，由 `providerGuess`（前缀规则）兜底。
     */
    val providerId: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("prefix", prefix)
        put("createdAt", createdAt)
        put("provider", providerId)
    }

    companion object {
        fun fromJson(o: JSONObject): CustomVoice = CustomVoice(
            id = o.optString("id"),
            name = o.optString("name"),
            prefix = o.optString("prefix"),
            createdAt = o.optLong("createdAt", 0),
            providerId = o.optString("provider", "")       // 旧数据没有 → 空串（走前缀兜底）
        )
    }
}

/** 本机存储：设置 + 历史记录 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("vt", Context.MODE_PRIVATE)
    private val dir: File = File(context.filesDir, "history").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val voicesFile = File(context.filesDir, "custom_voices.json")
    /** 每个音色的自定义试听模板（本地音频；未设置则用 TTS 现场合成） */
    private val previewDir = File(context.filesDir, "voice_previews").apply { mkdirs() }

    // ---- AI 闹钟 ----
    /** 闹钟配置（JSON；单闹钟 MVP） */
    var alarmJson: String
        get() = prefs.getString("alarm_json", "") ?: ""
        set(v) { prefs.edit().putString("alarm_json", v).apply() }

    var alarmSnoozeUntil: Long
        get() = prefs.getLong("alarm_snooze", 0L)
        set(v) { prefs.edit().putLong("alarm_snooze", v).apply() }

    /** 闹钟语音缓存目录（预生成的 WAV） */
    val alarmDir: File = File(context.filesDir, "alarm").apply { mkdirs() }


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

    /** 卡片浓度（%）：**主界面**用 —— 主界面/设置页**各自独立**（用户 2026-10-13，像「遮罩」那样成对） */
    var cardAlphaMain: Int
        get() = prefs.getInt("cardAlphaMain", 100)
        set(v) { prefs.edit().putInt("cardAlphaMain", v.coerceIn(0, 100)).apply() }

    /** 卡片浓度（%）：**设置页**用（沿用旧键 `cardAlpha` → 老用户已设的值不会丢） */
    var cardAlphaPage: Int
        get() = prefs.getInt("cardAlpha", 100)
        set(v) { prefs.edit().putInt("cardAlpha", v.coerceIn(0, 100)).apply() }

    /** 记录字号（%）：只作用在记录卡的标题/信息/详情——小按钮不动（用户 2026-10-05）。 */
    var recordFontPct: Int
        get() = prefs.getInt("recFontPct", 100)
        set(v) { prefs.edit().putInt("recFontPct", v.coerceIn(90, 130)).apply() }

    /** 翻译方式："auto"＝有 LLM Key 就走 LLM、否则走免费接口 ｜ "llm" ｜ "free"（用户 2026-10-14） */
    var transEngine: String
        get() = prefs.getString("transEngine", "auto") ?: "auto"
        set(v) { prefs.edit().putString("transEngine", v).apply() }

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

    /** 上次成功导出备份的时间（0 = 从未备份） */
    var lastBackupAt: Long
        get() = prefs.getLong("lastBackupAt", 0L)
        set(v) { prefs.edit().putLong("lastBackupAt", v).apply() }

    /** 上次翻译成功的免费源（google / mm），下次优先用它 */
    var trLastSource: String
        get() = prefs.getString("trLastSource", "") ?: ""
        set(v) { prefs.edit().putString("trLastSource", v).apply() }

    /** 选过的 App 图标样式 key（default / a / b / c） */
    var appIconStyle: String
        get() = prefs.getString("appIconStyle", "default") ?: "default"
        set(v) { prefs.edit().putString("appIconStyle", v).apply() }

    /** 是否已完成"首次安装的语言选择"（选过就不再出现，更新也不重置） */
    var langChosen: Boolean
        get() = prefs.getBoolean("langChosen", false)
        set(v) { prefs.edit().putBoolean("langChosen", v).apply() }

    /** 界面语言："" = 跟随系统；否则 zh / en / ja / ko */
    var lang: String
        get() = prefs.getString("lang", "") ?: ""
        set(v) { prefs.edit().putString("lang", v).apply() }

    /** 主界面「高级参数」是否展开（记忆上次状态） */
    var advExpanded: Boolean
        get() = prefs.getBoolean("advExpanded", false)
        set(v) { prefs.edit().putBoolean("advExpanded", v).apply() }

    fun newAudioFile(ext: String): File =
        File(dir, "vt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6) + "." + ext)

    /** 记录音频目录（备份 / 恢复用） */
    fun historyDir(): File = dir

    // ---------------------------------------------------------------- 音色试听模板
    /** 音色 id → 稳定的短文件名（避免 id 里的特殊字符进路径） */
    private fun previewKey(voiceId: String): String = voiceId.hashCode().toUInt().toString(16)

    /** 该音色已导入的试听模板；没有/空文件返回 null。 */
    fun previewTemplate(voiceId: String): File? {
        val key = previewKey(voiceId)
        return previewDir.listFiles()?.firstOrNull {
            it.isFile && it.length() > 0 && it.nameWithoutExtension == key
        }
    }

    /** 保存导入的试听模板；失败返回 null。同名旧模板会先删掉。 */
    fun savePreviewTemplate(voiceId: String, input: InputStream, ext: String): File? {
        val key = previewKey(voiceId)
        val safeExt = ext.lowercase().let {
            if (it in setOf("wav", "mp3", "m4a", "aac", "ogg", "opus", "flac")) it else "wav"
        }
        runCatching {
            previewDir.listFiles()?.filter { it.nameWithoutExtension == key }?.forEach { it.delete() }
        }
        val f = File(previewDir, key + "." + safeExt)
        return runCatching {
            f.outputStream().use { out -> input.copyTo(out) }
            if (f.length() > 0) f else { f.delete(); null }
        }.getOrElse {
            runCatching { f.delete() }
            null
        }
    }

    /** 删除该音色的自定义试听模板（回到 TTS 默认）。 */
    fun clearPreviewTemplate(voiceId: String) {
        val key = previewKey(voiceId)
        runCatching {
            previewDir.listFiles()?.filter { it.nameWithoutExtension == key }?.forEach { it.delete() }
        }
    }

    /** 全部试听模板文件（备份用）。 */
    fun voicePreviewFiles(): List<File> =
        previewDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()

    /** 恢复备份里的试听模板；文件名是 hash，不需要对应 voiceId。 */
    fun restorePreviewFile(name: String, input: InputStream): File? {
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
        if (safe.isBlank() || safe.contains("..")) return null
        val ext = safe.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("wav", "mp3", "m4a", "aac", "ogg", "opus", "flac")) return null
        val f = File(previewDir, safe)
        if (!f.exists() || f.length() == 0L) {
            f.outputStream().use { out -> input.copyTo(out) }
        }
        return f
    }

    /** 本地数据占用（记录音频 + 试听模板 + 索引/音色 JSON），用于备份卡提示。 */
    fun storageBytes(): Long {
        var n = 0L
        runCatching { dir.listFiles()?.forEach { if (it.isFile) n += it.length() } }
        runCatching { previewDir.listFiles()?.forEach { if (it.isFile) n += it.length() } }
        runCatching { if (voicesFile.isFile) n += voicesFile.length() }
        return n
    }

    /** 恢复时按原文件名写入音频；只接受常见音频扩展名，防路径穿越。 */
    fun restoreAudioFile(name: String, input: java.io.InputStream): File? {
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
        if (safe.isBlank() || safe.contains("..")) return null
        val ext = safe.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("wav", "mp3", "m4a", "opus", "ogg")) return null
        val f = File(dir, safe)
        if (!f.exists() || f.length() == 0L) {
            f.outputStream().use { out -> input.copyTo(out) }
        }
        return f
    }

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
                    // 2026-10-17：多留一代 —— 先把现有 .bak 挪成 .bak2，再写新的 .bak。
                    // 用户丢过 9 条记录，只留一代 .bak 时，"最近一次写入"就把证据覆盖掉了。
                    val bak = File(file.parentFile, file.name + ".bak")
                    val bak2 = File(file.parentFile, file.name + ".bak2")
                    if (bak.exists()) runCatching { bak.copyTo(bak2, overwrite = true) }
                    file.copyTo(bak, overwrite = true)
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

    /**
     * **读-改-写**：在**磁盘上的当前列表**上做改动再存回。
     *
     * ⚠️ 2026-10-17：不要拿"很久以前的内存快照"整体覆盖（`saveTakes(旧快照)`）——
     * 那会把这期间**新增的记录抹掉**（用户报"收藏 15 条只显示五六条"、且时有时无，
     * 根因就是后台的时长迁移线程用 onCreate 的快照覆盖了索引）。
     */
    fun patchTakes(change: (MutableList<Take>) -> Unit) {
        synchronized(takesLock) {
            val list = loadTakes()
            change(list)
            saveTakes(list)
        }
    }

    private val takesLock = Any()

    /** 可恢复的条数（= .bak 里多的 + 音频孤儿）；用于在记录页提示 */
    fun countRecoverable(): Int = runCatching {
        val have = loadTakes().map { it.id }.toSet()
        var n = 0
        bakFiles().forEach { bak ->
            runCatching {
                val arr = JSONArray(bak.readText())
                for (i in 0 until arr.length()) {
                    val id = arr.getJSONObject(i).optString("id")
                    if (id.isBlank() || id in have) continue
                    if (File(indexFile.parentFile, id + ".wav").exists() || File(indexFile.parentFile, id + ".mp3").exists()) n++
                }
            }
        }
        n + orphanAudioFiles().size
    }.getOrDefault(0)

    private fun bakFile() = File(indexFile.parentFile, indexFile.name + ".bak")

    /** 两代备份（.bak = 上一次、.bak2 = 上上次）——恢复时都读 */
    private fun bakFiles(): List<File> = listOf(
        File(indexFile.parentFile, indexFile.name + ".bak"),
        File(indexFile.parentFile, indexFile.name + ".bak2")
    ).filter { it.exists() }

    /** 音频文件在、索引里没有的那些文件（排除 index/status 之类） */
    private fun orphanAudioFiles(): List<File> {
        val have = loadTakes().map { it.id }.toSet()
        val exts = setOf("wav", "mp3", "m4a", "opus", "ogg")
        return indexFile.parentFile?.listFiles()?.filter { f ->
            f.isFile && f.extension.lowercase() in exts && f.nameWithoutExtension !in have
        } ?: emptyList()
    }

    /**
     * **恢复丢失的记录**（用户 2026-10-17：收藏 15 条只显示 6 条）。
     *  ① 先吃 `index.json.bak`（元数据完整：原文/音色/时长都在）
     *  ② 再收"音频在、索引里没有"的孤儿（元数据残缺，用文件名 + mtime 尽力补）
     *  **只增不删**；返回补回来的条数。
     */
    fun recoverTakes(): Int = synchronized(takesLock) {
        val cur = loadTakes()
        val have = cur.map { it.id }.toMutableSet()
        var added = 0
        runCatching {
            bakFiles().forEach { bak ->
                val arr = JSONArray(bak.readText())
                for (i in 0 until arr.length()) {
                    val t = Take.fromJson(arr.getJSONObject(i))
                    if (t.id.isNotBlank() && t.id !in have && fileOf(t).exists()) {
                        cur.add(t); have.add(t.id); added++
                    }
                }
            }
            orphanAudioFiles().forEach { f ->
                val ext = f.extension.lowercase()
                cur.add(
                    Take(
                        id = f.nameWithoutExtension, fileName = f.name,
                        voiceName = "（恢复）", voiceId = "",
                        text = "（原文缺失·从音频文件恢复）", instruction = "",
                        rate = 1.0, pitch = 1.0, volume = 50, seed = 0,
                        format = if (ext == "wav") "wav24" else "mp3",
                        model = "", durationMs = 0, createdAt = f.lastModified()
                    )
                )
                have.add(f.nameWithoutExtension); added++
            }
            if (added > 0) { cur.sortByDescending { it.createdAt }; saveTakes(cur) }
        }
        added
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
