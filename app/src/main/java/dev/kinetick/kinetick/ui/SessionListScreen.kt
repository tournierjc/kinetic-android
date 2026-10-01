@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetick.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetick.KinetickApp
import dev.kinetick.kinetick.api.KcodeClient
import dev.kinetick.kinetick.api.SessionInfo
import dev.kinetick.kinetick.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Relative "2h ago" stamp — the list read as an undated wall of titles. */
internal fun relativeTime(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
    if (epochMs == null || epochMs <= 0) return null
    val d = nowMs - epochMs
    if (d < 0) return "now"
    val minutes = d / 60_000
    val hours = d / 3_600_000
    val days = d / 86_400_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 30 -> "${days / 7}w"
        else -> "${days / 30}mo"
    }
}

/** Sort key: pinned first, then most recently touched. */
private fun sortSessions(list: List<SessionInfo>): List<SessionInfo> =
    list.sortedWith(
        compareByDescending<SessionInfo> { it.pinned == true }
            .thenByDescending { it.updatedAt ?: it.createdAt ?: 0L }
    )

@Composable
fun SessionListScreen(
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as KinetickApp
    val server by app.settings.server.collectAsState(
        initial = SettingsStore.ServerSettings(SettingsStore.NO_SERVER, ""),
    )
    val baseUrl = server.baseUrl
    val client = remember(server) { KcodeClient(server.baseUrl, server.token) }
    val configured = client.configured
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
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

    LaunchedEffect(server, showArchived) { if (configured) load() }

    // Subagent (task / parented) sessions are opened from their parent, never
    // from this list — including when a search would otherwise surface them.
    val listed = remember(sessions) { sessions.filterNot { it.isSubagentSession() } }
    val filtered = remember(listed, query) {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) listed
        else listed.filter {
            (it.title ?: "").lowercase().contains(q) ||
                (it.workspaceDir ?: "").lowercase().contains(q) ||
                it.sessionId.lowercase().contains(q)
        }
        sortSessions(base)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { KinetickWordmark() },
                actions = {
                    IconButton(onClick = { load() }) { Text("⟳", style = MaterialTheme.typography.titleMedium) }
                    IconButton(onClick = { showArchived = !showArchived }) {
                        Text(if (showArchived) "🗄" else "🗃", style = MaterialTheme.typography.titleMedium)
                    }
                    ThemeToggleButton(themeMode) { onSetTheme(themeMode.next()) }
                    IconButton(onClick = onSettings) { Text("⚙", style = MaterialTheme.typography.titleMedium) }
                },
            )
        },
        floatingActionButton = {
            if (configured) {
                FloatingActionButton(
                    onClick = { creating = true },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!configured) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (baseUrl.isBlank()) "No kcode server yet" else "Server token required",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (baseUrl.isBlank()) {
                            "On the machine running your sessions:\n" +
                                "kcode --server --host 0.0.0.0 --port 8788\n\n" +
                                "Then enter that address and the bearer token from\n" +
                                "~/.kinetick/run/session-server.token."
                        } else {
                            "kcode --server refuses every request without\n" +
                                "Authorization: Bearer.\n\n" +
                                "Paste the token from ~/.kinetick/run/session-server.token."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onSettings) { Text("Set up server") }
                }
            }

            if (configured) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search title, workspace, id…") },
                    leadingIcon = { Text("🔍", style = MaterialTheme.typography.titleSmall) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { query = "" }) { Text("Clear") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }

            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        "⚠ Can't reach $baseUrl — $it",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (refreshing && sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(Modifier.width(120.dp))
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filtered.isEmpty() && !refreshing && configured) {
                    item {
                        Text(
                            if (query.isNotBlank()) "Nothing matches “$query”."
                            else if (showArchived) "No sessions, archived included."
                            else "No sessions yet — tap ＋ to start one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(filtered, key = { it.sessionId }) { s ->
                    SessionRow(
                        session = s,
                        onOpen = { onOpen(s.sessionId) },
                        onActions = { actionTarget = s },
                    )
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
        val pinned = target.pinned == true
        val archived = target.archived == true
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.title ?: target.sessionId, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionLine(pinned.let { if (it) "📌 Unpin" else "📌 Pin" }) {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.setPinned(target.sessionId, !pinned)
                                }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }
                    ActionLine(if (archived) "🗄 Unarchive" else "🗄 Archive") {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.setArchived(target.sessionId, !archived)
                                }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }
                    ActionLine("🗑 Delete", danger = true) {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { client.deleteSession(target.sessionId) }
                            }.onFailure { error = it.message }
                            actionTarget = null
                            load()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionTarget = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ActionLine(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionRow(session: SessionInfo, onOpen: () -> Unit, onActions: () -> Unit) {
    val status = session.status ?: "idle"
    SectionCard(
        onClick = onOpen,
        borderColor = if (session.pinned == true) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title ?: session.sessionId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.pinned == true) {
                        Spacer(Modifier.width(6.dp))
                        Text("📌", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        session.workspaceDir?.substringAfterLast('/'),
                        session.model?.modelId,
                    ).joinToString("  ·  ").ifBlank { session.sessionId.take(12) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status == "error") {
                    session.errorMessage?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(status)
                Spacer(Modifier.height(4.dp))
                relativeTime(session.updatedAt ?: session.createdAt)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconAction("⋯", contentDescription = "Session actions", onClick = onActions, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
