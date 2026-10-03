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
    private val customLabel = "✏️ 自定义音色 ID…"

    private val formats = listOf(
        Fmt("wav24", "wav24 · 推荐", "wav", 24000, null, "wav"),
        Fmt("wav48", "wav48", "wav", 48000, null, "wav"),
        Fmt("wav16", "wav16", "wav", 16000, null, "wav"),
        Fmt("mp3_256", "mp3_256", "mp3", 24000, null, "mp3"),
        Fmt("mp3_128", "mp3_128", "mp3", 16000, null, "mp3")
    )

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
    /** 破坏性动作（删除）的红色：不写死一个 —— 浅色主题更深、深色主题提亮 */
    private fun deleteRed(): Int {
        val c = Skin.colors(this)
        return if (c.light) 0xFFB23A34.toInt()
        else Skin.Colors.mix(0xFFE06B64.toInt(), c.txt, 0.18f)
    }

    // ---------------------------------------------------------------- 状态
    private lateinit var store: Store
    private val client = DashScopeClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val takes = mutableListOf<Take>()
    private val rowRefs = mutableListOf<RowRef>()
    private val customVoices = mutableListOf<CustomVoice>()

    // 建音色（声音复刻）
    private val REQ_PICK_AUDIO = 1001
    private val MAX_SAMPLE_BYTES = 15L * 1024 * 1024
    /** 本地记录首屏最多渲染多少条（其余点「显示全部」再看）——直接决定冷启动要建多少 View */
    private val HISTORY_PREVIEW = 25
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
    /** 正在播放那行的细进度条 */
    private var playingProgress: View? = null
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
    private var historyExpanded = false

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
        setContentView(R.layout.activity_main)
        store = Store(this)
        store.hidePresetVoicesOnce()          // 预设音色默认隐藏（一次性）
        customVoices.addAll(store.loadCustomVoices())
        hiddenVoices.addAll(store.hiddenVoices)
        bindViews()
        setupVoicePicker()
        setupSliders()
        setupActions()

        takes.addAll(store.loadTakes())
        renderHistory()
        migrateDurationsAsync()

        if (store.apiKey.isBlank()) {
            tvStatus.text = "首次使用：请点右上角「设置」填入 API Key（可从剪贴板粘贴）"
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
        applyLook()
    }

    override fun onResume() {
        super.onResume()
        applyLook()
        // 设置页可能删/改过记录、音色、隐藏列表 → 以 Store 为准整表重载（Store = 唯一数据源）
        reloadFromStore()
        // 设置页改过「默认 model」且用户没在高级参数里手改过 → 同步过来（不覆盖用户输入）
        if (!modelTouched) setModelField(store.lastModel)
        if (etApiKey.text.toString() != store.apiKey) etApiKey.setText(store.apiKey)
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
            currentVoiceId = allVoices().firstOrNull()?.id ?: ""
        }
        fitVoiceWidth()
        syncVoiceUi()
        renderHistory()
    }

    /** 设置「模型」输入框并把它标记为"未被用户手改" */
    private fun setModelField(v: String) {
        suppressModelWatch = true
        etModel.setText(v)
        suppressModelWatch = false
        modelTouched = false
    }

    /** v0.3：主题 + 壁纸统一入口（设置页改完回来即生效） */
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
        // 结果卡三颗按钮与记录卡统一成"玻璃按键"
        listOf(btnPlay, btnShare, btnExport).forEach { it.background = glassBg(); it.tag = "bg:keep" }
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
        etCustomVoice = findViewById(R.id.etCustomVoice)
        tvVoiceNote = findViewById(R.id.tvVoiceNote)
        btnCreateVoice = findViewById(R.id.btnCreateVoice)
        etText = findViewById(R.id.etText)
        btnClearText = findViewById(R.id.btnClearText)
        // 清空叉号：仅清空文本，不改变其它参数
        btnClearText.setOnClickListener {
            etText.setText("")
            etText.requestFocus()
        }
        etInstr = findViewById(R.id.etInstr)
        llChips = findViewById(R.id.llChips)
        etSeed = findViewById(R.id.etSeed)
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
                store.apiKey = s?.toString()?.trim() ?: ""
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

        // 翻译：一键把文本框内容翻成目标语言（内置免费接口，不跳 App）
        btnTranslate.background = glassBg()
        btnTranslate.tag = "bg:keep"
        fx(btnTranslate, findViewById(R.id.llRoot))
        tvTransTarget.text = "→ " + store.trTarget
        btnTranslate.setOnClickListener { translateText() }
        tvTransTarget.setOnClickListener { pickTransTarget() }
    }

    /** 翻译文本框内容（原地替换，不跳任何 App） */
    private fun translateText() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) {
            toast("请先输入文本")
            return
        }
        tvStatus.text = "翻译中…"
        btnTranslate.isEnabled = false
        TransClient.translate(text, store.trTarget, store.trLastSource) { out, err, src ->
            ui {
                btnTranslate.isEnabled = true
                if (out != null) {
                    src?.let { store.trLastSource = it }
                    etText.setText(out)
                    tvStatus.text = "✅ 已翻译成" + store.trTarget
                } else {
                    tvStatus.text = "❌ " + (err ?: "翻译失败")
                    toast(err ?: "翻译失败")
                }
            }
        }
    }

    /** 选目标语言 */
    private fun pickTransTarget() {
        val names = TransClient.targetNames().toTypedArray()
        val d = AlertDialog.Builder(this)
            .setTitle("翻译成")
            .setSingleChoiceItems(names, names.indexOf(store.trTarget)) { dlg, which ->
                store.trTarget = names[which]
                tvTransTarget.text = "→ " + names[which]
                dlg.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()
        d.setOnShowListener { skinDialog(d) }
        d.show()
    }

    /** 高级参数展开/收起：180ms 高度过渡（原来瞬变） */
    private fun setAdvanced(show: Boolean, animate: Boolean) {
        tvAdvanced.text = if (show) "高级参数 ▾" else "高级参数 ▸"
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
            (v as? TextView)?.setTextColor(Skin.colors(parent.context).txt)
            pressFx(v, parent)
            return v
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getDropDownView(position, convertView, parent)
            (v as? TextView)?.setTextColor(Skin.colors(parent.context).txt)
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

        spFormat.adapter = ThemedSpinnerAdapter(this, formats.map { it.label })
        setModelField(store.lastModel)
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
            customVoices.map { Voice(it.name, it.id, "自建音色（前缀 " + it.prefix + "）") }

    /** 设为某个音色（voiceId 为空/已失效时回落到第一个） */
    private fun setVoice(voiceId: String?) {
        val all = allVoices()
        if (voiceId != null && all.any { it.id == voiceId }) {
            currentVoiceId = voiceId
            voiceIsCustom = false
        } else if (all.none { it.id == currentVoiceId }) {
            currentVoiceId = all.firstOrNull()?.id ?: ""
            voiceIsCustom = false
        }
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
                toast("当前厂商（" + (TtsProviders.byId(store.providerId)?.name ?: "") + "）不支持声音复刻，请切到「阿里云百炼」再建音色")
                return@setOnClickListener
            }
 openCreateVoice() }
        btnDice.setOnClickListener { etSeed.setText((0..65535).random().toString()) }
        tvAdvanced.setOnClickListener {
            val show = llAdvanced.visibility != View.VISIBLE
            setAdvanced(show, animate = true)
            store.advExpanded = show
        }
        btnGenerate.setOnClickListener { generate(null) }
        btnCancel.setOnClickListener {
            if (busy) {
                client.cancel()
                finishBusy()
                tvStatus.text = "已取消"
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

    /** 「自定义音色 ID…」→ 弹一张卡片：① 填厂商那边的音色 ID ② 给它起个中文名 */
    private fun openCustomIdDialog() {
        val c = Skin.colors(this)
        val d = dp(16)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d, dp(6), d, 0)
        }
        val etId = EditText(this).apply {
            hint = "音色 ID（从厂商网站复制的那串数字/字母）"
            setSingleLine(true)
            setText(if (voiceIsCustom) etCustomVoice.text.toString().trim() else "")
        }
        val etName = EditText(this).apply {
            hint = "自定义名字"
            setSingleLine(true)
            setText("")
        }
        box.addView(etId, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(etName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dlg = AlertDialog.Builder(this)
            .setTitle("自定义音色")
            .setView(box)
            .setPositiveButton("导入") { _, _ ->
                val id = etId.text.toString().trim()
                val nm = etName.text.toString().trim()
                if (id.isEmpty()) {
                    toast("音色 ID 不能为空")
                    return@setPositiveButton
                }
                val name = nm.ifEmpty { "自定义音色" }
                // 存进"自建音色"列表（这样它能出现在下拉里，也能在「管理音色」里改名/删除）
                if (customVoices.none { it.id == id }) {
                    customVoices.add(CustomVoice(id, name, "", System.currentTimeMillis()))
                    store.saveCustomVoices(customVoices)
                }
                etCustomVoice.setText(id)
                setVoice(id)
                syncVoiceUi()
                toast("已加入：" + name)
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }   // 跟随主题（原来没接换肤，弹窗是默认深灰）
        dlg.show()
    }

    private fun syncVoiceUi() {
        // P0.5：按所选模型置灰不适用的参数（数据驱动，见 TtsModels.supports）
        val note = TtsModels.unsupportedNote(store.providerId, store.lastModel)
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
            text = if (noKey) "还没配置任何厂商：进「设置 → 模型」选厂商、填 Key 就能用。\n本机「系统 TTS」不需要 Key，也可以直接选它先试一句。" else note
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val all = allVoices()
        val cur = all.firstOrNull { it.id == currentVoiceId }
        tvVoice.text = if (voiceIsCustom) customLabel else (cur?.name ?: all.firstOrNull()?.name ?: "")
        if (voiceIsCustom) {
            etCustomVoice.visibility = View.VISIBLE
            tvVoiceNote.text = "粘贴完整音色 ID"
        } else {
            etCustomVoice.visibility = View.GONE
            tvVoiceNote.text = cur?.note ?: ""
        }
        renderChips()
    }

    private fun selectedVoiceId(): String =
        if (voiceIsCustom) etCustomVoice.text.toString().trim() else currentVoiceId

    private fun voiceNameOf(id: String): String = allVoices().firstOrNull { it.id == id }?.name ?: "自定义音色"

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
        clear.text = "清空"
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
        etSearch.hint = "搜索语言（中文 / zh / ja…）"
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
            .setTitle("选择语言（可多选）")
            .setView(sc)
            .setPositiveButton("确定") { _, _ ->
                langSel.clear(); langSel.addAll(draft)   // 只有“确定”才落盘
                applyLangSel()
            }
            .setNeutralButton("清空") { _, _ ->
                langSel.clear()
                applyLangSel()
            }
            .setNegativeButton("取消", null)
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
            tv.text = "没有匹配的语言"
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
    }

    // ---------------------------------------------------------------- 生成
    private fun generate(seedOverride: Int?) {
        if (busy) {
            toast("正在合成中，请稍候…")
            return
        }
        val key = store.apiKey.trim()
        if (key.isEmpty()) {
            toast("请先在「设置」里填写 API Key")
            openSettings()
            return
        }
        val text = etText.text.toString().trim()
        if (text.isEmpty()) {
            toast("请先输入文本")
            return
        }
        if (text.length > 5000) {
            toast("文本过长（" + text.length + " 字），请分段合成")
            return
        } else if (text.length > 2000) {
            toast("文本较长（" + text.length + " 字），若失败请分段合成")
        }
        val voiceId = selectedVoiceId()
        if (voiceId.isEmpty()) {
            toast("请选择音色或输入自定义音色 ID")
            return
        }

        val hotFix: JSONObject? = try {
            optionalJson(etHotfix, "hot_fix")
        } catch (e: BadJsonException) {
            toast(e.label + " 不是合法 JSON，请检查格式")
            return
        }
        val extra: JSONObject? = try {
            optionalJson(etExtra, "额外参数")
        } catch (e: BadJsonException) {
            toast(e.label + " 不是合法 JSON，请检查格式")
            return
        }

        val seed = (seedOverride ?: etSeed.text.toString().trim().toIntOrNull() ?: (0..65535).random()).coerceIn(0, 65535)
        // 留空 = 每次随机，不回填；仅「重抽」时回填实际种子
        if (seedOverride != null) {
            etSeed.setText(seed.toString())
        }

        val fmt = formats[spFormat.selectedItemPosition.coerceIn(0, formats.size - 1)]
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
        KeepAliveService.start(this, "正在合成语音…（可以切到后台等）")
        btnGenerate.isEnabled = false
        btnGenerate.alpha = 0.55f
        btnCancel.visibility = View.VISIBLE
        svRoot.smoothScrollTo(0, 0)
        tvStatus.text = "连接中…"

        var lastTick = 0L
        // P1：按厂商形态分派 —— 百炼内部再按模型分 ws/http；OpenAI 系走 OpenAI 兼容客户端
        val prov = TtsProviders.byId(store.providerId) ?: TtsProviders.all.first()
        val provKey = store.providerKey(prov.id).ifBlank { store.apiKey }
        store.addProviderKeyToHistory(prov.id, provKey)   // 用过的密钥进该厂商的历史下拉
        val synthCall = { cb: SynthCallback ->
            if (prov.shape == "system") {
                ExtraTts2.system(this@MainActivity, req.text, req.rate, req.pitch, cb)
            } else if (prov.shape == "gemini") {
                ExtraTts2.gemini(provKey, req.model, req.voice, req.text, cb)
            } else if (prov.shape == "mimo") {
                ExtraTts2.mimo(
                    store.providerBaseUrl(prov.id), provKey, req.model, req.text, cb,
                    req.instruction, req.voice, req.seed
                )
            } else if (prov.shape == "elevenlabs") {
                ExtraTts.eleven(
                    store.providerBaseUrl(prov.id), provKey, req.voice, req.model, req.text, 0.5, 0.75, cb,
                    req.rate, req.seed
                )
            } else if (prov.shape == "minimax") {
                ExtraTts.minimax(
                    store.providerBaseUrl(prov.id), provKey, store.workspace, req.model, req.voice,
                    req.text, req.rate, req.pitch, req.volume, cb, req.instruction
                )
            } else if (prov.shape == "fish") {
                ExtraTts.fish(
                    store.providerBaseUrl(prov.id), provKey, req.voice, req.text, cb, req.model,
                    req.rate, req.volume, extra, req.instruction, req.seed
                )
            } else if (prov.shape == "openai" || prov.shape == "xai") {
                // 自定义渠道：用用户填的 Base URL
                val eff = prov.copy(baseUrl = store.providerBaseUrl(prov.id))
                OpenAiCompatTts.synthesize(
                    eff, provKey, req.model, req.voice, req.text, req.instruction, cb,
                    TtsProviders.Cfg(
                        path = store.providerPath(prov.id),
                        auth = store.providerAuth(prov.id),
                        resp = store.providerResp(prov.id),
                        extra = extra
                    ),
                    rate = req.rate,
                    volume = req.volume,
                    seed = if (prov.id == "custom") req.seed else null
                )
            }
            else
                client.synthesize(req.copy(apiKey = provKey), cb)
        }
        synthCall(object : SynthCallback {
            override fun onConnected() {
                ui { tvStatus.text = "已连接…" }
            }

            override fun onStarted() {
                ui { tvStatus.text = "合成中…" }
            }

            override fun onProgress(receivedBytes: Int) {
                val now = System.currentTimeMillis()
                if (now - lastTick < 80) return
                lastTick = now
                val s = if (fmt.format == "wav") {
                    String.format(Locale.US, "合成中… %.1f 秒音频", receivedBytes / (2.0 * fmt.sampleRate))
                } else {
                    String.format(Locale.US, "合成中… %.0f KB", receivedBytes / 1024.0)
                }
                ui { tvStatus.text = s }
            }

            override fun onFinished(audio: ByteArray) {
                try {
                    val take = saveTake(req, fmt, audio)
                    ui { onTakeReady(take) }
                } catch (e: Exception) {
                    ui {
                        finishBusy()
                        tvStatus.text = "❌ 保存失败"
                        toast("保存失败：" + e.message)
                    }
                }
            }

            override fun onError(message: String) {
                val friendly = Err.friendly(message)
                ui {
                    finishBusy()
                    tvStatus.text = "❌ " + friendly
                    toast("合成失败：" + friendly.take(120))
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

    private fun saveTake(req: SynthRequest, fmt: Fmt, audio: ByteArray): Take {
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
        takes.add(0, take)
        while (takes.size > 100) {
            val old = takes.removeAt(takes.size - 1)
            store.deleteFile(old)
        }
        store.saveTakes(takes)
        return take
    }

    private fun onTakeReady(take: Take) {
        finishBusy()
        tvStatus.text = "✅ 完成"
        currentTake = take
        cardResult.visibility = View.VISIBLE
        tvResultInfo.text = take.voiceName + " · " + take.format + " · 语速 " + fmtNum(take.rate) +
                " · 音调 " + fmtNum(take.pitch) + " · 音量 " + take.volume +
                " · 🎲 " + take.seed + " · 时长 " + fmtDur(take.durationMs)
        renderHistory()
        startPlayback(take)
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
        AudioFocus.request(this) { stopPlayback(); refreshPlayButtons() }
        val file = store.fileOf(take)
        if (!file.exists()) {
            toast("文件不存在")
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
                stopPlayback()
                refreshPlayButtons()
            }
            mp.setOnErrorListener { _, what, extra ->
                stopPlayback()
                refreshPlayButtons()
                toast("播放出错（$what/$extra）")
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
            toast("播放失败：" + e.message)
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
        AudioFocus.abandon(this)      // 交还焦点 → 刚才暂停的音乐自动续播
    }

    private fun refreshPlayButtons() {
        val cur = currentTake
        btnPlay.text = if (cur != null && playingId == cur.id && player?.isPlaying == true) "⏸ 暂停" else "▶ 播放"
        for (r in rowRefs) {
            r.playBtn.text = if (playingId == r.take.id) "⏸ 暂停" else "▶ 播放"
            r.prog.visibility = if (playingId == r.take.id) View.VISIBLE else View.GONE
        }
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
            toast("文件不存在")
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
            startActivity(Intent.createChooser(intent, "分享音频"))
        } catch (e: Exception) {
            toast("分享失败：" + (e.message ?: ""))
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
            toast("文件不存在")
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
            toast("无法打开保存对话框：" + (e.message ?: ""))
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
        llHistory.removeAllViews()
        rowRefs.clear()
        val list = filterVoiceId?.let { id -> takes.filter { it.voiceId == id } } ?: takes
        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = if (filterVoiceId == null) "暂无记录" else "该音色暂无记录（点 ▼ 可换/取消筛选）"
            tv.setTextColor(cDim)
            tv.textSize = 13f
            tv.setPadding(0, dp(8), 0, 0)
            llHistory.addView(tv)
            return
        }
        // 首屏只建最近 25 条：冷启动不必为几十上百条记录创建上千个 View
        val shown = if (historyExpanded) list else list.take(HISTORY_PREVIEW)
        for (t in shown) {
            llHistory.addView(buildRow(t))
        }
        if (list.size > HISTORY_PREVIEW) {
            val more = TextView(this)
            more.text = if (historyExpanded) "收起（只显示最近 $HISTORY_PREVIEW 条）"
            else "显示全部（共 ${list.size} 条）"
            more.setTextColor(cDim)
            more.textSize = 13f
            more.gravity = Gravity.CENTER
            // 判定范围 = 一整条长条（卡片整宽 × 约 42dp 高）：字小、靶大，且与相邻行/按钮不重叠
            // 底部留白比顶部小 40%：卡片底边到文字的距离比原来近 20%
            more.setPadding(dp(12), dp(15), dp(12), dp(9))
            val tvAttr = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tvAttr, true)) {
                more.setBackgroundResource(tvAttr.resourceId)
            }
            more.isClickable = true
            more.isFocusable = true
            more.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            more.setOnClickListener {
                historyExpanded = !historyExpanded
                renderHistory()
            }
            llHistory.addView(more)
        }
        Skin.apply(llHistory, Skin.colors(this))
    }

    private fun buildRow(take: Take): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.background = ContextCompat.getDrawable(this, R.drawable.bg_row)
        row.tag = "r:row"
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
        title.textSize = 14f
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setPadding(0, dp(10), dp(20), 0)     // 顶部 10dp 移到标题上；右侧只让 20dp
        head.addView(title, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val x = TextView(this)
        x.text = "✕"
        x.textSize = 9f                             // 字形面积再 −10%
        x.setTextColor(Skin.Colors.mix(cDim, Skin.colors(this).bg, 0.15f))   // 比次文字再浅 15%
        x.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        val xInset = (3.5f * resources.displayMetrics.density).toInt()   // 字形内距 3.5dp
        x.setPadding(0, xInset, xInset, 0)
        x.layoutParams = android.widget.FrameLayout.LayoutParams(
            dp(48), dp(48), android.view.Gravity.TOP or android.view.Gravity.END   // 判定再 +15%
        )
        x.isClickable = true
        x.isFocusable = true
        x.setOnClickListener { confirmDelete(take) }
        head.addView(x)
        row.addView(head)

        // ✕ 的判定范围：宽度保持 48dp，高度向下延伸到"卡片高度的一半"（长方形、随卡片变化）
        row.post {
            val half = maxOf(dp(48), title.height)   // 用正文高度（不依赖 row，避免循环撑高）
            if (half > dp(48)) {
                (x.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
                    lp.height = half
                    x.layoutParams = lp
                }
            }
        }

        // 常驻信息：时间 · 音色 · 格式 · 字数 · 时长（🎲 与语速/音调/音量/模型都收进「属性」）
        val meta = TextView(this)
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(take.createdAt))
        meta.text = time + " · " + take.voiceName + " · " + take.format +
                " · " + take.text.length + " 字" +
                " · 时长 " + fmtDur(take.durationMs)
        meta.setTextColor(cDim)
        meta.textSize = 12f
        meta.setPadding(0, dp(3), dp(12), dp(6))
        row.addView(meta)

        // 「属性」收起时隐藏：语速 / 音调 / 音量 / 模型 / 种子
        val detail = TextView(this)
        val modelShown = if (take.model.isBlank()) store.lastModel else take.model
        detail.text = "语速 " + fmtNum(take.rate) + " · 音调 " + fmtNum(take.pitch) +
                " · 音量 " + take.volume + " · 模型 " + modelShown + " · 🎲 " + take.seed
        detail.setTextColor(cTxt)                  // 与正文一致（不再发灰"隐形"）
        detail.textSize = 13f
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

        val bPlay = smallBtn("▶ 播放")
        val bFill = smallBtn("回填")
        val bDown = smallBtn("下载")
        // 收藏星：手绘矢量（不再用 ☆/★ 字形——字形太小、且会被系统字体染成杂色）
        val bStar = LinearLayout(this)
        bStar.orientation = LinearLayout.HORIZONTAL
        bStar.gravity = android.view.Gravity.CENTER_VERTICAL
        // 与同排按钮用同一套"玻璃底"（原来这里还是旧的 bg_btn，会被换肤刷成更深的一档，显得突兀）
        bStar.background = glassBg()
        bStar.tag = "bg:keep"
        bStar.setPadding(dp(13), dp(8), dp(13), dp(8))
        bStar.isClickable = true
        bStar.isFocusable = true
        bStar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) }
        val starIcon = StarView(this)          // 自绘：绕开 tint/ColorFilter 不生效的问题
        starIcon.layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))   // 比原来小 10%
        bStar.addView(starIcon)
        val starLabel = TextView(this)
        starLabel.textSize = 13f                       // 与旁边按钮同字号
        starLabel.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(2) }              // 星与文字贴近 → 缩短整个按钮长度
        bStar.addView(starLabel)
        val bShare = smallBtn("分享")

        bPlay.setOnClickListener { toggleTake(take) }
        bFill.setOnClickListener {
            fillFrom(take)
            toast("已回填参数")
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
            // 自绘 View 不走 Skin.apply → 直接用主题色；描边比正文浅 35%，免得又厚又深太抢眼
            val sc = Skin.colors(this)
            val tc = Skin.Colors.mix(sc.txt, sc.card, 0.35f)
            starIcon.colorSolid = tc      // 未收藏＝描边跟旁边文字同色
            starIcon.filled = fav         // 已收藏＝亮黄渐变（StarView 里直接画）
            // 星与文字都用与旁边按钮相同的颜色（不做"收藏专属色"）
            starLabel.text = "收藏"                     // 恒定文字，只有星星上色/变实心
            starLabel.setTextColor(tc)
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
            toast(if (nowFav) "已收藏" else "已取消收藏")
        }
        bShare.setOnClickListener { shareTake(take) }

        btnRow.addView(bPlay)
        btnRow.addView(bFill)
        btnRow.addView(bDown)
        btnRow.addView(bStar)
        btnRow.addView(bShare)

        // 播放进度：一条 2dp 细线（播放时才出现）
        val prog = View(this)
        prog.background = Skin.shapeDp(this, Skin.colors(this).acc, null, 0f)   // 圆角 0 → 不撞 Skin 的角色
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
            openCustomIdDialog()   // 弹卡片：填 ID + 起名字（原来是回主页显示输入框）
            etCustomVoice.setText(take.voiceId)
            syncVoiceUi()
        }
        etText.setText(take.text)
        etInstr.setText(take.instruction)
        etSeed.setText(take.seed.toString())
        sbRate.progress = Math.round((take.rate - 0.5) * 100).toInt().coerceIn(0, 150)
        sbPitch.progress = Math.round((take.pitch - 0.5) * 100).toInt().coerceIn(0, 150)
        sbVol.progress = take.volume.coerceIn(0, 100)
        val fi = formats.indexOfFirst { it.key == take.format }
        if (fi >= 0) spFormat.setSelection(fi)
        onParamChanged()   // 程序化设置 progress 不触发 ratchet 的标签刷新 → 手动刷一次
    }

    private fun confirmDelete(take: Take) {
        val dlg = AlertDialog.Builder(this)
            .setTitle("删除这条记录？")
            .setMessage(take.text.take(60))
            .setPositiveButton("删除") { _, _ ->
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
                toast("已删除")
            }
            .setNegativeButton("取消", null)
            .create()
        dlg.setOnShowListener { skinDialog(dlg) }
        dlg.show()
    }

    private fun confirmClearHistory() {
        if (takes.isEmpty()) {
            toast("没有记录")
            return
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("清空全部记录？")
            .setMessage("将删除 " + takes.size + " 条记录及其音频文件")
            .setPositiveButton("清空") { _, _ ->
                stopPlayback()
                takes.forEach { store.deleteFile(it) }
                takes.clear()
                filterVoiceId = null
                historyExpanded = false
                store.saveTakes(takes)
                currentTake = null
                cardResult.visibility = View.GONE
                renderHistory()
                refreshPlayButtons()
                toast("已清空")
            }
            .setNegativeButton("取消", null)
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
                val dlg = AlertDialog.Builder(this)
                    .setTitle("删除这个音色？")
                    .setMessage("「" + v.name + "」将从列表里移除。")   // 中性文案：不提"内置/隐藏/恢复"这类内部信息
                    .setPositiveButton("删除") { _, _ ->
                        if (cv != null) {
                            customVoices.removeAll { it.id == cv.id }
                            store.saveCustomVoices(customVoices)
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
                        toast("已删除")
                    }
                    .setNegativeButton("取消", null)
                    .create()
                dlg.setOnShowListener { skinDialog(dlg) }   // 跟随主题
                dlg.show()
                true
            }
            listBox.addView(row)
        }

        fun switchTo(id: String) {
            voiceIsCustom = false
            currentVoiceId = id
            fitVoiceWidth()
            syncVoiceUi()
            pop?.dismiss()          // 指示符已搬到首页 → 这里立即收起（不再等 790ms）
        }

        if (all.isEmpty()) {
            val hint = TextView(this)
            hint.text = "还没有音色：用「＋ 建音色」上传样本，或用下面的「自定义音色 ID」"
            hint.setTextColor(c.hint)
            hint.textSize = 12.5f
            hint.setPadding(dp(16), dp(10), dp(16), dp(10))
            listBox.addView(hint)
        }
        // 当前音色用主题主色标出（指示符搬去首页后，这里留个静态标记）
        for (v in all) addRow(v.name, v.id, if (!voiceIsCustom && v.id == currentVoiceId) c.acc else c.txt) { switchTo(v.id) }
        addRow(customLabel, null, c.dim) {
            openCustomIdDialog()   // 弹卡片：填 ID + 起名字（原来是回主页显示输入框）
            syncVoiceUi()
            pop?.dismiss()
        }
        addRow("管理音色…", null, c.acc) {
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

        val h = minOf(dp(430), dp(12) + dp(48) * (all.size + 2))
        val w = minOf(dp(310), resources.displayMetrics.widthPixels - dp(32))
        // 获焦 = 返回键能关；INPUT_METHOD_NOT_NEEDED 防它把键盘带出来
        pop = PopupWindow(panel, w, h, true)
        pop.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        pop.isOutsideTouchable = true
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(8).toFloat()
        pop.showAsDropDown(llVoice, 0, dp(2))
    }

    // ---------------------------------------------------------------- 管理音色（自建真删 / 内置隐藏）
    private fun manageVoices() {
        val all = allVoices()
        if (all.isEmpty() && hiddenVoices.isEmpty()) {
            toast("音色列表是空的")
            return
        }
        val c = Skin.colors(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(4), dp(20), dp(4))
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
                rn.text = "改名"
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
                .setTitle("重命名音色")
                .setView(et)
                .setPositiveButton("保存") { _, _ ->
                    val nn = et.text.toString().trim()
                    if (nn.isEmpty()) {
                        toast("名字不能为空")
                    } else {
                        val idx = customVoices.indexOfFirst { it.id == cv.id }
                        if (idx >= 0) {
                            customVoices[idx] = customVoices[idx].copy(name = nn)
                            store.saveCustomVoices(customVoices)
                            fitVoiceWidth()
                            syncVoiceUi()
                            toast("已改名为：" + nn)
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .create()
            d.setOnShowListener { skinDialog(d) }
            d.show()
        }

        if (all.isNotEmpty()) {
            section("可用音色")
            for (v in all) {
                val custom = customVoices.firstOrNull { it.id == v.id }
                row(v.name, "", "删除", deleteRed()) {
                    if (custom != null) confirmDeleteVoice(custom) else hideVoice(v)
                }
            }
        }

        val sc = ScrollView(this)
        sc.addView(box)
        val d = AlertDialog.Builder(this)
            .setTitle("管理音色")
            .setView(sc)
            .setPositiveButton("完成", null)
            .create()
        dlg = d
        d.setOnShowListener { skinDialog(d) }
        d.show()
    }

    /** 删除内置音色 = 从列表移除（记进本机隐藏表）。想找回：用「自定义音色 ID」粘贴它的 ID。 */
    private fun hideVoice(v: Voice) {
        val d = AlertDialog.Builder(this)
            .setTitle("删除音色")
            .setMessage("「" + v.name + "」将从音色列表移除。")
            .setPositiveButton("删除") { _, _ ->
                hiddenVoices.add(v.id)
                store.hiddenVoices = hiddenVoices
                if (!voiceIsCustom && currentVoiceId == v.id) {
                    currentVoiceId = allVoices().firstOrNull()?.id ?: ""
                }
                fitVoiceWidth()
                syncVoiceUi()
                toast("已删除：" + v.name)
            }
            .setNegativeButton("取消", null)
            .create()
        d.setOnShowListener {
            skinDialog(d)
            d.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(deleteRed())
        }
        d.show()
    }

    private fun confirmDeleteVoice(v: CustomVoice) {
        val d = AlertDialog.Builder(this)
            .setTitle("删除音色")
            .setMessage("「" + v.name + "」将从本机音色列表中移除。\n云端已复刻的音色不受影响，之后可重新添加。")
            .setPositiveButton("删除") { _, _ ->
                customVoices.removeAll { it.id == v.id }
                store.saveCustomVoices(customVoices)
                if (!voiceIsCustom && currentVoiceId == v.id) {
                    currentVoiceId = allVoices().firstOrNull()?.id ?: ""
                }
                fitVoiceWidth()
                syncVoiceUi()
                toast("已删除：" + v.name)
            }
            .setNegativeButton("取消", null)
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
            val tv = TextView(this)
            val prefix = if (id == filterVoiceId) "✓ " else "　 "
            val text = prefix + label + if (tail.isEmpty()) "" else "  ·  " + tail
            val ss = android.text.SpannableString(text)
            if (tail.isNotEmpty()) {
                ss.setSpan(
                    android.text.style.ForegroundColorSpan(c.dim),
                    text.length - tail.length, text.length, 0
                )
            }
            tv.text = ss
            tv.setTextColor(c.txt)
            tv.textSize = 13f
            tv.setPadding(dp(14), dp(12), dp(14), dp(12))
            tv.isClickable = true
            tv.isFocusable = true
            fx(tv)
            tv.setOnClickListener {
                filterVoiceId = id
                historyExpanded = false
                renderHistory()
                pop?.dismiss()
            }
            listBox.addView(tv)
        }

        addRow("全部音色（显示所有记录）", "", null)
        // 只显示音色名（ID 尾巴去掉——要看 ID 去设置页的「音色管理」）
        for (id in ids) addRow(voiceNameOf(id), "", id)

        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.addView(listBox)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Skin.shapeDp(this, c.bg, c.line, 12f)
        panel.setPadding(dp(6), dp(6), dp(6), dp(6))
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
            toast("请先在「设置 → 模型」填写该厂商的 API Key")
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
        tvTip.text = "样本要求：干净人声、无背景音乐/杂音；可一次选多个文件（数量不限，App 自动合并）；支持 wav / mp3 / m4a 等。"
        tvTip.setTextColor(cDim)
        tvTip.textSize = 12f
        box.addView(tvTip)

        val btnPick = smallBtn("选择音频文件…（可多选）")
        fx(btnPick)
        btnPick.setOnClickListener { pickAudioFile() }
        val rowPick = LinearLayout(this)
        rowPick.orientation = LinearLayout.HORIZONTAL
        rowPick.setPadding(0, dp(8), 0, 0)
        rowPick.addView(btnPick)
        box.addView(rowPick)

        val tvFile = TextView(this)
        tvFile.text = "未选择样本"
        tvFile.setTextColor(cDim)
        tvFile.textSize = 12f
        tvFile.setPadding(0, dp(4), 0, 0)
        box.addView(tvFile)

        box.addView(labelView("前缀（用于生成音色 ID；1~10 位小写字母/数字）"))
        val etPrefix = EditText(this)
        etPrefix.hint = "如 ailin2"
        etPrefix.inputType = InputType.TYPE_CLASS_TEXT
        etPrefix.setTextColor(cTxt)
        etPrefix.setHintTextColor(cDim)
        etPrefix.textSize = 14f
        etPrefix.filters = arrayOf(InputFilter.LengthFilter(10))
        box.addView(etPrefix)

        box.addView(labelView("显示名称（留空则用前缀）"))
        val etName = EditText(this)
        etName.hint = "如：艾丽妮（新版）"
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
            .setTitle("建音色（声音复刻）")
            .setView(sc)
            .setPositiveButton("创建", null)
            .setNegativeButton("取消", null)
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
            toast("没有可用的文件选择器")
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
                    toast("已保存：" + f.name)
                } catch (e: Exception) {
                    toast("保存失败：" + e.message)
                }
            }
            pendingSaveFile = null
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
        createStatus?.text = "合并中…（共 " + uris.size + " 个文件）"
        Thread {
            try {
                val dst = File(cacheDir, "voice_merged_" + System.currentTimeMillis() + ".wav")
                val r = AudioMerge.merge(this, uris, dst) { i, n ->
                    ui { createStatus?.text = "合并中… " + i + "/" + n }
                }
                if (r == null || r.ok == 0) {
                    dst.delete()
                    ui { createStatus?.text = "❌ 合并失败：没有可解析的音频文件（支持 wav / mp3 / m4a 等）" }
                } else {
                    pendingSample = dst
                    pendingSampleDurMs = r.durMs
                    pendingSampleMime = "audio/wav"
                    // 逐条时长放后台算（原来在主线程里对每个文件解一次，文件多会卡）
                    val durs = LongArray(uris.size) { probeUriDuration(uris[it]) }
                    ui {
                        val failNote = if (r.fail > 0) "（" + r.fail + " 个无法解析，已跳过）" else ""
                        val each = StringBuilder()
                        for (i in uris.indices) {
                            val d = durs[i]
                            if (i > 0) each.append("   ")
                            each.append("[").append(i + 1).append("] ").append(if (d > 0) fmtDur(d) else "?")
                        }
                        createFileTv?.text = "已合并 " + r.ok + " 个文件" + failNote + " · 合计 " +
                                fmtDur(r.durMs) + " · " + (dst.length() / 1024) + " KB\n" + each
                        createStatus?.text = "✅ 素材就绪"
                        if ((createPrefixEt?.text?.toString() ?: "").isBlank()) {
                            createPrefixEt?.setText(suggestPrefix(displayNameOf(uris[0]) ?: "merged"))
                        }
                    }
                }
            } catch (e: Exception) {
                ui { createStatus?.text = "❌ 合并失败：" + (e.message ?: "") }
            }
        }.start()
    }

    private fun importSample(uri: Uri) {
        createStatus?.text = "读取样本中…"
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
                val ins = contentResolver.openInputStream(uri) ?: throw Exception("无法打开所选文件")
                ins.use { input ->
                    dst.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_SAMPLE_BYTES) {
                                throw Exception("文件过大（超过 " + (MAX_SAMPLE_BYTES / 1024 / 1024) + " MB）")
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
                    createFileTv?.text = dn + " · " + (if (durMs > 0) fmtDur(durMs) else "时长未知") +
                            " · " + (dst.length() / 1024) + " KB"
                    createStatus?.text = "✅ 素材就绪"
                    if ((createPrefixEt?.text?.toString() ?: "").isBlank()) {
                        createPrefixEt?.setText(suggestPrefix(dn))
                    }
                }
            } catch (e: Exception) {
                tmp?.delete()
                pendingSample = null
                ui { createStatus?.text = "❌ 读取失败：" + (e.message ?: "") }
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
            createStatus?.text = "❌ 请先选择音频样本"
            return
        }
        if (!sample.exists()) {
            createStatus?.text = "❌ 样本文件不存在，请重新选择"
            return
        }
        if (pendingSampleDurMs > 300000) {
            createStatus?.text = "❌ 样本超过 5 分钟，请减少素材后再来"
            return
        }
        val prefix = (createPrefixEt?.text?.toString() ?: "").trim().lowercase(Locale.US)
        val nonBailian = TtsProviders.byId(store.providerId)?.id != "aliyun-bailian"
        if (!nonBailian && !Regex("^[a-z0-9]{1,10}$").matches(prefix)) {
            createStatus?.text = "❌ 前缀需为 1~10 位小写字母/数字"
            return
        }
        val name = (createNameEt?.text?.toString() ?: "").trim().ifEmpty { prefix }
        val key = cloneKey()
        if (key.isEmpty()) {
            createStatus?.text = "❌ 请先在「设置 → 模型」填写该厂商的 API Key"
            return
        }
        val ws = store.workspace.trim().ifEmpty { "ws-9y8n1gp7w6pg23tv" }
        val model = store.lastModel.trim().ifEmpty { "cosyvoice-v3.5-plus" }
        val posBtn = createDlg?.getButton(DialogInterface.BUTTON_POSITIVE)
        posBtn?.isEnabled = false
        createStatus?.text = "编码样本…"

        // 复刻可能要好几十秒：前台服务把进程钉住，切后台也不断
        KeepAliveService.start(this, "正在克隆音色…（可以切到后台等）")

        Thread {
            try {
                // 非百炼：目前只有 Fish Audio 支持复刻（multipart 上传参考音频；不用前缀）
                if (nonBailian) {
                    val vid = ExtraTts.fishCreateVoice(
                        store.providerBaseUrl(store.providerId),
                        store.providerKey(store.providerId).ifBlank { store.apiKey },
                        sample.readBytes(),
                        name.ifBlank { "自建音色" },
                        "private"
                    )
                    ui {
                        customVoices.add(CustomVoice(vid, name, "", System.currentTimeMillis()))
                        store.saveCustomVoices(customVoices)
                        setVoice(vid)
                        syncVoiceUi()
                        if (createDlg?.isShowing == true) createDlg?.dismiss()
                        KeepAliveService.stop(this)
                        toast("音色已创建（Fish Audio）")
                    }
                    return@Thread
                }

                val b64 = Base64.encodeToString(sample.readBytes(), Base64.NO_WRAP)
                val dataUri = "data:" + pendingSampleMime + ";base64," + b64
                ui { createStatus?.text = "上传创建中…（几秒到几十秒）" }
                client.createVoice(key, ws, model, prefix, dataUri, onResult = { vid ->
                    ui {
                        customVoices.add(CustomVoice(vid, name, prefix, System.currentTimeMillis()))
                        store.saveCustomVoices(customVoices)
                        setVoice(vid)
                        syncVoiceUi()
                        if (createDlg?.isShowing == true) createDlg?.dismiss()
                        KeepAliveService.stop(this)
                        toast("✅ 音色已创建：" + name)
                        tvStatus.text = "✅ 音色已创建并选中：" + name
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
                    createStatus?.text = "❌ " + (e.message ?: "创建失败")
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
    private fun glassBg(): android.graphics.drawable.Drawable {
        val c = Skin.colors(this)
        val r = (Palettes.byId(store.themeId)?.btnRadius ?: 11).toFloat()
        val face = (c.acc and 0x00FFFFFF) or (0x29 shl 24)     // ~16%
        val stroke = (c.acc and 0x00FFFFFF) or (0x6B shl 24)   // ~42%
        return Skin.shapeDp(this, face, stroke, r, 100, 1.5f)
    }

    private fun smallBtn(label: String): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(cTxt)
        tv.textSize = 13f
        tv.background = glassBg()
        tv.tag = "bg:keep"          // 玻璃底的圆角 == 主题 btnRadius，会撞上 Skin 的魔法半径 → 明确声明别动
        tv.setPadding(dp(13), dp(8), dp(13), dp(8))
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
        if (ms <= 0) "未知" else String.format(Locale.US, "%.1f 秒", ms / 1000.0)

    /** 崩溃兜底：把未捕获异常写到本机，下次进来自动提示"可复制" */
    private fun installCrashGuard() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(filesDir, "crash.txt").writeText(
                    "时间：" + java.util.Date() + "\n" + android.util.Log.getStackTraceString(e)
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
            .setTitle("上次运行时出了点问题")
            .setMessage(text.take(600))
            .setPositiveButton("复制错误信息") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                toast("已复制，可直接发给我")
            }
            .setNegativeButton("知道了", null)
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
        Thread {
            val work = takes.toMutableList()
            var fixed = false
            for (i in work.indices) {
                val t = work[i]
                if (!t.format.startsWith("wav")) continue
                val f = store.fileOf(t)
                if (!f.exists()) continue
                fixWavHeader(f)
                val d = probeDurationMs(f)
                if (d > 0 && d != t.durationMs) {
                    work[i] = t.copy(durationMs = d)
                    fixed = true
                }
            }
            if (fixed) {
                store.saveTakes(work)
                ui {
                    takes.clear()
                    takes.addAll(work)
                    renderHistory()
                }
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
    }
}
