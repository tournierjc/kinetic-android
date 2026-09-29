package dev.kinetick.kinetic.api

import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The session server refuses every route, including `/health` and SSE, unless
 * `Authorization: Bearer` matches the operator token.
 */
class KcodeClientAuthTest {

    private val token = "0123456789abcdef"

    @Test
    fun healthAndWritesSendTheBearerToken() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ok":true,"version":"0.6.8"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        try {
            val client = KcodeClient(server.url("/").toString().trimEnd('/'), "  $token\n")
            client.health()
            client.abort("sess1", "stop")

            val health = server.takeRequest()
            assertEquals("GET", health.method)
            assertEquals("/health", health.path)
            assertEquals("Bearer $token", health.getHeader("Authorization"))

            val abort = server.takeRequest()
            assertEquals("POST", abort.method)
            assertEquals("/sessions/sess1/abort", abort.path)
            assertEquals("Bearer $token", abort.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun eventStreamSendsTheBearerToken() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(": open\n\n"),
        )
        server.start()
        try {
            val client = KcodeClient(server.url("/").toString().trimEnd('/'), token)
            val opened = CountDownLatch(1)
            val source = client.eventStream(object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    opened.countDown()
                }
            })
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            val recorded = server.takeRequest()
            assertEquals("/events", recorded.path)
            assertEquals("Bearer $token", recorded.getHeader("Authorization"))
            source.cancel()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun unauthorizedIsAnActionableError() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"unauthorized"}"""),
        )
        server.start()
        try {
            val client = KcodeClient(server.url("/").toString().trimEnd('/'), token)
            val error = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
            assertEquals(401, error.code)
            assertEquals("Unauthorized — check the server token", error.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aMalformedTokenNeverReachesTheServer() {
        val server = MockWebServer()
        server.start()
        try {
            val client = KcodeClient(server.url("/").toString().trimEnd('/'), "too-short")
            assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
