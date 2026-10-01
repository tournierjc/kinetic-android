package dev.kinetick.kinetick.events

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.kinetick.kinetick.KinetickApp
import dev.kinetick.kinetick.api.Wire
import dev.kinetick.kinetick.data.ServerEntry
import dev.kinetick.kinetick.data.ServerRegistry
import dev.kinetick.kinetick.data.configured
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service holding the Runtime event stream (GET /events) open for
 * *every* registered server at the same time.
 *
 * Input-needed events raise heads-up notifications:
 *  - `questionnaire.ask` → `request.title` / first step question
 *  - `permission.ask`    → `request.toolName`
 * Every event is relayed to [EventBus] tagged with its server id.
 */
class EventStreamService : Service() {

    private class Stream(val source: EventSource?, val generation: Int)

    private val streams = ConcurrentHashMap<String, Stream>()
    private val streamGeneration = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notify.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notify.NOTIF_STREAM,
            Notify.streamNotification(this, "Connecting to kcode…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        watch()
        return START_STICKY
    }

    /** Re-syncs streams whenever the registry changes (add/edit/delete). */
    private fun watch() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            val app = application as KinetickApp
            app.settings.servers.collect { servers -> syncStreams(servers) }
        }
    }

    private fun syncStreams(servers: List<ServerEntry>) {
        val wanted = servers.filter { it.configured() }.associateBy { it.id }
        // Drop streams of servers that were deleted, edited (url/token change
        // moves the cache key) or de-configured.
        streams.keys.filterNot { it in wanted }.forEach { id ->
            streams.remove(id)?.source?.cancel()
        }
        wanted.values.forEach { openStream(it) }
        updateNotification(servers)
    }

    private fun openStream(server: ServerEntry) {
        if (streams[server.id]?.source != null) return
        val generation = streamGeneration.incrementAndGet()
        val client = KinetickApp.clientFor(server)
        val opened = client.eventStream(object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (streams[server.id]?.generation != generation) return
                handleEvent(server, type, data)
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                if (streams[server.id]?.generation != generation) return
                streams.remove(server.id)
                if (response?.code == 401) {
                    // A rotated token is a settings change, not a retry case;
                    // the next registry emission re-opens the stream.
                    scope.launch { updateNotificationFromStore() }
                    return
                }
                scope.launch { updateNotificationFromStore() }
                // Back off before forcing a reconnect pass so a dead server
                // does not hot-loop.
                scope.launch {
                    delay(5000)
                    if (streams[server.id]?.source == null) forceReconnect()
                }
            }
        })
        streams[server.id] = Stream(opened, generation)
    }

    private fun forceReconnect() {
        scope.launch {
            val app = application as KinetickApp
            val servers = app.settings.servers.first()
            servers.filter { it.configured() }.forEach { server ->
                if (streams[server.id]?.source == null) openStream(server)
            }
            updateNotification(servers)
        }
    }

    private fun updateNotification(servers: List<ServerEntry>) {
        val configured = servers.count { it.configured() }
        val open = streams.count { (id, s) ->
            s.source != null && servers.any { it.id == id && it.configured() }
        }
        val text = when {
            configured == 0 && servers.isEmpty() -> "No kcode server configured"
            configured == 0 -> "All servers need a valid token"
            open == configured -> "Connected to $open kcode server${if (open == 1) "" else "s"}"
            else -> "$open of $configured kcode servers connected"
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notify.NOTIF_STREAM, Notify.streamNotification(this, text))
    }

    private suspend fun updateNotificationFromStore() {
        val app = application as KinetickApp
        updateNotification(app.settings.servers.first())
    }

    private fun handleEvent(server: ServerEntry, type: String?, data: String) {
        val runtimeType = Wire.runtimeEventType(data) ?: type ?: return
        EventBus.publish(runtimeType, data, serverId = server.id)
        val label = ServerRegistry.label(server)

        when (runtimeType) {
            "questionnaire.ask" -> {
                val (questionnaire, agentName) = Wire.questionnaireFromEvent(data) ?: return
                val question = questionnaire?.title
                    ?: questionnaire?.steps?.firstOrNull()?.question
                    ?: "The agent is asking a question"
                Notify.inputNeeded(
                    this,
                    sessionKey = agentName ?: questionnaire?.id ?: "kcode",
                    title = "Question from $label",
                    body = question,
                    id = ("${server.id}:${questionnaire?.id ?: question}").hashCode(),
                    serverId = server.id,
                )
            }

            "permission.ask" -> {
                val request = Wire.permissionFromEvent(data) ?: return
                Notify.inputNeeded(
                    this,
                    sessionKey = request.sessionId ?: request.agentName ?: "kcode",
                    title = "Permission requested · $label",
                    body = "kcode wants to run ${request.toolName ?: "a tool"}" +
                        (request.reason?.let { " — $it" } ?: ""),
                    id = ("${server.id}:${request.requestId}").hashCode(),
                    serverId = server.id,
                )
            }
        }
    }

    override fun onDestroy() {
        streams.values.forEach { it.source?.cancel() }
        streams.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, EventStreamService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventStreamService::class.java))
        }
    }
}
