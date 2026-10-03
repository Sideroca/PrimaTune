package com.sideroca.voicetuner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * 音频焦点（播放流程优化）：
 *
 * · 开始播放前申请 **短暂独占** AUDIOFOCUS_GAIN_TRANSIENT
 *   → 别的 App（音乐/播客）会**自动暂停**，我们放完再**自动续播**；
 *   这样"生成好了自动播放"就会**打断正在播放的声音**，而不是两个声音叠在一起。
 * · 我们自己放完（或被打断）就 abandon，交还焦点。
 * · 如果播放途中被别人抢走（来电、别人点了播放），回调里把自己停掉，不跟人抢。
 */
object AudioFocus {

    private var req: AudioFocusRequest? = null

    private fun am(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 申请焦点；onLost = 播放中被别人抢走时回调（一般直接停掉自己的播放） */
    fun request(ctx: Context, onLost: () -> Unit): Boolean {
        abandon(ctx)
        return try {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLost()
                        else -> Unit          // GAIN / DUCK：不折腾
                    }
                }
                .build()
            req = r
            am(ctx).requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun abandon(ctx: Context) {
        try {
            req?.let { am(ctx).abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
        }
        req = null
    }
}
