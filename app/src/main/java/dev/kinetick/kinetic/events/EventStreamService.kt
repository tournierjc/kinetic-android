package dev.kinetick.kinetic.events

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
        connectJob?.cancel()
        val app = application as KineticApp
        connectJob = scope.launch {
            // Re-open the stream when the URL or token changes. A cancelled
            // stream also fires onFailure; the generation ignores that.
            app.settings.server.collect { config ->
                val gen = streamGeneration.incrementAndGet()
                source?.cancel()
                source = null
                when {
                    config.baseUrl.isBlank() -> updateNotification("No kcode server configured")
                    config.token.isBlank() -> updateNotification("Server token required")
                    else -> {
                        val opened = runCatching {
                            val client = KcodeClient(config.baseUrl, config.token)
                            source = client.eventStream(streamListener(config.baseUrl, gen))
                        }
                        opened.onFailure { error ->
                            updateNotification(error.message ?: "Can't open the event stream")
                        }
                    }
                }
            }
        }
    }

    private fun streamListener(base: String, gen: Int) = object : EventSourceListener() {
        override fun onOpen(es: EventSource, response: Response) {
            if (gen == streamGeneration.get()) updateNotification("Connected to $base")
        }

        override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
            if (gen == streamGeneration.get()) handleEvent(type, data)
        }

        override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
            if (gen != streamGeneration.get()) return
            updateNotification("Disconnected — retrying")
            // START_STICKY restarts the service; pause first to avoid a hot loop.
            scope.launch {
                delay(5000)
                if (gen == streamGeneration.get()) stopSelf()
            }
        }
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
        streamGeneration.incrementAndGet()
        source?.cancel()
        connectJob?.cancel()
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
