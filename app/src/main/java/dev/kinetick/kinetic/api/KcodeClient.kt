package dev.kinetick.kinetic.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Synchronous client for the `kcode --server` HTTP API.
 * Endpoint reference: docs/harness-integration.md of kinetick-code.
 *
 * Every request, including `/health` and the SSE streams, sends
 * `Authorization: Bearer`. A missing or wrong token is `401`.
 *
 * Capability-optional endpoints answer 404 with
 * `{"error":"<capability> is not supported by this runtime"}` — surfaced as
 * [KcodeException] with code 404 so callers degrade instead of failing.
 */
class KcodeClient(val baseUrl: String, val token: String = "") {

    companion object {
        /**
         * Same shape the server accepts: 16 to 256 printable ASCII characters,
         * no spaces. Leading and trailing whitespace is ignored so a pasted
         * token file (which ends in a newline) still matches.
         */
        fun usableToken(token: String): Boolean {
            val value = token.trim()
            if (value.length !in 16..256) return false
            return value.all { it.code in 0x21..0x7E }
        }
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE streams stay open
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    class KcodeException(val code: Int, message: String) : Exception(message)

    /** True once the user has entered a usable server URL and bearer token. */
    val configured: Boolean
        get() {
            val base = baseUrl.trim()
            val urlOk = base.startsWith("http://") || base.startsWith("https://")
            return urlOk && usableToken(token)
        }

    private fun url(path: String): String {
        val base = baseUrl.trim()
        // Answer with something actionable rather than OkHttp's
        // "Expected url scheme 'http' or 'https'" leaking into the UI.
        if (base.isEmpty()) throw KcodeException(0, "No kcode server configured")
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw KcodeException(0, "Server URL must start with http:// or https://")
        }
        return base.trimEnd('/') + path
    }

    private fun builder(path: String): Request.Builder {
        val resolved = url(path)
        val bearer = normalizedToken()
        val request = try {
            Request.Builder()
                .url(resolved)
                .header("Authorization", "Bearer $bearer")
        } catch (e: IllegalArgumentException) {
            throw KcodeException(0, "Invalid server URL: ${e.message}")
        }
        return request
    }

    private fun normalizedToken(): String {
        val value = token.trim()
        if (value.isEmpty()) throw KcodeException(0, "Server token is required")
        if (!usableToken(value)) {
            throw KcodeException(
                0,
                "Server token must be 16 to 256 printable ASCII characters without spaces",
            )
        }
        return value
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun exec(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 401) {
                    throw KcodeException(401, "Unauthorized — check the server token")
                }
                val msg = Wire.obj(text)?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: "HTTP ${resp.code}"
                throw KcodeException(resp.code, msg)
            }
            return text
        }
    }

    fun get(path: String): String = exec(builder(path).build())

    fun post(path: String, body: JsonObject = JsonObject()): String = exec(
        builder(path).post(body.toString().toRequestBody(JSON)).build()
    )

    fun patch(path: String, body: JsonObject): String = exec(
        builder(path).patch(body.toString().toRequestBody(JSON)).build()
    )

    fun delete(path: String): String = exec(builder(path).delete().build())

    private fun sse(path: String, body: JsonObject?, listener: EventSourceListener): EventSource {
        val b = builder(path).header("Accept", "text/event-stream")
        if (body != null) b.post(body.toString().toRequestBody(JSON)) else b.get()
        return EventSources.createFactory(http).newEventSource(b.build(), listener)
    }

    private fun j(vararg pairs: Pair<String, Any?>): JsonObject {
        val o = JsonObject()
        for ((k, v) in pairs) when (v) {
            null -> {}
            is String -> o.addProperty(k, v)
            is Boolean -> o.addProperty(k, v)
            is Number -> o.addProperty(k, v)
            is JsonObject -> o.add(k, v)
            is JsonArray -> o.add(k, v)
        }
        return o
    }

    // ---- descriptor ----

    fun health(): String = get("/health")

    fun status(): String = get("/status")

    fun mcp(keyword: String? = null): String =
        get("/mcp" + if (keyword.isNullOrBlank()) "" else "?keyword=${enc(keyword)}")

    // ---- sessions ----

    fun listSessions(
        limit: Int = 50,
        cursor: String? = null,
        includeArchived: Boolean = false,
        onlyArchived: Boolean = false,
        allAgents: Boolean = false,
    ): SessionPage = Wire.sessionPage(
        get(
            "/sessions?limit=$limit" +
                (cursor?.let { "&cursor=$it" } ?: "") +
                (if (includeArchived) "&includeArchived=true" else "") +
                (if (onlyArchived) "&onlyArchived=true" else "") +
                (if (allAgents) "&allAgents=true" else "")
        )
    )

    fun getSession(id: String): SessionInfo =
        Wire.session(get("/sessions/${enc(id)}")) ?: throw KcodeException(404, "session not found")

    fun createSession(workspaceDir: String, title: String? = null): SessionInfo =
        Wire.session(post("/sessions", j("workspaceDir" to workspaceDir, "title" to title)))
            ?: throw KcodeException(500, "unexpected create response")

    fun renameSession(id: String, title: String) {
        patch("/sessions/${enc(id)}", j("title" to title))
    }

    fun deleteSession(id: String) { delete("/sessions/${enc(id)}") }

    fun setPinned(id: String, pinned: Boolean) {
        post("/sessions/${enc(id)}/pin", j("pinned" to pinned))
    }

    fun setArchived(id: String, archived: Boolean) {
        post("/sessions/${enc(id)}/archive", j("archived" to archived))
    }

    fun messages(id: String, limit: Int = 50, before: String? = null): MessagePage =
        Wire.messagePage(
            get("/sessions/${enc(id)}/messages?limit=$limit" + (before?.let { "&before=$it" } ?: ""))
        )

    // ---- turn lifecycle ----

    /** POST /prompt — answers an SSE stream of TuiStreamEvents. */
    fun promptStream(
        id: String,
        content: String,
        model: JsonObject?,
        listener: EventSourceListener,
    ): EventSource = sse("/sessions/${enc(id)}/prompt", j("content" to content, "model" to model), listener)

    fun abort(id: String, reason: String? = null) {
        post("/sessions/${enc(id)}/abort", j("reason" to reason))
    }

    fun steer(id: String, content: String) {
        post("/sessions/${enc(id)}/steer", j("content" to content))
    }

    fun activeRun(id: String): ActiveRun {
        val o = Wire.obj(get("/sessions/${enc(id)}/active-run"))
        val actions = o?.get("actions")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()
            ?.mapNotNull { (k, v) -> if (v.isJsonPrimitive) k to v.asBoolean else null }
            ?.toMap() ?: emptyMap()
        val state = o?.get("state")?.takeIf { it.isJsonPrimitive }?.asString
        return ActiveRun(state = state, actions = actions)
    }

    // ---- interactions ----

    fun interactions(id: String): Interactions =
        Wire.interactions(get("/sessions/${enc(id)}/interactions"))

    fun replyQuestionnaire(id: String, requestId: String, answers: List<QuestionnaireAnswer>) {
        val arr = JsonArray()
        for (a in answers) {
            val o = JsonObject()
            o.addProperty("stepId", a.stepId)
            if (a.selectedOptionIds.isNotEmpty()) {
                val ids = JsonArray()
                a.selectedOptionIds.forEach { ids.add(it) }
                o.add("selectedOptionIds", ids)
            }
            if (a.selectedOther) o.addProperty("selectedOther", true)
            a.otherText?.let { o.addProperty("otherText", it) }
            if (a.skipped) o.addProperty("skipped", true)
            arr.add(o)
        }
        post("/sessions/${enc(id)}/questionnaires/${enc(requestId)}/reply", j("answers" to arr))
    }

    fun dismissQuestionnaire(id: String, requestId: String) {
        post("/sessions/${enc(id)}/questionnaires/${enc(requestId)}/dismiss")
    }

    fun pendingPermissions(): List<PermissionRequest> = Wire.permissions(get("/permissions"))

    fun replyPermission(agentName: String, requestId: String, decision: String) {
        post("/permissions/${enc(agentName)}/${enc(requestId)}/reply", j("decision" to decision))
    }

    // ---- delegation / background ----

    fun delegation(id: String): DelegationSnapshot =
        Wire.delegation(get("/sessions/${enc(id)}/delegation"))

    fun stopDelegation(id: String) { post("/sessions/${enc(id)}/delegation/stop") }

    fun backgroundTasks(id: String): List<BackgroundTask> =
        Wire.backgroundTasks(get("/sessions/${enc(id)}/background-tasks"))

    // ---- queue ----

    fun queue(id: String): QueueSnapshot = Wire.queue(get("/sessions/${enc(id)}/queue"))

    /**
     * Enqueue a follow-up. The ack is authoritative: on an idle session the
     * runtime drains the message into the conversation immediately, so it may
     * never show up in a queue snapshot.
     */
    fun queueEnqueue(id: String, content: String): QueuedMessage =
        Wire.queuedMessage(post("/sessions/${enc(id)}/queue/enqueue", j("content" to content)))

    fun queueContinue(id: String) { post("/sessions/${enc(id)}/queue/continue") }

    fun queueSteer(id: String, itemId: String) { post("/sessions/${enc(id)}/queue/steer/${enc(itemId)}") }

    fun queueDelete(id: String, itemId: String) { post("/sessions/${enc(id)}/queue/delete/${enc(itemId)}") }

    // ---- usage / context ----

    fun usage(id: String): SessionUsage = Wire.usage(get("/sessions/${enc(id)}/usage"))

    fun context(id: String): ContextSnapshot = Wire.context(get("/sessions/${enc(id)}/context"))

    // ---- model ----

    fun models(id: String): List<ModelEntry> = Wire.models(get("/sessions/${enc(id)}/model"))

    fun selectModel(id: String, providerId: String, modelId: String, variant: String? = null) {
        post(
            "/sessions/${enc(id)}/model",
            j("model" to j("providerId" to providerId, "modelId" to modelId, "variant" to variant))
        )
    }

    // ---- goal ----

    fun goal(id: String): JsonObject? = Wire.obj(get("/sessions/${enc(id)}/goal"))

    fun createGoal(id: String, objective: String, tokenBudget: Long? = null) {
        post("/sessions/${enc(id)}/goal", j("objective" to objective, "tokenBudget" to tokenBudget))
    }

    fun clearGoal(id: String) { delete("/sessions/${enc(id)}/goal") }

    // ---- fork / rewind ----

    fun forkOptions(id: String, assistantMessageId: String? = null): ForkOptions =
        Wire.forkOptions(
            get(
                "/sessions/${enc(id)}/fork" +
                    (assistantMessageId?.let { "?assistantMessageId=${enc(it)}" } ?: "")
            )
        )

    fun fork(id: String, title: String? = null): String =
        post("/sessions/${enc(id)}/fork", j("title" to title))

    fun rewindPreview(id: String, userMessageId: String): String =
        get("/sessions/${enc(id)}/rewind-preview/${enc(userMessageId)}")

    fun rewind(id: String, userMessageId: String, rewindTurnDiff: Boolean? = null) {
        post(
            "/sessions/${enc(id)}/rewind",
            j("userMessageId" to userMessageId, "rewindTurnDiff" to rewindTurnDiff)
        )
    }

    // ---- resources ----

    fun skills(workspaceDir: String? = null, keyword: String? = null): SkillPage {
        val params = buildList {
            if (!workspaceDir.isNullOrBlank()) add("workspaceDir=${enc(workspaceDir)}")
            if (!keyword.isNullOrBlank()) add("keyword=${enc(keyword)}")
        }
        return Wire.skills(get("/skills" + if (params.isEmpty()) "" else "?" + params.joinToString("&")))
    }

    // ---- streams ----

    /** GET /events — Runtime event stream (notification backbone). */
    fun eventStream(listener: EventSourceListener): EventSource = sse("/events", null, listener)

    /** Open a raw path (SSE). */
    fun openStream(path: String, listener: EventSourceListener): EventSource = sse(path, null, listener)
}
