package dev.kinetick.kinetic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kinetick.kinetic.api.KcodeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** URL plus the bearer token `kcode --server` now requires on every request. */
data class ServerConfig(val baseUrl: String, val token: String) {
    val hasUrl: Boolean get() = baseUrl.isNotBlank()
    val hasToken: Boolean get() = token.isNotBlank()
    val ready: Boolean get() = hasUrl && hasToken

    companion object {
        val NONE = ServerConfig(SettingsStore.NO_SERVER, "")
    }
}

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

    /** Bearer token. Empty until the user pastes `session-server.token`. */
    val token: Flow<String> = context.dataStore.data
        .map { it[KEY_TOKEN] ?: "" }

    /**
     * Server address and token together. Distinct so a theme change does not
     * look like a new credential and reopen the event stream.
     */
    val server: Flow<ServerConfig> = context.dataStore.data
        .map {
            ServerConfig(
                baseUrl = it[KEY_BASE_URL] ?: NO_SERVER,
                token = it[KEY_TOKEN] ?: "",
            )
        }
        .distinctUntilChanged()

    /** One of "system", "dark", "light" — see ThemeMode. */
    val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME] ?: "system" }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trim().trimEnd('/') }
    }

    suspend fun setServer(url: String, token: String) {
        val normalizedUrl = url.trim().trimEnd('/')
        val normalizedToken = KcodeClient.normalizeServerToken(token)
        context.dataStore.edit {
            it[KEY_BASE_URL] = normalizedUrl
            it[KEY_TOKEN] = normalizedToken
        }
    }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { it[KEY_THEME] = id }
    }
}
