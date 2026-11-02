package com.gymtracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gymtracker.GymApp
import com.gymtracker.ui.exercises.ExercisesViewModel
import com.gymtracker.ui.history.HistoryViewModel
import com.gymtracker.ui.history.SessionDetailViewModel
import com.gymtracker.ui.programs.ProgramEditorViewModel
import com.gymtracker.ui.progress.ProgressViewModel
import com.gymtracker.ui.workout.WorkoutViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { WorkoutViewModel(app().repository, app().restTimer) }
        initializer { HistoryViewModel(app().repository) }
        initializer { SessionDetailViewModel(createSavedStateHandle(), app().repository) }
        initializer { ProgramEditorViewModel(createSavedStateHandle(), app().repository) }
        initializer { ProgressViewModel(app().repository) }
        initializer { ExercisesViewModel(app().repository, app().imageStore) }
    }
}

private fun CreationExtras.app(): GymApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymApp
