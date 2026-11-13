package com.gymtracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymtracker.GymApp

/** Boutons −15 s / +15 s / Passer de la notification. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = (context.applicationContext as GymApp).restTimer
        when (intent.action) {
            ACTION_MINUS -> timer.adjust(-15)
            ACTION_PLUS -> timer.adjust(15)
            ACTION_SKIP -> timer.dismiss()
        }
    }

    companion object {
        fun pendingIntent(context: Context, action: String, requestCode: Int): android.app.PendingIntent =
            android.app.PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, TimerActionReceiver::class.java).setAction(action),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )

        const val ACTION_MINUS = "com.gymtracker.timer.MINUS"
        const val ACTION_PLUS = "com.gymtracker.timer.PLUS"
        const val ACTION_SKIP = "com.gymtracker.timer.SKIP"
    }
}
