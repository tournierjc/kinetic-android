package dev.kinetick.kinetic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("server_base_url")
        const val DEFAULT_BASE_URL = "http://192.168.1.1:8788"
    }

    val baseUrl: Flow<String> = context.dataStore.data
        .map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trimEnd('/') }
    }
}
