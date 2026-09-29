package dev.kinetick.kinetic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinetick.kinetic.api.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Messages from the same author collapse when they land within this window. */
internal const val TRANSCRIPT_GROUP_WINDOW_MS = 5 * 60_000L

private val AvatarSize = 36.dp
private val AvatarGap = 10.dp

internal sealed interface ProsePiece {
    data class Text(val value: String) : ProsePiece
    data class Code(val value: String, val language: String?) : ProsePiece
}

internal sealed interface TranscriptEntry {
    val key: String

    data class Day(val label: String, override val key: String) : TranscriptEntry
    data class Message(
        val message: ChatMessage,
        val grouped: Boolean,
        override val key: String,
    ) : TranscriptEntry
}

/**
 * Slack/Discord transcript: a day rule when the calendar day changes, then a
 * full-width row. A follow-up from the same author inside five minutes drops
 * the avatar and name so the thread reads as one block.
 */
internal fun buildTranscript(
    messages: List<ChatMessage>,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<TranscriptEntry> {
    val out = mutableListOf<TranscriptEntry>()
    var previousDay: LocalDate? = null
    var previous: ChatMessage? = null
    messages.forEachIndexed { index, message ->
        val day = message.timestamp?.takeIf { it > 0 }?.let { localDate(it, zone) }
        if (day != null && day != previousDay) {
            out += TranscriptEntry.Day(
                label = dayLabel(day, nowMs, zone),
                key = "day-$day-$index",
            )
            previousDay = day
        }
        val grouped = previous?.let { continuesGroup(it, message, zone) } == true && day == previousDay
        out += TranscriptEntry.Message(
            message = message,
            grouped = grouped && message.role != "system",
            key = message.id ?: "idx$index",
        )
        // A system line breaks the author group so the next message stands alone.
        previous = if (message.role == "system") null else message
    }
    return out
}

internal fun continuesGroup(previous: ChatMessage, next: ChatMessage, zone: ZoneId): Boolean {
    if (previous.role != next.role || previous.role == "system") return false
    val a = previous.timestamp?.takeIf { it > 0 } ?: return false
    val b = next.timestamp?.takeIf { it > 0 } ?: return false
    if (kotlin.math.abs(b - a) > TRANSCRIPT_GROUP_WINDOW_MS) return false
    return localDate(a, zone) == localDate(b, zone)
}

internal fun authorName(role: String, agentName: String?): String = when (role) {
    "user" -> "You"
    "system" -> "System"
    else -> agentName?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    } ?: "Assistant"
}

internal fun transcriptClock(
    epochMs: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    if (epochMs == null || epochMs <= 0) return null
    return Instant.ofEpochMilli(epochMs)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("h:mm a", locale))
}

internal fun splitProse(raw: String): List<ProsePiece> {
    val text = raw.trimStart { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<ProsePiece>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("```", i)
        if (open < 0) {
            appendText(out, text.substring(i))
            break
        }
        if (open > i) appendText(out, text.substring(i, open))
        val after = open + 3
        val close = text.indexOf("```", after)
        if (close < 0) {
            out += codePiece(text.substring(after))
            break
        }
        out += codePiece(text.substring(after, close))
        i = close + 3
    }
    return out
}

private fun codePiece(span: String): ProsePiece.Code {
    val nl = span.indexOf('\n')
    if (nl < 0) return ProsePiece.Code(span, null)
    val lang = span.substring(0, nl).trim().ifBlank { null }
    return ProsePiece.Code(span.substring(nl + 1).trimEnd('\n'), lang)
}

private fun appendText(out: MutableList<ProsePiece>, value: String) {
    val shown = value.trim('\n')
    if (shown.isNotBlank()) out += ProsePiece.Text(shown)
}

private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

private fun dayLabel(day: LocalDate, nowMs: Long, zone: ZoneId): String {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (day.year == today.year) {
            day.format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))
        } else {
            day.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
        }
    }
}

@Composable
internal fun ChatTranscript(
    messages: List<ChatMessage>,
    listState: LazyListState,
    channelName: String,
    agentName: String?,
    streamingMessageId: String?,
) {
    val entries = remember(messages) { buildTranscript(messages) }
    LaunchedEffect(entries.size, streamingMessageId) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    if (entries.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "#",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                channelName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is the beginning of this conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 12.dp),
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is TranscriptEntry.Day -> DayDivider(entry.label)
                is TranscriptEntry.Message -> TranscriptMessage(
                    message = entry.message,
                    grouped = entry.grouped,
                    agentName = agentName,
                )
            }
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TranscriptMessage(message: ChatMessage, grouped: Boolean, agentName: String?) {
    if (message.role == "system") {
        SystemLine(message.content.ifBlank { message.kind ?: "system" })
        return
    }
    val name = authorName(message.role, agentName)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 16.dp,
                top = if (grouped) 1.dp else 8.dp,
                bottom = if (grouped) 1.dp else 2.dp,
            ),
    ) {
        if (grouped) {
            Spacer(Modifier.width(AvatarSize + AvatarGap))
        } else {
            AuthorAvatar(message.role, name)
            Spacer(Modifier.width(AvatarGap))
        }
        Column(Modifier.weight(1f)) {
            if (!grouped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (message.role == "user") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    transcriptClock(message.timestamp)?.let { clock ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            clock,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            if (message.role == "user") {
                MessageProse(message.content, streaming = false)
            } else {
                AssistantParts(message)
            }
        }
    }
}

@Composable
private fun AuthorAvatar(role: String, name: String) {
    val background = if (role == "user") {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val foreground = if (role == "user") {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondary
    }
    Box(
        Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
    }
}

@Composable
private fun SystemLine(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Flat message body: prose plus fenced code, the way a channel renders it. */
@Composable
fun MessageProse(content: String, streaming: Boolean) {
    val pieces = remember(content) { splitProse(content) }
    if (pieces.isEmpty()) {
        if (streaming) {
            Text("▍", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pieces.forEachIndexed { index, piece ->
            val last = index == pieces.lastIndex
            when (piece) {
                is ProsePiece.Text -> SelectionContainer {
                    Text(
                        if (last && streaming) piece.value + " ▍" else piece.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = Fonts.Ui,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is ProsePiece.Code -> CodeBlock(piece, caret = last && streaming)
            }
        }
    }
}

@Composable
private fun CodeBlock(piece: ProsePiece.Code, caret: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            piece.language?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            SelectionContainer {
                Text(
                    if (caret) piece.value + " ▍" else piece.value,
                    fontFamily = Fonts.Code,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
