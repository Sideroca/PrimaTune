package com.sideroca.voicetuner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * **首次安装**的「语言选择」页（白色）。
 * 选完记 `langChosen=true` → 以后（含**更新**）不再出现。
 * 语言名用各自文字（中文 / English / 日本語 / 한국어）自动可读，不依赖当前语言。
 */
class WelcomeActivity : AppCompatActivity() {

    private data class L(val flag: String, val name: String, val code: String)

    private val langs = listOf(
        L("🇨🇳", "中文", "zh"),
        L("🇬🇧", "English", "en"),
        L("🇯🇵", "日本語", "ja"),
        L("🇰🇷", "한국어", "ko"),
        L("🌐", "跟随系统 / System", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "玲珑调音 · Prima Tune"
            setTextColor(0xFF1F2329.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "请选择语言   /   Choose your language"
            setTextColor(0xFF828A9B.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(sub)

        // 每一行 = 「固定宽度的旗盒（居中）」＋「文字」，行内垂直居中；
        // 行宽一致 → 国旗成一列、文字成一列，彻底对齐（不再把国旗塞进同一个 TextView）
        val listW = minOf(dp(300), resources.displayMetrics.widthPixels - dp(48))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(listW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val flagBox = dp(40)
        for (l in langs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
                ).apply { topMargin = dp(10) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat(); setColor(0x0A1F2329)
                }
                isClickable = true
                isFocusable = true
            }
            val flag = TextView(this).apply {
                text = l.flag
                textSize = 22f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(flagBox, flagBox)
            }
            val name = TextView(this).apply {
                text = l.name
                textSize = 17f
                gravity = Gravity.START
                includeFontPadding = false
                setTextColor(0xFF1F2329.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(10) }
            }
            row.addView(flag); row.addView(name)
            row.setOnClickListener { pick(l.code) }
            list.addView(row)
        }
        root.addView(list)
        setContentView(root)
    }

    private fun pick(code: String) {
        val st = Store(this)
        st.lang = code
        st.langChosen = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
