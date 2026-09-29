package dev.kinetick.kinetic.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bad base URL used to reach OkHttp and surface as
 * "Expected url scheme 'http' or 'https'..." in the UI. The client now refuses
 * before the wire and says what to fix.
 */
class KcodeClientUrlTest {

    @Test
    fun blankBaseUrlIsRejectedWithAnActionableMessage() {
        val client = KcodeClient("")
        assertFalse(client.configured)
        val e = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
        assertEquals(0, e.code)
        assertEquals("No kcode server configured", e.message)
    }

    @Test
    fun missingSchemeSaysWhatToFix() {
        val client = KcodeClient("192.168.1.135:8788")
        assertFalse(client.configured)
        val e = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
        assertEquals(0, e.code)
        assertEquals("Server URL must start with http:// or https://", e.message)
    }

    @Test
    fun theGuardCoversEveryVerb() {
        val client = KcodeClient("")
        assertThrows(KcodeClient.KcodeException::class.java) { client.listSessions() }
        assertThrows(KcodeClient.KcodeException::class.java) { client.abort("s1") }
        assertThrows(KcodeClient.KcodeException::class.java) { client.renameSession("s1", "t") }
        assertThrows(KcodeClient.KcodeException::class.java) { client.deleteSession("s1") }
    }

    @Test
    fun aUrlWithoutATokenIsRejectedBeforeTheNetwork() {
        val client = KcodeClient("http://127.0.0.1:8788")
        val e = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
        assertEquals(0, e.code)
        assertEquals("Server token is required", e.message)
    }

    @Test
    fun aShortTokenIsRejectedBeforeTheNetwork() {
        val client = KcodeClient("http://127.0.0.1:8788", "short")
        val e = assertThrows(KcodeClient.KcodeException::class.java) { client.listSessions() }
        assertEquals(0, e.code)
        assertEquals(
            "Server token must be 16 to 256 printable ASCII characters without spaces",
            e.message,
        )
    }

    @Test
    fun pastedTokenFileContentsAreNormalized() {
        val fileLine = "test-session-token-0123456789\n"
        assertEquals("test-session-token-0123456789", KcodeClient.normalizeServerToken(fileLine))
        assertEquals(
            "test-session-token-0123456789",
            KcodeClient.normalizeServerToken("Bearer test-session-token-0123456789"),
        )
        assertEquals(
            "test-session-token-0123456789",
            KcodeClient.normalizeServerToken("\"test-session-token-0123456789\""),
        )
        assertEquals(
            "test-session-token-0123456789",
            KcodeClient("http://127.0.0.1:8788", "  Bearer test-session-token-0123456789\n").token,
        )
    }

    @Test
    fun usableUrlsAreAccepted() {
        assertTrue(KcodeClient("http://127.0.0.1:8788").configured)
        assertTrue(KcodeClient(" https://box.example:8788/ ").configured)
        assertFalse(KcodeClient("ftp://box:8788").configured)
        assertFalse(KcodeClient("   ").configured)
    }
}
