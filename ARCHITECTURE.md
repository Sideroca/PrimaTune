# 玲珑调音 · Prima Tune —— 代码结构（ARCHITECTURE）

> **给"下一个 AI / 下一个开发者"的地图**：读它 5 分钟，胜过通读 1 万行。
> 只记**结构与约定**；设计与数值见《夕汀前端规范》。**别写成变更日志**（只留最新）。

## 一句话
Android 原生（**Kotlin + View，无 Compose**）。**"轻内核、重前端"的壳应用**：核心能力（TTS / LLM）在云上，App 主要负责 **UI + 状态 + 各家接口适配**。

## 相关文档
- **`TTS-PROVIDERS.md`**：各家 TTS 的端点 / 模型 / 参数 / seed 支持 / 坑（**接新厂商、判断骰子先看它**）
- **完整交接/模块地图（工作区）**：`../玲珑调音/文档/项目结构与交接-2026-10-21.md`
- `README.md`：用户向说明 + 版本历史
- `ARCHITECTURE.md`：本文，代码结构地图

## 技术栈
- Kotlin 2.0.21 ｜ AGP 8.7.3 ｜ Gradle 8.9 ｜ JDK 17(CI) / 21(本机)
- `minSdk 26` · `targetSdk 34` · `compileSdk 35`
- 依赖**仅四件**：`core-ktx` / `appcompat` / `activity-ktx` / `okhttp`。**无 Compose / 无 DI / 无 ViewModel / 无协程依赖**
- 单 module `:app`，包名 `com.sideroca.voicetuner`

## 文件地图（`app/src/main/java/com/sideroca/voicetuner/`，平铺）

| 组 | 文件 | 职责 |
|---|---|---|
| 入口 / 页面 | `MainActivity`(3.8k 行) · `SettingsActivity`(3.2k) · `CropActivity` · `WelcomeActivity` | 界面与流程（**god-activity**） |
| 主题 / 自绘 | `Skin` · `Palettes` · `TapFx` · `PressButton` · `StarView` · `VoiceIndicatorView` · `EqBarsView` | 换肤引擎与自绘控件 |
| 存储 / 备份 | `Store` · `Backup` · `TimeFmt` | SharedPreferences + JSON（**唯一数据源**）；zip 备份/恢复；统一时间格式 |
| 网络 | `DashScopeClient`(WebSocket) · `QwenTtsClient` · `OpenAiCompatTts` · `ExtraTts` · `ExtraTts2` · `TransClient` · `LlmClient` · `TtsProviders` · `TtsModels` · `TtsCaps` · `LlmPresets` | 各家 TTS / LLM / 翻译适配；**参数能力表** |
| 音频 / 图片 | `AudioMerge` · `Wp` · `CropView` · `IconStyles` | 样本合并、壁纸、取景、图标合成 |
| **AI 闹钟** | `Alarm`(配置) · `AlarmScheduler`(精确闹钟) · `AlarmReceiver` · `AlarmService`(预生成+播放) · `AlarmRingActivity`(全屏响铃页) | 提前 2 分钟预生成；到点优先播匹配缓存；无缓存现场生成、完成即播；**不再用系统铃声兜底**；全屏响铃页 |
| 播放 / 保活 | `AudioFocus` · `KeepAliveService` | App 内单路播放仲裁（带 token）；dataSync 前台服务 + WakeLock |
| 工具 | `Loc` · `Clip` · `Err` · `Ratchet` · `LayoutFix` · `ContainsAdapter` · `UrlAdapter` · `VoiceCatalog` | 语言、剪贴板、报错人话、棘轮滑条、适配器等 |

## 关键机制（**改代码前必读**）

- **换肤引擎 `Skin`**：换主题＝换 **11 个语义槽**的值。`Skin.apply(root, colors)` 遍历视图树重绘。
  - 识别"角色"两条路：① **显式**（`Skin.setRole(v,"txt")`，或让控件实现 **`Themed`** 接口）② 推断（看文字原色常量 / `cornerRadius` 魔数）——**新代码请走①**。
  - 页面代码打 `v.tag = "bg:keep"` ＝"**别动我的底色**"。
  - ⚠️ **换肤必须重刷**：代码建的控件 · `bg:keep` · 点按水波 · 星点留痕 · 下拉弹层——否则会"**停在旧色**"。
- **数据流**：`Store`（唯一数据源）→ 页面 `onResume` **整表重载**；跨页交接用 `Store.pendingFillId` + Intent。
- **网络**：每次请求 `Thread{}` + 回调（`SynthCallback`）+ 自研 `Cancellable`（**未用协程**）；回主线程用 `ui{}`。
- **厂商分派**：`generate()` 按 `TtsProviders.byId(id).shape` 分支到不同 client；参数能不能用由 `TtsCaps` **数据驱动置灰**（不在界面手写）。
- **音色 ↔ 厂商**：`VoiceProviders.of(id, customs)` → providerId / `cosyvoice-*` 前缀 / 未知；主页生成与闹钟预生成都会**先校验再发请求**，不匹配直接提示/失败。闹钟音色列表 `voicesForProvider()` 按厂商过滤。
- **播放**：`AudioFocus` 是 App 内唯一仲裁；新播放先回调上一个持有者 stop；带 token 防旧 completion 误停新播放。`KeepAliveService` 用 dataSync 前台服务 + WakeLock 保后台生成；TTS 请求并发：主页单请求、试听在主生成期间不发 TTS、闹钟预生成最多 3 条并行。
- **记录**：正文默认 4 行；无保存上限；分页只影响渲染；筛选按「内置目录 → 自建创建顺序 → 已删除但仍有记录」；选中音色失效自动清空。
- **快删**：多译版同版删 2 字 → 整版删除，光标回开头防连删；风格指令 2s 内删 3 字 → 整框清空（1~2 字为微调）。
- **备份**：`Backup.kt` zip = 记录/音频/自建音色/收藏/试听模板 + manifest；**不含 API Key**；恢复只增不删；设置记录页底部显示记录数/占用/上次备份。
- **多语言**：`Loc`（AppCompat **per-app locale**）+ `Store.lang`；文案在 `res/values*/strings.xml`（默认中文）。首次安装走 `WelcomeActivity` 选语言。

## 存储与文件
- 偏好：`SharedPreferences("vt")`（见 `Store`）；语言键 `lang`；主题 / 壁纸 / 厂商 Key 等。
- 记录：`filesDir/history/index.json`（**原子写 + `.bak` 兜底**）；音频 `filesDir/history/vt_*.wav|mp3`。
- 时间：记录只存 epoch ms（`Take.createdAt`）；显示统一走 `TimeFmt.record()`（**当年** `MM-dd HH:mm`，**非当年** `yyyy-MM-dd HH:mm`）；日志走 `TimeFmt.log()`（固定带年）。**不要**在页面里手写 `SimpleDateFormat("MM-dd HH:mm")`，否则跨年会歧义。
- 自建音色 `filesDir/custom_voices.json`；试听模板 `filesDir/voice_previews/<hash>.<ext>`；壁纸 `filesDir/wp_*.jpg`（含 `_src` 原图）。
- 备份：`lastBackupAt` 存偏好；zip 由 `Backup.kt` 读写，`previews/` 存试听模板。

## 已知技术债（下一批可做）
1. 两个 **god-activity**（3.8k / 3.2k 行）→ 抽 `RecordRowView`、抽 `TtsEngine` 接口 + 注册表；
2. `Skin` 的"角色推断"脆弱 → 新控件走 `Themed` / `setRole`；
3. **无协程** → 取消 / 生命周期靠手写，可逐步替换；
4. **语言名 / 指令芯片是"数据即文案"**，未本地化（要翻得先把存值改成"语言代码"）；
5. Groq rate（0.5~5）/ Gemini TTS seed（0~20）未放行；需先扩 UI 动态范围/确认官方文档，详见 `TTS-PROVIDERS.md`；
6. 4 行正文让记录卡变高；记录很多时注意首屏建卡性能。

## 出包 / 交付
- 本地 release：`bash tools/build_release.sh /workspace/dl`（预检 + 完整日志 + 真实退出码；失败不发布）
- 调试：`/workspace/.tools/gradle-8.9/bin/gradle assembleDebug`
- 固定直链：`https://github.com/Sideroca/PrimaTune/releases/latest/download/PrimaTune.apk`
- **版本号同步**：`build.gradle` · 主页页脚（`activity_main.xml`）· 设置页「关于」（**动态读取**，无需改）· `README.md`
