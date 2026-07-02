package com.omniclaw.data.local.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OmniClawRuntime"
private const val BUSYBOX_ASSET = "busybox-arm64"
private const val BUSYBOX_BINARY = "busybox"

/**
 * Resolve the absolute path to a system tool (sh, chmod, cp, mv) under
 * `/system/bin`. We honor `ANDROID_ROOT` so custom ROMs that mount the system
 * tree elsewhere (e.g. emulators) work transparently.
 */
private fun systemBin(tool: String): String {
    val root = android.system.Os.getenv("ANDROID_ROOT") ?: "/system"
    return "$root/bin/$tool"
}

class OmniClawRuntimeManager(val context: Context) {
    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val binDir = File(runtimeDir, "bin")
    val tmpDir = File(runtimeDir, "tmp")
    val packagesDir = File(runtimeDir, "packages")
    val downloadsDir = File(runtimeDir, "downloads")
    val agentsDir = File(runtimeDir, "agents")
    val logsDir = File(runtimeDir, "logs")
    val environmentsDir = File(runtimeDir, "environments")

    // Caches whether busybox is actually executable.
    // canExecute() can return true on some Android versions but runtime execution
    // still fails with EACCES. We verify once and cache the result.
    private var busyboxVerified: Boolean? = null

    init {
        listOf(runtimeDir, binDir, tmpDir, packagesDir, downloadsDir, agentsDir, logsDir, environmentsDir).forEach {
            it.mkdirs()
        }
    }

    fun getEnvVars(): Array<String> {
        val existingPath = System.getenv("PATH") ?: ""
        return arrayOf("PATH=${binDir.absolutePath}:$existingPath")
    }

    /** Full path to the BusyBox binary, or null if not installed or not executable. */
    fun busyBoxPath(): String? {
        val f = File(binDir, BUSYBOX_BINARY)
        if (!f.exists()) return null

        // Use cached result if available
        if (busyboxVerified != null) {
            return if (busyboxVerified!!) f.absolutePath else null
        }

        // canExecute() can be unreliable on some Android kernels (returns true but
        // ProcessBuilder still gets EACCES). Verify with an actual execution.
        if (!f.canExecute()) {
            busyboxVerified = false
            return null
        }

        return try {
            val p = Runtime.getRuntime().exec(arrayOf(f.absolutePath, "true"))
            p.waitFor()
            busyboxVerified = true
            Log.i(TAG, "BusyBox verified at ${f.absolutePath}")
            f.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "BusyBox at ${f.absolutePath} exists but cannot be executed: ${e.message}")
            busyboxVerified = false
            null
        }
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

        // Already installed and verified — fast path
        if (busyboxVerified == true && busyboxFile.exists()) {
            return@withContext true
        }

        // If canExecute() says true but we previously verified it as false,
        // invalidate the cache so we re-extract and re-test.
        busyboxVerified = null

        try {
            // Extract BusyBox binary from APK assets
            context.assets.open(BUSYBOX_ASSET).use { input ->
                busyboxFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Use the system toybox chmod (always available on Android)
            val chmodResult = Runtime.getRuntime()
                .exec(arrayOf(systemBin("chmod"), "755", busyboxFile.absolutePath))
            chmodResult.waitFor()

            if (!busyboxFile.canExecute()) {
                // Last resort — copy via system toybox directly
                Runtime.getRuntime().exec(arrayOf(
                    systemBin("sh"), "-c",
                    "${systemBin("cp")} ${busyboxFile.absolutePath} ${busyboxFile.absolutePath}.tmp && " +
                    "${systemBin("mv")} ${busyboxFile.absolutePath}.tmp ${busyboxFile.absolutePath} && " +
                    "${systemBin("chmod")} 755 ${busyboxFile.absolutePath}"
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
