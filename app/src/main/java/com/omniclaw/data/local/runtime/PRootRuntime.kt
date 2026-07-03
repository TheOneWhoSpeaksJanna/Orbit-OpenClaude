package com.omniclaw.data.local.runtime

import android.content.Context
import com.omniclaw.core.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "PRootRuntime"

/**
 * PRoot-based Linux runtime manager.
 *
 * This is the NEW architecture for running AI agents on Android. It replaces
 * the old BusyBox-based approach which was unreliable due to:
 *  1. SELinux W^X enforcement blocking exec of files in filesDir
 *  2. Shared library dependencies (Termux debs need libc++, openssl, etc.)
 *  3. Stale package URLs causing 404 errors
 *
 * HOW IT WORKS:
 *  1. Alpine Linux minirootfs (3.9MB compressed) is bundled in APK assets
 *  2. PRoot static binary (402KB) is bundled as libproot.so in jniLibs
 *  3. PRoot loader (18KB) is bundled as libproot_loader.so in jniLibs
 *  4. On first launch, the rootfs is extracted to filesDir/alpine-rootfs/
 *  5. Node.js, npm, and git are installed via apk (Alpine's package manager)
 *     inside the proot environment on first launch
 *  6. Agents run inside the proot environment, where they have a full Linux
 *     filesystem with proper /usr/bin/node, /usr/bin/git, etc.
 *
 * WHY PRoot WORKS WHEN BusyBox DIDN'T:
 *  - PRoot is a real binary, so it CAN be exec'd from nativeLibraryDir
 *    (which has the app_lib_data_file SELinux label that allows exec)
 *  - PRoot uses ptrace (unprivileged) to intercept syscalls and translate
 *    paths — no root, no chroot, no mount needed
 *  - Inside the proot environment, agents see a standard Linux filesystem
 *    with /usr/bin/node, /usr/bin/git, /usr/lib, etc. — no wrapper scripts
 *    needed, no PATH hacks, no LD_LIBRARY_PATH issues
 *  - The proot environment has its own /tmp, /etc, /var — completely
 *    isolated from Android's filesystem quirks
 *
 * AGENT EXECUTION FLOW:
 *  1. User sends a message
 *  2. ChatViewModel calls prootRuntime.executeInRootfs("node /agents/openclaude/cli.js", stdin)
 *  3. PRootRuntime builds the proot command line:
 *     libproot.so --rootfs=.../alpine-rootfs --cwd=/root
 *       --bind=.../agents:/agents --bind=.../workspace:/workspace
 *       -- /usr/bin/node /agents/openclaude/cli.js
 *  4. The command runs inside the Alpine environment with full Linux compat
 *  5. Output is captured and returned to the user
 */
class PRootRuntime(private val context: Context) {

    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val rootfsDir = File(runtimeDir, "alpine-rootfs")
    val agentsDir = File(runtimeDir, "agents")
    val workspaceDir = File(runtimeDir, "workspace")
    val tmpDir = File(runtimeDir, "tmp")

    private val nativeLibDir: String by lazy {
        context.applicationInfo.nativeLibraryDir
            ?: throw IllegalStateException("nativeLibraryDir not available")
    }

    val prootBinary: String by lazy { "$nativeLibDir/libproot.so" }
    val prootLoader: String by lazy { "$nativeLibDir/libproot_loader.so" }

    val isRootfsInstalled: Boolean get() = File(rootfsDir, "bin/sh").exists()

    init {
        runtimeDir.mkdirs()
        agentsDir.mkdirs()
        workspaceDir.mkdirs()
        tmpDir.mkdirs()
    }

    /**
     * Extract the bundled Alpine rootfs from APK assets to filesDir.
     * Safe to call repeatedly — skips if already extracted.
     *
     * @param onProgress called with (0.0 to 1.0, statusMessage) during extraction
     * @return true if rootfs is ready after this call
     */
    suspend fun installRootfs(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRootfsInstalled) {
            FileLogger.i(TAG, "Rootfs already installed at ${rootfsDir.absolutePath}")
            return@withContext true
        }

        try {
            onProgress(0.1f, "Extracting Alpine Linux rootfs...")
            FileLogger.i(TAG, "Extracting Alpine rootfs from APK assets to ${rootfsDir.absolutePath}")

            rootfsDir.mkdirs()

            // Try both .tar.gz and .tar — AAPT2 may have decompressed the
            // .tar.gz into a plain .tar (depending on noCompress config).
            val assetStream = try {
                FileLogger.d(TAG, "Trying alpine-rootfs.tar.gz...")
                context.assets.open("alpine-rootfs.tar.gz")
            } catch (e: Exception) {
                FileLogger.d(TAG, "alpine-rootfs.tar.gz not found, trying alpine-rootfs.tar...")
                try {
                    context.assets.open("alpine-rootfs.tar")
                } catch (e2: Exception) {
                    FileLogger.e(TAG, "Neither alpine-rootfs.tar.gz nor alpine-rootfs.tar found in assets!")
                    // List available assets for debugging
                    val assets = context.assets.list("") ?: arrayOf()
                    FileLogger.d(TAG, "Available assets: ${assets.joinToString(", ")}")
                    throw e2
                }
            }

            val tempArchive = File(tmpDir, "alpine-rootfs.tar")
            tempArchive.parentFile?.mkdirs()

            // Copy asset to temp file
            assetStream.use { input ->
                FileOutputStream(tempArchive).use { output ->
                    input.copyTo(output)
                }
            }

            onProgress(0.3f, "Extracting rootfs archive...")

            // Extract: try gzip first (if the file is still .tar.gz),
            // then fall back to plain tar (if AAPT2 decompressed it).
            val extractRootfs: (java.io.InputStream) -> Unit = { input ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(input).use { tis ->
                    var entry = tis.nextEntry
                    while (entry != null) {
                        val outFile = File(rootfsDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                tis.copyTo(fos)
                            }
                            // Preserve executable bits
                            if (entry.mode and 0b001000000 != 0) {
                                outFile.setExecutable(true, false)
                            }
                        }
                        entry = tis.nextEntry
                    }
                }
            }

            try {
                // Try as gzip first
                java.util.zip.GZIPInputStream(tempArchive.inputStream()).use { gzip ->
                    extractRootfs(gzip)
                }
                FileLogger.d(TAG, "Extracted as gzip")
            } catch (gzipEx: Exception) {
                FileLogger.d(TAG, "Not gzip, trying as plain tar: ${gzipEx.message}")
                // Fall back to plain tar
                tempArchive.inputStream().use { plain ->
                    extractRootfs(plain)
                }
                FileLogger.d(TAG, "Extracted as plain tar")
            }

            tempArchive.delete()
            onProgress(0.7f, "Rootfs extracted, configuring...")

            // Create necessary directories
            File(rootfsDir, "root").mkdirs()
            File(rootfsDir, "tmp").mkdirs()
            File(rootfsDir, "etc").mkdirs()

            // Set up DNS resolution
            File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

            // Set up Alpine repositories for apk
            File(rootfsDir, "etc/apk/repositories").writeText(
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/main\n" +
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/community\n"
            )

            // Create /agents and /workspace mount points (will be bind-mounted)
            File(rootfsDir, "agents").mkdirs()
            File(rootfsDir, "workspace").mkdirs()

            onProgress(0.8f, "Rootfs configured. Installing nodejs, npm, git, gh, python3...")

            // Install all base packages inside the rootfs via apk.
            // These are the "basic needs" pre-installed so the user doesn't
            // have to wait for individual installs.
            val installResult = executeInRootfs(
                "apk update && apk add --no-cache nodejs npm git gh python3 py3-pip curl wget openssh-client make gcc g++",
                "",
                workingDir = "/root"
            )

            if (installResult.exitCode != 0) {
                FileLogger.e(TAG, "apk install failed: ${installResult.output}")
                onProgress(0.0f, "Failed to install packages: ${installResult.output.take(200)}")
                return@withContext false
            }

            onProgress(1.0f, "Rootfs ready with nodejs, npm, git, gh, python3, pip, curl, wget, ssh, make, gcc")
            FileLogger.i(TAG, "Rootfs installation complete")
            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Rootfs installation failed: ${e.message}", e)
            onProgress(0.0f, "Installation failed: ${e.message}")
            false
        }
    }

    /**
     * Execute a command inside the PRoot Alpine environment.
     *
     * @param command The shell command to run (e.g. "node /agents/openclaude/cli.js")
     * @param stdin Text to pipe to the command's stdin
     * @param workingDir Working directory inside the rootfs (default: /root)
     * @return CommandResult with output, exit code, and the original command
     */
    suspend fun executeInRootfs(
        command: String,
        stdin: String = "",
        workingDir: String = "/root"
    ): CommandResult = withContext(Dispatchers.IO) {
        if (!isRootfsInstalled) {
            return@withContext CommandResult(
                "Rootfs not installed. Call installRootfs() first.",
                -1,
                command
            )
        }

        val prootCmd = buildProotCommand(command, workingDir)
        FileLogger.d(TAG, "EXEC in rootfs: $command")
        FileLogger.d(TAG, "PRoot cmd: ${prootCmd.joinToString(" ")}")

        try {
            val pb = ProcessBuilder(prootCmd)
            pb.directory(runtimeDir)
            pb.redirectErrorStream(false)

            // Set up environment for PRoot
            val env = pb.environment()
            env["PROOT_LOADER"] = prootLoader
            env["PROOT_NO_SECCOMP"] = "1"
            env["PROOT_TMP_DIR"] = tmpDir.absolutePath
            env["HOME"] = "/root"
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            env["TERM"] = "xterm-256color"
            env["LANG"] = "C.UTF-8"
            // Remove LD_PRELOAD — it conflicts with ptrace
            env.remove("LD_PRELOAD")

            val process = pb.start()

            // Write stdin
            if (stdin.isNotEmpty()) {
                process.outputStream.write(stdin.toByteArray())
                process.outputStream.flush()
            }
            process.outputStream.close()

            // Read stdout and stderr in parallel
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

            if (exitCode != 0) {
                FileLogger.w(TAG, "Command exited $exitCode: $command")
                if (stderr.isNotBlank()) {
                    FileLogger.w(TAG, "stderr: ${stderr.take(500)}")
                }
            }

            // Return stdout + stderr combined (like a terminal would show)
            val combinedOutput = if (stderr.isNotBlank()) {
                "$output\n--- stderr ---\n$stderr"
            } else {
                output
            }

            CommandResult(combinedOutput.trim(), exitCode, command)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Exception executing '$command': ${e.message}", e)
            CommandResult("Error: ${e.message}", -1, command)
        }
    }

    /**
     * Build the PRoot command line for executing a command inside the rootfs.
     */
    private fun buildProotCommand(command: String, workingDir: String): List<String> {
        return listOf(
            prootBinary,
            "--kill-on-exit",
            "--rootfs=${rootfsDir.absolutePath}",
            "--cwd=$workingDir",
            "--change-id=0:0",
            // Bind standard filesystems
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            // Bind Android storage so agents can access user files
            "--bind=/sdcard:/sdcard",
            // Bind our agent code and workspace directories
            "--bind=${agentsDir.absolutePath}:/agents",
            "--bind=${workspaceDir.absolutePath}:/workspace",
            "--bind=${tmpDir.absolutePath}:/tmp",
            // Run via sh -c so we get full shell syntax (pipes, redirects, etc.)
            "/bin/sh", "-c", command
        )
    }

    /**
     * Check if a specific tool is installed in the rootfs.
     */
    fun isToolInstalled(tool: String): Boolean {
        return File(rootfsDir, "usr/bin/$tool").exists()
    }

    /**
     * Get the version of a tool (e.g. "node --version").
     */
    suspend fun getToolVersion(tool: String, args: String = "--version"): String? {
        val result = executeInRootfs("$tool $args")
        return if (result.exitCode == 0) result.output.trim() else null
    }
}

/**
 * Result of a command execution.
 */
data class CommandResult(
    val output: String,
    val exitCode: Int,
    val command: String
)
