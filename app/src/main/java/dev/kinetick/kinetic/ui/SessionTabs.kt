@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.api.BackgroundTask
import dev.kinetick.kinetic.api.ContextSnapshot
import dev.kinetick.kinetic.api.DelegationSnapshot
import dev.kinetick.kinetic.api.ForkOptions
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.ModelEntry
import dev.kinetick.kinetic.api.QueueSnapshot
import dev.kinetick.kinetic.api.SessionInfo
import dev.kinetick.kinetic.api.SessionUsage
import dev.kinetick.kinetic.api.SkillEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- subagents

@Composable
fun AgentsTab(client: KcodeClient, sessionId: String, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DelegationSnapshot?>(null) }
    var tasks by remember { mutableStateOf<List<BackgroundTask>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching { withContext(Dispatchers.IO) { client.delegation(sessionId) } }
            .onSuccess { snapshot = it }
        runCatching { withContext(Dispatchers.IO) { client.backgroundTasks(sessionId) } }
            .onSuccess { tasks = it }
    }

    LaunchedEffect(sessionId) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subagents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { load() } }) { Text("Refresh") }
            TextButton(
                enabled = !busy && snapshot?.members?.any { it.status == "running" || it.status == "queued" } == true,
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.stopDelegation(sessionId) } }
                        busy = false
                        load()
                        onOpen()
                    }
                }
            ) { Text("Stop tree") }
        }

        val members = snapshot?.members ?: emptyList()
        if (members.isEmpty()) {
            Text(
                "No delegated agents on this session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        members.forEach { member ->
            ListItem(
                headlineContent = { Text(member.task ?: member.sessionId, maxLines = 2) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            member.agentName,
                            member.status,
                            member.errorMessage,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingContent = { Text(statusGlyph(member.status)) },
            )
            HorizontalDivider()
        }

        Spacer(Modifier.height(16.dp))
        Text("Background tasks", style = MaterialTheme.typography.titleMedium)
        if (tasks.isEmpty()) {
            Text(
                "None.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        tasks.forEach { t ->
            ListItem(
                headlineContent = { Text(t.label ?: t.id ?: t.taskId ?: "task", maxLines = 1) },
                supportingContent = { Text(t.status ?: "", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun statusGlyph(status: String): String = when (status) {
    "running" -> "▶"
    "queued" -> "…"
    "completed" -> "✓"
    "failed" -> "✕"
    "stopped" -> "⏹"
    else -> "•"
}

// ------------------------------------------------------------------- queue

@Composable
fun QueueTab(client: KcodeClient, sessionId: String) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<QueueSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        runCatching { withContext(Dispatchers.IO) { client.queue(sessionId) } }
            .onSuccess { queue = it }
            .onFailure { error = it.message }
    }

    LaunchedEffect(sessionId) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Queue" + (queue?.let { " (${it.pendingCount})" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { scope.launch { load() } }) { Text("Refresh") }
            if (queue?.paused == true) {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.queueContinue(sessionId) } }
                        load()
                    }
                }) { Text("Continue") }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        queue?.items?.forEach { item ->
            ListItem(
                headlineContent = { Text(item.content ?: item.id, maxLines = 3) },
                supportingContent = { Text(item.status ?: "queued", style = MaterialTheme.typography.labelSmall) },
                trailingContent = {
                    Row {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { client.queueSteer(sessionId, item.id) } }
                                load()
                            }
                        }) { Text("Steer") }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { client.queueDelete(sessionId, item.id) } }
                                load()
                            }
                        }) { Text("Drop") }
                    }
                },
            )
            HorizontalDivider()
        }
        if (queue?.items.isNullOrEmpty()) {
            Text(
                "Nothing queued.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------------- info

@Composable
fun InfoTab(
    client: KcodeClient,
    sessionId: String,
    session: SessionInfo?,
    onSessionChanged: () -> Unit,
    onForked: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<SessionUsage?>(null) }
    var context by remember { mutableStateOf<ContextSnapshot?>(null) }
    var models by remember { mutableStateOf<List<ModelEntry>>(emptyList()) }
    var skills by remember { mutableStateOf<List<SkillEntry>>(emptyList()) }
    var forkOptions by remember { mutableStateOf<ForkOptions?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }

    suspend fun load() {
        runCatching { withContext(Dispatchers.IO) { client.usage(sessionId) } }.onSuccess { usage = it }
        runCatching { withContext(Dispatchers.IO) { client.context(sessionId) } }.onSuccess { context = it }
        runCatching { withContext(Dispatchers.IO) { client.models(sessionId) } }.onSuccess { models = it }
        runCatching { withContext(Dispatchers.IO) { client.skills(session?.workspaceDir) } }
            .onSuccess { skills = it.skills }
        runCatching { withContext(Dispatchers.IO) { client.forkOptions(sessionId) } }
            .onSuccess { forkOptions = it }
    }

    LaunchedEffect(sessionId) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        // ---- usage ----
        Text("Usage", style = MaterialTheme.typography.titleMedium)
        usage?.let { u ->
            InfoRow("turns", u.summary.turns.toString())
            InfoRow("input tokens", u.summary.inputTokens.toString())
            InfoRow("output tokens", u.summary.outputTokens.toString())
            InfoRow("reasoning", u.summary.reasoningTokens.toString())
            InfoRow("cache read", u.summary.cacheReadTokens.toString())
            InfoRow("total", u.summary.totalTokens.toString())
            InfoRow("cost", "$%.4f".format(u.summary.costUsd))
        } ?: Text("—", style = MaterialTheme.typography.bodySmall)

        // ---- context ----
        Spacer(Modifier.height(12.dp))
        Text("Context", style = MaterialTheme.typography.titleMedium)
        context?.let { c ->
            InfoRow("status", c.status ?: "—")
            InfoRow("model", listOfNotNull(c.model?.provider, c.model?.id).joinToString("/"))
            InfoRow("window", c.model?.contextWindow?.toString() ?: "—")
            val used = c.usedTokens
            val total = c.model?.contextWindow
            if (used != null && total != null && total > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$used / $total tokens", style = MaterialTheme.typography.labelSmall)
            }
        } ?: Text("—", style = MaterialTheme.typography.bodySmall)

        // ---- model ----
        Spacer(Modifier.height(12.dp))
        Text("Model", style = MaterialTheme.typography.titleMedium)
        if (models.isEmpty()) {
            Text("No model roster for this session.", style = MaterialTheme.typography.bodySmall)
        }
        models.take(30).forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = m.selected != true) {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.selectModel(sessionId, m.providerId, m.modelId, m.variant)
                                }
                            }.onSuccess { message = "Model set to ${m.displayName ?: m.modelId}" }
                                .onFailure { message = "Failed: ${it.message}" }
                            load()
                            onSessionChanged()
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = m.selected == true, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayName ?: m.modelId, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            "${m.providerId}/${m.modelId}",
                            m.contextLimit?.let { "ctx $it" },
                            m.variant,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- session actions ----
        Spacer(Modifier.height(12.dp))
        Text("Session", style = MaterialTheme.typography.titleMedium)
        if (renaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.renameSession(sessionId, titleDraft) } }
                            .onSuccess { message = "Renamed" }
                            .onFailure { message = "Failed: ${it.message}" }
                        renaming = false
                        onSessionChanged()
                        load()
                    }
                }) { Text("Save") }
            }
        } else {
            Row {
                TextButton(onClick = {
                    titleDraft = session?.title ?: ""
                    renaming = true
                }) { Text("Rename") }
                TextButton(onClick = {
                    scope.launch {
                        val pinned = session?.pinned != true
                        runCatching { withContext(Dispatchers.IO) { client.setPinned(sessionId, pinned) } }
                            .onSuccess { message = if (pinned) "Pinned" else "Unpinned" }
                            .onFailure { message = "Failed: ${it.message}" }
                        onSessionChanged()
                    }
                }) { Text(if (session?.pinned == true) "Unpin" else "Pin") }
                TextButton(onClick = {
                    scope.launch {
                        val archived = session?.archived != true
                        runCatching { withContext(Dispatchers.IO) { client.setArchived(sessionId, archived) } }
                            .onSuccess { message = if (archived) "Archived" else "Unarchived" }
                            .onFailure { message = "Failed: ${it.message}" }
                        onSessionChanged()
                    }
                }) { Text(if (session?.archived == true) "Unarchive" else "Archive") }
            }
            Row {
                TextButton(
                    enabled = forkOptions?.canFork == true,
                    onClick = {
                        scope.launch {
                            val res = runCatching {
                                withContext(Dispatchers.IO) { client.fork(sessionId, forkOptions?.suggestedTitle) }
                            }
                            res.onSuccess { body ->
                                val newId = dev.kinetick.kinetic.api.Wire.obj(body)
                                    ?.get("sessionId")?.takeIf { it.isJsonPrimitive }?.asString
                                if (newId != null) onForked(newId) else message = "Forked"
                            }.onFailure { message = "Fork failed: ${it.message}" }
                        }
                    }
                ) { Text("Fork") }
                TextButton(onClick = { scope.launch { load() } }) { Text("Refresh info") }
            }
            forkOptions?.takeIf { !it.canFork }?.unavailableReason?.let {
                Text(
                    "Fork unavailable: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- skills ----
        Spacer(Modifier.height(12.dp))
        Text("Skills (${skills.size})", style = MaterialTheme.typography.titleMedium)
        skills.take(40).forEach { s ->
            var open by remember(s.name) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp)) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(
                    if (open) s.summary else s.summary.take(90) + if (s.summary.length > 90) "…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
