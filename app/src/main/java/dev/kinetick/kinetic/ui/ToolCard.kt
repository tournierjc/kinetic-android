package dev.kinetick.kinetic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import dev.kinetick.kinetic.api.ChatMessage
import dev.kinetick.kinetic.api.MessagePart
import dev.kinetick.kinetic.api.ToolCall

/** Collapsible tool-call card: name, status, input/output, structured preview. */
@Composable
fun ToolCard(call: ToolCall) {
    var expanded by remember { mutableStateOf(call.preview != null) }
    val statusColor = when (call.status?.lowercase()) {
        "error", "failed" -> MaterialTheme.colorScheme.error
        "running", "started", "in_progress" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    call.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                call.status?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                call.durationMs?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("${it}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A one-line hint stays visible while collapsed.
            if (!expanded) {
                call.preview?.blocks?.firstOrNull()?.let { b ->
                    val hint = when (b) {
                        is dev.kinetick.kinetic.api.PreviewBlock.Diff -> "${b.path ?: "diff"}  +${b.addedLines} −${b.removedLines}"
                        is dev.kinetick.kinetic.api.PreviewBlock.File -> "${b.path ?: "file"}  ${b.lineCount} lines"
                        is dev.kinetick.kinetic.api.PreviewBlock.Summary -> b.message
                    }
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            "$label ${if (expanded) "▾" else "▸"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (expanded) raw else raw.take(160).replace('\n', ' ') + " …",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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

/** An assistant message: thinking + text + tool calls, in the order received. */
@Composable
fun AssistantParts(message: ChatMessage) {
    val parts = message.parts
    if (parts.isEmpty()) {
        if (!message.thinking.isNullOrBlank()) ThinkingBlock(message.thinking, message.thinkingDurationMs)
        message.toolCalls.forEach { ToolCard(it) }
        if (message.content.isNotBlank()) Text(message.content, style = MaterialTheme.typography.bodyMedium)
    } else {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Thinking -> ThinkingBlock(part.content, part.durationMs)
                is MessagePart.Text -> if (part.content.isNotBlank()) {
                    Text(part.content, style = MaterialTheme.typography.bodyMedium)
                }
                is MessagePart.Tool -> ToolCard(part.toolCall)
            }
        }
    }
    message.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                bits.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                append(if (expanded) "▾ thinking" else "▸ thinking")
                durationMs?.let { append(" (${it}ms)") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
