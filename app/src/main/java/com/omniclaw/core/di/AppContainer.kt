package com.omniclaw.core.di

import android.content.Context
import androidx.room.Room
import com.omniclaw.data.api.tools.ExecuteCommandTool
import com.omniclaw.data.api.tools.SudoCommandTool
import com.omniclaw.data.api.tools.ToolRegistry
import com.omniclaw.data.local.OmniClawDatabase
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.repository.OmniClawRepositoryImpl
import com.omniclaw.data.repository.OpenCodeRepositoryImpl
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.repository.OmniClawRepository
import com.omniclaw.domain.repository.OpenCodeRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface AppContainer {
    val repository: OmniClawRepository
    val prefsManager: PreferencesManager
    val aiProvider: AiProvider
    val toolRegistry: ToolRegistry
    val localCommandRunner: LocalCommandRunner
    val runtimeManager: com.omniclaw.data.local.runtime.OmniClawRuntimeManager
    val termuxRuntime: com.omniclaw.data.local.runtime.TermuxRuntime
    val toolCallRecorder: ToolCallRecorder
    val openCodeRepository: OpenCodeRepository
    val okHttpClient: OkHttpClient
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: OmniClawDatabase by lazy {
        Room.databaseBuilder(context, OmniClawDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    override val repository: OmniClawRepository by lazy {
        OmniClawRepositoryImpl(database.dao())
    }

    override val prefsManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(POOL_SIZE, POOL_KEEPALIVE_SECONDS, TimeUnit.SECONDS))
            .build()
    }

    override val aiProvider: AiProvider by lazy {
        com.omniclaw.data.api.providers.AiProviderSelector(okHttpClient, context)
    }

    override val runtimeManager: com.omniclaw.data.local.runtime.OmniClawRuntimeManager by lazy {
        com.omniclaw.data.local.runtime.OmniClawRuntimeManager(context)
    }

    override val localCommandRunner: LocalCommandRunner by lazy {
        LocalCommandRunner(runtimeManager)
    }

    override val termuxRuntime: com.omniclaw.data.local.runtime.TermuxRuntime by lazy {
        com.omniclaw.data.local.runtime.TermuxRuntime(context)
    }

    override val toolCallRecorder: ToolCallRecorder by lazy {
        ToolCallRecorder()
    }

    override val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(
            listOf(
                ExecuteCommandTool(localCommandRunner),
                SudoCommandTool(localCommandRunner)
            )
        )
    }

    override val openCodeRepository: OpenCodeRepository by lazy {
        OpenCodeRepositoryImpl()
    }

    companion object {
        private const val DATABASE_NAME = "omniclaw_database"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        private const val POOL_SIZE = 5
        private const val POOL_KEEPALIVE_SECONDS = 30L
    }
}
