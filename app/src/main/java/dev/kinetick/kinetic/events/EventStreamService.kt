package dev.kinetick.kinetic.events

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.google.gson.JsonObject
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * Foreground service holding the GET /events SSE open. Input-needed events
 * (questionnaire.ask, permission.ask) raise heads-up notifications; every
 * event is relayed to [EventBus] for in-app UI.
 */
class EventStreamService : Service() {

    private var source: EventSource? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notify.ensureChannels(this)
        val notification = Notify.streamNotification(this, "Connected to kcode")
        ServiceCompat.startForeground(
            this, Notify.NOTIF_STREAM, notification,
            if (Build.VERSION.SDK_INT >= 29)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        connect()
        return START_STICKY
    }

    private fun connect() {
        scope.launch {
            val app = application as KineticApp
            val base = app.settings.baseUrl.first()
            val client = KcodeClient(base)
            source?.cancel()
            source = client.eventStream(object : EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    handleEvent(type ?: "message", data)
                }

                override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                    // Reconnect with backoff via START_STICKY restart cycle
                    stopSelf()
                }
            })
        }
    }

    private fun handleEvent(type: String, data: String) {
        EventBus.publish(type, data)
        val obj = runCatching { com.google.gson.JsonParser.parseString(data).asJsonObject }
            .getOrNull() ?: return
        when (type) {
            "questionnaire.ask" -> {
                val sid = obj.str("sessionId") ?: return
                Notify.inputNeeded(
                    this, sid, "Question from kcode",
                    obj.str("question") ?: obj.str("title") ?: "The agent is asking a question",
                    sid.hashCode()
                )
            }
            "permission.ask" -> {
                val sid = obj.str("sessionId") ?: obj.str("agentName") ?: "kcode"
                Notify.inputNeeded(
                    this, sid, "Permission requested",
                    "kcode wants to run ${obj.str("toolName") ?: "a tool"}",
                    sid.hashCode() + 1
                )
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        if (has(key) && !get(key).isJsonNull) get(key).asString else null

    override fun onDestroy() {
        source?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, EventStreamService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventStreamService::class.java))
        }
    }
}
