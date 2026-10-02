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
    private lateinit var etProvider: android.widget.AutoCompleteTextView
    private lateinit var etBaseUrl: EditText
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
    private lateinit var ivIconPreview: ImageView
    private lateinit var llIconStyles: LinearLayout
    private lateinit var etEntryName: EditText
    private lateinit var llTips: LinearLayout

    private var cat = "modern"
    /** 配色列表是否展开全部（懒建：默认只建前 20 套） */
    private var palShowAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        store.hidePresetVoicesOnce()
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
            ac.setAdapter(ContainsAdapter(this, TtsModels.all.map { TtsModels.display(it) }))
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
        cardPalette = findViewById(R.id.cardPalette)
        cardIndicator = findViewById(R.id.cardIndicator)
        cardWallpaper = findViewById(R.id.cardWallpaper)
        ivIconPreview = findViewById(R.id.ivIconPreview)
        llIconStyles = findViewById(R.id.llIconStyles)
        etEntryName = findViewById(R.id.etEntryName)
        llTips = findViewById(R.id.llTips)
        setupIconWorkshop()      // 必须放在上面几个 findViewById 之后（否则 lateinit 未初始化 → 崩）

        buildCatChips()
        renderPalettes()
        renderIndicatorColors()
        bindInterface()
        bindWallpaper()

        buildDock()
        buildProviders()
        buildVoices()
        buildRecords()
        bindAbout()
        btnSaveAll.setOnClickListener { saveAll() }
        applyThemeTab()
        selectPage(0)
    }

    override fun onResume() {
        super.onResume()
        applyLook()
        // Store = 唯一数据源：首页/本页任何增删改（音色、记录）回来即同步
        buildVoices()
        buildProviders()
        buildRecords()
    }

    private fun applyLook() {
        val c = Skin.colors(this)
        Skin.applyWindow(this, c)
        Skin.apply(window.decorView, c)
        Wp.applySlot(this, wpImg, wpScrim, store.wpPage, store.scrimPage, c.bg)
        // 坞与保存键不在 Skin 的"角色"体系里 → 必须在 Skin.apply 之后显式上色，才不会被它盖掉
        styleDock(c)
        styleDropdowns(c)
        renderIconStyleChips()
        buildTips()
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
    private var eqBars: EqBarsView? = null
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
                // 跟随主题的线色（原来写死 0x1A786E60，所有主题都不变）
                val lineCol = Skin.colors(this).line
                dv.setBackgroundColor((lineCol and 0x00FFFFFF) or (0x80 shl 24))
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
            if (i == 1) {
                // 「音色」：三条动态音柱（切到本页时长短起伏）
                val eq = EqBarsView(this)
                eq.layoutParams = android.widget.FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
                pad.addView(eq)
                eqBars = eq
            } else {
                val iv = ImageView(this)
                iv.layoutParams = android.widget.FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
                iv.setImageResource(dockIconRes[i])
                pad.addView(iv)
            }

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
            fx(item)
            llDock.addView(item)
            dockItems.add(item); dockPads.add(pad)
            dockIcons.add(pad.getChildAt(0) as? ImageView ?: ImageView(this))   // 音色那格不是 ImageView，占位以免索引错位
            dockLabels.add(lb)
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
        // 「音色」图标的均衡器动效：到这一页起伏，离开就停
        if (i == 1) eqBars?.start() else eqBars?.stop()
        styleDock(Skin.colors(this))
        dockPads.getOrNull(i)?.let { pad ->
            pad.scaleX = 0.9f; pad.scaleY = 0.9f; pad.alpha = 0.5f
            pad.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
        }
    }

    /** 三个自动补全框的下拉弹窗：浅底 + 主题描边（原来跟随系统，深色很突兀） */
    private fun styleDropdowns(c: Skin.Colors) {
        val bg = Skin.shapeDp(this, c.card, c.line, 12f, 100, 1f)
        listOf(R.id.etProvider, R.id.etModel, R.id.etKey).forEach { id ->
            (findViewById<View>(id) as? android.widget.AutoCompleteTextView)
                ?.setDropDownBackgroundDrawable(bg)
        }
    }

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
                Skin.shapeDp(this, onFill, (c.acc and 0x00FFFFFF) or (0x99 shl 24), btnR, 100, 1.5f)
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
    /** 模型页第二张卡：只列"厂商"（引擎/模型在上面那栏选），每行 3 个 chip */
    private fun buildProviders() {
        llProviders.removeAllViews()
        val c = Skin.colors(this)
        // 不规则排列：每行个数 3/2/3/2/3…，并给每行一点左偏移，避免"整整齐齐一排排"
        val perRow = intArrayOf(3, 2, 3, 2, 3, 2)
        val leftShift = intArrayOf(0, 22, 8, 30, 12, 26)
        val rowStart = ArrayList<Int>()
        var acc = 0
        for (n in perRow) {
            if (acc >= TtsProviders.all.size) break
            rowStart.add(acc)
            acc += n
        }
        var row: LinearLayout? = null
        TtsProviders.all.forEachIndexed { i, p ->
            if (rowStart.contains(i)) {
                val ri = rowStart.indexOf(i)
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(8)
                        leftMargin = dp(leftShift.getOrElse(ri) { 0 })
                    }
                }
                llProviders.addView(row)
            }
            val sel = p.id == store.providerId
            val tv = TextView(this)
            tv.text = p.name
            tv.textSize = 12.5f
            tv.isSingleLine = true          // 关键：chip 内部不许换行（"自定义渠道"原来被折成两行）
            tv.maxLines = 1
            tv.isSelected = sel
            tv.setPadding(dp(12), dp(7), dp(12), dp(7))
            tv.background = Skin.shapeDp(this, if (sel) c.acc else c.card2, if (sel) c.acc else c.line, 96f, 100, 1f)
            tv.setTextColor(if (sel) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                // 点厂商 chip = 选这家（原来 chips 只是摆设、点了没反应）
                store.providerId = p.id
                etProvider.setText(TtsProviders.display(p), false)
                applyProvider(p.id)
                buildProviders()
            }
            row?.addView(
                tv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8) }
            )
        }
    }

    // ---------------------------------------------------------------- 音色页

    private fun buildVoices() {
        llVoices.removeAllViews()
        val c = Skin.colors(this)
        val hidden = store.hiddenVoices

        fun addRow(name: String, id: String, builtIn: Boolean) {
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
            act.text = "删除"
            act.setTextColor(dangerColor())
            act.textSize = 14f
            act.setPadding(dp(14f), dp(6f), dp(4f), dp(6f))
            act.isClickable = true
            act.isFocusable = true
            fx(act)
            act.setOnClickListener {
                // 删除 = 直接从列表移除（内置的记入本机隐藏表，不再有「已隐藏」区）
                val dd = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("删除这个音色？")
                    .setMessage("「" + name + "」将从音色列表移除。")
                    .setPositiveButton("删除") { _, _ ->
                        if (builtIn) {
                            val h = store.hiddenVoices; h.add(id); store.hiddenVoices = h
                        } else {
                            val list = store.loadCustomVoices()
                            list.removeAll { it.id == id }
                            store.saveCustomVoices(list)
                        }
                        buildVoices(); buildProviders()
                    }
                    .setNegativeButton("取消", null)
                    .create()
                dd.setOnShowListener {
                    skinDialog(dd)
                    dd.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(dangerColor())
                }
                dd.show()
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
        if (llVoices.childCount == 0) {
            val tv = TextView(this)
            tv.text = "还没有音色：在主页用「＋ 建音色」上传样本，或粘贴自定义音色 ID。"
            tv.setTextColor(c.hint)
            tv.textSize = 12.5f
            tv.setPadding(0, dp(10f), 0, dp(2f))
            llVoices.addView(tv)
        }
    }

    // ---------------------------------------------------------------- 记录页

    /** 记录页：分享这条记录（FileProvider，别的 App 可直接收） */
    private fun shareHistory(t: Take) {
        try {
            val f = store.fileOf(t)
            if (!f.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, packageName + ".fileprovider", f
            )
            val i = Intent(Intent.ACTION_SEND).apply {
                type = if (t.fileName.endsWith("mp3")) "audio/mpeg" else "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "分享到"))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败：" + (e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录页：试听（用系统 MediaPlayer 直接播本地文件） */
    private var recPlayer: android.media.MediaPlayer? = null

    private fun playHistory(t: Take) {
        try {
            recPlayer?.release()
            val f = store.fileOf(t)
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
                this, if (on) c.acc else c.card2, if (on) c.acc else c.line, 96f, 100, 1f
            )
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
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
            val x = TextView(this)
            x.text = "✕"
            x.textSize = 9f
            x.setTextColor(Skin.Colors.mix(c.dim, c.bg, 0.15f))
            x.gravity = Gravity.TOP or Gravity.END
            x.setPadding(0, dp(3.5f), dp(3.5f), 0)     // 字形内距 3.5dp
            x.layoutParams = android.widget.FrameLayout.LayoutParams(
                dp(48f), dp(48f), Gravity.TOP or Gravity.END   // 判定 48dp，贴上/右两边
            )
            x.isClickable = true
            x.isFocusable = true
            fx(x)
            x.setOnClickListener {
                val dd = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("删除这条记录？")
                    .setMessage(t.text.take(60))
                    .setPositiveButton("删除") { _, _ ->
                        val list = store.loadTakes()
                        list.removeAll { it.id == t.id }
                        store.saveTakes(list)
                        try {
                            store.fileOf(t).delete()
                        } catch (e: Exception) {
                            // ignore
                        }
                        buildRecords()
                    }
                    .setNegativeButton("取消", null)
                    .create()
                dd.setOnShowListener { skinDialog(dd) }
                dd.show()
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
            // 正文 / 常驻信息 / 参数行 —— 点哪边都展开收起（与首页同款）
            fun toggleAll() {
                val ex = a.maxLines != Int.MAX_VALUE
                a.maxLines = if (ex) Int.MAX_VALUE else 2
                a.ellipsize = if (ex) null else android.text.TextUtils.TruncateAt.END
                d2.visibility = if (ex) View.VISIBLE else View.GONE
            }
            a.setOnClickListener { toggleAll() }
            b.setOnClickListener { toggleAll() }
            d2.setOnClickListener { toggleAll() }
            box.addView(d2)

            val bar2 = LinearLayout(this)
            bar2.orientation = LinearLayout.HORIZONTAL

            val share = TextView(this)
            share.text = "分享"
            share.setTextColor(c.dim)
            share.textSize = 11.5f
            share.setPadding(0, dp(6f), dp(16f), dp(2f))
            share.isClickable = true
            share.isFocusable = true
            fx(share)
            share.setOnClickListener { shareHistory(t) }
            bar2.addView(share)

            // 试听（记录页也要有）
            val play = TextView(this)
            play.text = "▶ 播放"
            play.setTextColor(c.dim)
            play.textSize = 11.5f
            play.setPadding(0, dp(6f), dp(16f), dp(2f))
            play.isClickable = true
            play.isFocusable = true
            fx(play)
            play.setOnClickListener { playHistory(t) }
            bar2.addView(play)

            val star = LinearLayout(this)
            star.orientation = LinearLayout.HORIZONTAL
            star.gravity = Gravity.CENTER_VERTICAL
            star.setPadding(0, dp(6f), dp(14f), dp(2f))
            star.isClickable = true
            star.isFocusable = true
            val starIcon = StarView(this)
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
                starIcon.colorSolid = c.dim
                starIcon.filled = fav
                starLabel.text = "收藏"                 // 文字恒定，只有星星上色/变实心
                starLabel.setTextColor(c.dim)
            }
            refreshStar()
            fx(star)
            star.setOnClickListener {
                val set = store.favTakes
                if (set.contains(t.id)) set.remove(t.id) else set.add(t.id)
                store.favTakes = set
                refreshStar()
            }
            bar2.addView(star)

            box.addView(bar2)

            // 整卡可点展开/收起（与首页一致；去掉冗余的「属性」按钮）
            box.isClickable = true
            box.setOnClickListener { toggleAll() }

            llRecords.addView(box)
        }
    }

    /** 保存键：把「模型」页的接口字段落盘 */
    private fun saveAll() {
        saveProviderFields()
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
            row.isSelected = selected          // Skin 按 isSelected 重绘 bg_btn 角色 → 不设就会丢高亮
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
            fx(row)
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
            tv.background = Skin.shapeDp(this, if (sel) c.acc else c.card2, if (sel) null else c.line, 96f)   // 96 避开 Skin 的 chip 角色(100dp)，否则被重绘成浅色
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
            fx(tv)
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

        fx(row)
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
        etProvider = findViewById(R.id.etProvider)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etKey.setText(store.apiKey)
        etWs.setText(store.workspace)
        etModel.setText(store.lastModel)
        setupProviderPicker()

        fx(findViewById(R.id.btnPaste))
        fx(findViewById(R.id.btnSaveKey))
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
            saveProviderFields()
            toast("已保存")
        }
    }

    /** P1：厂商选择（输入首字母即匹配）→ 切换该厂商的 Key 与模型清单 */
    private fun setupProviderPicker() {
        etProvider.setAdapter(
            ContainsAdapter(this, TtsProviders.all.map { TtsProviders.display(it) })
        )
        etProvider.threshold = 1
        val cur = TtsProviders.byId(store.providerId) ?: TtsProviders.all.first()
        etProvider.setText(TtsProviders.display(cur), false)
        applyProvider(cur.id)
        // 关键：在厂商框里"直接打字"也要立刻切换（否则模型清单还是上一家的，输入 s 出来的是千问的模型）
        etProvider.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(t: android.text.Editable?) {
                val typed = t?.toString().orEmpty().trim()
                val norm = typed.lowercase().filter { !it.isWhitespace() }
                if (norm.isEmpty()) return
                val hit = TtsProviders.byId(TtsProviders.idOf(typed))
                    ?: TtsProviders.all.firstOrNull {
                        TtsProviders.display(it).lowercase().filter { c -> !c.isWhitespace() }.contains(norm) ||
                            it.id.lowercase().contains(norm)
                    }
                if (hit != null && hit.id != store.providerId) {
                    store.providerId = hit.id
                    applyProvider(hit.id)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        etProvider.setOnItemClickListener { _, _, _, _ ->
            val id = TtsProviders.idOf(etProvider.text.toString())
            if (TtsProviders.byId(id) != null) {
                store.providerId = id
                applyProvider(id)
            }
        }
    }

    /** 按厂商刷新：Key / 模型清单 / 业务空间显隐 / Key 标签 */
    private fun applyProvider(providerId: String) {
        val p = TtsProviders.byId(providerId) ?: return
        val isBailian = p.shape == "dashscope"
        etKey.setText(store.providerKey(providerId))
        etKey.hint = p.keyHint
        // 密钥历史：点进这一栏（键盘弹出）时自动展开下拉
        (etKey as? android.widget.AutoCompleteTextView)?.let { ac ->
            ac.setAdapter(ContainsAdapter(this, store.providerKeyHistory(providerId)))
            ac.threshold = 0
            ac.setOnClickListener { ac.showDropDown() }
            ac.setOnFocusChangeListener { _, has -> if (has) ac.showDropDown() }
        }
        (etModel as? android.widget.AutoCompleteTextView)?.let { ac ->
            ac.setAdapter(ContainsAdapter(this, TtsProviders.modelDisplays(providerId)))
            ac.threshold = 1
        }
        val saved = store.lastModel
        val model = if (saved.isNotBlank() && saved in p.models) saved else p.models.firstOrNull().orEmpty()
        // 自定义渠道没有预设模型 → 保留用户已填的，别把输入框清空
        if (model.isNotEmpty() || p.models.isEmpty()) {
            if (p.models.isNotEmpty() || saved.isBlank()) etModel.setText(model)
        }
        etModel.hint = if (p.models.isEmpty()) "填服务商文档里的模型名" else "该厂商的模型 ID"

        // 业务空间那栏：百炼 = workspace；MiniMax = GroupId（同一栏复用）
        val needGroup = isBailian || p.shape == "minimax"
        // 按约定：不适用的项「置灰禁填」而不是隐藏（隐藏会让布局忽长忽短，切换厂商整页乱跳）
        findViewById<TextView>(R.id.tvWsLabel).apply {
            visibility = View.VISIBLE
            alpha = if (needGroup) 1f else 0.4f
            text = when {
                isBailian -> "业务空间 ID"
                p.shape == "minimax" -> "GroupId（MiniMax 需要）"
                else -> "业务空间 ID（" + p.name + " 不需要，已停用）"
            }
        }
        etWs.apply {
            visibility = View.VISIBLE
            isEnabled = needGroup
            alpha = if (needGroup) 1f else 0.4f
        }

        // Base URL：自定义渠道（没写在目录里的）才显示
        // 只有"自定义渠道"才需要手填 Base URL / 路径 / 鉴权 / 返回形式（本地系统 TTS 不需要任何一项）
        val customBase = true                  // 一律显示（按约定：不适用就置灰，不隐藏 → 布局稳定）
        val editable = p.shape != "system"     // 系统 TTS 用不到这些
        findViewById<TextView>(R.id.tvBaseUrlLabel).visibility = if (customBase) View.VISIBLE else View.GONE
        etBaseUrl.visibility = if (customBase) View.VISIBLE else View.GONE
        listOf(
            R.id.tvBaseUrlLabel, R.id.etBaseUrl, R.id.tvPathLabel, R.id.etPath,
            R.id.tvAuthLabel, R.id.etAuth
        ).forEach { id ->
            findViewById<View>(id).apply {
                visibility = View.VISIBLE
                isEnabled = editable
                alpha = if (editable) 1f else 0.4f
            }
        }
        if (customBase) {
            etBaseUrl.setText(store.providerBaseUrl(p.id))
            // Base URL 下拉：预设（第一项=推荐）+ 你用过/填过的其它地址
            val items = ArrayList<String>()
            p.presets.forEachIndexed { i, u ->
                items.add(if (i == 0) "★" + u else u)
            }
            store.providerBaseUrlHistory(p.id).forEach { u ->
                if (items.none { it.removePrefix("★") == u }) items.add(u)
            }
            if (etBaseUrl.text.toString().trim().isNotEmpty() &&
                items.none { it.removePrefix("★") == etBaseUrl.text.toString().trim() }
            ) {
                items.add(0, etBaseUrl.text.toString().trim() + " ")
            }
            (etBaseUrl as? android.widget.AutoCompleteTextView)?.let { ac ->
                ac.setAdapter(UrlAdapter(this, items))
                ac.threshold = 0
                ac.setOnClickListener { ac.showDropDown() }
                ac.setOnFocusChangeListener { _, has -> if (has) ac.showDropDown() }
            }
        }

        // 自定义渠道专属三项：端点路径 / 鉴权头 / 返回形式
        // 自定义渠道专属两项：端点路径 / 鉴权头
        // （"音频返回形式"已删：程序会自动识别二进制 / base64 / URL，没必要让用户选这么专业的东西）
        listOf(R.id.tvPathLabel, R.id.etPath, R.id.tvAuthLabel, R.id.etAuth)
            .forEach { findViewById<View>(it).visibility = if (customBase) View.VISIBLE else View.GONE }
        if (customBase) {
            findViewById<EditText>(R.id.etPath).setText(store.providerPath(p.id))
            findViewById<EditText>(R.id.etAuth).setText(store.providerAuth(p.id))
        }

        // 本地系统 TTS 不需要 Key：整栏隐藏
        val needKey = p.shape != "system"
        listOf(R.id.tvKeyLabel, R.id.etKey, R.id.btnPaste, R.id.btnSaveKey).forEach { id ->
            findViewById<View>(id).apply {
                visibility = View.VISIBLE
                isEnabled = needKey
                alpha = if (needKey) 1f else 0.4f
            }
        }
        findViewById<TextView>(R.id.tvKeyLabel).text = "API Key（" + p.name + "；仅保存在本机）"
    }

    /** 开发者联系与支持：整块点一下复制（仿闪译；只提 DeepSeek，不提 GLM） */
    private fun bindAbout() {
        val tv = findViewById<TextView>(R.id.tvDevInfo)
        tv.text = """开发者联系与支持

QQ：2093523014（邮箱同号）
任何一个聪明的 ai 助手 ∠( ᐛ 」∠)_，
尤其是 deepseek-v4.1-flash

如果这个软件帮助了你，请打赏开发者
https://ifdian.net/a/qq2093523014
你的支持将帮助我们拿出更好、更美观的实用软件。

软件代码全开源，以下是代码仓库地址
https://github.com/Sideroca?tab=repositories""".trimIndent()

        // 检查更新：打开 GitHub 最新发布页（本机不联网自检，避免多一个权限）
        findViewById<TextView>(R.id.btnCheckUpdate)?.setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/Sideroca/PrimaTune/releases/latest")
                    )
                )
            } catch (e: Exception) {
                toast("打不开浏览器：" + (e.message ?: ""))
            }
        }

        val hint = findViewById<TextView>(R.id.tvDevCopyHint)
        val doCopy = {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("dev", tv.text))
            toast("已复制开发者联系与支持")
        }
        fx(tv); fx(hint); fx(findViewById(R.id.btnCheckUpdate))
        tv.setOnClickListener { doCopy() }
        hint.setOnClickListener { doCopy() }
    }

    /** 保存当前厂商的这三项（Key 按厂商分开存；百炼额外把 Key 同步给老字段） */
    private fun saveProviderFields() {
        // 先留住用户刚输入的三项 —— 下面切厂商会刷新输入框，不先留就会被冲掉
        val typedKey = etKey.text.toString().trim()
        val typedWs = etWs.text.toString().trim()
        val typedModel = etModel.text.toString()

        // 再把"厂商"框里的文字解析成厂商：手打了名字但没点下拉项时，也要能正确切换并保存
        val typed = etProvider.text.toString().trim()
        val typedId = TtsProviders.idOf(typed)
        val normTyped = typed.lowercase().filter { !it.isWhitespace() }
        val resolved = TtsProviders.byId(typedId)
            ?: TtsProviders.all.firstOrNull {
                TtsProviders.display(it).lowercase().filter { c -> !c.isWhitespace() }.contains(normTyped) ||
                    it.id.lowercase().contains(normTyped)
            }
        if (resolved != null && resolved.id != store.providerId) {
            store.providerId = resolved.id
            applyProvider(resolved.id)
        }

        store.setProviderKey(store.providerId, typedKey)
        if (store.providerId == "aliyun-bailian") store.apiKey = typedKey
        store.workspace = typedWs
        val mid = TtsProviders.modelIdOf(typedModel)
        if (mid.isNotBlank()) store.lastModel = mid
        if (etBaseUrl.visibility == View.VISIBLE) {
            store.setProviderBaseUrl(store.providerId, etBaseUrl.text.toString())
            store.addProviderBaseUrlToHistory(store.providerId, etBaseUrl.text.toString())
            store.setProviderPath(store.providerId, findViewById<EditText>(R.id.etPath).text.toString())
            store.setProviderAuth(store.providerId, findViewById<EditText>(R.id.etAuth).text.toString())
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

        // 棘轮卡位：共 20 档 = 量程 ÷ 20（遮罩 0~80 → 每档 4；卡片 0~100 → 每档 5）
        Ratchet.attach(sbScrimMain, (sbScrimMain.max / 20).coerceAtLeast(1)) {
            val p = sbScrimMain.progress
            store.scrimMain = p
            tvScrimMain.text = "$p%"
        }
        Ratchet.attach(sbScrimPage, (sbScrimPage.max / 20).coerceAtLeast(1)) {
            val p = sbScrimPage.progress
            store.scrimPage = p
            tvScrimPage.text = "$p%"
            Wp.applySlot(this, wpImg, wpScrim, store.wpPage, store.scrimPage, Skin.colors(this).bg)
        }
        Ratchet.attach(sbCardAlpha, (sbCardAlpha.max / 20).coerceAtLeast(1)) {
            val p = sbCardAlpha.progress
            store.cardAlphaPct = p
            tvCardAlpha.text = "$p%"
            Skin.apply(window.decorView, Skin.colors(this))
        }

        listOf(R.id.btnWpMain, R.id.btnWpMainClear, R.id.btnWpPage, R.id.btnWpPageClear, R.id.btnWpReset)
            .forEach { fx(findViewById(it)) }
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

    private fun refreshWpStates() {
        tvWpMainState.text = if (store.wpMain.isNotEmpty() && File(store.wpMain).exists()) "已设置" else "未设置"
        tvWpPageState.text = if (store.wpPage.isNotEmpty() && File(store.wpPage).exists()) "已设置" else "未设置"
    }

    private fun pick(slot: String) {
        // 优先走"系统相册"（ACTION_PICK），而不是 SAF 文件浏览器；没有相册再退回 SAF
        val gallery = Intent(
            Intent.ACTION_PICK,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply { type = "image/*" }
        val i = if (gallery.resolveActivity(packageManager) != null) gallery else Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, if (slot == "main") REQ_MAIN else REQ_PAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        // ① 系统选图回来 → 先进「二次截取」页，让你决定要哪一块
        if (requestCode == REQ_ICON_PICK) {
            val uri = data?.data ?: return
            startActivityForResult(
                Intent(this, CropActivity::class.java)
                    .putExtra(CropActivity.EXTRA_SLOT, "icon")
                    .putExtra(CropActivity.EXTRA_URI, uri.toString()),
                REQ_CROP_ICON
            )
            return
        }

        if (requestCode == REQ_MAIN || requestCode == REQ_PAGE) {
            val uri = data?.data ?: return
            val slot = if (requestCode == REQ_MAIN) "main" else "page"
            startActivityForResult(
                Intent(this, CropActivity::class.java)
                    .putExtra(CropActivity.EXTRA_SLOT, slot)
                    .putExtra(CropActivity.EXTRA_URI, uri.toString()),
                if (slot == "main") REQ_CROP_MAIN else REQ_CROP_PAGE
            )
            return
        }

        // ①' 图标取景回来 → 用裁好的图钉一个桌面入口
        if (requestCode == REQ_CROP_ICON) {
            val path = data?.getStringExtra(CropActivity.EXTRA_PATH).orEmpty()
            if (path.isEmpty()) { toast("没有拿到截取结果"); return }
            val bmp = try { android.graphics.BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
            if (bmp == null) { toast("图片读取失败"); return }
            val label = etEntryName.text.toString().trim().ifEmpty { getString(R.string.app_name_launcher) }
            pinShortcut("custom_" + System.currentTimeMillis(), label, bmp)
            return
        }

        // ② 截取页回来 → 裁好的图直接作为该槽位壁纸
        val slot = if (requestCode == REQ_CROP_MAIN) "main" else "page"
        val path = data?.getStringExtra(CropActivity.EXTRA_PATH).orEmpty()
        if (path.isNotEmpty() && File(path).exists()) {
            if (slot == "main") store.wpMain = path else store.wpPage = path
            refreshWpStates()
            applyLook()
            toast("壁纸已应用（已截取）")
        } else {
            toast("没有拿到截取结果")
        }
    }

    // ---------------------------------------------------------------- 快捷图标工坊（仿闪译）
    /** 内置图标样式（App 自身图标 + 桌面入口都能用） */
    private val iconStyles = listOf(
        "default" to "紫·默认",
        "a" to "银环比",
        "b" to "白方框",
        "c" to "调音台"
    )
    private var curIconStyle = "default"

    private fun iconFgRes(key: String) = when (key) {
        "a" -> R.drawable.icon_style_a_fg
        "b" -> R.drawable.icon_style_b_fg
        "c" -> R.drawable.icon_style_c_fg
        else -> R.drawable.icon_style_d_fg
    }

    private fun iconBgRes(key: String) = when (key) {
        "a" -> R.drawable.icon_style_a_bg
        "b" -> R.drawable.icon_style_b_bg
        "c" -> R.drawable.icon_style_c_bg
        else -> R.drawable.icon_style_d_bg
    }

    /** 合成一张该样式的完整图标位图（背景层铺底 + 前景层盖上） */
    private fun composeIcon(key: String, size: Int): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val bg = androidx.core.content.ContextCompat.getDrawable(this, iconBgRes(key))
        val fg = androidx.core.content.ContextCompat.getDrawable(this, iconFgRes(key))
        bg?.setBounds(0, 0, size, size); bg?.draw(cv)
        fg?.setBounds(0, 0, size, size); fg?.draw(cv)
        return bmp
    }

    private fun setupIconWorkshop() {
        curIconStyle = store.appIconStyle
        ivIconPreview.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, 26f * resources.displayMetrics.density)
            }
        }
        ivIconPreview.clipToOutline = true
        listOf(R.id.btnPinEntry, R.id.btnPinCustom, R.id.btnIconReset).forEach { fx(findViewById(it)) }
        findViewById<TextView>(R.id.btnPinEntry).setOnClickListener { pinEntry() }
        findViewById<TextView>(R.id.btnIconReset).setOnClickListener { applyAppIconStyle("default", tell = true) }
        findViewById<TextView>(R.id.btnPinCustom).setOnClickListener { pickIconImage() }
        renderIconStyleChips()
        updateIconPreview()
        // 兜底：保证恰好有一个 alias 开着（否则桌面上会没有图标）
        applyAppIconStyle(curIconStyle, tell = false)
    }

    private fun renderIconStyleChips() {
        llIconStyles.removeAllViews()
        val c = Skin.colors(this)
        for ((key, label) in iconStyles) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 12.5f
            tv.isSingleLine = true
            tv.maxLines = 1
            val sel = key == curIconStyle
            tv.isSelected = sel
            tv.setPadding(dp(13f), dp(7f), dp(13f), dp(7f))
            tv.background = Skin.shapeDp(this, if (sel) c.acc else c.card2, if (sel) c.acc else c.line, 96f, 100, 1f)
            tv.setTextColor(if (sel) c.onAcc else c.dim)
            tv.isClickable = true; tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener { applyAppIconStyle(key, tell = true) }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(8f)
            tv.layoutParams = lp
            llIconStyles.addView(tv)
        }
    }

    private fun updateIconPreview() {
        val c = Skin.colors(this)
        ivIconPreview.background = Skin.shapeDp(this, c.card2, c.line, 26f, 100, 1f)
        ivIconPreview.setImageBitmap(composeIcon(curIconStyle, 240))
    }

    /** 切换 App 自身图标：每套样式对应一个 activity-alias，运行时只开一个 */
    private fun applyAppIconStyle(key: String, tell: Boolean) {
        curIconStyle = key
        store.appIconStyle = key
        val aliases = mapOf(
            "default" to ".IconStyleDefault", "a" to ".IconStyleA",
            "b" to ".IconStyleB", "c" to ".IconStyleC"
        )
        val pm = packageManager
        for ((k, alias) in aliases) {
            try {
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, packageName + alias),
                    if (k == key) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                // alias 未声明则跳过
            }
        }
        renderIconStyleChips()
        updateIconPreview()
        if (tell) toast("已切换 App 图标：" + (iconStyles.firstOrNull { it.first == key }?.second ?: key))
    }

    /** 钉一个桌面入口（用当前样式的图标） */
    private fun pinEntry() {
        val label = etEntryName.text.toString().trim().ifEmpty { getString(R.string.app_name_launcher) }
        pinShortcut("style_" + curIconStyle + "_" + System.currentTimeMillis(), label, composeIcon(curIconStyle, 256))
    }

    private fun pinShortcut(id: String, label: String, bmp: android.graphics.Bitmap) {
        try {
            val si = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(label).setLongLabel(label)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bmp))
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, si, null)
            toast("已请求钉到桌面：" + label)
        } catch (e: Exception) {
            toast("创建失败：" + (e.message ?: ""))
        }
    }

    /** 自定义：选图 → 取景(1:1) → 用那张图钉一个桌面入口 */
    private fun pickIconImage() {
        val gallery = Intent(
            Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply { type = "image/*" }
        val i = if (gallery.resolveActivity(packageManager) != null) gallery
        else Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        startActivityForResult(i, REQ_ICON_PICK)
    }

    /** 弹窗统一换肤：面板 / 标题 / 正文 / 按钮（与首页同一套） */
    private fun skinDialog(dlg: androidx.appcompat.app.AlertDialog) {
        dlg.window?.setDimAmount(0.28f)
        val c = Skin.colors(this)
        Skin.apply(dlg.window!!.decorView, c)
        dlg.window?.setBackgroundDrawable(Skin.dialogPanel(this, c))
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) dlg.findViewById<TextView>(titleId)?.setTextColor(c.txt)
        dlg.findViewById<TextView>(android.R.id.message)?.setTextColor(c.dim)
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(c.acc)
        dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setTextColor(c.dim)
        dlg.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(c.dim)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recPlayer?.release() } catch (e: Exception) { /* ignore */ }
        recPlayer = null
    }

    /** 点按水波（+ root 时在该卡片里迸"留痕"星点） */
    private fun fx(v: View?, root: View? = null) {
        v ?: return
        TapFx.press(v, Skin.colors(this).acc, root)
    }

    /** 「提示」栏：使用提示 / 关于 / 许可与致谢 —— 分组 + 细线，颜色全走主题 */
    private fun buildTips() {
        llTips.removeAllViews()
        val c = Skin.colors(this)
        fun label(t: String) {
            val tv = TextView(this)
            tv.text = t; tv.setTextColor(c.dim); tv.textSize = 13f
            tv.setPadding(0, dp(16f), 0, 0)
            llTips.addView(tv)
        }
        fun line(t: String, col: Int, size: Float = 12.5f) {
            val tv = TextView(this)
            tv.text = t; tv.setTextColor(col); tv.textSize = size
            tv.setLineSpacing(dp(4f).toFloat(), 1f)
            tv.setPadding(0, dp(6f), 0, 0)
            llTips.addView(tv)
        }
        fun divider() {
            val v = View(this)
            v.setBackgroundColor((c.line and 0x00FFFFFF) or (0x80 shl 24))
            v.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)
            ).apply { topMargin = dp(14f) }
            llTips.addView(v)
        }
        label("使用提示")
        line("① 「模型」页选厂商、填 API Key（本机「系统 TTS」免 Key）", c.dim)
        line("② 选音色；没有合适的就「＋ 建音色」上传样本做声音复刻", c.dim)
        line("③ 输入文本 → 生成；记录卡支持「播放 / 回填 / 重抽 / 分享 / 下载」", c.dim)
        line("④ 外观：主题页 64 套配色 + 壁纸二次截取；「App 图标与桌面入口」可换图标、钉桌面入口", c.dim)
        divider()
        label("关于")
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "" }
        line("玲珑调音 · Prima Tune    v" + ver, c.txt, 13f)
        line("· 语音合成：10 家厂商 + 本机系统 TTS + 自定义渠道", c.dim)
        line("· 音色：预制音色（默认隐藏）+ 自建（声音复刻）", c.dim)
        line("· 数据：全部只保存在本机，不上传", c.dim)
        divider()
        label("许可与致谢")
        line("· 界面设计参照《夕汀前端规范》", c.dim)
        line("· 各第三方模型与名称归各自厂商所有", c.dim)
    }

    /** 危险色（删除）：不写死一个红 —— 浅色主题用更深、深色主题提亮，保证对比 */
    private fun dangerColor(): Int {
        val c = Skin.colors(this)
        return if (c.light) 0xFFB23A34.toInt()
        else Skin.Colors.mix(0xFFE06B64.toInt(), c.txt, 0.18f)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_MAIN = 2001
        private const val REQ_PAGE = 2002
        private const val REQ_ICON_PICK = 2005
        private const val REQ_CROP_ICON = 2006
        private const val REQ_CROP_MAIN = 2003
        private const val REQ_CROP_PAGE = 2004
    }
}
