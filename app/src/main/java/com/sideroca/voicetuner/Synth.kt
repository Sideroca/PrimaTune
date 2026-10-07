package com.sideroca.voicetuner

import android.content.Context
import org.json.JSONObject

/**
 * TTS 分派：把「按当前厂商/形态选客户端」集中在一处。
 * **主页生成** 与 **AI 闹钟** 共用，避免两处各写一遍。
 */
object Synth {

    fun dispatch(
        ctx: Context, store: Store, req: SynthRequest, extra: JSONObject?,
        fmt: String, dash: DashScopeClient, cb: SynthCallback
    ): Cancellable {
        val prov = TtsProviders.byId(store.providerId) ?: TtsProviders.all.first()
        val provKey = store.providerKey(prov.id).ifBlank { if (prov.id == "aliyun-bailian") store.apiKey else "" }
        return when {
            prov.shape == "system" -> ExtraTts2.system(ctx, req.text, req.rate, req.pitch, cb)
            prov.shape == "gemini" -> ExtraTts2.gemini(provKey, req.model, req.voice, req.text, cb)
            prov.shape == "mimo" -> ExtraTts2.mimo(
                store.providerBaseUrl(prov.id), provKey, req.model, req.text, cb, req.instruction, req.voice, req.seed
            )
            prov.shape == "elevenlabs" -> ExtraTts.eleven(
                store.providerBaseUrl(prov.id), provKey, req.voice, req.model, req.text, 0.5, 0.75, cb, req.rate, req.seed
            )
            prov.shape == "minimax" -> ExtraTts.minimax(
                store.providerBaseUrl(prov.id), provKey, store.workspace, req.model, req.voice,
                req.text, req.rate, req.pitch, req.volume, cb, req.instruction
            )
            prov.shape == "fish" -> ExtraTts.fish(
                store.providerBaseUrl(prov.id), provKey, req.voice, req.text, cb, req.model,
                req.rate, req.volume, extra, req.instruction, req.seed, fmt
            )
            prov.shape == "openai" || prov.shape == "xai" -> OpenAiCompatTts.synthesize(
                prov.copy(baseUrl = store.providerBaseUrl(prov.id)), provKey, req.model, req.voice, req.text, req.instruction, cb,
                TtsProviders.Cfg(
                    path = store.providerPath(prov.id), auth = store.providerAuth(prov.id),
                    resp = store.providerResp(prov.id), extra = extra
                ),
                rate = req.rate, volume = req.volume, seed = if (prov.id == "custom") req.seed else null
            )
            else -> dash.synthesize(req.copy(apiKey = provKey), cb)
        }
    }
}
