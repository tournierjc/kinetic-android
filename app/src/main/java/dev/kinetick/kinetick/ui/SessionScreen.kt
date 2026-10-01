@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetick.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetick.KinetickApp
import dev.kinetick.kinetick.api.Interactions
import dev.kinetick.kinetick.api.KcodeClient
import dev.kinetick.kinetick.api.PermissionRequest
import dev.kinetick.kinetick.api.SessionInfo
import dev.kinetick.kinetick.api.Wire
import dev.kinetick.kinetick.data.SettingsStore
import dev.kinetick.kinetick.events.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

private val TABS = listOf("Chat", "Agents", "Queue", "Info")

@Composable
fun SessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSettings: () -> Unit,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as KinetickApp
    val server by app.settings.server.collectAsState(
        initial = SettingsStore.ServerSettings(SettingsStore.NO_SERVER, ""),
    )
    val client = remember(server) { KcodeClient(server.baseUrl, server.token) }
    val configured = client.configured
    val store = remember(sessionId, server.baseUrl) { ChatStore() }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var subagentCount by remember(sessionId) { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<SessionInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var interact by remember { mutableStateOf<Interactions?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<EventSource?>(null) }
    val listState = rememberLazyListState()

    // Notices (queue acks) clear themselves.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4000)
            notice = null
        }
    }

    suspend fun reloadHistory() {
        runCatching { withContext(Dispatchers.IO) { client.messages(sessionId, limit = 100) } }
            .onSuccess { store.seed(it.messages) }
            .onFailure { error = it.message }
    }

    suspend fun reloadInteractions() {
        runCatching { withContext(Dispatchers.IO) { client.interactions(sessionId) } }
            .onSuccess { interact = it }
    }

    LaunchedEffect(sessionId, server) {
        if (!configured) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { client.getSession(sessionId) } }
            .onSuccess { session = it }
            .onFailure { error = it.message }
        // Badge the Agents tab before it is opened. The tab replaces this with
        // the merged child count once it loads.
        runCatching { withContext(Dispatchers.IO) { client.delegation(sessionId) } }
            .onSuccess { subagentCount = it.members.size }
        reloadHistory()
        reloadInteractions()
    }

    // Runtime events relayed by the foreground service refresh the input surface.
    LaunchedEffect(sessionId) {
        EventBus.events.collect { ev ->
            if (ev.data.contains(sessionId) ||
                ev.type.startsWith("questionnaire") ||
                ev.type.startsWith("permission")
            ) {
                reloadInteractions()
            }
        }
    }

    // Safety net: while a turn runs, poll for input requests.
    LaunchedEffect(state.running) {
        while (state.running) {
            delay(3000)
            reloadInteractions()
        }
    }

    DisposableEffect(sessionId) {
        onDispose { source?.cancel() }
    }

    fun startStream(text: String) {
        source?.cancel()
        source = client.promptStream(sessionId, text, null, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                store.apply(Wire.streamEvent(type, data))
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                error = if (response?.code == 401) {
                    "Unauthorized — check the server token"
                } else {
                    "stream failed: ${t?.message ?: response?.message ?: "unknown"}"
                }
            }
        })
    }

    fun onPrimaryAction() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        error = null
        if (state.running) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { client.steer(sessionId, text) } }
                    .onFailure { error = it.message }
            }
        } else {
            startStream(text)
        }
    }

    fun onQueueAction() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.queueEnqueue(sessionId, text) } }
                .onSuccess {
                    // The ack is authoritative: an idle session drains the queue
                    // into the conversation, so the snapshot can stay empty.
                    notice = it.position?.let { p -> "Queued (position $p)" } ?: "Queued"
                    reloadHistory()
                }
                .onFailure { error = it.message }
        }
    }

    // safeDrawing keeps the composer above the navigation bar and the keyboard,
    // and the title bar below the status bar, on edge-to-edge Android 15.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopAppBar(
            title = {
                val channel = session?.title ?: sessionId
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            channel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (state.running) {
                            Spacer(Modifier.width(8.dp))
                            LinearProgressIndicator(
                                Modifier.width(42.dp).height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                    val subtitle = listOfNotNull(
                        session?.takeIf { it.isSubagentSession() }?.let { "subagent" },
                        session?.workspaceDir?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  ")
                    if (subtitle.isNotBlank() || state.status != "idle") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (state.status != "idle") {
                                if (subtitle.isNotBlank()) Spacer(Modifier.width(6.dp))
                                StatusPill(state.status)
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
            },
            actions = {
                ThemeToggleButton(themeMode) { onSetTheme(themeMode.next()) }
                if (state.running) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { client.abort(sessionId) } }
                                    .onFailure { error = it.message }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("■ Stop", style = MaterialTheme.typography.labelMedium) }
                }
                IconButton(onClick = {
                    scope.launch {
                        reloadHistory()
                        reloadInteractions()
                    }
                }) { Text("⟳", style = MaterialTheme.typography.titleMedium) }
            }
        )

        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            TABS.forEachIndexed { i, label ->
                val shown = if (i == 1 && subagentCount > 0) "Agents $subagentCount" else label
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            shown,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        session?.parentSessionId?.takeIf { it.isNotBlank() && it != sessionId }?.let { parentId ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Subagent thread",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = { onOpenSession(parentId) }) { Text("Open parent") }
                }
            }
        }

        if (!configured) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (server.baseUrl.isBlank()) "No kcode server configured." else "Server token is required.",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onSettings) { Text("Set it up") }
            }
        }

        (error ?: state.error)?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "⚠ $it",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        notice?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "✓ $it",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ChatTab(
                    state = state,
                    listState = listState,
                    channelName = session?.title ?: "session",
                    agentName = session?.agentName,
                    onResync = { scope.launch { reloadHistory() } },
                )
                1 -> AgentsTab(
                    client = client,
                    sessionId = sessionId,
                    onOpenSession = onOpenSession,
                    onCount = { subagentCount = it },
                    onTreeStopped = { scope.launch { reloadHistory() } },
                )
                2 -> QueueTab(client, sessionId)
                else -> InfoTab(
                    client = client,
                    sessionId = sessionId,
                    session = session,
                    onSessionChanged = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.getSession(sessionId) } }
                                .onSuccess { session = it }
                        }
                    },
                    onForked = onOpenSession,
                    onSessionDeleted = onBack,
                )
            }
        }

        if (tab == 0) {
            Composer(
                value = input,
                onValueChange = { input = it },
                running = state.running,
                channelName = session?.title,
                onSend = { onPrimaryAction() },
                onQueue = { onQueueAction() },
            )
        }
    }

    interact?.questionnaire?.let { q ->
        QuestionnaireDialog(
            questionnaire = q,
            busy = busy,
            onSubmit = { answers ->
                busy = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { client.replyQuestionnaire(sessionId, q.id, answers) }
                    }.onFailure { error = it.message }
                    busy = false
                    interact = interact?.copy(questionnaire = null)
                    reloadHistory()
                }
            },
            onDismiss = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { client.dismissQuestionnaire(sessionId, q.id) }
                    }
                    interact = interact?.copy(questionnaire = null)
                }
            },
        )
    }

    interact?.permissions?.firstOrNull()?.let { perm: PermissionRequest ->
        PermissionDialog(
            request = perm,
            busy = busy,
            onDecision = { decision ->
                busy = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            client.replyPermission(
                                perm.agentName ?: session?.agentName ?: "",
                                perm.requestId,
                                decision,
                            )
                        }
                    }.onFailure { error = it.message }
                    busy = false
                    interact = interact?.let { cur -> cur.copy(permissions = cur.permissions.drop(1)) }
                }
            },
            onDismiss = { interact = interact?.let { cur -> cur.copy(permissions = emptyList()) } },
        )
    }
}

@Composable
private fun ChatTab(
    state: TurnState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    channelName: String,
    agentName: String?,
    onResync: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.needsResync) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "The transcript drifted from the server.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = onResync) { Text("Reload") }
                }
            }
        }
        state.statusMessage?.takeIf { state.status == "error" }?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        ChatTranscript(
            messages = state.messages,
            listState = listState,
            channelName = channelName,
            agentName = agentName,
            streamingMessageId = state.streamingMessageId,
        )
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    channelName: String?,
    onSend: () -> Unit,
    onQueue: () -> Unit,
) {
    val canSend = value.isNotBlank()
    val hint = when {
        running -> "Steer this turn…"
        !channelName.isNullOrBlank() -> "Message #$channelName"
        else -> "Message…"
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (running) {
                Text(
                    "A turn is running. Send steers it — Queue holds a follow-up.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(hint) },
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    FilledIconButton(
                        enabled = canSend,
                        onClick = onSend,
                        modifier = Modifier.padding(bottom = 4.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(if (running) "🧭" else "➤", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = canSend,
                        onClick = onQueue,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text("Queue") }
                }
            }
        }
    }
}
