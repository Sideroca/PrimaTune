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
import android.widget.SeekBar
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
        const val EXTRA_STYLE = "style"     // 用内置图标样式当素材（此时不需要 uri）
        const val EXTRA_NAME = "name"       // 桌面入口名字（原样回传）
        const val EXTRA_SRC = "src"         // 直接给"原图"文件路径（重裁用；优先于 uri）
        const val EXTRA_NX = "nx"           // 带入上次归一化状态（重裁：可随时重裁）
        const val EXTRA_NY = "ny"
        const val EXTRA_NZ = "nz"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Loc.apply(this)          // 界面语言（per-app locale）
        val slot = intent.getStringExtra(EXTRA_SLOT) ?: "main"
        val uriStr = intent.getStringExtra(EXTRA_URI).orEmpty()

        val style = intent.getStringExtra(EXTRA_STYLE).orEmpty()
        val srcPath = intent.getStringExtra(EXTRA_SRC).orEmpty()
        val src: Bitmap? = try {
            if (style.isNotEmpty()) {
                IconStyles.compose(this, style, 1024)      // 素材 = 内置样式（合成一张大图来取景）
            } else if (srcPath.isNotEmpty()) {
                BitmapFactory.decodeFile(srcPath)          // 重裁：直接用保留的原图
            } else if (uriStr.isNotEmpty()) {
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
        val scrW = resources.displayMetrics.widthPixels
        val scrH = resources.displayMetrics.heightPixels
        bar.addView(mkBtn(if (isIcon) "确定，用作图标" else "确定，用作壁纸") {
            val out0 = crop.cropped()
            if (out0 == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@mkBtn
            }
            val n = crop.normalized()
            val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
            val f = if (isIcon) File(filesDir, "icon_custom.png") else Wp.file(this, slot)
            // 压缩 + 保存原图都放后台线程：原来在主线程做「全尺寸 PNG」→ 卡约 2 秒
            Thread {
                try {
                    val out = if (isIcon) Bitmap.createScaledBitmap(out0, 512, 512, true)
                              else Bitmap.createScaledBitmap(out0, scrW, scrH, true)   // 壁纸按屏幕分辨率
                    FileOutputStream(f).use {
                        out.compress(if (isIcon) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                                     if (isIcon) 100 else 92, it)
                    }
                    if (!isIcon) Wp.saveSource(this, slot, src)   // 永久保留原图（供重新取景）
                } catch (e: Exception) {
                    // ignore
                }
                runOnUiThread {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(EXTRA_PATH, f.absolutePath)
                            .putExtra(EXTRA_NAME, name)
                            .putExtra(EXTRA_NX, n[0])
                            .putExtra(EXTRA_NY, n[1])
                            .putExtra(EXTRA_NZ, n[2])
                    )
                    finish()
                }
            }.start()
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
        // 取景框：图标=1:1；壁纸=跟随屏幕比例（0 = 跟随视图比例，由 CropView 自己算）
        crop.setImage(src, if (isIcon) 1f else 0f)
        // 重裁：带入上次的归一化状态（"可随时重裁"），在图片布局前设入 → 布局时按此还原
        val inNx = intent.getFloatExtra(EXTRA_NX, Float.NaN)
        val inNy = intent.getFloatExtra(EXTRA_NY, Float.NaN)
        val inNz = intent.getFloatExtra(EXTRA_NZ, Float.NaN)
        if (!inNx.isNaN() && !inNy.isNaN() && !inNz.isNaN()) {
            crop.setInitialState(inNx, inNy, inNz)
        }
        // 裁「主界面壁纸」时叠加"首页映射剪影"（规范 §2.3），遮罩浓度取当前设置，所见即所得
        if (!isIcon && slot == "main") {
            val st = Store(this)
            crop.setMapping(true, 0.6f, st.scrimMain)
        }
    }
}
