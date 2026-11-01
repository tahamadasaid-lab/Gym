package com.gymtracker

import android.app.Application
import com.gymtracker.data.AppDatabase
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GymApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val repository by lazy { WorkoutRepository(AppDatabase.build(this).workoutDao()) }
    val restTimer by lazy { RestTimer(this, appScope) }
}
