package com.example.gemini.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global Crash Protector for JIN.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 *
 * Ensures the app never crashes from unhandled coroutine or thread exceptions.
 * Intercepts uncaught exceptions, writes persistent diagnostic logs, and recovers gracefully.
 */
object GlobalCrashProtector {

    private const val TAG = "GlobalCrashProtector"
    private var isInstalled = false

    fun install(context: Context) {
        if (isInstalled) return
        isInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL INTERCEPT: Uncaught exception in thread '${thread.name}': ${throwable.message}", throwable)
                writeCrashLog(context, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed writing crash log: ${e.message}")
            }

            // If it's a non-fatal coroutine or network socket timeout exception, don't kill process
            val isRecoverable = throwable is java.io.IOException ||
                    throwable is java.net.SocketTimeoutException ||
                    throwable is kotlinx.coroutines.CancellationException ||
                    throwable is java.lang.IllegalStateException && throwable.message?.contains("coroutine", true) == true

            if (!isRecoverable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "GlobalCrashProtector installed successfully.")
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logContent = """
            ====================================================
            JIN STABILITY & CRASH REPORT
            Time: $timeStamp
            Thread: ${thread.name} (id=${thread.id})
            Exception: ${throwable.javaClass.name}
            Message: ${throwable.message}
            ====================================================
            Stack Trace:
            $stackTrace
            ====================================================
        """.trimIndent()

        // 1. Write to internal storage
        try {
            val internalFile = File(context.filesDir, "last_crash.log")
            internalFile.writeText(logContent)
        } catch (_: Throwable) {}

        // 2. Write to public Downloads for easy debugging if permitted
        try {
            val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (pubDir.exists()) {
                val pubFile = File(pubDir, "JIN_crash_log.txt")
                pubFile.appendText("\n\n" + logContent)
            }
        } catch (_: Throwable) {}
    }
}
