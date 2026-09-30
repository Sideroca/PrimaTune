package com.sideroca.voicetuner

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 设置页 v0.3：接口 / 主题配色（64 套）/ 壁纸 / 关于。
 * 主题点选即换；壁纸主界面与设置页各自独立；全部只保存在本机。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var wpImg: ImageView
    private lateinit var wpScrim: View
    private lateinit var llCats: LinearLayout
    private lateinit var llPalettes: LinearLayout
    private lateinit var tvWpMainState: TextView
    private lateinit var tvWpPageState: TextView
    private lateinit var tvScrimMain: TextView
    private lateinit var tvScrimPage: TextView
    private lateinit var tvCardAlpha: TextView
    private lateinit var etKey: EditText
    private lateinit var etWs: EditText
    private lateinit var etModel: EditText
    private lateinit var llIndicator: LinearLayout
    private lateinit var pageTheme: ScrollView
    private lateinit var pageModel: ScrollView
    private lateinit var pageVoice: ScrollView
    private lateinit var pageRecords: ScrollView
    private lateinit var pageAbout: ScrollView
    private lateinit var tvPageTitle: TextView
    private lateinit var llDock: LinearLayout
    private lateinit var llProviders: LinearLayout
    private lateinit var llVoices: LinearLayout
    private lateinit var llRecords: LinearLayout
    private lateinit var btnSaveAll: PressButton
    private lateinit var cardPalette: LinearLayout
    private lateinit var cardIndicator: LinearLayout
    private lateinit var cardWallpaper: LinearLayout
    private lateinit var preview: ThemePreviewView
    private lateinit var btnPvMain: TextView
    private lateinit var btnPvPage: TextView
    private lateinit var tvPvHint: TextView
    private var previewPage = 0

    private val density = 0f   // 占位，运行时用 resources 取（保持字段顺序稳定）

    private var cat = "modern"
    /** 配色列表是否展开全部（懒建：默认只建前 20 套） */
    private var palShowAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        // 「本机原色」已下线：老设备若存的是空主题，兜底迁移到默认竹青
        if (store.themeId.isEmpty()) store.themeId = "tea"
        cat = Palettes.byId(store.themeId)?.group ?: "modern"

        wpImg = findViewById(R.id.wpImg)
        wpScrim = findViewById(R.id.wpScrim)
        llCats = findViewById(R.id.llCats)
        llPalettes = findViewById(R.id.llPalettes)
        tvWpMainState = findViewById(R.id.tvWpMainState)
        tvWpPageState = findViewById(R.id.tvWpPageState)
        tvScrimMain = findViewById(R.id.tvScrimMain)
        tvScrimPage = findViewById(R.id.tvScrimPage)
        tvCardAlpha = findViewById(R.id.tvCardAlpha)
        etKey = findViewById(R.id.etKey)
        etWs = findViewById(R.id.etWs)
        etModel = findViewById(R.id.etModel)
        // 模型名自动补全：输入第一个字母就弹下拉（数据来自 TtsModels 目录）
        (etModel as? android.widget.AutoCompleteTextView)?.let { ac ->
            ac.setAdapter(
                android.widget.ArrayAdapter(
                    this, android.R.layout.simple_dropdown_item_1line,
                    TtsModels.all.map { TtsModels.display(it) }
                )
            )
            ac.threshold = 1
        }
        llIndicator = findViewById(R.id.llIndicator)
        tvPageTitle = findViewById(R.id.tvPageTitle)
        pageModel = findViewById(R.id.pageModel)
        pageVoice = findViewById(R.id.pageVoice)
        pageTheme = findViewById(R.id.pageTheme)
        pageRecords = findViewById(R.id.pageRecords)
        pageAbout = findViewById(R.id.pageAbout)
        llDock = findViewById(R.id.llDock)
        llProviders = findViewById(R.id.llProviders)
        llVoices = findViewById(R.id.llVoices)
        llRecords = findViewById(R.id.llRecords)
        btnSaveAll = findViewById(R.id.btnSaveAll)
        preview = findViewById(R.id.preview)
        cardPalette = findViewById(R.id.cardPalette)
        cardIndicator = findViewById(R.id.cardIndicator)
        cardWallpaper = findViewById(R.id.cardWallpaper)
        btnPvMain = findViewById(R.id.btnPvMain)
        btnPvPage = findViewById(R.id.btnPvPage)
        tvPvHint = findViewById(R.id.tvPvHint)
        btnPvMain.setOnClickListener { previewPage = 0; updatePreview() }
        btnPvPage.setOnClickListener { previewPage = 1; updatePreview() }

        buildCatChips()
        renderPalettes()
        renderIndicatorColors()
        bindInterface()
        bindWallpaper()

        buildDock()
        buildProviders()
        buildVoices()
        buildRecords()
        btnSaveAll.setOnClickListener { saveAll() }
        applyThemeTab()
        selectPage(0)
    }

    override fun onResume() {
        super.onResume()
        applyLook()
    }

    private fun applyLook() {
        val c = Skin.colors(this)
        Skin.applyWindow(this, c)
        Skin.apply(window.decorView, c)
        Wp.applySlot(this, wpImg, wpScrim, store.wpPage, store.scrimPage, c.bg)
        // 坞与保存键不在 Skin 的"角色"体系里 → 必须在 Skin.apply 之后显式上色，才不会被它盖掉
        styleDock(c)
        updatePreview()
    }

    /** 预览台：把当前配色 + 壁纸 + 遮罩实时画出来 */
    private fun updatePreview() {
        val c = Skin.colors(this)
        val path = if (previewPage == 1) store.wpPage else store.wpMain
        val scrim = if (previewPage == 1) store.scrimPage else store.scrimMain
        val bmp = if (path.isNotEmpty() && File(path).exists()) Wp.decode(path, 600) else null
        preview.pageMode = previewPage
        preview.setData(c, bmp, scrim)
        styleSeg(btnPvMain, previewPage == 0, c)
        styleSeg(btnPvPage, previewPage == 1, c)
        tvPvHint.setTextColor(c.hint)
    }

    private fun styleSeg(tv: TextView, on: Boolean, c: Skin.Colors) {
        tv.background = Skin.shapeDp(
            this, if (on) c.acc else c.card2, if (on) c.acc else c.line, 100f, 100, 1f
        )
        tv.setTextColor(if (on) c.onAcc else c.dim)
    }

    // ---------------------------------------------------------------- 分页 + 坞

    private val pageTitles = listOf("模型", "音色", "主题", "记录", "关于")
    private val dockDefs = listOf("模型", "音色", "主题", "记录", "关于")
    /** 坞图标：手绘矢量（统一 24dp 画布/线宽），跨机型一致、跟主题变色 */
    private val dockIconRes = intArrayOf(
        R.drawable.ic_tab_model, R.drawable.ic_tab_voice, R.drawable.ic_tab_theme,
        R.drawable.ic_tab_record, R.drawable.ic_tab_about
    )
    private val dockItems = mutableListOf<LinearLayout>()
    private val dockPads = mutableListOf<android.widget.FrameLayout>()
    private val dockIcons = mutableListOf<ImageView>()
    private val dockLabels = mutableListOf<TextView>()
    private var selectedPage = 0

    private fun dp(px: Float): Int = (px * resources.displayMetrics.density).toInt()

    private fun buildDock() {
        // 坞整体留白：图标不再贴着卡片上沿，避免拥挤
        llDock.setPadding(0, dp(9f), 0, dp(6f))
        llDock.removeAllViews()
        dockItems.clear(); dockPads.clear(); dockIcons.clear(); dockLabels.clear()
        for (i in dockDefs.indices) {
            if (i > 0) {
                val dv = View(this)
                dv.layoutParams = LinearLayout.LayoutParams(dp(1f).coerceAtLeast(1), dp(17f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                dv.setBackgroundColor(0x1A786E60)
                llDock.addView(dv)
            }
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            // 图标垫片：选中时才出现（52×40dp —— 比原来加高，不再扁平）；图标统一 22dp 居中
            val pad = android.widget.FrameLayout(this)
            pad.layoutParams = LinearLayout.LayoutParams(dp(52f), dp(40f))
            pad.elevation = 3f * resources.displayMetrics.density
            val iv = ImageView(this)
            iv.layoutParams = android.widget.FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
            iv.setImageResource(dockIconRes[i])
            pad.addView(iv)

            val lb = TextView(this)
            lb.text = dockDefs[i]
            lb.textSize = 11f
            lb.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }

            item.addView(pad)
            item.addView(lb)
            val idx = i
            item.isClickable = true
            item.isFocusable = true
            item.setOnClickListener { selectPage(idx) }
            llDock.addView(item)
            dockItems.add(item); dockPads.add(pad); dockIcons.add(iv); dockLabels.add(lb)
        }
    }

    private fun selectPage(i: Int) {
        selectedPage = i
        val pages = listOf(pageModel, pageVoice, pageTheme, pageRecords, pageAbout)
        val d = resources.displayMetrics.density
        pages.forEachIndexed { idx, v ->
            if (idx == i) {
                v.visibility = View.VISIBLE
                v.translationX = 24f * d
                v.alpha = 0f
                v.animate().translationX(0f).alpha(1f).setDuration(180).start()
            } else {
                v.visibility = View.GONE
            }
        }
        tvPageTitle.text = pageTitles.getOrElse(i) { "设置" }
        styleDock(Skin.colors(this))
    }

    /** 坞 + 保存键的显式上色（浅/深主题各一套） */
    /** 坞 + 保存键的显式上色（颜色走 11 槽；形状/质感走 4 参数 —— 对齐《夕汀前端规范》） */
    private fun styleDock(c: Skin.Colors) {
        val pal = Palettes.byId(store.themeId)
        val cardR = (pal?.cardRadius ?: 14).toFloat()
        val btnR = (pal?.btnRadius ?: 11).toFloat()
        val elev = (pal?.elev ?: 0).toFloat()
        val solid = pal?.solidBtn ?: false

        llDock.background = Skin.shapeDp(this, c.card, c.line, cardR, 92, 1f)
        llDock.elevation = elev * 0.6f * resources.displayMetrics.density

        val onFill = (c.acc and 0x00FFFFFF) or (0x2B shl 24)      // 主色 17% 透明
        dockPads.forEachIndexed { i, pad ->
            pad.background = if (i == selectedPage) {
                Skin.shapeDp(this, onFill, 0xFFFFFFFF.toInt(), btnR, 100, 1.5f)
            } else {
                Skin.shapeDp(this, 0x00000000, null, btnR)
            }
            dockLabels[i].setTextColor(if (i == selectedPage) c.acc else c.dim)
            // 图标是"彩色"的（仿表情图标）→ 不再 tint，只让垫片与文字表示选中
            dockIcons[i].imageTintList = null
        }

        // 保存键：保持"透明玻璃"（玲珑调音的取向，非硬性规则）——solidBtn 只决定玻璃浓度与描边强度
        //   实心档主题 → 面 accent@22% / 描边 accent@40%
        //   描边档主题 → 面 accent@10% / 描边 accent@60%
        // 字色一律 accent；颜色全走语义槽位（《夕汀前端规范》〇-6），换主题即换色
        val faceAlpha = if (solid) 0x38 else 0x1A          // 22% / 10%
        val strokeAlpha = if (solid) 0x66 else 0x99        // 40% / 60%
        val glassFace = (c.acc and 0x00FFFFFF) or (faceAlpha shl 24)
        val glassStroke = (c.acc and 0x00FFFFFF) or (strokeAlpha shl 24)
        btnSaveAll.applyTheme(glassFace, glassStroke, c.acc, btnR, elev, false)
    }

    // ---------------------------------------------------------------- 模型页

    /** Provider 列表（当前只有阿里云百炼；接第二家时把这段抽成 TtsProvider 实现即可） */
    private fun buildProviders() {
        llProviders.removeAllViews()
        val c = Skin.colors(this)
        val cur = TextView(this)
        cur.text = "阿里云百炼 · CosyVoice"
        cur.setTextColor(c.txt)
        cur.textSize = 15f
        llProviders.addView(cur)

        val sum = TextView(this)
        sum.text = "v3.5-plus · 业务空间 " + store.workspace + " · " +
                (if (store.apiKey.isBlank()) "Key 未配置" else "Key 已配置")
        sum.setTextColor(c.dim)
        sum.textSize = 12f
        sum.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(3f) }
        llProviders.addView(sum)
    }

    // ---------------------------------------------------------------- 音色页

    private fun buildVoices() {
        llVoices.removeAllViews()
        val c = Skin.colors(this)
        val hidden = store.hiddenVoices

        fun addRow(name: String, id: String, builtIn: Boolean, hiddenRow: Boolean = false) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(0, dp(9f), 0, dp(9f))

            val head = LinearLayout(this)
            head.orientation = LinearLayout.HORIZONTAL
            head.gravity = Gravity.CENTER_VERTICAL
            val nm = TextView(this)
            nm.text = name
            nm.setTextColor(c.txt)
            nm.textSize = 15f
            head.addView(nm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val act = TextView(this)
            act.text = if (hiddenRow) "恢复" else "删除"
            act.setTextColor(if (hiddenRow) c.acc else 0xFFC44E4E.toInt())
            act.textSize = 14f
            act.setPadding(dp(14f), dp(6f), dp(4f), dp(6f))
            act.isClickable = true
            act.isFocusable = true
            act.setOnClickListener {
                if (hiddenRow) {
                    val h = store.hiddenVoices; h.remove(id); store.hiddenVoices = h
                } else if (builtIn) {
                    val h = store.hiddenVoices; h.add(id); store.hiddenVoices = h
                } else {
                    val list = store.loadCustomVoices()
                    list.removeAll { it.id == id }
                    store.saveCustomVoices(list)
                }
                buildVoices(); buildProviders()
            }
            head.addView(act)
            box.addView(head)

            val idv = TextView(this)
            idv.text = id
            idv.setTextColor(c.hint)
            idv.textSize = 11f
            idv.setTextIsSelectable(true)
            idv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
            box.addView(idv)
            llVoices.addView(box)
        }

        VoiceCatalog.builtIn.forEach { if (it.second !in hidden) addRow(it.first, it.second, true) }
        store.loadCustomVoices().forEach { addRow(it.name, it.id, false) }
        val hiddenOnes = VoiceCatalog.builtIn.filter { it.second in hidden }
        if (hiddenOnes.isNotEmpty()) {
            val sec = TextView(this)
            sec.text = "已隐藏"
            sec.setTextColor(c.dim)
            sec.textSize = 12f
            sec.setPadding(0, dp(14f), 0, dp(1f))
            llVoices.addView(sec)
            hiddenOnes.forEach { addRow(it.first, it.second, true, hiddenRow = true) }
        }
    }

    // ---------------------------------------------------------------- 记录页

    /** 记录页：试听（用系统 MediaPlayer 直接播本地文件） */
    private var recPlayer: android.media.MediaPlayer? = null

    private fun playHistory(t: Take) {
        try {
            recPlayer?.release()
            val f = java.io.File(java.io.File(filesDir, "history"), t.fileName)
            if (!f.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            recPlayer = android.media.MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败：" + (e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录页：是否只看收藏 */
    private var recOnlyFav = false

    private fun buildRecords() {
        llRecords.removeAllViews()
        val c = Skin.colors(this)
        val all = store.loadTakes()
        val favs = store.favTakes

        // 顶部筛选：全部 / ★ 收藏
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(0, 0, 0, dp(2f))
        fun chip(label: String, on: Boolean, tap: () -> Unit) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 12.5f
            tv.setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
            tv.background = Skin.shapeDp(
                this, if (on) c.acc else c.card2, if (on) c.acc else c.line, 100f, 100, 1f
            )
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener { tap() }
            bar.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) })
        }
        chip("全部", !recOnlyFav) { recOnlyFav = false; buildRecords() }
        chip("★ 收藏（" + favs.size + "）", recOnlyFav) { recOnlyFav = true; buildRecords() }
        llRecords.addView(bar)

        val takes = if (recOnlyFav) all.filter { it.id in favs } else all
        if (takes.isEmpty()) {
            val tv = TextView(this)
            tv.text = if (recOnlyFav) "还没有收藏的记录（点记录右边的星星即可收藏）" else "暂无记录"
            tv.setTextColor(c.dim)
            tv.textSize = 13f
            tv.setPadding(0, dp(10f), 0, 0)
            llRecords.addView(tv)
            return
        }

        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        takes.forEach { t ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(0, dp(10f), 0, dp(10f))

            // 头部：文本 ＋ 右上角删除 ✕（字形 9.5sp；判定 42dp，贴卡片右上内沿）
            val head = android.widget.FrameLayout(this)
            val a = TextView(this)
            a.text = t.text
            a.setTextColor(c.txt)
            a.textSize = 14f
            a.maxLines = 2
            a.ellipsize = android.text.TextUtils.TruncateAt.END
            a.setPadding(0, 0, dp(20f), 0)
            head.addView(a, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            a.setOnClickListener {
                val ex = a.maxLines != Int.MAX_VALUE
                a.maxLines = if (ex) Int.MAX_VALUE else 2
                a.ellipsize = if (ex) null else android.text.TextUtils.TruncateAt.END
            }
            val x = TextView(this)
            x.text = "✕"
            x.textSize = 9f
            x.setTextColor(c.dim)
            x.gravity = Gravity.TOP or Gravity.END
            x.setPadding(0, dp(3.5f), dp(3.5f), 0)     // 字形内距 3.5dp
            x.layoutParams = android.widget.FrameLayout.LayoutParams(
                dp(48f), dp(48f), Gravity.TOP or Gravity.END   // 判定 48dp，贴上/右两边
            )
            x.isClickable = true
            x.isFocusable = true
            x.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("删除这条记录？")
                    .setMessage(t.text.take(60))
                    .setPositiveButton("删除") { _, _ ->
                        val list = store.loadTakes()
                        list.removeAll { it.id == t.id }
                        store.saveTakes(list)
                        try {
                            java.io.File(java.io.File(filesDir, "history"), t.fileName).delete()
                        } catch (e: Exception) {
                            // ignore
                        }
                        buildRecords()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            head.addView(x)
            box.addView(head)

            // 常驻信息：时间 · 音色 · 格式 · 字数
            val b = TextView(this)
            b.text = fmt.format(java.util.Date(t.createdAt)) + " · " + t.voiceName + " · " +
                    t.format + " · " + t.text.length + " 字"
            b.setTextColor(c.dim)
            b.textSize = 11.5f
            b.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
            box.addView(b)

            // 收起时隐藏的「属性」：语速 / 音调 / 音量 / 模型 / 种子
            val d2 = TextView(this)
            val modelShown = if (t.model.isBlank()) store.lastModel else t.model
            d2.text = "语速 " + t.rate + " · 音调 " + t.pitch + " · 音量 " + t.volume +
                    " · 模型 " + modelShown + " · 🎲 " + t.seed
            d2.setTextColor(c.txt)                 // 与正文一致（不再发灰"隐形"）
            d2.textSize = 13f
            d2.visibility = View.GONE
            d2.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f) }
            box.addView(d2)

            val bar2 = LinearLayout(this)
            bar2.orientation = LinearLayout.HORIZONTAL

            // 试听（记录页也要有）
            val play = TextView(this)
            play.text = "▶ 播放"
            play.setTextColor(c.dim)
            play.textSize = 11.5f
            play.setPadding(0, dp(6f), dp(16f), dp(2f))
            play.isClickable = true
            play.isFocusable = true
            play.setOnClickListener { playHistory(t) }
            bar2.addView(play)

            val star = LinearLayout(this)
            star.orientation = LinearLayout.HORIZONTAL
            star.gravity = Gravity.CENTER_VERTICAL
            star.setPadding(0, dp(6f), dp(14f), dp(2f))
            star.isClickable = true
            star.isFocusable = true
            val starIcon = ImageView(this)
            starIcon.layoutParams = LinearLayout.LayoutParams(dp(15f), dp(15f))
            star.addView(starIcon)
            val starLabel = TextView(this)
            starLabel.textSize = 11.5f
            starLabel.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(2f) }
            star.addView(starLabel)
            val refreshStar = {
                val fav = t.id in store.favTakes
                starIcon.setImageResource(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_hollow)
                starIcon.imageTintList = android.content.res.ColorStateList.valueOf(c.dim)
                starLabel.text = "收藏"                 // 文字恒定，只有星星上色/变实心
                starLabel.setTextColor(c.dim)
            }
            refreshStar()
            star.setOnClickListener {
                val set = store.favTakes
                if (set.contains(t.id)) set.remove(t.id) else set.add(t.id)
                store.favTakes = set
                refreshStar()
            }
            bar2.addView(star)

            val attr = TextView(this)
            attr.text = "属性"
            attr.setTextColor(c.dim)
            attr.textSize = 11.5f
            attr.setPadding(0, dp(6f), 0, dp(2f))
            attr.isClickable = true
            attr.isFocusable = true
            attr.setOnClickListener {
                val show = d2.visibility != View.VISIBLE
                d2.visibility = if (show) View.VISIBLE else View.GONE
                attr.text = if (show) "收起" else "属性"
            }
            bar2.addView(attr)

            box.addView(bar2)

            llRecords.addView(box)
        }
    }

    /** 保存键：把「模型」页的接口字段落盘 */
    private fun saveAll() {
        store.apiKey = etKey.text.toString().trim()
        store.workspace = etWs.text.toString().trim()
        // 下拉项是「id   说明」，这里只取回 id
        store.lastModel = TtsModels.idOf(etModel.text.toString())
        buildProviders()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- 当前音色指示符颜色
    private val indicatorOptions = listOf(
        "亮蓝" to "#2E8FFF",
        "樱花粉" to "#FFAFC5",
        "极光绿" to "#2FE39B",
        "极光紫" to "#A56BFF",
        "雾蓝灰" to "#8AA0B6",
        "橙光橙" to "#FF8A3D",
        "银白灰" to "#D3DAE3"
    )

    private fun renderIndicatorColors() {
        llIndicator.removeAllViews()
        val c = Skin.colors(this)
        for ((name, hex) in indicatorOptions) {
            val col = try {
                android.graphics.Color.parseColor(hex)
            } catch (e: Exception) {
                0xFF2FE39B.toInt()
            }
            val selected = store.indicatorColor.equals(hex, ignoreCase = true)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            row.background = Skin.shapeDp(
                this, c.card2, if (selected) c.acc else c.line, 10f, 100, if (selected) 2f else 1f
            )
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            row.layoutParams = lp

            val sw = View(this)
            sw.background = Skin.shapeDp(this, col, null, 6f)
            row.addView(sw, LinearLayout.LayoutParams(dp(22), dp(22)))

            val tv = TextView(this)
            tv.text = name + "   " + hex
            tv.setTextColor(c.txt)
            tv.textSize = 14f
            tv.setPadding(dp(12), 0, 0, 0)
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // 实时预览：就是下拉页里那个小波形，按这个颜色画
            val ind = VoiceIndicatorView(this)
            ind.indicatorColor = col
            ind.showStatic()
            row.addView(ind, LinearLayout.LayoutParams(dp(22), dp(22)))

            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener {
                store.indicatorColor = hex
                renderIndicatorColors()
            }
            llIndicator.addView(row)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- 主题
    private fun buildCatChips() {
        llCats.removeAllViews()
        val c = Skin.colors(this)
        val defs = listOf(
            "modern" to "现代经典 18",
            "chinese" to "🏮 中国传统色 46",
            "attrs" to "壁纸属性"
        )
        for ((id, label) in defs) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 13f
            tv.setPadding(dp(12), dp(6), dp(12), dp(6))
            val sel = id == cat
            tv.isSelected = sel
            tv.background = Skin.shapeDp(this, if (sel) c.acc else c.card2, if (sel) null else c.line, 100f)
            tv.setTextColor(if (sel) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener {
                cat = id
                palShowAll = false
                buildCatChips()
                renderPalettes()
                applyThemeTab()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            tv.layoutParams = lp
            llCats.addView(tv)
        }
    }

    /** 「属性设置」= 只看壁纸与浓度；配色分类 = 看配色与指示符色 */
    private fun applyThemeTab() {
        val attrs = cat == "attrs"
        cardPalette.visibility = if (attrs) View.GONE else View.VISIBLE
        cardIndicator.visibility = if (attrs) View.GONE else View.VISIBLE
        cardWallpaper.visibility = if (attrs) View.VISIBLE else View.GONE
    }

    private fun renderPalettes() {
        llPalettes.removeAllViews()
        val c = Skin.colors(this)
        if (cat == "attrs") return        // 「属性设置」时整张配色卡隐藏
        val list = Palettes.all.filter { it.group == cat }
        // 懒建：默认只建前 20 行，其余点「显示全部」
        val shown = if (palShowAll) list else list.take(20)
        for (p in shown) llPalettes.addView(palRow(p, c))
        if (list.size > 20) {
            val more = TextView(this)
            more.text = if (palShowAll) "收起（只显示前 20 套）" else "显示全部（共 " + list.size + " 套）"
            more.setTextColor(c.dim)
            more.textSize = 13f
            more.gravity = Gravity.CENTER
            // 整条都能点：固定 54dp 高、左右贯穿卡片宽度（截图里点文字下方那一块也算）
            more.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54f)
            )
            more.isClickable = true
            more.isFocusable = true
            more.setOnClickListener {
                palShowAll = !palShowAll
                renderPalettes()
            }
            llPalettes.addView(more)
        }
    }

    private fun palRow(p: Pal?, c: Skin.Colors): View {
        val id = p?.id ?: ""
        val selected = store.themeId == id
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(10), dp(12), dp(10))
        row.isSelected = selected
        row.background = Skin.shapeDp(this, c.card2, if (selected) c.acc else c.line, 10f, 100, if (selected) 2f else 1f)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        row.layoutParams = lp

        val name = TextView(this)
        name.text = p?.name ?: "本机原色（v0.1 深蓝）"
        name.textSize = 14f
        name.setTextColor(c.txt)
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val cols = if (p == null) {
            listOf(0xFF0E1116.toInt(), 0xFF151A22.toInt(), 0xFF4F8CFF.toInt(), 0xFF7A5CFF.toInt())
        } else {
            listOf(p.bg, p.card, p.accent, if (p.barBg != 0) p.barBg else p.accent)
        }
        for (col in cols) {
            val strip = View(this)
            val slp = LinearLayout.LayoutParams(dp(16), dp(16))
            slp.marginStart = dp(6)
            strip.layoutParams = slp
            strip.background = Skin.shapeDp(this, col, null, 8f)
            row.addView(strip)
        }

        row.setOnClickListener {
            // 原地换肤：不 recreate()，滚动位置保持不变，只有颜色变
            val keepY = pageTheme.scrollY
            store.themeId = id
            applyLook()
            buildCatChips()
            renderPalettes()
            renderIndicatorColors()
            pageTheme.scrollTo(0, keepY)
        }
        return row
    }

    // ---------------------------------------------------------------- 接口
    private fun bindInterface() {
        etKey.setText(store.apiKey)
        etWs.setText(store.workspace)
        etModel.setText(store.lastModel)

        findViewById<TextView>(R.id.btnPaste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                etKey.setText(clip.getItemAt(0).coerceToText(this).toString().trim())
                toast("已粘贴到 API Key")
            } else {
                toast("剪贴板为空")
            }
        }
        findViewById<TextView>(R.id.btnSaveKey).setOnClickListener {
            store.apiKey = etKey.text.toString().trim()
            store.workspace = etWs.text.toString().trim()
            store.lastModel = etModel.text.toString().trim()
            toast("已保存")
        }
    }

    // ---------------------------------------------------------------- 壁纸
    private fun bindWallpaper() {
        val sbScrimMain = findViewById<SeekBar>(R.id.sbScrimMain)
        val sbScrimPage = findViewById<SeekBar>(R.id.sbScrimPage)
        val sbCardAlpha = findViewById<SeekBar>(R.id.sbCardAlpha)

        sbScrimMain.progress = store.scrimMain
        tvScrimMain.text = "${store.scrimMain}%"
        sbScrimPage.progress = store.scrimPage
        tvScrimPage.text = "${store.scrimPage}%"
        sbCardAlpha.progress = store.cardAlphaPct
        tvCardAlpha.text = "${store.cardAlphaPct}%"

        sbScrimMain.setOnSeekBarChangeListener(seek { p ->
            store.scrimMain = p
            tvScrimMain.text = "$p%"
            updatePreview()
        })
        sbScrimPage.setOnSeekBarChangeListener(seek { p ->
            store.scrimPage = p
            tvScrimPage.text = "$p%"
            Wp.applySlot(this, wpImg, wpScrim, store.wpPage, store.scrimPage, Skin.colors(this).bg)
            updatePreview()
        })
        sbCardAlpha.setOnSeekBarChangeListener(seek { p ->
            store.cardAlphaPct = p
            tvCardAlpha.text = "$p%"
            Skin.apply(window.decorView, Skin.colors(this))
            updatePreview()
        })

        findViewById<TextView>(R.id.btnWpMain).setOnClickListener { pick("main") }
        findViewById<TextView>(R.id.btnWpMainClear).setOnClickListener {
            Wp.clear(this, "main")
            store.wpMain = ""
            refreshWpStates()
            toast("已清除主界面壁纸")
        }
        findViewById<TextView>(R.id.btnWpPage).setOnClickListener { pick("page") }
        findViewById<TextView>(R.id.btnWpPageClear).setOnClickListener {
            Wp.clear(this, "page")
            store.wpPage = ""
            refreshWpStates()
            applyLook()
            toast("已清除设置页壁纸")
        }
        findViewById<TextView>(R.id.btnWpReset).setOnClickListener {
            Wp.clear(this, "main")
            Wp.clear(this, "page")
            store.wpMain = ""
            store.wpPage = ""
            store.scrimMain = 35
            store.scrimPage = 35
            store.cardAlphaPct = 100
            sbScrimMain.progress = 35
            tvScrimMain.text = "35%"
            sbScrimPage.progress = 35
            tvScrimPage.text = "35%"
            sbCardAlpha.progress = 100
            tvCardAlpha.text = "100%"
            refreshWpStates()
            applyLook()
            toast("已恢复默认")
        }
        refreshWpStates()
    }

    private fun seek(f: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                f(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

    private fun refreshWpStates() {
        tvWpMainState.text = if (store.wpMain.isNotEmpty() && File(store.wpMain).exists()) "已设置" else "未设置"
        tvWpPageState.text = if (store.wpPage.isNotEmpty() && File(store.wpPage).exists()) "已设置" else "未设置"
    }

    private fun pick(slot: String) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, if (slot == "main") REQ_MAIN else REQ_PAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val slot = if (requestCode == REQ_MAIN) "main" else "page"
        if (Wp.import(this, uri, slot)) {
            val path = Wp.file(this, slot).absolutePath
            if (slot == "main") store.wpMain = path else store.wpPage = path
            refreshWpStates()
            applyLook()
            toast("壁纸已应用")
        } else {
            toast("图片读取失败")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_MAIN = 2001
        private const val REQ_PAGE = 2002
    }
}
