package com.omniclaw.core.di

import android.content.Context
import androidx.room.Room
import com.omniclaw.data.api.tools.ExecuteCommandTool
import com.omniclaw.data.api.tools.SudoCommandTool
import com.omniclaw.data.api.tools.ToolRegistry
import com.omniclaw.data.local.OmniClawDatabase
import com.omniclaw.data.local.prefs.CredentialsStore
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.repository.OmniClawRepositoryImpl
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.repository.OmniClawRepository

interface AppContainer {
    val repository: OmniClawRepository
    val prefsManager: PreferencesManager
    val aiProvider: AiProvider
    val toolRegistry: ToolRegistry
    val localCommandRunner: LocalCommandRunner
    val runtimeManager: com.omniclaw.data.local.runtime.OmniClawRuntimeManager
    val packageInstaller: com.omniclaw.data.local.runtime.PackageInstaller
    val toolCallRecorder: ToolCallRecorder
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: OmniClawDatabase by lazy {
        Room.databaseBuilder(context, OmniClawDatabase::class.java, "omniclaw_database")
            .build()
    }

    override val repository: OmniClawRepository by lazy {
        OmniClawRepositoryImpl(database.dao())
    }

    override val prefsManager: PreferencesManager by lazy {
        PreferencesManager(context, CredentialsStore(context))
    }

    override val aiProvider: AiProvider by lazy {
        com.omniclaw.data.api.providers.AiProviderSelector()
    }
    
    override val runtimeManager: com.omniclaw.data.local.runtime.OmniClawRuntimeManager by lazy {
        com.omniclaw.data.local.runtime.OmniClawRuntimeManager(context)
    }

    override val packageInstaller: com.omniclaw.data.local.runtime.PackageInstaller by lazy {
        com.omniclaw.data.local.runtime.PackageInstaller(
            runtimeManager,
            okhttp3.OkHttpClient.Builder().build()
        )
    }

    override val localCommandRunner: LocalCommandRunner by lazy {
        LocalCommandRunner(runtimeManager)
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
}
