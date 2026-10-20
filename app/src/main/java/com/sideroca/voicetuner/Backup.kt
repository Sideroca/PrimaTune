package com.sideroca.voicetuner

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地备份 / 恢复（2026-10-17 用户：卸载重装后记录没了怎么办）。
 *
 * 为什么不做“把记录打包进 APK”：APK 是只读安装包，用户以后生成的记录不可能写回去；
 * 重装 APK 也不会带回旧的 App 私有数据。可靠做法是**导出成一个 zip 文件**（用户自己放网盘/电脑），
 * 重装后再从 zip 恢复。
 *
 * 备份内容：记录索引 + 音频 + 自建音色 + 收藏 + 每音色试听模板。
 * **不含 API Key / 厂商密钥**（隐私 + 换机后密钥本来就该重填）。
 */
object Backup {

    data class Result(val takes: Int, val voices: Int, val favorites: Int, val audio: Int, val previews: Int)

    private const val MANIFEST = "manifest.json"
    private const val TAKES = "takes.json"
    private const val VOICES = "custom_voices.json"
    private const val FAVS = "favorites.json"
    private const val AUDIO_PREFIX = "audio/"
    private const val PREVIEW_PREFIX = "previews/"

    fun export(ctx: Context, store: Store, uri: Uri): Result {
        val takes = store.loadTakes()
        val voices = store.loadCustomVoices()
        val favs = store.favTakes

        val takesArr = JSONArray().apply { takes.forEach { put(it.toJson()) } }
        val voicesArr = JSONArray().apply { voices.forEach { put(it.toJson()) } }
        val favArr = JSONArray().apply { favs.forEach { put(it) } }

        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()

        var audioCount = 0
        var previewCount = 0
        val previews = store.voicePreviewFiles()
        val out = ctx.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("无法写入所选文件")
        BufferedOutputStream(out).use { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.setLevel(java.util.zip.Deflater.BEST_SPEED)

                fun putText(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                // 先写 manifest：以后格式升级时好判断
                putText(
                    MANIFEST,
                    JSONObject().apply {
                        put("app", "PrimaTune")
                        put("format", "primatune-backup-v1")
                        put("version", version)
                        put("exportedAt", System.currentTimeMillis())
                        put("takes", takes.size)
                        put("voices", voices.size)
                        put("favorites", favs.size)
                        put("previews", previews.size)
                    }.toString(2)
                )
                putText(TAKES, takesArr.toString())
                putText(VOICES, voicesArr.toString())
                putText(FAVS, favArr.toString())

                val added = HashSet<String>()
                for (t in takes) {
                    val f = store.fileOf(t)
                    if (!f.exists() || !f.isFile || f.length() == 0L) continue
                    if (!added.add(f.name)) continue
                    zip.putNextEntry(ZipEntry(AUDIO_PREFIX + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    audioCount++
                }
                // 每个音色的自定义试听模板（文件名是 hash，恢复时原样放回）
                for (f in previews) {
                    zip.putNextEntry(ZipEntry(PREVIEW_PREFIX + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    previewCount++
                }
            }
        }
        return Result(takes.size, voices.size, favs.size, audioCount, previewCount)
    }

    fun import(ctx: Context, store: Store, uri: Uri): Result {
        var takesText: String? = null
        var voicesText: String? = null
        var favText: String? = null
        val audioNames = HashSet<String>()
        val previewNames = HashSet<String>()

        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        BufferedInputStream(input).use { bis ->
            ZipInputStream(bis).use { zip ->
                var e: ZipEntry? = zip.nextEntry
                while (e != null) {
                    val name = e.name.orEmpty()
                    when {
                        name == TAKES -> takesText = zip.readBytes().toString(Charsets.UTF_8)
                        name == VOICES -> voicesText = zip.readBytes().toString(Charsets.UTF_8)
                        name == FAVS -> favText = zip.readBytes().toString(Charsets.UTF_8)
                        !e.isDirectory && name.startsWith(AUDIO_PREFIX) -> {
                            val f = store.restoreAudioFile(name, zip)
                            if (f != null) audioNames.add(f.name)
                        }
                        !e.isDirectory && name.startsWith(PREVIEW_PREFIX) -> {
                            val f = store.restorePreviewFile(name, zip)
                            if (f != null) previewNames.add(f.name)
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }

        val arr = JSONArray(takesText ?: throw IOException("备份里缺少 $TAKES"))
        val existing = store.loadTakes()
        val byId = existing.associateBy { it.id }.toMutableMap()
        var addedTakes = 0

        for (i in 0 until arr.length()) {
            val raw = Take.fromJson(arr.getJSONObject(i))
            // 记录必须能对上音频文件，否则恢复出来也是“点播放没声音”
            var fileName = raw.fileName
            if (fileName.isBlank() || !File(store.historyDir(), fileName).exists()) {
                fileName = audioNames.firstOrNull { it == raw.fileName }
                    ?: audioNames.firstOrNull { it.substringBeforeLast('.') == raw.id }
                    ?: ""
            }
            if (fileName.isBlank()) continue
            val id = fileName.substringBeforeLast('.')
            if (id.isBlank() || byId.containsKey(id)) continue
            val t = raw.copy(id = id, fileName = fileName)
            existing.add(t)
            byId[id] = t
            addedTakes++
        }
        existing.sortByDescending { it.createdAt }
        store.saveTakes(existing)

        // 自建音色：按 id 合并，已有的不覆盖
        val voiceArr = runCatching { JSONArray(voicesText ?: "[]") }.getOrDefault(JSONArray())
        val voices = store.loadCustomVoices()
        val voiceIds = voices.map { it.id }.toMutableSet()
        var addedVoices = 0
        for (i in 0 until voiceArr.length()) {
            val v = CustomVoice.fromJson(voiceArr.getJSONObject(i))
            if (v.id.isBlank() || v.id in voiceIds) continue
            voices.add(v); voiceIds.add(v.id); addedVoices++
        }
        if (addedVoices > 0) store.saveCustomVoices(voices)

        // 收藏：只保留“记录真的恢复了/本来就存在”的 id
        val favArr = runCatching { JSONArray(favText ?: "[]") }.getOrDefault(JSONArray())
        val favs = store.favTakes
        var addedFavs = 0
        for (i in 0 until favArr.length()) {
            val id = favArr.optString(i)
            if (id.isBlank() || id !in byId || id in favs) continue
            favs.add(id); addedFavs++
        }
        if (addedFavs > 0) store.favTakes = favs

        return Result(addedTakes, addedVoices, addedFavs, audioNames.size, previewNames.size)
    }
}
