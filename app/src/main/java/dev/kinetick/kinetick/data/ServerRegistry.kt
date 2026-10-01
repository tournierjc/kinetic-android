package dev.kinetick.kinetick.data

import java.util.UUID

/**
 * One registered kcode server: a display name, the base URL and the bearer
 * token. The app keeps a registry of these and can talk to all of them at the
 * same time — sessions from every server are listed together.
 */
data class ServerEntry(
    val id: String,
    val name: String,
    val baseUrl: String,
    val token: String,
)

/** A session paired with the server it lives on (server == null: not resolvable). */
data class TaggedSession(
    val server: ServerEntry?,
    val sessionId: String,
)

object ServerRegistry {

    /**
     * Stable server id derived from the base URL (path and trailing slash
     * ignored — KcodeClient trims them anyway). Re-saving a server therefore
     * keeps its identity. A URL with no host (malformed) gets a random id
     * instead of the empty string, so two bad rows never collide.
     */
    fun idFor(baseUrl: String): String {
        val host = baseUrl.trim().trimEnd('/')
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
        return if (host.isBlank()) "srv-" + UUID.randomUUID().toString()
        else "srv-" + host.lowercase()
    }

    /** Short label for a server: the user name, the host, or a fallback. */
    fun label(server: ServerEntry?): String {
        if (server == null) return "unknown server"
        if (server.name.isNotBlank()) return server.name
        val host = server.baseUrl.trim().trimEnd('/')
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
        return host.ifBlank { server.baseUrl }
    }

    /** Label + host for rows that must disambiguate across servers. */
    fun detail(server: ServerEntry?): String {
        if (server == null) return "server removed"
        val host = server.baseUrl.trim().trimEnd('/')
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
        return host.ifBlank { server.baseUrl }
    }

    /**
     * Resolves a session id (from a notification, a deep link, a child-session
     * link) to the server that hosts it, checking the given servers in order.
     * Ids are unique per server only, so the first hit is the best answer the
     * app has.
     */
    fun resolveSession(
        sessionId: String,
        servers: List<ServerEntry>,
        lookup: (ServerEntry, String) -> Boolean,
    ): ServerEntry? = servers.firstOrNull { lookup(it, sessionId) }

    /**
     * Parses the DataStore blob ("id|name|url|token" per line) into a registry,
     * carrying over the active id when it is present. Malformed lines are
     * dropped.
     */
    fun parse(blob: String, activeId: String): Pair<List<ServerEntry>, String> {
        val servers = blob.lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size != 4) return@mapNotNull null
            val (id, name, url, token) = parts
            if (id.isBlank() || url.isBlank()) return@mapNotNull null
            ServerEntry(id, name, url, token)
        }.toList()
        val active = activeId.takeIf { id -> servers.any { it.id == id } }
            ?: servers.firstOrNull()?.id ?: ""
        return servers to active
    }

    /** Serialises a registry back to the DataStore blob. */
    fun serialize(servers: List<ServerEntry>): String =
        servers.joinToString("\n") { "${it.id}|${it.name}|${it.baseUrl}|${it.token}" }
}
