package com.riprog.launcher.util

import android.content.Context
import com.riprog.launcher.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private var logFile: File? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return

        try {
            val dir = File("/storage/emulated/0/RiProG")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sessionName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(dir, "${sessionName}.txt")

            log("Session started: $sessionName")
            log("Device: ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            android.util.Log.e("RiProGLogger", "Failed to initialize logger", e)
        }
    }

    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "$time: $message\n"

        android.util.Log.d("RiProG", message)

        logFile?.let { file ->
            try {
                FileOutputStream(file, true).use { out ->
                    out.write(line.toByteArray())
                }
            } catch (e: Exception) {
                android.util.Log.e("RiProGLogger", "Failed to write log", e)
            }
        }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        log("ERROR: $message")
        throwable?.let {
            log(it.stackTraceToString())
        }
        android.util.Log.e("RiProG", message, throwable)
    }
}
