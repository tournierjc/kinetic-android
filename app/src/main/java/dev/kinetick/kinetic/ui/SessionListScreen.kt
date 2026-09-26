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
import dev.kinetick.kinetic.api.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SessionListScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KineticApp
    val baseUrl by app.settings.baseUrl.collectAsState(initial = "")
    val client = remember(baseUrl) { KcodeClient(baseUrl) }
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newWorkspace by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<SessionInfo?>(null) }

    fun load() {
        scope.launch {
            refreshing = true
            runCatching {
                withContext(Dispatchers.IO) {
                    client.listSessions(limit = 80, includeArchived = showArchived)
                }
            }
                .onSuccess { sessions = it.sessions; error = null }
                .onFailure { error = it.message }
            refreshing = false
        }
    }

    LaunchedEffect(baseUrl, showArchived) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kinetic") },
                actions = {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(if (showArchived) "Hide archived" else "Archived")
                    }
                    TextButton(onClick = { load() }) { Text("Refresh") }
                    TextButton(onClick = onSettings) { Text("Server") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Text("+") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            error?.let {
                Text(
                    "Can't reach $baseUrl — $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (refreshing && sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(sessions, key = { it.sessionId }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(s.sessionId) }
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title ?: s.sessionId,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(
                                    s.agentName,
                                    s.workspaceDir?.substringAfterLast('/'),
                                    s.status?.takeIf { it != "idle" },
                                    s.model?.modelId,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (s.pinned == true) Text("📌")
                        if (s.archived == true) Text(" 🗄")
                        TextButton(onClick = { actionTarget = s }) { Text("⋯") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New session") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newWorkspace,
                        onValueChange = { newWorkspace = it },
                        label = { Text("Workspace directory (on the server host)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newWorkspace.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.createSession(newWorkspace.trim(), newTitle.trim().ifBlank { null })
                                }
                            }.onSuccess {
                                newWorkspace = ""
                                newTitle = ""
                                creating = false
                                load()
                            }.onFailure { error = it.message }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } }
        )
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.title ?: target.sessionId, maxLines = 2) },
            text = {
                Column {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.setPinned(target.sessionId, target.pinned != true)
                                }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }) { Text(if (target.pinned == true) "Unpin" else "Pin") }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.setArchived(target.sessionId, target.archived != true)
                                }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }) { Text(if (target.archived == true) "Unarchive" else "Archive") }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { client.deleteSession(target.sessionId) }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { actionTarget = null }) { Text("Close") } }
        )
    }
}
