package dev.kinetick.kinetick.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kinetick.kinetick.api.KcodeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    /** Server address plus the bearer token `kcode --server` now requires. */
    data class ServerSettings(val baseUrl: String, val token: String)

    /** The whole registry plus the server new sessions are created on. */
    data class Config(
        val servers: List<ServerEntry> = emptyList(),
        val activeId: String = "",
    ) {
        val active: ServerEntry?
            get() = servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()

        fun server(id: String): ServerEntry? = servers.firstOrNull { it.id == id }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("server_base_url")
        private val KEY_TOKEN = stringPreferencesKey("server_token")
        private val KEY_SERVERS = stringPreferencesKey("servers")
        private val KEY_ACTIVE = stringPreferencesKey("active_server_id")
        private val KEY_THEME = stringPreferencesKey("theme_mode")

        /**
         * Empty until the user configures a server. A placeholder IP used to sit
         * here, which only produced a confusing timeout on first run.
         */
        const val NO_SERVER = ""
    }

    /**
     * Full multi-server configuration. The pre-multi-server single-server
     * keys are migrated into the registry on first read, so existing installs
     * keep working untouched.
     */
    val config: Flow<Config> = context.dataStore.data
        .map { prefs ->
            val stored = prefs[KEY_SERVERS]
            val activeId = prefs[KEY_ACTIVE] ?: ""
            if (!stored.isNullOrBlank()) {
                ServerRegistry.parse(stored, activeId).let { (servers, active) ->
                    Config(servers, active)
                }
            } else {
                val url = prefs[KEY_BASE_URL] ?: NO_SERVER
                val token = prefs[KEY_TOKEN] ?: ""
                if (url.isBlank()) Config()
                else {
                    val entry = ServerEntry(
                        id = ServerRegistry.idFor(url),
                        name = "",
                        baseUrl = url,
                        token = token,
                    )
                    Config(listOf(entry), entry.id)
                }
            }
        }
        .distinctUntilChanged()

    val servers: Flow<List<ServerEntry>> = config.map { it.servers }

    val active: Flow<ServerEntry?> = config.map { it.active }

    /** Kept for the single-server view of the world (tests, fallbacks). */
    val server: Flow<ServerSettings> = config.map { c ->
        c.active?.let { ServerSettings(it.baseUrl, it.token) } ?: ServerSettings(NO_SERVER, "")
    }

    val baseUrl: Flow<String> = server.map { it.baseUrl }

    val token: Flow<String> = server.map { it.token }

    /** One of "system", "dark", "light" — see ThemeMode. */
    val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME] ?: "system" }

    /** Adds a server (id derived from the URL, so editing keeps identity) and makes it active. */
    suspend fun addServer(url: String, token: String, name: String = ""): ServerEntry {
        val base = url.trim().trimEnd('/')
        val entry = ServerEntry(ServerRegistry.idFor(base), name.trim(), base, token.trim())
        mutate { servers, _ ->
            val replaced = servers.filterNot { it.id == entry.id } + entry
            replaced to entry.id
        }
        return entry
    }

    suspend fun updateServer(id: String, url: String, token: String, name: String) {
        mutate { servers, activeId ->
            val updated = servers.map { s ->
                if (s.id != id) return@map s
                val base = url.trim().trimEnd('/')
                val nextToken = token.trim().ifBlank { s.token }
                s.copy(name = name.trim(), baseUrl = base, token = nextToken)
            }
            updated to activeId
        }
    }

    suspend fun deleteServer(id: String) {
        mutate { servers, activeId ->
            val remaining = servers.filterNot { it.id == id }
            val nextActive = if (activeId == id) remaining.firstOrNull()?.id ?: "" else activeId
            remaining to nextActive
        }
    }

    suspend fun setActive(id: String) {
        mutate { servers, _ ->
            servers to (if (servers.any { it.id == id }) id else servers.firstOrNull()?.id ?: "")
        }
    }

    /** Single-server entry point — updates the active server (or creates one). */
    suspend fun setServer(url: String, token: String) {
        val config = config.first()
        val active = config.active
        if (active != null) updateServer(active.id, url, token, active.name)
        else addServer(url, token)
    }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { it[KEY_THEME] = id }
    }

    private suspend fun mutate(transform: (List<ServerEntry>, String) -> Pair<List<ServerEntry>, String>) {
        val current = config.first()
        val (servers, activeId) = transform(current.servers, current.activeId)
        context.dataStore.edit {
            it[KEY_SERVERS] = ServerRegistry.serialize(servers)
            it[KEY_ACTIVE] = activeId
            // Keep the legacy keys in sync so any old reader sees the active server.
            val active = servers.firstOrNull { s -> s.id == activeId }
            it[KEY_BASE_URL] = active?.baseUrl ?: NO_SERVER
            it[KEY_TOKEN] = active?.token ?: ""
        }
    }
}

/** True when this server has a URL with an http(s) scheme and a usable token. */
fun ServerEntry.configured(): Boolean = KcodeClient(baseUrl, token).configured

/** True when at least one registered server can be talked to. */
fun SettingsStore.Config.anyConfigured(): Boolean = servers.any { it.configured() }
