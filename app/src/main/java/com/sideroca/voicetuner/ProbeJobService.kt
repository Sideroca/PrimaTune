package com.sideroca.voicetuner

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * **探针作业**（2026-10-17）：只用来回答一个问题 ——
 * *"在这台机器上，把 App 从最近任务里划掉之后，系统还会不会按时把我们的进程拉起来干活？"*
 *
 * 为什么先做探针：AI 闹钟的"生成"现在跑在**我们自己的前台服务**里，划掉 App 就断（日志已证）。
 * 想改用 `JobScheduler` 托管，但**它会不会被这台 ROM 拦**属于"ROM 会不会给"的行为 ——
 * 按用户定下的原则：**先探针确认事实，再往上写功能**，不确认就开发＝赌博。
 *
 * 用法：设置 → 闹钟 → 闹钟权限体检 → 诊断 → 「排一个 60 秒后的测试作业」→
 *       **把 App 划掉** → 等一分钟 → 回来看 status.txt 里有没有「作业跑到了 ✅」。
 */
class ProbeJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        AlarmService.logStatus(this, "作业跑到了 ✅（JobScheduler 在 App 不在的情况下依然把我们叫起来了）")
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false   // 不重排

    companion object {
        private const val JOB_ID = 8801

        fun schedule(ctx: Context, delayMs: Long): Boolean = try {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val b = JobInfo.Builder(JOB_ID, ComponentName(ctx, ProbeJobService::class.java))
                .setOverrideDeadline(delayMs)          // "最迟 X 毫秒后必须跑"（JobScheduler 的准确说法）
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            if (Build.VERSION.SDK_INT >= 26) b.setRequiresBatteryNotLow(false)
            js.schedule(b.build()) == JobScheduler.RESULT_SUCCESS
        } catch (e: Exception) {
            AlarmService.logStatus(ctx, "测试作业登记失败：${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
