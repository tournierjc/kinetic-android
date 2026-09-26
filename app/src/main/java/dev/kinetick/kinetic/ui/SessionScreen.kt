@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

data class ChatLine(val role: String, val text: String)

@Composable
fun SessionScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KineticApp
    val baseUrl by app.settings.baseUrl.collectAsState(initial = "")
    val client = remember(baseUrl) { KcodeClient(baseUrl) }
    val scope = rememberCoroutineScope()

    var lines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<EventSource?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(sessionId) {
        try {
            val page = withContext(Dispatchers.IO) { client.messages(sessionId, limit = 50) }
            // API returns newest-first on the first page; show chronological.
            lines = page.messages.reversed().map { m ->
                ChatLine(m.role, extractText(m.content))
            }
        } catch (e: Exception) {
            error = "Load failed: ${e.message}"
        }
    }

    LaunchedEffect(lines.size, streaming) {
        val total = lines.size + if (streaming != null) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    DisposableEffect(sessionId) {
        onDispose { source?.cancel() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Session", maxLines = 1) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.abort(sessionId) } }
                    }
                }) { Text("Abort") }
            }
        )
        if (error != null) {
            Text(
                error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(lines) { line -> ChatBubble(line) }
            streaming?.let { item { ChatBubble(ChatLine("assistant", it), isStreaming = true) } }
        }
        InteractionBanner(client, sessionId) { scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.interactions(sessionId) } }
                .getOrNull()?.let { interact ->
                    if (interact.questionnaire != null || !interact.permissions.isNullOrEmpty()) {
                        // Refresh transcript after answering
                        val page = withContext(Dispatchers.IO) { client.messages(sessionId, limit = 50) }
                        lines = page.messages.reversed().map { ChatLine(it.role, extractText(it.content)) }
                    }
                }
        } }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                enabled = input.isNotBlank() && source == null,
                onClick = {
                    val text = input
                    input = ""
                    streaming = ""
                    scope.launch {
                        source = withContext(Dispatchers.IO) {
                            client.promptStream(sessionId, text, null, object : EventSourceListener() {
                                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                                    when (type) {
                                        "delta" -> {
                                            val d = parseStr(data, "text") ?: parseStr(data, "delta") ?: ""
                                            streaming = (streaming ?: "") + d
                                        }
                                        "message" -> {
                                            lines = lines + ChatLine("assistant", streaming ?: "")
                                            streaming = null
                                        }
                                        "done", "end" -> {
                                            streaming?.let { s -> if (s.isNotEmpty()) lines = lines + ChatLine("assistant", s) }
                                            streaming = null
                                            source = null
                                        }
                                        "error" -> {
                                            error = parseStr(data, "error") ?: "turn error"
                                            streaming = null
                                            source = null
                                        }
                                    }
                                }

                                override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                                    error = "stream failed: ${t?.message ?: response?.message}"
                                    streaming = null
                                    source = null
                                }
                            })
                        }
                    }
                }
            ) { Text("Send") }
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine, isStreaming: Boolean = false) {
    val isUser = line.role == "user"
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isUser) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.CenterStart
    ) {
        Surface(
            tonalElevation = if (isStreaming) 3.dp else 1.dp,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(
                line.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (line.role == "tool" || line.role == "toolResult") FontFamily.Monospace
                                else FontFamily.Default
                ),
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun InteractionBanner(client: KcodeClient, sessionId: String, onAnswered: () -> Unit) {
    var interact by remember { mutableStateOf<Interactions?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionId) {
        interact = runCatching<Interactions?> { withContext(Dispatchers.IO) { client.interactions(sessionId) } }.getOrNull()
    }
    val i = interact ?: return
    val pending = !i.permissions.isNullOrEmpty() || i.questionnaire != null
    if (!pending) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            i.permissions?.firstOrNull()?.let { p ->
                Text("Permission: ${p.toolName ?: "tool"}", style = MaterialTheme.typography.titleSmall)
                p.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.replyPermission(p.agentName, p.requestId, "allowOnce") } }
                            interact = null
                            onAnswered()
                        }
                    }) { Text("Allow") }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.replyPermission(p.agentName, p.requestId, "deny") } }
                            interact = null
                            onAnswered()
                        }
                    }) { Text("Deny") }
                }
            }
            if (i.questionnaire != null) {
                Text("kcode is asking questions — open the prompt to answer",
                     style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun extractText(el: com.google.gson.JsonElement?): String = when {
    el == null -> ""
    el.isJsonPrimitive -> el.asString
    el.isJsonObject -> {
        val o = el.asJsonObject
        when {
            o.has("text") && o.get("text").isJsonPrimitive -> o.get("text").asString
            o.has("content") -> extractText(o.get("content"))
            else -> o.toString()
        }
    }
    el.isJsonArray -> el.asJsonArray.joinToString("\n") { extractText(it) }
    else -> el.toString()
}

private fun parseStr(json: String, key: String): String? =
    runCatching {
        val o = com.google.gson.JsonParser.parseString(json).asJsonObject
        if (o.has(key) && !o.get(key).isJsonNull) o.get(key).asString else null
    }.getOrNull()
