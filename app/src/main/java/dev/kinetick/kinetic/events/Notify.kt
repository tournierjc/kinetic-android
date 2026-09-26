package dev.kinetick.kinetic.events

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.kinetick.kinetic.MainActivity
import dev.kinetick.kinetic.R

/**
 * Notification backbone: the Runtime event stream (GET /events) emits
 * questionnaire.ask / permission.ask / session.queue.updated — anything that
 * means "the agent is waiting for a human" gets a heads-up notification.
 */
object Notify {

    const val CHANNEL_INPUT = "input_needed"
    const val CHANNEL_STREAM = "stream"
    const val NOTIF_STREAM = 1
    const val NOTIF_INPUT_BASE = 1000

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
            ).apply { description = "Keeps the kcode event stream alive" }
        )
    }

    fun inputNeeded(context: Context, sessionKey: String, title: String, body: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_id", sessionKey)
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
                .setContentTitle(title)
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
