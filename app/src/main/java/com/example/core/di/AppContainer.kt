package com.example.core.di

import android.content.Context
import androidx.room.Room
import com.example.data.api.GeminiApiProvider
import com.example.data.local.OrbitDatabase
import com.example.data.local.prefs.PreferencesManager
import com.example.data.local.termux.TermuxExecutor
import com.example.data.repository.OrbitRepositoryImpl
import com.example.domain.api.AiProvider
import com.example.domain.repository.OrbitRepository

interface AppContainer {
    val repository: OrbitRepository
    val prefsManager: PreferencesManager
    val aiProvider: AiProvider
    val termuxExecutor: TermuxExecutor
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: OrbitDatabase by lazy {
        Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit_database")
            .build()
    }

    override val repository: OrbitRepository by lazy {
        OrbitRepositoryImpl(database.dao())
    }

    override val prefsManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override val aiProvider: AiProvider by lazy {
        GeminiApiProvider()
    }

    override val termuxExecutor: TermuxExecutor by lazy {
        TermuxExecutor()
    }
}
