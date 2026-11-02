package com.gymtracker.timer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gymtracker.R

object Notifications {
    private const val CHANNEL_RUNNING = "rest_timer"
    private const val CHANNEL_FINISHED = "rest_finished"
    const val ID_RUNNING = 1
    private const val ID_FINISHED = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Minuteur de repos", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Compte à rebours du repos entre les séries"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FINISHED, "Fin du repos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerte quand le repos est terminé"
                // Le son et la vibration sont joués directement par le minuteur.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun buildRunning(context: Context, state: RestTimer.State): Notification =
        NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Repos en cours")
            .setContentText(state.label.ifBlank { "Prochaine série bientôt" })
            .setWhen(state.endsAtWallClock)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .addAction(0, "−15 s", action(context, TimerActionReceiver.ACTION_MINUS, 1))
            .addAction(0, "+15 s", action(context, TimerActionReceiver.ACTION_PLUS, 2))
            .addAction(0, "Passer", action(context, TimerActionReceiver.ACTION_SKIP, 3))
            .build()

    @SuppressLint("MissingPermission")
    fun showFinished(context: Context, label: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Repos terminé 💪")
            .setContentText(if (label.isBlank()) "C'est parti pour la série suivante !" else "Série suivante : $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(ID_FINISHED, n) }
    }

    fun cancelFinished(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_FINISHED)
    }
}
