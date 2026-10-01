package dev.kinetick.kinetick.ui

import dev.kinetick.kinetick.api.BackgroundTask
import dev.kinetick.kinetick.api.DelegationMember
import dev.kinetick.kinetick.api.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentLinkTest {

    @Test
    fun mergesMembersChildrenAndBackgroundOntoOneRow() {
        val links = mergeSubagentLinks(
            parentSessionId = "parent",
            members = listOf(
                DelegationMember(
                    sessionId = "child",
                    parentSessionId = "parent",
                    agentName = "scout",
                    task = "read the repo",
                    status = "running",
                    updatedAtMs = 10,
                ),
            ),
            children = listOf(
                SessionInfo(
                    sessionId = "child",
                    parentSessionId = "parent",
                    title = "ignored when the task is set",
                    sessionKind = "task",
                    updatedAt = 50,
                ),
                SessionInfo(
                    sessionId = "other",
                    parentSessionId = "parent",
                    title = "side peek",
                    sessionKind = "peek",
                    status = "idle",
                    updatedAt = 20,
                ),
                SessionInfo(sessionId = "parent", title = "not a child"),
            ),
            background = listOf(
                BackgroundTask(id = "b1", label = "lint", status = "queued", sessionId = "bg"),
                BackgroundTask(id = "b2", label = "no session", status = "running"),
            ),
        )
        assertEquals(listOf("child", "bg", "other"), links.map { it.sessionId })
        assertEquals("read the repo", links[0].title)
        assertEquals("running", links[0].status)
        assertEquals(50L, links[0].updatedAt)
        assertEquals("lint", links[1].title)
        assertEquals("side peek", links[2].title)
    }
}
