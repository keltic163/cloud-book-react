package com.krendstudio.cloudledger.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashReporter {
    private const val PREFS_NAME = "cloudledger_prefs"
    private const val KEY_CRASH_CODE = "last_crash_code"
    private const val KEY_CRASH_MESSAGE = "last_crash_message"
    private const val LOG_FILE_NAME = "crash.log"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val code = "CRASH-$timestamp"
            val message = throwable.javaClass.simpleName + ": " + (throwable.message ?: "no-message")
            prefs.edit()
                .putString(KEY_CRASH_CODE, code)
                .putString(KEY_CRASH_MESSAGE, message)
                .apply()

            writeCrashLog(appContext, code, thread.name, throwable)

            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                exitProcess(10)
            }
        }
    }

    fun hasCrashLog(context: Context): Boolean {
        val file = File(context.filesDir, LOG_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun readCrashLog(context: Context): String? {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun clearCrashLog(context: Context) {
        runCatching { File(context.filesDir, LOG_FILE_NAME).delete() }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CRASH_CODE).remove(KEY_CRASH_MESSAGE).apply()
    }

    private fun writeCrashLog(context: Context, code: String, threadName: String, throwable: Throwable) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val content = buildString {
            appendLine("code=$code")
            appendLine("thread=$threadName")
            appendLine("type=${throwable.javaClass.name}")
            appendLine("message=${throwable.message}")
            appendLine("stacktrace=")
            throwable.stackTrace.forEach { element ->
                appendLine(element.toString())
            }
        }
        runCatching { file.writeText(content) }
    }
}
