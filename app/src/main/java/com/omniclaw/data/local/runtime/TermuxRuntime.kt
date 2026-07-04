package com.omniclaw.data.local.runtime

import android.content.Context
import com.omniclaw.core.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry

private const val TAG = "TermuxRuntime"

/**
 * Termux-based runtime — uses Termux's bootstrap zip as the root filesystem.
 *
 * WHY THIS WORKS WHEN ALPINE DIDN'T:
 * Termux binaries are compiled with the Android NDK against Bionic (Android's
 * libc) and use /system/bin/linker64 as their ELF interpreter — which exists
 * on EVERY Android device. Alpine binaries use musl libc + /lib/ld-musl-aarch64.so.1
 * which doesn't exist on Android.
 *
 * With Termux's bootstrap, binaries run NATIVELY — no PRoot needed.
 */
class TermuxRuntime(private val context: Context) {

    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val prefixDir = File(runtimeDir, "termux-rootfs")
    val binDir = File(prefixDir, "bin")
    val agentsDir = File(runtimeDir, "agents")
    val workspaceDir = File(runtimeDir, "workspace")
    val tmpDir = File(runtimeDir, "tmp")

    private val nativeLibDir: String by lazy {
        context.applicationInfo.nativeLibraryDir ?: ""
    }

    val isInstalled: Boolean get() = File(binDir, "bash").exists()

    // Track install progress so UI can show it
    private var _installInProgress = false
    val installInProgress: Boolean get() = _installInProgress

    init {
        runtimeDir.mkdirs()
        agentsDir.mkdirs()
        workspaceDir.mkdirs()
        tmpDir.mkdirs()
    }

    suspend fun install(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled) {
            FileLogger.i(TAG, "Termux rootfs already installed")
            return@withContext true
        }
        if (_installInProgress) {
            FileLogger.w(TAG, "Termux install already in progress, skipping duplicate call")
            return@withContext false
        }

        _installInProgress = true
        FileLogger.i(TAG, "Termux install start")
        val startTime = System.currentTimeMillis()

        try {
            // Step 1: Copy bootstrap zip from assets
            onProgress(0.05f, "Copying Termux bootstrap...")
            val assetStream = context.assets.open("termux-bootstrap.zip")
            val tempZip = File(tmpDir, "termux-bootstrap.zip")
            tempZip.parentFile?.mkdirs()

            val copyStart = System.currentTimeMillis()
            assetStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }
            FileLogger.i(TAG, "Bootstrap zip copied", "bytes=${tempZip.length()} time=${System.currentTimeMillis() - copyStart}ms")

            // Step 2: Extract using Java ZipInputStream (reliable, no dependency on system unzip)
            onProgress(0.1f, "Extracting rootfs (this takes a minute)...")
            FileLogger.i(TAG, "Extraction start")
            val extractStart = System.currentTimeMillis()
            var fileCount = 0
            java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(prefixDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        outFile.setExecutable(true, false)
                        fileCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val extractDuration = System.currentTimeMillis() - extractStart
            FileLogger.i(TAG, "Extraction success", "files=$fileCount time=${extractDuration}ms")

            tempZip.delete()

            // Step 3: Create symlinks from SYMLINKS.txt
            onProgress(0.4f, "Creating symlinks...")
            FileLogger.i(TAG, "Symlink creation start")
            val symlinksFile = File(prefixDir, "SYMLINKS.txt")
            if (symlinksFile.exists()) {
                val symlinkStart = System.currentTimeMillis()
                var symlinkCount = 0
                var symlinkFailed = 0
                symlinksFile.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split("←")
                    if (parts.size == 2) {
                        val target = parts[0].trim().removePrefix("./")
                        val linkName = parts[1].trim().removePrefix("./")
                        val linkFile = File(prefixDir, linkName)
                        try {
                            linkFile.parentFile?.mkdirs()
                            if (linkFile.exists()) linkFile.delete()
                            Runtime.getRuntime().exec(arrayOf(
                                "ln", "-s", target, linkFile.absolutePath
                            )).waitFor()
                            symlinkCount++
                        } catch (_: Exception) {
                            symlinkFailed++
                        }
                    }
                }
                FileLogger.i(TAG, "Symlinks created", "count=$symlinkCount failed=$symlinkFailed time=${System.currentTimeMillis() - symlinkStart}ms")
            }

            // Verify /bin/sh exists (critical — needed by all shell scripts)
            val binSh = File(prefixDir, "bin/sh")
            val binBash = File(prefixDir, "bin/bash")
            FileLogger.i(TAG, "Binary check", "sh=${binSh.exists()} bash=${binBash.exists()} sh_canonical=${binSh.canonicalPath}")

            // Step 4: Set up environment
            onProgress(0.5f, "Configuring environment...")
            setupTermuxEnvironment()
            FileLogger.i(TAG, "Environment configured")

            // Step 5: Install packages
            onProgress(0.6f, "Installing nodejs, git, python3 (downloads ~50MB)...")
            FileLogger.i(TAG, "pkg install start", "packages=nodejs,git,python3")
            val pkgStart = System.currentTimeMillis()
            val pkgResult = executeInTermux("apt update 2>&1 && apt install -y nodejs git python3 2>&1")
            val pkgDuration = System.currentTimeMillis() - pkgStart
            FileLogger.i(TAG, "pkg install result", "exit=${pkgResult.exitCode} time=${pkgDuration}ms output=${pkgResult.output.take(500)}")

            if (pkgResult.exitCode != 0) {
                FileLogger.w(TAG, "pkg install had issues, checking what's available")
                // Check if node was installed despite errors
                val nodeCheck = File(binDir, "node")
                if (!nodeCheck.exists()) {
                    FileLogger.e(TAG, "Node not found after install", "path=${nodeCheck.absolutePath}")
                }
            }

            // Step 6: Install npm (comes with nodejs in Termux, but verify)
            val npmCheck = File(binDir, "npm")
            FileLogger.i(TAG, "Tool check", "node=${File(binDir, "node").exists()} npm=${npmCheck.exists()} git=${File(binDir, "git").exists()}")

            val totalDuration = System.currentTimeMillis() - startTime
            FileLogger.i(TAG, "Termux install success", "time=${totalDuration}ms")
            onProgress(1.0f, "Ready")
            true
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            FileLogger.e(TAG, "Termux install failed", e, "time=${totalDuration}ms reason=${e.message}")
            FileLogger.e(TAG, "SUMMARY: Termux install failed because ${e.message}")
            onProgress(0.0f, "Failed: ${e.message}")
            false
        } finally {
            _installInProgress = false
        }
    }

    private fun setupTermuxEnvironment() {
        File(prefixDir, "etc/apt/sources.list.d").mkdirs()
        File(prefixDir, "var/log").mkdirs()
        File(prefixDir, "tmp").mkdirs()

        val sourcesList = File(prefixDir, "etc/apt/sources.list")
        sourcesList.parentFile?.mkdirs()
        sourcesList.writeText("deb https://packages.termux.dev/apt/termux-main/ stable main\n")

        val envScript = File(prefixDir, "bin/orbit-env")
        envScript.writeText("""#!/system/bin/sh
export PREFIX="${prefixDir.absolutePath}"
export PATH="${prefixDir.absolutePath}/bin:${nativeLibDir}:/system/bin:/system/xbin"
export LD_LIBRARY_PATH="${prefixDir.absolutePath}/lib:${nativeLibDir}"
export LD_PRELOAD="${prefixDir.absolutePath}/lib/libtermux-exec-ld-preload.so"
export HOME="${runtimeDir.absolutePath}"
export TMPDIR="${prefixDir.absolutePath}/tmp"
export LANG=en_US.UTF-8
export TERM=xterm-256color
""")
        envScript.setExecutable(true)
    }

    suspend fun executeInTermux(
        command: String,
        stdin: String = ""
    ): CommandResult = withContext(Dispatchers.IO) {
        if (!isInstalled) {
            return@withContext CommandResult("Termux rootfs not installed.", -1, command)
        }

        val execStart = System.currentTimeMillis()
        FileLogger.d(TAG, "Termux exec start", "cmd=${command.take(200)}")

        try {
            val fullCommand = ". ${prefixDir.absolutePath}/bin/orbit-env && $command"
            val pb = ProcessBuilder("/system/bin/sh", "-c", fullCommand)
            pb.directory(runtimeDir)

            val env = pb.environment()
            env["PREFIX"] = prefixDir.absolutePath
            env["PATH"] = "${prefixDir.absolutePath}/bin:${nativeLibDir}:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = "${prefixDir.absolutePath}/lib:${nativeLibDir}"
            val ldPreload = File(prefixDir, "lib/libtermux-exec-ld-preload.so")
            if (ldPreload.exists()) {
                env["LD_PRELOAD"] = ldPreload.absolutePath
            }
            env["HOME"] = runtimeDir.absolutePath
            env["TMPDIR"] = File(prefixDir, "tmp").absolutePath
            env["LANG"] = "en_US.UTF-8"
            env["TERM"] = "xterm-256color"

            val process = pb.start()

            if (stdin.isNotEmpty()) {
                process.outputStream.write(stdin.toByteArray())
                process.outputStream.flush()
            }
            process.outputStream.close()

            val stdoutText = StringBuilder()
            val stderrText = StringBuilder()

            val stdoutThread = Thread {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { stdoutText.appendLine(it) }
                }
            }
            val stderrThread = Thread {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { stderrText.appendLine(it) }
                }
            }
            stdoutThread.start()
            stderrThread.start()

            process.waitFor()
            stdoutThread.join()
            stderrThread.join()

            val exitCode = process.exitValue()
            val output = stdoutText.toString().trim()
            val stderr = stderrText.toString().trim()
            val execDuration = System.currentTimeMillis() - execStart

            if (exitCode != 0) {
                FileLogger.w(TAG, "Termux exec failed", "exit=$exitCode time=${execDuration}ms stderr=${stderr.take(300)}")
            } else {
                FileLogger.d(TAG, "Termux exec success", "exit=0 time=${execDuration}ms output=${output.length}chars")
            }

            val combinedOutput = if (stderr.isNotBlank()) "$output\n$stderr" else output
            CommandResult(combinedOutput.trim(), exitCode, command)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Termux exec exception", e, "cmd=${command.take(100)} reason=${e.message}")
            CommandResult("Error: ${e.message}", -1, command)
        }
    }

    fun isToolInstalled(tool: String): Boolean = File(binDir, tool).exists()

    fun getNodePath(): String? {
        val node = File(binDir, "node")
        return if (node.exists() && node.canExecute()) node.absolutePath else null
    }
}

data class CommandResult(
    val output: String,
    val exitCode: Int,
    val command: String
)
