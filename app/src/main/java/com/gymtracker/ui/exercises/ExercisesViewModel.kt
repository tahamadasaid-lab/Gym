package com.gymtracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.Exercise
import com.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel(private val repo: WorkoutRepository) : ViewModel() {
    val exercises: StateFlow<List<Exercise>?> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** onResult(true) si enregistré, false si le nom existe déjà. */
    fun save(exercise: Exercise, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repo.saveExercise(exercise))
    }

    fun delete(exercise: Exercise) = viewModelScope.launch { repo.deleteExercise(exercise) }
}
