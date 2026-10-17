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
 * · **App 内也做单路播放**：新的 request 会先回调上一个持有者（首页记录 → 设置页试听、
 *   设置页试听 → 首页记录），避免两个 MediaPlayer 同时响。
 */
object AudioFocus {

    private var req: AudioFocusRequest? = null
    /** 当前持有者的"被顶掉/被抢走"回调；null = 当前没有我们这边的播放 */
    private var lostCb: (() -> Unit)? = null
    /** 播放权代号：每次 request/abandon 递增；旧的 MediaPlayer 回调拿着旧代号，不能误停新播放 */
    private var gen = 0

    private fun am(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * 申请焦点；onLost = 播放中被别人抢走时回调（一般直接停掉自己的播放）。
     *
     * 返回本次播放的**代号**，停止时把它传给 [abandon] —— 这样旧播放器的 completion/error
     * 晚到一步也不会把新播放器的焦点顺手关掉。
     *
     * ⚠️ 只在主线程调用（当前所有调用点都在主线程）：新的 request 会**同步**回调上一个持有者，
     * 让它先 stop/release，再申请新焦点 —— 这是 App 内跨 Activity 防叠音的关键。
     */
    fun request(ctx: Context, onLost: () -> Unit): Int {
        // 新的播放开始 → 先通知上一个持有者停下（首页记录 ↔ 设置页试听 ↔ 音色试听）
        val prev = lostCb
        lostCb = null
        if (prev != null) runCatching { prev() }   // 它内部通常会再调 abandon，下面再清一次

        abandon(ctx)
        val myToken = ++gen
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
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            val cb = lostCb
                            lostCb = null
                            cb?.let { runCatching { it() } }
                        }
                        else -> Unit          // GAIN / DUCK：不折腾
                    }
                }
                .build()
            req = r
            lostCb = onLost
            am(ctx).requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            myToken
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 交还焦点。
     * @param token [request] 返回的代号；传 0 / 省略 = 强制清掉当前焦点（仅用于“确定自己是当前持有者”的收尾）。
     *              旧播放器的回调请务必传自己的 token，避免误伤后来居上的新播放器。
     */
    fun abandon(ctx: Context, token: Int = 0) {
        if (token != 0 && token != gen) return   // 旧播放器的迟到回调：新播放已经接管，别动
        try {
            req?.let { am(ctx).abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
        }
        req = null
        lostCb = null
        gen++                                    // 让所有旧 token 立即失效
    }
}
