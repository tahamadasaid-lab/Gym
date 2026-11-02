package com.gymtracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.BodyWeight
import com.gymtracker.data.Exercise
import com.gymtracker.data.ExerciseSessionStat
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.toSessionStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Metric(val label: String, val unit: String) {
    MaxWeight("Poids max", "kg"),
    OneRepMax("1RM estimé", "kg"),
    Volume("Volume", "kg"),
    MaxReps("Reps max", "reps"),
}

data class ProgressUiState(
    val trainedExercises: List<Exercise> = emptyList(),
    val selected: Exercise? = null,
    val stats: List<ExerciseSessionStat> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val repo: WorkoutRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    val metric = MutableStateFlow(Metric.MaxWeight)

    private val trained = combine(repo.observeExercises(), repo.observeTrainedExerciseIds()) { all, ids ->
        val set = ids.toSet()
        all.filter { it.id in set }
    }

    private val selected = combine(trained, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    private val stats = selected.map { it?.id }.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeExerciseHistory(id).map { it.toSessionStats() }
    }

    val uiState: StateFlow<ProgressUiState> = combine(trained, selected, stats) { t, s, st ->
        ProgressUiState(trainedExercises = t, selected = s, stats = st, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun select(exercise: Exercise) {
        selectedId.value = exercise.id
    }

    fun selectMetric(m: Metric) {
        metric.value = m
    }

    val tab = MutableStateFlow(0)

    val bodyWeights: StateFlow<List<BodyWeight>> =
        repo.observeBodyWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addBodyWeight(kg: Double) = viewModelScope.launch { repo.addBodyWeight(kg) }
    fun deleteBodyWeight(entry: BodyWeight) = viewModelScope.launch { repo.deleteBodyWeight(entry) }
}

fun ExerciseSessionStat.valueOf(metric: Metric): Double = when (metric) {
    Metric.MaxWeight -> maxWeight
    Metric.OneRepMax -> bestOneRepMax
    Metric.Volume -> volume
    Metric.MaxReps -> maxReps.toDouble()
}
