package com.gymtracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.MuscleGroup
import com.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryRow(val group: MuscleGroup, val exerciseCount: Int)

class CategoriesViewModel(private val repo: WorkoutRepository) : ViewModel() {
    val rows: StateFlow<List<CategoryRow>?> =
        combine(repo.observeCategories(), repo.observeExercises()) { groups, exercises ->
            val counts = exercises.groupingBy { it.muscleGroup.lowercase() }.eachCount()
            groups.map { CategoryRow(it, counts[it.name.lowercase()] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** onResult(true) si enregistré, false si le nom existe déjà. */
    fun save(group: MuscleGroup, previousName: String?, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repo.saveCategory(group, previousName) != null)
    }

    fun delete(group: MuscleGroup) = viewModelScope.launch { repo.deleteCategory(group) }
}
