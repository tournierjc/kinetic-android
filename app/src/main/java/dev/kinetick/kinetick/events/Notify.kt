package dev.kinetick.kinetick.events

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.kinetick.kinetick.MainActivity
import dev.kinetick.kinetick.R

/**
 * Notification backbone: the Runtime event stream (GET /events) emits
 * questionnaire.ask / permission.ask / session.queue.updated — anything that
 * means "the agent is waiting for a human" gets a heads-up notification.
 *
 * With several servers registered the server's label rides in the title/body
 * and the notification carries `extra_server_id`, so a tap can reopen the
 * exact server the event came from.
 */
object Notify {

    const val CHANNEL_INPUT = "input_needed"
    const val CHANNEL_STREAM = "stream"
    const val NOTIF_STREAM = 1
    const val NOTIF_INPUT_BASE = 1000
    const val EXTRA_SESSION = "session_id"
    const val EXTRA_SERVER_ID = "server_id"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INPUT, "Input needed",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "kcode is waiting for your answer" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STREAM, "Event stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the kcode event streams alive" }
        )
    }

    fun inputNeeded(
        context: Context,
        sessionKey: String,
        title: String,
        body: String,
        id: Int,
        serverId: String? = null,
        serverLabel: String? = null,
    ) {
        val fullTitle = if (serverLabel.isNullOrBlank()) title else "$title · $serverLabel"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION, sessionKey)
            serverId?.let { putExtra(EXTRA_SERVER_ID, it) }
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIF_INPUT_BASE + (id % 1000),
            NotificationCompat.Builder(context, CHANNEL_INPUT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(fullTitle)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
    }

    fun streamNotification(context: Context, text: String): Notification {
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_STREAM)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Kinetic")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
