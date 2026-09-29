@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.ChatMessage
import dev.kinetick.kinetic.api.Interactions
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.ServerToken
import dev.kinetick.kinetic.api.PermissionRequest
import dev.kinetick.kinetic.api.SessionInfo
import dev.kinetick.kinetic.api.Wire
import dev.kinetick.kinetic.data.SettingsStore
import dev.kinetick.kinetic.events.EventBus
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
    val app = context.applicationContext as KineticApp
    val baseUrl by app.settings.baseUrl.collectAsState(initial = SettingsStore.NO_SERVER)
    val token by app.settings.token.collectAsState(initial = "")
    val configured = baseUrl.isNotBlank()
    val client = remember(baseUrl, token) { KcodeClient(baseUrl, token) }
    val store = remember(sessionId, baseUrl) { ChatStore() }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(sessionId, baseUrl, token) {
        if (!configured) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { client.getSession(sessionId) } }
            .onSuccess { session = it }
            .onFailure { error = it.message }
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

    LaunchedEffect(state.messages.size, state.streamingMessageId) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    fun startStream(text: String) {
        source?.cancel()
        source = client.promptStream(sessionId, text, null, object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                store.apply(Wire.streamEvent(type, data))
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                error = if (response?.code == 401) {
                    KcodeClient.unauthorizedMessage(ServerToken.normalize(client.token).isNotEmpty())
                } else {
                    "stream failed: ${t?.message ?: response?.message ?: "closed"}"
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
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(session?.title ?: sessionId, maxLines = 1)
                        if (state.running) {
                            Spacer(Modifier.width(8.dp))
                            LinearProgressIndicator(
                                Modifier.width(42.dp).height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session?.workspaceDir?.substringAfterLast('/') ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.status != "idle") {
                            Spacer(Modifier.width(6.dp))
                            StatusPill(state.status)
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
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        if (!configured) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("No kcode server configured.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
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
                0 -> ChatTab(state, listState) { scope.launch { reloadHistory() } }
                1 -> AgentsTab(client, sessionId) { scope.launch { reloadHistory() } }
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
private fun ChatTab(state: TurnState, listState: LazyListState, onResync: () -> Unit) {
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
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.messages, key = { it.id ?: "m${it.hashCode()}" }) { message ->
                MessageRow(message)
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = when {
            isUser -> Alignment.End
            isSystem -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        when {
            isUser -> Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                SelectionContainer {
                    Text(
                        message.content.trim(),
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = Fonts.Ui,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            isSystem -> Text(
                message.content.ifBlank { message.kind ?: "system" },
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                AssistantParts(message)
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onQueue: () -> Unit,
) {
    val canSend = value.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (running) {
                Text(
                    "🔄 A turn is running — Send steers it, Queue defers it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (running) "Steer the agent…" else "Message…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    FilledIconButton(
                        enabled = canSend,
                        onClick = onSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(if (running) "🧭" else "➤", style = MaterialTheme.typography.titleMedium)
                    }
                    if (running) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            enabled = canSend,
                            onClick = onQueue,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        ) { Text("Queue", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}
