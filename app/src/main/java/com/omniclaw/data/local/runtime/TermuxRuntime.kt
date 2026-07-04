package com.omniclaw.data.local.runtime

import android.content.Context
import com.omniclaw.core.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "TermuxRuntime"

/**
 * Termux-based runtime that runs the Termux bootstrap rootfs under PRoot.
 *
 * WHY PRoot IS REQUIRED:
 *   Android 10+ (API 29+) with targetSdk >= 29 enforces W^X via SELinux:
 *   files under /data/data/<pkg>/files/ get the `app_data_file` label,
 *   which does NOT allow execve(). Trying to exec any binary extracted
 *   from the Termux bootstrap zip fails with "Permission denied" (EACCES).
 *
 *   The ONLY writable+executable location available to a targetSdk>=29
 *   app is /data/app/<pkg>/lib/<abi>/ (label `apk_data_file`), which is
 *   populated at install time from jniLibs. We bundle libproot.so there.
 *   PRoot uses ptrace to intercept every execve syscall made by its
 *   children, so binaries inside the rootfs are mapped into memory by
 *   proot itself (only needing read access, which app_data_file allows)
 *   and never exec'd by the kernel.
 *
 * Architecture:
 *   1. termux-bootstrap.zip -> extracted to prefixDir (app_data_file, read-only)
 *   2. libproot.so + libproot_loader.so -> jniLibs -> /data/app/<pkg>/lib/arm64/
 *   3. All commands run as: libproot.so -r prefixDir -b ... -- /bin/sh -c "cmd"
 *   4. Inside the rootfs, /bin/sh, /bin/apt, /bin/node etc. all work because
 *      proot ptrace-intercepts the execve calls.
 */
class TermuxRuntime(private val context: Context) {

    val runtimeDir = File(context.filesDir, "orbit_runtime")
    val prefixDir = File(runtimeDir, "termux-rootfs")
    val binDir = File(prefixDir, "bin")
    val agentsDir = File(runtimeDir, "agents")
    val workspaceDir = File(runtimeDir, "workspace")
    val tmpDir = File(runtimeDir, "tmp")

    /**
     * Directory where Android extracts jniLibs at install time.
     * SELinux label `apk_data_file` allows execve() here — this is
     * the ONLY place we can exec native binaries on Android 10+.
     */
    private val nativeLibDir: String by lazy {
        context.applicationInfo.nativeLibraryDir ?: ""
    }

    /** Path to the PRoot binary in the exec-allowed lib directory. */
    val prootPath: String by lazy { "$nativeLibDir/libproot.so" }
    val prootLoaderPath: String by lazy { "$nativeLibDir/libproot_loader.so" }

    val isInstalled: Boolean get() = File(binDir, "bash").exists()

    private var _installInProgress = false
    val installInProgress: Boolean get() = _installInProgress

    init {
        runtimeDir.mkdirs()
        agentsDir.mkdirs()
        workspaceDir.mkdirs()
        tmpDir.mkdirs()
    }

    /**
     * Install the Termux bootstrap rootfs by extracting the bundled zip.
     * After extraction, packages (nodejs, git, python3) are installed via
     * apt — run inside the rootfs through PRoot so apt can exec dpkg, ar,
     * tar, etc. without hitting the W^X SELinux block.
     */
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

            // Step 2: Extract using Java ZipInputStream
            onProgress(0.1f, "Extracting rootfs (this takes a minute)...")
            FileLogger.i(TAG, "Extraction start")
            val extractStart = System.currentTimeMillis()
            var fileCount = 0
            java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
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
                    val parts = line.split("\u2190")
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

            val binSh = File(prefixDir, "bin/sh")
            val binBash = File(prefixDir, "bin/bash")
            FileLogger.i(TAG, "Binary check", "sh=${binSh.exists()} bash=${binBash.exists()} sh_canonical=${binSh.canonicalPath}")

            // Step 4: Set up apt sources.list, DNS, and writable dirs
            onProgress(0.5f, "Configuring environment...")
            File(prefixDir, "etc/apt/sources.list.d").mkdirs()
            File(prefixDir, "etc/apt/apt.conf.d").mkdirs()
            File(prefixDir, "etc/apt/preferences.d").mkdirs()
            File(prefixDir, "var/log").mkdirs()
            File(prefixDir, "tmp").mkdirs()
            // Create /home inside the rootfs — used as HOME for commands
            // running under PRoot without the -0 (fake root) flag.
            File(prefixDir, "home").mkdirs()
            // dpkg state directories — apt needs these to track installs.
            // The bootstrap may include them, but create if missing.
            File(prefixDir, "var/lib/dpkg/info").mkdirs()
            File(prefixDir, "var/lib/dpkg/updates").mkdirs()
            File(prefixDir, "var/lib/dpkg/parts").mkdirs()
            File(prefixDir, "var/cache/apt/archives/partial").mkdirs()
            File(prefixDir, "var/cache/apt/archives/partial").mkdirs()
            val dpkgStatus = File(prefixDir, "var/lib/dpkg/status")
            if (!dpkgStatus.exists()) dpkgStatus.writeText("")
            val dpkgAvailable = File(prefixDir, "var/lib/dpkg/available")
            if (!dpkgAvailable.exists()) dpkgAvailable.writeText("")
            // DNS resolution — PRoot bind-mounts /system but apt needs
            // /etc/resolv.conf inside the rootfs to resolve package URLs.
            val resolvConf = File(prefixDir, "etc/resolv.conf")
            if (!resolvConf.exists()) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            val sourcesList = File(prefixDir, "etc/apt/sources.list")
            sourcesList.parentFile?.mkdirs()
            sourcesList.writeText("deb https://packages.termux.dev/apt/termux-main/ stable main\n")
            FileLogger.i(TAG, "Environment configured")

            // Step 5: Install packages via PRoot + apt
            // This is the critical step that previously failed with "Permission denied"
            // because apt, dpkg, ar etc. couldn't be exec'd from app_data_file.
            // Now they run under PRoot, which ptrace-intercepts the execve calls.
            onProgress(0.6f, "Installing nodejs, git, python3 (downloads ~50MB)...")
            FileLogger.i(TAG, "pkg install start", "packages=nodejs,git,python3")
            val pkgStart = System.currentTimeMillis()
            val pkgResult = executeInTermux("apt update -y && apt install -y nodejs git python3")
            val pkgDuration = System.currentTimeMillis() - pkgStart
            FileLogger.i(TAG, "pkg install result", "exit=${pkgResult.exitCode} time=${pkgDuration}ms output=${pkgResult.output.take(500)}")

            if (pkgResult.exitCode != 0) {
                FileLogger.w(TAG, "pkg install had issues, checking what's available")
                val nodeCheck = File(binDir, "node")
                if (!nodeCheck.exists()) {
                    FileLogger.e(TAG, "Node not found after install", "path=${nodeCheck.absolutePath}")
                }
            }

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

    /**
     * Execute a command inside the Termux rootfs under PRoot.
     *
     * PRoot flags used:
     *   --kill-on-exit     — kill all children if proot dies
     *   -0                 — fake root uid (some scripts expect this)
     *   --link2symlink     — required on Android for hardlink emulation
     *   -r <rootfs>        — use prefixDir as the root
     *   -b <src>:<dst>     — bind mount (Android system dirs + our dirs)
     *   -w <dir>           — set working dir inside rootfs
     *
     * The command is passed to /bin/sh -c inside the rootfs. All execve
     * calls made by sh (and its children) are intercepted by proot via
     * ptrace, so binaries in the rootfs run despite being on app_data_file.
     */
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
            // Build PRoot argv
            val prootArgs = mutableListOf(
                prootPath,
                "--kill-on-exit",
                // NOTE: do NOT use -0 (fake root uid). Termux patches apt to
                // refuse running as root with "Ability to run this command as
                // root has been disabled permanently for safety purposes".
                // Termux is designed to run as the app's regular UID.
                "--link2symlink",
                "-r", prefixDir.absolutePath,
                // Bind Android system directories so binaries can find libs, /dev, /proc etc.
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/system",
                "-b", "/vendor",
                "-b", "/apex",
                "-b", "/odm",
                "-b", "/linkerconfig",
                "-b", "/data/local/tmp:/tmp",
                // CRITICAL: Termux binaries have /data/data/com.termux/files/usr
                // hardcoded as their PREFIX. apt looks for config at
                // /data/data/com.termux/files/usr/etc/apt/apt.conf.d/
                // and symlinks point to /data/data/com.termux/files/usr/bin/*.
                // Bind-mount our prefixDir over that path so everything resolves.
                "-b", "${prefixDir.absolutePath}:/data/data/com.termux/files/usr",
                // Make our runtime dir visible inside the rootfs at /orbit
                "-b", "$runtimeDir:/orbit",
                // Set working directory to / (inside rootfs)
                "-w", "/",
                // Exec /bin/sh -c "command"
                "/bin/sh", "-c", command
            )

            val pb = ProcessBuilder(prootArgs)
            pb.directory(runtimeDir)

            val env = pb.environment()
            // PRoot loader path (proot dlopens this for ptrace injection)
            env["PROOT_LOADER"] = prootLoaderPath
            env["PROOT_LOADER_32"] = prootLoaderPath
            env["PROOT_TMP_DIR"] = tmpDir.absolutePath
            // Inside the rootfs, set up Termux-like env.
            // PREFIX is the Termux convention — many scripts expect it.
            env["PREFIX"] = prefixDir.absolutePath
            // PATH inside the rootfs: Termux puts binaries in /bin (symlinked
            // from prefix/bin). Include /system/bin for toybox applets.
            env["PATH"] = "/bin:/system/bin:/system/xbin"
            // HOME must be a writable directory inside the rootfs. /home is
            // in the bootstrap and writable by any user (no -0 needed).
            env["HOME"] = "/home"
            env["TMPDIR"] = "/tmp"
            env["LANG"] = "en_US.UTF-8"
            env["TERM"] = "xterm-256color"
            // LD_LIBRARY_PATH includes Termux's lib dir (symlinks to prefix/lib)
            // plus system lib dirs.
            env["LD_LIBRARY_PATH"] = "/lib:/usr/lib:/system/lib64"

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

    /**
     * Returns the path to node INSIDE the rootfs (e.g. /data/.../bin/node).
     * Callers that want to exec node directly must do so via executeInTermux()
     * — direct execve on this path will be blocked by SELinux.
     */
    fun getNodePath(): String? {
        val node = File(binDir, "node")
        return if (node.exists() && node.canExecute()) node.absolutePath else null
    }

    fun getPrefixPath(): String = prefixDir.absolutePath
    fun getRuntimePath(): String = runtimeDir.absolutePath
}

data class CommandResult(
    val output: String,
    val exitCode: Int,
    val command: String
)
