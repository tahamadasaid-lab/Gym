package com.gymtracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.gymtracker.GymApp

/** Widget d'écran d'accueil. Le contenu est poussé par [WidgetRenderer] à chaque changement. */
class GymWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        (context.applicationContext as GymApp).startWidgetUpdates(forceRefresh = true)
    }

    override fun onEnabled(context: Context) {
        (context.applicationContext as GymApp).startWidgetUpdates(forceRefresh = true)
    }

    companion object {
        const val ACTION_START_SESSION = "com.gymtracker.action.START_SESSION"
    }
}
