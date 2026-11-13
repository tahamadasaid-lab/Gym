package com.gymtracker.ui.workout

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.Effort
import com.gymtracker.data.Exercise
import com.gymtracker.data.efforts
import com.gymtracker.data.ProgramWithExercises
import com.gymtracker.data.SessionExerciseWithSets
import com.gymtracker.data.SessionSummary
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.summary
import com.gymtracker.data.supersetBlocks
import com.gymtracker.timer.RestTimer
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class SetInput(val weight: String, val reps: String)

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data class NoSession(
        val sessionsThisWeek: Int,
        val totalSessions: Int,
        val weekVolume: Double,
        val weekEfforts: List<Effort>,
        val recent: List<SessionSummary>,
        val programs: List<ProgramWithExercises>,
    ) : WorkoutUiState
    data class Active(val session: SessionWithExercises) : WorkoutUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val repo: WorkoutRepository,
    val restTimer: RestTimer,
) : ViewModel() {

    private val activeDetail = repo.observeActiveSession()
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeSession(id) }

    val uiState: StateFlow<WorkoutUiState> =
        combine(activeDetail, repo.observeFinishedSessions(), repo.observePrograms()) { active, finished, programs ->
            if (active != null) {
                WorkoutUiState.Active(active)
            } else {
                val weekStart = startOfWeek()
                val week = finished.filter { it.session.startedAt >= weekStart }
                WorkoutUiState.NoSession(
                    sessionsThisWeek = week.size,
                    totalSessions = finished.size,
                    weekVolume = week.sumOf { it.summary().volume },
                    weekEfforts = week.flatMap { it.efforts() },
                    recent = finished.take(3).map { it.summary() },
                    programs = programs,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutUiState.Loading)

    val exercises: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Séries de la séance précédente, par id de SessionExercise. */
    private val _previous = MutableStateFlow<Map<Long, List<WorkoutSet>>>(emptyMap())
    val previous: StateFlow<Map<Long, List<WorkoutSet>>> = _previous.asStateFlow()

    /** Valeurs saisies (poids / reps) pour chaque exercice en cours. */
    val inputs = mutableStateMapOf<Long, SetInput>()

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (state !is WorkoutUiState.Active) return@collect
                state.session.exercises.forEach { item ->
                    val key = item.sessionExercise.id
                    if (!_previous.value.containsKey(key)) {
                        val prev = repo.previousSets(item.exercise.id, state.session.session.id)
                        _previous.update { it + (key to prev) }
                    }
                }
            }
        }
    }

    fun inputFor(item: SessionExerciseWithSets, previousSets: List<WorkoutSet>): SetInput {
        inputs[item.sessionExercise.id]?.let { return it }
        val sets = item.orderedSets
        val reference = sets.lastOrNull()
            ?: previousSets.getOrNull(sets.size)
            ?: previousSets.lastOrNull()
        return if (reference != null) SetInput(formatWeight(reference.weightKg), reference.reps.toString())
        else SetInput("", "")
    }

    fun updateInput(sessionExerciseId: Long, input: SetInput) {
        inputs[sessionExerciseId] = input
    }

    fun startSession() = viewModelScope.launch { repo.startSession() }

    fun startProgram(programId: Long) = viewModelScope.launch { repo.startSessionFromProgram(programId) }

    fun saveAsProgram(session: SessionWithExercises, name: String) = viewModelScope.launch {
        repo.saveSessionAsProgram(session, name)
    }

    /** Termine la séance ; si elle est vide, elle est simplement supprimée. */
    fun finishSession(session: SessionWithExercises) = viewModelScope.launch {
        if (session.exercises.all { it.sets.isEmpty() }) repo.deleteSession(session.session.id)
        else repo.finishSession(session.session.id)
        restTimer.dismiss()
        inputs.clear()
        _previous.value = emptyMap()
    }

    fun cancelSession(session: SessionWithExercises) = viewModelScope.launch {
        repo.deleteSession(session.session.id)
        restTimer.dismiss()
        inputs.clear()
        _previous.value = emptyMap()
    }

    fun addExercise(sessionId: Long, exerciseId: Long) = viewModelScope.launch {
        repo.addExerciseToSession(sessionId, exerciseId)
    }

    fun createAndAddExercise(sessionId: Long, name: String) = viewModelScope.launch {
        val id = repo.findOrCreateExercise(name)
        repo.addExerciseToSession(sessionId, id)
    }

    fun removeExercise(item: SessionExerciseWithSets) = viewModelScope.launch {
        repo.removeSessionExercise(item.sessionExercise.id)
        inputs.remove(item.sessionExercise.id)
    }

    /**
     * Enregistre une série. Le minuteur de repos démarre, sauf :
     * - série dégressive (enchaînée sans repos ; le poids suivant est proposé à −20 %) ;
     * - exercice d'une supersérie qui n'est pas le dernier du bloc (on enchaîne avec le suivant).
     * Retourne false si la saisie est invalide.
     */
    fun logSet(session: SessionWithExercises, item: SessionExerciseWithSets, input: SetInput, isDropSet: Boolean): Boolean {
        val reps = input.reps.toIntOrNull()?.takeIf { it > 0 } ?: return false
        val weight = if (input.weight.isBlank()) 0.0 else parseWeight(input.weight) ?: return false
        inputs[item.sessionExercise.id] = if (isDropSet) {
            input.copy(weight = formatWeight((Math.round(weight * 0.8 / 2.5) * 2.5).coerceAtLeast(0.0)))
        } else input
        viewModelScope.launch {
            repo.logSet(session.session.id, item.sessionExercise.id, reps, weight, isDropSet)
        }
        val block = supersetBlockOf(session, item)
        val chainNext = block.size > 1 && block.last().sessionExercise.id != item.sessionExercise.id
        if (!isDropSet && !chainNext) {
            restTimer.start(item.exercise.defaultRestSeconds, nextLabel(session, item, block))
        }
        return true
    }

    /** Bloc (supersérie ou exercice seul) contenant cet exercice. */
    fun supersetBlockOf(session: SessionWithExercises, item: SessionExerciseWithSets): List<SessionExerciseWithSets> =
        session.orderedExercises
            .supersetBlocks { it.sessionExercise.supersetGroup }
            .firstOrNull { b -> b.any { it.sessionExercise.id == item.sessionExercise.id } }
            ?: listOf(item)

    /** Pour une supersérie, l'annonce de fin de repos cite le premier exercice du bloc. */
    private fun nextLabel(session: SessionWithExercises, item: SessionExerciseWithSets, block: List<SessionExerciseWithSets>): String =
        if (block.size > 1) block.first().exercise.name else item.exercise.name

    /** Lie cet exercice au suivant en supersérie. */
    fun linkWithNext(session: SessionWithExercises, item: SessionExerciseWithSets) = viewModelScope.launch {
        val ordered = session.orderedExercises
        val idx = ordered.indexOfFirst { it.sessionExercise.id == item.sessionExercise.id }
        val next = ordered.getOrNull(idx + 1) ?: return@launch
        val group = item.sessionExercise.supersetGroup ?: item.sessionExercise.id
        val nextGroup = next.sessionExercise.supersetGroup
        val ids = mutableListOf(item.sessionExercise.id, next.sessionExercise.id)
        if (nextGroup != null && nextGroup != group) {
            ids += ordered.filter { it.sessionExercise.supersetGroup == nextGroup }.map { it.sessionExercise.id }
        }
        repo.setSuperset(ids, group)
    }

    fun unlinkSuperset(item: SessionExerciseWithSets) = viewModelScope.launch {
        repo.setSuperset(listOf(item.sessionExercise.id), null)
    }

    fun deleteSet(set: WorkoutSet) = viewModelScope.launch { repo.deleteSet(set) }

    fun updateSet(set: WorkoutSet, weightKg: Double, reps: Int) = viewModelScope.launch {
        repo.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    fun updateRest(exerciseId: Long, seconds: Int) = viewModelScope.launch { repo.updateRest(exerciseId, seconds) }

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
