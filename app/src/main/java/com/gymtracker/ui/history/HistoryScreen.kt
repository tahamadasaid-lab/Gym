@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.estimatedOneRepMax
import com.gymtracker.data.summary
import com.gymtracker.data.volume
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.EmptyState
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.StatTile
import com.gymtracker.util.formatDuration
import com.gymtracker.util.formatDurationHuman
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatTime
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Historique") { padding ->
        val list = sessions
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                Icons.Filled.History,
                "Aucune séance terminée",
                "Tes séances apparaîtront ici une fois terminées.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { s ->
                    Card(onClick = { onOpenSession(s.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(formatLongDate(s.startedAt), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${formatTime(s.startedAt)} · ${formatDurationHuman(s.durationSeconds)} · ${s.setCount} séries · ${formatVolume(s.volume)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (s.exerciseNames.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.exerciseNames.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    vm: SessionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val s = session

    ScreenScaffold(
        title = s?.let { formatLongDate(it.session.startedAt) } ?: "Séance",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        },
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer la séance") }
        },
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val summary = s.summary()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Durée", formatDurationHuman(summary.durationSeconds), Modifier.weight(1f))
                    StatTile("Séries", "${summary.setCount}", Modifier.weight(1f))
                    StatTile("Volume", formatVolume(summary.volume), Modifier.weight(1.3f))
                }
            }
            summary.averageRestSeconds?.let { avg ->
                item { Text("Repos moyen entre les séries : ${formatDuration(avg.toLong())}", style = MaterialTheme.typography.bodyMedium) }
            }
            items(s.orderedExercises.filter { it.sets.isNotEmpty() }, key = { it.sessionExercise.id }) { ex ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Volume : ${formatVolume(ex.sets.sumOf { it.volume })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        ex.orderedSets.forEachIndexed { i, set ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${formatWeight(set.weightKg)} kg × ${set.reps}",
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "1RM ≈ ${formatWeight(Math.round(estimatedOneRepMax(set.weightKg, set.reps) * 2) / 2.0)}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    set.restSeconds?.let { "⏱ ${formatDuration(it.toLong())}" } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer la séance ?",
            message = "Cette séance et toutes ses séries seront définitivement supprimées.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
        )
    }
}
