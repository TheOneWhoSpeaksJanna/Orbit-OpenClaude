package com.omniclaw.data.local.runner

import com.omniclaw.data.local.runtime.OmniClawRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val ERROR_EXIT_CODE = -1

private const val SHIZUKU_NOT_RUNNING = "Shizuku is not running or unavailable."
private const val SHIZUKU_API_CHANGED = "Shizuku API changed \u2014 newProcess method not found. Please update the app."
private const val SHIZUKU_PERMISSION_DENIED = "Shizuku permission denied. Grant permission in the Shizuku app."
private const val ERROR_COMMAND_PREFIX = "Error executing local command: "
private const val ERROR_PRIVILEGED_PREFIX = "Error executing privileged command via Shizuku: "

data class CommandResult(val output: String, val exitCode: Int, val command: String)

class LocalCommandRunner(
    private val runtimeManager: OmniClawRuntimeManager
) {

    /**
     * Returns the shell command array to use.
     *
     * On Android 10+, wrapper scripts in app-private directories cannot be made
     * executable. Instead of depending on system `sh` + PATH wrappers, we call
     * the BusyBox binary directly when available: `busybox sh -c <cmd>`.
     * When BusyBox is absent, fall back to the system shell.
     */
    private fun shellCommand(command: String): List<String> {
        val busyboxPath = runtimeManager.busyBoxPath()
        if (busyboxPath != null) {
            return listOf(busyboxPath, "sh", "-c", command)
        }
        return listOf("sh", "-c", command)
    }

    private fun setupProcessBuilder(command: String): ProcessBuilder {
        val processBuilder = ProcessBuilder(shellCommand(command))
        processBuilder.directory(runtimeManager.runtimeDir)
        val env = processBuilder.environment()
        val currentPath = env["PATH"] ?: ""
        env["PATH"] = "${runtimeManager.binDir.absolutePath}:$currentPath"
        return processBuilder
    }

    suspend fun executeCommandStreamed(command: String, onOutput: (String) -> Unit): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val process = setupProcessBuilder(command).start()
                val outputBuilder = StringBuilder()

                val stdInThread = Thread {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        generateSequence { reader.readLine() }.forEach { line ->
                            outputBuilder.appendLine(line)
                            onOutput(line)
                        }
                    }
                }

                val stdErrThread = Thread {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        generateSequence { reader.readLine() }.forEach { line ->
                            outputBuilder.appendLine(line)
                            onOutput(line)
                        }
                    }
                }

                stdInThread.start()
                stdErrThread.start()

                process.waitFor()
                stdInThread.join()
                stdErrThread.join()

                CommandResult(outputBuilder.toString().trim(), process.exitValue(), command)
            } catch (e: Exception) {
                val errorMsg = "$ERROR_COMMAND_PREFIX${e.message}"
                onOutput(errorMsg)
                CommandResult(errorMsg, ERROR_EXIT_CODE, command)
            }
        }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = setupProcessBuilder(command).start()
            val output = buildString {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    generateSequence { reader.readLine() }.forEach { line ->
                        appendLine(line)
                    }
                }
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    generateSequence { reader.readLine() }.forEach { line ->
                        appendLine(line)
                    }
                }
            }
            process.waitFor()
            CommandResult(output.trim(), process.exitValue(), command)
        } catch (e: Exception) {
            CommandResult("$ERROR_COMMAND_PREFIX${e.message}", ERROR_EXIT_CODE, command)
        }
    }

    suspend fun executePrivilegedCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext CommandResult(SHIZUKU_NOT_RUNNING, ERROR_EXIT_CODE, command)
        }
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null, shellCommand(command).toTypedArray(), null, null
            ) as Process

            val output = buildString {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    generateSequence { reader.readLine() }.forEach { line ->
                        appendLine(line)
                    }
                }
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    generateSequence { reader.readLine() }.forEach { line ->
                        appendLine(line)
                    }
                }
            }
            process.waitFor()
            CommandResult(output.trim(), process.exitValue(), command)
        } catch (e: NoSuchMethodException) {
            CommandResult(SHIZUKU_API_CHANGED, ERROR_EXIT_CODE, command)
        } catch (e: SecurityException) {
            CommandResult(SHIZUKU_PERMISSION_DENIED, ERROR_EXIT_CODE, command)
        } catch (e: Exception) {
            CommandResult("$ERROR_PRIVILEGED_PREFIX${e.message}", ERROR_EXIT_CODE, command)
        }
    }
}
