package com.omniclaw.core.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import com.omniclaw.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object FileLogger {

    private const val LOG_DIR = "logs"
    private const val MAX_LOG_FILES = 7
    private const val MAX_CRASH_FILES = 10
    private const val TAG = "FileLogger"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val executor = Executors.newSingleThreadExecutor()

    private var appContext: Context? = null
    private var logDir: File? = null
    private var isInitialized = false
    private var originalExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        val appCtx = appContext!!

        logDir = appCtx.getExternalFilesDir(LOG_DIR)
        if (logDir == null) {
            logDir = File(appCtx.cacheDir, LOG_DIR)
        }
        logDir?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            cleanOldLogs(dir)
        }
        isInitialized = true
        installCrashHandler()
        i(TAG, "FileLogger initialized at: ${logDir?.absolutePath}")
        i(TAG, "App version: ${BuildConfig.VERSION_NAME}, SDK: ${Build.VERSION.SDK_INT}")
    }

    private fun installCrashHandler() {
        originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrashSync(throwable, thread)
            originalExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    fun d(tag: String, msg: String) = write("D", tag, msg, null)
    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String) = write("W", tag, msg, null)
    fun e(tag: String, msg: String) = write("E", tag, msg, null)
    fun e(tag: String, msg: String, throwable: Throwable?) = write("E", tag, msg, throwable)

    private fun write(level: String, tag: String, msg: String, throwable: Throwable?) {
        if (!isInitialized) return
        val time = timeFormat.format(Date())
        val threadName = Thread.currentThread().name
        val line = buildString {
            append("$time | $level | $threadName | $tag | $msg")
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
            append("\n")
        }
        executor.execute {
            logDir?.let { dir ->
                val file = File(dir, "app_${dateFormat.format(Date())}.log")
                try {
                    FileWriter(file, true).use { it.append(line) }
                } catch (_: Exception) { }
            }
        }
    }

    private fun buildCrashReport(throwable: Throwable, thread: Thread?): String {
        val time = timeFormat.format(Date())
        val threadName = thread?.name ?: "unknown"
        return buildString {
            append("$time | E | $threadName | CRASH | === UNCAUGHT_CRASH ===\n")
            append("$time | E | $threadName | CRASH | Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("$time | E | $threadName | CRASH | Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("$time | E | $threadName | CRASH | App: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\n")
            append("$time | E | $threadName | CRASH | ${throwable.javaClass.name}: ${throwable.message}\n")
            append(throwable.stackTraceToString().lines().joinToString("\n") { line ->
                "$time | E | $threadName | CRASH |   $line"
            })
            append("\n")
        }
    }

    private fun logCrashSync(throwable: Throwable, thread: Thread?) {
        if (!isInitialized) return
        val header = buildCrashReport(throwable, thread)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFileName = "crash_$ts.log"

        // Write to private log dir (cache or externalFiles)
        logDir?.let { dir ->
            try {
                FileWriter(File(dir, crashFileName), false).use { it.append(header) }
                FileWriter(File(dir, "app_${dateFormat.format(Date())}.log"), true).use { it.append(header) }
                cleanCrashLogs(dir)
            } catch (_: Exception) { }
        }

        // Also write to public Downloads folder via MediaStore (API 29+)
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, crashFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OrbitCrashLogs")
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    ctx.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(header.toByteArray())
                        outputStream.flush()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun cleanOldLogs(dir: File) {
        val files = dir.listFiles { f -> f.name.matches(Regex("app_\\d{4}-\\d{2}-\\d{2}\\.log")) }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_LOG_FILES) {
            files.drop(MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    private fun cleanCrashLogs(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_CRASH_FILES) {
            files.drop(MAX_CRASH_FILES).forEach { it.delete() }
        }
    }
}
