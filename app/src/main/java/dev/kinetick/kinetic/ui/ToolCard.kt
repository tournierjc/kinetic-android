package dev.kinetick.kinetic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import dev.kinetick.kinetic.api.ChatMessage
import dev.kinetick.kinetic.api.MessagePart
import dev.kinetick.kinetic.api.ToolCall

/** Which glyph marks a tool, so a transcript is scannable without reading names. */
private fun toolEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        listOf("shell", "bash", "exec", "command", "terminal", "run_").any { n.contains(it) } -> "🖥️"
        listOf("write", "edit", "patch", "replace", "create", "apply").any { n.contains(it) } -> "✍️"
        listOf("read", "view", "cat_", "open_file").any { n.contains(it) } -> "📖"
        listOf("glob", "grep", "search", "find").any { n.contains(it) } -> "🔍"
        listOf("web", "fetch", "http", "browse", "url").any { n.contains(it) } -> "🌐"
        listOf("todo", "plan", "task").any { n.contains(it) } -> "🗒️"
        listOf("agent", "delegate").any { n.contains(it) } -> "🤖"
        n.contains("skill") -> "🧩"
        n.contains("memory") -> "🧠"
        listOf("image", "screenshot", "vision").any { n.contains(it) } -> "🖼️"
        listOf("git", "diff").any { n.contains(it) } -> "🌿"
        n.contains("mcp") -> "🔌"
        listOf("python", "notebook", "repl").any { n.contains(it) } -> "🐍"
        else -> "🔧"
    }
}

private fun statusEmoji(status: String): String = when (status) {
    "running", "started", "in_progress", "pending" -> "🔄"
    "error", "failed" -> "❌"
    "stopped", "cancelled", "aborted" -> "⏹️"
    "completed", "done", "success", "succeeded" -> "✅"
    else -> "•"
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "${ms} ms" else "%.1f s".format(ms / 1000.0)

/** Collapsible tool-call card: glyph, name, status, input/output, preview. */
@Composable
fun ToolCard(call: ToolCall) {
    var expanded by remember { mutableStateOf(call.preview != null) }
    val status = call.status?.lowercase() ?: "unknown"
    val failed = status in setOf("error", "failed")
    val running = status in setOf("running", "started", "in_progress", "pending")
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = when {
        failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        running -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(toolEmoji(call.name), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        call.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = Fonts.Code,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    call.durationMs?.let {
                        Text(
                            formatDuration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.16f),
                ) {
                    Text(
                        "${statusEmoji(status)} $status",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A one-line hint stays visible while collapsed.
            if (!expanded) {
                call.preview?.blocks?.firstOrNull()?.let { b ->
                    val hint = when (b) {
                        is dev.kinetick.kinetic.api.PreviewBlock.Diff -> "📝 ${b.path ?: "diff"}  +${b.addedLines} −${b.removedLines}"
                        is dev.kinetick.kinetic.api.PreviewBlock.File -> "📄 ${b.path ?: "file"}  ${b.lineCount} lines"
                        is dev.kinetick.kinetic.api.PreviewBlock.Summary -> b.message
                    }
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (expanded) {
                call.preview?.let { StructuredPreviewView(it) }
                call.input?.let { JsonBlock("input", it) }
                call.output?.let { JsonBlock("output", it) }
                call.error?.let { JsonBlock("error", it, error = true) }
            }
        }
    }
}

@Composable
private fun JsonBlock(label: String, el: JsonElement, error: Boolean = false) {
    val raw = remember(el) { prettyJson(el) }
    if (raw.isBlank()) return
    var expanded by remember { mutableStateOf(raw.length < 600) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(
            (if (error) "❌" else "▪") + " $label ${if (expanded) "▾" else "▸"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (expanded) raw else raw.take(160).replace('\n', ' ') + " …",
            fontSize = 11.sp,
            fontFamily = Fonts.Code,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun prettyJson(el: JsonElement): String = when {
    el.isJsonNull -> ""
    el.isJsonPrimitive -> el.asString
    else -> runCatching {
        com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(el)
    }.getOrDefault(el.toString())
}

/** Assistant prose in a bubble; thinking and tools live outside it, in order. */
@Composable
fun AssistantBubble(content: String, streaming: Boolean) {
    // The runtime prefixes assistant text with blank lines; in a bubble they
    // read as a hole above the answer.
    val shown = content.trimStart('\n', '\r', ' ', '\t')
    if (shown.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(max = 360.dp).padding(vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SelectionContainer {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = Fonts.Ui,
                )
            }
            if (streaming) {
                Text(
                    "▍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** An assistant message: thinking + text + tool calls, in the order received. */
@Composable
fun AssistantParts(message: ChatMessage) {
    val parts = message.parts
    if (parts.isEmpty()) {
        if (!message.thinking.isNullOrBlank()) ThinkingBlock(message.thinking, message.thinkingDurationMs)
        message.toolCalls.forEach { ToolCard(it) }
        if (message.content.isNotBlank()) AssistantBubble(message.content, message.streaming)
    } else {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Thinking -> ThinkingBlock(part.content, part.durationMs)
                is MessagePart.Text -> if (part.content.isNotBlank()) {
                    AssistantBubble(part.content, message.streaming)
                }
                is MessagePart.Tool -> ToolCard(part.toolCall)
            }
        }
    }
    message.error?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text(
                "❌ $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
    message.usage?.let { u ->
        val bits = listOfNotNull(
            u.inputTokens?.let { "in $it" },
            u.outputTokens?.let { "out $it" },
            u.reasoningTokens?.takeIf { it > 0 }?.let { "reason $it" },
            u.cacheReadTokens?.takeIf { it > 0 }?.let { "cache $it" },
        )
        if (bits.isNotEmpty()) {
            Text(
                "🔢 " + bits.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ThinkingBlock(content: String, durationMs: Long?) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(
            buildString {
                append(if (expanded) "💭 thinking ▾" else "💭 thinking ▸")
                durationMs?.let { append(" · ${formatDuration(it)}") }
            },
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
