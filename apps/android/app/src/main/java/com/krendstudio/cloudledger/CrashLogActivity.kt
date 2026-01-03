package com.krendstudio.cloudledger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.krendstudio.cloudledger.util.CrashReporter

class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            textSize = 12f
            setPadding(24, 24, 24, 24)
            text = CrashReporter.readCrashLog(this@CrashLogActivity) ?: "沒有找到閃退紀錄"
        }
        scrollView.addView(textView)

        val button = Button(this).apply {
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
            addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        setContentView(root)
    }
}
