package com.gymtracker.timer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.gymtracker.GymApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Service au premier plan : garde le minuteur de repos visible dans les notifications. */
class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timer = (application as GymApp).restTimer
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RUNNING,
            Notifications.buildRunning(this, timer.state.value),
            type,
        )
        if (observer == null) {
            observer = scope.launch {
                timer.state
                    .map { Triple(it.running, it.endsAtWallClock, it.label) }
                    .distinctUntilChanged()
                    .collect {
                        val s = timer.state.value
                        if (!s.running) {
                            ServiceCompat.stopForeground(this@RestTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            runCatching {
                                NotificationManagerCompat.from(this@RestTimerService)
                                    .notify(Notifications.ID_RUNNING, Notifications.buildRunning(this@RestTimerService, s))
                            }
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
