package dev.kinetick.kinetick.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinetick.kinetick.api.PermissionRequest

/**
 * Permission prompt (`permission.ask`): shows what the tool wants to run, its
 * diff preview when the runtime provides one, and the three decisions the
 * server accepts (allowOnce / allowAlways / deny).
 */
@Composable
fun PermissionDialog(
    request: PermissionRequest,
    busy: Boolean,
    onDecision: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Permission requested")
                Text(
                    request.toolName ?: "tool",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = Fonts.Code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                request.toolDescription?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                request.reason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                request.toolInput?.let {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            it.take(4000),
                            fontSize = 11.sp,
                            fontFamily = Fonts.Code,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                if (request.ruleContents.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    request.ruleContents.forEach {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                request.preview?.let {
                    Spacer(Modifier.height(8.dp))
                    StructuredPreviewView(it, maxLines = 120)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onDecision("allowOnce") }) { Text("Allow once") }
        },
        dismissButton = {
            Row {
                if (request.allowAlwaysSupported) {
                    TextButton(enabled = !busy, onClick = { onDecision("allowAlways") }) { Text("Always") }
                }
                TextButton(enabled = !busy, onClick = { onDecision("deny") }) { Text("Deny") }
            }
        }
    )
}
