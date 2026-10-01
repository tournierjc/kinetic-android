package dev.kinetick.kinetick.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionVisibilityTest {

    @Test
    fun rootsStayInTheMainList() {
        assertFalse(session(kind = "conversation").isSubagentSession())
        assertFalse(session(kind = null).isSubagentSession())
        assertFalse(session(kind = "conversation", parent = "  ").isSubagentSession())
    }

    @Test
    fun parentedAndTaskSessionsAreSubagents() {
        assertTrue(session(kind = "conversation", parent = "parent").isSubagentSession())
        assertTrue(session(kind = "task").isSubagentSession())
        assertTrue(session(kind = "Task", parent = "parent").isSubagentSession())
        assertTrue(session(kind = "peek", parent = "parent").isSubagentSession())
    }

    private fun session(kind: String?, parent: String? = null) = SessionInfo(
        sessionId = "s",
        sessionKind = kind,
        parentSessionId = parent,
    )
}
