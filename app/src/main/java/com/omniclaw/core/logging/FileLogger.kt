package com.omniclaw.core.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Log
import com.omniclaw.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * File-backed logger with logcat fallback.
 *
 * Why this exists:
 *  - The app needs persistent logs for diagnosing on-device issues (agent
 *    install failures, runtime exec errors, etc.).
 *  - Historically this logger silently swallowed all write errors, which
 *    meant that when file logging failed (no permission, no disk space,
 *    logDir missing), the user got zero diagnostic info — "the log system
 *    doesn't work" with no clue why.
 *
 * What this version does differently:
 *  1. Every log line is also written to logcat via [android.util.Log].
 *     So even if file logging fails completely, `adb logcat` still shows
 *     everything. Tag = "OmniClaw".
 *  2. The logDir is created on every init() call AND on every write if
 *     missing (cheap mkdirs() check).
 *  3. Write errors are logged to logcat ONCE with the actual exception
 *     message, so the user can see "FileWriter failed: EACCES" etc.
 *  4. The active log directory path is exposed via [getLogDirPath] so the
 *     UI can show users where to find the logs.
 */
object FileLogger {

    private const val LOG_DIR = "omniclaw_logs"
    private const val MAX_LOG_FILES = 7
    private const val MAX_CRASH_FILES = 10
    private const val TAG = "OmniClaw"
    private const val FILE_LOGGER_TAG = "FileLogger"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val executor = Executors.newSingleThreadExecutor()

    private var appContext: Context? = null
    private var logDir: File? = null
    private var isInitialized = false
    private var writeFailureLogged = false
    private var originalExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        val appCtx = appContext!!

        // Try public external storage: /storage/emulated/0/omniclaw_logs/
        // Requires MANAGE_EXTERNAL_STORAGE on Android 11+, falls back gracefully
        logDir = resolvePublicLogDir(appCtx)
        if (logDir == null || (logDir!!.exists() && !logDir!!.canWrite())) {
            logDir = appCtx.getExternalFilesDir(LOG_DIR)
        }
        if (logDir == null) {
            logDir = File(appCtx.cacheDir, LOG_DIR)
        }
        logDir?.let { dir ->
            // Always mkdirs() — if the dir was deleted externally (e.g. by a
            // storage cleaner), we need to recreate it before writing.
            if (!dir.exists()) dir.mkdirs()
            cleanOldLogs(dir)
        }
        isInitialized = true
        installCrashHandler()
        i(TAG, "FileLogger initialized at: ${logDir?.absolutePath}")
        i(TAG, "App version: ${BuildConfig.VERSION_NAME}, SDK: ${Build.VERSION.SDK_INT}")
    }

    /**
     * The absolute path of the directory where logs are currently being
     * written. Exposed so the Settings screen can show users where to find
     * their logs (and so support can ask for the path).
     */
    fun getLogDirPath(): String? = logDir?.absolutePath

    /**
     * Attempt to resolve the public log path /storage/emulated/0/omniclaw_logs/.
     * Returns null if the permission is unavailable, so the caller can fall
     * back to app-private storage.
     *
     * Permission requirements by API level:
     * - API 33+  → MANAGE_EXTERNAL_STORAGE
     * - API 30-32 → MANAGE_EXTERNAL_STORAGE
     * - API 29   → WRITE_EXTERNAL_STORAGE
     * - API 28-   → WRITE_EXTERNAL_STORAGE (auto-granted at install)
     */
    private fun resolvePublicLogDir(context: Context): File? {
        return try {
            val externalRoot = Environment.getExternalStorageDirectory()
            if (externalRoot == null || !externalRoot.exists()) return null
            val publicLogDir = File(externalRoot, LOG_DIR)
            // Already exists and writable (e.g. permission was granted previously)
            if (publicLogDir.exists() && publicLogDir.canWrite()) return publicLogDir
            // Doesn't exist yet but parent is writable — we can create it
            if (!publicLogDir.exists() && externalRoot.canWrite()) return publicLogDir
            null
        } catch (_: Exception) {
            null
        }
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
        // ── ALWAYS write to logcat first ───────────────────────────────
        // This is the critical fix: even if file logging fails (no permission,
        // no disk, logDir missing), logcat still gets the message. Users can
        // run `adb logcat -s OmniClaw` to see everything.
        try {
            when (level) {
                "D" -> Log.d(tag, msg, throwable)
                "I" -> Log.i(tag, msg, throwable)
                "W" -> Log.w(tag, msg, throwable)
                "E" -> Log.e(tag, msg, throwable)
                else -> Log.i(tag, msg, throwable)
            }
        } catch (_: Throwable) {
            // Log itself failed (e.g. logcat disabled on user builds) — nothing
            // we can do, fall through to file logging.
        }

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
            val dir = logDir ?: return@execute
            // Recreate dir if it was deleted externally (storage cleaner, etc.)
            if (!dir.exists()) dir.mkdirs()
            if (!dir.exists() || !dir.canWrite()) {
                // Log the failure to logcat ONCE so the user can see why
                // file logging isn't working — the old code silently swallowed
                // this, which made "the log system doesn't work" impossible
                // to debug.
                if (!writeFailureLogged) {
                    writeFailureLogged = true
                    try {
                        Log.e(FILE_LOGGER_TAG, "Cannot write logs to ${dir.absolutePath} " +
                            "(exists=${dir.exists()}, canWrite=${dir.canWrite()}). " +
                            "File logging disabled; logs are still going to logcat.")
                    } catch (_: Throwable) { }
                }
                return@execute
            }
            val file = File(dir, "app_${dateFormat.format(Date())}.log")
            try {
                FileWriter(file, true).use { it.append(line) }
                writeFailureLogged = false  // reset on success
            } catch (e: Exception) {
                if (!writeFailureLogged) {
                    writeFailureLogged = true
                    try {
                        Log.e(FILE_LOGGER_TAG, "FileWriter failed for ${file.absolutePath}: ${e.message}", e)
                    } catch (_: Throwable) { }
                }
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

        // Always log crash to logcat first (in case file write fails)
        try { Log.e(TAG, "=== UNCAUGHT CRASH ===\n$header") } catch (_: Throwable) { }

        // Write to private log dir (cache or externalFiles)
        logDir?.let { dir ->
            try {
                if (!dir.exists()) dir.mkdirs()
                FileWriter(File(dir, crashFileName), false).use { it.append(header) }
                FileWriter(File(dir, "app_${dateFormat.format(Date())}.log"), true).use { it.append(header) }
                cleanCrashLogs(dir)
            } catch (e: Exception) {
                try { Log.e(FILE_LOGGER_TAG, "Crash log file write failed: ${e.message}", e) } catch (_: Throwable) { }
            }
        }

        // Also write to public Downloads folder via MediaStore (API 29+)
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, crashFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/omniclaw_logs")
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
