package dev.kinetick.kinetic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinetick.kinetic.api.PreviewBlock
import dev.kinetick.kinetic.api.StructuredPreview

private val AddBg = Color(0xFF1B3B22)
private val DelBg = Color(0xFF3C1D1D)
private val AddFg = Color(0xFF9BE49B)
private val DelFg = Color(0xFFF0A0A0)
private val HeaderFg = Color(0xFF9FB8D0)
private val HunkFg = Color(0xFFC6A0F0)
private val NeutralFg = Color(0xFFCFCFCF)

/** Renders a unified diff with per-line coloring, capped for very large diffs. */
@Composable
fun DiffText(diff: String, maxLines: Int = 300) {
    val lines = remember(diff, maxLines) { diff.split('\n').take(maxLines) }
    val truncated = remember(diff, maxLines) { diff.split('\n').size > maxLines }
    val text = remember(lines) {
        buildAnnotatedString {
            lines.forEachIndexed { i, line ->
                val (fg, bg) = when {
                    line.startsWith("+++") || line.startsWith("---") -> HeaderFg to null
                    line.startsWith("@@") -> HunkFg to null
                    line.startsWith("+") -> AddFg to AddBg
                    line.startsWith("-") -> DelFg to DelBg
                    else -> NeutralFg to null
                }
                withStyle(SpanStyle(color = fg, background = bg ?: Color.Transparent)) { append(line) }
                if (i != lines.lastIndex) append("\n")
            }
        }
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
    if (truncated) {
        Text(
            "… diff truncated at $maxLines lines",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Renders the TUI structured preview carried by a tool call: diff blocks,
 * file contents, and non-renderable summaries.
 */
@Composable
fun StructuredPreviewView(preview: StructuredPreview, maxLines: Int = 300) {
    Column(Modifier.fillMaxWidth()) {
        preview.state?.let { state ->
            Text(
                when (state) {
                    "applied" -> "applied"
                    "proposed" -> "proposed"
                    "not-applied" -> "not applied"
                    else -> state
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        preview.blocks.forEach { block ->
            when (block) {
                is PreviewBlock.Diff -> PreviewCard(
                    title = block.path ?: "diff",
                    meta = "+${block.addedLines} −${block.removedLines}"
                ) {
                    DiffText(block.diff, maxLines)
                    if (block.truncated) {
                        Text(
                            "… ${block.omittedLines ?: 0} more lines omitted by the server",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is PreviewBlock.File -> PreviewCard(
                    title = block.path ?: "file",
                    meta = "${block.lineCount} lines"
                ) {
                    DiffText(block.content, maxLines)
                    if (block.truncated) {
                        Text(
                            "… ${block.omittedLines ?: 0} more lines omitted by the server",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is PreviewBlock.Summary -> PreviewCard(
                    title = block.path ?: "preview",
                    meta = block.reason ?: ""
                ) {
                    Text(
                        block.message + (block.byteCount?.let { " ($it bytes)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(title: String, meta: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}
