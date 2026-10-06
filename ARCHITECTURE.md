# 玲珑调音 · Prima Tune —— 代码结构（ARCHITECTURE）

> **给"下一个 AI / 下一个开发者"的地图**：读它 5 分钟，胜过通读 1 万行。
> 只记**结构与约定**；设计与数值见《夕汀前端规范》。**别写成变更日志**（只留最新）。

## 一句话
Android 原生（**Kotlin + View，无 Compose**）。**"轻内核、重前端"的壳应用**：核心能力（TTS / LLM）在云上，App 主要负责 **UI + 状态 + 各家接口适配**。

## 技术栈
- Kotlin 2.0.21 ｜ AGP 8.7.3 ｜ Gradle 8.9 ｜ JDK 17(CI) / 21(本机)
- `minSdk 26` · `targetSdk 34` · `compileSdk 35`
- 依赖**仅四件**：`core-ktx` / `appcompat` / `activity-ktx` / `okhttp`。**无 Compose / 无 DI / 无 ViewModel / 无协程依赖**
- 单 module `:app`，包名 `com.sideroca.voicetuner`

## 文件地图（`app/src/main/java/com/sideroca/voicetuner/`，平铺）

| 组 | 文件 | 职责 |
|---|---|---|
| 入口 / 页面 | `MainActivity`(3.1k 行) · `SettingsActivity`(2.2k) · `CropActivity` · `WelcomeActivity` | 界面与流程（**god-activity**） |
| 主题 / 自绘 | `Skin` · `Palettes` · `TapFx` · `PressButton` · `StarView` · `VoiceIndicatorView` · `EqBarsView` | 换肤引擎与自绘控件 |
| 存储 | `Store` | SharedPreferences + JSON 文件（**唯一数据源**） |
| 网络 | `DashScopeClient`(WebSocket) · `QwenTtsClient` · `OpenAiCompatTts` · `ExtraTts` · `ExtraTts2` · `TransClient` · `LlmClient` · `TtsProviders` · `TtsModels` · `LlmPresets` | 各家 TTS / LLM / 翻译适配 |
| 音频 / 图片 | `AudioMerge` · `Wp` · `CropView` · `IconStyles` | 样本合并、壁纸、取景、图标合成 |
| 工具 | `Loc` · `Clip` · `Err` · `Ratchet` · `LayoutFix` · `ContainsAdapter` · `UrlAdapter` · `AudioFocus` · `KeepAliveService` · `VoiceCatalog` | 语言、剪贴板、报错人话、棘轮滑条、适配器等 |

## 关键机制（**改代码前必读**）

- **换肤引擎 `Skin`**：换主题＝换 **11 个语义槽**的值。`Skin.apply(root, colors)` 遍历视图树重绘。
  - 识别"角色"两条路：① **显式**（`Skin.setRole(v,"txt")`，或让控件实现 **`Themed`** 接口）② 推断（看文字原色常量 / `cornerRadius` 魔数）——**新代码请走①**。
  - 页面代码打 `v.tag = "bg:keep"` ＝"**别动我的底色**"。
  - ⚠️ **换肤必须重刷**：代码建的控件 · `bg:keep` · 点按水波 · 星点留痕 · 下拉弹层——否则会"**停在旧色**"。
- **数据流**：`Store`（唯一数据源）→ 页面 `onResume` **整表重载**；跨页交接用 `Store.pendingFillId` + Intent。
- **网络**：每次请求 `Thread{}` + 回调（`SynthCallback`）+ 自研 `Cancellable`（**未用协程**）；回主线程用 `ui{}`。
- **厂商分派**：`generate()` 按 `TtsProviders.byId(id).shape` 分支到不同 client；参数能不能用由 `TtsModels.supports()` **数据驱动置灰**（不在界面手写）。
- **多语言**：`Loc`（AppCompat **per-app locale**）+ `Store.lang`；文案在 `res/values*/strings.xml`（默认中文）。首次安装走 `WelcomeActivity` 选语言。

## 存储与文件
- 偏好：`SharedPreferences("vt")`（见 `Store`）；语言键 `lang`；主题 / 壁纸 / 厂商 Key 等。
- 记录：`filesDir/history/index.json`（**原子写 + `.bak` 兜底**）；音频 `filesDir/history/vt_*.wav|mp3`。
- 自建音色 `filesDir/custom_voices.json`；壁纸 `filesDir/wp_*.jpg`（含 `_src` 原图）。

## 已知技术债（下一批可做）
1. 两个 **god-activity**（3.1k / 2.2k 行）→ 抽 `RecordRowView`、抽 `TtsEngine` 接口 + 注册表；
2. `Skin` 的"角色推断"脆弱 → 新控件走 `Themed` / `setRole`；
3. **无协程** → 取消 / 生命周期靠手写，可逐步替换；
4. **语言名 / 指令芯片是"数据即文案"**，未本地化（要翻得先把存值改成"语言代码"）。

## 出包 / 交付
- 本地编译：`/workspace/.tools/gradle-8.9/bin/gradle assembleDebug`
- 固定直链：`bash /workspace/工作区信息/publish_apk.sh . nightly PrimaTune.apk`
- **版本号四处同步**：`build.gradle` · 主页页脚（`activity_main.xml`）· 设置页「关于」（**动态读取**，无需改）· `README.md`
