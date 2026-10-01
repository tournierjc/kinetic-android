package dev.kinetick.kinetick.ui

import dev.kinetick.kinetick.api.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatTranscriptTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 9, 29).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = day + TimeUnit.HOURS.toMillis(16)

    @Test
    fun groupsSameAuthorInsideFiveMinutesAndSplitsDays() {
        val first = day + TimeUnit.HOURS.toMillis(15)
        val followUp = first + TimeUnit.MINUTES.toMillis(2)
        val later = first + TimeUnit.MINUTES.toMillis(20)
        val yesterday = first - TimeUnit.DAYS.toMillis(1)
        val messages = listOf(
            msg("u1", "user", yesterday),
            msg("u2", "user", first),
            msg("u3", "user", followUp),
            msg("a1", "assistant", later),
        )
        val rows = buildTranscript(messages, nowMs = now, zone = zone)
        assertEquals(
            listOf("Yesterday", "user", "Today", "user", "user", "assistant"),
            rows.map {
                when (it) {
                    is TranscriptEntry.Day -> it.label
                    is TranscriptEntry.Message -> it.message.role
                }
            },
        )
        val grouped = rows.filterIsInstance<TranscriptEntry.Message>().map { it.grouped }
        assertEquals(listOf(false, false, true, false), grouped)
    }

    @Test
    fun systemLineBreaksTheGroup() {
        val t = day + TimeUnit.HOURS.toMillis(15)
        val messages = listOf(
            msg("u1", "user", t),
            msg("s1", "system", t + 1_000),
            msg("u2", "user", t + 2_000),
        )
        val grouped = buildTranscript(messages, nowMs = now, zone = zone)
            .filterIsInstance<TranscriptEntry.Message>()
            .map { it.grouped }
        assertEquals(listOf(false, false, false), grouped)
    }

    @Test
    fun clockUsesTheGivenZone() {
        val t = day + TimeUnit.HOURS.toMillis(15)
        val clock = transcriptClock(t, zone, Locale.US)!!.normalizeSpace()
        assertEquals("3:00 PM", clock)
        assertEquals(null, transcriptClock(null, zone, Locale.US))
        assertEquals(null, transcriptClock(0, zone, Locale.US))
    }

    @Test
    fun authorNames() {
        assertEquals("You", authorName("user", "mavis"))
        assertEquals("Mavis", authorName("assistant", "mavis"))
        assertEquals("Assistant", authorName("assistant", " "))
    }

    @Test
    fun proseSplitsFences() {
        val pieces = splitProse("\n\nSee\n```kotlin\nval x = 1\n```\nDone")
        assertEquals(3, pieces.size)
        assertEquals(ProsePiece.Text("See"), pieces[0])
        assertEquals(ProsePiece.Code("val x = 1", "kotlin"), pieces[1])
        assertEquals(ProsePiece.Text("Done"), pieces[2])
    }

    @Test
    fun unclosedFenceStaysCode() {
        val pieces = splitProse("```\nstill typing")
        assertEquals(1, pieces.size)
        val code = pieces.single() as ProsePiece.Code
        assertEquals(null, code.language)
        assertTrue(code.value.contains("still typing"))
        assertFalse(code.value.contains("```"))
    }

    private fun msg(id: String, role: String, at: Long) = ChatMessage(
        id = id,
        role = role,
        content = id,
        timestamp = at,
    )

    private fun String.normalizeSpace() = replace('\u202F', ' ').replace('\u00A0', ' ')
}
