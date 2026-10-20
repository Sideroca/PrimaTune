# 玲珑调音 · TTS 厂商与模型手册

> **最后核对：2026-10-20**
>
> 目的：接新厂商、排查参数、判断某个模型能不能用「骰子（seed）」时，**先看这份**，不要重新全网搜。
>
> ⚠️ 官方 API 会变。**代码是最终执行事实**：与本文冲突时，以
> `TtsProviders.kt` / `TtsModels.kt` / `TtsCaps.kt` / 各 client 为准，并回来改本文。
>
> 相关代码：
> - `TtsProviders.kt`：厂商目录（Base URL、shape、模型清单、输出格式）
> - `TtsModels.kt`：百炼模型目录（含 ws/http transport）
> - `TtsCaps.kt`：**(厂商, 模型) → 支持参数** 的唯一数据源；界面置灰由它决定
> - `Synth.kt`：按 shape 分派到各 client
> - `DashScopeClient.kt` / `QwenTtsClient.kt` / `OpenAiCompatTts.kt` / `ExtraTts.kt` / `ExtraTts2.kt`：各协议实现
> - `Err.kt`：厂商原始报错 → 人话

---

## 0. 一页速查

| 厂商 `id` | 显示名 | shape | Base URL | 鉴权 | 模型 | App 输出格式 | seed |
|---|---|---|---|---|---|---|---|
| `aliyun-bailian` | 阿里云百炼 | `dashscope` | `https://dashscope.aliyuncs.com` | `Authorization: Bearer` | 10 个（见 3.1） | wav24/48/16 · mp3_256/128 | ws 模型 ✅；http 模型 ❌ |
| `openai` | OpenAI | `openai` | `https://api.openai.com/v1` | `Authorization: Bearer` | `gpt-4o-mini-tts` | mp3_256/128 | ❌ |
| `groq` | Groq | `openai` | `https://api.groq.com/openai/v1` | `Authorization: Bearer` | Orpheus ×2 | wav24 · mp3_256/128 | ❌ |
| `step` | Step 阶跃星辰 | `openai` | `https://api.stepfun.com/v1` | `Authorization: Bearer` | stepaudio-2.5-tts / step-tts-mini | mp3_256/128 | ❌ |
| `xai` | xAI | `xai` | `https://api.x.ai/v1` | `Authorization: Bearer` | grok-tts | mp3_256/128 | ❌ |
| `elevenlabs` | ElevenLabs | `elevenlabs` | `https://api.elevenlabs.io` | `xi-api-key` | eleven_v4 / v4_turbo / v3 | mp3（客户端实际返回 PCM→WAV） | ✅ |
| `minimax` | MiniMax | `minimax` | `https://api.minimax.io/v1` | `Authorization: Bearer` | speech-2.8/2.6 hd/turbo | mp3 | ❌ |
| `fish` | Fish Audio | `fish` | `https://api.fish.audio` | `Authorization: Bearer` + `model` header | s2.1-pro-free / s2.1-pro / s2-pro / s1 | wav / mp3 | ✅（用户实测） |
| `gemini` | Gemini | `gemini` | `https://generativelanguage.googleapis.com` | `?key=` | gemini-3.8-flash-tts 等 4 个 | wav24/48/16 | ⚠️ 有 seed 字段（0–20），TTS 文档未列为参数 → 未放行 |
| `mimo` | MiMo 小米 | `mimo` | `https://api.xiaomimimo.com/v1` | `api-key`（不是 Bearer） | mimo-v2.5-tts ×3 | wav24/48/16 | ❌（文档未列） |
| `index-tts` | B站 IndexTTS | `openai` | 用户填（预设 `https://api.siliconflow.cn/v1`） | `Authorization: Bearer` | `IndexTeam/IndexTTS-2` | mp3_256/128 | ❌ |
| `system` | 系统 TTS（本地） | `system` | — | 无需 Key | 系统引擎 | wav24 | ❌ |
| `custom` | 自定义渠道 | `openai` | 用户填 | 用户配置（默认 Bearer） | 用户填 | 全部 | ✅（透传，服务认就用） |

> **seed 的界面逻辑**：`TtsModels.supports(providerId, modelId, "seed")` → `TtsCaps`。
> 置灰只代表界面不允许填；`SynthRequest.seed` 本身始终有值，**只有 client 真的读取它才会发出去**。
> 因此不要“只给 TtsCaps 加 SEED”而不改 client。

---

## 1. 参数键与含义

| 键 | 界面 | 含义 / 备注 |
|---|---|---|
| `rate` | 语速滑条 | App 范围 0.5–2.0，默认 1.0；各家量程不同，client 内会 clamp |
| `pitch` | 音调滑条 | App 范围 0.5–2.0；百炼直接发倍率，MiniMax 转半音，系统 TTS 用 `setPitch` |
| `volume` | 音量滑条 | App 0–100；各家映射不同（如 Fish −20~20、MiniMax (0,10]） |
| `seed` | 骰子 / 种子 | App 生成 0–65535；只有支持的厂商才发 |
| `instruction` | 情绪 / 风格指令 | 自然语言；不同厂商字段名/位置不同（见各节） |
| `langhints` | 语言提示 | 百炼 `language_hints` / `language_type`；xAI 目前固定 `auto` |
| `hotfix` | 发音纠错表 | 百炼 ws 专用 `hot_fix` |
| `extra` | 额外参数（JSON） | 按厂商合并进请求体；**不覆盖**已有字段 |
| `ssml` | SSML 开关 | 百炼 ws 专用 `enable_ssml` |

### 1.1 App 输出格式

| key | 容器 | 采样率 | 备注 |
|---|---|---|---|
| `wav24` | WAV | 24000 | 推荐 |
| `wav48` | WAV | 48000 | |
| `wav16` | WAV | 16000 | |
| `mp3_256` | MP3 | 24000 | 实际码率由厂商决定 |
| `mp3_128` | MP3 | 16000 | |

`TtsProviders.formats` 会按厂商过滤；**不可能选的格式不出现在下拉里**。

---

## 2. 全模型参数矩阵

> ✅ = `TtsCaps` 放行；— = 未放行（界面置灰）。
> `extra` 基本所有厂商都放行（用于 temperature/top_p/pronunciation 等厂商扩展）。

### 2.1 阿里云百炼

| 模型 | transport | rate | pitch | vol | seed | instr | lang | hotfix | extra | ssml |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `cosyvoice-v3.5-plus` | ws | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `cosyvoice-v3-flash` | ws | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `qwen-audio-3.1-tts-flash` | ws | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `qwen-audio-3.0-tts-plus` | ws | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `qwen3-tts-flash` | http | — | — | — | — | — | ✅ | — | ✅ | — |
| `qwen3-tts-flash-2025-11-27` | http | — | — | — | — | — | ✅ | — | ✅ | — |
| `qwen3-tts-instruct-flash` | http | — | — | — | — | ✅ | ✅ | — | ✅ | — |
| `qwen3-tts-instruct-flash-2026-01-26` | http | — | — | — | — | ✅ | ✅ | — | ✅ | — |
| `qwen-tts-latest` | http | — | — | — | — | — | ✅ | — | ✅ | — |
| `qwen-tts` | http | — | — | — | — | — | ✅ | — | ✅ | — |

### 2.2 其他厂商

| 厂商 / 模型 | rate | pitch | vol | seed | instr | lang | hotfix | extra | ssml |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| OpenAI `gpt-4o-mini-tts` | ✅ | — | — | — | ✅ | — | — | ✅ | — |
| Groq `orpheus-v1-english` | — | — | — | — | ✅ | — | — | ✅ | — |
| Groq `orpheus-arabic-saudi` | — | — | — | — | — | — | — | ✅ | — |
| Step `stepaudio-2.5-tts` | ✅ | — | ✅ | — | ✅ | ✅ | — | ✅ | — |
| Step `step-tts-mini` | ✅ | — | ✅ | — | ✅ | ✅ | — | ✅ | — |
| xAI `grok-tts` | — | — | — | — | — | ✅* | — | ✅ | — |
| ElevenLabs `eleven_v3` | — | — | — | ✅ | ✅ | — | — | ✅ | — |
| ElevenLabs `eleven_v4` | ✅ | — | — | ✅ | ✅ | — | — | ✅ | — |
| ElevenLabs `eleven_v4_turbo` | ✅ | — | — | ✅ | ✅ | — | — | ✅ | — |
| MiniMax `speech-2.8-hd` | ✅ | ✅ | ✅ | — | ✅ | — | — | ✅ | — |
| MiniMax `speech-2.8-turbo` | ✅ | ✅ | ✅ | — | ✅ | — | — | ✅ | — |
| MiniMax `speech-2.6-hd` | ✅ | ✅ | ✅ | — | ✅ | — | — | ✅ | — |
| MiniMax `speech-2.6-turbo` | ✅ | ✅ | ✅ | — | ✅ | — | — | ✅ | — |
| Fish `s2.1-pro-free` | ✅ | — | ✅ | ✅ | ✅ | — | — | ✅ | — |
| Fish `s2.1-pro` | ✅ | — | ✅ | ✅ | ✅ | — | — | ✅ | — |
| Fish `s2-pro` | ✅ | — | ✅ | ✅ | ✅ | — | — | ✅ | — |
| Fish `s1` | ✅ | — | ✅ | ✅ | ✅ | — | — | ✅ | — |
| Gemini `gemini-3.8-flash-tts` | — | — | — | — | ✅ | — | — | ✅ | — |
| Gemini `gemini-3.8-flash-lite-tts` | — | — | — | — | ✅ | — | — | ✅ | — |
| Gemini `gemini-2.5-pro-preview-tts` | — | — | — | — | ✅ | — | — | ✅ | — |
| Gemini `gemini-2.5-flash-preview-tts` | — | — | — | — | ✅ | — | — | ✅ | — |
| MiMo `mimo-v2.5-tts` | — | — | — | — | ✅ | — | — | ✅ | — |
| MiMo `mimo-v2.5-tts-voiceclone` | — | — | — | — | ✅ | — | — | ✅ | — |
| MiMo `mimo-v2.5-tts-voicedesign` | — | — | — | — | ✅ | — | — | ✅ | — |
| IndexTTS `IndexTeam/IndexTTS-2` | ✅ | — | — | — | — | — | — | ✅ | — |
| 系统 TTS `系统引擎` | ✅ | ✅ | — | — | — | — | — | — | — |
| 自定义渠道 | 用户服务决定 | | | ✅ | 用户服务决定 | | | ✅ | |

\* xAI：`TtsCaps` 放行 `langhints`，但当前 client 固定发 `language: "auto"`，语言提示实际未映射（见 3.5）。

---

## 3. 厂商详情

### 3.1 阿里云百炼 `aliyun-bailian`

- **Base URL**：`https://dashscope.aliyuncs.com`
- **鉴权**：`Authorization: Bearer <API Key>`
- **shape**：`dashscope`
- **复刻**：支持（`canClone=true`）；内置音色 4 个在 `VoiceCatalog.kt`；复刻 ID 以 `cosyvoice-` 开头。

#### ws 系（CosyVoice / Qwen-Audio-TTS）

- **URL**：`wss://{workspace}.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference`
- **workspace 规则**：`^[a-z0-9-]+$`；App 默认 `ws-9y8n1gp7w6pg23tv`
- **消息流程**：
  1. `run-task`（含 voice / format / sample_rate / rate / pitch / volume / seed / hot_fix / instruction 等）
  2. `task-started`
  3. `continue-task`（整段文本）
  4. `finish-task`
  5. 二进制音频流
  6. `task-finished`
- **seed**：发在 `parameters.seed`。
- **格式**：wav24/48/16、mp3_256/128。
- **注意**：
  - `cosyvoice-*` 音色只能配 CosyVoice 模型；用 Qwen-TTS 模型会失败。
  - `hot_fix`、`enable_ssml` 只在 ws 系有效。
  - 克隆音色与模型版本绑定；换模型前先试听/试合成。

#### http 系（Qwen3-TTS / Qwen-TTS）

- **URL**：`POST https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`
- **body**：`{model, input:{text, voice, language_type, instructions?, optimize_instructions?}}`
- **返回**：`output.audio.data`（base64；可能是 WAV，也可能是裸 PCM → 本地套 44 字节 WAV 头）
- **没有** rate / pitch / volume / seed；`qwen3-tts-instruct-flash` 才支持 `instructions`。
- **语言**：`language_type` 由界面语言提示映射为 `Chinese / English / Japanese / Korean / Auto`。

#### 模型清单

| 模型 | transport | 说明 |
|---|---|---|
| `cosyvoice-v3.5-plus` | ws | 现用复刻音色；参数最全 |
| `cosyvoice-v3-flash` | ws | 快速版；参数同上 |
| `qwen-audio-3.1-tts-flash` | ws | Qwen-Audio-TTS 3.1 快速版；参数齐全 |
| `qwen-audio-3.0-tts-plus` | ws | Qwen-Audio-TTS 3.0 高质版；参数齐全 |
| `qwen3-tts-flash` | http | 稳定版（= 2025-11-27 快照） |
| `qwen3-tts-flash-2025-11-27` | http | 固定快照 |
| `qwen3-tts-instruct-flash` | http | 支持 instructions 风格指令 |
| `qwen3-tts-instruct-flash-2026-01-26` | http | 固定快照 |
| `qwen-tts-latest` | http | 最新（= 2025-05-22） |
| `qwen-tts` | http | 稳定（= 2025-04-10） |

---

### 3.2 OpenAI `openai`

- **Base URL**：`https://api.openai.com/v1`
- **端点**：`POST {base}/audio/speech`
- **鉴权**：`Authorization: Bearer <Key>`
- **模型**：`gpt-4o-mini-tts`（`tts-1` / `tts-1-hd` 属旧代，未列入）
- **body**：`model / input / voice / response_format / instructions? / speed?`
- **参数**：`instruction`→`instructions`；`rate`→`speed`（App 0.5–2.0）；无 seed。
- **格式**：mp3_256/128（App 侧）。
- **注意**：
  - voice 可填任意官方音色 ID/名称；`alloy` 为兜底。
  - 官方 speed 0.25–4；App 只开放 0.5–2.0。
  - 无 seed；界面骰子应保持置灰。

---

### 3.3 Groq `groq`

- **Base URL**：`https://api.groq.com/openai/v1`
- **端点**：`POST {base}/audio/speech`
- **鉴权**：`Authorization: Bearer <Key>`
- **模型**：
  - `canopylabs/orpheus-v1-english`：支持 `[cheerful]` 等 vocal directions（写在文本里）
  - `canopylabs/orpheus-arabic-saudi`：不支持 vocal directions
- **参数**：英文模型 `instruction` 放行（实际由文本标签表达）；无 rate/pitch/volume/seed。
- **格式**：wav24、mp3_256/128。
- **注意**：旧的 `playai-tts` 已不在官方在售清单，App 已移除。

---

### 3.4 StepFun 阶跃星辰 `step`

- **Base URL**：`https://api.stepfun.com/v1`
- **端点**：`POST {base}/audio/speech`
- **鉴权**：`Authorization: Bearer <Key>`
- **模型**：`stepaudio-2.5-tts`、`step-tts-mini`
- **body**：`model / voice / input / response_format / sample_rate`
- **扩展参数（extra_body）**：
  - `volume`：1.0 = 正常；App 音量 50 → 1.0
  - `instruction`：全局风格指令，≤200 字（注意字段名是 `instruction`，不是 `instructions`）
  - `text_normalization` / `pronunciation_map` / `voice_label` 等
- **参数**：rate / volume / instruction / langhints；无 seed。
- **格式**：mp3_256/128。
- **注意**：`input` 里的 `(括号内容)` 会被当作 inline 指令，不朗读；与全局 `instruction` 是两层控制。

---

### 3.5 xAI `xai`

- **Base URL**：`https://api.x.ai/v1`
- **端点**：`POST {base}/tts`
- **鉴权**：`Authorization: Bearer <Key>`
- **模型**：`grok-tts`（请求体**没有 model 字段**）
- **body**：`text / voice_id / language / codec / sample_rate / bit_rate`
- **参数**：无 rate / pitch / volume / instruction / seed；`langhints` 对应 `language`。
- **App 现状**：client 固定发 `language: "auto"`，所以界面放行的语言提示**实际未映射**；接新语言需求时先改 `OpenAiCompatTts` xai 分支。
- **格式**：mp3_256/128。

---

### 3.6 ElevenLabs `elevenlabs`

- **Base URL**：`https://api.elevenlabs.io`
- **端点**：`POST {base}/v1/text-to-speech/{voice_id}?output_format=pcm_24000`
- **鉴权**：`xi-api-key: <Key>`
- **模型**：`eleven_v4`、`eleven_v4_turbo`、`eleven_v3`
- **body**：
  - `text`
  - `model_id`
  - `seed`（顶层字段）
  - `voice_settings: { stability, similarity_boost, speed? }`
- **参数**：
  - seed ✅
  - v4 / v4_turbo 支持 speed；v3 不支持（界面置灰）
  - App speed clamp 到官方 0.7–1.2
- **格式**：请求 `pcm_24000`，客户端本地套 44 字节 WAV 头；App 下拉显示 mp3 档位。
- **音色兜底**：`21m00Tcm4TlvDq8ikWAM`（官方示例 Rachel）。
- **注意**：ElevenLabs 的 seed 是“尽量确定性”，不保证逐字节一致。

---

### 3.7 MiniMax `minimax`

- **Base URL**：`https://api.minimax.io/v1`
- **端点**：`POST {base}/t2a_v2?GroupId={groupId}`（GroupId 为空则不带）
- **鉴权**：`Authorization: Bearer <Key>`
- **模型**：`speech-2.8-hd`、`speech-2.8-turbo`、`speech-2.6-hd`、`speech-2.6-turbo`
- **body**：
  - `model / text / stream:false`
  - `voice_setting: { voice_id, speed 0.5–2, pitch −12~12 半音, vol (0,10], emotion? }`
  - `audio_setting: { format:"mp3", sample_rate:32000 }`
- **App 映射**：
  - 音量 0–100 → `(vol/50).coerceIn(0.1, 10.0)`
  - 音调倍率 → `(pitch−1)×12` 半音
  - 情绪指令 → 识别 8 种官方 emotion（happy/sad/angry/fearful/disgusted/surprised/calm/fluent/whisper）
- **返回**：`data.audio` 为 **hex** 字符串 → 客户端转字节。
- **参数**：rate / pitch / volume / instruction(emotion)；无 seed。
- **格式**：mp3（客户端固定）。
- **注意**：`GroupId` 复用 App 的 `workspace` 字段；不要和百炼 workspace 混淆。

---

### 3.8 Fish Audio `fish`

- **Base URL**：`https://api.fish.audio`
- **端点**：`POST {base}/v1/tts`
- **鉴权**：`Authorization: Bearer <Key>` + **`model: <模型 id>` header**
- **模型**：`s2.1-pro-free`（免费档，Fair Use）、`s2.1-pro`、`s2-pro`、`s1`
- **body**：
  - `text`
  - `format`: `wav` / `mp3`
  - `reference_id`: 音色 ID
  - `prosody: { speed 0.5–2, volume −20~20 }`
  - `seed`（客户端会发；用户实测支持）
  - `instruction` 不单独存在：客户端把风格指令拼到文本前面——s1 用 `(圆括号)`，s2 系用 `[方括号]`
  - `extra` 可合并 `temperature / top_p / repetition_penalty / normalize` 等
- **参数**：rate / volume / instruction / seed；无 pitch。
- **格式**：wav / mp3。
- **复刻**：`POST {base}/model` multipart（type=tts、title、voices=样本、train_mode=fast、visibility=private）；返回 `_id` 即音色 ID。
- **注意**：
  - **不带 `model` header 会走付费模型并可能 402**；免费档必须显式带 `s2.1-pro-free`。
  - 官方 TTS 文档未列 seed；`/v1/voice-design` 文档有 seed。App 按用户实测放行，若服务端未来拒绝，先查这里。
  - 音色与模型匹配：s1 的表情标签语法与 s2 不同。

---

### 3.9 Gemini `gemini`

- **Base URL**：`https://generativelanguage.googleapis.com`
- **端点**：`POST /v1beta/models/{model}:generateContent?key={API Key}`
- **模型**：
  - `gemini-3.8-flash-tts`
  - `gemini-3.8-flash-lite-tts`
  - `gemini-2.5-pro-preview-tts`
  - `gemini-2.5-flash-preview-tts`
- **body**：
  ```json
  {
    "contents": [{"parts": [{"text": "..."}]}],
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "speechConfig": {
        "voiceConfig": {
          "prebuiltVoiceConfig": {"voiceName": "Kore"}
        }
      }
    }
  }
  ```
- **返回**：`candidates[0].content.parts[0].inlineData.data`（base64；可能是 PCM → 本地套 WAV 头）。
- **参数**：风格通过 prompt / inline tags；无数值 rate/pitch/volume；**TTS 文档未列 seed**。
- **seed 特别说明**：`GenerateContentConfig` 有 `seed`（范围 **0–20**），但 speech-generation 文档没有把它列为 TTS 参数；App 暂不放行，避免“看起来能复现、实际不保证”。如要试，需在 `ExtraTts2.gemini` 里加 `generationConfig.seed` 并把 0–65535 夹到 0–20。
- **格式**：wav24/48/16（App 侧）。
- **音色**：`Kore` 为兜底；可填官方 prebuilt voice 名。

---

### 3.10 MiMo 小米 `mimo`

- **Base URL**：`https://api.xiaomimimo.com/v1`
- **端点**：`POST {base}/chat/completions`
- **鉴权**：**`api-key: <Key>`**（不是 `Authorization: Bearer`）
- **模型**：`mimo-v2.5-tts`、`mimo-v2.5-tts-voiceclone`、`mimo-v2.5-tts-voicedesign`
- **body**：
  - `model`
  - `messages`: `user` = 自然语言风格/人设指令；`assistant` = **待合成文本**（官方要求）
  - `audio: { format:"wav", voice:"..." }`
  - `seed`：client 代码已支持，但官方 TTS 文档未列；`TtsCaps` 目前**不放行**
- **返回**：base64 音频（客户端全 JSON 找最长 base64 兜底）
- **参数**：instruction；无数值 rate/pitch/volume/seed。
- **格式**：wav24/48/16。
- **注意**：voicedesign 模型的 `user` 指令必填；voiceclone 的 `audio.voice` 要放 `data:audio/mpeg;base64,...`。

---

### 3.11 B站 IndexTTS（硅基流动托管）`index-tts`

- **Base URL**：用户填；预设 `https://api.siliconflow.cn/v1`
- **端点**：`POST {base}/audio/speech`（OpenAI 兼容）
- **鉴权**：`Authorization: Bearer <Key>`（可留空，取决于自建服务）
- **模型**：`IndexTeam/IndexTTS-2`
- **参数**：`speed` 0.25–4.0、`gain`（额外参数）；无 seed。
- **格式**：mp3_256/128。
- **注意**：这是“OpenAI 兼容形态”，但不同托管商的字段可能略有差异；先用文档里的预设。

---

### 3.12 系统 TTS（本地）`system`

- **实现**：Android `TextToSpeech`
- **参数**：`setSpeechRate`（rate）、`setPitch`（pitch）；无 seed / volume / instruction。
- **格式**：wav24（引擎输出后本地写文件）。
- **注意**：设备没装语音引擎会报“系统 TTS 初始化失败”；不同 ROM 的音色/语言支持不同。

---

### 3.13 自定义渠道 `custom`

- **Base URL**：用户填
- **端点**：默认 `POST {base}/audio/speech`（可在设置里改 path）
- **鉴权**：默认 `Authorization: Bearer {key}`；可在设置里改成 `x-api-key: {key}` 等模板
- **返回**：`binary` / `base64:<json路径>` / `url`
- **参数**：只放行 `extra` 与 `seed`（服务支持就用；不支持一般会被忽略/报错）
- **格式**：全部
- **用途**：接新厂商/自建服务时先用它验证，稳定后再进 `TtsProviders`。

---

## 4. seed（骰子）支持审计

> 2026-10-20 逐家核对。**不要只看“厂商看起来支持”，要看官方 TTS 请求体里有没有这个字段。**

| 厂商 / 模型 | seed | 依据 / 备注 |
|---|---|---|
| 百炼 CosyVoice v3.5-plus / v3-flash | ✅ | ws `parameters.seed`；官方支持 |
| 百炼 Qwen-Audio-TTS 3.1 / 3.0 | ✅ | ws `parameters.seed` |
| 百炼 Qwen3-TTS / Qwen-TTS（http） | ❌ | 官方只有 text/voice/language_type/instructions |
| OpenAI `gpt-4o-mini-tts` | ❌ | 官方请求体无 seed |
| Groq Orpheus | ❌ | 官方示例/请求体无 seed |
| StepFun stepaudio-2.5-tts / step-tts-mini | ❌ | 官方 create-audio 无 seed |
| xAI grok-tts | ❌ | 官方只有 text/voice_id/language/codec/sample_rate/bit_rate |
| ElevenLabs v3 / v4 / v4_turbo | ✅ | 顶层 `seed` |
| MiniMax speech-2.8 / 2.6 | ❌ | `t2a_v2` 无 seed |
| Fish Audio s2.1/s2/s1 | ✅ | 用户实测；官方 Voice Design 文档有 seed，TTS 文档未列 |
| Gemini TTS | ⚠️ 未放行 | `GenerateContentConfig.seed` 有（0–20），但 TTS 文档未列为参数 |
| MiMo TTS | ❌ | 官方文档无 seed；client 代码虽支持，TtsCaps 不放行 |
| IndexTTS / 硅基流动 | ❌ | TTS 参数只有 model/input/voice/speed/gain/response_format |
| 系统 TTS | ❌ | Android TTS 无 seed |
| 自定义渠道 | ✅ | 透传，服务认就用 |

---

## 5. 新增厂商 / 模型 checklist

1. **厂商目录** `TtsProviders.all` 加 `P(...)`：
   - `id`（稳定、小写）、`name`、`baseUrl`、`shape`、`keyHint`
   - `models`（只放**在售/核实过**的）
   - `canClone`、`formats`
2. **百炼模型**才需要改 `TtsModels.all`（`id/label/family/transport`）。
3. **参数能力** `TtsCaps`：
   - 精确模型 → `byModel["provider/model"]`
   - 厂商兜底 → `byVendor["provider"]`
   - **只写官方文档确认的**；不确定就不放行，避免 UI 显示假能力。
4. **分派** `Synth.dispatch` 加 shape 分支；如果新厂商复用 OpenAI 兼容形态，优先复用 `OpenAiCompatTts`，只加配置。
5. **客户端**：
   - 端点 / 鉴权 / body 字段
   - 响应解析（binary/base64/url/hex/PCM 套头）
   - 错误信息带厂商名 + HTTP 码
   - 取消 / 超时 / 长文本
6. **测试矩阵**：
   - 最短文本、长文本、中文/英文/日文
   - 每个格式（wav/mp3）各一次
   - seed 0 / 1 / 65535（支持的厂商）
   - 参数置灰是否符合 `TtsCaps`
   - 无 Key / 错 Key / 429 / 5xx
   - 复制音色/复刻（如支持）
7. **更新本文件**：模型、参数矩阵、seed 审计、官方链接。

---

## 6. 官方文档链接

| 厂商 | 链接 |
|---|---|
| 阿里云百炼（模型/语音合成） | https://help.aliyun.com/zh/model-studio/ |
| OpenAI TTS | https://platform.openai.com/docs/guides/text-to-speech |
| Groq Orpheus TTS | https://console.groq.com/docs/text-to-speech/orpheus |
| StepFun TTS API | https://platform.stepfun.ai/docs/en/api-reference/audio/create-audio |
| xAI Text to Speech | https://docs.x.ai/developers/model-capabilities/audio/text-to-speech |
| ElevenLabs TTS | https://elevenlabs.io/docs/api-reference/text-to-speech/convert |
| MiniMax T2A v2 | https://platform.minimax.io/docs/api-reference/speech-t2a-http |
| Fish Audio TTS | https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech |
| Fish Audio Voice Design | https://docs.fish.audio/features/voice-design |
| Gemini Speech Generation | https://ai.google.dev/gemini-api/docs/speech-generation |
| Gemini GenerateContentConfig（seed） | https://ai.google.dev/api/generate-content |
| MiMo Speech Synthesis | https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/speech-synthesis-v2.5 |
| SiliconFlow TTS | https://docs.siliconflow.com/en/userguide/capabilities/text-to-speech |
| Android TextToSpeech | https://developer.android.com/reference/android/speech/tts/TextToSpeech |

---

## 7. App 侧补充（不属于厂商 API）

### 7.1 音色与厂商匹配

`VoiceProviders.of(id, customs)`：
1. 自建音色 → `CustomVoice.providerId`
2. `cosyvoice-*` → 百炼
3. 认不出 → null（不猜、不切）

**音色 ID 跨厂商不通用**；百炼内置 4 个音色在 `VoiceCatalog.builtIn`。

### 7.2 每个音色的自定义试听模板

- 存储：`filesDir/voice_previews/<voiceId.hashCode 的 hex>.<ext>`
- 试听时优先播本地模板；没有才发 TTS 默认中文句（`voice_preview_text`）
- 支持扩展名：wav/mp3/m4a/aac/ogg/opus/flac
- 这是 App 本地功能，**不是厂商 API**；备份 zip 目前不包含它。

### 7.3 后台生成保活

- `KeepAliveService`：dataSync 前台服务 + PARTIAL_WAKE_LOCK
- 百炼 WebSocket：20 秒 ping
- 长文本 TTS HTTP 读超时：300 秒
- 目的：切到别的 App 等生成时，不被系统冻结/断网

### 7.4 错误翻译

`Err.friendly(raw)` 会把 401/402/403/404/408/409/422/429/5xx、DNS、timeout、cancel 等翻成人话，并在后面附原始信息（截 300 字）。

---

## 8. 维护记录

- **2026-10-20**：建立本手册；逐家核对 seed（Fish 补上，其他未文档化的不放行）；补充各厂商端点/参数/坑。
- 以后每次增删模型、改参数能力、发现官方文档变更，**改这里 + 改对应代码**，不要把资料只留在聊天记录里。
