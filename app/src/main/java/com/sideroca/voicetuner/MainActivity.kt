package com.sideroca.voicetuner

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 语音调控台 v0.2
 * 调参面板 → 百炼 CosyVoice 直连合成 → 试听 / 保存 / 分享 → 本地记录（回填、重抽）
 */
class MainActivity : AppCompatActivity() {

    // ---------------------------------------------------------------- 数据
    private data class Voice(val name: String, val id: String, val note: String)

    private data class Fmt(val key: String, val label: String, val format: String, val sampleRate: Int, val bitRate: Int?, val ext: String)

    private data class InstrChip(val label: String, val text: String, val forVoice: String? = null)

    private class BadJsonException(val label: String) : Exception()

    private data class RowRef(val take: Take, val playBtn: TextView, val prog: View)

    private val voices = VoiceCatalog.builtIn.map { Voice(it.first, it.second, it.third) }
    private val customLabel get() = getString(R.string.vc_custom)

    private val formats = listOf(
        Fmt("wav24", "wav24 · 推荐", "wav", 24000, null, "wav"),
        Fmt("wav48", "wav48", "wav", 48000, null, "wav"),
        Fmt("wav16", "wav16", "wav", 16000, null, "wav"),
        Fmt("mp3_256", "mp3_256", "mp3", 24000, null, "mp3"),
        Fmt("mp3_128", "mp3_128", "mp3", 16000, null, "mp3")
    )

    /** 当前厂商**实际可选**的格式（按 TtsProviders.formats 过滤后的子集）——下拉与生成都用它 */
    private var fmtList: List<Fmt> = formats

    private val instrChips = listOf(
        InstrChip("温柔", "语气温柔治愈，语速偏慢，像在轻声安慰"),
        InstrChip("开心", "语气开心活泼，语速稍快，句尾轻快上扬"),
        InstrChip("悲伤", "语气悲伤低沉，语速缓慢，带一点哽咽感"),
        InstrChip("平静", "语气平静自然，语速平稳"),
        InstrChip("战斗", "语气凌厉果断，短促有力，字字分明"),
        InstrChip("从容讲述", "语气自信从容，带一点骄傲与得意；重点词稍加重音，句间有停顿；语速从容偏慢，情感有起伏。"),
        InstrChip("热情安利", "热情安利新发现：语气明亮带笑意，句尾轻快上扬；重点词稍加重音，句间留出停顿；语速从容不赶。"),
        InstrChip("演讲感", "像一段演讲：开场平稳，讲到重点时情绪上扬、放慢并加重；句间有呼吸感，结尾干脆有力。"),
        InstrChip("惊喜分享", "像对前辈分享惊喜：声音温柔轻软，但讲到重点时忍不住兴奋，语调明显起伏。"),
        InstrChip("艾丽妮·默认", "语气清冷平稳，带着审慎的高傲，吐字干脆利落", "艾丽妮"),
        InstrChip("艾丽妮·柔和", "语气放软放缓，带有一点不易察觉的温和与关切", "艾丽妮"),
        InstrChip("艾丽妮·不耐烦", "语气冷淡，语速略快，透出些许不耐烦", "艾丽妮"),
        InstrChip("艾雅法拉·默认", "声音温柔轻快，亲切自然，带一点点害羞", "艾雅法拉"),
        InstrChip("艾雅法拉·认真", "语气认真专注，语速平稳，像在耐心讲解研究", "艾雅法拉"),
        InstrChip("艾雅法拉·害羞", "声音轻软迟疑，带一点腼腆和试探", "艾雅法拉")
    )

    // ---------------------------------------------------------------- 语言选择（预置）
    private val langPresets = listOf(
        "zh" to "中文", "en" to "英语", "ja" to "日语", "ko" to "韩语",
        "de" to "德语", "fr" to "法语", "ru" to "俄语", "es" to "西班牙语",
        "it" to "意大利语", "pt" to "葡萄牙语", "th" to "泰语", "vi" to "越南语", "id" to "印尼语"
    )
    private val langSel = LinkedHashSet<String>()

    private val cTxt = Color.parseColor("#E8EEF8")
    private val cDim = Color.parseColor("#8D99AD")
    /** 破坏性动作（删除）的强调色：**跟随主题**（用户 2026-10-05：不要固定的红） */
    private fun deleteRed(): Int = Skin.colors(this).acc

    // ---------------------------------------------------------------- 状态
    private lateinit var store: Store
    private val client = DashScopeClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val takes = mutableListOf<Take>()
    private val rowRefs = mutableListOf<RowRef>()
    private val customVoices = mutableListOf<CustomVoice>()

    // 建音色（声音复刻）
    private val REQ_PICK_AUDIO = 1001
    /** 音色试听模板：选一段本地音频 */
    private val REQ_VOICE_TEMPLATE = 1002
    private var pendingVoiceTemplateId: String? = null
    private val MAX_SAMPLE_BYTES = 15L * 1024 * 1024
    /** 本地记录首屏最多渲染多少条（其余点「显示全部」再看）——直接决定冷启动要建多少 View */
    // 2026-10-17：曾试过 25 → 8（省 ~200ms），用户不接受"首屏太少" → **恢复 25**，
    // 改为在"建卡本身"上提速（见 Skin.matchByConstantState 的建表缓存）。
    private val HISTORY_PREVIEW = 25
    /** 每次「显示更多」**再**追加多少条（渐进加载，避免一次建上千个 View） */
    // 2026-10-17 用户："点按钮不要展开 100 条，展开 25 条吧"
    private val HISTORY_PAGE = 25
    private var createDlg: AlertDialog? = null
    private var createFileTv: TextView? = null
    private var createStatus: TextView? = null
    private var createPrefixEt: EditText? = null
    private var createNameEt: EditText? = null
    private var pendingSample: File? = null
    private var pendingSampleDurMs = 0L
    private var pendingSampleMime = "audio/wav"

    private var player: MediaPlayer? = null
    private var playingId: String? = null
    /** 当前记录播放的 AudioFocus 代号（旧 completion 迟到时用它避免误停新播放） */
    private var playerFocusToken = 0
    /** 正在播放那行的细进度条 */
    private var playingProgress: View? = null

    // 音色「试听」（长按删除卡片左侧按钮）：独立于记录播放器，避免动到记录行状态
    private var previewPlayer: MediaPlayer? = null
    private var previewCall: Cancellable? = null
    private var previewToken = 0
    private var previewFocusToken = 0
    private val progTick = object : Runnable {
        override fun run() {
            val mp = player ?: return
            val p = playingProgress ?: return
            val dur = try { mp.duration } catch (e: Exception) { -1 }
            if (dur > 0) p.scaleX = (mp.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
            mainHandler.postDelayed(this, 150)
        }
    }
    private var currentTake: Take? = null
    private var busy = false
    /** 高级参数里的 model 是否被用户手改过（onResume 时据此决定要不要用 store 覆盖） */
    private var modelTouched = false
    private var suppressModelWatch = false

    /**
     * **控件 → 参数名** 的声明表：某个输入控件该不该置灰，完全由这张表 + [TtsModels.supports] 决定。
     * 以后接新厂商 / 新模型，只改 TtsModels 里那张表即可 —— 界面代码零改动。
     */
    private val gatedControls: List<Pair<Int, String>> = listOf(
        R.id.sbRate to "rate", R.id.tvRate to "rate",
        R.id.sbPitch to "pitch", R.id.tvPitch to "pitch",
        R.id.sbVol to "volume", R.id.tvVol to "volume",
        R.id.etSeed to "seed", R.id.btnDice to "seed",
        R.id.etInstr to "instruction",
        R.id.etLangHints to "langhints",
        R.id.etHotfix to "hotfix",
        R.id.etExtra to "extra",
        R.id.cbSsml to "ssml"
    )

    /** 本地记录的音色筛选（null = 全部）；仅本次运行有效 */
    private var filterVoiceId: String? = null

    /** 本地记录是否已展开全部（默认只渲染最近 HISTORY_PREVIEW 条） */
    private var historyShown = HISTORY_PREVIEW

    /** 冷启动首屏只建这么多张（首帧后自动补齐）；第二遍起（JIT 热了）直接全建 */
    private val COLD_FIRST_ROWS = 8
    private var coldHistRender = true

    /** 当前音色（自绘选择器）：voiceIsCustom = 选了「自定义音色 ID…」 */
    private var currentVoiceId: String = ""
    private var voiceIsCustom = false

    // ---------------------------------------------------------------- 视图
    private lateinit var wpImg: ImageView
    private lateinit var wpScrim: View
    private lateinit var svRoot: ScrollView
    private lateinit var btnSettings: TextView
    private lateinit var llVoice: LinearLayout
    private lateinit var tvVoice: TextView
    private lateinit var ivVoiceArrow: ImageView
    private lateinit var ivVoiceInd: VoiceIndicatorView
    private lateinit var btnTranslate: TextView
    private lateinit var tvTransTarget: TextView
    private lateinit var tvTransEngine: TextView
    private lateinit var llTransBlocks: LinearLayout
    private lateinit var etProvMain: android.widget.AutoCompleteTextView
    /** 多版本翻译：生成时逐个跑（译版在上、原文在下）；非空时 generate() 用它当文本 */
    private val genQueue = ArrayDeque<String>()
    private var genTextOverride: String? = null
    /** 多版本翻译：所有译版都写进「合成文本」框（每行一版）；这里留一份清单给「生成＝全部生成」用 */
    private val transVersions = mutableListOf<String>()
    /** 多译版「删除即整版清掉」：正在被删的版本下标（-1 = 无）与该版累计删掉的字数 */
    private var trDelLine = -1
    private var trDelHits = 0
    /** 最近一次文本变更（TextWatcher 的 onTextChanged 原始参数，用来区分“删除/打字/组词”） */
    private var editStart = 0
    private var editRemoved = 0
    private var editAdded = 0
    private lateinit var btnYi: TextView
    private lateinit var btnRun: TextView
    private lateinit var btnRole: TextView
    private lateinit var btnSmartFill: TextView
    private lateinit var etCustomVoice: EditText
    private lateinit var tvVoiceNote: TextView
    private lateinit var btnCreateVoice: TextView
    private lateinit var etText: EditText
    private lateinit var btnClearText: TextView
    private lateinit var etInstr: EditText
    private lateinit var llChips: LinearLayout
    private lateinit var etSeed: EditText
    private lateinit var btnDice: TextView
    private lateinit var sbRate: SeekBar
    private lateinit var tvRate: TextView
    private lateinit var sbPitch: SeekBar
    private lateinit var tvPitch: TextView
    private lateinit var sbVol: SeekBar
    private lateinit var tvVol: TextView
    private lateinit var spFormat: Spinner
    private lateinit var tvAdvanced: TextView
    private lateinit var llAdvanced: LinearLayout
    private lateinit var etModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etLangHints: TextView
    private lateinit var etHotfix: EditText
    private lateinit var etExtra: EditText
    private lateinit var cbSsml: CheckBox
    private lateinit var btnGenerate: TextView
    private lateinit var btnCancel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cardResult: LinearLayout
    private lateinit var tvResultInfo: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnExport: TextView
    private lateinit var btnClearHistory: TextView
    private lateinit var llHistoryTitle: LinearLayout
    private lateinit var llHistory: LinearLayout

    // ---------------------------------------------------------------- 生命周期
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tStart = android.os.SystemClock.elapsedRealtime()
        mark("onCreate 开始")
        if (!Store(this).langChosen) {                       // 首次安装：先选语言
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        Loc.apply(this)          // 界面语言（per-app locale）
        setContentView(R.layout.activity_main)
        mark("setContentView 之后（窗口已铺）")
        // 卡片顶距只由卡片自己的 paddingTop 决定（把"卡片里第一个元素"的上边距清零，避免两者叠加）
        LayoutFix.flattenCardFirstTop(window.decorView, dp(8))
        store = Store(this)
        store.hidePresetVoicesOnce()          // 预设音色默认隐藏（一次性）
        customVoices.addAll(store.loadCustomVoices())
        hiddenVoices.addAll(store.hiddenVoices)
        bindViews()
        mark("bindViews 之后")
        setupVoicePicker()
        setupSliders()
        setupActions()
        mark("setup* 之后")

        takes.addAll(store.loadTakes())
        renderHistory()
        mark("读记录 + 建首屏记录卡 之后")
        migrateDurationsAsync()
        // 闹钟补排 / 预生成**挪到首帧之后**跑（用户 2026-10-15：冷启动白屏 3s+）
        window.decorView.post {
            mark("首帧之后（decorView.post）")
            runCatching { AlarmScheduler.apply(this) }   // 已保存的闹钟：启动时补排（幂等）
            runCatching {                                // 闹钟：还没预生成好 → 补跑（前台，不受后台限制）
                val ac = AlarmCfg.from(store.alarmJson)
                if (ac.enabled && (store.alarmDir.listFiles()?.none { it.isFile && it.length() > 0 } ?: true)) {
                    androidx.core.content.ContextCompat.startForegroundService(
                        this, Intent(this, AlarmService::class.java).putExtra(AlarmService.EXTRA, "pregen")
                    )
                }
            }
        }

        if (store.apiKey.isBlank()) {
            tvStatus.text = getString(R.string.first_run_hint)
        }
        installCrashGuard()
        showCrashIfAny()
        // Android 13+ 前台服务的通知需要这个权限（不给也不影响服务运行）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 77) }
        }
        // ⚠️ 这里**不再调 applyLook()**：onResume 紧跟着就会调一次
        //    （以前冷启动 = 换肤两次：整棵视图树重刷 + 壁纸解码，白屏元凶之一）
    }

    /** 冷启动第一次 onResume：onCreate 已经 loadTakes + renderHistory 过一遍了，别再整表重载 */
    private var firstResume = true

    override fun onResume() {
        super.onResume()
        mark("onResume 开始")
        applyLook()
        mark("applyLook 之后（整树换肤 + 壁纸）")
        // 用户 2026-10-10：闹钟正响时打开 App → **立刻停止响铃**。
        // （以前 App 里没有任何地方能停，划掉最近任务也停不掉，只能干等——体验最差的那个 bug）
        runCatching { AlarmService.stopIfRinging(this) }
        // 设置页可能删/改过记录、音色、隐藏列表 → 以 Store 为准整表重载（Store = 唯一数据源）
        // 冷启动第一次不重载：onCreate 刚读过一遍（省掉"再读一次 JSON + 再建 25 张记录卡 + 再换肤"）
        if (firstResume) firstResume = false else reloadFromStore()
        // 设置页点了「回填」→ 在这里应用（跨页交接）
        val pendingFill = store.pendingFillId
        if (pendingFill.isNotBlank()) {
            store.pendingFillId = ""
            takes.firstOrNull { it.id == pendingFill }?.let {
                fillFrom(it)
                svRoot.smoothScrollTo(0, 0)
                toast(getString(R.string.toast_backfill_done))
            }
        }
        // 设置页改过「默认 model」且用户没在高级参数里手改过 → 同步过来（不覆盖用户输入）
        if (!modelTouched) setModelField(store.lastModel)
        val ek = effectiveKey()
        if (etApiKey.text.toString() != ek) etApiKey.setText(ek)
        (etApiKey as? android.widget.AutoCompleteTextView)?.setAdapter(ContainsAdapter(this, keyHistory()))
    }

    /**
     * 以 Store（唯一数据源）整表重载，修掉「设置页删了记录/音色，主页还显示旧的」这类跨页不同步。
     * 原来只在 onCreate 读一次，onResume 只换肤 → 内存缓存会一直停留在旧数据。
     */
    private fun reloadFromStore() {
        takes.clear(); takes.addAll(store.loadTakes())
        customVoices.clear(); customVoices.addAll(store.loadCustomVoices())
        hiddenVoices.clear(); hiddenVoices.addAll(store.hiddenVoices)
        if (!voiceIsCustom && allVoices().none { it.id == currentVoiceId }) {
            setVoice(null)          // 音色被删/隐藏后回落："当前音色"变了就清空骰子
        }
        fitVoiceWidth()
        syncVoiceUi()
        renderHistory()
    }

    /** 当前厂商**真正生效**的那份 Key（生成/复刻都用它，界面展示也用这份 → 不会再两处不一致） */
    private fun effectiveKey(): String {
        val pid = store.providerId
        return store.providerKey(pid).ifBlank { if (pid == "aliyun-bailian") store.apiKey else "" }
    }

    /** 首页密钥栏的下拉历史：用过的密钥 + 当前这一份 */
    private fun keyHistory(): List<String> {
        val pid = store.providerId
        val list = ArrayList(store.providerKeyHistory(pid))
        val cur = effectiveKey()
        if (cur.isNotBlank() && !list.contains(cur)) list.add(0, cur)
        return list
    }

    /**
     * 缺 Key 时的引导：展开「高级参数」（这样能看到「TTS 厂商」芯片），
     * 并明确告诉用户**密钥在设置页**（主页已不再暴露密钥框）。
     */
    private fun revealApiKeyField() {
        if (!store.advExpanded) {
            setAdvanced(true, animate = false)
            store.advExpanded = true
        }
        toast(getString(R.string.keys_hint))
    }

    /** 设置「模型」输入框并把它标记为"未被用户手改" */
    private fun setModelField(v: String) {
        suppressModelWatch = true
        etModel.setText(v)
        suppressModelWatch = false
        modelTouched = false
    }

    /** v0.3：主题 + 壁纸统一入口（设置页改完回来即生效） */
    // ── 启动耗时打点（2026-10-17 探针）：只记事实，不改逻辑。
    //    产物：filesDir/startup.txt（保留最近 ~8KB），点「诊断快照」时也会一起带出来。
    private var tStart = 0L
    private fun mark(label: String) {
        if (tStart == 0L) return
        runCatching {
            val f = java.io.File(filesDir, "startup.txt")
            if (f.length() > 8000) f.delete()
            f.appendText(
                java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) +
                    String.format(java.util.Locale.US, "  +%5d ms  %s%n",
                        android.os.SystemClock.elapsedRealtime() - tStart, label)
            )
        }
    }

    private fun applyLook() {
        val c = Skin.colors(this)
        Skin.applyWindow(this, c)
        Skin.apply(window.decorView, c)
        Wp.applySlot(this, wpImg, wpScrim, store.wpMain, store.scrimMain, c.bg)
        // 下拉浮层：底色 / 描边 / 宽度 跟随主题（音色选择器已改为自绘，见 showVoicePickerPopup）
        val ddw = resources.displayMetrics.widthPixels - dp(28)
        ivVoiceInd.indicatorColor = runCatching { Color.parseColor(store.indicatorColor) }
            .getOrDefault(0xFF2FE39B.toInt())
        spFormat.setPopupBackgroundDrawable(Skin.shapeDp(this, c.bg, c.line, 12f))
        spFormat.setDropDownWidth(ddw)
        // 主页「密钥」下拉同样是 AutoCompleteTextView —— 不刷底就是系统深色弹层（黑卡）
        (etApiKey as? android.widget.AutoCompleteTextView)
            ?.setDropDownBackgroundDrawable(Skin.shapeDp(this, c.card, c.line, 12f, 100, 1f))
        // 结果卡三颗按钮与记录卡统一成"玻璃按键"
        listOf(btnPlay, btnShare, btnExport).forEach { it.background = glassBg(); it.tag = "bg:keep" }

        // ⑭ 色彩派生（《夕汀前端规范》2026-10-05）：中国传统色 · 浅色 26 套
        rebuildFormats()      // 换厂商回来 → 格式下拉按新厂商过滤
        val dv = Palettes.derivOf(store.themeId)
        val pal = Palettes.byId(store.themeId)
        val bR = (pal?.btnRadius ?: 12).toFloat()
        // 「生成」主行动按钮：提亮提纯实色 + 按"白字线"配的字色（未列入的配色回退给 Skin 照常处理）
        if (dv != null) {
            btnGenerate.background = genButtonBg(dv.gen, bR)
            btnGenerate.tag = "bg:keep"
            // 「生成」字色**统一白字**（用户 2026-10-05：绿/金系原先用主题近黑，觉得不好看；已看过预览）
            btnGenerate.setTextColor(if (dv.genText != 0) dv.genText else 0xFFFFFFFF.toInt())
        } else {
            btnGenerate.tag = null
            btnGenerate.background = ContextCompat.getDrawable(this, R.drawable.bg_btn_primary)
            btnGenerate.setTextColor(0xFFFFFFFF.toInt())
        }
        // ⚠️ 这两个以前只在 bindViews 里刷一次 → 换主题后**留在旧色**（竹青下仍是蓝）→ 现在并入 applyLook。
        // 智能填参：默认 accent 派生玻璃；⑭ 给了专属底/字（竹青）就用专属
        btnSmartFill.background = if (dv != null && dv.sparkBg != 0)
            Skin.shapeDp(this, dv.sparkBg, null, bR, 100, 0f) else glassBg()
        btnSmartFill.tag = "bg:keep"
        btnSmartFill.setTextColor(if (dv != null && dv.spark != 0) dv.spark else c.acc)
        // 「角色提示词」：保持旧版观感（淡底 + 细边、字色走 dim）；⑭ 给了专属字色（竹青 #778D61）时用它。
        btnRole.setTextColor(if (dv != null && dv.spark != 0) dv.spark else c.dim)
        // 备注：曾做过"accent 字 + 细 accent 描边"的版本，用户说"还有点喜欢、但先不定"——
        // 于是**只留在代码里、不启用**。将来若要换回，把下面两行放开即可：
        //   btnRole.background = Skin.shapeDp(this, 0x00000000, c.acc, 11f, 100, 1f); btnRole.tag = "bg:keep"
        //   btnRole.setTextColor(c.acc)
        // 「翻译」是同一款 accent 玻璃，一并刷新（字色保持原样，不动它的观感）
        btnTranslate.background = glassBg()
        btnTranslate.tag = "bg:keep"
        syncTransEngineUi()      // 「翻译方式」那行：显示当前实际走哪家（LLM / 免费接口）
    }

    private fun bindViews() {
        svRoot = findViewById(R.id.svRoot)
        wpImg = findViewById(R.id.wpImg)
        wpScrim = findViewById(R.id.wpScrim)
        btnSettings = findViewById(R.id.btnSettings)
        llVoice = findViewById(R.id.llVoice)
        tvVoice = findViewById(R.id.tvVoice)
        ivVoiceArrow = findViewById(R.id.ivVoiceArrow)
        ivVoiceInd = findViewById(R.id.ivVoiceInd)
        btnTranslate = findViewById(R.id.btnTranslate)
        tvTransTarget = findViewById(R.id.tvTransTarget)
        tvTransEngine = findViewById(R.id.tvTransEngine)
        tvTransEngine.setOnClickListener { showTransEnginePick() }
        llTransBlocks = findViewById(R.id.llTransBlocks)
        etProvMain = findViewById(R.id.etProvMain)
        setupProviderField()
        btnYi = findViewById(R.id.btnYi)
        btnRun = findViewById(R.id.btnRun)
        btnRole = findViewById(R.id.btnRole)
        btnSmartFill = findViewById(R.id.btnSmartFill)
        etCustomVoice = findViewById(R.id.etCustomVoice)
        tvVoiceNote = findViewById(R.id.tvVoiceNote)
        btnCreateVoice = findViewById(R.id.btnCreateVoice)
        etText = findViewById(R.id.etText)
        btnClearText = findViewById(R.id.btnClearText)
        // 清空叉号：仅清空文本，不改变其它参数
        btnClearText.setOnClickListener {
            transVersions.clear(); trDelLine = -1; trDelHits = 0   // 整段清空 = 退出多译版模式，别被"删字"逻辑拆行
            etText.setText("")
            etText.requestFocus()
        }
        etInstr = findViewById(R.id.etInstr)
        llChips = findViewById(R.id.llChips)
        etSeed = findViewById(R.id.etSeed)
        // 文本一变（含被清空）→ **骰子清空**：下次生成重新拿随机种子，让模型"自由发挥"（用户 2026-10-05）
        //（放在 etSeed 初始化之后挂，避免启动路径上 watcher 先触发、lateinit 还没就绪）
        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editStart = start; editRemoved = before; editAdded = count
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (::etSeed.isInitialized && etSeed.text.isNotEmpty()) etSeed.setText("")
                val composing = (s as? android.text.Spannable)?.let {
                    android.view.inputmethod.BaseInputConnection.getComposingSpanStart(it) >= 0
                } ?: false
                handleMultiVersionEdit(s?.toString() ?: "", editStart, editRemoved, editAdded, composing)
            }
        })
        btnDice = findViewById(R.id.btnDice)
        sbRate = findViewById(R.id.sbRate)
        Ratchet.attach(sbRate, 5) { onParamChanged() }   // 语速：0.05 一档
        tvRate = findViewById(R.id.tvRate)
        sbPitch = findViewById(R.id.sbPitch)
        Ratchet.attach(sbPitch, 5) { onParamChanged() }  // 音调：0.05 一档
        tvPitch = findViewById(R.id.tvPitch)
        sbVol = findViewById(R.id.sbVol)
        Ratchet.attach(sbVol, 2) { onParamChanged() }    // 音量：2% 一档
        tvVol = findViewById(R.id.tvVol)
        spFormat = findViewById(R.id.spFormat)
        tvAdvanced = findViewById(R.id.tvAdvanced)
        llAdvanced = findViewById(R.id.llAdvanced)
        etModel = findViewById(R.id.etModel)
        // 记录用户是否手改过 model（onResume 时据此决定要不要用 store 覆盖，避免丢输入）
        etModel.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (!suppressModelWatch) modelTouched = true }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        etApiKey = findViewById(R.id.etApiKey)
        // 高级参数里的 API 密钥：改完即生效（与设置页共用同一个本机存档）
        etApiKey.setText(store.apiKey)
        etApiKey.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.trim() ?: ""
                store.apiKey = v
                // 百炼就是"主 Key"：与厂商栏那一份保持同步，避免两处不一致
                if (store.providerId == "aliyun-bailian") store.setProviderKey("aliyun-bailian", v)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        etLangHints = findViewById(R.id.etLangHints)
        etHotfix = findViewById(R.id.etHotfix)
        etExtra = findViewById(R.id.etExtra)
        cbSsml = findViewById(R.id.cbSsml)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnCancel = findViewById(R.id.btnCancel)
        tvStatus = findViewById(R.id.tvStatus)
        cardResult = findViewById(R.id.cardResult)
        tvResultInfo = findViewById(R.id.tvResultInfo)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)
        btnExport = findViewById(R.id.btnExport)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        llHistory = findViewById(R.id.llHistory)
        llHistoryTitle = findViewById(R.id.llHistoryTitle)

        // 点按反馈：小按钮 = 水波（记录卡里另有"留痕"），主行动 = 水波 + 下沉
        listOf<View>(btnSettings, btnCreateVoice, btnDice, btnCancel, btnPlay,
            btnShare, btnExport, btnClearHistory, btnClearText, tvAdvanced).forEach { fx(it) }
        fxPrimary(btnGenerate)

        setAdvanced(store.advExpanded, animate = false)

        // 空白处点按也有"留痕"（整页常驻；滚动不算——位移超过阈值就跳过）
        findViewById<View>(R.id.llRoot)?.let { TapFx.tapAnywhere(it) { Skin.colors(this).acc } }

        // 首页密钥栏也吃"用过的密钥"历史：点一下/输一个字就弹（和设置页同一套）
        (etApiKey as? android.widget.AutoCompleteTextView)?.let { ac ->
            ac.setAdapter(ContainsAdapter(this, keyHistory()))
            ac.threshold = 0
            ac.setOnClickListener { ac.showDropDown() }
            ac.setOnFocusChangeListener { _, has -> if (has) ac.showDropDown() }
        }

        // 翻译：一键把文本框内容翻成目标语言（内置免费接口，不跳 App）
        btnTranslate.background = glassBg()
        btnTranslate.tag = "bg:keep"
        fx(btnTranslate, findViewById(R.id.llRoot))
        btnTranslate.setOnClickListener { translateText() }
        tvTransTarget.setOnClickListener { pickTransLangs() }
        syncTransUi()

        // 悬浮「译」：判定 48dp、字形小而浅；位置 = 文本框✕所在竖线 × 屏幕竖直中心
        // ⚠️ 2026-10-15 用户反馈：这两个小字**没有点按特效**像"点了没反应" → 补上水波 + 留痕星点
        // （旧注释担心"涟漪在字上闪一下像变色"，实测不如"完全没反馈"糟）
        btnYi.setTextColor((Skin.colors(this).dim and 0x00FFFFFF) or (0x8A shl 24))
        fx(btnYi, findViewById(R.id.llRoot))
        // 智能填参：给 LLM 一条请求 → 自动挑厂商 / 写风格指令 / 生成纠错表
        btnSmartFill.background = glassBg()
        btnSmartFill.tag = "bg:keep"
        // 原来是 @color/dim（灰字压透明底）→ 看着"发闷、不亮"；改主色字，玻璃底才立起来
        btnSmartFill.setTextColor(Skin.colors(this).acc)
        fx(btnSmartFill, findViewById(R.id.llRoot))
        btnSmartFill.setOnClickListener { smartFill() }

        // 一物两用：文本框**空**→ 选目标语言；文本框**有字**→ 直接翻译
        btnRun.setTextColor((Skin.colors(this).dim and 0x00FFFFFF) or (0x8A shl 24))
        fx(btnRun, findViewById(R.id.llRoot))      // 同上：补点按反馈（用户 2026-10-15）
        btnRun.setOnClickListener { polishText() }
        // 「角色提示词」：写这个音色的人设（喂给 AI，不是给 TTS）。**保持旧版淡底细边**，字色随主题 dim。
        btnRole.setTextColor(Skin.colors(this).dim)
        fx(btnRole, findViewById(R.id.llRoot))
        btnRole.setOnClickListener { openRoleDialog() }
        // 「译」：直接按"高级参数里选好的语言"翻译（不再看文本框空不空——那正是旧机制的病根）
        btnYi.setOnClickListener { translateText() }
    }

    /** 润色：把文本框内容交给 LLM 润色成"适合朗读的稿子"，原地替换 */
    private fun polishText() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) { toast(getString(R.string.toast_need_text)); return }
        if (store.llmKey.isBlank()) {
            toast(getString(R.string.need_llm_polish))
            openSettings()
            return
        }
        llmRewrite(false, text)
    }

    /**
     * 翻译（新机制 · 2026-10-05 用户定稿）：
     *   高级参数里**多选语言** → 系统判定源语言 → 翻成"所选语言里除源语言之外的"。
     *   · 勾 2 种 = 互译：只出一版，**原地替换**；
     *   · 勾 3 种＋ = 多版本：**所有译版都写进「合成文本」这一个框**（每行一版）；「生成」逐个生成。
     *   · 配了 LLM 且只出一版时，优先走 LLM（质量好）。
     */
    private fun translateText() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) { toast(getString(R.string.toast_need_text)); return }
        val langs = store.transLangList()
        if (langs.isEmpty()) { toast(getString(R.string.need_trans_lang)); return }

        val src = detectLangName(text)
        var targets = if (src.isNotEmpty() && langs.contains(src)) langs.filter { it != src } else langs
        if (targets.isEmpty()) targets = langs

        // 用户 2026-10-14：翻译方式**可选**（以前只按"有没有 LLM Key"暗自动，用户不知道用的是哪家）
        val useLlm = when (store.transEngine) {
            "llm" -> true
            "free" -> false
            else -> store.llmKey.isNotBlank()
        }
        if (useLlm && store.llmKey.isBlank()) {
            toast(getString(R.string.trans_need_llm)); return
        }

        clearTransBlocks()
        tvStatus.text = getString(R.string.st_translating)
        btnTranslate.isEnabled = false

        if (targets.size <= 1) {
            val t = targets.first()
            store.trTarget = t
            if (useLlm) { btnTranslate.isEnabled = true; llmRewrite(true, text); return }
            TransClient.translate(text, t, store.trLastSource, store.transEmail) { out, err, s ->
                ui {
                    btnTranslate.isEnabled = true
                    if (out != null) {
                        s?.let { store.trLastSource = it }
                        transVersions.clear()
                        etText.setText(out)
                        tvStatus.text = getString(R.string.trans_done, t)
                    } else {
                        tvStatus.text = "❌ " + (err ?: getString(R.string.trans_failed)); toast(err ?: getString(R.string.trans_failed))
                    }
                }
            }
        } else {
            TransClient.translateMany(text, targets, store.transEmail, store.trLastSource) { map, err ->
                ui {
                    btnTranslate.isEnabled = true
                    if (map == null || map.isEmpty()) {
                        tvStatus.text = "❌ " + (err ?: getString(R.string.trans_failed)); toast(err ?: getString(R.string.trans_failed))
                    } else {
                        transVersions.clear()
                        transVersions.addAll(map.values)
                        etText.setText(transVersions.joinToString("\n"))
                        tvStatus.text = getString(R.string.trans_multi_done, map.keys.joinToString(" · "))
                    }
                }
            }
        }
    }

    /** 源语言判定：假名/谚文/西里尔/阿拉伯/泰文优先，其次比"中文字数 vs 拉丁字母数" */
    private fun detectLangName(s: String): String {
        var cjk = 0; var kana = 0; var hangul = 0; var cyr = 0; var arab = 0; var thai = 0; var latin = 0
        for (ch in s) {
            val code = ch.code
            when {
                code in 0x3040..0x30FF -> kana++
                code in 0xAC00..0xD7AF -> hangul++
                code in 0x0400..0x04FF -> cyr++
                code in 0x0600..0x06FF -> arab++
                code in 0x0E00..0x0E7F -> thai++
                code in 0x4E00..0x9FFF -> cjk++
                (code in 65..90) || (code in 97..122) -> latin++
            }
        }
        if (kana > 0) return "日语"
        if (hangul > 0) return "韩语"
        if (cyr > 0) return "俄语"
        if (arab > 0) return "阿拉伯语"
        if (thai > 0) return "泰语"
        return if (cjk > 0 && cjk >= latin) "中文" else if (latin > 0) "英语" else ""
    }

    /** 清掉所有"译版块" */
    private fun clearTransBlocks() {
        llTransBlocks.removeAllViews()
        llTransBlocks.visibility = View.GONE
    }

    /**
     * 「多译版」状态下（一次翻出好几版，都写进「合成文本」框、每版之间用换行分隔）：
     * **在同一版里连续删到 2 个字** → **整版删除**（这一版消失、其余版上移）。
     * - 只删 1 个字时按兵不动（防手滑一下抹掉整版），但让 `transVersions` 与文本保持同步；
     * - 打字 / 替换 / 整段清空 → 退出多译版模式（沿用原行为）；
     * - **版本内部可以含换行**（多段原文的译文）——按“版本块”定位，不再按行号；
     * - 输入法组词中的插入不退出模式（否则 `transVersions` 会被提前清空，后面再删就没反应）。
     * 用户 2026-10-08 指定；2026-10-17 重写为块定位。
     */
    private fun handleMultiVersionEdit(
        newText: String, start: Int, removed: Int, added: Int, composing: Boolean
    ) {
        if (transVersions.isEmpty()) { trDelLine = -1; trDelHits = 0; return }
        val old = transVersions.joinToString("\n")
        if (newText == old) return

        // 打字 / 替换 → 退出多译版模式；输入法“组词中”的插入不算
        if (added > 0) {
            if (!composing) { transVersions.clear(); trDelLine = -1; trDelHits = 0 }
            return
        }
        if (removed <= 0) return
        if (newText.isEmpty()) {                         // 整段删光
            transVersions.clear(); trDelLine = -1; trDelHits = 0
            return
        }

        // 每一版在整段文本里的起点（版本内部可以含换行）
        val starts = IntArray(transVersions.size)
        var pos = 0
        for (i in transVersions.indices) {
            starts[i] = pos
            pos += transVersions[i].length + 1           // + 版间分隔换行
        }

        // 定位删除起点落在哪一版；落在版间换行上 = 跨版编辑 → 退出模式
        var vi = -1
        for (i in transVersions.indices) {
            val s = starts[i]
            val e = s + transVersions[i].length
            if (start in s until e || (i == transVersions.lastIndex && start == e)) { vi = i; break }
        }
        if (vi < 0) { transVersions.clear(); trDelLine = -1; trDelHits = 0; return }

        val vs = starts[vi]
        val ve = vs + transVersions[vi].length
        if (start < vs || start + removed > ve) {         // 选中内容跨了版本/换行
            transVersions.clear(); trDelLine = -1; trDelHits = 0
            return
        }

        val local = start - vs
        val localEnd = local + removed
        if (vi == trDelLine) trDelHits += removed else { trDelLine = vi; trDelHits = removed }

        if (trDelHits < 2) {                             // 只删了 1 个字：先不动整版，但同步这一版
            transVersions[vi] = transVersions[vi].removeRange(local, localEnd)
            return
        }
        // 删够 2 个字 → 整版删除（只删这一版）
        trDelLine = -1; trDelHits = 0
        transVersions.removeAt(vi)
        if (transVersions.isEmpty()) {
            etText.setText("")
            return
        }
        val joined = transVersions.joinToString("\n")
        // 2026-10-18 用户：整版删掉后手没刹住，退格会接着把下一版也删掉。
        // 最稳的办法：把光标送回开头。继续按退格前面没字可删，不会连锁误删；
        // 想删下一版必须自己重新把光标点进去。
        etText.setText(joined)
        etText.setSelection(0)
        toast(getString(R.string.trans_version_deleted))
    }

    /** 走 LLM：translate=true 翻译 / false 润色 */
    private fun llmRewrite(translate: Boolean, text: String) {
        val sys = if (translate)
            store.transPrompt.ifBlank { LlmClient.DEFAULT_TRANS_PROMPT }.replace("{target}", store.trTarget)
        else
            store.llmPrompt.ifBlank { LlmClient.DEFAULT_POLISH_PROMPT }
        tvStatus.text = if (translate) getString(R.string.llm_trans_busy) else getString(R.string.llm_polish_busy)
        btnTranslate.isEnabled = false
        // 润色时带上「音色 + 角色提示词」（给世界知识不足的模型补背景）；翻译则只给正文
        val userMsg = if (!translate && roleVoiceId().isNotBlank()) buildString {
            val nm = voiceNameOf(roleVoiceId())
            if (nm.isNotBlank()) append(getString(R.string.pfx_voice)).append(nm).append('\n')
            val role = store.rolePrompt(roleVoiceId())
            if (role.isNotBlank()) append(getString(R.string.pfx_role)).append(role).append('\n')
            append("内容：\n").append(text)
        } else text
        LlmClient.ask(
            store.llmBaseUrl, store.llmKey, store.llmModel, sys, userMsg,
            store.llmMaxTokens, store.llmTemp / 10.0, store.llmLevel
        ) { out, err ->
            ui {
                btnTranslate.isEnabled = true
                if (out != null) {
                    transVersions.clear()          // 整段替换 = 退出多译版模式
                    etText.setText(out)
                    tvStatus.text = if (translate) getString(R.string.llm_trans_done, store.trTarget) else getString(R.string.llm_polish_done)
                } else {
                    tvStatus.text = "❌ " + (err ?: getString(R.string.common_fail))
                    toast(err ?: getString(R.string.common_fail))
                }
            }
        }
    }



    /**
     * 智能填参（2026-10-15 改版 · **两条请求并发**）。
     *
     * 为什么拆成两条：这两件事是**完全不同的任务**，塞进同一条 prompt 模型会互相干扰
     * （实测：让它同时"写风格指令 + 给原文加标签"，它跑去写台词了）：
     *   · A) 情绪 / 风格指令（＋纠错表）→ 写进 **`etInstr` / `etHotfix`**
     *   · B) 给原文加情感标签            → 写回 **`etText`**（就是"文本"那个框）
     * 两个框是分开的，所以两条请求各写各的、互不覆盖；同时发出（并发），谁先回谁先落。
     *
     * 进函数还多做一件事：**校验"当前音色"是否属于"当前 TTS 厂商"**，
     * 不属于就自动切过去（前提：目标厂商已存过 Key；没 Key 只提示，绝不静默切）。
     */
    private fun smartFill() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) {
            toast(getString(R.string.toast_need_text))
            return
        }
        if (store.llmKey.isBlank()) {
            toast(getString(R.string.need_llm_key_smart))
            openSettings()
            return
        }

        // ── ⓪ 音色 ↔ 厂商 自动校正（会改 store.providerId；返回一句要显示的话） ──
        val alignNote = alignProviderToVoice()

        val voice = if (voiceIsCustom) getString(R.string.voice_custom) else (allVoices().firstOrNull { it.id == currentVoiceId }?.name ?: "")
        // 当前 TTS 厂商 + 它接受的参数：**系统按当前配置自动附带**（不让模型去猜 provider）
        val prov = TtsProviders.byId(store.providerId)
        // 该模型吃不吃"情绪/风格指令"（由**软件**按数据判定，不让 LLM 猜）
        val instrOk = TtsModels.supports(store.providerId, store.lastModel, "instruction")
        val head = buildString {
            if (prov != null) {
                append(getString(R.string.pfx_provider)).append(prov.name).append("（").append(prov.id).append("）\n")
                append(getString(R.string.pfx_model)).append(store.lastModel.ifBlank { getString(R.string.not_specified) }).append('\n')
                val params = TtsModels.supportedNames(store.providerId, store.lastModel)
                append(getString(R.string.pfx_params)).append(if (params.isEmpty()) getString(R.string.params_only_text_voice) else params.joinToString("、")).append('\n')
                if (!instrOk) append(getString(R.string.pfx_no_instr)).append('\n')   // 例如 CosyVoice 之外的普通 Qwen-TTS
            }
            append(getString(R.string.pfx_voice)).append(voice.ifBlank { getString(R.string.not_specified) }).append('\n')
            val role = store.rolePrompt(roleVoiceId())
            if (role.isNotBlank()) append(getString(R.string.pfx_role)).append(role).append('\n')
            if (store.llmExtra.isNotBlank()) append(getString(R.string.pfx_extra)).append(store.llmExtra).append('\n')
        }

        tvStatus.text = getString(R.string.smartfill_busy)
        btnSmartFill.isEnabled = false

        // 两条请求各自完成 → 汇总（两条都落定才重新放开按钮）
        var instrRes: String? = null
        var tagRes: String? = null
        fun settle() {
            if (instrRes == null || tagRes == null) return
            btnSmartFill.isEnabled = true
            val a = instrRes!!
            val b = tagRes!!
            tvStatus.text = buildString {
                append(when (a) {
                    OK -> getString(R.string.smartfill_a_ok)
                    SKIP -> getString(R.string.smartfill_a_skip)      // 该模型不吃风格指令
                    else -> getString(R.string.smartfill_a_bad, a)
                })
                append("　")
                append(if (b == OK) getString(R.string.smartfill_b_ok) else getString(R.string.smartfill_b_bad, b))
                if (!alignNote.isNullOrBlank()) append('\n').append(alignNote)
            }
        }

        // ── A) 情绪 / 风格指令 + 纠错表 → etInstr / etHotfix ──
        //    2026-10-15 修"风格指令加工失败"：① 服务端强制 JSON（jsonMode）
        //    ② 把 JSON 要求**放在 user 消息末尾**（模型对最后一句最敏感；只写在 system 里常被忽略）
        //    ③ 该模型本来就不吃风格指令（如普通 Qwen-TTS）→ 直接跳过，别让 LLM 白写
        if (!instrOk) {
            instrRes = SKIP
            settle()
        } else LlmClient.ask(
            store.llmBaseUrl, store.llmKey, store.llmModel,
            store.llmPrompt.ifBlank { LlmClient.DEFAULT_PROMPT },
            head + getString(R.string.pfx_text) + "\n" + text +
                "\n\n只输出 JSON（不要解释、不要代码块、不要引号）：" +
                "{\"instruction\":\"一句自然语言风格指令\"}",
            store.llmMaxTokens, store.llmTemp / 10.0, store.llmLevel,
            jsonMode = true
        ) { out, err ->
            ui {
                instrRes = if (out == null) (err ?: getString(R.string.toast_req_fail))
                else if (applySmartFill(out)) OK
                else getString(R.string.smartfill_bad, out.take(60))
                settle()
            }
        }

        // ── B) 给原文加情感标签 → etText（**不动** etInstr） ──
        LlmClient.ask(
            store.llmBaseUrl, store.llmKey, store.llmModel,
            LlmClient.DEFAULT_TAG_PROMPT,
            head + getString(R.string.pfx_text_raw) + "\n" + text,
            store.llmMaxTokens, store.llmTemp / 10.0, store.llmLevel
        ) { out, err ->
            ui {
                tagRes = if (out == null) (err ?: getString(R.string.toast_req_fail)) else {
                    val tagged = stripCodeFence(out).trim()
                    when {
                        tagged.isEmpty() -> getString(R.string.smartfill_tag_none)
                        '[' !in tagged -> getString(R.string.smartfill_tag_none)   // 安全网：没标签就不覆盖原文
                        else -> { etText.setText(tagged); OK }
                    }
                }
                settle()
            }
        }
    }

    /** 两条请求共用的哨兵（别用字面量散在各处） */
    private val OK = "OK"
    private val SKIP = "SKIP"

    /** 去掉 ```json / ``` 之类的围栏（模型很爱加） */
    private fun stripCodeFence(s: String): String =
        s.replace(Regex("^\\s*```[a-zA-Z]*\\s*"), "").replace(Regex("\\s*```\\s*$"), "")

    /**
     * 音色 ↔ 厂商 自动校正（用户 2026-10-15 要求）。
     * 返回一句要显示在状态栏的话；不需要动/没话说 → null。
     *
     * · 认不出这个音色属于哪家 → **不动**（不猜）
     * · 认得出且和当前厂商一致 → 不动
     * · 不一致 → 目标厂商**已存 Key** 才切；否则只提示，避免切过去反而不能用
     */
    private fun alignProviderToVoice(): String? {
        val want = VoiceProviders.of(selectedVoiceId(), customVoices) ?: return null
        if (want == store.providerId) return null
        val target = TtsProviders.byId(want) ?: return null
        val k = store.providerKey(want).ifBlank { if (want == VoiceProviders.BAILIAN) store.apiKey else "" }
        if (k.isBlank()) return getString(R.string.smartfill_prov_nokey, target.name)
        store.providerId = want
        setModelField(store.lastModel)
        rebuildFormats()
        syncProviderField()
        syncVoiceUi()
        return getString(R.string.smartfill_prov_switched, target.name)
    }

    /** 解析并应用 LLM 的建议（容错：从返回里抠出第一个 { ... } 再解析） */
    /**
     * 解析并应用 LLM 的建议（容错：剥围栏 → 抠第一个 `{ … }` 再解析）。
     *
     * ⚠️ 2026-10-15 用户要求：**「智能填参」不再管纠错表（`hot_fix`）**。
     * 那是发给 TTS 的**发音修正**参数（多音字/专名注音），属于"用户按需手填"的东西，
     * 不该让 LLM 顺手改；厂商/模型的基础条件也由**软件**拼好，不回头相信 LLM 的 `provider`。
     * → 这里只认 `instruction`（风格指令）。
     */
    private fun applySmartFill(raw0: String): Boolean = try {
        val raw = stripCodeFence(raw0)          // 模型爱加 ```json 围栏 → 先剥掉
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        if (s < 0 || e <= s) false else {
            val o = JSONObject(raw.substring(s, e + 1))
            val instr = o.optString("instruction").trim()
            if (instr.isEmpty()) false else {
                etInstr.setText(instr)
                true
            }
        }
    } catch (ex: Exception) {
        false
    }

    /** 刷新翻译相关文案（目标语言） */
    private fun syncTransUi() {
        val ls = store.transLangList()
        tvTransTarget.text = if (ls.isEmpty()) "" else ls.joinToString(" · ")
        syncTransEngineUi()
    }

    /**
     * 「翻译方式」那一行：显示**当前实际会走哪家**（用户 2026-10-14：
     * 以前只按"有没有 LLM Key"暗自动，用户根本看不出用的是 LLM 还是免费接口）。
     */
    private fun syncTransEngineUi() {
        if (!::tvTransEngine.isInitialized) return
        val mode = when (store.transEngine) {
            "llm" -> getString(R.string.trans_engine_llm)
            "free" -> getString(R.string.trans_engine_free)
            else -> getString(R.string.trans_engine_auto)
        }
        val eff = if (store.transEngine != "free" &&
            (store.transEngine == "llm" || store.llmKey.isNotBlank())
        ) {
            "LLM" + store.llmModel.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        } else getString(R.string.trans_eff_free)
        tvTransEngine.text = getString(R.string.trans_engine_fmt, mode, eff)
    }

    /** 选「翻译方式」：自动 / 只用 LLM / 只用免费接口（自绘行；✓ **紧跟文字**，不再顶到卡片最右边） */
    private fun showTransEnginePick() {
        val c = Skin.colors(this)
        var pop: android.widget.PopupWindow? = null
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val opts = listOf(
            "auto" to getString(R.string.trans_engine_auto),
            "llm" to getString(R.string.trans_engine_llm),
            "free" to getString(R.string.trans_engine_free)
        )
        for ((code, name) in opts) {
            val on = store.transEngine == code
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // 2026-10-15 用户：这三项离卡片左边太远 → 左内距 16 → 10
                setPadding(dp(10), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
            }
            row.addView(TextView(this).apply {
                text = name
                setTextColor(if (on) c.acc else c.txt)
                textSize = 14f
                // 去掉 weight=1：否则 ✓ 被推到卡片最右边（用户 2026-10-15）
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
            if (on) row.addView(TextView(this).apply {
                text = "✓"
                setTextColor(c.acc)
                textSize = 14f
                setPadding(dp(6), 0, 0, 0)          // ✓ 紧跟文字右边
            })
            fx(row)
            row.setOnClickListener {
                pop?.dismiss()
                store.transEngine = code
                syncTransEngineUi()
            }
            box.addView(row)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Skin.shapeDp(this@MainActivity, c.bg, c.line, 12f)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        panel.addView(box)
        val w = minOf(dp(320), resources.displayMetrics.widthPixels - dp(32))
        pop = android.widget.PopupWindow(panel, w, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pop.inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        pop.isOutsideTouchable = true
        pop.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        pop.elevation = dp(8).toFloat()
        pop.showAsDropDown(tvTransEngine, 0, dp(2))
    }

    /**
     * 选目标语言。
     * 注意：不能用 setSingleChoiceItems —— 它的列表项吃系统 textColorPrimary（近白），
     * 在浅色主题的弹窗底板上会**看不见字**（实测）。这里改成自带主题色的自定义行。
     */
    /**
     * 「翻译语言」**多选**弹窗（点高级参数里那个字段打开）。
     * 勾几种决定行为：2 种＝互译；3 种＋＝多版本（译版在上、原文压底）。
     */
    private fun pickTransLangs() {
        val c = Skin.colors(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(6), dp(18), dp(6))
        val draft = LinkedHashSet(store.transLangList())
        val rows = ArrayList<TextView>()

        fun refresh() {
            for (tv in rows) {
                val nm = tv.tag as? String ?: ""
                val on = draft.contains(nm)
                tv.text = (if (on) "✓ " else "　 ") + nm
                tv.setTextColor(if (on) c.acc else c.txt)
            }
        }

        for (name in TransClient.targetNames()) {
            val tv = TextView(this)
            // ⚠️ 名字存进 tag —— **不能从 tv.text 反推**：初始为空 → 每行只剩空格 → 弹窗整页空白（bug）
            tv.tag = name
            tv.textSize = 15f
            tv.setPadding(dp(4), dp(11), dp(4), dp(11))
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                val nm = tv.tag as? String ?: return@setOnClickListener
                if (draft.contains(nm)) draft.remove(nm) else draft.add(nm)
                refresh()
            }
            rows.add(tv)
            box.addView(tv)
        }
        refresh()

        val sc = ScrollView(this)
        sc.addView(box)
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.trans_langs_title))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_ok)) { _, _ ->
                store.transLangs = draft.joinToString(",")
                syncTransUi()
                toast(getString(R.string.trans_langs_toast, if (draft.isEmpty()) getString(R.string.trans_none) else draft.joinToString(" · ")))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener {
            skinDialog(dlg)
            sc.layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 300).toInt()
            )
            dlg.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.72).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dlg.setCanceledOnTouchOutside(true)
        dlg.show()
    }

    /** 高级参数展开/收起：180ms 高度过渡（原来瞬变） */
    private fun setAdvanced(show: Boolean, animate: Boolean) {
        tvAdvanced.text = if (show) getString(R.string.adv_expand) else getString(R.string.adv_collapse)
        if (!animate) {
            llAdvanced.visibility = if (show) View.VISIBLE else View.GONE
            return
        }
        if (show) {
            llAdvanced.visibility = View.VISIBLE
            llAdvanced.alpha = 0f
            val w = llAdvanced.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            llAdvanced.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val h = llAdvanced.measuredHeight
            val a = android.animation.ValueAnimator.ofInt(0, h)
            a.addUpdateListener {
                llAdvanced.layoutParams = llAdvanced.layoutParams.apply { height = it.animatedValue as Int }
                llAdvanced.requestLayout()
            }
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(an: android.animation.Animator) {
                    llAdvanced.layoutParams = llAdvanced.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                    llAdvanced.requestLayout()
                }
            })
            a.duration = 180; a.start()
            llAdvanced.animate().alpha(1f).setDuration(180).start()
        } else {
            val h = llAdvanced.height
            if (h <= 0) { llAdvanced.visibility = View.GONE; return }
            val a = android.animation.ValueAnimator.ofInt(h, 0)
            a.addUpdateListener {
                llAdvanced.layoutParams = llAdvanced.layoutParams.apply { height = it.animatedValue as Int }
                llAdvanced.alpha = 1f - it.animatedFraction
                llAdvanced.requestLayout()
            }
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(an: android.animation.Animator) {
                    llAdvanced.layoutParams = llAdvanced.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                    llAdvanced.visibility = View.GONE
                    llAdvanced.alpha = 1f
                }
            })
            a.duration = 180; a.start()
        }
    }

    /** 点按水波（+ 传 root 时在该卡片里迸"留痕"星点） */
    private fun fx(v: View?, root: View? = null) {
        v ?: return
        TapFx.press(v, Skin.colors(this).acc, root)
    }

    /** 主行动按钮：额外加"按下下沉 / 松手回弹" */
    private fun fxPrimary(v: View?) {
        v ?: return
        TapFx.press(v, Skin.colors(this).acc, null, primary = true)
    }

    /** 下拉浮层适配器：浮层条目文字颜色跟随主题（其余沿用系统样式） */
    private class ThemedSpinnerAdapter(ctx: Context, items: List<String>) :
        ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent)
            // ⚠️ 显式声明角色（Skin.setRole）：否则换主题后 Skin 认不出它的字色角色 → 字色**停在旧主题**
            //（症状：深色主题下格式那行仍是黑字）
            (v as? TextView)?.let {
                it.setTextColor(Skin.colors(parent.context).txt)
                Skin.setRole(it, "txt")
            }
            pressFx(v, parent)
            return v
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getDropDownView(position, convertView, parent)
            (v as? TextView)?.let {
                it.setTextColor(Skin.colors(v.context).txt)
                Skin.setRole(it, "txt")
            }
            // 下拉卡片里的每一项也给点按反馈：水波 + 留痕（留痕画在它所在的那个列表上）
            TapFx.press(v, Skin.colors(v.context).acc, rootAtTouch = { (v.parent as? View) })
            return v
        }

        /** 收起状态的那个控件本身也给反馈（留痕画在它自己的父容器上） */
        private fun pressFx(v: View, parent: ViewGroup) {
            TapFx.press(v, Skin.colors(v.context).acc, rootAtTouch = { parent })
        }
    }

    private fun setupVoicePicker() {
        llVoice.setOnClickListener { showVoicePickerPopup() }
        if (currentVoiceId.isEmpty()) currentVoiceId = allVoices().firstOrNull()?.id ?: ""
        fitVoiceWidth()
        syncVoiceUi()

        setModelField(store.lastModel)
        rebuildFormats()
    }

    /**
     * 主页「格式」下拉：**只列当前厂商支持的**（不可能选的就不显示，而不是列出来再置灰）。
     * 依据 `TtsProviders.formats`（单一数据源）。
     */
    private fun rebuildFormats() {
        if (!::spFormat.isInitialized) return        // 同上：可能被早于 findViewById 的调用链碰到
        val sup = TtsProviders.byId(store.providerId)?.formats ?: formats.map { it.key }
        val sub = formats.filter { it.key in sup }.ifEmpty { formats }
        val keep = fmtList.getOrNull(spFormat.selectedItemPosition)?.key
        fmtList = sub
        spFormat.adapter = ThemedSpinnerAdapter(this, sub.map { it.label })
        val idx = sub.indexOfFirst { it.key == keep }.let { if (it >= 0) it else 0 }
        spFormat.setSelection(idx)
    }

    /** 展开态宽度对齐原 Spinner：取最长条目宽度 → 名字靠左、三角靠右 */
    private fun fitVoiceWidth() {
        val longest = (allVoices().map { it.name } + customLabel).maxByOrNull { it.length } ?: ""
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.typeface = tvVoice.typeface
        p.textSize = tvVoice.textSize
        val w = p.measureText(longest) + dp(10 + 10 + 8) + dp(24 + 10)   // ⌀ 指示符 + 间距
        val lp = llVoice.layoutParams
        lp.width = w.toInt()
        llVoice.layoutParams = lp
    }

    /** 内置音色里被用户隐藏掉的（本地生效，可恢复） */
    private val hiddenVoices = mutableSetOf<String>()

    /** 全部音色 = 内置（去掉已隐藏的） + 自建 */
    private fun allVoices(): List<Voice> =
        voices.filter { it.id !in hiddenVoices } +
            customVoices.map { Voice(it.name, it.id, getString(R.string.voice_self_prefix, it.prefix)) }

    /** 设为某个音色（voiceId 为空/已失效时回落到第一个） */
    private fun setVoice(voiceId: String?) {
        val beforeVoice = currentVoiceId
        val all = allVoices()
        if (voiceId != null && all.any { it.id == voiceId }) {
            currentVoiceId = voiceId
            voiceIsCustom = false
        } else if (all.none { it.id == currentVoiceId }) {
            currentVoiceId = all.firstOrNull()?.id ?: ""
            voiceIsCustom = false
        }
        // 换音色 → **清空骰子**：文本不变、只换音色时，让新音色从随机种子重新"自由发挥"（用户 2026-10-05）
        if (beforeVoice != currentVoiceId) etSeed.setText("")
        fitVoiceWidth()
        syncVoiceUi()
    }

    /**
     * 只设初值 + 刷一次标签。**不能**在这里再 setOnSeekBarChangeListener ——
     * 那会把 bindViews() 里 ratchet() 装的监听器覆盖掉（v0.5.5 的棘轮卡位/轻震就是这样被废掉的）。
     */
    private fun setupSliders() {
        sbRate.progress = 50
        sbPitch.progress = 50
        sbVol.progress = 50
        onParamChanged()
    }

    private fun setupActions() {
        btnSettings.setOnClickListener { openSettings() }
        btnCreateVoice.setOnClickListener {
            // 声音复刻目前只有百炼实现：别的厂商点了必然失败，直接拦下并说明
            if (TtsProviders.byId(store.providerId)?.canClone != true) {
                toast(getString(R.string.clone_unsupported, TtsProviders.byId(store.providerId)?.name ?: ""))
                return@setOnClickListener
            }
 openCreateVoice() }
        btnDice.setOnClickListener { etSeed.setText((0..65535).random().toString()) }
        tvAdvanced.setOnClickListener {
            val show = llAdvanced.visibility != View.VISIBLE
            setAdvanced(show, animate = true)
            store.advExpanded = show
        }
        btnGenerate.setOnClickListener { startGenerateAll() }
        btnCancel.setOnClickListener {
            if (busy) {
                client.cancel()
                finishBusy()
                tvStatus.text = getString(R.string.st_cancelled)
            }
        }
        btnPlay.setOnClickListener { currentTake?.let { toggleTake(it) } }
        btnShare.setOnClickListener { currentTake?.let { shareTake(it) } }
        btnExport.setOnClickListener { currentTake?.let { exportTake(it) } }
        btnClearHistory.setOnClickListener { confirmClearHistory() }
        llHistoryTitle.setOnClickListener { showVoiceFilterPopup() }
        etLangHints.setOnClickListener { openLangPicker() }
    }

    /** 只刷新三根滑条的数字标签（不碰 progress、不换肤 → 不会和拖动打架） */
    private fun onParamChanged() {
        tvRate.text = fmtNum(0.5 + sbRate.progress * 0.01)
        tvPitch.text = fmtNum(0.5 + sbPitch.progress * 0.01)
        tvVol.text = sbVol.progress.toString()
    }

    /** 角色提示词挂在哪个音色上（自定义音色时用输入的 ID） */
    private fun roleVoiceId(): String =
        if (voiceIsCustom) etCustomVoice.text.toString().trim() else currentVoiceId

    /** 「角色提示词」：给当前音色写人设 —— 只喂给 AI 助手（润色/写台词），不发给 TTS。 */
    private fun openRoleDialog() {
        val vid = roleVoiceId()
        if (vid.isBlank()) {
            toast(getString(R.string.need_voice))
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val who = TextView(this).apply {
            text = getString(R.string.role_desc, voiceNameOf(vid))
            setTextColor(cDim)
            textSize = 12f
        }
        box.addView(who)
        val et = EditText(this).apply {
            setText(store.rolePrompt(vid))
            hint = getString(R.string.role_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            minLines = 3
            setTextColor(cTxt)
            setHintTextColor(cDim)
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        box.addView(et)
        val sc = ScrollView(this)
        sc.addView(box)
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.role_title))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                store.setRolePrompt(vid, et.text.toString().trim())
                toast(getString(R.string.role_saved))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    /** 「自定义音色 ID…」→ 弹一张卡片：① 填厂商那边的音色 ID ② 给它起个中文名 */
    private fun openCustomIdDialog() {
        val c = Skin.colors(this)
        val d = dp(16)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d, dp(6), d, 0)
        }
        val etId = EditText(this).apply {
            hint = getString(R.string.role_hint_id)
            setSingleLine(true)
            setText(if (voiceIsCustom) etCustomVoice.text.toString().trim() else "")
        }
        val etName = EditText(this).apply {
            hint = getString(R.string.custom_name_hint)
            setSingleLine(true)
            setText("")
        }
        box.addView(etId, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(etName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.voice_custom))
            .setView(box)
            .setPositiveButton(getString(R.string.common_import)) { _, _ ->
                val id = etId.text.toString().trim()
                val nm = etName.text.toString().trim()
                if (id.isEmpty()) {
                    toast(getString(R.string.voice_id_empty))
                    return@setPositiveButton
                }
                val name = nm.ifEmpty { getString(R.string.voice_custom) }
                // 存进getString(R.string.voice_self)列表（这样它能出现在下拉里，也能在「管理音色」里改名/删除）
                if (customVoices.none { it.id == id }) {
                    // 归属厂商：先按 id 前缀认（cosyvoice-* → 百炼），认不出就记"当前厂商"
                    customVoices.add(CustomVoice(id, name, "", System.currentTimeMillis(),
                        VoiceProviders.of(id, customVoices) ?: store.providerId))
                    store.saveCustomVoices(customVoices)
                }
                etCustomVoice.setText(id)
                setVoice(id)
                syncVoiceUi()
                toast(getString(R.string.toast_joined, name))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }   // 跟随主题（原来没接换肤，弹窗是默认深灰）
        dlg.show()
    }

    private fun syncVoiceUi() {
        // P0.5：按所选模型置灰不适用的参数（数据驱动，见 TtsModels.supports）
        // 一律按 View 取（View 本身就有 isEnabled/alpha）—— 之前按具体类型强取，
        // 遇到 id 实际是别的控件（如语言提示栏其实是 TextView）会 ClassCastException 崩在启动路径上
        gatedControls.forEach { (id, param) ->
            val ok = TtsModels.supports(store.providerId, store.lastModel, param)
            findViewById<View>(id)?.apply { isEnabled = ok; alpha = if (ok) 1f else 0.4f }
        }
        // 「+ 建音色」：当前厂商不支持复刻就置灰（避免点了白等）
        val canClone = TtsProviders.byId(store.providerId)?.canClone == true
        findViewById<TextView>(R.id.btnCreateVoice)?.apply {
            isEnabled = canClone
            alpha = if (canClone) 1f else 0.4f
        }

        findViewById<TextView>(R.id.tvParamNote)?.apply {
            // 首次使用引导：一个厂商都没配 → 直接告诉他去哪配（普通用户不用去看文档）
            val noKey = TtsProviders.all.none { p ->
                p.shape == "system" || store.providerKey(p.id).isNotBlank() || store.apiKey.isNotBlank() && p.id == "aliyun-bailian"
            }
            // 不再列"不支持什么"（太长太丑）——只保留"一个厂商都没配"的引导
            text = if (noKey) "还没配置任何厂商：进「设置 → 模型」选厂商、填 Key 就能用。\n本机「系统 TTS」不需要 Key，也可以直接选它先试一句。" else ""
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val all = allVoices()
        val cur = all.firstOrNull { it.id == currentVoiceId }
        tvVoice.text = if (voiceIsCustom) customLabel else (cur?.name ?: all.firstOrNull()?.name ?: "")
        if (voiceIsCustom) {
            etCustomVoice.visibility = View.VISIBLE
            tvVoiceNote.text = getString(R.string.hint_custom_voice)
        } else {
            etCustomVoice.visibility = View.GONE
            tvVoiceNote.text = cur?.note ?: ""
        }
        renderChips()
        syncProviderField()
    }

    /** 主页「TTS 厂商」：**填空 ＋ 下拉**（点一下弹可选清单；也可直接打字筛）——不再摆一堆芯片 */
    private fun setupProviderField() {
        if (!::etProvMain.isInitialized) return
        etProvMain.setAdapter(ContainsAdapter(this, TtsProviders.all.map { TtsProviders.display(it) }))
        etProvMain.threshold = 0
        etProvMain.setOnClickListener { etProvMain.showDropDown() }
        etProvMain.setOnFocusChangeListener { _, has -> if (has) etProvMain.showDropDown() }
        etProvMain.setOnItemClickListener { _, _, _, _ ->
            val id = TtsProviders.idOf(etProvMain.text.toString())
            if (TtsProviders.byId(id) != null) {
                store.providerId = id
                setModelField(store.lastModel)
                syncVoiceUi()
                rebuildFormats()
            }
        }
    }

    /** 把当前厂商写进那个填空栏（false = 不触发筛选） */
    private fun syncProviderField() {
        if (!::etProvMain.isInitialized) return
        val p = TtsProviders.byId(store.providerId)
        etProvMain.setText(if (p == null) "" else TtsProviders.display(p), false)
    }

    private fun selectedVoiceId(): String =
        if (voiceIsCustom) etCustomVoice.text.toString().trim() else currentVoiceId

    /**
     * 音色 id → 显示名。
     * 先查当前音色列表；查不到就**回退到记录里存的名字**（每条记录自带 voiceName）——
     * 否则自建音色一旦被删除/改过名，筛选下拉里就会一片「自定义音色」（实测踩到）。
     */
    private fun voiceNameOf(id: String): String =
        allVoices().firstOrNull { it.id == id }?.name
            ?: takes.firstOrNull { it.voiceId == id }?.voiceName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.voice_custom)

    /**
     * 「自定义音色 ID…」那一行的强调色 —— **固定金，不跟主题**（用户 2026-10-15 钦定）。
     *
     * 取值：**`#9E8368`** —— 就是「润色中…」那行状态字用的 `dim/subText` 色（竹青主题的 subText）。
     * 为什么是它：这一行 v1.7.0 之前一直是 `c.dim`（用户记忆里的"漂亮的金色"其实是这个**暗金／古铜**，
     * 不是配色里的明黄 `#F0C239`，也不是 v0.3 描金的 `#EACD76`——2026-10-15 两轮实测否决）。
     *
     * 为什么固定：原先取主题 `barBg`，绿/蓝主题会露出闷黄 `#F0C239`，观感不对；现任何主题都用这一支。
     * 附带好处：`#9E8368` 在浅底约 3.5:1、深底约 4.3:1，比 `#F0C239`(1.7:1) 可读得多。
     */
    private val customVoiceGold = 0xFF9E8368.toInt()

    private fun emphasizeColor(@Suppress("UNUSED_PARAMETER") c: Skin.Colors): Int = customVoiceGold

    private fun renderChips() {
        llChips.removeAllViews()
        val vName = if (voiceIsCustom) "" else (allVoices().firstOrNull { it.id == currentVoiceId }?.name ?: "")
        val list = instrChips.filter { it.forVoice == null || vName.contains(it.forVoice) }
        for (chip in list) {
            val tv = TextView(this)
            tv.text = chip.label
            tv.setTextColor(cDim)
            tv.textSize = 13f
            tv.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
            tv.setPadding(dp(12), dp(6), dp(12), dp(6))
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener { etInstr.setText(chip.text) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            tv.layoutParams = lp
            llChips.addView(tv)
        }
        // 清空
        val clear = TextView(this)
        clear.text = getString(R.string.clear_ok)
        clear.setTextColor(cDim)
        clear.textSize = 13f
        clear.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
        clear.setPadding(dp(12), dp(6), dp(12), dp(6))
        clear.isClickable = true
        clear.isFocusable = true
        clear.setOnClickListener { etInstr.setText("") }
        llChips.addView(clear)
        Skin.apply(llChips, Skin.colors(this))
    }

    // ---------------------------------------------------------------- 语言选择
    private fun openLangPicker() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(10), dp(20), dp(4))

        val etSearch = EditText(this)
        etSearch.hint = getString(R.string.lang_search_hint)
        etSearch.inputType = InputType.TYPE_CLASS_TEXT
        etSearch.setTextColor(cTxt)
        etSearch.setHintTextColor(cDim)
        etSearch.textSize = 14f
        box.addView(etSearch)

        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        box.addView(listBox)

        // 草稿：点「取消」不应改动已选语言（原来直接改 langSel —— 取消也生效、且和标签不一致）
        val draft = LinkedHashSet(langSel)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                renderLangList(listBox, etSearch, draft)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        renderLangList(listBox, etSearch, draft)

        val sc = ScrollView(this)
        sc.addView(box)

        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.lang_pick_title))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_ok)) { _, _ ->
                langSel.clear(); langSel.addAll(draft)   // 只有“确定”才落盘
                applyLangSel()
            }
            .setNeutralButton(getString(R.string.clear_ok)) { _, _ ->
                langSel.clear()
                applyLangSel()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    private fun renderLangList(listBox: LinearLayout, etSearch: EditText, sel: MutableSet<String>) {
        listBox.removeAllViews()
        val f = etSearch.text.toString().trim().lowercase(Locale.US)
        val items = langPresets.filter {
            f.isEmpty() || it.first.contains(f) || it.second.lowercase(Locale.US).contains(f)
        }
        if (items.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.lang_no_match)
            tv.setTextColor(cDim)
            tv.textSize = 13f
            tv.setPadding(0, dp(10), 0, dp(6))
            listBox.addView(tv)
            return
        }
        for ((code, name) in items) {
            val row = TextView(this)
            val on = code in sel
            row.text = (if (on) "✓ " else "　 ") + name + "（" + code + "）"
            row.setTextColor(if (on) cTxt else cDim)
            row.textSize = 14f
            row.setPadding(dp(4), dp(10), dp(4), dp(10))
            row.isClickable = true
            row.isFocusable = true
            fx(row)
            row.setOnClickListener {
                if (code in sel) sel.remove(code) else sel.add(code)
                renderLangList(listBox, etSearch, sel)
            }
            listBox.addView(row)
        }
    }

    private fun applyLangSel() {
        val names = langSel.map { code -> langPresets.firstOrNull { it.first == code }?.second ?: code }
        etLangHints.text = names.joinToString("、")
    }

    /** 弹窗统一换肤：面板 / 标题 / 正文 / 按钮 */
    private fun skinDialog(dlg: AlertDialog) {
        // 遮罩再显式压浅一次（主题里也设了 0.28，这里兜底）
        dlg.window?.setDimAmount(0.28f)
        val c = Skin.colors(this)
        Skin.apply(dlg.window!!.decorView, c)
        dlg.window?.setBackgroundDrawable(Skin.dialogPanel(this, c))
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) dlg.findViewById<TextView>(titleId)?.setTextColor(c.txt)
        dlg.findViewById<TextView>(android.R.id.message)?.setTextColor(c.dim)
        dlg.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(c.acc)
        dlg.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(c.dim)
        dlg.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(c.dim)
        // 弹窗按钮也装"跟随主题"的水波（系统默认水波吃系统色，不跟主题——用户 2026-10-05 反馈）
        listOf(
            DialogInterface.BUTTON_POSITIVE,
            DialogInterface.BUTTON_NEGATIVE,
            DialogInterface.BUTTON_NEUTRAL
        ).forEach { b -> dlg.getButton(b)?.let { fx(it) } }
    }

    // ---------------------------------------------------------------- 生成
    /** 「生成」入口：若文本框里是「多版本译文」（每行一版）→ **逐个生成**；否则照常生成整段 */
    private fun startGenerateAll() {
        val original = etText.text.toString().trim()
        genQueue.clear()
        if (transVersions.size >= 2 && original == transVersions.joinToString("\n").trim()) {
            genQueue.addAll(transVersions)
            toast(getString(R.string.will_gen, genQueue.size))
            nextGenerate()
            return
        }
        transVersions.clear()
        genTextOverride = null
        generate(null)
    }

    /** 从队列取下一条来生成；队列空则收工 */
    private fun nextGenerate() {
        if (genQueue.isEmpty()) {
            genTextOverride = null
            return
        }
        genTextOverride = genQueue.removeFirst()
        generate(null)
    }

    private fun generate(seedOverride: Int?) {
        if (busy) {
            toast(getString(R.string.toast_busy))
            return
        }
        val key = effectiveKey()
        if (key.isEmpty()) {
            // 主页不再放密钥框 → 引导去设置页（同时展开高级参数，能看到「TTS 厂商」）
            toast(getString(R.string.toast_need_key))
            revealApiKeyField()
            return
        }
        // 「生成＝全部生成」：多版本时逐个跑；文本来自队列（译版在上、原文在下）
        val text = (genTextOverride ?: etText.text.toString()).trim()
        if (text.isEmpty()) {
            toast(getString(R.string.toast_need_text))
            return
        }
        if (text.length > 5000) {
            toast(getString(R.string.toast_too_long, text.length))
            return
        } else if (text.length > 2000) {
            toast(getString(R.string.toast_long, text.length))
        }
        val voiceId = selectedVoiceId()
        if (voiceId.isEmpty()) {
            toast(getString(R.string.toast_need_voice))
            return
        }

        val hotFix: JSONObject? = try {
            optionalJson(etHotfix, "hot_fix")
        } catch (e: BadJsonException) {
            toast(getString(R.string.invalid_json, e.label))
            return
        }
        val extra: JSONObject? = try {
            optionalJson(etExtra, getString(R.string.extra_label))
        } catch (e: BadJsonException) {
            toast(getString(R.string.invalid_json, e.label))
            return
        }

        val seed = (seedOverride ?: etSeed.text.toString().trim().toIntOrNull() ?: (0..65535).random()).coerceIn(0, 65535)
        // 骰子栏**不在生成前写**（否则数字会"飞快"提前跳出来）；等**生成完成**后再回填实际种子 —— 见 onTakeReady()

        val fmt = fmtList[spFormat.selectedItemPosition.coerceIn(0, fmtList.size - 1)]
        val instr = etInstr.text.toString().trim().take(128)
        val model = etModel.text.toString().trim().ifEmpty { "cosyvoice-v3.5-plus" }
        store.lastModel = model
        modelTouched = false
        val hints = langSel.toList().ifEmpty { null }

        val req = SynthRequest(
            apiKey = key,
            workspace = store.workspace.trim().ifEmpty { "ws-9y8n1gp7w6pg23tv" },
            model = model,
            voice = voiceId,
            text = text,
            instruction = instr.ifEmpty { null },
            rate = 0.5 + sbRate.progress * 0.01,
            pitch = 0.5 + sbPitch.progress * 0.01,
            volume = sbVol.progress,
            seed = seed,
            format = fmt.format,
            sampleRate = fmt.sampleRate,
            bitRate = fmt.bitRate,
            languageHints = hints,
            hotFixJson = hotFix,
            extraJson = extra,
            ssml = cbSsml.isChecked
        )

        busy = true
        ivVoiceInd.startBusy()          // 生成期间：指示符持续摆动
        KeepAliveService.start(this, getString(R.string.keepalive_synth))
        btnGenerate.isEnabled = false
        btnGenerate.alpha = 0.55f
        btnCancel.visibility = View.VISIBLE
        svRoot.smoothScrollTo(0, 0)
        tvStatus.text = getString(R.string.st_connecting)

        var lastTick = 0L
        // P1：按厂商形态分派 —— 百炼内部再按模型分 ws/http；OpenAI 系走 OpenAI 兼容客户端
        val prov0 = TtsProviders.byId(store.providerId) ?: TtsProviders.all.first()
        // 用过的密钥进该厂商的历史下拉
        store.addProviderKeyToHistory(prov0.id, store.providerKey(prov0.id).ifBlank { if (prov0.id == "aliyun-bailian") store.apiKey else "" })
        val synthCall = { cb: SynthCallback -> Synth.dispatch(this, store, req, extra, fmt.format, client, cb) }
        synthCall(object : SynthCallback {
            override fun onConnected() {
                ui { tvStatus.text = getString(R.string.st_connected) }
            }

            override fun onStarted() {
                ui { tvStatus.text = getString(R.string.st_synthesizing) }
            }

            override fun onProgress(receivedBytes: Int) {
                val now = System.currentTimeMillis()
                if (now - lastTick < 80) return
                lastTick = now
                val s = if (fmt.format == "wav") {
                    String.format(Locale.US, getString(R.string.st_synth_sec), receivedBytes / (2.0 * fmt.sampleRate))
                } else {
                    String.format(Locale.US, getString(R.string.st_synth_kb), receivedBytes / 1024.0)
                }
                ui { tvStatus.text = s }
            }

            override fun onFinished(audio: ByteArray) {
                try {
                    // 2026-10-17 修：**后台线程只写文件**；内存列表 + 索引的改动一律回主线程做。
                    // （以前 saveTake 在合成回调线程里 `takes.add(0,…)` + `saveTakes(…)`，与主线程的
                    //  `reloadFromStore()`（clear+addAll）/`renderHistory()` 抢同一份列表
                    //  → 新记录可能"没进列表"——用户报的"本次结果不再出现在本地记录第 1 条"）
                    val take = saveTakeFile(req, fmt, audio)
                    ui {
                        addTakeAndPersist(take)
                        onTakeReady(take)
                    }
                } catch (e: Exception) {
                    ui {
                        finishBusy()
                        tvStatus.text = getString(R.string.st_failed, getString(R.string.save_failed))
                        toast(getString(R.string.toast_save_fail, e.message))
                    }
                }
            }

            override fun onError(message: String) {
                val friendly = Err.friendly(message)
                ui {
                    finishBusy()
                    tvStatus.text = getString(R.string.st_failed, friendly)
                    toast(getString(R.string.toast_fail, friendly.take(120)))
                }
            }
        })
    }

    private fun optionalJson(et: EditText, label: String): JSONObject? {
        val t = et.text.toString().trim()
        if (t.isEmpty()) return null
        return try {
            JSONObject(t)
        } catch (e: JSONException) {
            throw BadJsonException(label)
        }
    }

    /** **只写文件 + 造 Take**（可在后台线程跑）。**不要**在这里改内存列表 / 存索引。 */
    private fun saveTakeFile(req: SynthRequest, fmt: Fmt, audio: ByteArray): Take {
        // 扩展名/格式按"真实字节"来（有的厂商返回 mp3，别叫成 .wav）
        val isWav = audio.size > 12 && audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte()
        // 记录格式：WAV 按 WAV 档位 key；本来就是 mp3 档位的保留 mp3_256/mp3_128（否则回填会丢档位）
        val realFormat = when {
            isWav -> fmt.key
            fmt.format == "mp3" -> fmt.key
            else -> "mp3"
        }
        val file = store.newAudioFile(if (isWav) fmt.ext else "mp3")
        file.writeBytes(audio)
        fixWavHeader(file)
        val take = Take(
            id = file.nameWithoutExtension,
            fileName = file.name,
            voiceName = voiceNameOf(req.voice),
            voiceId = req.voice,
            text = req.text,
            instruction = req.instruction ?: "",
            rate = req.rate,
            pitch = req.pitch,
            volume = req.volume,
            seed = req.seed,
            // 按真实字节记格式（realFormat 在上方算好）
            format = realFormat,
            model = req.model,
            durationMs = probeDurationMs(file),
            createdAt = System.currentTimeMillis()
        )
        return take
    }

    /** **主线程**：新记录插到列表头 + 裁到 100 条 + 存索引（与 UI 同线程，避免跨线程改同一份列表） */
    private fun addTakeAndPersist(take: Take) {
        takes.add(0, take)
        diagLastTakeId = take.id
        // 2026-10-17 用户明确定调：**不许设置任何上限**——"用户磁盘爆了，那是用户的事情"。
        // （以前这里 while (takes.size > 100) 会**自动删最旧一条并删音频文件**；已移除。
        //   现在只有用户自己点 ✕ 或「清空」才会删。）
        store.saveTakes(takes)
    }

    companion object {
        /** 诊断用（只读事实）：主页面当前的角色筛选 / 最近一次落库的记录 id */
        @Volatile var diagFilterVoiceId: String? = null
        @Volatile var diagLastTakeId: String? = null
    }

    private fun onTakeReady(take: Take) {
        finishBusy()
        tvStatus.text = getString(R.string.st_done)
        // 骰子：**生成完毕后才**写上实际用的种子（只在该 厂商+模型 支持 seed 时；别的引擎不写，免得误导）
        if (TtsModels.supports(store.providerId, store.lastModel, "seed")) {
            etSeed.setText(take.seed.toString())
        }
        currentTake = take
        cardResult.visibility = View.VISIBLE
        tvResultInfo.text = getString(R.string.result_info, take.voiceName, take.format, fmtNum(take.rate), fmtNum(take.pitch), take.volume, take.seed, fmtDur(take.durationMs))
        renderHistory()
        // 多版本：一条生成完 → 接着生成下一条（最后一条才自动播放，避免互相打断）
        if (genQueue.isNotEmpty()) nextGenerate() else startPlayback(take)
    }

    private fun finishBusy() {
        busy = false
        // 生成结束 = 进入播放阶段 → 不再动，直接"变灰 → 消失"
        ivVoiceInd.stopBusy()
        ivVoiceInd.playOut()
        KeepAliveService.stop(this)
        btnGenerate.isEnabled = true
        btnGenerate.alpha = 1f
        btnCancel.visibility = View.GONE
    }

    // ---------------------------------------------------------------- 播放
    private fun toggleTake(take: Take) {
        val mp = player
        if (mp != null && playingId == take.id) {
            if (mp.isPlaying) mp.pause() else mp.start()
            refreshPlayButtons()
        } else {
            startPlayback(take)
        }
    }

    private fun startPlayback(take: Take) {
        stopPlayback()
        // 申请音频焦点：音乐/播客会先暂停，我们放完它们自动续播
        // ——"新的生成好了自动播放"就能打断正在响的声音，而不是两个声音叠着
        playerFocusToken = AudioFocus.request(this) { stopPlayback(); refreshPlayButtons() }
        val file = store.fileOf(take)
        if (!file.exists()) {
            toast(getString(R.string.file_missing))
            return
        }
        try {
            val mp = MediaPlayer()
            // 声明成"媒体/语音"用途，配合上面的焦点申请
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                if (player === mp) {
                    stopPlayback()
                    refreshPlayButtons()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                if (player === mp) {
                    stopPlayback()
                    refreshPlayButtons()
                }
                toast(getString(R.string.play_err, what, extra))
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            playingId = take.id
            playingProgress = rowRefs.firstOrNull { it.take.id == take.id }?.prog?.apply {
                pivotX = 0f; scaleX = 0f
            }
            mainHandler.removeCallbacks(progTick)
            mainHandler.post(progTick)
        } catch (e: Exception) {
            stopPlayback()
            toast(getString(R.string.play_fail, e.message ?: ""))
        }
        refreshPlayButtons()
    }

    private fun stopPlayback() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // ignore
        }
        try {
            player?.release()
        } catch (e: Exception) {
            // ignore
        }
        player = null
        playingId = null
        playingProgress = null
        mainHandler.removeCallbacks(progTick)
        if (playerFocusToken != 0) {
            AudioFocus.abandon(this, playerFocusToken)  // 交还焦点 → 刚才暂停的音乐自动续播
            playerFocusToken = 0
        }
    }

    private fun refreshPlayButtons() {
        val cur = currentTake
        // 判据**必须统一**：顶卡与记录行都看「同一个 player 真的在播」。
        // 旧代码记录行只判 playingId —— **暂停后 playingId 还在**，于是记录行仍显示「暂停」、
        // 顶卡已显示「播放」，两个按钮状态对不上（用户 2026-10-10 报的 bug）。
        val playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
        btnPlay.text = if (cur != null && playingId == cur.id && playing) getString(R.string.st_pause) else getString(R.string.rec_play)
        for (r in rowRefs) {
            val on = playing && playingId == r.take.id
            r.playBtn.text = if (on) getString(R.string.st_pause) else getString(R.string.rec_play)
            r.prog.visibility = if (on) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------- 音色试听（长按删除卡片）
    /**
     * 试听某个音色：用**该音色所属厂商**的 Key/模型合成一句很短的样本，缓存到 cacheDir 后播放。
     *
     * 为什么要“所属厂商”：音色 ID 跨厂商不通用（cosyvoice-* 属百炼；自建音色记了 providerId）。
     * 百炼的 cosyvoice-* 还必须用 cosyvoice 模型（当前若选了 qwen-tts 会失败）→ 这里强制 v3.5-plus。
     */
    private fun previewVoice(voiceId: String, onBusy: (Boolean) -> Unit) {
        // 该音色导入过自定义试听模板 → 直接播本地文件，不发 TTS 请求（也不依赖 API Key）
        val custom = store.previewTemplate(voiceId)
        if (custom != null) {
            stopPlayback()
            refreshPlayButtons()
            stopVoicePreview()
            onBusy(true)
            playPreviewFile(custom, onBusy)
            return
        }

        val pid = VoiceProviders.of(voiceId, customVoices) ?: store.providerId
        val prov = TtsProviders.byId(pid)
        val key = store.providerKey(pid).ifBlank { if (pid == "aliyun-bailian") store.apiKey else "" }
        if (prov?.shape != "system" && key.isBlank()) {
            toast(getString(R.string.toast_need_key))
            return
        }
        val currentModel = TtsModels.idOf(etModel.text.toString().trim())
        val model = when {
            pid == VoiceProviders.BAILIAN && voiceId.startsWith("cosyvoice-") &&
                currentModel.startsWith("cosyvoice-") -> currentModel
            pid == VoiceProviders.BAILIAN && voiceId.startsWith("cosyvoice-") -> "cosyvoice-v3.5-plus"
            pid == store.providerId && currentModel.isNotBlank() -> currentModel
            !prov?.models.isNullOrEmpty() -> prov!!.models.first()
            else -> store.lastModel
        }
        if (model.isBlank() && prov?.shape != "system") {
            toast(getString(R.string.voice_preview_fail, getString(R.string.label_model)))
            return
        }

        stopPlayback()          // 试听前先停记录播放，避免两个声音叠在一起
        refreshPlayButtons()
        stopVoicePreview()
        val token = previewToken
        onBusy(true)

        val req = SynthRequest(
            apiKey = key, workspace = store.workspace.trim().ifEmpty { "ws-9y8n1gp7w6pg23tv" },
            model = model, voice = voiceId,
            text = getString(R.string.voice_preview_text), instruction = null,
            rate = 1.0, pitch = 1.0, volume = 50, seed = 0,
            format = "wav", sampleRate = 24000, bitRate = null, languageHints = null,
            hotFixJson = null, extraJson = null, ssml = false
        )
        previewCall = Synth.dispatch(this, store, req, null, "wav", client, object : SynthCallback {
            override fun onConnected() {}
            override fun onStarted() {}
            override fun onProgress(receivedBytes: Int) {}

            override fun onFinished(audio: ByteArray) {
                if (token != previewToken) return
                if (audio.isEmpty()) {
                    ui { if (token == previewToken) { onBusy(false); toast(getString(R.string.voice_preview_fail, "empty audio")) } }
                    return
                }
                val isWav = audio.size > 12 && audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte()
                val f = File(cacheDir, "voice_preview_" + voiceId.hashCode().toUInt().toString(16) + if (isWav) ".wav" else ".mp3")
                val written = runCatching { f.writeBytes(audio) }.isSuccess
                ui {
                    if (token != previewToken) return@ui
                    if (!written) {
                        onBusy(false)
                        toast(getString(R.string.voice_preview_fail, getString(R.string.save_failed)))
                        return@ui
                    }
                    playPreviewFile(f, onBusy)
                }
            }

            override fun onError(message: String) {
                ui {
                    if (token == previewToken) {
                        onBusy(false)
                        toast(getString(R.string.voice_preview_fail, message))
                    }
                }
            }
        }, pid)
    }

    private fun playPreviewFile(file: File, onBusy: (Boolean) -> Unit) {
        try {
            previewFocusToken = AudioFocus.request(this) { stopVoicePreview() }
            val mp = MediaPlayer()
            previewPlayer = mp
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { stopVoicePreview() }
            mp.setOnErrorListener { _, what, extra ->
                stopVoicePreview()
                onBusy(false)
                toast(getString(R.string.play_err, what, extra))
                true
            }
            mp.prepare()
            mp.start()
            onBusy(false)
        } catch (e: Exception) {
            stopVoicePreview()
            onBusy(false)
            toast(getString(R.string.play_fail, e.message ?: ""))
        }
    }

    private fun stopVoicePreview() {
        previewToken++                    // 让还在合成的回调自然失效
        runCatching { previewCall?.cancel() }
        previewCall = null
        val mp = previewPlayer
        previewPlayer = null
        if (mp != null) {
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        if (previewFocusToken != 0) {
            AudioFocus.abandon(this, previewFocusToken)
            previewFocusToken = 0
        }
    }

    // ---------------------------------------------------------------- 音色试听模板（本地音频）
    private fun startVoiceTemplatePicker(voiceId: String) {
        pendingVoiceTemplateId = voiceId
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            startActivityForResult(i, REQ_VOICE_TEMPLATE)
        } catch (e: Exception) {
            toast(getString(R.string.toast_no_picker))
        }
    }

    private fun voiceTemplateExt(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
            }
        }.getOrNull().orEmpty()
        val byName = name.substringAfterLast('.', "").lowercase()
        if (byName in setOf("wav", "mp3", "m4a", "aac", "ogg", "opus", "flac")) return byName
        val mime = contentResolver.getType(uri).orEmpty().lowercase()
        return when {
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "wav" in mime -> "wav"
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "m4a"
            "opus" in mime -> "opus"
            "ogg" in mime -> "ogg"
            "flac" in mime -> "flac"
            else -> "wav"
        }
    }

    private fun importVoiceTemplate(voiceId: String, uri: Uri) {
        val ext = voiceTemplateExt(uri)
        toast(getString(R.string.preview_template_importing))
        Thread {
            val ok = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    store.savePreviewTemplate(voiceId, input, ext) != null
                } ?: false
            }.getOrDefault(false)
            ui {
                toast(
                    if (ok) getString(R.string.preview_template_imported)
                    else getString(R.string.preview_template_fail, getString(R.string.common_fail))
                )
            }
        }.start()
    }

    private fun confirmClearVoiceTemplate(voiceId: String) {
        if (store.previewTemplate(voiceId) == null) {
            toast(getString(R.string.preview_template_no_custom))
            return
        }
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.preview_template_clear_title))
            .setMessage(getString(R.string.preview_template_clear_msg))
            .setPositiveButton(getString(R.string.preview_template_clear_ok)) { _, _ ->
                store.clearPreviewTemplate(voiceId)
                toast(getString(R.string.preview_template_cleared))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        d.setOnShowListener { skinDialog(d) }
        d.show()
    }

    // ---------------------------------------------------------------- 分享 / 导出
    /** 分享前先把音频复制成"好名字"（对方收到的就是「音色·正文.wav」，而不是 vt_1759…_abc.wav） */
    private fun shareableOf(take: Take): File {
        val src = store.fileOf(take)
        return try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val dst = File(dir, niceFileName(take))
            if (!dst.exists() || dst.length() != src.length()) src.copyTo(dst, overwrite = true)
            if (dst.exists()) dst else src
        } catch (e: Exception) {
            src
        }
    }

    private fun shareTake(take: Take) {
        val file = store.fileOf(take)
        if (!file.exists()) {
            toast(getString(R.string.file_missing))
            return
        }
        val out = shareableOf(take)
        try {
            val uri: Uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", out)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(out.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, out.name)
                // 关键：有些接收方/系统只认 ClipData 里的 uri（只给 EXTRA_STREAM 会"点了没反应"）
                clipData = android.content.ClipData.newUri(contentResolver, "audio", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_audio)))
        } catch (e: Exception) {
            toast(getString(R.string.share_fail, e.message ?: ""))
        }
    }

    /** 待下载的文件（用户选好名字/目录后再写入） */
    private var pendingSaveFile: java.io.File? = null
    private val REQ_SAVE = 9001

    /** 默认文件名 = 音色 + 正文一小段（去掉文件名非法字符、限长，空则回退） */

    /** 下载：自己命名 + 自己选目录（SAF）；默认名已按「音色·正文片段」预填好，可直接用 */
    private fun exportTake(take: Take) {
        val file = store.fileOf(take)
        if (!file.exists()) {
            toast(getString(R.string.file_missing))
            return
        }
        pendingSaveFile = file
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeOf(file.name)
            putExtra(Intent.EXTRA_TITLE, niceFileName(take))   // 建议名（认它的文件管理器会自动填上）
        }
        try {
            startActivityForResult(i, REQ_SAVE)
        } catch (e: Exception) {
            toast(getString(R.string.save_dlg_fail, e.message ?: ""))
        }
    }

    private fun mimeOf(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "opus", "ogg" -> "audio/ogg"
            else -> "audio/*"
        }

    // ---------------------------------------------------------------- 历史
    private fun renderHistory() {
        // 用户 2026-10-13：**保住"展开到第几屏"与当前滚动位置**。
        // 旧代码每次 renderHistory 都把 historyShown 打回 25 条 → 用户展开到几百条、往下滑很远时，
        // 只要 renderHistory 被触发一次（切后台回来就会），内容缩回 25 条 → 滚动位置被夹回上方
        // （症状："不断下滑看记录，屏幕莫名跳回顶部"）。
        val keepY = runCatching { svRoot.scrollY }.getOrDefault(0)
        llHistory.removeAllViews()
        rowRefs.clear()
        val list = filterVoiceId?.let { id -> takes.filter { it.voiceId == id } } ?: takes
        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(if (filterVoiceId == null) R.string.hist_empty else R.string.hist_empty_voice)
            tv.setTextColor(cDim)
            tv.textSize = 13f
            tv.setPadding(0, dp(8), 0, 0)
            llHistory.addView(tv)
            return
        }
        // 首屏只建最近 25 条；之后每次「显示更多」**再追加 100 条**（增量，不重建已建行）——
        // 记录成百上千条时，避免一次建出上千个 View 造成卡顿
        historyShown = minOf(maxOf(historyShown, HISTORY_PREVIEW), list.size)
        // 2026-10-17：冷启动**先只建 8 张**让首帧尽快出来，**首帧之后自动补齐到 25**（不用她多点一下）。
        //   依据：实测"建 25 张卡"冷启动要 334ms，但第二遍只要 9ms → 贵在类加载/JIT，不在逻辑。
        val first = if (coldHistRender) minOf(COLD_FIRST_ROWS, historyShown) else historyShown
        appendHistoryRange(list, 0, first)
        mark("记录卡：首屏 $first 张（快）")
        if (first < historyShown) {
            llHistory.post {
                if (llHistory.childCount != first) return@post   // 期间又重建过 → 放弃这次补齐
                appendHistoryRange(list, first, historyShown)
                if (list.size > historyShown) llHistory.addView(buildHistoryMoreRow(list))
                Skin.apply(llHistory, Skin.colors(this))
                mark("记录卡：首帧后补齐到 $historyShown 张")
            }
        } else if (list.size > historyShown) llHistory.addView(buildHistoryMoreRow(list))
        Skin.apply(llHistory, Skin.colors(this))
        mark("记录卡：换肤完成")
        coldHistRender = false
        // 重建后把滚动位置放回去（post：等新布局完成再滚，ScrollView 自己会夹到合法范围）
        if (keepY > 0) runCatching { svRoot.post { svRoot.scrollTo(0, keepY) } }
    }

    /** 追加 [from, until) 区间的记录行（增量） */
    private fun appendHistoryRange(list: List<Take>, from: Int, until: Int) {
        for (i in from until until) llHistory.addView(buildRow(list[i]))
    }

    /** 底部「显示更多 / 收起」行：点一次**再展开 100 条**；到底再点＝收起 */
    private fun buildHistoryMoreRow(list: List<Take>): TextView {
        val more = TextView(this)
        fun refreshText() {
            more.text = if (historyShown >= list.size) getString(R.string.hist_collapse, HISTORY_PREVIEW)
                        else getString(R.string.hist_show_more, minOf(HISTORY_PAGE, list.size - historyShown), list.size)
        }
        refreshText()
        more.setTextColor(cDim)
        more.textSize = 13f
        more.gravity = Gravity.CENTER
        more.setPadding(dp(12), dp(15), dp(12), dp(9))
        more.isClickable = true
        more.isFocusable = true
        fx(more, llHistory)
        more.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        more.setOnClickListener {
            if (historyShown >= list.size) {
                historyShown = HISTORY_PREVIEW       // 「收起」→ 显式回到首屏 25 条（renderHistory 现在会保住展开数）
                renderHistory()
                return@setOnClickListener
            }
            val idx = llHistory.indexOfChild(more)
            val next = minOf(historyShown + HISTORY_PAGE, list.size)
            var at = idx
            for (i in historyShown until next) { llHistory.addView(buildRow(list[i]), at); at++ }
            historyShown = next
            refreshText()
            Skin.apply(llHistory, Skin.colors(this))   // 给新行上色
        }
        return more
    }

    /** 记录卡字号缩放：只作用在**标题 / 信息 / 详情**上（小按钮一律不动，用户 2026-10-05） */
    private fun recScale(base: Float): Float = base * store.recordFontPct / 100f

    private fun buildRow(take: Take): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        val rowColors = Skin.colors(this)          // 每行只取一次（原来一行要取 3~4 次）
        row.background = ContextCompat.getDrawable(this, R.drawable.bg_row)
        Skin.setRole(row, "row")
        // 上/右不再留内边距 → ✕ 的判定方块才能真正贴住卡片那两条边
        row.setPadding(dp(12), 0, 0, dp(10))
        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(8)
        row.layoutParams = rlp

        // 头部：文本（默认 2 行 ≈1.8 行，点它就地展开/收起） ＋ 右上角删除 ✕
        // ✕：字形 9.5sp（≈2.6dp）；判定 42dp 正方形，上/右两边贴卡片内沿
        val head = android.widget.FrameLayout(this)

        val title = TextView(this)
        title.text = take.text
        title.setTextColor(cTxt)
        title.textSize = recScale(14f)
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setPadding(0, dp(10), dp(20), 0)     // 顶部 10dp 移到标题上；右侧只让 20dp
        head.addView(title, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val x = TextView(this)
        x.text = "✕"
        x.textSize = 9f                             // 字形面积再 −10%
        x.setTextColor(Skin.Colors.mix(cDim, rowColors.bg, 0.15f))   // 比次文字再浅 15%
        x.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        val xInset = (3.5f * resources.displayMetrics.density).toInt()   // 字形内距 3.5dp
        x.setPadding(0, xInset, xInset, 0)
        x.layoutParams = android.widget.FrameLayout.LayoutParams(
            dp(48), android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.END   // 高度不再写死 48dp → 卡片高度随正文走
        )
        x.isClickable = true
        x.isFocusable = true
        x.setOnClickListener { confirmDelete(take) }
        head.addView(x)
        row.addView(head)

        // ✕ 判定范围：宽固定 48dp；**高度 = 正文高度**（不再抬到 48dp——否则 1 行消息的卡片也会被撑出空行）
        row.post {
            val h = title.height
            if (h > 0) {
                (x.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
                    lp.height = h
                    x.layoutParams = lp
                }
            }
        }

        // 常驻信息：时间 · 音色 · 格式 · 字数 · 时长（🎲 与语速/音调/音量/模型都收进「属性」）
        val meta = TextView(this)
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(take.createdAt))
        meta.text = getString(R.string.rec_meta, time, take.voiceName, take.format, fmtDur(take.durationMs), charCount(take.text))        // 时长在左、字数最右
        meta.setTextColor(cDim)
        meta.textSize = recScale(12f)
        meta.setPadding(0, dp(3), dp(12), dp(6))
        row.addView(meta)

        // 「属性」收起时隐藏：语速 / 音调 / 音量 / 模型 / 种子
        val detail = TextView(this)
        val modelShown = if (take.model.isBlank()) store.lastModel else take.model
        detail.text = getString(R.string.rec_detail, fmtNum(take.rate), fmtNum(take.pitch), take.volume, modelShown, take.seed)
        detail.setTextColor(cTxt)                  // 与正文一致（不再发灰"隐形"）
        detail.textSize = recScale(13f)
        detail.setPadding(0, 0, dp(12), dp(2))
        detail.visibility = View.GONE
        row.addView(detail)

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.setPadding(0, 0, dp(12), 0)
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        hs.addView(btnRow)
        row.addView(hs)

        val bPlay = smallBtn(getString(R.string.rec_play))
        val bFill = smallBtn(getString(R.string.rec_fill))
        val bDown = smallBtn(getString(R.string.rec_download))
        // 收藏星：手绘矢量（不再用 ☆/★ 字形——字形太小、且会被系统字体染成杂色）
        val bStar = LinearLayout(this)
        bStar.orientation = LinearLayout.HORIZONTAL
        bStar.gravity = android.view.Gravity.CENTER_VERTICAL
        // 与同排按钮用同一套"玻璃底"（原来这里还是旧的 bg_btn，会被换肤刷成更深的一档，显得突兀）
        bStar.background = glassBg()
        bStar.tag = "bg:keep"
        bStar.setPadding(dp(12), dp(7), dp(12), dp(7))
        bStar.isClickable = true
        bStar.isFocusable = true
        bStar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) }
        val starIcon = StarView(this)          // 自绘：绕开 tint/ColorFilter 不生效的问题
        starIcon.layoutParams = LinearLayout.LayoutParams(dp(13), dp(13))   // 同比缩到 ~90%
        bStar.addView(starIcon)
        val starLabel = TextView(this)
        starLabel.textSize = 12f                       // 与旁边按钮同字号（同比 -10%）
        starLabel.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(2) }              // 星与文字贴近 → 缩短整个按钮长度
        bStar.addView(starLabel)
        val bShare = smallBtn(getString(R.string.rec_share))

        bPlay.setOnClickListener { toggleTake(take) }
        bFill.setOnClickListener {
            fillFrom(take)
            toast(getString(R.string.toast_backfill_done))
        }
        // 「属性」撤掉：正文 / 常驻信息 / 参数行 合成一个功能 —— 点哪边都展开（或收起）全文+参数
        fun toggleAll() {
            val ex = title.maxLines != Int.MAX_VALUE
            title.maxLines = if (ex) Int.MAX_VALUE else 2
            title.ellipsize = if (ex) null else android.text.TextUtils.TruncateAt.END
            if (ex) {
                detail.visibility = View.VISIBLE
                detail.alpha = 0f
                detail.animate().alpha(1f).setDuration(150).start()
            } else {
                detail.animate().alpha(0f).setDuration(120).withEndAction {
                    detail.visibility = View.GONE
                }.start()
            }
        }
        title.setOnClickListener { toggleAll() }
        meta.setOnClickListener { toggleAll() }
        detail.setOnClickListener { toggleAll() }
        // 整张卡片都能点开/收起（原来点文字之间的空隙没反应）
        row.setOnClickListener { toggleAll() }

        // 这一格改成「下载」（与"本次结果"里那颗同功能：弹命名+选目录）
        bDown.setOnClickListener { exportTake(take) }
        val refreshStar = {
            val fav = take.id in store.favTakes
            // 文字跟旁边按钮**完全一致**（Skin 会按角色重着色）；只有星星描边略微收浅一点，别又厚又深
            val sc = Skin.colors(this)
            starIcon.colorSolid = Skin.Colors.mix(sc.txt, sc.card, 0.18f)
            starIcon.filled = fav         // 已收藏＝亮黄渐变（StarView 里直接画）
            // 星与文字都用与旁边按钮相同的颜色（不做"收藏专属色"）
            starLabel.text = getString(R.string.rec_star)                     // 恒定文字，只有星星上色/变实心
            starLabel.setTextColor(cTxt)                // 与「播放/回填/下载」同色（Skin 按角色统一重着色）
        }
        refreshStar()
        bStar.setOnClickListener {
            val set = store.favTakes
            val nowFav = if (set.contains(take.id)) {
                set.remove(take.id); false
            } else {
                set.add(take.id); true
            }
            store.favTakes = set
            refreshStar()
            starIcon.animate().scaleX(1.28f).scaleY(1.28f).setDuration(90).withEndAction {
                starIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            toast(if (nowFav) getString(R.string.toast_faved) else getString(R.string.toast_unfaved))
        }
        bShare.setOnClickListener { shareTake(take) }

        btnRow.addView(bPlay)
        btnRow.addView(bFill)
        btnRow.addView(bDown)
        btnRow.addView(bStar)
        btnRow.addView(bShare)

        // 播放进度：一条 2dp 细线（播放时才出现）
        val prog = View(this)
        prog.background = Skin.shapeDp(this, rowColors.acc, null, 0f)   // 圆角 0 → 不撞 Skin 的角色
        prog.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
        ).apply { topMargin = dp(8) }
        prog.visibility = View.GONE
        row.addView(prog)

        // 点按反馈：这些小按钮都在卡片里 → 带"留痕"
        listOf(bPlay, bFill, bDown, bStar, bShare).forEach { fx(it, row) }
        // 记录卡的删除 ✕ 刻意**不做**点按特效：字形小、判定大是它的设计取向，加反馈反而破坏极简

        rowRefs.add(RowRef(take, bPlay, prog))
        return row
    }

    private fun fillFrom(take: Take) {
        val all = allVoices()
        if (all.any { it.id == take.voiceId }) {
            setVoice(take.voiceId)
        } else {
            // 音色已从列表删掉了：直接把它的 ID 填进「自定义音色」并**设为当前选择** ——
            // 这样"回填后立刻生成"能用同一条音色；而且**不会**把它加回音色管理列表（记录只是历史事实）。
            // （旧实现漏了 voiceIsCustom=true，生成时用的还是上一个音色；而且还多弹了一个卡片，已去掉）
            voiceIsCustom = true
            etCustomVoice.setText(take.voiceId)
            syncVoiceUi()
        }
        transVersions.clear()          // 回填历史 = 退出多译版模式（否则会被"删字"逻辑拆行）
        etText.setText(take.text)
        etInstr.setText(take.instruction)
        etSeed.setText(take.seed.toString())
        sbRate.progress = Math.round((take.rate - 0.5) * 100).toInt().coerceIn(0, 150)
        sbPitch.progress = Math.round((take.pitch - 0.5) * 100).toInt().coerceIn(0, 150)
        sbVol.progress = take.volume.coerceIn(0, 100)
        val fi = fmtList.indexOfFirst { it.key == take.format }
        if (fi >= 0) spFormat.setSelection(fi)
        onParamChanged()   // 程序化设置 progress 不触发 ratchet 的标签刷新 → 手动刷一次
    }

    private fun confirmDelete(take: Take) {
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.rec_del_title))
            .setMessage(take.text.take(60))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                if (playingId == take.id) stopPlayback()
                takes.remove(take)
                store.deleteFile(take)
                store.saveTakes(takes)
                if (currentTake?.id == take.id) {
                    currentTake = null
                    cardResult.visibility = View.GONE
                }
                renderHistory()
                refreshPlayButtons()
                toast(getString(R.string.common_deleted))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    private fun confirmClearHistory() {
        // 2026-10-17 用户：本地记录选了某个音色后点「清空」→ **只清这个音色**；
        // 只有筛选是「全部音色」时才清全部。
        val target = filterVoiceId?.let { id -> takes.filter { it.voiceId == id } } ?: takes
        if (target.isEmpty()) {
            toast(getString(R.string.toast_no_records))
            return
        }
        val voiceName = filterVoiceId?.let { voiceNameOf(it) }
        val dlg = AlertDialog.Builder(this)
            .setTitle(
                if (voiceName != null) getString(R.string.clear_voice_title, voiceName)
                else getString(R.string.clear_title)
            )
            .setMessage(
                if (voiceName != null) getString(R.string.clear_voice_msg, target.size)
                else getString(R.string.clear_msg, target.size)
            )
            .setPositiveButton(getString(R.string.clear_ok)) { _, _ ->
                stopPlayback()
                val ids = target.map { it.id }.toSet()
                target.forEach { store.deleteFile(it) }
                takes.removeAll { it.id in ids }
                // 按音色清空 → 保留当前筛选（让用户看到「该音色暂无记录」）；
                // 全部清空 → 筛选本来就是空的，无需改。
                store.saveTakes(takes)
                if (currentTake?.id?.let { it in ids } == true) {
                    currentTake = null
                    cardResult.visibility = View.GONE
                }
                renderHistory()
                refreshPlayButtons()
                toast(getString(R.string.toast_cleared))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    // ---------------------------------------------------------------- 音色选择器（自绘下拉）
    /** 音色下拉：内置 + 自建；当前音色行右端有心电指示符；底部有「管理音色…」 */
    private fun showVoicePickerPopup() {
        val c = Skin.colors(this)
        val all = allVoices()
        var pop: PopupWindow? = null

        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL

        fun addRow(label: String, id: String?, tint: Int, onClick: () -> Unit) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), dp(11), dp(16), dp(11))
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(tint)
            tv.textSize = 15f
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.isClickable = true
            row.isFocusable = true
            fx(row)
            row.setOnClickListener { onClick() }
            // 长按 = 快速删除（自建真删 / 内置隐藏，二者都能在「管理音色」里找回）
            row.setOnLongClickListener {
                if (id == null) return@setOnLongClickListener false
                val v = allVoices().firstOrNull { it.id == id }
                    ?: return@setOnLongClickListener false
                val cv = customVoices.firstOrNull { it.id == id }
                val c0 = Skin.colors(this)
                fun doDelete() {
                    if (cv != null) {
                        customVoices.removeAll { it.id == cv.id }
                        store.saveCustomVoices(customVoices)
                        store.clearPreviewTemplate(cv.id)
                        if (voiceIsCustom && currentVoiceId == cv.id) {
                            voiceIsCustom = false
                            allVoices().firstOrNull()?.let { setVoice(it.id) }
                        }
                    } else {
                        val h = store.hiddenVoices
                        h.add(v.id)
                        store.hiddenVoices = h
                        if (currentVoiceId == v.id) {
                            allVoices().firstOrNull { it.id != v.id }?.let { setVoice(it.id) }
                        }
                    }
                    pop?.dismiss()
                    syncVoiceUi()
                    toast(getString(R.string.common_deleted))
                }

                var dlg: AlertDialog? = null
                var listenBtn: TextView? = null
                fun action(label: String, color: Int, click: () -> Unit): TextView {
                    val tv = TextView(this)
                    tv.text = label
                    tv.textSize = 14f
                    tv.setTextColor(color)
                    tv.setPadding(dp(10), dp(10), dp(10), dp(10))
                    tv.isClickable = true
                    tv.isFocusable = true
                    fx(tv)
                    tv.setOnClickListener { click() }
                    return tv
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(8), dp(6))
                }
                val tplBtn = action(
                    getString(
                        if (store.previewTemplate(v.id) != null) R.string.preview_template_set
                        else R.string.preview_template
                    ),
                    c0.acc
                ) {
                    startVoiceTemplatePicker(v.id)
                }.apply {
                    isLongClickable = true
                    setOnLongClickListener { confirmClearVoiceTemplate(v.id); true }
                }
                listenBtn = action(getString(R.string.voice_preview), c0.acc) {
                    previewVoice(v.id) { busy ->
                        // 不改文字，避免按钮变宽后行内重新排版
                        listenBtn?.isEnabled = !busy
                        listenBtn?.alpha = if (busy) 0.45f else 1f
                    }
                }
                val cancelBtn = action(getString(R.string.common_cancel), c0.dim) { dlg?.dismiss() }
                val delBtn = action(getString(R.string.common_delete), c0.acc) {
                    doDelete(); dlg?.dismiss()
                }
                val gap = android.widget.Space(this)
                val spacer = android.widget.Space(this)
                row.addView(tplBtn)
                row.addView(gap, LinearLayout.LayoutParams(0, 1))
                row.addView(listenBtn)
                row.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
                row.addView(cancelBtn)
                row.addView(delBtn)

                dlg = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.vc_del_title))
                    .setMessage(getString(R.string.vc_del_msg, v.name))   // 中性文案：不提"内置/隐藏/恢复"这类内部信息
                    .setView(row)
                    .create()
                dlg?.setOnShowListener {
                    val d = dlg ?: return@setOnShowListener
                    skinDialog(d)
                    // Skin 会按角色重刷文字色，这里再显式上一次
                    tplBtn.setTextColor(c0.acc)
                    listenBtn?.setTextColor(c0.acc)
                    cancelBtn.setTextColor(c0.dim)
                    delBtn.setTextColor(c0.acc)
                    listenBtn?.let { alignDeleteButtonsToMessage(d, row, gap, tplBtn, it, v.name) }
                }
                dlg?.setOnDismissListener { stopVoicePreview() }
                dlg?.show()
                true
            }
            listBox.addView(row)
        }

        fun switchTo(id: String) {
            setVoice(id)            // 走统一入口：内部会处理"换音色 → 清空骰子"，别直接改 currentVoiceId（那会绕过）
            pop?.dismiss()          // 指示符已搬到首页 → 这里立即收起（不再等 790ms）
        }

        if (all.isEmpty()) {
            val hint = TextView(this)
            hint.text = getString(R.string.vc_empty)
            hint.setTextColor(c.hint)
            hint.textSize = 12.5f
            hint.setPadding(dp(16), dp(10), dp(16), dp(10))
            listBox.addView(hint)
        }
        // 当前音色用主题主色标出（指示符搬去首页后，这里留个静态标记）
        for (v in all) addRow(v.name, v.id, if (!voiceIsCustom && v.id == currentVoiceId) c.acc else c.txt) { switchTo(v.id) }
        addRow(customLabel, null, emphasizeColor(c)) {
            openCustomIdDialog()   // 弹卡片：填 ID + 起名字（原来是回主页显示输入框）
            syncVoiceUi()
            pop?.dismiss()
        }
        addRow(getString(R.string.vc_manage), null, c.acc) {
            pop?.dismiss()
            manageVoices()
        }

        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.addView(listBox)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Skin.shapeDp(this, c.bg, c.line, 12f)
        panel.setPadding(dp(6), dp(6), dp(6), dp(6))
        panel.addView(sc)

        val w = minOf(dp(310), resources.displayMetrics.widthPixels - dp(32))
        // 高度**贴合内容**：原来按"每行 48dp"估算，行矮时下方会留一大块空白
        listBox.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = minOf(listBox.measuredHeight + dp(12),
            (resources.displayMetrics.heightPixels * 0.6f).toInt())
        // 获焦 = 返回键能关；INPUT_METHOD_NOT_NEEDED 防它把键盘带出来
        pop = PopupWindow(panel, w, h, true)
        pop.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        pop.isOutsideTouchable = true
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(8).toFloat()
        pop.showAsDropDown(llVoice, 0, dp(2))
    }

    /**
     * 删除弹窗的自绘按钮行：模板 / 试听 / 取消 / 删除。
     * 试听的文字起点 = 正文「「音色名」」之后（即「将从」的 x）再向左 20dp。
     * 用 Space 的宽度把试听推过去；剩余空间由 weight=1 的 spacer 吃掉，取消/删除始终靠右。
     */
    private fun alignDeleteButtonsToMessage(
        dlg: AlertDialog, row: LinearLayout, gap: android.widget.Space,
        tplBtn: TextView, listenBtn: TextView, name: String
    ) {
        val msg = dlg.findViewById<TextView>(android.R.id.message) ?: return
        row.post {
            runCatching {
                if (msg.width <= 0 || row.width <= 0) return@runCatching
                val avail = msg.width - msg.paddingLeft - msg.paddingRight
                val prefixW = msg.paint.measureText("「$name」")
                if (prefixW <= 0f || prefixW > avail) return@runCatching

                val msgLoc = IntArray(2); msg.getLocationOnScreen(msgLoc)
                val listenLoc = IntArray(2); listenBtn.getLocationOnScreen(listenLoc)
                val target = msgLoc[0] + msg.paddingLeft + prefixW - dp(20)
                val current = listenLoc[0] + listenBtn.paddingLeft
                val delta = (target - current).toInt()
                if (delta == 0) return@runCatching

                val lp = gap.layoutParams as LinearLayout.LayoutParams
                val maxGap = row.width - row.paddingLeft - row.paddingRight -
                    tplBtn.width - listenBtn.width - dp(120)   // 给取消/删除留位置
                lp.width = (lp.width + delta).coerceIn(0, maxGap.coerceAtLeast(0))
                gap.layoutParams = lp
            }
        }
    }

    // ---------------------------------------------------------------- 管理音色（自建真删 / 内置隐藏）
    private fun manageVoices() {
        val all = allVoices()
        if (all.isEmpty() && hiddenVoices.isEmpty()) {
            toast(getString(R.string.mgr_empty))
            return
        }
        val c = Skin.colors(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        // 用户 2026-10-10：名字 / 标题 / 删除按钮一律**向卡片中心挪 2dp**（20 → 22dp）
        box.setPadding(dp(22), dp(4), dp(22), dp(4))
        var dlg: AlertDialog? = null

        fun section(text: String) {
            val tv = TextView(this)
            tv.text = text
            tv.setTextColor(c.dim)
            tv.textSize = 12f
            tv.setPadding(0, dp(14), 0, dp(2))
            box.addView(tv)
        }

        fun row(
            name: String, note: String, action: String, actionColor: Int,
            rename: (() -> Unit)? = null, onTap: () -> Unit
        ) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(0, dp(10), 0, dp(10))
            val tv = TextView(this)
            tv.text = name + if (note.isEmpty()) "" else "    " + note
            tv.setTextColor(c.txt)
            tv.textSize = 15f
            r.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val act = TextView(this)
            act.text = action
            act.setTextColor(actionColor)
            act.textSize = 14f
            act.setPadding(dp(14), dp(8), dp(4), dp(8))
            act.isClickable = true
            act.isFocusable = true
            fx(act)
            act.setOnClickListener {
                dlg?.dismiss()
                onTap()
            }
            if (rename != null) {
                val rn = TextView(this)
                rn.text = getString(R.string.common_rename)
                rn.setTextColor(c.acc)
                rn.textSize = 14f
                rn.setPadding(dp(12), dp(8), dp(4), dp(8))
                rn.isClickable = true
                rn.isFocusable = true
                fx(rn)
                rn.setOnClickListener {
                    dlg?.dismiss()
                    rename()
                }
                r.addView(rn)
            }
            r.addView(act)
            box.addView(r)
        }

        fun renameVoice(cv: CustomVoice) {
            val et = EditText(this)
            et.setText(cv.name)
            et.setSelection(cv.name.length)
            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.mgr_rename_title))
                .setView(et)
                .setPositiveButton(getString(R.string.common_save)) { _, _ ->
                    val nn = et.text.toString().trim()
                    if (nn.isEmpty()) {
                        toast(getString(R.string.mgr_name_empty))
                    } else {
                        val idx = customVoices.indexOfFirst { it.id == cv.id }
                        if (idx >= 0) {
                            customVoices[idx] = customVoices[idx].copy(name = nn)
                            store.saveCustomVoices(customVoices)
                            fitVoiceWidth()
                            syncVoiceUi()
                            toast(getString(R.string.mgr_renamed, nn))
                        }
                    }
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .create()
            d.setOnShowListener { skinDialog(d) }
            d.show()
        }

        if (all.isNotEmpty()) {
            section(getString(R.string.mgr_section_available))
            for (v in all) {
                val custom = customVoices.firstOrNull { it.id == v.id }
                row(v.name, "", getString(R.string.common_delete), deleteRed()) {
                    if (custom != null) confirmDeleteVoice(custom) else hideVoice(v)
                }
            }
        }

        val sc = ScrollView(this)
        sc.addView(box)
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.mgr_title))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_done), null)
            .create()
        dlg = d
        d.setOnShowListener {
            skinDialog(d)
            // 用户 2026-10-10：标题也向卡片中心挪 2dp（与音色名 / 删除按钮一致）
            runCatching {
                val id = resources.getIdentifier("alertTitle", "id", "android")
                d.findViewById<TextView>(id)?.let { t ->
                    t.setPadding(t.paddingStart + dp(2), t.paddingTop, t.paddingEnd + dp(2), t.paddingBottom)
                }
            }
        }
        d.show()
    }

    /** 删除内置音色 = 从列表移除（记进本机隐藏表）。想找回：用「自定义音色 ID」粘贴它的 ID。 */
    private fun hideVoice(v: Voice) {
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.mgr_del_title))
            .setMessage(getString(R.string.mgr_del_msg, v.name))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                hiddenVoices.add(v.id)
                store.hiddenVoices = hiddenVoices
                if (!voiceIsCustom && currentVoiceId == v.id) {
                    setVoice(null)          // 走统一入口 → 顺带清空骰子
                }
                fitVoiceWidth()
                syncVoiceUi()
                toast(getString(R.string.mgr_deleted, v.name))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        d.setOnShowListener {
            skinDialog(d)
            d.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(deleteRed())
        }
        d.show()
    }

    private fun confirmDeleteVoice(v: CustomVoice) {
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.mgr_del_title))
            .setMessage(getString(R.string.mgr_del_msg2, v.name))
            .setPositiveButton(getString(R.string.common_delete)) { _, _ ->
                customVoices.removeAll { it.id == v.id }
                store.saveCustomVoices(customVoices)
                store.clearPreviewTemplate(v.id)
                if (!voiceIsCustom && currentVoiceId == v.id) {
                    setVoice(null)          // 走统一入口 → 顺带清空骰子
                }
                fitVoiceWidth()
                syncVoiceUi()
                toast(getString(R.string.mgr_deleted, v.name))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        d.setOnShowListener {
            skinDialog(d)
            d.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(deleteRed())
        }
        d.show()
    }

    // ---------------------------------------------------------------- 音色筛选（本地记录）
    /** 点 ▼ 弹下拉：列出记录里出现过的音色 ID，选一个 → 只显示该 ID 的记录 */
    private fun showVoiceFilterPopup() {
        val c = Skin.colors(this)
        // 顺序按"音色目录"走（内置的原有顺序 + 自建），不再随历史先后乱跳
        val takenIds = takes.map { it.voiceId }.toSet()
        val ids = LinkedHashSet<String>()
        for (v in allVoices()) if (v.id in takenIds) ids.add(v.id)
        for (t in takes) if (t.voiceId.isNotBlank()) ids.add(t.voiceId)

        var pop: PopupWindow? = null
        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL

        fun addRow(label: String, tail: String, id: String?) {
            val on = id == filterVoiceId
            val tv = TextView(this)
            // 2026-10-17 用户：对号从名字左边移到**名字右边**；不再用全角空格占位，
            // 整列文字因此自然左移（再配合下面略收左内距 = "适度往左"）。
            val text = label + if (tail.isEmpty()) "" else "  ·  " + tail + if (on) "  ✓" else ""
            val ss = android.text.SpannableString(text)
            if (tail.isNotEmpty()) {
                ss.setSpan(
                    android.text.style.ForegroundColorSpan(c.dim),
                    label.length + 5, label.length + 5 + tail.length, 0
                )
            }
            if (on) {
                ss.setSpan(
                    android.text.style.ForegroundColorSpan(c.acc),
                    text.length - 1, text.length, 0
                )
            }
            tv.text = ss
            tv.setTextColor(c.txt)
            tv.textSize = 13f
            tv.isSingleLine = true
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.setPadding(dp(10), dp(12), dp(12), dp(12))
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                filterVoiceId = id
                diagFilterVoiceId = id
                renderHistory()
                pop?.dismiss()
            }
            listBox.addView(tv)
        }

        addRow(getString(R.string.filter_all), "", null)
        // 只显示音色名（ID 尾巴去掉——要看 ID 去设置页的「音色管理」）
        for (id in ids) addRow(voiceNameOf(id), "", id)

        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.addView(listBox)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Skin.shapeDp(this, c.bg, c.line, 12f)
        panel.setPadding(dp(4), dp(6), dp(4), dp(6))
        panel.addView(sc)

        val rowCount = ids.size + 1
        val h = minOf(dp(320), dp(12) + dp(45) * rowCount)
        val w = minOf(dp(290), resources.displayMetrics.widthPixels - dp(32))
        pop = PopupWindow(panel, w, h, true)
        pop.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        pop.isOutsideTouchable = true
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(8).toFloat()
        pop.showAsDropDown(llHistoryTitle, 0, dp(2))
    }

    // ---------------------------------------------------------------- 设置（v0.3：独立设置页）
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ---------------------------------------------------------------- 建音色（声音复刻）
    /** 建音色用的 Key：百炼用 apiKey，其它支持复刻的厂商用该厂商自己的 Key */
    private fun cloneKey(): String {
        val prov = TtsProviders.byId(store.providerId)
        return if (prov == null || prov.shape == "dashscope") store.apiKey.trim()
        else store.providerKey(store.providerId).ifBlank { store.apiKey.trim() }
    }

    private fun openCreateVoice() {
        if (cloneKey().isBlank()) {
            toast(getString(R.string.need_key_setup))
            openSettings()
            return
        }
        pendingSample = null
        pendingSampleDurMs = 0L
        pendingSampleMime = "audio/wav"

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))

        val tvTip = TextView(this)
        tvTip.text = getString(R.string.sample_tip)
        tvTip.setTextColor(cDim)
        tvTip.textSize = 12f
        box.addView(tvTip)

        val btnPick = smallBtn(getString(R.string.pick_audio))   // 文案缩短（"可多选"上面提示里已写），好让一行放得下
        fx(btnPick)
        btnPick.setOnClickListener { pickAudioFile() }
        // [5] 不想复刻时，也可以直接导入厂商已有的音色 ID
        val btnImportId = smallBtn(getString(R.string.import_voice_id))
        fx(btnImportId)
        val rowPick = LinearLayout(this)
        rowPick.orientation = LinearLayout.HORIZONTAL
        rowPick.setPadding(0, dp(8), 0, 0)
        // 等宽平分一行、都单行不换行、居中 —— 原来右边那个被挤成两行，很难看
        listOf(btnPick, btnImportId).forEach { b ->
            b.setSingleLine(true)
            b.ellipsize = android.text.TextUtils.TruncateAt.END
            b.gravity = android.view.Gravity.CENTER
        }
        btnPick.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(8) }
        btnImportId.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        rowPick.addView(btnPick)
        rowPick.addView(btnImportId)
        box.addView(rowPick)

        val tvFile = TextView(this)
        tvFile.text = getString(R.string.no_sample)
        tvFile.setTextColor(cDim)
        tvFile.textSize = 12f
        tvFile.setPadding(0, dp(4), 0, 0)
        box.addView(tvFile)

        box.addView(labelView(getString(R.string.prefix_label)))
        val etPrefix = EditText(this)
        etPrefix.hint = getString(R.string.prefix_hint)
        etPrefix.inputType = InputType.TYPE_CLASS_TEXT
        etPrefix.setTextColor(cTxt)
        etPrefix.setHintTextColor(cDim)
        etPrefix.textSize = 14f
        etPrefix.filters = arrayOf(InputFilter.LengthFilter(10))
        box.addView(etPrefix)

        box.addView(labelView(getString(R.string.name_label)))
        val etName = EditText(this)
        etName.hint = getString(R.string.name_hint)
        etName.inputType = InputType.TYPE_CLASS_TEXT
        etName.setTextColor(cTxt)
        etName.setHintTextColor(cDim)
        etName.textSize = 14f
        box.addView(etName)

        val tvSt = TextView(this)
        tvSt.setTextColor(cDim)
        tvSt.textSize = 12f
        tvSt.setPadding(0, dp(8), 0, 0)
        box.addView(tvSt)

        val sc = ScrollView(this)
        sc.addView(box)

        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_voice_title))
            .setView(sc)
            .setPositiveButton(getString(R.string.common_create), null)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()

        createDlg = dlg
        createFileTv = tvFile
        createStatus = tvSt
        createPrefixEt = etPrefix
        createNameEt = etName

        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { doCreateVoice() }
            skinDialog(dlg)
        }
        // 导入音色 ID：关掉本弹窗，改走「自定义音色 ID」那张卡（按 ID 直接加一个音色）
        btnImportId.setOnClickListener {
            createDlg?.dismiss()
            openCustomIdDialog()
        }
        dlg.setOnDismissListener {
            createDlg = null
            createFileTv = null
            createStatus = null
            createPrefixEt = null
            createNameEt = null
        }
        dlg.show()
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, REQ_PICK_AUDIO)
        } catch (e: Exception) {
            toast(getString(R.string.toast_no_picker))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE && resultCode == RESULT_OK) {
            val uri = data?.data
            val f = pendingSaveFile
            if (uri != null && f != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        f.inputStream().use { input -> input.copyTo(out) }
                    }
                    toast(getString(R.string.saved_file, f.name))
                } catch (e: Exception) {
                    toast(getString(R.string.toast_save_fail, e.message ?: ""))
                }
            }
            pendingSaveFile = null
            return
        }

        if (requestCode == REQ_VOICE_TEMPLATE && resultCode == RESULT_OK) {
            val uri = data?.data
            val vid = pendingVoiceTemplateId
            pendingVoiceTemplateId = null
            if (uri != null && !vid.isNullOrBlank()) importVoiceTemplate(vid, uri)
            return
        }

        if (requestCode == REQ_PICK_AUDIO && resultCode == RESULT_OK) {
            val list = ArrayList<Uri>()
            val clip = data?.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) list.add(clip.getItemAt(i).uri)
            } else {
                data?.data?.let { list.add(it) }
            }
            if (list.isNotEmpty()) importSamples(list)
        }
    }

    /** 多选入口：单个→原流程直传；多个→本地合并为一个 WAV 再走原流程（数量不限） */
    private fun importSamples(uris: List<Uri>) {
        if (uris.size == 1) {
            importSample(uris[0])
            return
        }
        createStatus?.text = getString(R.string.merging_n, uris.size)
        Thread {
            try {
                val dst = File(cacheDir, "voice_merged_" + System.currentTimeMillis() + ".wav")
                val r = AudioMerge.merge(this, uris, dst) { i, n ->
                    ui { createStatus?.text = getString(R.string.merging, i, n) }
                }
                if (r == null || r.ok == 0) {
                    dst.delete()
                    ui { createStatus?.text = getString(R.string.merge_fail_none) }
                } else {
                    pendingSample = dst
                    pendingSampleDurMs = r.durMs
                    pendingSampleMime = "audio/wav"
                    // 逐条时长放后台算（原来在主线程里对每个文件解一次，文件多会卡）
                    val durs = LongArray(uris.size) { probeUriDuration(uris[it]) }
                    ui {
                        val failNote = if (r.fail > 0) getString(R.string.skipped_note, r.fail) else ""
                        val each = StringBuilder()
                        for (i in uris.indices) {
                            val d = durs[i]
                            if (i > 0) each.append("   ")
                            each.append("[").append(i + 1).append("] ").append(if (d > 0) fmtDur(d) else "?")
                        }
                        createFileTv?.text = getString(R.string.merged_summary, r.ok, failNote, fmtDur(r.durMs), dst.length() / 1024) + "\n" + each
                        createStatus?.text = getString(R.string.material_ready)
                        if ((createPrefixEt?.text?.toString() ?: "").isBlank()) {
                            createPrefixEt?.setText(suggestPrefix(displayNameOf(uris[0]) ?: "merged"))
                        }
                    }
                }
            } catch (e: Exception) {
                ui { createStatus?.text = getString(R.string.merge_fail, e.message ?: "") }
            }
        }.start()
    }

    private fun importSample(uri: Uri) {
        createStatus?.text = getString(R.string.reading_sample)
        Thread {
            var tmp: File? = null
            try {
                val dn = displayNameOf(uri) ?: "sample"
                var mime = contentResolver.getType(uri) ?: ""
                var ext = dn.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext !in setOf("wav", "mp3", "m4a", "aac", "ogg", "flac", "opus")) {
                    ext = when {
                        mime.startsWith("audio/wav") -> "wav"
                        mime.startsWith("audio/mpeg") -> "mp3"
                        mime.startsWith("audio/mp4") -> "m4a"
                        mime.startsWith("audio/aac") -> "aac"
                        else -> "wav"
                    }
                }
                mime = when (ext) {
                    "wav" -> "audio/wav"
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "ogg", "opus" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    else -> if (mime.startsWith("audio/")) mime else "audio/wav"
                }
                val dst = File(cacheDir, "voice_sample_" + System.currentTimeMillis() + "." + ext)
                tmp = dst
                val ins = contentResolver.openInputStream(uri) ?: throw Exception(getString(R.string.open_file_fail))
                ins.use { input ->
                    dst.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_SAMPLE_BYTES) {
                                throw Exception(getString(R.string.file_too_big, MAX_SAMPLE_BYTES / 1024 / 1024))
                            }
                            out.write(buf, 0, n)
                        }
                    }
                }
                val durMs = probeDurationMs(dst)
                pendingSample = dst
                pendingSampleDurMs = durMs
                pendingSampleMime = mime
                ui {
                    createFileTv?.text = dn + " · " + (if (durMs > 0) fmtDur(durMs) else getString(R.string.dur_unknown)) +
                            " · " + (dst.length() / 1024) + " KB"
                    createStatus?.text = getString(R.string.material_ready)
                    if ((createPrefixEt?.text?.toString() ?: "").isBlank()) {
                        createPrefixEt?.setText(suggestPrefix(dn))
                    }
                }
            } catch (e: Exception) {
                tmp?.delete()
                pendingSample = null
                ui { createStatus?.text = getString(R.string.read_fail, e.message ?: "") }
            }
        }.start()
    }

    private fun displayNameOf(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun suggestPrefix(name: String): String {
        var base = name.substringBeforeLast('.', name).lowercase(Locale.US)
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .take(10)
        if (base.isNotEmpty() && base[0] in '0'..'9') base = "v" + base.take(9)
        return base.ifEmpty { "v" + System.currentTimeMillis().toString().takeLast(5) }
    }

    private fun doCreateVoice() {
        val sample = pendingSample ?: run {
            createStatus?.text = getString(R.string.fail_no_sample)
            return
        }
        if (!sample.exists()) {
            createStatus?.text = getString(R.string.fail_sample_missing)
            return
        }
        if (pendingSampleDurMs > 300000) {
            createStatus?.text = getString(R.string.fail_sample_long)
            return
        }
        val prefix = (createPrefixEt?.text?.toString() ?: "").trim().lowercase(Locale.US)
        val nonBailian = TtsProviders.byId(store.providerId)?.id != "aliyun-bailian"
        if (!nonBailian && !Regex("^[a-z0-9]{1,10}$").matches(prefix)) {
            createStatus?.text = getString(R.string.fail_prefix)
            return
        }
        val name = (createNameEt?.text?.toString() ?: "").trim().ifEmpty { prefix }
        val key = cloneKey()
        if (key.isEmpty()) {
            createStatus?.text = getString(R.string.fail_need_key_create)
            return
        }
        val ws = store.workspace.trim().ifEmpty { "ws-9y8n1gp7w6pg23tv" }
        val model = store.lastModel.trim().ifEmpty { "cosyvoice-v3.5-plus" }
        val posBtn = createDlg?.getButton(DialogInterface.BUTTON_POSITIVE)
        posBtn?.isEnabled = false
        createStatus?.text = getString(R.string.encoding_sample)

        // 复刻可能要好几十秒：前台服务把进程钉住，切后台也不断
        KeepAliveService.start(this, getString(R.string.keepalive_clone))

        Thread {
            try {
                // 非百炼：目前只有 Fish Audio 支持复刻（multipart 上传参考音频；不用前缀）
                if (nonBailian) {
                    val vid = ExtraTts.fishCreateVoice(
                        store.providerBaseUrl(store.providerId),
                        store.providerKey(store.providerId).ifBlank { store.apiKey },
                        sample.readBytes(),
                        name.ifBlank { getString(R.string.voice_self) },
                        "private"
                    )
                    ui {
                        customVoices.add(CustomVoice(vid, name, "", System.currentTimeMillis(), store.providerId))
                        store.saveCustomVoices(customVoices)
                        setVoice(vid)
                        syncVoiceUi()
                        if (createDlg?.isShowing == true) createDlg?.dismiss()
                        KeepAliveService.stop(this)
                        toast(getString(R.string.fish_created))
                    }
                    return@Thread
                }

                val b64 = Base64.encodeToString(sample.readBytes(), Base64.NO_WRAP)
                val dataUri = "data:" + pendingSampleMime + ";base64," + b64
                ui { createStatus?.text = getString(R.string.uploading_create) }
                client.createVoice(key, ws, model, prefix, dataUri, onResult = { vid ->
                    ui {
                        customVoices.add(CustomVoice(vid, name, prefix, System.currentTimeMillis(), store.providerId))
                        store.saveCustomVoices(customVoices)
                        setVoice(vid)
                        syncVoiceUi()
                        if (createDlg?.isShowing == true) createDlg?.dismiss()
                        KeepAliveService.stop(this)
                        toast(getString(R.string.created_ok, name))
                        tvStatus.text = getString(R.string.created_selected, name)
                    }
                }, onError = { msg ->
                    ui {
                        KeepAliveService.stop(this)
                        createStatus?.text = "❌ " + msg
                        posBtn?.isEnabled = true
                    }
                })
            } catch (e: Exception) {
                ui {
                    KeepAliveService.stop(this)
                    createStatus?.text = "❌ " + (e.message ?: getString(R.string.create_failed))
                    posBtn?.isEnabled = true
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- 小工具
    /**
     * 玻璃按键底（候选 B，与「保存」同语言）：accent 面 16% + accent 描边 42% + 主题 btnRadius。
     * 半径取 11dp 而不是 bg_btn 的 10dp —— 刻意避开 Skin 的"圆角猜角色"，免得换肤把它重绘掉。
     */
    /**
     * ⑭「生成」按钮底：提亮提纯实色 + **顶内光**（规范 14.1 质感：只允许"受光"，
     * 顶边 ~7% 高度一条 `rgba(255,252,244,.60)` 渐隐高光），让按键像"被光照到"。
     */
    private fun genButtonBg(color: Int, radiusDp: Float): android.graphics.drawable.Drawable {
        // 用户 2026-10-13：「生成」按钮也要跟「卡片浓度」——只让**填充**随浓度变淡（描边/字照旧）。
        val base = Skin.cardFillDp(this, color, null, radiusDp, Skin.colors(this).cardAlphaPct, 0f)
        val light = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColors(
                intArrayOf(0x99FFFCF4.toInt(), 0x00FFFCF4.toInt(), 0x00FFFCF4.toInt()),
                floatArrayOf(0f, 0.07f, 1f)
            )
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(base, light))
    }

    private fun glassBg(): android.graphics.drawable.Drawable {        val c = Skin.colors(this)
        val r = (Palettes.byId(store.themeId)?.btnRadius ?: 11).toFloat()
        val face = (c.acc and 0x00FFFFFF) or (0x29 shl 24)     // ~16%
        val stroke = (c.acc and 0x00FFFFFF) or (0x6B shl 24)   // ~42%
        return Skin.shapeDp(this, face, stroke, r, 100, 1.5f)
    }

    private fun smallBtn(label: String): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(cTxt)
        tv.textSize = 12f                 // 等比缩小 10%（原 13sp）
        tv.background = glassBg()
        tv.tag = "bg:keep"          // 玻璃底的圆角 == 主题 btnRadius，会撞上 Skin 的魔法半径 → 明确声明别动
        tv.setPadding(dp(12), dp(7), dp(12), dp(7))
        tv.isClickable = true
        tv.isFocusable = true
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        tv.layoutParams = lp
        return tv
    }

    private fun labelView(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(cDim)
        tv.textSize = 13f
        tv.setPadding(0, dp(12), 0, dp(2))
        return tv
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun ui(block: () -> Unit) {
        if (isFinishing || isDestroyed) return
        mainHandler.post {
            if (!isFinishing && !isDestroyed) block()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun fmtNum(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun fmtDur(ms: Long): String =
        if (ms <= 0) getString(R.string.rec_unknown) else String.format(Locale.US, getString(R.string.dur_fmt), ms / 1000.0)

    /** 崩溃兜底：把未捕获异常写到本机，下次进来自动提示"可复制" */
    private fun installCrashGuard() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(filesDir, "crash.txt").writeText(
                    getString(R.string.crash_time, java.util.Date().toString()) + "\n" + android.util.Log.getStackTraceString(e)
                )
            } catch (x: Exception) {
                // ignore
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** 上次若崩过：提示一次，可一键复制错误信息 */
    private fun showCrashIfAny() {
        val f = java.io.File(filesDir, "crash.txt")
        if (!f.exists()) return
        val text = try {
            f.readText()
        } catch (e: Exception) {
            ""
        }
        f.delete()
        if (text.isBlank()) return
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.crash_title))
            .setMessage(text.take(600))
            .setPositiveButton(getString(R.string.crash_copy)) { _, _ ->
                Clip.copy(this, text, "crash")
            }
            .setNegativeButton(getString(R.string.common_got_it), null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    /** 单个 Uri 的时长（音频/视频通用；失败返回 0）—— 给"逐个列出样本时长"用 */
    private fun probeUriDuration(uri: Uri): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(this, uri)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (e: Exception) {
        0L
    }

    private fun probeDurationMs(file: File): Long {
        // WAV 按文件实际长度自己算：服务端头里的 size 是 ≈2GB 流式占位值，
        // 系统解析会得出 "44739.2 秒" 这种离谱数字（2147483547 ÷ 48000）
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            wavDurationMs(file)?.let { return it }
        }
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(file.absolutePath)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            d
        } catch (e: Exception) {
            0L
        }
    }

    /** 解析 WAV 头求时长（data 块按文件实际剩余长度截断）；失败返回 null 交给系统兜底 */
    private fun wavDurationMs(file: File): Long? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len < 44) return null
                val head = ByteArray(minOf(len, 4096L).toInt())
                raf.readFully(head)
                if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return null
                var byteRate = 0L
                var dataOffset = -1L
                var p = 12
                while (p + 8 <= head.size) {
                    val id = String(head, p, 4, Charsets.US_ASCII)
                    val sz = u32(head, p + 4)
                    if (id == "fmt ") {
                        if (sz >= 16 && p + 20 <= head.size) byteRate = u32(head, p + 16)
                    } else if (id == "data") {
                        dataOffset = (p + 8).toLong()
                        break
                    }
                    if (sz > head.size.toLong()) break
                    p += 8 + sz.toInt() + (sz.toInt() and 1)
                }
                if (byteRate <= 0 || dataOffset < 0 || dataOffset >= len) return null
                (len - dataOffset) * 1000 / byteRate
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 把 WAV 头里的流式占位大小改写为真实大小（导出/分享后其他播放器也能显示正确时长） */
    private fun fixWavHeader(file: File) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val len = raf.length()
                if (len < 44) return
                val head = ByteArray(minOf(len, 4096L).toInt())
                raf.readFully(head)
                if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return
                if (u32(head, 4) != len - 8) writeU32(raf, 4L, len - 8)
                var p = 12
                while (p + 8 <= head.size) {
                    val id = String(head, p, 4, Charsets.US_ASCII)
                    val sz = u32(head, p + 4)
                    if (id == "data") {
                        val actual = len - (p + 8)
                        if (sz != actual) writeU32(raf, (p + 4).toLong(), actual)
                        break
                    }
                    if (sz > head.size.toLong()) break
                    p += 8 + sz.toInt() + (sz.toInt() and 1)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 修正旧记录里离谱的时长：重算 + 修头（一次性）。后台线程跑，避免启动时主线程做大量 I/O */
    private fun migrateDurationsAsync() {
        // ⚠️ 2026-10-17 重写（用户报"收藏 15 条只显示五六条"）：
        //  旧实现＝「拿 onCreate 的内存快照 → 后台线程晚点再整体 saveTakes」——
        //  这期间**新生成的记录会被旧快照覆盖掉**（索引里消失，音频还在），而且只在"某条时长对不上"时触发
        //  → 症状正是"时有时无"。现在：① 以**磁盘**为准；② 只改**要改的那几条**；③ 只做一次。
        val stamp = java.io.File(filesDir, "durations_fixed.flag")
        if (stamp.exists()) return
        Thread {
            runCatching {
                val fixed = HashMap<String, Take>()
                for (t in store.loadTakes()) {
                    if (!t.format.startsWith("wav")) continue
                    val f = store.fileOf(t)
                    if (!f.exists()) continue
                    fixWavHeader(f)
                    val d = probeDurationMs(f)
                    if (d > 0 && d != t.durationMs) fixed[t.id] = t.copy(durationMs = d)
                }
                if (fixed.isNotEmpty()) {
                    store.patchTakes { list ->
                        for (i in list.indices) fixed[list[i].id]?.let { list[i] = it }
                    }
                    ui {
                        // 只更新对应条目，**不整体替换**（否则又会抹掉期间新增的记录）
                        for (i in takes.indices) fixed[takes[i].id]?.let { takes[i] = it }
                        renderHistory()
                    }
                }
                stamp.writeText("ok")
            }
        }.start()
    }

    private fun u32(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun writeU32(raf: RandomAccessFile, off: Long, v: Long) {
        raf.seek(off)
        raf.write(
            byteArrayOf(
                (v and 0xFF).toByte(),
                ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(),
                ((v shr 24) and 0xFF).toByte()
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        stopVoicePreview()
    }
}
