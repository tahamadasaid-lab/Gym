@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.SessionExerciseWithSets
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.volume
import com.gymtracker.timer.RestTimer
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.ui.components.RestPickerDialog
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.StatTile
import com.gymtracker.util.formatDuration
import com.gymtracker.util.formatDurationHuman
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(vm: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        WorkoutUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is WorkoutUiState.NoSession -> StartSessionScreen(s, onStart = { vm.startSession() })
        is WorkoutUiState.Active -> ActiveSessionScreen(s.session, vm)
    }
}

@Composable
private fun StartSessionScreen(state: WorkoutUiState.NoSession, onStart: () -> Unit) {
    ScreenScaffold(title = "GymTracker") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Cette semaine", "${state.sessionsThisWeek} séance(s)", Modifier.weight(1f))
                StatTile("Au total", "${state.totalSessions} séance(s)", Modifier.weight(1f))
            }
            state.lastSession?.let { last ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Dernière séance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(formatLongDate(last.startedAt), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatDurationHuman(last.durationSeconds)} · ${last.setCount} séries · ${formatVolume(last.volume)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (last.exerciseNames.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                last.exerciseNames.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.FitnessCenter, null,
                Modifier.size(96.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Prêt à t'entraîner ?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Démarrer une séance", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActiveSessionScreen(session: SessionWithExercises, vm: WorkoutViewModel) {
    val timer by vm.restTimer.state.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var restEdit by remember { mutableStateOf<SessionExerciseWithSets?>(null) }
    var removeTarget by remember { mutableStateOf<SessionExerciseWithSets?>(null) }

    // Garde l'écran allumé pendant la séance.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val allSets = session.exercises.flatMap { it.sets }
    val subtitle = "${formatDuration((now - session.session.startedAt) / 1000)} · ${allSets.size} séries · ${formatVolume(allSets.sumOf { it.volume })}"

    ScreenScaffold(
        title = "Séance en cours",
        subtitle = subtitle,
        actions = {
            TextButton(onClick = { showFinish = true }) { Text("Terminer") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Plus d'options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Annuler la séance") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; showCancel = true },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (timer.visible) {
                item(key = "timer") {
                    RestTimerCard(
                        state = timer,
                        onAdjust = vm.restTimer::adjust,
                        onDismiss = vm.restTimer::dismiss,
                    )
                }
            }
            if (session.exercises.isEmpty()) {
                item(key = "hint") {
                    Text(
                        "Ajoute ton premier exercice pour commencer à enregistrer tes séries.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(session.orderedExercises, key = { it.sessionExercise.id }) { item ->
                val prev = previous[item.sessionExercise.id].orEmpty()
                ExerciseCard(
                    item = item,
                    previousSets = prev,
                    input = vm.inputFor(item, prev),
                    onInputChange = { vm.updateInput(item.sessionExercise.id, it) },
                    onLogSet = { vm.logSet(session.session.id, item, it) },
                    onDeleteSet = { vm.deleteSet(it) },
                    onEditRest = { restEdit = item },
                    onRemove = {
                        if (item.sets.isEmpty()) {
                            vm.removeExercise(item)
                        } else {
                            removeTarget = item
                        }
                    },
                )
            }
            item(key = "add") {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter un exercice")
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            exercises = exercises,
            alreadyAdded = session.exercises.map { it.exercise.id }.toSet(),
            onDismiss = { showPicker = false },
            onPick = { vm.addExercise(session.session.id, it.id); showPicker = false },
            onCreate = { vm.createAndAddExercise(session.session.id, it); showPicker = false },
        )
    }
    restEdit?.let { item ->
        RestPickerDialog(
            exerciseName = item.exercise.name,
            currentSeconds = item.exercise.defaultRestSeconds,
            onDismiss = { restEdit = null },
            onConfirm = { vm.updateRest(item.exercise.id, it); restEdit = null },
        )
    }
    removeTarget?.let { item ->
        ConfirmDialog(
            title = "Retirer l'exercice ?",
            message = "Les ${item.sets.size} série(s) de « ${item.exercise.name} » enregistrées pendant cette séance seront supprimées.",
            confirmLabel = "Retirer",
            onDismiss = { removeTarget = null },
            onConfirm = { vm.removeExercise(item); removeTarget = null },
        )
    }
    if (showFinish) {
        val empty = allSets.isEmpty()
        ConfirmDialog(
            title = "Terminer la séance ?",
            message = if (empty) "Aucune série n'a été enregistrée : la séance sera supprimée."
            else "${allSets.size} séries · ${formatVolume(allSets.sumOf { it.volume })} en ${formatDurationHuman((now - session.session.startedAt) / 1000)}. Bravo !",
            confirmLabel = "Terminer",
            onDismiss = { showFinish = false },
            onConfirm = { vm.finishSession(session); showFinish = false },
        )
    }
    if (showCancel) {
        ConfirmDialog(
            title = "Annuler la séance ?",
            message = "Toutes les séries de cette séance seront définitivement supprimées.",
            confirmLabel = "Supprimer",
            onDismiss = { showCancel = false },
            onConfirm = { vm.cancelSession(session); showCancel = false },
        )
    }
}

@Composable
private fun RestTimerCard(state: RestTimer.State, onAdjust: (Int) -> Unit, onDismiss: () -> Unit) {
    val container = if (state.finished) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (state.finished) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.finished) "Repos terminé, c'est reparti ! 💪" else "Repos · ${state.label}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fermer le minuteur") }
            }
            if (!state.finished) {
                Text(
                    formatDuration(state.remainingSeconds.toLong()),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = { onAdjust(-15) }) { Text("−15 s") }
                    OutlinedButton(onClick = { onAdjust(15) }) { Text("+15 s") }
                    Button(onClick = onDismiss) { Text("Passer") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    item: SessionExerciseWithSets,
    previousSets: List<WorkoutSet>,
    input: SetInput,
    onInputChange: (SetInput) -> Unit,
    onLogSet: (SetInput) -> Boolean,
    onDeleteSet: (WorkoutSet) -> Unit,
    onEditRest: () -> Unit,
    onRemove: () -> Unit,
) {
    var error by remember { mutableStateOf(false) }
    val sets = item.orderedSets
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.exercise.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = onEditRest,
                    label = { Text(formatDuration(item.exercise.defaultRestSeconds.toLong())) },
                    leadingIcon = { Icon(Icons.Filled.Timer, "Temps de repos", Modifier.size(AssistChipDefaults.IconSize)) },
                )
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Retirer l'exercice") }
            }

            if (sets.isNotEmpty() || previousSets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SetRow("Série", "Précédent", "Réalisé", "Repos", header = true)
                HorizontalDivider()
            }
            val rows = maxOf(sets.size, previousSets.size)
            for (i in 0 until rows) {
                val set = sets.getOrNull(i)
                val prev = previousSets.getOrNull(i)
                SetRow(
                    index = "${i + 1}",
                    previous = prev?.let { "${formatWeight(it.weightKg)} × ${it.reps}" } ?: "—",
                    done = set?.let { "${formatWeight(it.weightKg)} kg × ${it.reps}" } ?: "",
                    rest = set?.restSeconds?.let { formatDuration(it.toLong()) } ?: "",
                    trailing = if (set != null && i == sets.lastIndex) {
                        { IconButton(onClick = { onDeleteSet(set) }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Supprimer la série", Modifier.size(18.dp)) } }
                    } else null,
                    dimmed = set == null,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberStepper(
                    label = "Poids (kg)",
                    value = input.weight,
                    onValueChange = { onInputChange(input.copy(weight = it)); error = false },
                    step = 2.5,
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
                NumberStepper(
                    label = "Répétitions",
                    value = input.reps,
                    onValueChange = { onInputChange(input.copy(reps = it)); error = false },
                    step = 1.0,
                    decimal = false,
                    modifier = Modifier.weight(1f),
                )
            }
            if (error) {
                Text("Indique un nombre de répétitions valide.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { error = !onLogSet(input) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Valider la série ${sets.size + 1}")
            }
        }
    }
}

@Composable
private fun SetRow(
    index: String,
    previous: String,
    done: String,
    rest: String,
    header: Boolean = false,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = when {
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, style = style, color = color, modifier = Modifier.width(44.dp))
        Text(previous, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(done, style = style, color = color, fontWeight = if (header) null else FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
        Text(rest, style = style, color = color, modifier = Modifier.width(48.dp))
        Box(Modifier.width(32.dp)) { trailing?.invoke() }
    }
}
