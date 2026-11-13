package com.gymtracker

import android.app.Application
import com.gymtracker.backup.BackupManager
import com.gymtracker.data.AppDatabase
import com.gymtracker.data.CalorieCalculator
import com.gymtracker.data.ImageStore
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.efforts
import com.gymtracker.data.volume
import com.gymtracker.settings.SettingsStore
import com.gymtracker.timer.Notifications
import com.gymtracker.timer.RestTimer
import com.gymtracker.util.startOfWeekMillis
import com.gymtracker.widget.WidgetModel
import com.gymtracker.widget.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GymApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.build(this) }
    val repository by lazy { WorkoutRepository(database) }
    val backup by lazy { BackupManager(this, database, settings) }
    val settings by lazy { SettingsStore(this) }
    val restTimer by lazy { RestTimer(this, appScope, settings) }
    val imageStore by lazy { ImageStore(this) }

    private var widgetJob: Job? = null
    private var lastWidgetModel: WidgetModel? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (WidgetRenderer.hasWidgets(this)) startWidgetUpdates()
    }

    /** Met le widget à jour à chaque changement (séance, séries, minuteur). Sans effet s'il n'y a pas de widget. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startWidgetUpdates(forceRefresh: Boolean = false) {
        if (forceRefresh) lastWidgetModel?.let { WidgetRenderer.push(this, it) }
        if (widgetJob != null) return
        val active = repository.observeActiveSession()
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeSession(id) }
        // On ignore le décompte seconde par seconde : le widget a son propre chronomètre.
        val timer = restTimer.state
            .map { Triple(it.running, it.endsAtWallClock, it.visible to it.label) }
            .distinctUntilChanged()
        widgetJob = appScope.launch {
            combine(
                active,
                repository.observeFinishedSessions(),
                timer,
                repository.observeCategories(),
                repository.observeBodyWeights(),
            ) { session, finished, t, categories, weights ->
                val calc = CalorieCalculator.of(weights.lastOrNull()?.weightKg ?: settings.settings.value.profileWeightKg, categories)
                val (running, endsAt, visibleLabel) = t
                val (visible, label) = visibleLabel
                val sessionSets = session?.exercises?.sumOf { it.sets.size } ?: 0
                when {
                    running -> WidgetModel.Rest(endsAt, label, sessionSets)
                    visible -> WidgetModel.RestDone(label)
                    session != null -> WidgetModel.Session(
                        startedAt = session.session.startedAt,
                        sets = sessionSets,
                        volume = session.exercises.flatMap { it.sets }.sumOf { it.volume },
                        kcal = calc.kcal(session.efforts()).toInt(),
                    )
                    else -> {
                        val week = finished.filter { it.session.startedAt >= startOfWeekMillis() }
                        WidgetModel.Idle(
                            weekSessions = week.size,
                            weekKcal = calc.kcal(week.flatMap { it.efforts() }).toInt(),
                            lastSession = finished.firstOrNull()?.session?.startedAt,
                        )
                    }
                }
            }
                .distinctUntilChanged()
                .conflate()
                .collect { model ->
                    lastWidgetModel = model
                    WidgetRenderer.push(this@GymApp, model)
                }
        }
    }
}
