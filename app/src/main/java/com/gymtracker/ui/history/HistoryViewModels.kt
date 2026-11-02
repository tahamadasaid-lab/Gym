package com.gymtracker.ui.history

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.SessionSummary
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.summary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class HistoryViewModel(private val repo: WorkoutRepository) : ViewModel() {
    /** null = chargement en cours */
    val sessions: StateFlow<List<SessionSummary>?> = repo.observeFinishedSessions()
        .map { list -> list.map { it.summary() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val month = MutableStateFlow(YearMonth.now())
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    fun changeMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
        selectedDay.value = null
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = if (selectedDay.value == day) null else day
    }

    fun exportCsv(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching {
            val count = resolver.openOutputStream(uri)?.use { repo.exportCsv(it) } ?: error("Fichier inaccessible")
            "$count séance(s) exportée(s)"
        }.getOrElse { "Échec de l'export : ${it.message}" }
        onResult(msg)
    }

    fun importCsv(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching {
            val r = resolver.openInputStream(uri)?.use { repo.importCsv(it) } ?: error("Fichier inaccessible")
            buildString {
                append("${r.sessions} séance(s) importée(s)")
                if (r.bodyWeights > 0) append(", ${r.bodyWeights} pesée(s)")
                if (r.skipped > 0) append(" · ${r.skipped} déjà présente(s)")
            }
        }.getOrElse { "Échec de l'import : ${it.message}" }
        onResult(msg)
    }
}

class SessionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: WorkoutRepository,
) : ViewModel() {
    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<SessionWithExercises?> = repo.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSet(set: WorkoutSet, weightKg: Double, reps: Int) = viewModelScope.launch {
        repo.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    fun deleteSet(set: WorkoutSet) = viewModelScope.launch { repo.deleteSet(set) }

    fun addSet(sessionExerciseId: Long, weightKg: Double, reps: Int) = viewModelScope.launch {
        repo.addSetAfterwards(sessionExerciseId, reps, weightKg)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteSession(sessionId)
        onDone()
    }
}
