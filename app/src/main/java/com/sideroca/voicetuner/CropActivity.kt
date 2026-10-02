package com.sideroca.voicetuner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * 二次截取页（移植闪译 CropActivity 的极简版）：
 * 由壁纸"选择图片"进来，带 EXTRA_SLOT 区分主界面/设置页；
 * 取景框比例 = 屏幕比例；确定后把裁好的 PNG 路径回传。
 */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLOT = "slot"       // "main" | "page" | "icon"
        const val EXTRA_URI = "uri"
        const val EXTRA_PATH = "path"       // 结果：裁好的文件绝对路径
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slot = intent.getStringExtra(EXTRA_SLOT) ?: "main"
        val uriStr = intent.getStringExtra(EXTRA_URI).orEmpty()

        val src: Bitmap? = try {
            if (uriStr.isNotEmpty()) {
                contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (e: Exception) {
            null
        }
        if (src == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val d = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF101418.toInt())

        val crop = CropView(this)
        root.addView(
            crop,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        fun mkBtn(label: String, onTap: () -> Unit): TextView {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 15f
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (12 * d).toInt())
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener { onTap() }
            return tv
        }

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.addView(mkBtn("取消") {
            setResult(Activity.RESULT_CANCELED)
            finish()
        })
        val isIcon = slot == "icon"
        bar.addView(mkBtn(if (isIcon) "确定，用作图标" else "确定，用作壁纸") {
            val out0 = crop.cropped()
            if (out0 == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                // 图标槽位：1:1 → 烘焙成 512×512；壁纸槽位：与原有壁纸同路径（「清除」能删掉）
                val out = if (isIcon) Bitmap.createScaledBitmap(out0, 512, 512, true) else out0
                val f = if (isIcon) File(filesDir, "icon_custom.png") else Wp.file(this, slot)
                try {
                    FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, if (isIcon) 100 else 95, it) }
                } catch (e: Exception) {
                    // ignore
                }
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PATH, f.absolutePath))
                finish()
            }
        })
        val blp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        blp.gravity = Gravity.BOTTOM
        blp.bottomMargin = (30 * d).toInt()
        root.addView(bar, blp)

        setContentView(root)

        // 取景框比例：图标 = 1:1；壁纸 = 屏幕比例
        val dm = resources.displayMetrics
        crop.setImage(src, if (isIcon) 1f else dm.widthPixels.toFloat() / dm.heightPixels.toFloat())
    }
}
