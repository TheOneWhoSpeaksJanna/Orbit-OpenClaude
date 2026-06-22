package com.omniclaw.data.api

import com.omniclaw.domain.api.AgentDownloader
import com.omniclaw.domain.models.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AgentDownloaderImpl(
    private val downloadDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : AgentDownloader {

    override fun download(
        url: String,
        destinationFileName: String
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Requesting)

        try {
            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                emit(DownloadState.Error("Server returned ${response.code}"))
                response.close()
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                response.close()
                return@flow
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputFile = File(downloadDir, destinationFileName)

            withContext(Dispatchers.IO) {
                downloadDir.mkdirs()
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var lastEmittedProgress = -1f
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val progress = (bytesRead.toFloat() / contentLength)
                            val progressPct = (progress * 100).toInt()
                            if (progressPct != lastEmittedProgress) {
                                lastEmittedProgress = progressPct
                                emit(DownloadState.Transferring(progress))
                            }
                        }
                    }
                }
            }

            inputStream.close()
            response.close()

            outputFile.setExecutable(true, false)
            emit(DownloadState.Complete(outputFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }
}
