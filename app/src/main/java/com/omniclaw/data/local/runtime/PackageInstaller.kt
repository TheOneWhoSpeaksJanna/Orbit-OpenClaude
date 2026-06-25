package com.omniclaw.data.local.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

private const val ARCH = "aarch64"
private const val CHECKSUM_SKIP = "skip"
private const val FAIL_PREFIX = "FAIL: "
private const val PROGRESS_START = 0f
private const val PROGRESS_COMPLETE = 1f
private const val BUFFER_SIZE = 4096

class PackageInstaller(
    private val runtimeManager: OmniClawRuntimeManager,
    private val httpClient: OkHttpClient
) {
    private val registryFile = File(runtimeManager.runtimeDir, "registry/packages.json")

    init {
        if (!registryFile.parentFile.exists()) {
            registryFile.parentFile.mkdirs()
        }
        if (!registryFile.exists()) {
            val defaultRegistry = JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "git")
                    put("type", "deb")
                    put("arch", ARCH)
                    put("url", "https://packages.termux.dev/apt/termux-main/pool/main/g/git/git_2.45.2_aarch64.deb")
                    put("checksum", CHECKSUM_SKIP)
                    put("install_method", "deb_extract")
                    put("test_command", "git --version")
                    put("validation_rules", JSONObject().put("must_pass_exit_code", 0).put("must_contain_output", "git"))
                })
                put(JSONObject().apply {
                    put("name", "python")
                    put("type", "tar.gz")
                    put("arch", ARCH)
                    put("url", "https://github.com/astral-sh/python-build-standalone/releases/download/20240415/cpython-3.12.3+20240415-aarch64-unknown-linux-musl-install_only.tar.gz")
                    put("checksum", CHECKSUM_SKIP)
                    put("install_method", "tar_extract")
                    put("test_command", "python3 --version")
                    put("validation_rules", JSONObject().put("must_pass_exit_code", 0).put("must_contain_output", "Python"))
                })
                put(JSONObject().apply {
                    put("name", "curl")
                    put("type", "binary")
                    put("arch", ARCH)
                    put("url", "https://github.com/moparisthebest/static-curl/releases/download/v8.7.1/curl-aarch64")
                    put("checksum", CHECKSUM_SKIP)
                    put("install_method", "binary_copy")
                    put("test_command", "curl --version")
                    put("validation_rules", JSONObject().put("must_pass_exit_code", 0).put("must_contain_output", "curl"))
                })
                put(JSONObject().apply {
                    put("name", "nodejs")
                    put("type", "deb")
                    put("arch", ARCH)
                    put("url", "https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs/nodejs_22.2.0_aarch64.deb")
                    put("checksum", CHECKSUM_SKIP)
                    put("install_method", "deb_extract")
                    put("test_command", "node --version")
                    put("validation_rules", JSONObject().put("must_pass_exit_code", 0).put("must_contain_output", "v22"))
                })
            }
            registryFile.writeText(defaultRegistry.toString(2))
        }
    }

    private fun loadRegistry(): List<JSONObject> {
        val array = JSONArray(registryFile.readText())
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    suspend fun installPackage(
        packageId: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val pkg = loadRegistry().find { it.getString("name") == packageId } ?: return@withContext false

        try {
            val url = pkg.getString("url")
            val type = pkg.getString("type")
            val installMethod = pkg.getString("install_method")
            val name = pkg.getString("name")

            onProgress(PROGRESS_START, "Downloading $name...")
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                onProgress(PROGRESS_START, "${FAIL_PREFIX}DOWNLOAD_404")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val downloadFile = File(runtimeManager.downloadsDir, "$name.$type")

            body.byteStream().use { input ->
                FileOutputStream(downloadFile).use { output ->
                    input.copyTo(output)
                }
            }

            val processElf = ProcessBuilder("readelf", "-h", downloadFile.absolutePath).start()
            val elfOut = processElf.inputStream.bufferedReader().readText()
            processElf.waitFor()
            if (type == "binary" && elfOut.contains("ld-linux")) {
                onProgress(PROGRESS_START, "${FAIL_PREFIX}GLIBC_INCOMPATIBLE")
                return@withContext false
            }

            val installDir = File(runtimeManager.packagesDir, name)
            if (installDir.exists()) installDir.deleteRecursively()
            installDir.mkdirs()

            when (installMethod) {
                "tar_extract" -> {
                    val p = ProcessBuilder("tar", "-xzf", downloadFile.absolutePath, "-C", installDir.absolutePath).start()
                    p.waitFor()
                }
                "binary_copy" -> {
                    val target = File(installDir, name)
                    downloadFile.copyTo(target)
                    target.setExecutable(true)
                    if (!target.canExecute()) {
                        Runtime.getRuntime().exec(arrayOf("chmod", "+x", target.absolutePath)).waitFor()
                    }
                }
                "deb_extract" -> {
                    val raf = RandomAccessFile(downloadFile, "r")
                    val magic = ByteArray(8)
                    raf.readFully(magic)
                    if (String(magic) == "!<arch>\n") {
                        var dataTarFile: File? = null
                        while (raf.filePointer < raf.length()) {
                            val header = ByteArray(60)
                            raf.readFully(header)
                            val entryName = String(header, 0, 16).trim()
                            val size = String(header, 48, 10).trim().toLong()
                            if (entryName.startsWith("data.tar")) {
                                dataTarFile = File(runtimeManager.downloadsDir, entryName.trim('/'))
                                val out = FileOutputStream(dataTarFile)
                                val buffer = ByteArray(BUFFER_SIZE)
                                var remaining = size
                                while (remaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                    val read = raf.read(buffer, 0, toRead)
                                    out.write(buffer, 0, read)
                                    remaining -= read
                                }
                                out.close()
                                break
                            } else {
                                raf.seek(raf.filePointer + size + (size % 2))
                            }
                        }
                        raf.close()
                        if (dataTarFile != null) {
                            val p = ProcessBuilder("tar", "-xf", dataTarFile.absolutePath, "-C", installDir.absolutePath).start()
                            p.waitFor()
                        }
                    }
                }
            }

            val actualBinary = if (installMethod == "binary_copy") File(installDir, name) else File(installDir, "usr/bin/$name")
            val backupBinary = File(installDir, "bin/$name")
            val binToUse = if (actualBinary.exists()) actualBinary else backupBinary

            binToUse.setExecutable(true)
            if (!binToUse.canExecute()) {
                Runtime.getRuntime().exec(arrayOf("chmod", "+x", binToUse.absolutePath)).waitFor()
            }

            val wrapper = File(runtimeManager.binDir, name)
            val libDir = File(installDir, "usr/lib")
            val ldLibraryPathEnv = if (libDir.exists()) "export LD_LIBRARY_PATH=\"${libDir.absolutePath}:\$LD_LIBRARY_PATH\"\n" else ""

            wrapper.writeText("#!/system/bin/sh\n$ldLibraryPathEnv\nexec \"${binToUse.absolutePath}\" \"\$@\"\n")
            wrapper.setExecutable(true)
            if (!wrapper.canExecute()) {
                Runtime.getRuntime().exec(arrayOf("chmod", "+x", wrapper.absolutePath)).waitFor()
            }

            val testCmd = pkg.getString("test_command")
            val rules = pkg.getJSONObject("validation_rules")

            val tProcess = ProcessBuilder(testCmd.split(" ")).start()
            val tOut = tProcess.inputStream.bufferedReader().readText()
            tProcess.waitFor()

            if (tProcess.exitValue() == rules.getInt("must_pass_exit_code") && tOut.contains(rules.getString("must_contain_output"))) {
                onProgress(PROGRESS_COMPLETE, "PASS")
                return@withContext true
            } else {
                onProgress(PROGRESS_COMPLETE, "${FAIL_PREFIX}Execution Test Failed")
                return@withContext false
            }

        } catch (e: Exception) {
            onProgress(PROGRESS_START, "${FAIL_PREFIX}Exception ${e.message}")
            return@withContext false
        }
    }
}
