package com.officepilot.ai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.officepilot.ai.util.C
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsRepository @Inject constructor(private val ds: DataStore<Preferences>) {

    val geminiKey: Flow<String> = ds.data.catch { emit(emptyPreferences()) }
        .map { it[stringPreferencesKey(C.PREF_GEMINI_KEY)] ?: "" }
    val groqKey: Flow<String> = ds.data.catch { emit(emptyPreferences()) }
        .map { it[stringPreferencesKey(C.PREF_GROQ_KEY)] ?: "" }
    val openrouterKey: Flow<String> = ds.data.catch { emit(emptyPreferences()) }
        .map { it[stringPreferencesKey(C.PREF_OPENROUTER_KEY)] ?: "" }
    val setupDone: Flow<Boolean> = ds.data.catch { emit(emptyPreferences()) }
        .map { it[booleanPreferencesKey(C.PREF_SETUP_DONE)] ?: false }
    val defaultFormat: Flow<String> = ds.data.catch { emit(emptyPreferences()) }
        .map { it[stringPreferencesKey(C.PREF_DEFAULT_FORMAT)] ?: "pdf" }

    suspend fun setGeminiKey(k: String) { ds.edit { it[stringPreferencesKey(C.PREF_GEMINI_KEY)] = k } }
    suspend fun setGroqKey(k: String) { ds.edit { it[stringPreferencesKey(C.PREF_GROQ_KEY)] = k } }
    suspend fun setOpenrouterKey(k: String) { ds.edit { it[stringPreferencesKey(C.PREF_OPENROUTER_KEY)] = k } }
    suspend fun setSetupDone(v: Boolean) { ds.edit { it[booleanPreferencesKey(C.PREF_SETUP_DONE)] = v } }
    suspend fun setDefaultFormat(f: String) { ds.edit { it[stringPreferencesKey(C.PREF_DEFAULT_FORMAT)] = f } }

    suspend fun hasAnyKey(): Boolean {
        val prefs = ds.data.firstOrNull() ?: return false
        return prefs[stringPreferencesKey(C.PREF_GEMINI_KEY)]?.isNotBlank() == true ||
               prefs[stringPreferencesKey(C.PREF_GROQ_KEY)]?.isNotBlank() == true ||
               prefs[stringPreferencesKey(C.PREF_OPENROUTER_KEY)]?.isNotBlank() == true
    }
}
