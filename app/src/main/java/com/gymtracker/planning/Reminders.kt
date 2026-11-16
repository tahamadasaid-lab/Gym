package com.gymtracker.planning

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gymtracker.GymApp
import com.gymtracker.MainActivity
import com.gymtracker.R
import com.gymtracker.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Rappel quotidien (à l'heure choisie) les jours où une séance est planifiée. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val NOTIF_ID = 30
    const val ACTION_START_PLANNED = "com.gymtracker.action.START_PLANNED"
    const val EXTRA_PROGRAM = "programId"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rappels de séance", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rappel les jours d'entraînement planifiés"
            }
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 40, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** (Re)programme le rappel quotidien selon les réglages. */
    fun schedule(context: Context, settings: AppSettings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(context))
        if (!settings.reminderEnabled || settings.weekPlan.isEmpty()) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(settings.reminderMinutes / 60, settings.reminderMinutes % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, alarmIntent(context))
    }

    /** Programme prévu aujourd'hui (0 = séance libre), ou null si repos. */
    fun plannedToday(settings: AppSettings): Long? = settings.weekPlan[LocalDate.now().dayOfWeek.value]

    fun startIntent(context: Context, programId: Long): PendingIntent = PendingIntent.getActivity(
        context, 41,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_START_PLANNED)
            .putExtra(EXTRA_PROGRAM, programId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun notifyIfPlanned(app: GymApp) {
        val settings = app.settings.settings.value
        val planned = plannedToday(settings) ?: return
        val repo = app.repository
        // Déjà une séance aujourd'hui (en cours ou terminée) : pas de rappel.
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (repo.observeActiveSession().first() != null) return
        if (repo.observeFinishedSessions().first().any { it.session.startedAt >= todayStart }) return
        val name = if (planned == 0L) "séance libre" else repo.getProgram(planned)?.program?.name ?: return
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("C'est jour d'entraînement 💪")
            .setContentText("Au programme aujourd'hui : $name")
            .setAutoCancel(true)
            .setContentIntent(startIntent(app, planned))
            .addAction(0, "Démarrer", startIntent(app, planned))
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as GymApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Reminders.notifyIfPlanned(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Après un redémarrage du téléphone, les alarmes sont perdues : on les reprogramme. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as GymApp
        Reminders.schedule(app, app.settings.settings.value)
    }
}
