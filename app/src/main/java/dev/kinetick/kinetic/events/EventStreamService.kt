package dev.kinetick.kinetic.events

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.Wire
import dev.kinetick.kinetic.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service holding the Runtime event stream (GET /events) open.
 *
 * Input-needed events raise heads-up notifications:
 *  - `questionnaire.ask` → `request.title` / first step question
 *  - `permission.ask`    → `request.toolName`
 * Every event is relayed to [EventBus] for the in-app UI.
 */
class EventStreamService : Service() {

    @Volatile
    private var source: EventSource? = null
    private var connectJob: Job? = null
    private val streamGeneration = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notify.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notify.NOTIF_STREAM,
            Notify.streamNotification(this, "Connecting to kcode…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        connect()
        return START_STICKY
    }

    private fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            val app = application as KineticApp
            app.settings.server.collect { server ->
                openStream(server)
            }
        }
    }

    private fun openStream(server: SettingsStore.ServerSettings) {
        val previous = source
        val generation = streamGeneration.incrementAndGet()
        source = null
        previous?.cancel()

        val client = KcodeClient(server.baseUrl, server.token)
        if (!client.configured) {
            updateNotification(
                if (server.baseUrl.isBlank()) "No kcode server configured" else "Server token is required",
            )
            return
        }
        updateNotification("Connecting to kcode…")
        val opened = client.eventStream(object : EventSourceListener() {
            override fun onOpen(es: EventSource, response: Response) {
                if (streamGeneration.get() != generation) return
                updateNotification("Connected to ${server.baseUrl}")
            }

            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (streamGeneration.get() != generation) return
                handleEvent(type, data)
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                if (streamGeneration.get() != generation) return
                if (response?.code == 401) {
                    updateNotification("Unauthorized — check the server token")
                    return
                }
                updateNotification("Disconnected — retrying")
                // START_STICKY restarts the service; pause first to avoid a hot loop.
                // Skip the restart when a newer stream (or a settings change) has
                // already replaced this one.
                scope.launch {
                    delay(5000)
                    if (streamGeneration.get() == generation) stopSelf()
                }
            }
        })
        if (streamGeneration.get() == generation) source = opened
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notify.NOTIF_STREAM, Notify.streamNotification(this, text))
    }

    private fun handleEvent(type: String?, data: String) {
        val runtimeType = Wire.runtimeEventType(data) ?: type ?: return
        EventBus.publish(runtimeType, data)

        when (runtimeType) {
            "questionnaire.ask" -> {
                val (questionnaire, agentName) = Wire.questionnaireFromEvent(data) ?: return
                val question = questionnaire?.title
                    ?: questionnaire?.steps?.firstOrNull()?.question
                    ?: "The agent is asking a question"
                Notify.inputNeeded(
                    this,
                    sessionKey = agentName ?: questionnaire?.id ?: "kcode",
                    title = "Question from kcode",
                    body = question,
                    id = (questionnaire?.id ?: question).hashCode(),
                )
            }

            "permission.ask" -> {
                val request = Wire.permissionFromEvent(data) ?: return
                Notify.inputNeeded(
                    this,
                    sessionKey = request.sessionId ?: request.agentName ?: "kcode",
                    title = "Permission requested",
                    body = "kcode wants to run ${request.toolName ?: "a tool"}" +
                        (request.reason?.let { " — $it" } ?: ""),
                    id = request.requestId.hashCode(),
                )
            }
        }
    }

    override fun onDestroy() {
        source?.cancel()
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
