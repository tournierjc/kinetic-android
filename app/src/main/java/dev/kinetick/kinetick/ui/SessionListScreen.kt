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
import dev.kinetick.kinetick.api.SessionInfo
import dev.kinetick.kinetick.data.ServerEntry
import dev.kinetick.kinetick.data.ServerRegistry
import dev.kinetick.kinetick.data.SettingsStore
import dev.kinetick.kinetick.data.configured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** A section in the merged list: one server plus the sessions it returned. */
internal data class ServerRow(val server: ServerEntry, val sessions: List<SessionInfo>)

/** A stable key for cross-server composite ids (LazyColumn, chat stores). */
internal fun sessionKey(serverId: String, sessionId: String): String = "$serverId#$sessionId"

/** Sort key: pinned first, then most recently touched. */
private fun sortSessions(list: List<SessionInfo>): List<SessionInfo> =
    list.sortedWith(
        compareByDescending<SessionInfo> { it.pinned == true }
            .thenByDescending { it.updatedAt ?: it.createdAt ?: 0L }
    )

@Composable
fun SessionListScreen(
    onOpen: (String, String) -> Unit,
    onSettings: () -> Unit,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as KinetickApp
    val config by app.settings.config.collectAsState(initial = SettingsStore.Config())
    val servers = config.servers
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var rows by remember { mutableStateOf<List<ServerRow>>(emptyList()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshing by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var creatingFor by remember { mutableStateOf<ServerEntry?>(null) }
    var newWorkspace by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Pair<ServerEntry, SessionInfo>?>(null) }

    val configuredServers = remember(servers) { servers.filter { it.configured() } }
    val anyConfigured = configuredServers.isNotEmpty()

    fun fail(server: ServerEntry, message: String?) {
        errors = errors + (server.id to (message ?: "request failed"))
    }

    /** Queries every configured server in parallel; failures stay per-server. */
    fun load() {
        scope.launch {
            refreshing = true
            val results: List<Pair<ServerEntry, Result<List<SessionInfo>>>> =
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        configuredServers.map { server ->
                            async {
                                server to runCatching {
                                    KinetickApp.clientFor(server)
                                        .listSessions(limit = 80, includeArchived = showArchived)
                                        .sessions
                                }
                            }
                        }.awaitAll()
                    }
                }
            rows = results.mapNotNull { (server, result) ->
                result.getOrNull()?.let { ServerRow(server, it) }
            }
            errors = results.mapNotNull { (server, result) ->
                result.exceptionOrNull()?.let { server.id to (it.message ?: "unreachable") }
            }.toMap()
            refreshing = false
        }
    }

    LaunchedEffect(servers, showArchived) { if (anyConfigured) load() }

    // Subagent (task / parented) sessions are opened from their parent, never
    // from this list — including when a search would otherwise surface them.
    val visibleRows = remember(rows) {
        rows.map { row -> row.copy(sessions = row.sessions.filterNot { it.isSubagentSession() }) }
            .filter { it.sessions.isNotEmpty() }
    }
    val filtered = remember(visibleRows, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) visibleRows
        else visibleRows.mapNotNull { row ->
            val kept = row.sessions.filter {
                (it.title ?: "").lowercase().contains(q) ||
                    (it.workspaceDir ?: "").lowercase().contains(q) ||
                    it.sessionId.lowercase().contains(q) ||
                    ServerRegistry.label(row.server).lowercase().contains(q)
            }
            row.copy(sessions = sortSessions(kept)).takeIf { it.sessions.isNotEmpty() }
        }
    }
    val multiServer = filtered.size > 1

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
            if (anyConfigured) {
                FloatingActionButton(
                    onClick = {
                        // New sessions land on the active server — the same
                        // one Settings marks.
                        creatingFor = config.active?.takeIf { it.configured() } ?: configuredServers.first()
                    },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("＋", style = MaterialTheme.typography.titleLarge) }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!anyConfigured) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (servers.isEmpty()) "No kcode server yet" else "Server token required",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (servers.isEmpty()) {
                            "On the machine running your sessions:\n" +
                                "kcode --server --host 0.0.0.0 --port 8788\n\n" +
                                "Then enter that address and the bearer token from\n" +
                                "~/.kinetick/run/session-server.token.\n" +
                                "You can register several servers."
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
                    Button(onClick = onSettings) { Text("Set up servers") }
                }
            }

            if (anyConfigured) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search title, workspace, server, id…") },
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

            errors.forEach { (serverId, message) ->
                val server = servers.firstOrNull { it.id == serverId } ?: return@forEach
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        "⚠ Can't reach ${ServerRegistry.label(server)} — $message",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (refreshing && rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(Modifier.width(120.dp))
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filtered.isEmpty() && !refreshing && anyConfigured) {
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
                filtered.forEach { row ->
                    // Multi-server: one section header per server so rows are
                    // never ambiguous. A single server needs no header.
                    if (multiServer) {
                        item(key = "hdr-${row.server.id}") {
                            SectionHeader(
                                title = ServerRegistry.label(row.server),
                                count = row.sessions.size,
                            )
                        }
                    }
                    items(row.sessions, key = { sessionKey(row.server.id, it.sessionId) }) { s ->
                        SessionRow(
                            session = s,
                            serverLabel = if (multiServer) null
                            else ServerRegistry.detail(row.server).takeIf { row.server.name.isNotBlank() },
                            onOpen = { onOpen(row.server.id, s.sessionId) },
                            onActions = { actionTarget = row.server to s },
                        )
                    }
                }
            }
        }
    }

    creatingFor?.let { target ->
        AlertDialog(
            onDismissRequest = { creatingFor = null },
            title = { Text("New session · ${ServerRegistry.label(target)}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newWorkspace,
                        onValueChange = { newWorkspace = it },
                        label = { Text("Workspace directory (on ${ServerRegistry.detail(target)})") },
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
                                    KinetickApp.clientFor(target)
                                        .createSession(newWorkspace.trim(), newTitle.trim().ifBlank { null })
                                }
                            }.onSuccess {
                                newWorkspace = ""
                                newTitle = ""
                                creatingFor = null
                                load()
                            }.onFailure { fail(target, it.message) }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creatingFor = null }) { Text("Cancel") } }
        )
    }

    actionTarget?.let { (server, target) ->
        val pinned = target.pinned == true
        val archived = target.archived == true
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.title ?: target.sessionId, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        ServerRegistry.label(server),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionLine(pinned.let { if (it) "📌 Unpin" else "📌 Pin" }) {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    KinetickApp.clientFor(server).setPinned(target.sessionId, !pinned)
                                }
                            }.onFailure { fail(server, it.message) }
                            actionTarget = null
                            load()
                        }
                    }
                    ActionLine(if (archived) "🗄 Unarchive" else "🗄 Archive") {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    KinetickApp.clientFor(server).setArchived(target.sessionId, !archived)
                                }
                            }.onFailure { fail(server, it.message) }
                            actionTarget = null
                            load()
                        }
                    }
                    ActionLine("🗑 Delete", danger = true) {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    KinetickApp.clientFor(server).deleteSession(target.sessionId)
                                }
                            }.onFailure { fail(server, it.message) }
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
private fun SessionRow(
    session: SessionInfo,
    serverLabel: String?,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
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
                        serverLabel?.let { "🖥 $it" },
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
