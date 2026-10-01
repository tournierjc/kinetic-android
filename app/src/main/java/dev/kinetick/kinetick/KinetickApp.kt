package dev.kinetick.kinetick

import android.app.Application
import android.content.Context
import dev.kinetick.kinetick.api.KcodeClient
import dev.kinetick.kinetick.data.ServerEntry
import dev.kinetick.kinetick.data.SettingsStore
import dev.kinetick.kinetick.data.configured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class KinetickApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }

    companion object {
        /**
         * One client per server, cached by (url, token) — each client owns an
         * OkHttpClient (connection pool + dispatcher threads), so a long-lived
         * client per server is exactly the right shape for talking to several
         * servers at the same time.
         */
        private val clients = java.util.concurrent.ConcurrentHashMap<String, KcodeClient>()

        fun clientFor(context: Context, server: ServerEntry): KcodeClient =
            clientFor(server)

        fun clientFor(server: ServerEntry): KcodeClient =
            clients.getOrPut("${server.baseUrl}\n${server.token}") {
                KcodeClient(server.baseUrl, server.token)
            }

        /** Drop the cached client of a server that was edited or deleted. */
        fun forget(server: ServerEntry) {
            clients.remove("${server.baseUrl}\n${server.token}")
        }

        /**
         * Resolves a session id (notification, deep link, child link) to the
         * server that hosts it by probing every configured server in parallel.
         * Ids are unique per server only; the first hit wins.
         */
        suspend fun resolveServer(
            context: Context,
            sessionId: String,
            servers: List<ServerEntry>? = null,
        ): ServerEntry? = withContext(Dispatchers.IO) {
            val app = context.applicationContext as KinetickApp
            val candidates = (servers ?: app.settings.servers.first()).filter { it.configured() }
            val gate = Semaphore(4)
            coroutineScope {
                candidates.map { server ->
                    async {
                        gate.withPermit {
                            runCatching { clientFor(server).getSession(sessionId) }.getOrNull()
                        }
                    }
                }.awaitAll().zip(candidates)
                    .firstOrNull { (session, _) -> session != null }
                    ?.second
            }
        }
    }
}
