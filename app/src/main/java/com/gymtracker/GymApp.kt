package com.gymtracker

import android.app.Application
import com.gymtracker.data.AppDatabase
import com.gymtracker.data.ImageStore
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.settings.SettingsStore
import com.gymtracker.timer.Notifications
import com.gymtracker.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GymApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val repository by lazy { WorkoutRepository(AppDatabase.build(this)) }
    val settings by lazy { SettingsStore(this) }
    val restTimer by lazy { RestTimer(this, appScope, settings) }
    val imageStore by lazy { ImageStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
