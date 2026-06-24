package com.omniclaw.data.local.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
}
