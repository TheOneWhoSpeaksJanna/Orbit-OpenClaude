package com.example.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("orbit_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GEMINI_API_KEY]
    }

    val themeMode: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE]
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[GEMINI_API_KEY] = key
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }
}
