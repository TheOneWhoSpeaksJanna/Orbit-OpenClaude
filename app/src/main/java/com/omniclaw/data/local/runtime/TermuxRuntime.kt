package com.omniclaw.data.local.runtime

import android.content.Context
import com.omniclaw.core.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "TermuxRuntime"

/**
 * Termux-based runtime — uses Termux's bootstrap zip as the root filesystem.
 *
 * WHY THIS WORKS WHEN ALPINE DIDN'T:
 * Termux binaries are compiled with the Android NDK against Bionic (Android's
 * libc) and use /system/bin/linker64 as their ELF interpreter — which exists
 * on EVERY Android device. Alpine binaries use musl libc + /lib/ld-musl-aarch64.so.1
 * which doesn't exist on Android. No amount of PRoot can fix that.
 *
 * With Termux's bootstrap, binaries run NATIVELY — no PRoot needed at all.
 * The bootstrap includes bash, tar, coreutils, apt (pkg), and all the POSIX
 * tools needed. We just extract the zip, create symlinks from SYMLINKS.txt,
 * and exec binaries directly.
 *
 * FLOW:
 * 1. Extract bootstrap zip to filesDir/termux-rootfs/
 * 2. Read SYMLINKS.txt and create symlinks programmatically
 * 3. Set LD_PRELOAD to termux-exec (redirects /bin/sh → prefix/bin/sh)
 * 4. Install nodejs, npm, git via pkg install (apt under the hood)
 * 5. Install agents via npm install
 * 6. Run agents directly: prefix/bin/node /agents/openclaude/cli.js
 *
 * No PRoot. No Alpine. No musl. No linker issues. No symlink issues.
 * Just Android-native binaries running directly.
 */
class TermuxRuntime(private val context: Context) {

    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val prefixDir = File(runtimeDir, "termux-rootfs")  // equivalent to Termux's $PREFIX
    val binDir = File(prefixDir, "bin")
    val agentsDir = File(runtimeDir, "agents")
    val workspaceDir = File(runtimeDir, "workspace")
    val tmpDir = File(runtimeDir, "tmp")

    private val nativeLibDir: String by lazy {
        context.applicationInfo.nativeLibraryDir ?: ""
    }

    val isInstalled: Boolean get() = File(binDir, "bash").exists()

    init {
        runtimeDir.mkdirs()
        agentsDir.mkdirs()
        workspaceDir.mkdirs()
        tmpDir.mkdirs()
    }

    /**
     * Extract the Termux bootstrap zip and create symlinks.
     * This replaces the Alpine rootfs + PRoot approach entirely.
     */
    suspend fun install(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled) {
            FileLogger.i(TAG, "Termux rootfs already installed")
            return@withContext true
        }

        FileLogger.i(TAG, "Termux install start")
        val startTime = System.currentTimeMillis()

        try {
            // Step 1: Extract bootstrap zip
            onProgress(0.1f, "Extracting Termux rootfs...")
            prefixDir.mkdirs()

            val assetStream = context.assets.open("termux-bootstrap.zip")
            val tempZip = File(tmpDir, "termux-bootstrap.zip")
            tempZip.parentFile?.mkdirs()

            val copyStart = System.currentTimeMillis()
            assetStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            }
            FileLogger.d(TAG, "Bootstrap zip copied", "bytes=${tempZip.length()} time=${System.currentTimeMillis() - copyStart}ms")

            // Step 2: Extract using system unzip (handles permissions correctly)
            onProgress(0.3f, "Extracting files...")
            val extractStart = System.currentTimeMillis()
            val unzipProcess = ProcessBuilder(
                "unzip", "-o", tempZip.absolutePath, "-d", prefixDir.absolutePath
            ).apply {
                environment()["PATH"] = "/system/bin:/system/xbin"
            }.start()
            val unzipExit = unzipProcess.waitFor()
            val unzipErr = unzipProcess.errorStream.bufferedReader().readText().trim()
            val extractDuration = System.currentTimeMillis() - extractStart

            if (unzipExit != 0) {
                FileLogger.e(TAG, "unzip failed", "exit=$unzipExit time=${extractDuration}ms stderr=${unzipErr.take(300)}")
                // Fall back to Java zip extraction (doesn't handle symlinks, but we fix that next)
                FileLogger.w(TAG, "Falling back to Java zip extraction")
                extractZipWithJava(tempZip, prefixDir)
            }
            FileLogger.d(TAG, "Rootfs extracted", "time=${extractDuration}ms")

            tempZip.delete()

            // Step 3: Create symlinks from SYMLINKS.txt
            onProgress(0.5f, "Creating symlinks...")
            val symlinksFile = File(prefixDir, "SYMLINKS.txt")
            if (symlinksFile.exists()) {
                val symlinkStart = System.currentTimeMillis()
                var symlinkCount = 0
                symlinksFile.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    // Format: target←linkname  (e.g. "dash←./bin/sh")
                    val parts = line.split("←")
                    if (parts.size == 2) {
                        val target = parts[0].trim().removePrefix("./")
                        val linkName = parts[1].trim().removePrefix("./")
                        val linkFile = File(prefixDir, linkName)
                        val targetFile = File(linkFile.parentFile, target)
                        try {
                            linkFile.parentFile?.mkdirs()
                            if (linkFile.exists()) linkFile.delete()
                            // Create relative symlink
                            Runtime.getRuntime().exec(arrayOf(
                                "ln", "-s", target, linkFile.absolutePath
                            )).waitFor()
                            symlinkCount++
                        } catch (_: Exception) { }
                    }
                }
                FileLogger.d(TAG, "Symlinks created", "count=$symlinkCount time=${System.currentTimeMillis() - symlinkStart}ms")
            }

            // Step 4: Set up environment
            onProgress(0.6f, "Configuring environment...")
            setupTermuxEnvironment()

            // Step 5: Install packages (nodejs, npm, git, gh, python3)
            onProgress(0.7f, "Installing nodejs, npm, git, python3...")
            FileLogger.i(TAG, "pkg install start", "packages=nodejs,npm,git,gh,python3")
            val pkgStart = System.currentTimeMillis()
            val pkgResult = executeInTermux("pkg install -y nodejs git python3 2>&1")
            val pkgDuration = System.currentTimeMillis() - pkgStart

            if (pkgResult.exitCode != 0) {
                FileLogger.w(TAG, "pkg install warning", "exit=${pkgResult.exitCode} time=${pkgDuration}ms output=${pkgResult.output.take(300)}")
                // Try apt directly
                val aptResult = executeInTermux("apt update && apt install -y nodejs git python3 2>&1")
                if (aptResult.exitCode != 0) {
                    FileLogger.e(TAG, "apt install failed", "exit=${aptResult.exitCode} output=${aptResult.output.take(300)}")
                }
            }
            FileLogger.i(TAG, "pkg install done", "time=${pkgDuration}ms")

            val totalDuration = System.currentTimeMillis() - startTime
            FileLogger.i(TAG, "Termux install success", "time=${totalDuration}ms")
            onProgress(1.0f, "Termux rootfs ready")
            true
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            FileLogger.e(TAG, "Termux install failed", e, "time=${totalDuration}ms reason=${e.message}")
            FileLogger.e(TAG, "SUMMARY: Termux install failed because ${e.message}")
            onProgress(0.0f, "Installation failed: ${e.message}")
            false
        }
    }

    /**
     * Set up the Termux environment: PATH, LD_PRELOAD, repositories, etc.
     */
    private fun setupTermuxEnvironment() {
        // Create necessary directories
        File(prefixDir, "etc/apt/sources.list.d").mkdirs()
        File(prefixDir, "var/log").mkdirs()

        // Set up apt repositories
        val sourcesList = File(prefixDir, "etc/apt/sources.list")
        sourcesList.parentFile?.mkdirs()
        sourcesList.writeText(
            "deb https://packages.termux.dev/apt/termux-main/ stable main\n"
        )

        // Set up PATH wrapper script
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

        FileLogger.d(TAG, "Termux environment configured", "prefix=${prefixDir.absolutePath}")
    }

    /**
     * Execute a command inside the Termux environment.
     * Binaries run natively (no PRoot) because they're Bionic-compiled.
     */
    suspend fun executeInTermux(
        command: String,
        stdin: String = ""
    ): CommandResult = withContext(Dispatchers.IO) {
        if (!isInstalled) {
            return@withContext CommandResult(
                "Termux rootfs not installed. Call install() first.",
                -1,
                command
            )
        }

        val execStart = System.currentTimeMillis()
        FileLogger.d(TAG, "Termux exec start", "cmd=${command.take(200)}")

        try {
            // Build the full command: source env script, then run the command
            val fullCommand = ". ${prefixDir.absolutePath}/bin/orbit-env && $command"
            val pb = ProcessBuilder("/system/bin/sh", "-c", fullCommand)
            pb.directory(runtimeDir)

            val env = pb.environment()
            env["PREFIX"] = prefixDir.absolutePath
            env["PATH"] = "${prefixDir.absolutePath}/bin:${nativeLibDir}:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = "${prefixDir.absolutePath}/lib:${nativeLibDir}"
            // LD_PRELOAD makes /bin/sh → prefix/bin/sh work
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

            val combinedOutput = if (stderr.isNotBlank()) {
                "$output\n$stderr"
            } else {
                output
            }

            CommandResult(combinedOutput.trim(), exitCode, command)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Termux exec exception", e, "cmd=${command.take(100)} reason=${e.message}")
            CommandResult("Error: ${e.message}", -1, command)
        }
    }

    /**
     * Java zip extraction fallback (doesn't handle symlinks, but we fix that
     * with SYMLINKS.txt afterward).
     */
    private fun extractZipWithJava(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    // Set all extracted files executable (SYMLINKS.txt handles the rest)
                    outFile.setExecutable(true, false)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun isToolInstalled(tool: String): Boolean {
        return File(binDir, tool).exists()
    }

    /**
     * Get the path to the node binary (for use in wrapper scripts).
     */
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
