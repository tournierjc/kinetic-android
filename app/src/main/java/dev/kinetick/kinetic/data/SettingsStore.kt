package dev.kinetick.kinetic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kinetick.kinetic.api.ServerToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

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

    val baseUrl: Flow<String> = context.dataStore.data
        .map { it[KEY_BASE_URL] ?: NO_SERVER }

    /**
     * Bearer token for the session server. Empty until the user pastes one.
     * Stored next to the base URL in app-private preferences.
     */
    val token: Flow<String> = context.dataStore.data
        .map { it[KEY_TOKEN] ?: "" }

    /** One of "system", "dark", "light" — see ThemeMode. */
    val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME] ?: "system" }

    /** Persist the URL and token together after a successful health check. */
    suspend fun setServer(url: String, token: String) {
        val normalizedUrl = url.trim().trimEnd('/')
        val normalizedToken = ServerToken.normalize(token)
        context.dataStore.edit {
            it[KEY_BASE_URL] = normalizedUrl
            it[KEY_TOKEN] = normalizedToken
        }
    }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { it[KEY_THEME] = id }
    }
}
