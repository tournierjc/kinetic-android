package dev.kinetick.kinetic.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The session server refuses every route, including health and the event
 * stream, unless `Authorization: Bearer` matches the token file.
 */
class KcodeClientAuthTest {

    private val server = MockWebServer()
    private val token = "test-session-token-0123456789"

    @Before
    fun start() {
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun getAndPostSendTheBearerToken() {
        server.enqueue(MockResponse().setBody("""{"ok":true,"version":"0.6.8"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val client = KcodeClient(server.url("/").toString().trimEnd('/'), "$token\n")

        client.health()
        client.abort("s1", "stop")

        val health = server.takeRequest()
        assertEquals("/health", health.path)
        assertEquals("Bearer $token", health.getHeader("Authorization"))

        val abort = server.takeRequest()
        assertEquals("/sessions/s1/abort", abort.path)
        assertEquals("Bearer $token", abort.getHeader("Authorization"))
        assertEquals("POST", abort.method)
    }

    @Test
    fun theEventStreamCarriesTheSameHeader() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", "text/event-stream")
                .setBody("event: ping\ndata: {}\n\n"),
        )
        val client = KcodeClient(base(), "Bearer $token")
        val source = client.eventStream(object : okhttp3.sse.EventSourceListener() {})
        try {
            val request = server.takeRequest()
            assertEquals("/events", request.path)
            assertEquals("Bearer $token", request.getHeader("Authorization"))
            assertEquals("text/event-stream", request.getHeader("Accept"))
        } finally {
            source.cancel()
        }
    }

    @Test
    fun aRejectedTokenSaysToCheckSettings() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("www-authenticate", "Bearer")
                .setHeader("content-type", "application/json")
                .setBody("""{"error":"unauthorized"}"""),
        )
        val client = KcodeClient(base(), token)
        val error = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
        assertEquals(401, error.code)
        assertTrue(error.message!!.contains("server token"))
    }

    private fun base(): String = server.url("/").toString().trimEnd('/')
}
