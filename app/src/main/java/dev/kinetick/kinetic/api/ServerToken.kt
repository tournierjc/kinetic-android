package dev.kinetick.kinetic.api

/**
 * Bearer credential for `kcode --server`.
 *
 * Every request, including `GET /health`, must send `Authorization: Bearer`.
 * The server writes the value to `<data-dir>/run/session-server.token`
 * (typically `~/.kinetick/run/session-server.token`) and accepts 16–256
 * printable ASCII characters with no spaces.
 */
object ServerToken {
    private const val BEARER_PREFIX = "bearer"

    /**
     * Trim surrounding whitespace (the token file ends with a newline) and a
     * pasted `Bearer ` prefix so the header value is the token alone.
     */
    fun normalize(raw: String): String {
        var value = raw.trim()
        // "Bearer <token>" and a pasted header with nothing after the scheme.
        // A token that merely begins with those letters, with no separator, stays intact.
        if (value.length >= BEARER_PREFIX.length &&
            value.regionMatches(0, BEARER_PREFIX, 0, BEARER_PREFIX.length, ignoreCase = true)
        ) {
            val rest = value.substring(BEARER_PREFIX.length)
            if (rest.isEmpty() || rest[0].isWhitespace()) value = rest.trim()
        }
        return value
    }

    /** True when [raw] matches the server's token shape. An empty value is not acceptable. */
    fun isAcceptable(raw: String): Boolean {
        val value = normalize(raw)
        if (value.length !in 16..256) return false
        return value.all { it.code in 0x21..0x7E }
    }
}
