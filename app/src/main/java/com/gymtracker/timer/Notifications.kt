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
    /** v2 : avec vibration, pour que la montre connectée vibre aussi. */
    private const val CHANNEL_FINISHED = "rest_finished_v2"
    private const val OLD_CHANNEL_FINISHED = "rest_finished"
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
        nm.deleteNotificationChannel(OLD_CHANNEL_FINISHED)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FINISHED, "Fin du repos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerte quand le repos est terminé (relayée sur la montre connectée)"
                // Le son est joué par le minuteur (écouteurs) ; la vibration fait aussi vibrer la montre.
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
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
    fun showFinished(context: Context, label: String, next: NextSet? = null) {
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
            .apply {
                if (next != null) {
                    val w = if (next.weightKg % 1.0 == 0.0) next.weightKg.toLong().toString() else next.weightKg.toString().replace('.', ',')
                    setContentText("${next.exerciseName} : $w kg × ${next.reps}")
                    addAction(0, "✓ Série faite ($w × ${next.reps})", SessionActionReceiver.pendingIntent(context))
                }
            }
            .extend(NotificationCompat.WearableExtender())
            .build()
        runCatching { nm.notify(ID_FINISHED, n) }
    }

    fun cancelFinished(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_FINISHED)
    }
}
