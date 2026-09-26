package dev.kinetick.kinetic.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Decodes the server's JSON into the wire models.
 *
 * `session-status` carries `message` as a *string* while `message` events carry
 * it as an *object*, so the stream is dispatched on `type` before field reads.
 */
object Wire {

    fun root(text: String): JsonElement? = runCatching { JsonParser.parseString(text) }.getOrNull()

    fun obj(text: String): JsonObject? = root(text)?.takeIf { it.isJsonObject }?.asJsonObject

    fun array(text: String): JsonArray? = root(text)?.takeIf { it.isJsonArray }?.asJsonArray

    // ---- field readers ----

    private fun JsonObject.s(k: String): String? {
        val v = get(k) ?: return null
        return if (v.isJsonNull) null else if (v.isJsonPrimitive) v.asString else null
    }

    private fun JsonObject.l(k: String): Long? {
        val v = get(k) ?: return null
        return if (v.isJsonPrimitive) runCatching { v.asLong }.getOrNull() else null
    }

    private fun JsonObject.i(k: String): Int? = l(k)?.toInt()

    private fun JsonObject.d(k: String): Double? {
        val v = get(k) ?: return null
        return if (v.isJsonPrimitive) runCatching { v.asDouble }.getOrNull() else null
    }

    private fun JsonObject.b(k: String): Boolean? {
        val v = get(k) ?: return null
        return if (v.isJsonPrimitive) runCatching { v.asBoolean }.getOrNull() else null
    }

    private fun JsonObject.o(k: String): JsonObject? = get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.a(k: String): JsonArray? = get(k)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.objs(k: String): List<JsonObject> = a(k).objs()

    /** The JSON objects inside an array, skipping anything else. */
    private fun JsonArray?.objs(): List<JsonObject> =
        this?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList()

    private fun JsonArray?.strings(): List<String> =
        this?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()

    private fun JsonObject.strList(k: String): List<String> = a(k).strings()

    /** A top-level payload that is either the array itself or an object wrapping it. */
    private fun payload(text: String, vararg keys: String): JsonArray? {
        val el = root(text) ?: return null
        if (el.isJsonArray) return el.asJsonArray
        if (!el.isJsonObject) return null
        val o = el.asJsonObject
        for (k in keys) o.a(k)?.let { return it }
        return null
    }

    // ---- messages ----

    fun message(o: JsonObject): ChatMessage = ChatMessage(
        id = o.s("id"),
        turnId = o.s("turnId"),
        role = o.s("role") ?: "unknown",
        kind = o.s("kind"),
        content = o.s("content") ?: "",
        thinking = o.s("thinking"),
        thinkingDurationMs = o.l("thinkingDurationMs"),
        error = o.s("error"),
        timestamp = o.l("timestamp"),
        toolCalls = o.objs("toolCalls").map { toolCall(it) },
        parts = o.objs("parts").map { part(it) },
        usage = o.o("usage")?.let { usage(it) },
        actions = o.o("actions")?.let { MessageActions(it.b("fork"), it.b("rewind")) },
        attachments = o.objs("attachments").map { attachment(it) },
        finishReason = o.s("finishReason"),
    )

    fun messages(el: JsonElement?): List<ChatMessage> =
        el?.takeIf { it.isJsonArray }?.asJsonArray.objs().map { message(it) }

    fun messagePage(text: String): MessagePage {
        val o = obj(text)
        return MessagePage(
            messages = messages(o?.get("messages")),
            hasMore = o?.b("hasMore") ?: false,
            nextCursor = o?.s("nextCursor"),
        )
    }

    private fun part(o: JsonObject): MessagePart = when (o.s("type")) {
        "thinking" -> MessagePart.Thinking(o.s("content") ?: "", o.l("durationMs"))
        "tool" -> MessagePart.Tool(o.o("toolCall")?.let { toolCall(it) } ?: ToolCall())
        else -> MessagePart.Text(o.s("content") ?: "")
    }

    private fun toolCall(o: JsonObject): ToolCall = ToolCall(
        id = o.s("id"),
        name = o.s("name") ?: "tool",
        status = o.s("status"),
        input = o.get("input"),
        output = o.get("output"),
        error = o.get("error"),
        durationMs = o.l("durationMs"),
        preview = o.o("structuredPreview")?.let { preview(it) },
    )

    private fun attachment(o: JsonObject) = Attachment(
        type = o.s("type"),
        fileName = o.s("fileName"),
        mimeType = o.s("mimeType"),
        sizeBytes = o.l("sizeBytes"),
        filePath = o.s("filePath"),
    )

    private fun usage(o: JsonObject) = TokenUsage(
        totalTokens = o.l("totalTokens"),
        inputTokens = o.l("inputTokens"),
        outputTokens = o.l("outputTokens"),
        reasoningTokens = o.l("reasoningTokens"),
        cacheReadTokens = o.l("cacheReadTokens"),
        cacheWriteTokens = o.l("cacheWriteTokens"),
        requestDurationMs = o.l("requestDurationMs"),
    )

    // ---- structured preview ----

    fun preview(o: JsonObject): StructuredPreview {
        val blocks = o.objs("blocks").mapNotNull { b ->
            when (b.s("kind")) {
                "diff" -> PreviewBlock.Diff(
                    path = b.s("path"),
                    diff = b.s("diff") ?: "",
                    addedLines = b.i("addedLines") ?: 0,
                    removedLines = b.i("removedLines") ?: 0,
                    truncated = b.b("truncated") ?: false,
                    omittedLines = b.i("omittedLines"),
                )
                "file" -> PreviewBlock.File(
                    path = b.s("path"),
                    content = b.s("content") ?: "",
                    lineCount = b.i("lineCount") ?: 0,
                    truncated = b.b("truncated") ?: false,
                    omittedLines = b.i("omittedLines"),
                )
                "summary" -> PreviewBlock.Summary(
                    path = b.s("path"),
                    message = b.s("message") ?: "",
                    reason = b.s("reason"),
                    byteCount = b.l("byteCount"),
                )
                else -> null
            }
        }
        return StructuredPreview(state = o.s("state"), blocks = blocks)
    }

    // ---- permissions ----

    fun permission(o: JsonObject): PermissionRequest? {
        val id = o.s("requestId") ?: return null
        return PermissionRequest(
            requestId = id,
            agentName = o.s("agentName"),
            sessionId = o.s("sessionId"),
            toolName = o.s("toolName"),
            toolInput = o.s("toolInput"),
            toolDescription = o.s("toolDescription"),
            reason = o.s("reason"),
            ruleContents = o.strList("ruleContents"),
            allowAlwaysSupported = o.b("allowAlwaysSupported") ?: false,
            preview = o.o("structuredPreview")?.let { preview(it) },
        )
    }

    fun permissions(text: String): List<PermissionRequest> =
        (payload(text, "permissions", "requests")).objs().mapNotNull { permission(it) }

    // ---- questionnaire ----

    fun questionnaire(el: JsonElement?): Questionnaire? {
        val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = o.s("id") ?: return null
        return Questionnaire(
            id = id,
            title = o.s("title"),
            purpose = o.s("purpose"),
            steps = o.objs("steps").map { step(it) },
        )
    }

    private fun step(o: JsonObject): QuestionnaireStep {
        val mode = o.get("selectionMode")
        // The TUI treats 1 / "multiple" as multi-select and 0 / "single" as one choice.
        val modeStr = when {
            mode == null || mode.isJsonNull -> "single"
            mode.isJsonPrimitive && mode.asJsonPrimitive.isNumber ->
                if (mode.asInt == 1) "multiple" else "single"
            mode.isJsonPrimitive -> mode.asString
            else -> "single"
        }
        return QuestionnaireStep(
            id = o.s("id") ?: "",
            header = o.s("header"),
            question = o.s("question") ?: "",
            description = o.s("description"),
            selectionMode = modeStr,
            options = o.objs("options").map {
                QuestionnaireOption(
                    id = it.s("id") ?: "",
                    label = it.s("label") ?: "",
                    description = it.s("description"),
                    recommended = it.b("recommended") ?: false,
                )
            },
            allowOther = o.b("allowOther") ?: false,
            otherPlaceholder = o.s("otherPlaceholder"),
            required = o.b("required") ?: false,
        )
    }

    // ---- interactions ----

    fun interactions(text: String): Interactions {
        val o = obj(text) ?: return Interactions()
        return Interactions(
            sessionId = o.s("sessionId"),
            questionnaire = questionnaire(o.get("questionnaire")),
            planReview = o.get("planReview"),
            permissions = o.a("permissions").objs().mapNotNull { permission(it) },
            activeRun = o.o("activeRun")?.let { run ->
                val actions = run.o("actions")?.entrySet()
                    ?.mapNotNull { (k, v) -> if (v.isJsonPrimitive) k to v.asBoolean else null }
                    ?.toMap() ?: emptyMap()
                ActiveRun(state = run.s("state"), actions = actions)
            },
        )
    }

    // ---- catalogue ----

    fun sessionPage(text: String): SessionPage {
        val o = obj(text) ?: return SessionPage()
        return SessionPage(
            sessions = o.objs("sessions").map { session(it) },
            hasMore = o.b("hasMore") ?: false,
            nextCursor = o.s("nextCursor"),
        )
    }

    fun session(text: String): SessionInfo? = obj(text)?.let { session(it) }

    fun session(o: JsonObject): SessionInfo = SessionInfo(
        sessionId = o.s("sessionId") ?: o.s("id") ?: "",
        agentName = o.s("agentName"),
        title = o.s("title"),
        workspaceDir = o.s("workspaceDir"),
        sessionKind = o.s("sessionKind"),
        visibility = o.s("visibility"),
        archived = o.b("archived"),
        pinned = o.b("pinned"),
        parentSessionId = o.s("parentSessionId"),
        status = o.s("status"),
        createdAt = o.l("createdAt"),
        updatedAt = o.l("updatedAt"),
        model = o.o("model")?.let { SessionModel(it.s("providerId"), it.s("modelId"), it.s("variant")) },
    )

    fun models(text: String): List<ModelEntry> =
        payload(text, "models").objs().mapNotNull { o ->
            val p = o.s("providerId") ?: return@mapNotNull null
            val m = o.s("modelId") ?: return@mapNotNull null
            ModelEntry(
                providerId = p,
                modelId = m,
                modelConfigId = o.s("modelConfigId"),
                displayName = o.s("displayName"),
                enabled = o.b("enabled"),
                selected = o.b("selected"),
                providerName = o.s("providerName"),
                contextLimit = o.l("contextLimit"),
                variant = o.s("variant"),
            )
        }

    fun skills(text: String): SkillPage {
        val o = obj(text) ?: return SkillPage()
        return SkillPage(
            skills = o.objs("skills").map {
                SkillEntry(
                    name = it.s("name") ?: "",
                    description = it.s("description"),
                    displayDescription = it.s("displayDescription"),
                    source = it.s("source"),
                    enabled = it.b("enabled"),
                )
            },
            hasMore = o.b("hasMore") ?: false,
        )
    }

    // ---- delegation / queue / background ----

    fun delegation(text: String): DelegationSnapshot {
        val o = obj(text) ?: return DelegationSnapshot()
        return DelegationSnapshot(
            rootSessionId = o.s("rootSessionId"),
            members = o.objs("members").map {
                DelegationMember(
                    sessionId = it.s("sessionId") ?: "",
                    parentSessionId = it.s("parentSessionId"),
                    agentName = it.s("agentName"),
                    task = it.s("task"),
                    status = it.s("status") ?: "unknown",
                    backgroundTaskId = it.s("backgroundTaskId"),
                    errorMessage = it.s("errorMessage"),
                    createdAtMs = it.l("createdAtMs"),
                    updatedAtMs = it.l("updatedAtMs"),
                )
            },
        )
    }

    fun backgroundTasks(text: String): List<BackgroundTask> =
        payload(text, "tasks").objs().map {
            BackgroundTask(it.s("id"), it.s("taskId"), it.s("label"), it.s("status"), it.s("sessionId"))
        }

    fun queue(text: String): QueueSnapshot {
        val o = obj(text) ?: return QueueSnapshot()
        return QueueSnapshot(
            items = o.objs("items").map { QueueItem(it.s("id") ?: "", it.s("content"), it.s("status")) },
            paused = o.b("paused") ?: false,
            pendingCount = o.i("pendingCount") ?: 0,
        )
    }

    // ---- usage / context / fork ----

    fun usage(text: String): SessionUsage {
        val o = obj(text) ?: return SessionUsage()
        val s = o.o("summary")
        return SessionUsage(
            summary = UsageSummary(
                inputTokens = s?.l("inputTokens") ?: 0,
                outputTokens = s?.l("outputTokens") ?: 0,
                reasoningTokens = s?.l("reasoningTokens") ?: 0,
                cacheReadTokens = s?.l("cacheReadTokens") ?: 0,
                cacheWriteTokens = s?.l("cacheWriteTokens") ?: 0,
                costUsd = s?.d("costUsd") ?: 0.0,
                turns = s?.i("turns") ?: 0,
                totalTokens = s?.l("totalTokens") ?: 0,
            ),
            rows = o.objs("rows").map {
                UsageRow(
                    providerId = it.s("providerId"),
                    modelId = it.s("modelId"),
                    inputTokens = it.l("inputTokens") ?: 0,
                    outputTokens = it.l("outputTokens") ?: 0,
                    costUsd = it.d("costUsd") ?: 0.0,
                    turns = it.i("turns") ?: 0,
                )
            },
        )
    }

    fun context(text: String): ContextSnapshot {
        val o = obj(text) ?: return ContextSnapshot()
        return ContextSnapshot(
            status = o.s("status"),
            model = o.o("model")?.let { ContextModel(it.s("provider"), it.s("id"), it.l("contextWindow")) },
            usedTokens = o.l("usedTokens") ?: o.l("tokens"),
            totalTokens = o.l("totalTokens"),
        )
    }

    fun forkOptions(text: String): ForkOptions {
        val o = obj(text) ?: return ForkOptions()
        return ForkOptions(
            canFork = o.b("canFork") ?: false,
            unavailableReason = o.s("unavailableReason"),
            suggestedTitle = o.s("suggestedTitle"),
            nextForkOrdinal = o.i("nextForkOrdinal"),
        )
    }

    // ---- stream ----

    fun streamEvent(type: String?, data: String): StreamEvent {
        val o = obj(data) ?: return StreamEvent.Unknown(data)
        val kind = o.s("type") ?: type ?: "unknown"
        val turnId = o.s("turnId")
        return when (kind) {
            "heartbeat" -> StreamEvent.Heartbeat(turnId)
            "done" -> StreamEvent.Done(turnId)
            "error" -> StreamEvent.Failed(o.s("message") ?: "turn failed", turnId)
            "message" -> o.o("message")?.let { StreamEvent.MessageUpsert(message(it)) }
                ?: StreamEvent.Unknown(data)
            "session-status" -> StreamEvent.SessionStatus(o.s("status") ?: "idle", o.s("message"), turnId)
            "delta" -> StreamEvent.Delta(
                messageId = o.s("messageId"),
                turnId = turnId,
                role = o.s("role"),
                content = o.s("content"),
                thinking = o.s("thinking"),
                chunkIndex = o.i("chunkIndex"),
                started = o.b("started") ?: false,
                finish = o.b("finish") ?: false,
            )
            "messages-replaced" -> StreamEvent.MessagesReplaced(messages(o.get("messages")))
            "messages-rewound" -> StreamEvent.MessagesRewound(o.strList("messageIds"))
            "resync-required" -> StreamEvent.ResyncRequired(turnId)
            "generic" -> StreamEvent.Generic(o.s("eventType") ?: "generic", o.get("data"))
            else -> StreamEvent.Unknown(data)
        }
    }

    // ---- /events backbone ----

    /** Runtime event name carried by a `GET /events` frame. */
    fun runtimeEventType(data: String): String? = obj(data)?.s("type")

    fun questionnaireFromEvent(data: String): Pair<Questionnaire?, String?>? {
        val o = obj(data) ?: return null
        if (o.s("type") != "questionnaire.ask") return null
        return questionnaire(o.get("request")) to o.s("agentName")
    }

    fun permissionFromEvent(data: String): PermissionRequest? {
        val o = obj(data) ?: return null
        if (o.s("type") != "permission.ask") return null
        return o.o("request")?.let { permission(it) }
    }

    fun sessionEventIds(data: String): Triple<String?, String?, String?>? {
        val o = obj(data) ?: return null
        val type = o.s("type") ?: return null
        return Triple(type, o.s("sessionId"), o.s("itemId"))
    }
}
