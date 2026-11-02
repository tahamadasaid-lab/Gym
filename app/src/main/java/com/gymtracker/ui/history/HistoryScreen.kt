@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.SessionExerciseWithSets
import com.gymtracker.data.SessionSummary
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.estimatedOneRepMax
import com.gymtracker.data.summary
import com.gymtracker.data.volume
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.EditSetDialog
import com.gymtracker.ui.components.EmptyState
import com.gymtracker.ui.components.ExerciseAvatar
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.StatTile
import com.gymtracker.util.formatDuration
import com.gymtracker.util.formatDurationHuman
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatTime
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(context.contentResolver, uri, notify)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(context.contentResolver, uri, notify)
    }

    ScreenScaffold(
        title = "Historique",
        snackbarHostState = snackbar,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Plus d'options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Exporter (CSV)") },
                        leadingIcon = { Icon(Icons.Filled.Upload, null) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("gymtracker-${LocalDate.now()}.csv")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Importer (CSV)") },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                        },
                    )
                }
            }
        },
    ) { padding ->
        val list = sessions
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                Icons.Filled.History,
                "Aucune séance terminée",
                "Tes séances apparaîtront ici une fois terminées. Tu peux aussi importer un fichier CSV depuis le menu ⋮.",
                Modifier.padding(padding),
            )
            else -> {
                val trainingDays = list.map { it.startedAt.toLocalDate() }.toSet()
                val shown = list.filter {
                    val d = it.startedAt.toLocalDate()
                    if (selectedDay != null) d == selectedDay else YearMonth.from(d) == month
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        MonthCalendar(
                            month = month,
                            trainingDays = trainingDays,
                            selected = selectedDay,
                            onSelect = vm::selectDay,
                            onChangeMonth = vm::changeMonth,
                        )
                    }
                    item {
                        val title = selectedDay?.let { formatLongDate(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                            ?: "Ce mois-ci"
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${shown.size} séance(s) · ${shown.sumOf { it.setCount }} séries · ${formatVolume(shown.sumOf { it.volume })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "Aucune séance sur cette période.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(shown, key = { it.id }) { s -> SessionCard(s, onClick = { onOpenSession(s.id) }) }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: SessionSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Voir le détail")
        }
    }
}

// =====================================================================================  Détail

private sealed interface SetEdit {
    data class Existing(val set: WorkoutSet, val index: Int) : SetEdit
    data class New(val item: SessionExerciseWithSets) : SetEdit
}

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    vm: SessionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<SetEdit?>(null) }
    val s = session

    ScreenScaffold(
        title = s?.let { formatLongDate(it.session.startedAt) } ?: "Séance",
        subtitle = s?.let { "Début à ${formatTime(it.session.startedAt)}" },
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
                item {
                    Text(
                        "Repos moyen entre les séries : ${formatDuration(avg.toLong())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.orderedExercises, key = { it.sessionExercise.id }) { ex ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseAvatar(ex.exercise, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${ex.sets.size} séries · ${formatVolume(ex.sets.sumOf { it.volume })}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        ex.orderedSets.forEachIndexed { i, set ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { edit = SetEdit.Existing(set, i) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        TextButton(onClick = { edit = SetEdit.New(ex) }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ajouter une série")
                        }
                    }
                }
            }
            item {
                Text(
                    "Touche une série pour la corriger ou la supprimer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (val e = edit) {
        is SetEdit.Existing -> EditSetDialog(
            title = "Modifier la série ${e.index + 1}",
            initialWeight = formatWeight(e.set.weightKg),
            initialReps = e.set.reps.toString(),
            onDismiss = { edit = null },
            onSave = { w, r -> vm.updateSet(e.set, w, r); edit = null },
            onDelete = { vm.deleteSet(e.set); edit = null },
        )
        is SetEdit.New -> {
            val last = e.item.orderedSets.lastOrNull()
            EditSetDialog(
                title = "Nouvelle série · ${e.item.exercise.name}",
                initialWeight = last?.let { formatWeight(it.weightKg) } ?: "",
                initialReps = last?.reps?.toString() ?: "",
                onDismiss = { edit = null },
                onSave = { w, r -> vm.addSet(e.item.sessionExercise.id, w, r); edit = null },
            )
        }
        null -> Unit
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
