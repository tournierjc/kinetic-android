package dev.kinetick.kinetic.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.sse.EventSourceListener

class ServerTokenTest {

    @Test
    fun normalizeStripsWhitespaceAndABearerPrefix() {
        val token = "a".repeat(32)
        assertEquals(token, ServerToken.normalize("  $token\n"))
        assertEquals(token, ServerToken.normalize("Bearer $token\n"))
        assertEquals(token, ServerToken.normalize("bearer $token"))
        assertEquals("bearer" + token, ServerToken.normalize("bearer$token"))
        assertEquals("", ServerToken.normalize("   "))
        assertEquals("", ServerToken.normalize("Bearer "))
    }

    @Test
    fun acceptableTokensMatchTheServerRule() {
        assertTrue(ServerToken.isAcceptable("a".repeat(16)))
        assertTrue(ServerToken.isAcceptable("a".repeat(256)))
        assertTrue(!ServerToken.isAcceptable("a".repeat(15)))
        assertTrue(!ServerToken.isAcceptable("a".repeat(257)))
        assertTrue(!ServerToken.isAcceptable("has a space inside!!"))
        assertTrue(!ServerToken.isAcceptable(""))
    }
}

/**
 * The session server refuses every route, including health and SSE, unless
 * `Authorization: Bearer` matches its token.
 */
class KcodeClientAuthTest {

    @Test
    fun requestsSendTheNormalizedBearerToken() {
        val server = MockWebServer()
        val token = "a".repeat(32)
        server.enqueue(MockResponse().setBody("""{"ok":true,"version":"0.6.8"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        try {
            val client = KcodeClient(server.url("/").toString(), "Bearer $token\n")
            client.health()
            client.abort("s1")

            val health = server.takeRequest()
            assertEquals("/health", health.path)
            assertEquals("GET", health.method)
            assertEquals("Bearer $token", health.getHeader("Authorization"))

            val abort = server.takeRequest()
            assertEquals("/sessions/s1/abort", abort.path)
            assertEquals("POST", abort.method)
            assertEquals("Bearer $token", abort.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aBlankTokenOmitsTheHeader() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        try {
            KcodeClient(server.url("/").toString(), "  ").health()
            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun unauthorizedExplainsHowToSetTheToken() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"unauthorized"}""")
                .setHeader("www-authenticate", "Bearer")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"unauthorized"}""")
        )
        server.start()
        try {
            val base = server.url("/").toString()
            val missing = assertThrows(KcodeClient.KcodeException::class.java) {
                KcodeClient(base).health()
            }
            assertEquals(401, missing.code)
            assertTrue(missing.message!!.contains("requires a bearer token"))

            val rejected = assertThrows(KcodeClient.KcodeException::class.java) {
                KcodeClient(base, "b".repeat(32)).health()
            }
            assertEquals(401, rejected.code)
            assertTrue(rejected.message!!.contains("refused this token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun theEventStreamCarriesTheSameHeader() {
        val server = MockWebServer()
        val token = "c".repeat(32)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: ping\ndata: {}\n\n")
        )
        server.start()
        try {
            val opened = CountDownLatch(1)
            val source = KcodeClient(server.url("/").toString(), token).eventStream(
                object : EventSourceListener() {
                    override fun onOpen(eventSource: okhttp3.sse.EventSource, response: okhttp3.Response) {
                        opened.countDown()
                    }

                    override fun onEvent(
                        eventSource: okhttp3.sse.EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        opened.countDown()
                    }

                    override fun onFailure(
                        eventSource: okhttp3.sse.EventSource,
                        t: Throwable?,
                        response: okhttp3.Response?,
                    ) {
                        opened.countDown()
                    }
                }
            )
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/events", recorded?.path)
            assertEquals("Bearer $token", recorded?.getHeader("Authorization"))
            source.cancel()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aTokenWithANewlineIsRejectedBeforeTheRequest() {
        val client = KcodeClient(
            "http://127.0.0.1:9",
            "0123456789abcdef\n0123456789abcdef",
        )
        val error = assertThrows(KcodeClient.KcodeException::class.java) { client.health() }
        assertEquals(0, error.code)
        assertTrue(error.message!!.contains("Authorization"))
    }
}
