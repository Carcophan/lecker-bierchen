package com.picscan.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.picscan.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "picscan_settings")

class ApiKeyPreferenceRepository(private val context: Context) {

    companion object {
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        
        const val DEFAULT_MODEL = "gemini-3.6-flash"
        val AVAILABLE_MODELS = listOf(
            "gemini-3.6-flash" to "Gemini 3.6 Flash (Neuestes High-Speed-Modell)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Next-Gen High-Speed)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Schnell & sparsam)",
            "gemini-1.5-pro" to "Gemini 1.5 Pro (Tiefgehende Sommelier-Analyse)"
        )
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val savedKey = preferences[KEY_GEMINI_API_KEY]
        if (!savedKey.isNullOrBlank()) {
            savedKey
        } else {
            BuildConfig.DEFAULT_GEMINI_API_KEY
        }
    }

    val selectedModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveSelectedModel(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_MODEL] = modelName
        }
    }
}
