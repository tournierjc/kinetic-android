package dev.kinetick.kinetic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    /** Server address plus the bearer token `kcode --server` now requires. */
    data class ServerSettings(val baseUrl: String, val token: String)

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("server_base_url")
        private val KEY_TOKEN = stringPreferencesKey("server_token")
        private val KEY_THEME = stringPreferencesKey("theme_mode")

        /**
         * Empty until the user configures a server. A placeholder IP used to sit
         * here, which only produced a confusing timeout on first run.
         */
        const val NO_SERVER = ""
    }

    val server: Flow<ServerSettings> = context.dataStore.data
        .map { prefs ->
            ServerSettings(
                baseUrl = prefs[KEY_BASE_URL] ?: NO_SERVER,
                token = prefs[KEY_TOKEN] ?: "",
            )
        }
        .distinctUntilChanged()

    val baseUrl: Flow<String> = server.map { it.baseUrl }

    val token: Flow<String> = server.map { it.token }

    /** One of "system", "dark", "light" — see ThemeMode. */
    val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME] ?: "system" }

    suspend fun setServer(url: String, token: String) {
        context.dataStore.edit {
            it[KEY_BASE_URL] = url.trim().trimEnd('/')
            it[KEY_TOKEN] = token.trim()
        }
    }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { it[KEY_THEME] = id }
    }
}
