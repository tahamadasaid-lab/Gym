package com.gymtracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.gymtracker.MainActivity
import com.gymtracker.R
import com.gymtracker.timer.TimerActionReceiver
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatVolume

/** Ce qu'affiche le widget. */
sealed interface WidgetModel {
    data class Rest(val endsAtWallClock: Long, val label: String, val sessionSets: Int) : WidgetModel
    data class RestDone(val label: String) : WidgetModel
    data class Session(val startedAt: Long, val sets: Int, val volume: Double, val kcal: Int) : WidgetModel
    data class Idle(val weekSessions: Int, val weekKcal: Int, val lastSession: Long?) : WidgetModel
}

object WidgetRenderer {
    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, GymWidgetProvider::class.java))

    fun hasWidgets(context: Context): Boolean = runCatching { ids(context).isNotEmpty() }.getOrDefault(false)

    fun push(context: Context, model: WidgetModel) {
        val ids = runCatching { ids(context) }.getOrNull() ?: return
        if (ids.isEmpty()) return
        AppWidgetManager.getInstance(context).updateAppWidget(ids, build(context, model))
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 20, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun startSession(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(GymWidgetProvider.ACTION_START_SESSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 21, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Base du chronomètre (horloge « elapsedRealtime ») correspondant à une heure murale. */
    private fun chronoBase(wallClock: Long): Long = SystemClock.elapsedRealtime() + (wallClock - System.currentTimeMillis())

    fun build(context: Context, model: WidgetModel): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_gymtracker)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        v.setViewVisibility(R.id.widget_chrono, View.GONE)
        v.setViewVisibility(R.id.widget_big, View.VISIBLE)
        v.setViewVisibility(R.id.widget_btn_secondary, View.GONE)
        v.setOnClickPendingIntent(R.id.widget_btn_primary, openApp(context))

        when (model) {
            is WidgetModel.Rest -> {
                v.setTextViewText(R.id.widget_label, "Repos · ${model.label}")
                v.setViewVisibility(R.id.widget_big, View.GONE)
                v.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                v.setChronometer(R.id.widget_chrono, chronoBase(model.endsAtWallClock), null, true)
                v.setChronometerCountDown(R.id.widget_chrono, true)
                v.setTextViewText(R.id.widget_detail, "Séance en cours · ${model.sessionSets} séries")
                v.setTextViewText(R.id.widget_btn_primary, "+15 s")
                v.setOnClickPendingIntent(
                    R.id.widget_btn_primary,
                    TimerActionReceiver.pendingIntent(context, TimerActionReceiver.ACTION_PLUS, 22),
                )
                v.setViewVisibility(R.id.widget_btn_secondary, View.VISIBLE)
                v.setTextViewText(R.id.widget_btn_secondary, "Passer")
                v.setOnClickPendingIntent(
                    R.id.widget_btn_secondary,
                    TimerActionReceiver.pendingIntent(context, TimerActionReceiver.ACTION_SKIP, 23),
                )
            }
            is WidgetModel.RestDone -> {
                v.setTextViewText(R.id.widget_label, "Repos terminé")
                v.setTextViewText(R.id.widget_big, "C'est reparti 💪")
                v.setTextViewText(R.id.widget_detail, "Série suivante : ${model.label}")
                v.setTextViewText(R.id.widget_btn_primary, "Ouvrir la séance")
            }
            is WidgetModel.Session -> {
                v.setTextViewText(R.id.widget_label, "Séance en cours")
                v.setViewVisibility(R.id.widget_big, View.GONE)
                v.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                v.setChronometer(R.id.widget_chrono, chronoBase(model.startedAt), null, true)
                v.setChronometerCountDown(R.id.widget_chrono, false)
                v.setTextViewText(
                    R.id.widget_detail,
                    "${model.sets} séries · ${formatVolume(model.volume)} · ≈ ${model.kcal} kcal",
                )
                v.setTextViewText(R.id.widget_btn_primary, "Ouvrir la séance")
            }
            is WidgetModel.Idle -> {
                v.setTextViewText(R.id.widget_label, "Cette semaine")
                v.setTextViewText(R.id.widget_big, "${model.weekSessions} séance(s) · ≈ ${model.weekKcal} kcal")
                v.setTextViewText(
                    R.id.widget_detail,
                    model.lastSession?.let { "Dernière séance : ${formatLongDate(it)}" } ?: "Aucune séance pour l'instant",
                )
                v.setTextViewText(R.id.widget_btn_primary, "▶ Démarrer une séance")
                v.setOnClickPendingIntent(R.id.widget_btn_primary, startSession(context))
            }
        }
        return v
    }
}
