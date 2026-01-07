package com.krendstudio.cloudledger

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.krendstudio.cloudledger.util.CrashReporter

class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashLog = CrashReporter.readCrashLog(this@CrashLogActivity) ?: "沒有找到閃退紀錄"

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            textSize = 12f
            setPadding(24, 24, 24, 24)
            text = crashLog
        }
        scrollView.addView(textView)

        // 新增複製按鈕
        val copyButton = Button(this).apply {
            text = "複製錯誤訊息"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Log", crashLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashLogActivity, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
            }
        }

        val backButton = Button(this).apply {
            text = "清除並返回"
            setOnClickListener {
                CrashReporter.clearCrashLog(this@CrashLogActivity)
                startActivity(Intent(this@CrashLogActivity, MainActivity::class.java))
                finish()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            addView(copyButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(backButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        setContentView(root)
    }
}
