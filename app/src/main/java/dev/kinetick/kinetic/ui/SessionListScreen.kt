@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SessionListScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KineticApp
    val baseUrl by app.settings.baseUrl.collectAsState(initial = "")

    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            refreshing = true
            try {
                val page = withContext(Dispatchers.IO) {
                    KcodeClient(baseUrl).listSessions()
                }
                sessions = page.sessions
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(baseUrl) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Kinetic") },
            actions = {
                TextButton(onClick = onSettings) { Text("Server") }
                TextButton(onClick = { load() }) { Text("Refresh") }
            }
        )
        if (error != null) {
            Text(
                "Can't reach $baseUrl — ${error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (refreshing && sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(sessions, key = { it.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(s.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title ?: s.id,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                s.agent,
                                s.workspaceDir?.substringAfterLast('/'),
                                s.updatedAt?.take(16)?.replace('T', ' ')
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (s.pinned == true) Text("📌")
                }
                HorizontalDivider()
            }
        }
    }
}
