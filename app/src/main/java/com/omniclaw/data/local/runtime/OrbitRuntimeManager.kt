package com.omniclaw.data.local.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OmniClawRuntime"
private const val BUSYBOX_ASSET = "busybox-arm64"
private const val BUSYBOX_BINARY = "busybox"
class OmniClawRuntimeManager(val context: Context) {
    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val binDir = File(runtimeDir, "bin")
    val tmpDir = File(runtimeDir, "tmp")
    val packagesDir = File(runtimeDir, "packages")
    val downloadsDir = File(runtimeDir, "downloads")
    val agentsDir = File(runtimeDir, "agents")
    val logsDir = File(runtimeDir, "logs")
    val environmentsDir = File(runtimeDir, "environments")

    init {
        listOf(runtimeDir, binDir, tmpDir, packagesDir, downloadsDir, agentsDir, logsDir, environmentsDir).forEach {
            it.mkdirs()
        }
    }

    fun getEnvVars(): Array<String> {
        val existingPath = System.getenv("PATH") ?: ""
        return arrayOf("PATH=${binDir.absolutePath}:$existingPath")
    }

    /** Full path to the BusyBox binary, or null if not installed. */
    fun busyBoxPath(): String? {
        val f = File(binDir, BUSYBOX_BINARY)
        return if (f.exists() && f.canExecute()) f.absolutePath else null
    }

    /**
     * Install BusyBox from bundled APK assets. BusyBox provides ~300 POSIX tools
     * (sh, cp, mv, chmod, tar, grep, wget, etc.) in a single ~1MB binary, making
     * Orbit-AI self-contained without requiring Termux.
     *
     * Wrapper scripts are NOT created — they cannot be made executable on
     * Android 10+ in app-private directories (setExecutable silently fails,
     * chmod via Runtime.exec has a chicken-and-egg problem). Instead,
     * [LocalCommandRunner] calls `busybox sh -c <cmd>` directly.
     *
     * This is safe to call repeatedly — it skips if already installed.
     *
     * @return true if BusyBox is available after the call
     */
    suspend fun installBusyBox(): Boolean = withContext(Dispatchers.IO) {
        val busyboxFile = File(binDir, BUSYBOX_BINARY)

        // Already installed — fast path
        if (busyboxFile.exists() && busyboxFile.canExecute()) {
            return@withContext true
        }

        try {
            // Extract BusyBox binary from APK assets
            context.assets.open(BUSYBOX_ASSET).use { input ->
                busyboxFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Use the system toybox chmod (always available on Android)
            val chmodResult = Runtime.getRuntime()
                .exec(arrayOf("/system/bin/chmod", "755", busyboxFile.absolutePath))
            chmodResult.waitFor()

            if (!busyboxFile.canExecute()) {
                // Last resort — copy via system toybox directly
                Runtime.getRuntime().exec(arrayOf(
                    "/system/bin/sh", "-c",
                    "/system/bin/cp ${busyboxFile.absolutePath} ${busyboxFile.absolutePath}.tmp && " +
                    "/system/bin/mv ${busyboxFile.absolutePath}.tmp ${busyboxFile.absolutePath} && " +
                    "/system/bin/chmod 755 ${busyboxFile.absolutePath}"
                )).waitFor()
            }

            val ok = busyboxFile.canExecute()
            if (ok) {
                Log.i(TAG, "BusyBox installed at ${busyboxFile.absolutePath}")
            } else {
                Log.w(TAG, "BusyBox extracted but NOT executable — check filesystem mount options")
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "BusyBox install skipped: ${e.message}")
            // BusyBox is a fallback — system may have Termux or toybox
            false
        }
    }
}
