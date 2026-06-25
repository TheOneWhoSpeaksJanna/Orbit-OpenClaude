package com.omniclaw.data.local.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.omniclaw.BuildConfig
import com.omniclaw.data.local.prefs.DownloadProgress
import com.omniclaw.data.local.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewer: Boolean,
    val assetId: Long = 0
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Downloaded(val filePath: String) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

class UpdateManager(
    private val context: Context,
    private val prefsManager: PreferencesManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private suspend fun githubToken(): String? {
        return prefsManager.githubToken.first()
    }

    private suspend fun buildAuthenticatedRequest(url: String): Request {
        val token = githubToken()
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    suspend fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        try {
            val result = withContext(Dispatchers.IO) {
                val response = httpClient.newCall(
                    buildAuthenticatedRequest("$API_BASE_URL/releases/latest")
                ).execute()

                if (response.code == 404) {
                    val listResponse = httpClient.newCall(
                        buildAuthenticatedRequest("$API_BASE_URL/releases?per_page=1")
                    ).execute()

                    if (!listResponse.isSuccessful || listResponse.body == null) {
                        return@withContext null
                    }
                    val listBody = listResponse.body!!.string()
                    val releasesArray = JSONArray(listBody)
                    if (releasesArray.length() == 0) {
                        return@withContext null
                    }
                    val json = releasesArray.getJSONObject(0)
                    parseReleaseJson(json)
                } else if (!response.isSuccessful) {
                    throw Exception("GitHub API returned ${response.code}")
                } else {
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    val json = JSONObject(body)
                    parseReleaseJson(json)
                }
            }

            if (result == null) {
                _updateState.value = UpdateState.UpToDate
            } else if (result.isNewer && result.downloadUrl.isNotBlank()) {
                _updateState.value = UpdateState.Available(result)
            } else {
                _updateState.value = UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed("${CHECK_FAILED_PREFIX}${e.message}")
        }
    }

    suspend fun downloadUpdate(info: UpdateInfo) {
        _updateState.value = UpdateState.Downloading(0f)
        try {
            val apkFile = withContext(Dispatchers.IO) {
                val downloadsDir = File(context.cacheDir, "updates")
                downloadsDir.mkdirs()
                val fileName = "Orbit-AI-${info.latestVersion}.apk"
                val finalFile = File(downloadsDir, fileName)
                val partialFile = File(downloadsDir, "$fileName.partial")

                val savedProgress = prefsManager.getDownloadProgress().first()
                var resumeBytes = 0L
                if (savedProgress != null &&
                    savedProgress.url == info.downloadUrl &&
                    savedProgress.version == info.latestVersion &&
                    partialFile.exists()
                ) {
                    resumeBytes = partialFile.length()
                }

                val requestBuilder = Request.Builder().url(info.downloadUrl)
                if (resumeBytes > 0) {
                    requestBuilder.header("Range", "bytes=$resumeBytes-")
                }
                requestBuilder.header("Accept", "application/octet-stream")
                val token = githubToken()
                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                val request = requestBuilder.build()

                val response = httpClient.newCall(request).execute()
                val isResuming = resumeBytes > 0 && response.code == 206

                if (!response.isSuccessful && !isResuming) {
                    response.close()
                    if (response.code == 416) {
                        partialFile.delete()
                        prefsManager.clearDownloadProgress()
                        val retry = httpClient.newCall(
                            Request.Builder().url(info.downloadUrl)
                                .header("Accept", "application/octet-stream")
                                .header("Authorization", "Bearer $token")
                                .build()
                        ).execute()
                        if (!retry.isSuccessful) {
                            throw Exception("${DOWNLOAD_FAILED_PREFIX}${retry.code}")
                        }
                        return@withContext downloadToFile(retry, finalFile, partialFile, info)
                    }
                    throw Exception("${DOWNLOAD_FAILED_PREFIX}${response.code}")
                }

                downloadToFile(response, finalFile, partialFile, info)
            }

            _updateState.value = UpdateState.Downloaded(apkFile.absolutePath)
        } catch (e: Exception) {
            persistProgressForResume(info)
            _updateState.value = UpdateState.Failed("${DOWNLOAD_FAILED_PREFIX}${e.message}")
        }
    }

    private suspend fun downloadToFile(
        response: okhttp3.Response,
        finalFile: File,
        partialFile: File,
        info: UpdateInfo
    ): File {
        val body = response.body ?: throw Exception("No response body")

        val existingBytes = if (partialFile.exists() && response.code == 206) partialFile.length() else 0L
        val resumeTotal = parseContentRange(response.header("Content-Range"))
        val totalBytes = if (response.code == 206 && resumeTotal > 0) resumeTotal else body.contentLength()
        var downloadedBytes = existingBytes

        val output = if (response.code == 206 && existingBytes > 0) {
            FileOutputStream(partialFile, true)
        } else {
            partialFile.delete()
            FileOutputStream(partialFile)
        }

        body.byteStream().use { input ->
            output.use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        _updateState.value = UpdateState.Downloading(
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        )
                    }
                }
            }
        }

        partialFile.renameTo(finalFile)
        prefsManager.clearDownloadProgress()
        return finalFile
    }

    private fun parseContentRange(contentRange: String?): Long {
        if (contentRange == null) return 0L
        return try {
            val total = contentRange.substringAfter("/").toLongOrNull() ?: 0L
            total
        } catch (_: Exception) { 0L }
    }

    private suspend fun persistProgressForResume(info: UpdateInfo) {
        try {
            val partialDir = File(context.cacheDir, "updates")
            val partialFile = File(partialDir, "Orbit-AI-${info.latestVersion}.apk.partial")
            if (partialFile.exists()) {
                prefsManager.setDownloadProgress(
                    url = info.downloadUrl,
                    filePath = partialFile.absolutePath,
                    bytes = partialFile.length(),
                    version = info.latestVersion
                )
            }
        } catch (_: Exception) { /* best effort */ }
    }

    fun installApk(filePath: String) {
        try {
            val file = File(filePath)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed("${INSTALL_FAILED_PREFIX}${e.message}")
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun parseReleaseJson(json: JSONObject): UpdateInfo {
        val tagName = json.optString("tag_name", "").removePrefix("v")
        val rawNotes = json.optString("body", "")
        val releaseNotes = if (rawNotes == "null" || rawNotes.isBlank()) "" else rawNotes
        val assets = json.optJSONArray("assets")
        var downloadUrl = ""
        var assetId = 0L

        val currentFlavor = BuildConfig.FLAVOR

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk") && name.contains(currentFlavor)) {
                    assetId = asset.optLong("id", 0)
                    downloadUrl = buildApiDownloadUrl(assetId)
                    break
                }
            }
            if (downloadUrl.isBlank()) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        assetId = asset.optLong("id", 0)
                        downloadUrl = buildApiDownloadUrl(assetId)
                        break
                    }
                }
            }
        }

        val currentVersion = BuildConfig.VERSION_NAME.split("-").first()
        val isNewer = compareVersions(tagName, currentVersion) > 0

        return UpdateInfo(
            latestVersion = tagName,
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
            isNewer = isNewer,
            assetId = assetId
        )
    }

    companion object {
        private const val REPO_OWNER = "TheOneWhoSpeaksJanna"
        private const val REPO_NAME = "Orbit-AI"
        private val API_BASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME"

        private fun buildApiDownloadUrl(assetId: Long): String {
            return "$API_BASE_URL/releases/assets/$assetId"
        }

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val BUFFER_SIZE = 8192

        private const val CHECK_FAILED_PREFIX = "Check failed: "
        private const val DOWNLOAD_FAILED_PREFIX = "Download failed: "
        private const val INSTALL_FAILED_PREFIX = "Install failed: "
    }
}
