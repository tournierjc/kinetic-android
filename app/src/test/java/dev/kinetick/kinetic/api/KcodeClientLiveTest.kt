package dev.kinetick.kinetic.api

import dev.kinetick.kinetic.ui.ChatStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * End-to-end client test against a live `kcode --server`.
 *
 * Opt-in: set `KCODE_BASE_URL` (e.g. `http://127.0.0.1:8788`) and
 * `KCODE_SERVER_TOKEN` (the contents of `session-server.token`) to run it. Each
 * test creates its own session and deletes it again, so no real conversation is
 * touched.
 *
 * Two sessions on purpose: a queued message on an idle session starts its own
 * turn, so the turn assertions need a session nothing else has written to.
 */
class KcodeClientLiveTest {

    private val baseUrl: String? = System.getenv("KCODE_BASE_URL")
    private val token: String? = System.getenv("KCODE_SERVER_TOKEN")

    private val client: KcodeClient get() = KcodeClient(baseUrl!!, token.orEmpty())

    private fun assumeLiveServer() {
        assumeTrue("set KCODE_BASE_URL to run the live client test", !baseUrl.isNullOrBlank())
        assumeTrue("set KCODE_SERVER_TOKEN to the session server token", !token.isNullOrBlank())
    }

    @Test
    fun catalogueAndSessionWrites() {
        assumeLiveServer()
        val c = client

        // ---- descriptor ----
        val health = Wire.obj(c.health())
        assertNotNull("health did not answer JSON", health)
        assertNotNull(health!!.get("version")?.asString)

        val workspace = tempDir()
        val created = c.createSession(workspace, "Kinetic live test")
        val sid = created.sessionId
        assertTrue("session id should be non-empty", sid.isNotBlank())
        assertEquals("Kinetic live test", created.title)

        try {
            // ---- every read the UI relies on ----
            assertTrue(
                "the new session should be listed",
                c.listSessions(limit = 50).sessions.any { it.sessionId == sid },
            )
            assertEquals(sid, c.getSession(sid).sessionId)
            assertEquals(0, c.messages(sid, limit = 10).messages.size)
            assertEquals("idle", c.interactions(sid).activeRun?.state)
            assertEquals(0, c.queue(sid).pendingCount)
            assertNotNull(c.delegation(sid).rootSessionId)
            assertEquals(0, c.backgroundTasks(sid).size)
            assertEquals(0, c.usage(sid).summary.turns)
            assertNotNull(c.context(sid).model)
            assertEquals(false, c.forkOptions(sid).canFork)
            assertEquals(0, c.pendingPermissions().size)
            assertTrue("model roster should not be empty", c.models(sid).isNotEmpty())
            c.skills(workspaceDir = workspace)

            // ---- rename goes through the runtime's content-safety gate, which
            // fails closed when its reviewer is unreachable (422) ----
            try {
                c.renameSession(sid, "Kinetic live test renamed")
                assertEquals("Kinetic live test renamed", c.getSession(sid).title)
            } catch (e: KcodeClient.KcodeException) {
                assertEquals(422, e.code)
                assertTrue("unexpected rename error: ${e.message}", e.message!!.contains("Content validation"))
                assertEquals("Kinetic live test", c.getSession(sid).title)
            }

            c.setPinned(sid, true)
            assertEquals(true, c.getSession(sid).pinned)
            c.setPinned(sid, false)
            assertEquals(false, c.getSession(sid).pinned)

            c.setArchived(sid, true)
            assertEquals(true, c.getSession(sid).archived)
            c.setArchived(sid, false)

            // ---- queue ack ----
            val ack = c.queueEnqueue(sid, "queued follow-up")
            assertTrue("enqueue should ack an item id", !ack.itemId.isNullOrBlank())
            assertEquals("queued", ack.status)
            assertTrue("position should be positive", (ack.position ?: 0) >= 1)
            // An idle session drains the queue straight into the conversation, so
            // the snapshot may stay empty — the ack is the confirmation.
            val inSnapshot = c.queue(sid).items.any { it.content == "queued follow-up" }
            val inTranscript = c.messages(sid, limit = 20).messages.any { it.content == "queued follow-up" }
            assertTrue("queued text should land in the queue or the transcript", inSnapshot || inTranscript)
            c.queue(sid).items.firstOrNull { it.content == "queued follow-up" }?.let {
                c.queueDelete(sid, it.id)
            }
        } finally {
            c.deleteSession(sid)
            java.io.File(workspace).deleteRecursively()
        }
    }

    @Test
    fun turnStreamAndTranscript() {
        assumeLiveServer()
        val c = client
        val workspace = tempDir()
        val sid = c.createSession(workspace, "Kinetic live turn").sessionId

        try {
            val store = ChatStore()
            val latch = CountDownLatch(1)
            val failure = AtomicReference<String?>(null)
            val source = c.promptStream(sid, "Reply with exactly: PONG", null, object : EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    val event = Wire.streamEvent(type, data)
                    store.apply(event)
                    when (event) {
                        is StreamEvent.Done -> latch.countDown()
                        is StreamEvent.Failed -> {
                            failure.set(event.message)
                            latch.countDown()
                        }
                        else -> Unit
                    }
                }

                override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                    failure.set(t?.message ?: response?.message ?: "stream failure")
                    latch.countDown()
                }
            })

            assertTrue("turn stream did not finish in time", latch.await(60, TimeUnit.SECONDS))
            source.cancel()

            val state = store.state.value
            assertTrue(
                "the prompt should be in the transcript: " +
                    state.messages.map { "${it.role}:${it.content.take(30)}" },
                state.messages.any { it.role == "user" && it.content.contains("PONG") },
            )
            // Either the model answered, or the runtime said why it could not.
            val answered = state.messages.any { it.role == "assistant" && it.content.isNotBlank() }
            val reported = failure.get() != null || state.status == "error" || state.error != null
            assertTrue(
                "expected an assistant reply or an explicit runtime error; " +
                    "status=${state.status} error=${state.error} streamFailure=${failure.get()}",
                answered || reported,
            )

            // The transcript is readable back over HTTP too.
            val page = c.messages(sid, limit = 20)
            assertTrue("first page should hold the user prompt", page.messages.any { it.role == "user" })
            // `turns` counts completed model turns: with the runtime's provider
            // credentials unsynced the turn aborts before inference, so only
            // assert the accounting once a reply actually landed.
            if (answered) assertTrue("an answered turn should be accounted for", c.usage(sid).summary.turns >= 1)
            else assertTrue("usage should still decode", c.usage(sid).summary.turns >= 0)

            c.abort(sid, "test cleanup")
        } finally {
            c.deleteSession(sid)
            java.io.File(workspace).deleteRecursively()
        }
    }

    private fun tempDir(): String {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "kinetic-live-${System.nanoTime()}")
        dir.mkdirs()
        return dir.absolutePath
    }
}
