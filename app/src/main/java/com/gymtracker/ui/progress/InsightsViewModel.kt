package com.gymtracker.ui.progress

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.BodyWeight
import com.gymtracker.data.Exercise
import com.gymtracker.data.ExerciseRecords
import com.gymtracker.data.Goal
import com.gymtracker.data.GoalProgress
import com.gymtracker.data.GoalType
import com.gymtracker.data.ImageStore
import com.gymtracker.data.Measurement
import com.gymtracker.data.PeriodStats
import com.gymtracker.data.ProgressPhoto
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.computeRecords
import com.gymtracker.data.goalProgress
import com.gymtracker.data.periodStats
import com.gymtracker.data.sessionsPerWeek
import com.gymtracker.data.startMillis
import com.gymtracker.data.weekStart
import com.gymtracker.data.weekStreak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ReviewMode { WEEK, MONTH }

data class ReviewState(
    val mode: ReviewMode,
    val start: LocalDate,
    val end: LocalDate,
    val stats: PeriodStats,
    val previous: PeriodStats,
    val streakWeeks: Int,
    val perWeek: List<Pair<LocalDate, Int>>,
)

data class RecordItem(val exercise: Exercise, val records: ExerciseRecords)

class InsightsViewModel(
    private val repo: WorkoutRepository,
    private val images: ImageStore,
) : ViewModel() {

    private val exercises = repo.observeExercises().map { list -> list.associateBy { it.id } }
    private val workSets = repo.observeWorkSets()
    private val finished = repo.observeFinishedSessions()
    val bodyWeights: StateFlow<List<BodyWeight>> =
        repo.observeBodyWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allExercises: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------------ Records
    val records: StateFlow<List<RecordItem>?> = combine(workSets, exercises) { rows, ex ->
        computeRecords(rows).mapNotNull { (id, r) -> ex[id]?.let { RecordItem(it, r) } }
            .sortedByDescending { it.records.lastDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ------------------------------------------------------------------ Bilan
    val reviewMode = MutableStateFlow(ReviewMode.WEEK)
    val reviewAnchor = MutableStateFlow(LocalDate.now())

    val review: StateFlow<ReviewState?> = combine(finished, reviewMode, reviewAnchor) { sessions, mode, anchor ->
        val (start, end) = range(mode, anchor)
        val (pStart, pEnd) = range(mode, if (mode == ReviewMode.WEEK) anchor.minusWeeks(1) else anchor.minusMonths(1))
        ReviewState(
            mode = mode,
            start = start,
            end = end,
            stats = periodStats(sessions.inRange(start, end)),
            previous = periodStats(sessions.inRange(pStart, pEnd)),
            streakWeeks = weekStreak(sessions.map { it.session.startedAt }),
            perWeek = sessionsPerWeek(sessions.map { it.session.startedAt }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun range(mode: ReviewMode, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        ReviewMode.WEEK -> anchor.weekStart().let { it to it.plusDays(6) }
        ReviewMode.MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
    }

    private fun List<SessionWithExercises>.inRange(start: LocalDate, end: LocalDate): List<SessionWithExercises> {
        val from = start.startMillis()
        val to = end.plusDays(1).startMillis()
        return filter { it.session.startedAt in from until to }
    }

    fun shiftReview(delta: Long) {
        reviewAnchor.value = if (reviewMode.value == ReviewMode.WEEK) reviewAnchor.value.plusWeeks(delta)
        else reviewAnchor.value.plusMonths(delta)
    }

    fun setReviewMode(mode: ReviewMode) {
        reviewMode.value = mode
        reviewAnchor.value = LocalDate.now()
    }

    // ------------------------------------------------------------------ Objectifs
    val goals: StateFlow<List<GoalProgress>?> =
        combine(repo.observeGoals(), exercises, workSets, repo.observeBodyWeights()) { goals, ex, rows, weights ->
            goals.map { g -> goalProgress(g, g.exerciseId?.let { ex[it]?.name }, rows, weights) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Mémorise la date à laquelle un objectif est atteint.
        viewModelScope.launch {
            goals.collect { list ->
                list.orEmpty().filter { it.achieved && it.goal.achievedAt == null }.forEach {
                    repo.updateGoal(it.goal.copy(achievedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    /** Point de départ : record actuel de l'exercice, ou dernière pesée. */
    fun currentValue(type: GoalType, exerciseId: Long?): Double? = when (type) {
        GoalType.EXERCISE_WEIGHT -> records.value?.firstOrNull { it.exercise.id == exerciseId }?.records?.bestWeight
        GoalType.BODY_WEIGHT -> bodyWeights.value.lastOrNull()?.weightKg
    }

    fun addGoal(type: GoalType, exerciseId: Long?, target: Double, deadline: Long?) = viewModelScope.launch {
        val start = currentValue(type, exerciseId) ?: 0.0
        repo.addGoal(
            Goal(
                type = type.name,
                exerciseId = if (type == GoalType.EXERCISE_WEIGHT) exerciseId else null,
                targetValue = target,
                startValue = start,
                createdAt = System.currentTimeMillis(),
                deadline = deadline,
            )
        )
    }

    fun deleteGoal(goal: Goal) = viewModelScope.launch { repo.deleteGoal(goal) }

    // ------------------------------------------------------------------ Mensurations et photos
    val measurements: StateFlow<List<Measurement>> =
        repo.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val photos: StateFlow<List<ProgressPhoto>> =
        repo.observePhotos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMeasurement(type: String, valueCm: Double) = viewModelScope.launch { repo.addMeasurement(type, valueCm) }
    fun deleteMeasurement(m: Measurement) = viewModelScope.launch { repo.deleteMeasurement(m) }

    fun addPhoto(uri: Uri) = viewModelScope.launch {
        images.saveProgressPhoto(uri)?.let { repo.addPhoto(it) }
    }

    fun deletePhoto(p: ProgressPhoto) = viewModelScope.launch {
        images.delete(p.path)
        repo.deletePhoto(p)
    }
}
