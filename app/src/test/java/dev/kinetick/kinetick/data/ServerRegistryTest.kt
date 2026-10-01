package dev.kinetick.kinetick.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The multi-server registry: ids derived from URLs, the DataStore blob codec,
 * and the session-id resolution order. All pure functions, no Android.
 */
class ServerRegistryTest {

    @Test
    fun idIsStableAcrossTrailingSlashAndPath() {
        val a = ServerRegistry.idFor("http://192.168.1.5:8788")
        val b = ServerRegistry.idFor(" http://192.168.1.5:8788/ ")
        val c = ServerRegistry.idFor("http://192.168.1.5:8788/api")
        assertEquals(a, b)
        assertEquals(a, c)
        assertEquals("srv-192.168.1.5:8788", a)
    }

    @Test
    fun schemeIsNotPartOfTheId() {
        val http = ServerRegistry.idFor("http://box:8788")
        val https = ServerRegistry.idFor("https://box:8788")
        assertEquals(http, https)
    }

    @Test
    fun distinctHostsGetDistinctIds() {
        assertNotEquals(
            ServerRegistry.idFor("http://box-a:8788"),
            ServerRegistry.idFor("http://box-b:8788"),
        )
    }

    @Test
    fun hostlessUrlGetsAUniqueIdNotTheEmptyString() {
        // "/no-host" has no scheme; splitting on '/' leaves an empty host —
        // the malformed case idFor must not collapse onto the empty string.
        val a = ServerRegistry.idFor("/no-host")
        val b = ServerRegistry.idFor("/no-host")
        assertTrue(a.startsWith("srv-"))
        assertNotEquals("Two host-less URLs must not collide on the empty host", a, b)
    }

    @Test
    fun labelFallsBackToHost() {
        val named = ServerEntry("srv-1", "home", "http://192.168.1.5:8788", "t")
        val anon = ServerEntry("srv-2", "", "http://192.168.1.9:8788/", "t")
        assertEquals("home", ServerRegistry.label(named))
        assertEquals("192.168.1.9:8788", ServerRegistry.label(anon))
        assertEquals("unknown server", ServerRegistry.label(null))
    }

    @Test
    fun roundTripsTheBlob() {
        val servers = listOf(
            ServerEntry("srv-a", "home", "http://a:8788", "tokA"),
            ServerEntry("srv-b", "work", "https://b.example:8788", "token|with|pipes"),
        )
        val blob = ServerRegistry.serialize(servers)
        val (parsed, active) = ServerRegistry.parse(blob, "srv-b")
        assertEquals(servers, parsed)
        assertEquals("srv-b", active)
    }

    @Test
    fun parseDropsMalformedLinesAndFallsBackToFirstActive() {
        val blob = "junk line\nsrv-a|home|http://a:8788|tok\n|nope|url|tok"
        val (parsed, active) = ServerRegistry.parse(blob, "missing")
        assertEquals(1, parsed.size)
        assertEquals("srv-a", parsed.single().id)
        assertEquals("srv-a", active)
    }

    @Test
    fun parseHandlesEmptyBlob() {
        val (parsed, active) = ServerRegistry.parse("", "")
        assertTrue(parsed.isEmpty())
        assertEquals("", active)
    }

    @Test
    fun resolveReturnsTheFirstServerThatHostsTheSession() {
        val a = ServerEntry("srv-a", "a", "http://a:8788", "t")
        val b = ServerEntry("srv-b", "b", "http://b:8788", "t")
        val hit = ServerRegistry.resolveSession("s1", listOf(a, b)) { server, _ -> server == b }
        assertEquals(b, hit)
        assertNull(ServerRegistry.resolveSession("s1", listOf(a, b)) { _, _ -> false })
    }

    @Test
    fun resolveChecksServersInOrder() {
        val a = ServerEntry("srv-a", "a", "http://a:8788", "t")
        val b = ServerEntry("srv-b", "b", "http://b:8788", "t")
        val probed = mutableListOf<String>()
        ServerRegistry.resolveSession("s1", listOf(a, b)) { server, _ ->
            probed += server.id
            server == a
        }
        // First hit short-circuits: b must never be probed.
        assertEquals(listOf("srv-a"), probed)
    }
}
