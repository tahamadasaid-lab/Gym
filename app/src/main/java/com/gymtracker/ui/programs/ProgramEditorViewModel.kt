package com.gymtracker.ui.programs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.Exercise
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.currentWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgramItem(
    val exerciseId: Long,
    val targetSets: Int,
    val supersetGroup: Long? = null,
    val targetReps: Int? = null,
    val startWeight: Double? = null,
    val weeklyIncrement: Double? = null,
)

class ProgramEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: WorkoutRepository,
) : ViewModel() {
    private val programId: Long? = savedStateHandle.get<Long>("programId")?.takeIf { it > 0 }
    val isNew: Boolean = programId == null

    var name by mutableStateOf("")
    /** Nombre de semaines du cycle ; null = programme classique. */
    var weeks by mutableStateOf<Int?>(null)
    var deloadLastWeek by mutableStateOf(false)
    var restartCycle by mutableStateOf(false)
    var currentWeek by mutableStateOf<Int?>(null)
        private set
    val items = mutableStateListOf<ProgramItem>()
    var loaded by mutableStateOf(isNew)
        private set

    val exercises: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (programId != null) {
            viewModelScope.launch {
                repo.getProgram(programId)?.let { p ->
                    name = p.program.name
                    weeks = p.program.weeks
                    deloadLastWeek = p.program.deloadLastWeek
                    currentWeek = p.program.currentWeek().takeIf { p.program.cycleStartedAt != null }
                    items.clear()
                    items.addAll(
                        p.orderedItems.map {
                            ProgramItem(
                                it.exercise.id, it.item.targetSets, it.item.supersetGroup,
                                it.item.targetReps, it.item.startWeight, it.item.weeklyIncrement,
                            )
                        }
                    )
                }
                loaded = true
            }
        }
    }

    fun add(exerciseId: Long) {
        items.add(ProgramItem(exerciseId, 3))
    }

    fun createAndAdd(exerciseName: String) = viewModelScope.launch {
        add(repo.findOrCreateExercise(exerciseName))
    }

    fun setSets(index: Int, sets: Int) {
        items[index] = items[index].copy(targetSets = sets.coerceIn(1, 20))
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in items.indices) return
        val item = items.removeAt(index)
        items.add(target, item)
    }

    /** Vrai si l'exercice [index] est enchaîné en supersérie avec le suivant. */
    fun isLinked(index: Int): Boolean {
        val a = items.getOrNull(index)?.supersetGroup ?: return false
        return items.getOrNull(index + 1)?.supersetGroup == a
    }

    private fun newGroupId(): Long = (items.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0L) + 1

    /** Lie / délie l'exercice [index] et le suivant en supersérie. */
    fun toggleLink(index: Int) {
        if (index + 1 !in items.indices) return
        if (isLinked(index)) {
            val g = items[index].supersetGroup
            // Partie après la coupure
            val tail = mutableListOf<Int>()
            var j = index + 1
            while (j < items.size && items[j].supersetGroup == g) { tail += j; j++ }
            val tailGroup = if (tail.size > 1) newGroupId() else null
            tail.forEach { items[it] = items[it].copy(supersetGroup = tailGroup) }
            // Partie avant la coupure : seule, elle n'est plus une supersérie
            if (index == 0 || items[index - 1].supersetGroup != g) items[index] = items[index].copy(supersetGroup = null)
        } else {
            val g = items[index].supersetGroup ?: newGroupId()
            items[index] = items[index].copy(supersetGroup = g)
            val old = items[index + 1].supersetGroup
            var j = index + 1
            while (j < items.size && (j == index + 1 || (old != null && items[j].supersetGroup == old))) {
                items[j] = items[j].copy(supersetGroup = g)
                j++
            }
        }
    }

    fun updateItem(index: Int, transform: (ProgramItem) -> ProgramItem) {
        items[index] = transform(items[index])
    }

    /** Séances terminées proposées pour l'import. */
    val sessions = repo.observeFinishedSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ajoute les exercices des séances choisies (séries, répétitions et charge de la meilleure série). */
    fun importSessions(sessionIds: List<Long>) = viewModelScope.launch {
        val chosen = sessionIds.mapNotNull { id -> sessions.value.firstOrNull { it.session.id == id } }
            .sortedBy { it.session.startedAt }
        chosen.forEach { s ->
            s.orderedExercises.forEach { e ->
                val work = e.sets.filter { it.isWork && !it.isDropSet }
                val top = work.maxWithOrNull(compareBy<com.gymtracker.data.WorkoutSet> { it.weightKg }.thenBy { it.reps })
                val item = ProgramItem(
                    exerciseId = e.exercise.id,
                    targetSets = work.size.takeIf { it > 0 } ?: 3,
                    targetReps = top?.reps,
                    startWeight = top?.weightKg?.takeIf { it > 0 },
                )
                val idx = items.indexOfFirst { it.exerciseId == e.exercise.id }
                if (idx >= 0) items[idx] = item.copy(supersetGroup = items[idx].supersetGroup) else items.add(item)
            }
        }
        if (name.isBlank() && chosen.size == 1) {
            name = chosen.single().session.programLabel?.substringBefore(" · ") ?: "Ma séance"
        }
    }

    fun remove(index: Int) {
        items.removeAt(index)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        repo.saveProgramEntries(
            programId,
            name,
            items.map {
                WorkoutRepository.ProgramEntry(
                    it.exerciseId, it.targetSets, it.supersetGroup, it.targetReps, it.startWeight,
                    it.weeklyIncrement.takeIf { weeks != null },
                )
            },
            weeks = weeks,
            deloadLastWeek = deloadLastWeek && weeks != null,
            restartCycle = restartCycle,
        )
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        programId?.let { repo.deleteProgram(it) }
        onDone()
    }
}
