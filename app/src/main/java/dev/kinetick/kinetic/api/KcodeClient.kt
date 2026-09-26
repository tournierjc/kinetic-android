package dev.kinetick.kinetic.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Thin, synchronous client for the `kcode --server` HTTP API.
 * Endpoint reference: docs/harness-integration.md of kinetick-code.
 *
 * All capability-optional endpoints may answer 404 with
 * {"error":"<capability> is not supported by this runtime"} — callers
 * surface that as KcodeException(404).
 */
class KcodeClient(private val baseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE streams stay open
        .build()

    val json: Gson = GsonBuilder().setLenient().create()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    class KcodeException(val code: Int, message: String) : Exception(message)

    private fun get(path: String): String = exec(
        Request.Builder().url(baseUrl.trimEnd('/') + path).build()
    )

    private fun post(path: String, body: JsonObject = JsonObject()): String = exec(
        Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toString().toRequestBody(JSON))
            .build()
    )

    private fun patch(path: String, body: JsonObject): String = exec(
        Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .patch(body.toString().toRequestBody(JSON))
            .build()
    )

    private fun delete(path: String): String = exec(
        Request.Builder().url(baseUrl.trimEnd('/') + path).delete().build()
    )

    private fun exec(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw KcodeException(resp.code, parseError(text) ?: "HTTP ${resp.code}")
            return text
        }
    }

    private fun parseError(text: String): String? =
        runCatching {
            val el = com.google.gson.JsonParser.parseString(text)
            if (el.isJsonObject) el.asJsonObject.get("error")?.asString else null
        }.getOrNull()

    private fun <T> fromJson(text: String, type: java.lang.reflect.Type): T =
        json.fromJson(text, type)

    // ---- descriptor / health ----

    fun health(): Health = fromJson(get("/health"), Health::class.java)

    // ---- sessions ----

    fun listSessions(limit: Int = 50, cursor: String? = null, includeArchived: Boolean = false): SessionPage {
        val q = buildString {
            append("?limit=").append(limit)
            if (cursor != null) append("&cursor=").append(cursor)
            if (includeArchived) append("&includeArchived=true")
        }
        return fromJson(get("/sessions$q"), SessionPage::class.java)
    }

    fun getSession(id: String): SessionInfo = fromJson(get("/sessions/$id"), SessionInfo::class.java)

    fun createSession(workspaceDir: String, title: String? = null): SessionInfo {
        val body = JsonObject().apply {
            addProperty("workspaceDir", workspaceDir)
            if (title != null) addProperty("title", title)
        }
        return fromJson(post("/sessions", body), SessionInfo::class.java)
    }

    fun renameSession(id: String, title: String) {
        patch("/sessions/$id", JsonObject().apply { addProperty("title", title) })
    }

    fun deleteSession(id: String) { delete("/sessions/$id") }

    fun setPinned(id: String, pinned: Boolean) {
        post("/sessions/$id/pin", JsonObject().apply { addProperty("pinned", pinned) })
    }

    fun messages(id: String, limit: Int = 50, before: String? = null): MessagePage {
        val q = buildString {
            append("?limit=").append(limit)
            if (before != null) append("&before=").append(before)
        }
        return fromJson(get("/sessions/$id/messages$q"), MessagePage::class.java)
    }

    // ---- turn lifecycle ----

    /** POST /prompt answers an SSE stream of TuiStreamEvents; returns the source. */
    fun promptStream(id: String, content: String, model: String? = null, listener: EventSourceListener): EventSource {
        val body = JsonObject().apply {
            addProperty("content", content)
            if (model != null) addProperty("model", model)
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/sessions/$id/prompt")
            .post(body.toString().toRequestBody(JSON))
            .header("Accept", "text/event-stream")
            .build()
        return EventSources.createFactory(http).newEventSource(request, listener)
    }

    fun abort(id: String, reason: String? = null) {
        post("/sessions/$id/abort", JsonObject().apply { if (reason != null) addProperty("reason", reason) })
    }

    fun steer(id: String, content: String) {
        post("/sessions/$id/steer", JsonObject().apply { addProperty("content", content) })
    }

    fun activeRun(id: String): ActiveRun = fromJson(get("/sessions/$id/active-run"), ActiveRun::class.java)

    // ---- user-input surface ----

    fun interactions(id: String): Interactions = fromJson(get("/sessions/$id/interactions"), Interactions::class.java)

    fun replyQuestionnaire(id: String, requestId: String, body: JsonObject) {
        post("/sessions/$id/questionnaires/$requestId/reply", body)
    }

    fun dismissQuestionnaire(id: String, requestId: String) {
        post("/sessions/$id/questionnaires/$requestId/dismiss")
    }

    fun pendingPermissions(): PermissionList = fromJson(get("/permissions"), PermissionList::class.java)

    fun replyPermission(agentName: String, requestId: String, decision: String) {
        post("/permissions/$agentName/$requestId/reply", JsonObject().apply { addProperty("decision", decision) })
    }

    // ---- subagents / background ----

    fun delegation(id: String): DelegationSnapshot = fromJson(get("/sessions/$id/delegation"), DelegationSnapshot::class.java)

    fun stopDelegation(id: String) { post("/sessions/$id/delegation/stop") }

    fun backgroundTasks(id: String): BackgroundTasks = fromJson(get("/sessions/$id/background-tasks"), BackgroundTasks::class.java)

    // ---- queue ----

    fun queue(id: String): QueueSnapshot = fromJson(get("/sessions/$id/queue"), QueueSnapshot::class.java)

    fun queueEnqueue(id: String, content: String) {
        post("/sessions/$id/queue/enqueue", JsonObject().apply { addProperty("content", content) })
    }

    fun queueContinue(id: String) { post("/sessions/$id/queue/continue") }

    // ---- model ----

    fun models(id: String): ModelList = fromJson(get("/sessions/$id/model"), ModelList::class.java)

    fun selectModel(id: String, providerId: String, modelId: String) {
        post("/sessions/$id/model", JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("providerId", providerId)
                addProperty("modelId", modelId)
            })
        })
    }

    // ---- resources ----

    fun skills(workspaceDir: String? = null): SkillList {
        val q = if (workspaceDir != null) "?workspaceDir=$workspaceDir" else ""
        return fromJson(get("/skills$q"), SkillList::class.java)
    }

    fun status(): com.google.gson.JsonElement =
        com.google.gson.JsonParser.parseString(get("/status"))


    /** GET /events — Runtime event stream (SSE backbone for notifications). */
    fun eventStream(listener: EventSourceListener): EventSource {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/events")
            .header("Accept", "text/event-stream")
            .build()
        return EventSources.createFactory(http).newEventSource(request, listener)
    }

    /** Long-lived raw GET (SSE). */
    fun openStream(path: String, listener: EventSourceListener): EventSource {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "text/event-stream")
            .build()
        return EventSources.createFactory(http).newEventSource(request, listener)
    }
}

// ---- DTOs (subset of the API surface, tolerant of extra fields) ----

data class Health(val ok: Boolean, val version: String)

data class SessionInfo(
    val id: String,
    val title: String?,
    val workspaceDir: String?,
    val agent: String?,
    val updatedAt: String?,
    val pinned: Boolean?,
    val archived: Boolean?,
)

data class SessionPage(val sessions: List<SessionInfo>, val nextCursor: String?)

data class MessageInfo(
    val id: String,
    val role: String,
    val createdAt: String?,
    val content: com.google.gson.JsonElement?,
)

data class MessagePage(val messages: List<MessageInfo>, val nextCursor: String?)

data class ActiveRun(val running: Boolean, val state: String?)

data class Interactions(
    val questionnaire: com.google.gson.JsonObject?,
    val planReview: com.google.gson.JsonObject?,
    val permissions: List<PermissionRequest>?,
    val activeRun: com.google.gson.JsonObject?,
)

data class PermissionRequest(
    val agentName: String,
    val requestId: String,
    val toolName: String?,
    val detail: String?,
)

data class PermissionList(val requests: List<PermissionRequest>)

data class DelegationMember(
    val id: String,
    val label: String?,
    val status: String,
)

data class DelegationSnapshot(val members: List<DelegationMember>)

data class BackgroundTask(val id: String, val label: String?, val status: String?)

data class BackgroundTasks(val tasks: List<BackgroundTask>)

data class QueueItem(val id: String, val content: String?, val status: String?)

data class QueueSnapshot(val items: List<QueueItem>, val paused: Boolean, val pendingCount: Int)

data class ModelEntry(val providerId: String, val modelId: String, val label: String?, val selected: Boolean?)

data class ModelList(val models: List<ModelEntry>)

data class SkillEntry(val name: String, val description: String?, val enabled: Boolean?)

data class SkillList(val skills: List<SkillEntry>)
