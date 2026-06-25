package com.omniclaw.data.local.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val BUSYBOX_APPLETS = listOf(
    // shell
    "ash", "sh",
    // file operations
    "cp", "mv", "rm", "ls", "cat", "echo", "mkdir", "ln", "touch",
    // permissions
    "chmod", "chown",
    // archive extraction (used by PackageInstaller)
    "tar", "gunzip", "bunzip2", "gzip", "bzip2", "unzip",
    // text processing
    "grep", "sed", "awk", "cut", "sort", "uniq", "wc", "head", "tail",
    // scripting / utilities
    "find", "test", "xargs", "which", "basename", "dirname",
    // downloads
    "wget",
    // time / process
    "date", "sleep", "timeout", "ps", "kill", "pidof",
    // misc
    "tr", "strings", "tee", "true", "false", "clear", "diff", "comm"
)

private const val TAG = "OmniClawRuntime"
private const val BUSYBOX_ASSET = "busybox-arm64"
private const val BUSYBOX_BINARY = "busybox"
private const val WRAPPER_HEADER = "#!/system/bin/sh"
private const val WRAPPER_TEMPLATE = "exec busybox %s \"\$@\""

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

    /**
     * Install BusyBox from bundled APK assets. BusyBox provides ~300 POSIX tools
     * (sh, cp, mv, chmod, tar, grep, wget, etc.) in a single ~1MB binary, making
     * Orbit-AI self-contained without requiring Termux.
     *
     * For each applet we need, a small shell wrapper script is created that
     * delegates to the BusyBox binary. We use wrappers instead of symlinks
     * because Android's seccomp filter blocks the symlink() syscall for
     * non-root processes on many devices.
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

            // Ensure executable (Java API may silently fail on Android)
            busyboxFile.setExecutable(true)
            if (!busyboxFile.canExecute()) {
                Runtime.getRuntime()
                    .exec(arrayOf("chmod", "+x", busyboxFile.absolutePath))
                    .waitFor()
            }

            // Create wrapper scripts for each applet
            for (applet in BUSYBOX_APPLETS) {
                val wrapperFile = File(binDir, applet)
                if (!wrapperFile.exists()) {
                    wrapperFile.writeText(
                        "$WRAPPER_HEADER\n${WRAPPER_TEMPLATE.format(applet)}\n"
                    )
                    wrapperFile.setExecutable(true)
                    if (!wrapperFile.canExecute()) {
                        Runtime.getRuntime()
                            .exec(arrayOf("chmod", "+x", wrapperFile.absolutePath))
                            .waitFor()
                    }
                }
            }

            Log.i(TAG, "BusyBox installed (${BUSYBOX_APPLETS.size} applet wrappers)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "BusyBox install skipped: ${e.message}")
            // BusyBox is a fallback — system may have Termux or toybox
            false
        }
    }
}
