package com.gymtracker.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.Exercise
import com.gymtracker.data.ImageStore
import com.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Changement de photo demandé dans le formulaire. */
sealed interface ImageChange {
    data object None : ImageChange
    data object Remove : ImageChange
    data class Replace(val uri: Uri) : ImageChange
}

class ExercisesViewModel(
    private val repo: WorkoutRepository,
    private val images: ImageStore,
) : ViewModel() {
    val exercises: StateFlow<List<Exercise>?> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** onResult(true) si enregistré, false si le nom existe déjà. */
    fun save(exercise: Exercise, imageChange: ImageChange, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val id = repo.saveExercise(exercise)
        if (id == null) {
            onResult(false)
            return@launch
        }
        when (imageChange) {
            ImageChange.None -> Unit
            ImageChange.Remove -> {
                images.delete(exercise.imageUri)
                repo.updateExerciseImage(id, null)
            }
            is ImageChange.Replace -> {
                val path = images.saveExerciseImage(id, imageChange.uri)
                if (path != null) {
                    images.delete(exercise.imageUri)
                    repo.updateExerciseImage(id, path)
                }
            }
        }
        onResult(true)
    }

    fun delete(exercise: Exercise) = viewModelScope.launch {
        images.delete(exercise.imageUri)
        repo.deleteExercise(exercise)
    }
}
