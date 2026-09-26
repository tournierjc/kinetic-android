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
import androidx.compose.ui.text.style.TextOverflow
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
    onSessionDeleted: () -> Unit,
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
    var busy by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // null = follow the selected model's provider; a set = the user's choice.
    var expandedProviders by remember(sessionId) { mutableStateOf<Set<String>?>(null) }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                session?.title ?: "Session",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { load() } }) { Text("Refresh") }
        }
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

        // ---- model ---- grouped per provider, expandable
        Spacer(Modifier.height(12.dp))
        Text("Model", style = MaterialTheme.typography.titleMedium)
        if (models.isEmpty()) {
            Text("No model roster for this session.", style = MaterialTheme.typography.bodySmall)
        }
        val expanded = expandedProviders ?: setOfNotNull(models.firstOrNull { it.selected == true }?.providerId)
        models.groupBy { it.providerId }.forEach { (providerId, entries) ->
            val isOpen = providerId in expanded
            val current = entries.firstOrNull { it.selected == true }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable {
                        expandedProviders = if (isOpen) expanded - providerId else expanded + providerId
                    },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isOpen) "▾" else "▸", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entries.firstNotNullOfOrNull { it.providerName } ?: providerId,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            current?.let { it.displayName ?: it.modelId }
                                ?: "${entries.size} model${if (entries.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (isOpen) {
                entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = m.selected != true && !busy) {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            client.selectModel(sessionId, m.providerId, m.modelId, m.variant)
                                        }
                                    }.onSuccess { message = "Model set to ${m.displayName ?: m.modelId}" }
                                        .onFailure { message = "Failed: ${it.message}" }
                                    busy = false
                                    load()
                                    onSessionChanged()
                                }
                            }
                            .padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = m.selected == true, onClick = null)
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName ?: m.modelId, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    m.modelId,
                                    m.contextLimit?.let { "ctx $it" },
                                    m.variant,
                                    m.enabled?.takeIf { !it }?.let { "disabled" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                    label = { Text("Title") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !busy && titleDraft.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { client.renameSession(sessionId, titleDraft) } }
                                .onSuccess { message = "Renamed" }
                                .onFailure { message = "Failed: ${it.message}" }
                            busy = false
                            renaming = false
                            onSessionChanged()
                            load()
                        }
                    },
                ) { Text("Save") }
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        titleDraft = session?.title ?: ""
                        renaming = true
                    },
                ) { Text("Rename") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val pinned = session?.pinned != true
                            runCatching { withContext(Dispatchers.IO) { client.setPinned(sessionId, pinned) } }
                                .onSuccess { message = if (pinned) "Pinned" else "Unpinned" }
                                .onFailure { message = "Failed: ${it.message}" }
                            busy = false
                            onSessionChanged()
                        }
                    },
                ) { Text(if (session?.pinned == true) "Unpin" else "Pin") }
                OutlinedButton(
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        busy = true
                        scope.launch {
                            val archived = session?.archived != true
                            runCatching { withContext(Dispatchers.IO) { client.setArchived(sessionId, archived) } }
                                .onSuccess { message = if (archived) "Archived" else "Unarchived" }
                                .onFailure { message = "Failed: ${it.message}" }
                            busy = false
                            onSessionChanged()
                        }
                    },
                ) { Text(if (session?.archived == true) "Unarchive" else "Archive") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = forkOptions?.canFork == true && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { client.fork(sessionId, forkOptions?.suggestedTitle) }
                            }.onSuccess { body ->
                                val newId = dev.kinetick.kinetic.api.Wire.obj(body)
                                    ?.get("sessionId")?.takeIf { it.isJsonPrimitive }?.asString
                                if (newId != null) onForked(newId) else message = "Forked"
                            }.onFailure { message = "Fork failed: ${it.message}" }
                            busy = false
                        }
                    },
                ) { Text("Fork") }
                OutlinedButton(
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = { confirmingDelete = true },
                ) { Text("Delete") }
            }
            forkOptions?.takeIf { !it.canFork }?.unavailableReason?.let {
                Text(
                    "Fork unavailable: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- skills ----
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Skills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${skills.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Tap a skill to read its description in full.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (skills.isEmpty()) {
            Text("No skills reported for this workspace.", style = MaterialTheme.typography.bodySmall)
        }
        skills.take(60).forEach { s ->
            var open by remember(s.name) { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { open = !open },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        s.source?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        s.summary.ifBlank { "No description provided." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (open) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (open) {
                        s.description?.takeIf { it.isNotBlank() && it != s.summary }?.let {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this session?") },
            text = {
                Text(
                    "\"${session?.title ?: sessionId}\" and its transcript are removed from " +
                        "the server. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.deleteSession(sessionId) } }
                            .onSuccess { onSessionDeleted() }
                            .onFailure { message = "Failed: ${it.message}" }
                        busy = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
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
