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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.api.BackgroundTask
import dev.kinetick.kinetic.api.ContextSnapshot
import dev.kinetick.kinetic.api.DelegationMember
import dev.kinetick.kinetic.api.DelegationSnapshot
import dev.kinetick.kinetic.api.ForkOptions
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.KnowledgeProposal
import dev.kinetick.kinetic.api.ModelEntry
import dev.kinetick.kinetic.api.QueueSnapshot
import dev.kinetick.kinetic.api.SessionInfo
import dev.kinetick.kinetic.api.SessionSkillPolicy
import dev.kinetick.kinetic.api.SessionUsage
import dev.kinetick.kinetic.api.SkillEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- subagents

internal data class SubagentLink(
    val sessionId: String,
    val title: String,
    val status: String,
    val detail: String?,
    val updatedAt: Long?,
)

private val ACTIVE_SUBAGENT = setOf("running", "queued", "started", "in_progress", "decision-blocked")

/**
 * One row per child session. Delegation members, parented sessions, and
 * background tasks that point at a session all collapse onto the same id so
 * the parent is the only door into that transcript.
 */
internal fun mergeSubagentLinks(
    parentSessionId: String,
    members: List<DelegationMember>,
    children: List<SessionInfo>,
    background: List<BackgroundTask> = emptyList(),
): List<SubagentLink> {
    val childrenById = children
        .filter { it.sessionId.isNotBlank() && it.sessionId != parentSessionId }
        .associateBy { it.sessionId }
    val seen = linkedSetOf<String>()
    val links = mutableListOf<SubagentLink>()
    for (member in members) {
        if (member.sessionId.isBlank() || member.sessionId == parentSessionId) continue
        if (!seen.add(member.sessionId)) continue
        links += subagentLink(member, childrenById[member.sessionId])
    }
    for (child in childrenById.values) {
        if (!seen.add(child.sessionId)) continue
        links += subagentLink(null, child)
    }
    for (task in background) {
        val id = task.sessionId?.takeIf { it.isNotBlank() && it != parentSessionId } ?: continue
        if (!seen.add(id)) continue
        links += SubagentLink(
            sessionId = id,
            title = task.label?.takeIf { it.isNotBlank() } ?: id,
            status = task.status?.takeIf { it.isNotBlank() } ?: "idle",
            detail = null,
            updatedAt = null,
        )
    }
    return links.sortedWith(
        compareByDescending<SubagentLink> { it.status.lowercase() in ACTIVE_SUBAGENT }
            .thenByDescending { it.updatedAt ?: 0L }
    )
}

private fun subagentLink(
    member: DelegationMember?,
    child: SessionInfo?,
): SubagentLink {
    val id = member?.sessionId ?: child!!.sessionId
    val title = member?.task?.takeIf { it.isNotBlank() }
        ?: child?.title?.takeIf { it.isNotBlank() }
        ?: member?.agentName?.takeIf { it.isNotBlank() }
        ?: id
    val status = member?.status?.takeIf { it.isNotBlank() && it != "unknown" }
        ?: child?.status?.takeIf { it.isNotBlank() }
        ?: member?.status?.takeIf { it.isNotBlank() }
        ?: "idle"
    val detail = listOfNotNull(
        member?.agentName ?: child?.agentName,
        child?.archived?.takeIf { it }?.let { "archived" },
        member?.errorMessage ?: child?.errorMessage,
    ).distinct().joinToString(" · ").ifBlank { null }
    val updated = child?.updatedAt ?: child?.createdAt ?: member?.updatedAtMs ?: member?.createdAtMs
    return SubagentLink(id, title, status, detail, updated)
}

@Composable
fun AgentsTab(
    client: KcodeClient,
    sessionId: String,
    onOpenSession: (String) -> Unit,
    onCount: (Int) -> Unit,
    onTreeStopped: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember(sessionId) { mutableStateOf<DelegationSnapshot?>(null) }
    var tasks by remember(sessionId) { mutableStateOf<List<BackgroundTask>>(emptyList()) }
    var children by remember(sessionId) { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching { withContext(Dispatchers.IO) { client.delegation(sessionId) } }
            .onSuccess { snapshot = it }
        runCatching { withContext(Dispatchers.IO) { client.backgroundTasks(sessionId) } }
            .onSuccess { tasks = it }
        runCatching { withContext(Dispatchers.IO) { childSessions(client, sessionId) } }
            .onSuccess { children = it }
    }

    LaunchedEffect(sessionId) { load() }

    val links = mergeSubagentLinks(
        parentSessionId = sessionId,
        members = snapshot?.members ?: emptyList(),
        children = children,
        background = tasks,
    )
    LaunchedEffect(links.size) { onCount(links.size) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subagents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { load() } }) { Text("Refresh") }
            TextButton(
                enabled = !busy && links.any { it.status.lowercase() in ACTIVE_SUBAGENT },
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { client.stopDelegation(sessionId) } }
                        busy = false
                        load()
                        onTreeStopped()
                    }
                }
            ) { Text("Stop tree") }
        }
        Text(
            "Delegated sessions open from here. They stay off the main session list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (links.isEmpty()) {
            Text(
                "No subagents on this session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        links.forEach { link ->
            SectionCard(onClick = { onOpenSession(link.sessionId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            link.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        link.detail?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(link.status)
                        relativeTime(link.updatedAt)?.let { stamp ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "›",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val looseTasks = tasks.filter { it.sessionId.isNullOrBlank() }
        if (looseTasks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Background tasks", style = MaterialTheme.typography.titleMedium)
            looseTasks.forEach { t ->
                ListItem(
                    headlineContent = { Text(t.label ?: t.id ?: t.taskId ?: "task", maxLines = 1) },
                    supportingContent = { Text(t.status ?: "", style = MaterialTheme.typography.labelSmall) },
                    leadingContent = { Text(statusGlyph(t.status ?: "")) },
                )
            }
        }
    }
}

/** Page the catalogue until the parent's children are collected, or a few pages pass. */
private suspend fun childSessions(client: KcodeClient, parentId: String): List<SessionInfo> {
    val found = mutableListOf<SessionInfo>()
    var cursor: String? = null
    val seenCursors = mutableSetOf<String>()
    repeat(4) {
        val page = client.listSessions(limit = 80, cursor = cursor, includeArchived = true)
        found += page.sessions.filter { it.parentSessionId == parentId }
        val next = page.nextCursor
        if (!page.hasMore || next.isNullOrBlank() || !seenCursors.add(next)) return found
        cursor = next
    }
    return found
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
    var skillPolicy by remember { mutableStateOf<SessionSkillPolicy?>(null) }
    var proposals by remember { mutableStateOf<List<KnowledgeProposal>>(emptyList()) }
    var proposalsSupported by remember { mutableStateOf(true) }
    var policySupported by remember { mutableStateOf(true) }
    var forkOptions by remember { mutableStateOf<ForkOptions?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // null = follow the selected model's provider; a set = the user's choice.
    var expandedProviders by remember(sessionId) { mutableStateOf<Set<String>?>(null) }
    var editingProposalId by remember { mutableStateOf<String?>(null) }
    var proposalDraftEdit by remember { mutableStateOf("") }

    suspend fun load() {
        runCatching { withContext(Dispatchers.IO) { client.usage(sessionId) } }.onSuccess { usage = it }
        runCatching { withContext(Dispatchers.IO) { client.context(sessionId) } }.onSuccess { context = it }
        runCatching { withContext(Dispatchers.IO) { client.models(sessionId) } }.onSuccess { models = it }
        runCatching { withContext(Dispatchers.IO) { client.skills(session?.workspaceDir) } }
            .onSuccess { skills = it.skills }
        runCatching { withContext(Dispatchers.IO) { client.getSession(sessionId) } }
            .onSuccess {
                skillPolicy = it.skillPolicy
                // Prefer the live session projection for policy badges.
            }
            .onFailure { /* keep last known policy */ }
        if (skillPolicy == null) skillPolicy = session?.skillPolicy
        // Match TUI `/skills review`: list all pending drafts, not only this session.
        // Idle drafts attach to root sessions; filtering by the open sessionId would hide them.
        runCatching {
            withContext(Dispatchers.IO) {
                client.knowledgeProposals(status = "pending", limit = 30)
            }
        }.onSuccess {
            proposals = it.proposals.sortedWith(
                compareByDescending<KnowledgeProposal> { it.sessionId == sessionId }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: 0L }
            )
            proposalsSupported = true
        }.onFailure { err ->
            val code = (err as? KcodeClient.KcodeException)?.code
            if (code == 404) {
                proposalsSupported = false
                proposals = emptyList()
            }
        }
        runCatching { withContext(Dispatchers.IO) { client.forkOptions(sessionId) } }
            .onSuccess { forkOptions = it }
    }

    fun setDisposition(skillName: String, disposition: String?) {
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.updateSkillPolicy(sessionId, dispositions = mapOf(skillName to disposition))
                }
            }.onSuccess {
                skillPolicy = it.skillPolicy
                policySupported = true
                message = when (disposition) {
                    "mandatory" -> "Required $skillName"
                    "optional" -> "Optional $skillName"
                    "forbidden" -> "Forbidden $skillName"
                    null -> "Cleared $skillName"
                    else -> "Updated $skillName"
                }
                onSessionChanged()
            }.onFailure { err ->
                val code = (err as? KcodeClient.KcodeException)?.code
                if (code == 404) {
                    policySupported = false
                    message = "Skill policy is not supported by this server"
                } else {
                    message = "Failed: ${err.message}"
                }
            }
            busy = false
            load()
        }
    }

    fun reviewProposal(proposal: KnowledgeProposal, decision: String, editedDraft: String? = null) {
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.reviewKnowledgeProposal(
                        proposalId = proposal.id,
                        decision = decision,
                        editedDraft = editedDraft,
                    )
                }
            }.onSuccess { result ->
                // Approve writes before recording; applied=false means the write did not land
                // and the draft stays pending for retry (kinetick-code #99).
                message = when {
                    decision == "approve" && result.applied ->
                        "Approved ${result.title.ifBlank { proposal.title }} and applied"
                    decision == "approve" ->
                        "Apply failed for ${proposal.title}; draft stays pending — retry or reject"
                    else -> "Rejected ${proposal.title}"
                }
                editingProposalId = null
                load()
            }.onFailure { err ->
                val code = (err as? KcodeClient.KcodeException)?.code
                if (code == 404) {
                    proposalsSupported = false
                    message = "Knowledge review is not supported by this server"
                } else if (decision == "approve") {
                    // Failed apply leaves the proposal pending server-side.
                    message = "Apply failed; draft stays pending — ${err.message}"
                    load()
                } else {
                    message = "Failed: ${err.message}"
                }
            }
            busy = false
        }
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

        // ---- knowledge review ----
        if (proposalsSupported || proposals.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Knowledge review",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${proposals.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Pending Skill/Memory drafts across sessions. Approve applies the write first; a failed apply stays pending so you can retry or reject.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (!proposalsSupported) {
                Text(
                    "This server does not expose knowledge proposals yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (proposals.isEmpty()) {
                Text(
                    "No pending Skill or Memory drafts.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            proposals.forEach { proposal ->
                val editing = editingProposalId == proposal.id
                val fromThisSession = proposal.sessionId == sessionId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                proposal.title.ifBlank { proposal.id },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${proposal.kind}/${proposal.action}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            buildString {
                                append(proposal.destinationLabel())
                                proposal.agentName?.takeIf { it.isNotBlank() }?.let {
                                    append(" · ").append(it)
                                }
                                when {
                                    fromThisSession -> append(" · this session")
                                    !proposal.sessionId.isNullOrBlank() ->
                                        append(" · session ").append(proposal.sessionId.take(8))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (proposal.summary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                proposal.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (editing) {
                            OutlinedTextField(
                                value = proposalDraftEdit,
                                onValueChange = { proposalDraftEdit = it },
                                label = { Text("Draft") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 12,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !busy && proposalDraftEdit.isNotBlank(),
                                    onClick = {
                                        reviewProposal(proposal, "approve", proposalDraftEdit)
                                    },
                                ) { Text("Approve edit") }
                                TextButton(enabled = !busy, onClick = { editingProposalId = null }) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            Text(
                                proposal.effectiveDraft.ifBlank { "(empty draft)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = Fonts.Code,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !busy,
                                    onClick = { reviewProposal(proposal, "approve") },
                                ) { Text("Approve") }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        editingProposalId = proposal.id
                                        proposalDraftEdit = proposal.effectiveDraft
                                    },
                                ) { Text("Edit") }
                                OutlinedButton(
                                    enabled = !busy,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    onClick = { reviewProposal(proposal, "reject") },
                                ) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }

        // ---- skills + policy ----
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Skills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${skills.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        skillPolicy?.let { policy ->
            Text(
                buildString {
                    append(if (policy.closed) "Closed catalog" else "Open catalog")
                    if (policy.mandatory.isNotEmpty()) append(" · require ${policy.mandatory.joinToString()}")
                    if (policy.forbidden.isNotEmpty()) append(" · forbid ${policy.forbidden.joinToString()}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && policySupported,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.updateSkillPolicy(sessionId, closed = !policy.closed)
                                }
                            }.onSuccess {
                                skillPolicy = it.skillPolicy
                                message = if (it.skillPolicy?.closed == true) "Catalog closed" else "Catalog opened"
                                onSessionChanged()
                            }.onFailure { err ->
                                val code = (err as? KcodeClient.KcodeException)?.code
                                if (code == 404) {
                                    policySupported = false
                                    message = "Skill policy is not supported by this server"
                                } else {
                                    message = "Failed: ${err.message}"
                                }
                            }
                            busy = false
                            load()
                        }
                    },
                ) { Text(if (policy.closed) "Open catalog" else "Close catalog") }
            }
        } ?: if (!policySupported) {
            Text(
                "Skill policy updates are not supported by this server.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Tap a skill to set require / optional / forbid for this session.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (skills.isEmpty()) {
            Text("No skills reported for this workspace.", style = MaterialTheme.typography.bodySmall)
        }
        skills.take(60).forEach { s ->
            var open by remember(s.name) { mutableStateOf(false) }
            val disposition = skillPolicy?.dispositionFor(s.name)
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
                            fontFamily = Fonts.Code,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        disposition?.takeIf { it != "hidden" }?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (it) {
                                    "mandatory" -> MaterialTheme.colorScheme.primary
                                    "forbidden" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
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
                        if (policySupported) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val current = disposition
                                listOf(
                                    "mandatory" to "Require",
                                    "optional" to "Optional",
                                    "forbidden" to "Forbid",
                                ).forEach { (value, label) ->
                                    if (current == value) {
                                        Button(
                                            enabled = !busy,
                                            onClick = { setDisposition(s.name, null) },
                                        ) { Text(label) }
                                    } else {
                                        OutlinedButton(
                                            enabled = !busy,
                                            onClick = { setDisposition(s.name, value) },
                                        ) { Text(label) }
                                    }
                                }
                                if (current != null) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { setDisposition(s.name, null) },
                                    ) { Text("Clear") }
                                }
                            }
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
