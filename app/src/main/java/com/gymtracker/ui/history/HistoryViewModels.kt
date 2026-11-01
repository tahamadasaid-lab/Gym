package com.gymtracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.SessionSummary
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.summary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(repo: WorkoutRepository) : ViewModel() {
    /** null = chargement en cours */
    val sessions: StateFlow<List<SessionSummary>?> = repo.observeFinishedSessions()
        .map { list -> list.map { it.summary() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class SessionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: WorkoutRepository,
) : ViewModel() {
    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<SessionWithExercises?> = repo.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteSession(sessionId)
        onDone()
    }
}
