package com.sideroca.voicetuner

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟「诊断快照」——**只读事实，不做任何决定**。
 *
 * 用户 2026-10-15 的原则：**凡是"依赖某个 ROM 会不会给"的行为，先只发一条探针确认事实，
 * 确认了再在上面写功能；不确认就开发＝赌博。** 这个对象就是那条探针。
 *
 * 它回答的都是"到点为什么不响"里我们**猜不出来**的部分：
 * 通知总开关 / 频道实况（重要级别、声音、是否被用户改过）/ 全屏意图权限 / 精确闹钟 /
 * 电池优化 / **闹钟流音量** / 勿扰 / **已排的闹钟还在不在系统里** / 缓存指纹 / 最近日志。
 */
object AlarmProbe {

    fun file(ctx: Context): File = File(Store(ctx).alarmDir, "probe.txt")

    fun snapshot(ctx: Context): String {
        val sb = StringBuilder()
        fun line(k: String, v: Any?) = sb.append(k).append("：").append(v).append('\n')

        sb.append("==== 玲珑调音 · 闹钟诊断快照 ====\n")
        line("时间", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        line("设备", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        // ① 精确闹钟
        runCatching {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            line("精确闹钟 canScheduleExactAlarms",
                if (Build.VERSION.SDK_INT < 31) "（API<31 恒真）" else am.canScheduleExactAlarms())
        }

        // ② 已排的闹钟**还在不在系统里**（FLAG_NO_CREATE：系统若清掉它，这里就是 false）
        runCatching {
            val alive = AlarmScheduler.pendingAlive(ctx)
            alive.forEach { (k, v) -> line("系统里还有这条闹钟? $k", if (v) "有 ✅" else "没了 ❌") }
        }

        // ③ 通知：总开关 + 每个频道的实况（含"用户/系统有没有改过它"）
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            line("通知总开关 areNotificationsEnabled", nm?.areNotificationsEnabled())
            val chs = nm?.notificationChannels ?: emptyList()
            line("频道数", chs.size)
            chs.forEach { ch ->
                sb.append("    · ").append(ch.id).append("「").append(ch.name).append("」")
                    .append(" importance=").append(ch.importance)
                    .append(" sound=").append(ch.sound?.toString() ?: "null")
                    .append(" vibrate=").append(ch.shouldVibrate())
                    .append(" userSetSound=").append(runCatching { ch.hasUserSetSound() }.getOrDefault(false))
                    .append('\n')
            }
            if (Build.VERSION.SDK_INT >= 34)
                line("全屏意图 canUseFullScreenIntent", nm?.canUseFullScreenIntent())
            line("勿扰 currentInterruptionFilter", nm?.currentInterruptionFilter)
        }

        // ④ 电池优化
        runCatching {
            line("已忽略电池优化", ctx.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(ctx.packageName))
        }

        // ⑤ **闹钟流音量**（很多人"闹钟不响"就是这条被调到 0 / 静音）
        runCatching {
            val au = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            line("闹钟音量", "${au.getStreamVolume(AudioManager.STREAM_ALARM)} / ${au.getStreamMaxVolume(AudioManager.STREAM_ALARM)}")
            line("闹钟流是否静音", au.isStreamMute(AudioManager.STREAM_ALARM))
            line("媒体音量", "${au.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${au.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
        }

        // ⑥ 当前闹钟配置 + 缓存指纹
        runCatching {
            val cfg = AlarmCfg.from(Store(ctx).alarmJson)
            line("闹钟配置", "enabled=${cfg.enabled} ${cfg.hour}:${String.format(Locale.US, "%02d", cfg.minute)} repeat=${cfg.repeat} 条数=${cfg.count} 字数下限=${cfg.minChars}")
            line("音色/厂商/模型", "${cfg.voiceName}(${cfg.voiceId.take(14)}) · ${cfg.providerId} · ${cfg.model}")
            line("缓存指纹是否匹配", if (AlarmService.cacheMatches(ctx)) "匹配 ✅" else "**不匹配/无缓存 ❌**")
            val fs = Store(ctx).alarmDir.listFiles()?.filter { it.isFile && it.name.endsWith(".wav") } ?: emptyList()
            line("可播 wav 段数", fs.size)
        }

        // ⑥.5 主页面状态 + 索引里最新 5 条（用户 2026-10-17 报"新生成的记录不出现在第 1 条"）
        runCatching {
            line("主页面角色筛选", MainActivity.diagFilterVoiceId ?: "（无）")
            line("最近一次落库的记录 id", MainActivity.diagLastTakeId ?: "（无）")
            val newest = Store(ctx).loadTakes().sortedByDescending { it.createdAt }.take(5)
            newest.forEach { t ->
                sb.append("    最新: ").append(t.id)
                    .append("  ").append(TimeFmt.log(t.createdAt))
                    .append("  ").append(t.voiceName).append('\n')
            }
        }

        // ⑦ 记录 / 收藏（用户 2026-10-17 报"收藏 15 条，设置页只显示五六条"→ 先用事实分清是
        //    "被筛选藏了" 还是 "记录本身丢了"）
        runCatching {
            val all = Store(ctx).loadTakes()
            val favs = Store(ctx).favTakes
            val hit = all.count { it.id in favs }
            line("记录总数", all.size)
            line("收藏 id 总数", favs.size)
            line("收藏里能在记录中找到的", "$hit 条" + if (hit < favs.size) " ← **差 ${favs.size - hit} 条**" else "")
            line("记录里有无重复 id", all.size != all.map { it.id }.toSet().size)
            if (hit < favs.size) {
                val missing = favs.filter { f -> all.none { it.id == f } }
                sb.append("    其中前 5 个找不到的收藏 id：").append(missing.take(5).joinToString("、")).append('\n')
            }
        }

        // ⑧ 启动耗时（startup.txt 尾部）——用户 2026-10-17：打开 App 还是有点慢
        runCatching {
            val f = java.io.File(ctx.filesDir, "startup.txt")
            if (f.exists()) {
                sb.append("---- startup.txt 最近 12 行 ----\n")
                f.readLines().takeLast(12).forEach { sb.append(it).append('\n') }
            } else {
                sb.append("（还没有 startup.txt —— 说明这一版 App 还没启动过）\n")
            }
        }

        // ⑨ 最近日志（status.txt 尾部）
        runCatching {
            val f = File(Store(ctx).alarmDir, "status.txt")
            if (f.exists()) {
                sb.append("---- status.txt 最近 20 行 ----\n")
                f.readLines().takeLast(20).forEach { sb.append(it).append('\n') }
            } else {
                sb.append("（还没有 status.txt —— 说明闹钟流程一次都没跑起来过）\n")
            }
        }
        return sb.toString()
    }

    /** 落盘 + 返回文件（给"复制给我"用） */
    fun write(ctx: Context): File {
        val f = file(ctx)
        runCatching { f.writeText(snapshot(ctx)) }
        return f
    }
}
