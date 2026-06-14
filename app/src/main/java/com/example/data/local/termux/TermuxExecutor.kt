package com.example.data.local.termux

import com.example.domain.model.TermuxLog
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.UUID
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TermuxCommandRequest(val command: String)

@JsonClass(generateAdapter = true)
data class TermuxCommandResponse(val output: String, val exitCode: Int)

interface TermuxApiService {
    @POST("/execute")
    suspend fun executeCommand(@Body request: TermuxCommandRequest): TermuxCommandResponse
}

class TermuxExecutor {
    private val moshi = Moshi.Builder().build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
        
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:8080/") // Termux local server address
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()

    private val service = retrofit.create(TermuxApiService::class.java)

    suspend fun executeCommand(command: String): TermuxLog {
        return withContext(Dispatchers.IO) {
            try {
                val response = service.executeCommand(TermuxCommandRequest(command))
                TermuxLog(
                    id = UUID.randomUUID().toString(),
                    command = command,
                    output = response.output,
                    exitCode = response.exitCode,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                TermuxLog(
                    id = UUID.randomUUID().toString(),
                    command = command,
                    output = "Connection to Termux Agent failed. Is the Python service running on 127.0.0.1:8080?\nError: ${e.message}",
                    exitCode = -1,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
}
