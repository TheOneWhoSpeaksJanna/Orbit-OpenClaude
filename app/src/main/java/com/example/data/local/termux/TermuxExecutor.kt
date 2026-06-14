package com.example.data.local.termux

import com.example.domain.model.TermuxLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class TermuxExecutor {

    suspend fun executeCommand(command: String): TermuxLog {
        return withContext(Dispatchers.IO) {
            try {
                // Warning: On real Android 10+ standard app, executing 'sh' may have restrictions
                // but for an AI workspace prototype targeting Termux/Linux environments, this is the standard way.
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                
                val outputText = StringBuilder()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputText.append(line).append("\n")
                }
                
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                while (errorReader.readLine().also { line = it } != null) {
                    outputText.append(line).append("\n")
                }

                val exitCode = process.waitFor()

                TermuxLog(
                    id = UUID.randomUUID().toString(),
                    command = command,
                    output = outputText.toString().trim(),
                    exitCode = exitCode,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                TermuxLog(
                    id = UUID.randomUUID().toString(),
                    command = command,
                    output = e.message ?: "Unknown Execution Error",
                    exitCode = -1,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
}
