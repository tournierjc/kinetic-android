package dev.kinetick.kinetic.api

import com.google.gson.JsonElement

/**
 * Wire models mirroring the kcode server contract
 * (packages/tui/src/runtime/stream-events.ts and types/runtime-models.ts).
 */

// ---- messages ----

data class ChatMessage(
    val id: String? = null,
    val turnId: String? = null,
    val role: String = "unknown",
    val kind: String? = null,
    val content: String = "",
    val thinking: String? = null,
    val thinkingDurationMs: Long? = null,
    val error: String? = null,
    val timestamp: Long? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val parts: List<MessagePart> = emptyList(),
    val usage: TokenUsage? = null,
    val actions: MessageActions? = null,
    val attachments: List<Attachment> = emptyList(),
    val finishReason: String? = null,
    /** Client-only: this message is still being streamed. */
    val streaming: Boolean = false,
)

data class MessageActions(val fork: Boolean? = null, val rewind: Boolean? = null)

sealed interface MessagePart {
    data class Thinking(val content: String, val durationMs: Long?) : MessagePart
    data class Text(val content: String) : MessagePart
    data class Tool(val toolCall: ToolCall) : MessagePart
}

data class ToolCall(
    val id: String? = null,
    val name: String = "tool",
    val status: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val error: JsonElement? = null,
    val durationMs: Long? = null,
    val preview: StructuredPreview? = null,
)

data class TokenUsage(
    val totalTokens: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val requestDurationMs: Long? = null,
)

data class Attachment(
    val type: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val filePath: String? = null,
)

// ---- structured previews (diff / file / summary) ----

data class StructuredPreview(
    val state: String? = null,
    val blocks: List<PreviewBlock> = emptyList(),
)

sealed interface PreviewBlock {
    data class Diff(
        val path: String?,
        val diff: String,
        val addedLines: Int,
        val removedLines: Int,
        val truncated: Boolean,
        val omittedLines: Int?,
    ) : PreviewBlock

    data class File(
        val path: String?,
        val content: String,
        val lineCount: Int,
        val truncated: Boolean,
        val omittedLines: Int?,
    ) : PreviewBlock

    data class Summary(
        val path: String?,
        val message: String,
        val reason: String?,
        val byteCount: Long?,
    ) : PreviewBlock
}

// ---- user input ----

data class PermissionRequest(
    val requestId: String,
    val agentName: String? = null,
    val sessionId: String? = null,
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolDescription: String? = null,
    val reason: String? = null,
    val ruleContents: List<String> = emptyList(),
    val allowAlwaysSupported: Boolean = false,
    val preview: StructuredPreview? = null,
)

data class Questionnaire(
    val id: String,
    val title: String? = null,
    val purpose: String? = null,
    val steps: List<QuestionnaireStep> = emptyList(),
)

data class QuestionnaireStep(
    val id: String,
    val header: String? = null,
    val question: String,
    val description: String? = null,
    val selectionMode: String = "single",
    val options: List<QuestionnaireOption> = emptyList(),
    val allowOther: Boolean = false,
    val otherPlaceholder: String? = null,
    val required: Boolean = false,
) {
    val isMultiple: Boolean get() = selectionMode == "multiple"
}

data class QuestionnaireOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val recommended: Boolean = false,
)

/** One answer in a questionnaire reply. */
data class QuestionnaireAnswer(
    val stepId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val selectedOther: Boolean = false,
    val otherText: String? = null,
    val skipped: Boolean = false,
)

// ---- interactions ----

data class Interactions(
    val sessionId: String? = null,
    val questionnaire: Questionnaire? = null,
    val planReview: JsonElement? = null,
    val permissions: List<PermissionRequest> = emptyList(),
    val activeRun: ActiveRun? = null,
)

data class ActiveRun(
    val state: String? = null,
    val actions: Map<String, Boolean> = emptyMap(),
) {
    val running: Boolean get() = state == "running" || state == "decision-blocked"
}

// ---- catalogue ----

data class SessionInfo(
    val sessionId: String,
    val agentName: String? = null,
    val title: String? = null,
    val workspaceDir: String? = null,
    val sessionKind: String? = null,
    val visibility: String? = null,
    val archived: Boolean? = null,
    val pinned: Boolean? = null,
    val parentSessionId: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val model: SessionModel? = null,
    val errorMessage: String? = null,
    /** Per-session Skill dispositions from kinetick-code #99. */
    val skillPolicy: SessionSkillPolicy? = null,
) {
    /**
     * Delegated work stays out of the main session list. The server forces
     * `sessionKind = task` rows to `visible`, and every child carries
     * `parentSessionId`, so a flat `/sessions` page would otherwise show them
     * next to real conversations. User forks are roots and have no parent.
     */
    fun isSubagentSession(): Boolean =
        !parentSessionId.isNullOrBlank() || sessionKind.equals("task", ignoreCase = true)
}

data class SessionModel(val providerId: String? = null, val modelId: String? = null, val variant: String? = null)

data class SessionPage(val sessions: List<SessionInfo> = emptyList(), val hasMore: Boolean = false, val nextCursor: String? = null)

data class MessagePage(val messages: List<ChatMessage> = emptyList(), val hasMore: Boolean = false, val nextCursor: String? = null)

data class ModelEntry(
    val providerId: String,
    val modelId: String,
    val modelConfigId: String? = null,
    val displayName: String? = null,
    val enabled: Boolean? = null,
    val selected: Boolean? = null,
    val providerName: String? = null,
    val contextLimit: Long? = null,
    val variant: String? = null,
)

data class SkillEntry(
    val name: String,
    val description: String? = null,
    val displayDescription: String? = null,
    val source: String? = null,
    val enabled: Boolean? = null,
) {
    val summary: String get() = displayDescription ?: description ?: ""
}

data class SkillPage(val skills: List<SkillEntry> = emptyList(), val hasMore: Boolean = false)

/**
 * Session Skill policy projected by the Runtime (kinetick-code #99).
 * Unlisted Skills stay optional unless [closed] is true.
 */
data class SessionSkillPolicy(
    val closed: Boolean = false,
    val mandatory: List<String> = emptyList(),
    val optional: List<String> = emptyList(),
    val forbidden: List<String> = emptyList(),
) {
    fun dispositionFor(name: String): String? {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return null
        if (mandatory.any { it.equals(key, ignoreCase = true) }) return "mandatory"
        if (forbidden.any { it.equals(key, ignoreCase = true) }) return "forbidden"
        if (optional.any { it.equals(key, ignoreCase = true) }) return "optional"
        return if (closed) "hidden" else null
    }
}

/** Idle Skill/Memory draft awaiting human approve/reject. */
data class KnowledgeProposal(
    val id: String,
    val kind: String,
    val action: String,
    val status: String,
    val title: String,
    val summary: String = "",
    val draft: String = "",
    val editedDraft: String? = null,
    val rationale: String? = null,
    val sessionId: String? = null,
    val agentName: String? = null,
    /**
     * Memory destination. Omitted/null means agent Memory (idle drafts do this);
     * `"user"` selects user Memory. Unused for Skill proposals.
     */
    val targetRef: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val effectiveDraft: String get() = editedDraft ?: draft

    /** Short destination label for the review UI. */
    fun destinationLabel(): String = when {
        kind.equals("memory", ignoreCase = true) &&
            targetRef.equals("user", ignoreCase = true) -> "user memory"
        kind.equals("memory", ignoreCase = true) -> "agent memory"
        else -> "skill"
    }
}

data class KnowledgeProposalPage(
    val proposals: List<KnowledgeProposal> = emptyList(),
)

data class KnowledgeReviewResult(
    val applied: Boolean = false,
    val title: String = "",
    val status: String = "",
)

// ---- delegation / background / queue ----

data class DelegationMember(
    val sessionId: String,
    val parentSessionId: String? = null,
    val agentName: String? = null,
    val task: String? = null,
    val status: String = "unknown",
    val backgroundTaskId: String? = null,
    val errorMessage: String? = null,
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null,
)

data class DelegationSnapshot(
    val rootSessionId: String? = null,
    val members: List<DelegationMember> = emptyList(),
)

data class BackgroundTask(
    val id: String? = null,
    val taskId: String? = null,
    val label: String? = null,
    val status: String? = null,
    val sessionId: String? = null,
)

data class QueueItem(val id: String, val content: String? = null, val status: String? = null)

data class QueueSnapshot(
    val items: List<QueueItem> = emptyList(),
    val paused: Boolean = false,
    val pendingCount: Int = 0,
)

/**
 * Ack of `POST /queue/enqueue`. The runtime answers `{itemId, status, position}`
 * and, on an idle session, drains the message straight into the conversation —
 * so the queue snapshot can stay empty while this ack is the real confirmation.
 */
data class QueuedMessage(
    val itemId: String? = null,
    val status: String? = null,
    val position: Int? = null,
)

// ---- usage / context ----

data class UsageSummary(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val costUsd: Double = 0.0,
    val turns: Int = 0,
    val totalTokens: Long = 0,
)

data class UsageRow(
    val providerId: String? = null,
    val modelId: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val costUsd: Double = 0.0,
    val turns: Int = 0,
)

data class SessionUsage(val summary: UsageSummary = UsageSummary(), val rows: List<UsageRow> = emptyList())

data class ContextModel(
    val provider: String? = null,
    val id: String? = null,
    val contextWindow: Long? = null,
)

data class ContextSnapshot(
    val status: String? = null,
    val model: ContextModel? = null,
    val usedTokens: Long? = null,
    val totalTokens: Long? = null,
)

// ---- fork / rewind ----

data class ForkOptions(
    val canFork: Boolean = false,
    val unavailableReason: String? = null,
    val suggestedTitle: String? = null,
    val nextForkOrdinal: Int? = null,
)

data class RewindPreview(
    val canRewind: Boolean = false,
    val files: List<String> = emptyList(),
    val diff: String? = null,
    val unavailableReason: String? = null,
)

// ---- stream events ----

sealed interface StreamEvent {
    data class Heartbeat(val turnId: String?) : StreamEvent
    data class Done(val turnId: String?) : StreamEvent
    data class Failed(val message: String, val turnId: String?) : StreamEvent
    data class MessageUpsert(val message: ChatMessage) : StreamEvent
    data class SessionStatus(val status: String, val message: String?, val turnId: String?) : StreamEvent
    data class Delta(
        val messageId: String?,
        val turnId: String?,
        val role: String?,
        val content: String?,
        val thinking: String?,
        val chunkIndex: Int?,
        val started: Boolean,
        val finish: Boolean,
    ) : StreamEvent

    data class MessagesReplaced(val messages: List<ChatMessage>) : StreamEvent
    data class MessagesRewound(val messageIds: List<String>) : StreamEvent
    data class ResyncRequired(val turnId: String?) : StreamEvent
    data class Generic(val eventType: String, val data: JsonElement?) : StreamEvent
    data class Unknown(val raw: String) : StreamEvent
}
