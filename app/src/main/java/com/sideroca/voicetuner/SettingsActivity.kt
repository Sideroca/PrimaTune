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
    private lateinit var etRecSearch: EditText
    private lateinit var llFontScale: LinearLayout
    private lateinit var llRecTop: LinearLayout
    private lateinit var etLlmBase: EditText
    private lateinit var etLlmKey: EditText
    private lateinit var etLlmModel: android.widget.AutoCompleteTextView
    private lateinit var etLlmPrompt: EditText
    private lateinit var etLlmExtra: EditText
    private lateinit var llVoiceCfg: LinearLayout
    private lateinit var llPolishCfg: LinearLayout
    private lateinit var btnTabVoice: TextView
    private lateinit var btnTabPolish: TextView
    private lateinit var etLlmProvider: android.widget.AutoCompleteTextView
    private lateinit var llLlmLevels: LinearLayout
    private lateinit var etLlmMaxTokens: EditText
    private lateinit var etLlmTemp: EditText
    private lateinit var etTransPrompt: EditText
    private lateinit var etTransEmail: EditText
    private lateinit var btnLlmTest: TextView
    private lateinit var tvLlmTest: TextView
    /** 记录页筛选状态：只看收藏 / 指定角色 / 搜索词 */
    /** 当前在语音页还是润色页（换主题时需要按它重新上色） */
    private var cfgTabVoice = true
    /** 防重入：applyLlmPreset 会 setText(etLlmProvider) → 又触发 afterTextChanged → 死循环 */
    private var applyingLlm = false
    private var recVoiceId: String? = null
    private var recQuery: String = ""

    private var cat = "modern"
    /** 配色列表是否展开全部（懒建：默认只建前 20 套） */
    private var palShowAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Loc.apply(this)          // 界面语言（per-app locale）
        setContentView(R.layout.activity_settings)
        // 卡片顶距只由卡片自己的 paddingTop 决定（把"卡片里第一个元素"的上边距清零，避免两者叠加）
        LayoutFix.flattenCardFirstTop(window.decorView, dp(7f))
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
        etRecSearch = findViewById(R.id.etRecSearch)
        llFontScale = findViewById(R.id.llFontScale)
        llRecTop = findViewById(R.id.llRecTop)
        etLlmBase = findViewById(R.id.etLlmBase)
        etLlmKey = findViewById(R.id.etLlmKey)
        etLlmModel = findViewById(R.id.etLlmModel)
        etLlmPrompt = findViewById(R.id.etLlmPrompt)
        etLlmExtra = findViewById(R.id.etLlmExtra)
        etLlmBase.setText(store.llmBaseUrl)
        etLlmKey.setText(store.llmKey)
        etLlmModel.setText(store.llmModel)
        LlmPresets.match(store.llmBaseUrl)?.let { setupLlmModelDrop(it.models) }   // 润色页模型名也给下拉
        etLlmPrompt.setText(store.llmPrompt.ifBlank { LlmClient.DEFAULT_PROMPT })
        etLlmExtra.setText(store.llmExtra)
        llVoiceCfg = findViewById(R.id.llVoiceCfg)
        llPolishCfg = findViewById(R.id.llPolishCfg)
        btnTabVoice = findViewById(R.id.btnTabVoice)
        btnTabPolish = findViewById(R.id.btnTabPolish)
        etLlmProvider = findViewById(R.id.etLlmProvider)
        setupLlmProviderPicker()
        llLlmLevels = findViewById(R.id.llLlmLevels)
        etLlmMaxTokens = findViewById(R.id.etLlmMaxTokens)
        etLlmTemp = findViewById(R.id.etLlmTemp)
        etTransPrompt = findViewById(R.id.etTransPrompt)
        btnLlmTest = findViewById(R.id.btnLlmTest)
        tvLlmTest = findViewById(R.id.tvLlmTest)
        etLlmMaxTokens.setText(if (store.llmMaxTokens > 0) store.llmMaxTokens.toString() else "")
        etLlmTemp.setText(if (store.llmTemp >= 0) (store.llmTemp / 10.0).toString() else "")
        etTransPrompt.setText(store.transPrompt.ifBlank { LlmClient.DEFAULT_TRANS_PROMPT })
        etTransEmail = findViewById(R.id.etTransEmail)
        etTransEmail.setText(store.transEmail)
        btnTabVoice.setOnClickListener { selectCfgTab(true) }
        btnTabPolish.setOnClickListener { selectCfgTab(false) }
        btnLlmTest.setOnClickListener { testLlm() }
        buildLlmLevels()
        selectCfgTab(true)
        // 搜索：只重建列表，不重建顶部（否则输入框会被顶掉、光标丢失）
        etRecSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                recQuery = s?.toString().orEmpty()
                renderRecList()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        // 首帧只做"壳"：绑定接口 + 坞 + 保存键；**页面内容改为"用到才建"**（见 buildPage）
        // —— 修「主页 → 设置页：开头没反应 / 中间有反应 / 后面又没反应」（用户 2026-10-06）
        bindInterface()
        buildDock()
        btnSaveAll.setOnClickListener { saveAll() }
        applyThemeTab()
        selectPage(0, animate = false)   // 第一屏交给"窗口转场"整页淡入，内容自己不再动
        applyPageShift()
        // 转场结束后**空闲时逐页预建**：既不阻塞进入动画，又不会"切一页卡一下"（用户 2026-10-06 的顾虑）
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        for (p in 1..4) h.postDelayed({ if (!isFinishing && !isDestroyed) buildPage(p) }, 280L + p * 170L)
    }

    override fun onResume() {
        super.onResume()
        applyLook()
        // Store = 唯一数据源：只**重建已建过的页**（没切到过的页不必建，省一次卡顿）
        if (pageBuilt[0]) buildProviders()
        if (pageBuilt[1]) buildVoices()
        if (pageBuilt[3]) buildRecords()
    }

    private fun applyLook() {
        val c = Skin.colors(this)
        Skin.applyWindow(this, c)
        Skin.apply(window.decorView, c)
        // 标题下划线：accent 短条（圆头），随主题
        findViewById<View>(R.id.tvTitleUnderline)?.apply {
            background = Skin.shapeDp(this@SettingsActivity, c.acc, null, 3f, 100, 0f)
            tag = "bg:keep"
        }
        Wp.applySlot(this, wpImg, wpScrim, store.wpPage, store.scrimPage, c.bg)
        // 坞与保存键不在 Skin 的"角色"体系里 → 必须在 Skin.apply 之后显式上色，才不会被它盖掉
        styleDock(c)
        styleDropdowns(c)
        renderIconStyleChips()
        buildTips()
        selectCfgTab(cfgTabVoice)      // 换主题后页签配色也跟着重刷
        // ⚠️ 「代码建一次」的芯片/色球都打了 bg:keep（换肤引擎不碰）→ **必须在这里重建**，
        //    否则换主题后它们停在旧色、要点第二下才刷新（用户 2026-10-06 报的 bug）
        if (pageBuilt[2]) {
            buildCatChips()
            renderPalettes()
            renderIndicatorColors()
            renderFontScaleChips()
        }
    }

    // ---------------------------------------------------------------- 分页 + 坞

    private val pageTitles get() = listOf(getString(R.string.tab_model), getString(R.string.tab_voice), getString(R.string.tab_theme), getString(R.string.tab_records), getString(R.string.tab_about))
    private val dockDefs get() = listOf(getString(R.string.tab_model), getString(R.string.tab_voice), getString(R.string.tab_theme), getString(R.string.tab_records), getString(R.string.tab_about))
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

    /** 哪些页**已经建过内容**（用到才建）。一进设置页只建"模型页"，其余等你切过去才建 → 转场不卡 */
    private val pageBuilt = BooleanArray(5)

    /** 切到某页时，第一次才把它的内容建出来（一次建一页，比"一次建 5 页"轻得多） */
    private fun buildPage(i: Int) {
        if (i !in pageBuilt.indices || pageBuilt[i]) return
        pageBuilt[i] = true
        when (i) {
            0 -> buildProviders()
            1 -> buildVoices()
            2 -> {
                setupIconWorkshop()      // 合成图标位图，较重 → 只在这一页建
                buildCatChips()
                renderPalettes()
                renderIndicatorColors()
                bindWallpaper()
                renderFontScaleChips()
            }
            3 -> buildRecords()
            4 -> bindAbout()
        }
    }

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
            // 底部坞的 5 个按钮**不加点按特效**：它们本来就有"选中垫片"这层反馈，够了
            item.setOnClickListener { selectPage(idx) }
            llDock.addView(item)
            dockItems.add(item); dockPads.add(pad)
            dockIcons.add(pad.getChildAt(0) as? ImageView ?: ImageView(this))   // 音色那格不是 ImageView，占位以免索引错位
            dockLabels.add(lb)
        }
    }

    private fun selectPage(i: Int, animate: Boolean = true) {
        selectedPage = i
        buildPage(i)          // 用到才建（首次切到这一页才建它的内容）
        val pages = listOf(pageModel, pageVoice, pageTheme, pageRecords, pageAbout)
        val d = resources.displayMetrics.density
        pages.forEachIndexed { idx, v ->
            if (idx == i) {
                v.visibility = View.VISIBLE
                if (animate) {
                    // 页内切页：小位移 + 淡入（**只有页内切页才动**）
                    v.translationX = 24f * d
                    v.alpha = 0f
                    v.animate().translationX(0f).alpha(1f).setDuration(180).start()
                } else {
                    // ⚠️ 进设置页的**第一屏不要自己动** —— 否则内容动画和窗口转场（整页淡入）叠一起，
                    //    看着"有的卡片从左抖、有的元素只淡入"，很不自然（用户 2026-10-06）
                    v.translationX = 0f
                    v.alpha = 1f
                    v.animate().cancel()
                }
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
        // ⚠️ 主题父类是深色（Theme.AppCompat.NoActionBar）→ 不刷底的下拉是"深底深字"，看不见。
        // 新增自动补全框后**务必加进这个名单**（etLlmProvider、etLlmModel 都曾漏掉 → 下拉整张黑卡）。
        listOf(R.id.etProvider, R.id.etModel, R.id.etKey, R.id.etLlmProvider, R.id.etLlmModel).forEach { id ->
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
        // 自适应换行：按 chip 实测宽度装箱，保证每行都放得下（原来固定 3/2/3 会顶出卡片、最后一个被切）
        val dm = resources.displayMetrics
        val avail = dm.widthPixels - dp(14f) * 2 - dp(14f) * 2 - dp(6f)   // 屏幕 - 页面边距 - 卡片内边距
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dm.scaledDensity
        }
        val gap = dp(8f)
        var rowNo = 0
        var row: LinearLayout = newProvRow(rowNo)
        var used = 0
        llProviders.addView(row)
        for (p in TtsProviders.all) {
            val w = paint.measureText(p.name).toInt() + dp(12f) * 2
            if (used > 0 && used + gap + w > avail - dp(provShift(rowNo))) {
                rowNo++
                row = newProvRow(rowNo)
                llProviders.addView(row)
                used = 0
            }
            val sel = p.id == store.providerId
            val tv = TextView(this)
            tv.text = p.name
            tv.textSize = 12.5f
            tv.isSingleLine = true          // 关键：chip 内部不许换行
            tv.maxLines = 1
            tv.isSelected = sel
            tv.setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            tv.background = Skin.shapeDp(this, if (sel) c.sel else c.card2, if (sel) c.sel else c.line, 96f, 100, 1f)
            tv.setTextColor(if (sel) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                // 点厂商 chip = 选这家
                store.providerId = p.id
                etProvider.setText(TtsProviders.display(p), false)
                applyProvider(p.id)
                buildProviders()
            }
            row.addView(
                tv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = gap }
            )
            used += w + gap
        }
    }

    /** 厂商 chip 每行的左偏移（保留"不规则排列"的味道，但不再溢出） */
    private fun provShift(i: Int): Float = floatArrayOf(0f, 22f, 8f, 30f, 12f, 26f)[i % 6]

    private fun newProvRow(i: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8f)
            leftMargin = dp(provShift(i))
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
            act.text = getString(R.string.common_delete)
            act.setTextColor(dangerColor())
            act.textSize = 14f
            act.setPadding(dp(14f), dp(6f), dp(4f), dp(6f))
            act.isClickable = true
            act.isFocusable = true
            fx(act)
            act.setOnClickListener {
                // 删除 = 直接从列表移除（内置的记入本机隐藏表，不再有「已隐藏」区）
                val dd = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.vc_del_title))
                    .setMessage(getString(R.string.mgr_del_msg, name))
                    .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                        if (builtIn) {
                            val h = store.hiddenVoices; h.add(id); store.hiddenVoices = h
                        } else {
                            val list = store.loadCustomVoices()
                            list.removeAll { it.id == id }
                            store.saveCustomVoices(list)
                        }
                        buildVoices(); buildProviders()
                    }
                    .setNegativeButton(getString(R.string.common_cancel), null)
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
            tv.text = getString(R.string.vc_empty2)
            tv.setTextColor(c.hint)
            tv.textSize = 12.5f
            tv.setPadding(0, dp(10f), 0, dp(2f))
            llVoices.addView(tv)
        }
    }

    // ---------------------------------------------------------------- 记录页

    /** 记录页：分享这条记录（FileProvider，别的 App 可直接收） */
    private var pendingSaveFile2: java.io.File? = null
    private val REQ_SAVE2 = 2010

    /** 记录页「下载」：自己命名 + 选目录（SAF），默认名已按「音色·正文片段」预填 */
    private fun exportHistory(t: Take) {
        val f = store.fileOf(t)
        if (!f.exists()) { Toast.makeText(this, getString(R.string.file_missing), Toast.LENGTH_SHORT).show(); return }
        pendingSaveFile2 = f
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (f.name.endsWith("mp3")) "audio/mpeg" else "audio/wav"
            putExtra(Intent.EXTRA_TITLE, niceFileName(t))
        }
        try {
            startActivityForResult(i, REQ_SAVE2)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_dlg_fail, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录页「翻译」：翻这条记录的正文（配了 LLM 走 LLM，否则走免费源），结果弹窗可复制 */
    private fun translateHistory(t: Take) {
        val src = t.text.trim()
        if (src.isEmpty()) { Toast.makeText(this, getString(R.string.rec_no_text), Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, getString(R.string.st_translating), Toast.LENGTH_SHORT).show()
        if (store.llmKey.isNotBlank()) {
            val sys = store.transPrompt.ifBlank { LlmClient.DEFAULT_TRANS_PROMPT }
                .replace("{target}", store.trTarget)
            LlmClient.ask(
                store.llmBaseUrl, store.llmKey, store.llmModel, sys, src,
                store.llmMaxTokens, store.llmTemp / 10.0, store.llmLevel
            ) { out, err ->
                runOnUiThread { if (out != null) showTranslated(out) else Toast.makeText(this, err ?: getString(R.string.trans_failed), Toast.LENGTH_SHORT).show() }
            }
        } else {
            TransClient.translate(src, store.trTarget, store.trLastSource, store.transEmail) { out, err, s2 ->
                runOnUiThread {
                    if (out != null) { s2?.let { store.trLastSource = it }; showTranslated(out) }
                    else Toast.makeText(this, err ?: getString(R.string.trans_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 译文弹窗：可选中、可一键复制（跟随主题） */
    private fun showTranslated(out: String) {
        val tv = TextView(this)
        tv.text = out
        tv.setTextIsSelectable(true)
        tv.textSize = 15f
        tv.setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        val sc = ScrollView(this)
        sc.addView(tv)
        val d = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.trans_into, store.trTarget))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_copy)) { _, _ ->
                Clip.copy(this, out, "trans")
                store.pendingFillId = ""
            }
            .setNegativeButton(getString(R.string.common_close), null)
            .create()
        d.setOnShowListener {
            skinDialog(d)
            val w = d.window
            if (w != null) {
                sc.layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 300).toInt()
                )
                w.setLayout((resources.displayMetrics.widthPixels * 0.82).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        d.setCanceledOnTouchOutside(true)
        d.show()
    }

    private fun shareHistory(t: Take) {
        try {
            val f = store.fileOf(t)
            if (!f.exists()) {
                Toast.makeText(this, getString(R.string.file_missing), Toast.LENGTH_SHORT).show()
                return
            }
            // 复制成"好名字"再分享（否则对方收到的是 vt_1759…_abc.wav）
            val out = try {
                val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
                val dst = java.io.File(dir, niceFileName(t))
                if (!dst.exists() || dst.length() != f.length()) f.copyTo(dst, overwrite = true)
                if (dst.exists()) dst else f
            } catch (e: Exception) { f }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, packageName + ".fileprovider", out
            )
            val i = Intent(Intent.ACTION_SEND).apply {
                type = if (out.name.endsWith("mp3")) "audio/mpeg" else "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, out.name)
                clipData = android.content.ClipData.newUri(contentResolver, "audio", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, getString(R.string.share_to)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.share_fail, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录页：试听（用系统 MediaPlayer 直接播本地文件） */
    private var recPlayer: android.media.MediaPlayer? = null

    private fun playHistory(t: Take) {
        try {
            recPlayer?.release()
            AudioFocus.request(this) { }
            val f = store.fileOf(t)
            if (!f.exists()) {
                Toast.makeText(this, getString(R.string.file_missing), Toast.LENGTH_SHORT).show()
                return
            }
            recPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(f.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    AudioFocus.abandon(this@SettingsActivity)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.play_fail, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录页：是否只看收藏 */
    private var recOnlyFav = false

    /** 记录页 = 顶部筛选（搜索框在布局里，这里只建 chip）＋ 列表；两者分开建，搜索时不重建顶部 */
    /** 记录卡字号缩放：只作用在**标题 / 信息 / 详情**上（小按钮一律不动，用户 2026-10-05） */
    private fun recScale(base: Float): Float = base * store.recordFontPct / 100f

    /** 记录字号档位芯片（放在「壁纸属性」卡底部） */
    private fun renderFontScaleChips() {
        llFontScale.removeAllViews()
        val c = Skin.colors(this)
        for ((pct, label) in listOf(90 to getString(R.string.font_small), 100 to getString(R.string.font_std), 115 to getString(R.string.font_large), 130 to getString(R.string.font_xl))) {
            val on = store.recordFontPct == pct
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 12.5f
            tv.setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            tv.background = Skin.shapeDp(this, if (on) c.sel else c.card2, if (on) c.sel else c.line, 8f, 100, 1f)
            tv.setTextColor(if (on) 0xFFFFFFFF.toInt() else c.dim)
            tv.tag = "bg:keep"
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                store.recordFontPct = pct
                renderFontScaleChips()
                renderRecList()          // 立刻按新字号重排记录
                Toast.makeText(this, getString(R.string.font_toast, label, pct), Toast.LENGTH_SHORT).show()
            }
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) }
            llFontScale.addView(tv)
        }
    }

    private fun buildRecords() {
        buildRecTop()
        renderRecList()
    }

    private fun recVoiceName(id: String): String =
        VoiceCatalog.builtIn.firstOrNull { it.second == id }?.first
            ?: store.loadCustomVoices().firstOrNull { it.id == id }?.name
            ?: store.loadTakes().firstOrNull { it.voiceId == id }?.voiceName
            ?: getString(R.string.voice_custom)

    private fun buildRecTop() {
        llRecTop.removeAllViews()
        val c = Skin.colors(this)
        val takesAll = store.loadTakes()
        val favs = store.favTakes

        fun chipRow(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2f), 0, dp(2f))
        }
        fun addChip(row: LinearLayout, label: String, on: Boolean, tap: () -> Unit) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 12.5f
            tv.isSingleLine = true
            tv.maxLines = 1
            tv.isSelected = on
            tv.setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
            tv.background = Skin.shapeDp(this, if (on) c.sel else c.card2, if (on) c.sel else c.line, 96f, 100, 1f)
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener { tap() }
            row.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) })
        }

        val r1 = chipRow()
        addChip(r1, getString(R.string.rec_all), !recOnlyFav && recVoiceId == null) {
            recOnlyFav = false; recVoiceId = null; buildRecords()
        }
        // 中间：一个「选择」按钮 → 下拉选音色（原来是把人名全摊成一行 chip，太挤）
        val pick = TextView(this)
        pick.text = if (recVoiceId == null) getString(R.string.rec_pick) else recVoiceName(recVoiceId!!) + " ▾"
        pick.textSize = 12.5f
        pick.isSingleLine = true
        pick.maxLines = 1
        pick.isSelected = recVoiceId != null
        pick.setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
        pick.background = Skin.shapeDp(
            this, if (recVoiceId != null) c.sel else c.card2,
            if (recVoiceId != null) c.acc else c.line, 96f, 100, 1f
        )
        pick.setTextColor(if (recVoiceId != null) c.onAcc else c.dim)
        pick.isClickable = true
        pick.isFocusable = true
        fx(pick)
        pick.setOnClickListener { showRecVoicePick() }
        r1.addView(pick, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8f) })
        addChip(r1, getString(R.string.rec_fav, favs.size), recOnlyFav) {
            recOnlyFav = true; recVoiceId = null; buildRecords()
        }
        llRecTop.addView(r1)
    }

    /** 记录页「选择音色」下拉：跟随主题的弹出面板（与首页记录筛选同一套做法） */
    private fun showRecVoicePick() {
        val c = Skin.colors(this)
        val ids = LinkedHashSet<String>()
        for (tk in store.loadTakes()) if (tk.voiceId.isNotBlank()) ids.add(tk.voiceId)
        var pop: android.widget.PopupWindow? = null
        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        fun row(label: String, id: String?) {
            val tv = TextView(this)
            val on = id == recVoiceId
            tv.text = (if (on) "✓ " else "　 ") + label
            tv.setTextColor(if (on) c.acc else c.txt)
            tv.textSize = 14f
            tv.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                recVoiceId = id
                recOnlyFav = false
                buildRecords()
                pop?.dismiss()
            }
            listBox.addView(tv)
        }
        row(getString(R.string.rec_all_voices), null)
        for (id in ids) row(recVoiceName(id), id)
        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.addView(listBox)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Skin.shapeDp(this, c.bg, c.line, 12f)
        panel.setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        panel.addView(sc)
        val rows = ids.size + 1
        val h = minOf(dp(300f), dp(12f) + dp(46f) * rows)
        val w = minOf(dp(280f), resources.displayMetrics.widthPixels - dp(32f))
        pop = android.widget.PopupWindow(panel, w, h, true)
        pop.inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        pop.isOutsideTouchable = true
        pop.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        pop.elevation = dp(8f).toFloat()
        pop.showAsDropDown(llRecTop, dp(4f), dp(2f))
    }

    /** 列表：按 收藏 / 角色 / 搜索词（关键词·角色·日期）过滤后渲染 */
    private fun renderRecList() {
        llRecords.removeAllViews()
        val c = Skin.colors(this)
        val favs = store.favTakes
        var takes: List<Take> = store.loadTakes()
        if (recOnlyFav) takes = takes.filter { it.id in favs }
        recVoiceId?.let { vid -> takes = takes.filter { it.voiceId == vid } }
        val q = recQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            val long = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val short = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            takes = takes.filter { tk ->
                tk.text.lowercase().contains(q) ||
                    tk.voiceName.lowercase().contains(q) ||
                    tk.instruction.lowercase().contains(q) ||
                    long.format(java.util.Date(tk.createdAt)).contains(q) ||
                    short.format(java.util.Date(tk.createdAt)).contains(q)
            }
        }
        if (takes.isEmpty()) {
            val tv = TextView(this)
            tv.text = when {
                q.isNotEmpty() -> getString(R.string.rec_empty_search)
                recOnlyFav -> getString(R.string.rec_empty_fav)
                recVoiceId != null -> getString(R.string.rec_empty_voice)
                else -> getString(R.string.rec_empty)
            }
            tv.setTextColor(c.dim)
            tv.textSize = 13f
            tv.setPadding(0, dp(10f), 0, 0)
            llRecords.addView(tv)
            return
        }

        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        takes.forEachIndexed { idx, t ->
            if (idx > 0) {
                val dv = View(this)
                dv.setBackgroundColor((c.line and 0x00FFFFFF) or (0x66 shl 24))
                dv.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)
                ).apply { topMargin = dp(4f); bottomMargin = dp(2f) }
                llRecords.addView(dv)
            }
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(0, dp(10f), 0, dp(10f))

            // 头部：文本 ＋ 右上角删除 ✕（字形 9.5sp；判定 42dp，贴卡片右上内沿）
            val head = android.widget.FrameLayout(this)
            val a = TextView(this)
            a.text = t.text
            a.setTextColor(c.txt)
            a.textSize = recScale(14f)
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
            // 同样的删除 ✕：不做点按特效
            x.setOnClickListener {
                val dd = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.rec_del_title))
                    .setMessage(t.text.take(60))
                    .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
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
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .create()
                dd.setOnShowListener { skinDialog(dd) }
                dd.show()
            }
            head.addView(x)
            box.addView(head)

            // 常驻信息：时间 · 音色 · 格式 · 字数
            val b = TextView(this)
            val durStr = if (t.durationMs > 0)
                String.format(java.util.Locale.US, getString(R.string.rec_sec), t.durationMs / 1000.0)
            else getString(R.string.rec_unknown)
            b.text = getString(R.string.rec_meta, fmt.format(java.util.Date(t.createdAt)), t.voiceName, t.format, durStr, t.text.length)
            b.setTextColor(c.dim)
            b.textSize = recScale(11.5f)
            b.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
            box.addView(b)

            // 收起时隐藏的「属性」：语速 / 音调 / 音量 / 模型 / 种子
            val d2 = TextView(this)
            val modelShown = if (t.model.isBlank()) store.lastModel else t.model
            d2.text = getString(R.string.rec_detail, t.rate, t.pitch, t.volume, modelShown, t.seed)
            d2.setTextColor(c.txt)                 // 与正文一致（不再发灰"隐形"）
            d2.textSize = recScale(13f)
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

            // 顺序按用户要求：▶播放 · 下载 · 分享 · ★收藏 · 回填（最右）
            val play = TextView(this)
            play.text = getString(R.string.rec_play)
            play.setTextColor(c.dim)
            play.textSize = 11.5f
            play.setPadding(0, dp(6f), dp(12f), dp(2f))
            play.isClickable = true
            play.isFocusable = true
            fx(play)
            play.setOnClickListener { playHistory(t) }
            bar2.addView(play)

            val down = TextView(this)
            down.text = getString(R.string.rec_download)
            down.setTextColor(c.dim)
            down.textSize = 11.5f
            down.setPadding(0, dp(6f), dp(12f), dp(2f))
            down.isClickable = true
            down.isFocusable = true
            fx(down)
            down.setOnClickListener { exportHistory(t) }
            bar2.addView(down)

            val share = TextView(this)
            share.text = getString(R.string.rec_share)
            share.setTextColor(c.dim)
            share.textSize = 11.5f
            share.setPadding(0, dp(6f), dp(12f), dp(2f))
            share.isClickable = true
            share.isFocusable = true
            fx(share)
            share.setOnClickListener { shareHistory(t) }
            bar2.addView(share)

            val star = LinearLayout(this)
            star.orientation = LinearLayout.HORIZONTAL
            star.gravity = Gravity.CENTER_VERTICAL
            star.setPadding(0, dp(6f), dp(12f), dp(2f))
            star.isClickable = true
            star.isFocusable = true
            val starIcon = StarView(this)
            starIcon.layoutParams = LinearLayout.LayoutParams(dp(13f), dp(13f))
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
                starLabel.text = getString(R.string.rec_star)
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

            // 回填（最右）：把这条记录回填到**主页**的表单（跨页交接，回去即生效）
            val fill = TextView(this)
            fill.text = getString(R.string.rec_fill)
            fill.setTextColor(c.dim)
            fill.textSize = 11.5f
            fill.setPadding(0, dp(6f), 0, dp(2f))
            fill.isClickable = true
            fill.isFocusable = true
            fx(fill)
            fill.setOnClickListener {
                store.pendingFillId = t.id
                toast(getString(R.string.toast_backfilled))
                finish()          // 回主页 → onResume 里自动应用
            }
            bar2.addView(fill)

            // 翻译（最右）：翻这条记录的正文，结果弹出来可复制（配了 LLM 走 LLM）
            val tr = TextView(this)
            tr.text = getString(R.string.rec_translate)
            tr.setTextColor(c.dim)
            tr.textSize = 11.5f
            tr.setPadding(dp(12f), dp(6f), dp(4f), dp(2f))
            tr.isClickable = true
            tr.isFocusable = true
            fx(tr)
            tr.setOnClickListener { translateHistory(t) }
            bar2.addView(tr)

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
        store.llmBaseUrl = etLlmBase.text.toString()
        store.llmKey = etLlmKey.text.toString().trim()
        store.llmModel = etLlmModel.text.toString()
        store.llmPrompt = etLlmPrompt.text.toString()
        store.llmExtra = etLlmExtra.text.toString().trim()
        store.llmMaxTokens = etLlmMaxTokens.text.toString().trim().toIntOrNull() ?: 0
        store.llmTemp = ((etLlmTemp.text.toString().trim().toDoubleOrNull() ?: -0.1) * 10).toInt()
        store.transPrompt = etTransPrompt.text.toString()
        store.transEmail = etTransEmail.text.toString().trim()
        buildProviders()
        Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- 当前音色指示符颜色
    private val indicatorOptions get() = listOf(
        getString(R.string.ind_blue) to "#2E8FFF",
        getString(R.string.ind_pink) to "#FFAFC5",
        getString(R.string.ind_green) to "#2FE39B",
        getString(R.string.ind_purple) to "#A56BFF",
        getString(R.string.ind_gray) to "#8AA0B6",
        getString(R.string.ind_orange) to "#FF8A3D",
        getString(R.string.ind_silver) to "#D3DAE3"
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

    /** 仿手机设置页：5 个设置页整体下移（按屏高动态算，不写死 dp）。
     *  用户 2026-10-04：先下移 15%，后要求「集体上移 8%」→ 现为 7%。 */
    private fun applyPageShift() {
        val host = findViewById<android.widget.FrameLayout>(R.id.pageHost) ?: return
        // 用户 2026-10-05：内容上移到"主标题与卡片的一半距离" → 7% 的一半 = 3.5%
        val shift = (resources.displayMetrics.heightPixels * 0.035f).toInt()
        host.setPadding(host.paddingLeft, shift, host.paddingRight, host.paddingBottom)
    }

    // ---------------------------------------------------------------- 主题
    private fun buildCatChips() {
        llCats.removeAllViews()
        val c = Skin.colors(this)
        val defs = listOf(
            "modern" to getString(R.string.cat_modern),
            "chinese" to getString(R.string.cat_chinese),
            "attrs" to getString(R.string.cat_attrs)
        )
        for ((id, label) in defs) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 13f
            tv.setPadding(dp(12), dp(6), dp(12), dp(6))
            val sel = id == cat
            tv.isSelected = sel
            tv.background = Skin.shapeDp(this, if (sel) c.sel else c.card2, if (sel) null else c.line, 96f)   // 96 避开 Skin 的 chip 角色(100dp)，否则被重绘成浅色
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
            more.text = if (palShowAll) getString(R.string.collapse20) else getString(R.string.show_all_n, list.size)
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
            // 换肤必须重刷所有"代码建过"的页（模型/音色/记录）——否则停在旧色（规范 ⑯.1）
            if (pageBuilt[0]) buildProviders()
            if (pageBuilt[1]) buildVoices()
            if (pageBuilt[3]) buildRecords()
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
                toast(getString(R.string.toast_pasted_key))
            } else {
                toast(getString(R.string.toast_clip_empty))
            }
        }
        findViewById<TextView>(R.id.btnSaveKey).setOnClickListener {
            saveProviderFields()
            toast(getString(R.string.toast_saved))
        }
    }

    /** P1：厂商选择（输入首字母即匹配）→ 切换该厂商的 Key 与模型清单 */
    private fun setupProviderPicker() {
        etProvider.setAdapter(
            ContainsAdapter(this, TtsProviders.all.map { TtsProviders.display(it) })
        )
        etProvider.threshold = 1
        val cur = TtsProviders.byId(store.providerId) ?: TtsProviders.all.first()
        // 当前厂商放在 hint 里（灰色提示），正文留空 —— 免得每次想输入都要先删掉
        etProvider.hint = TtsProviders.display(cur)
        etProvider.setText("", false)
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
        etModel.hint = if (p.models.isEmpty()) getString(R.string.hint_model_provider) else getString(R.string.hint_model_default)

        // 业务空间那栏：百炼 = workspace；MiniMax = GroupId（同一栏复用）
        val needGroup = isBailian || p.shape == "minimax"
        // 按约定：不适用的项「置灰禁填」而不是隐藏（隐藏会让布局忽长忽短，切换厂商整页乱跳）
        findViewById<TextView>(R.id.tvWsLabel).apply {
            visibility = View.VISIBLE
            alpha = if (needGroup) 1f else 0.4f
            text = when {
                isBailian -> getString(R.string.label_ws)
                p.shape == "minimax" -> getString(R.string.groupid_hint)
                else -> getString(R.string.ws_disabled, p.name)
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
        findViewById<TextView>(R.id.tvKeyLabel).text = getString(R.string.key_label, p.name)
    }

    /** 开发者联系与支持：整块点一下复制（仿闪译；只提 DeepSeek，不提 GLM） */
    private fun bindAbout() {
        val tv = findViewById<TextView>(R.id.tvDevInfo)
        // 标题由布局里的 label 提供 → 正文**不要再写一遍**（否则叠出两行「开发者联系与支持」，图3 的 bug）
        tv.text = """QQ：2093523014（邮箱同号）
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
                toast(getString(R.string.open_browser_fail, e.message ?: ""))
            }
        }

        val hint = findViewById<TextView>(R.id.tvDevCopyHint)
        val doCopy = {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("dev", tv.text))
            toast(getString(R.string.toast_dev_copied))
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
        // 厂商栏留空 = 不改动当前厂商（否则空串会"包含匹配"到第一家）
        val resolved = if (typedId.isEmpty() && normTyped.isEmpty()) null
        else TtsProviders.byId(typedId)
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
            store.wpMainCrop = ""
            refreshWpStates()
            toast(getString(R.string.toast_wp_main_cleared))
        }
        findViewById<TextView>(R.id.btnWpPage).setOnClickListener { pick("page") }
        findViewById<TextView>(R.id.btnWpPageClear).setOnClickListener {
            Wp.clear(this, "page")
            store.wpPage = ""
            store.wpPageCrop = ""
            refreshWpStates()
            applyLook()
            toast(getString(R.string.toast_wp_page_cleared))
        }
        findViewById<TextView>(R.id.btnWpReset).setOnClickListener {
            Wp.clear(this, "main")
            Wp.clear(this, "page")
            store.wpMain = ""
            store.wpPage = ""
            store.wpMainCrop = ""
            store.wpPageCrop = ""
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
            toast(getString(R.string.toast_reset))
        }
        tvWpMainState.setOnClickListener { if (store.wpMain.isNotEmpty()) startRecrop("main") }
        tvWpPageState.setOnClickListener { if (store.wpPage.isNotEmpty()) startRecrop("page") }
        refreshWpStates()
    }

    /** 归一化参数编码 "nx,ny,nz" → 三元组；无效返回 null */
    private fun parseCrop(s: String): Triple<Float, Float, Float>? {
        val p = s.split(",")
        if (p.size != 3) return null
        val nx = p[0].toFloatOrNull() ?: return null
        val ny = p[1].toFloatOrNull() ?: return null
        val nz = p[2].toFloatOrNull() ?: return null
        if (nx.isNaN() || ny.isNaN() || nz.isNaN()) return null
        return Triple(nx, ny, nz)
    }

    /**
     * 「重新取景」：用永久保留的原图 + 上次归一化参数回到 CropActivity，
     * 不重新选图、不丢手感（"可随时重裁"）。
     */
    private fun startRecrop(slot: String) {
        val src = Wp.sourceFile(this, slot)
        if (!src.exists()) {
            toast(getString(R.string.toast_need_image))
            pick(slot)
            return
        }
        val req = Intent(this, CropActivity::class.java)
            .putExtra(CropActivity.EXTRA_SLOT, slot)
            .putExtra(CropActivity.EXTRA_SRC, src.absolutePath)
        parseCrop(if (slot == "main") store.wpMainCrop else store.wpPageCrop)?.let { (nx, ny, nz) ->
            req.putExtra(CropActivity.EXTRA_NX, nx)
                .putExtra(CropActivity.EXTRA_NY, ny)
                .putExtra(CropActivity.EXTRA_NZ, nz)
        }
        startActivityForResult(req, if (slot == "main") REQ_CROP_MAIN else REQ_CROP_PAGE)
    }

    private fun refreshWpStates() {
        val mainSet = store.wpMain.isNotEmpty() && File(store.wpMain).exists()
        val pageSet = store.wpPage.isNotEmpty() && File(store.wpPage).exists()
        tvWpMainState.text = when {
            !mainSet -> getString(R.string.wp_unset)
            Wp.sourceFile(this, "main").exists() -> getString(R.string.wp_set_recrop)
            else -> getString(R.string.wp_set)
        }
        tvWpPageState.text = when {
            !pageSet -> getString(R.string.wp_unset)
            Wp.sourceFile(this, "page").exists() -> getString(R.string.wp_set_recrop)
            else -> getString(R.string.wp_set)
        }
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

        // 记录页「下载」：写进用户选的位置
        if (requestCode == REQ_SAVE2) {
            val uri = data?.data
            val f = pendingSaveFile2
            if (uri != null && f != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        f.inputStream().use { it.copyTo(out) }
                    }
                    Toast.makeText(this, getString(R.string.saved_file, f.name), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.toast_save_fail, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
            pendingSaveFile2 = null
            return
        }

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
            if (path.isEmpty()) { toast(getString(R.string.toast_no_crop)); return }
            val bmp = try { android.graphics.BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
            if (bmp == null) { toast(getString(R.string.toast_image_fail)); return }
            val label = data?.getStringExtra(CropActivity.EXTRA_NAME).orEmpty().trim()
                .ifEmpty { etEntryName.text.toString().trim() }
                .ifEmpty { getString(R.string.app_name_launcher) }
            pinShortcut("custom_" + System.currentTimeMillis(), label, bmp)
            return
        }

        // ② 截取页回来 → 裁好的图直接作为该槽位壁纸（并记下归一化参数，供日后「重新取景」）
        val slot = if (requestCode == REQ_CROP_MAIN) "main" else "page"
        val d2 = data
        val path = d2?.getStringExtra(CropActivity.EXTRA_PATH).orEmpty()
        if (path.isNotEmpty() && File(path).exists()) {
            if (slot == "main") store.wpMain = path else store.wpPage = path
            val nx = d2?.getFloatExtra(CropActivity.EXTRA_NX, Float.NaN) ?: Float.NaN
            val ny = d2?.getFloatExtra(CropActivity.EXTRA_NY, Float.NaN) ?: Float.NaN
            val nz = d2?.getFloatExtra(CropActivity.EXTRA_NZ, Float.NaN) ?: Float.NaN
            if (!nx.isNaN() && !ny.isNaN() && !nz.isNaN()) {
                val enc = "$nx,$ny,$nz"
                if (slot == "main") store.wpMainCrop = enc else store.wpPageCrop = enc
            }
            refreshWpStates()
            applyLook()
            toast(getString(R.string.toast_wp_applied))
        } else {
            toast(getString(R.string.toast_no_crop))
        }
    }

    // ---------------------------------------------------------------- 快捷图标工坊（仿闪译）
    /** 内置图标样式（App 自身图标 + 桌面入口都能用） */
    private val iconStyles get() = listOf(
        "default" to getString(R.string.icon_style_default),
        "a" to getString(R.string.icon_style_a),          // 原「银环比」→ 旋钮图（用户 2026-10-06）
        "b" to getString(R.string.icon_style_b),          // 原「白方框」→ 红玫瑰（方形图，取景时点一下即可完整框住）
        "c" to getString(R.string.icon_style_c)
    )
    private var curIconStyle = "default"



    /** 合成一张该样式的完整图标位图（背景层铺底 + 前景层盖上） */
    private fun composeIcon(key: String, size: Int): android.graphics.Bitmap =
        IconStyles.compose(this, key, size)

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
            tv.background = Skin.shapeDp(this, if (sel) c.sel else c.card2, if (sel) c.sel else c.line, 96f, 100, 1f)
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

    /**
     * 切 App 自身图标。
     * 说明：目前只有「紫·默认」这一张做得够干净，另外三套作为 App 图标效果不好 →
     * 选它们时**不再切 App 图标**，而是留给「创建桌面入口」当素材（那边可以自己截取）。
     */
    private fun applyAppIconStyle(key: String, tell: Boolean) {
        curIconStyle = key
        store.appIconStyle = if (key == "default") key else "default"
        if (key != "default") {
            renderIconStyleChips()
            updateIconPreview()
            if (tell) toast(getString(R.string.selected_material, iconStyles.firstOrNull { it.first == key }?.second ?: key) +
                getString(R.string.icon_cut_hint))
            return
        }
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
        if (tell) {
            toast(getString(R.string.icon_switched, iconStyles.firstOrNull { it.first == key }?.second ?: key))
            // 关掉旧 alias 会让系统结束当前任务（看起来像"闪退回桌面"）→ 立刻用新别名把 App 拉回前台
            try {
                val cn = android.content.ComponentName(packageName, packageName + (aliases[key] ?: ".IconStyleDefault"))
                startActivity(
                    Intent().setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (e: Exception) {
                // 拉不回来也不致命，图标已经切好了
            }
        }
    }

    /** 创建桌面入口：走"取景"让用户自己截一张（素材 = 当前样式），截完再钉上去 */
    private fun pinEntry() {
        if (etEntryName.text.toString().trim().isEmpty()) {
            toast(getString(R.string.toast_pin_name_hint))
        }
        startActivityForResult(
            Intent(this, CropActivity::class.java)
                .putExtra(CropActivity.EXTRA_SLOT, "icon")
                .putExtra(CropActivity.EXTRA_STYLE, curIconStyle)
                .putExtra(CropActivity.EXTRA_NAME, etEntryName.text.toString().trim()),
            REQ_CROP_ICON
        )
    }

    private fun pinShortcut(id: String, label: String, bmp: android.graphics.Bitmap) {
        try {
            val si = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(label).setLongLabel(label)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bmp))
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, si, null)
            toast(getString(R.string.pinned, label))
        } catch (e: Exception) {
            toast(getString(R.string.create_fail2, e.message ?: ""))
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

    /** 语音 / 润色 两个子页 */
    private fun selectCfgTab(voice: Boolean) {
        cfgTabVoice = voice
        val c = Skin.colors(this)
        llVoiceCfg.visibility = if (voice) View.VISIBLE else View.GONE
        llPolishCfg.visibility = if (voice) View.GONE else View.VISIBLE
        listOf(btnTabVoice to voice, btnTabPolish to !voice).forEach { (tv, on) ->
            tv.isSelected = on
            tv.background = Skin.shapeDp(
                this, if (on) c.sel else c.card2, if (on) c.sel else c.line, 12f, 100, 1f
            )
            // ⚠️ 圆角 12dp 恰好命中 Skin 的 bg_btn_primary 角色 → 换肤会把**两个都**刷成实心主色
            //（所以之前出现"两个都绿"）→ 打 tag 声明别动它；换主题时由 applyLook 重新上色
            tv.tag = "bg:keep"
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
        }
    }

    /** 润色页：厂商用**下拉填空**（仿闪译，输入关键字即匹配）—— 原来一排芯片会溢出、点选也不刷新高亮 */
    private fun setupLlmProviderPicker() {
        etLlmProvider.setAdapter(ContainsAdapter(this, LlmPresets.all.map { it.label }))
        etLlmProvider.threshold = 0
        val cur = LlmPresets.match(store.llmBaseUrl)
        etLlmProvider.setText(cur?.label ?: "", false)
        etLlmProvider.setOnClickListener { etLlmProvider.showDropDown() }
        etLlmProvider.setOnFocusChangeListener { _, has -> if (has) etLlmProvider.showDropDown() }
        etLlmProvider.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (applyingLlm) return      // ⚠️ 防死循环：见 applyLlmPreset
                val typed = s?.toString().orEmpty().trim()
                if (typed.isEmpty()) return
                // 只有「整串完全等于某个厂商名」才自动填 —— 否则你打字/退格时会被 setText 夺走光标
                // （现象：光标突然跳到最左、名字删不掉）。想按"名字里的字母"选，请用下拉（包含匹配）。
                val p = LlmPresets.all.firstOrNull { it.label.equals(typed, true) }
                if (p != null && p.id != "custom") applyLlmPreset(p)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        etLlmProvider.setOnItemClickListener { _, _, _, _ ->
            LlmPresets.all.firstOrNull { it.label == etLlmProvider.text.toString() }?.let { applyLlmPreset(it) }
        }
    }

    /** 应用一个厂商预设：**连状态一起写上**（旧实现在这里漏了，导致绿色高亮永远停在 DeepSeek） */
    private fun applyLlmPreset(p: LlmPresets.P) {
        applyingLlm = true               // 下面 setText 会触发 afterTextChanged，先上锁
        try {
            if (p.url.isNotBlank()) {
                etLlmBase.setText(p.url)
                store.llmBaseUrl = p.url
            }
            p.models.firstOrNull()?.let {
                etLlmModel.setText(it)
                store.llmModel = it
            }
            store.llmLevel = ""
            etLlmProvider.setText(p.label, false)
            setupLlmModelDrop(p.models)
            buildLlmLevels()
        } finally {
            applyingLlm = false
        }
    }

    /** 润色页：模型名也给下拉（用当前厂商预设的模型清单；「自定义」无清单则不挂） */
    private fun setupLlmModelDrop(models: List<String>) {
        if (models.isEmpty()) return
        etLlmModel.setAdapter(ContainsAdapter(this, models))
        etLlmModel.threshold = 0
        etLlmModel.setOnClickListener { etLlmModel.showDropDown() }
        etLlmModel.setOnFocusChangeListener { _, has -> if (has) etLlmModel.showDropDown() }
    }

    /** 润色页：思考档位芯片（按当前 Base URL 识别厂商，用它家的真实档位） */
    private fun buildLlmLevels() {
        llLlmLevels.removeAllViews()
        val c = Skin.colors(this)
        val levels = LlmPresets.match(etLlmBase.text.toString().trim())?.levels.orEmpty()
        llLlmLevels.visibility = if (levels.isEmpty()) View.GONE else View.VISIBLE
        for ((name, value) in levels) {
            val on = store.llmLevel == value
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 12.5f
            tv.isSelected = on
            tv.setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            // 圆角 96→8dp：原来近圆（药丸），用户要更方正的长方形；8 同时避开 Skin 的魔法半径
            tv.background = Skin.shapeDp(this, if (on) c.sel else c.card2, if (on) c.sel else c.line, 8f, 100, 1f)
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                store.llmLevel = if (on) "" else value
                buildLlmLevels()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(8f)
            tv.layoutParams = lp
            llLlmLevels.addView(tv)
        }
    }

    /** 连接测试：发一句最短的请求，回显耗时 */
    private fun testLlm() {
        val base = etLlmBase.text.toString().trim()
        val key = etLlmKey.text.toString().trim()
        val model = etLlmModel.text.toString().trim()
        if (base.isBlank() || key.isBlank()) { toast(getString(R.string.toast_need_base_key)); return }
        tvLlmTest.text = getString(R.string.st_testing)
        val t0 = System.currentTimeMillis()
        applyTransFieldsQuietly()
        LlmClient.ask(base, key, model, "你是连接测试助手，只回复：ok", "ping",
            maxTokens = 8, temperature = 0.0, level = store.llmLevel
        ) { out, err ->
            runOnUiThread {
                val ms = System.currentTimeMillis() - t0
                tvLlmTest.text = if (out != null) getString(R.string.conn_ok, ms, model)
                                 else "❌ " + (err ?: getString(R.string.common_fail))
            }
        }
    }

    /** 测试前把编辑框里的值先落盘（与生成时用的保持一致） */
    private fun applyTransFieldsQuietly() {
        store.llmBaseUrl = etLlmBase.text.toString()
        store.llmKey = etLlmKey.text.toString().trim()
        store.llmModel = etLlmModel.text.toString()
        store.llmMaxTokens = etLlmMaxTokens.text.toString().trim().toIntOrNull() ?: 0
        store.llmTemp = ((etLlmTemp.text.toString().trim().toDoubleOrNull() ?: -0.1) * 10).toInt()
        store.transPrompt = etTransPrompt.text.toString()
        store.transEmail = etTransEmail.text.toString().trim()
        store.llmPrompt = etLlmPrompt.text.toString()
        store.llmExtra = etLlmExtra.text.toString().trim()
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
        // 弹窗按钮也装"跟随主题"的水波（系统默认水波吃系统色，不跟主题）
        listOf(
            android.content.DialogInterface.BUTTON_POSITIVE,
            android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL
        ).forEach { b -> dlg.getButton(b)?.let { fx(it) } }
    }

    override fun onPause() {
        super.onPause()
        // 离开设置页就停掉试听（否则回到主页时可能和新生成的结果"两个声音一起响"）
        try { recPlayer?.stop() } catch (e: Exception) { /* ignore */ }
        try { recPlayer?.release() } catch (e: Exception) { /* ignore */ }
        recPlayer = null
        AudioFocus.abandon(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recPlayer?.release() } catch (e: Exception) { /* ignore */ }
        recPlayer = null
        AudioFocus.abandon(this)
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
        label(getString(R.string.tips_title))
        line(getString(R.string.tips_1), c.dim)
        line(getString(R.string.tips_2), c.dim)
        line(getString(R.string.tips_3), c.dim)
        line(getString(R.string.tips_4), c.dim)
        divider()
        label(getString(R.string.about_title))
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "" }
        line(getString(R.string.product_name) + "    v" + ver, c.txt, 13f)
        line(getString(R.string.about_1), c.dim)
        line(getString(R.string.about_2), c.dim)
        line(getString(R.string.about_3), c.dim)
        divider()
        label(getString(R.string.lang_title))
        val langRow = LinearLayout(this)
        langRow.orientation = LinearLayout.HORIZONTAL
        langRow.setPadding(0, dp(6f), 0, 0)
        for ((name, code) in Loc.OPTIONS) {
            val on = store.lang == code
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 12.5f
            tv.setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            tv.background = Skin.shapeDp(this, if (on) c.sel else c.card2, if (on) c.sel else c.line, 8f, 100, 1f)
            tv.setTextColor(if (on) c.onAcc else c.dim)
            tv.tag = "bg:keep"
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener { Loc.set(this, code) }
            tv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) }
            langRow.addView(tv)
        }
        llTips.addView(langRow)
    }

    /** 破坏性动作（删除）的强调色：**跟随主题**（用户 2026-10-05：不要固定的红） */
    private fun dangerColor(): Int = Skin.colors(this).acc

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
