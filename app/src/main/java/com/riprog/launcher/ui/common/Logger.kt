package com.riprog.launcher.ui.common

import android.content.Context
import android.util.Log
import com.riprog.launcher.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "RiProGLauncher"
    private var logFile: File? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        try {
            val dir = context.getExternalFilesDir("logs")
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                logFile = File(dir, "launcher_debug.log")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logger", e)
        }
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
            writeToFile("DEBUG", message)
        }
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
            writeToFile("INFO", message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        if (BuildConfig.DEBUG) {
            writeToFile("ERROR", "$message ${throwable?.stackTraceToString() ?: ""}")
        }
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun writeToFile(level: String, message: String) {
        val file = logFile ?: return
        executor.execute {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                FileWriter(file, true).use { writer ->
                    writer.append("$timestamp $level/$TAG: $message\n")
                }
            } catch (ignored: Exception) {
            }
        }
    }
}
