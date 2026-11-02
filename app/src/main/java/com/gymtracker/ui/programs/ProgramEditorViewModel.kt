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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgramItem(val exerciseId: Long, val targetSets: Int)

class ProgramEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: WorkoutRepository,
) : ViewModel() {
    private val programId: Long? = savedStateHandle.get<Long>("programId")?.takeIf { it > 0 }
    val isNew: Boolean = programId == null

    var name by mutableStateOf("")
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
                    items.clear()
                    items.addAll(p.orderedItems.map { ProgramItem(it.exercise.id, it.item.targetSets) })
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

    fun remove(index: Int) {
        items.removeAt(index)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        repo.saveProgram(programId, name, items.map { it.exerciseId to it.targetSets })
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        programId?.let { repo.deleteProgram(it) }
        onDone()
    }
}
