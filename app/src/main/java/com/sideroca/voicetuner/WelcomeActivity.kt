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

        for (l in langs) {
            val row = TextView(this).apply {
                text = l.flag + "    " + l.name
                setTextColor(0xFF1F2329.toInt())
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            row.setOnClickListener { pick(l.code) }
            root.addView(row)
        }
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
