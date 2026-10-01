package com.sideroca.voicetuner

/**
 * 把各家的原始报错翻成"人话"（不丢原始信息，附在后面）。
 * 各厂商的错误码含义大同小异，这里统一解释，避免界面上直接甩一坨 JSON。
 */
object Err {

    fun friendly(raw: String?): String {
        val s = raw.orEmpty().trim()
        if (s.isEmpty()) return "未知错误"

        val code = Regex("\\b(401|402|403|404|408|409|422|429|5\\d\\d)\\b")
            .find(s)?.value?.toIntOrNull()

        val head: String = when (code) {
            401 -> "Key 无效或已过期（401）"
            402 -> "余额/额度不足（402）"
            403 -> "没有权限，或该模型未开通（403）"
            404 -> "接口地址不对（404）—— 改过 Base URL 的话请检查"
            408 -> "请求超时（408）"
            409 -> "重复请求或资源已存在（409）"
            422 -> "参数不符合该厂商要求（422）—— 常见于模型名/字段名不对"
            429 -> "请求太频繁，稍后再试（429）"
            in 500..599 -> "对方服务器出错（" + code + "），过会儿再试"
            else -> ""
        }
        if (head.isNotEmpty()) return head + "\n\n原始信息：" + s.take(300)

        val low = s.lowercase()
        return when {
            low.contains("unable to resolve host") || low.contains("failed to connect") ||
                low.contains("connection refused") || low.contains("timed out") ||
                low.contains("timeout") || low.contains("connectexception") ->
                "连不上服务器（检查网络/代理；某些域名在本机可能不可达）\n\n原始信息：" + s.take(300)
            low.contains("canceled") || low.contains("cancelled") -> "已取消"
            else -> s
        }
    }
}
