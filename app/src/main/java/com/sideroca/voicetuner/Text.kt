package com.sideroca.voicetuner

/**
 * 文本统计工具。
 *
 * **字数口径（与主流输入法/微信一致）**：
 * - **汉字 / 假名 / 韩文**：一个字算 **1**；
 * - **英文 / 数字 / 西里尔 / 阿拉伯等**：**一段连续的字母数字算 1 个"词"**（不是按字母数！）；
 * - 空白与标点：**只断词、不计数**。
 *
 * 例：`"你好 world 123"` → **4**（你好＝2 字；world＝1 词；123＝1 词）。
 */
fun charCount(text: String): Int {
    var n = 0
    var inWord = false
    for (ch in text) {
        val c = ch.code
        val cjk = (c in 0x2E80..0x9FFF) || (c in 0xAC00..0xD7AF) || (c in 0xF900..0xFAFF)
        if (cjk) {                       // 汉字 / 假名 / 韩文：按字
            n++
            inWord = false
        } else if (ch.isLetterOrDigit()) { // 拉丁 / 数字 / 其它字母：按词
            if (!inWord) { n++; inWord = true }
        } else {                          // 空白 / 标点：断词
            inWord = false
        }
    }
    return n
}
