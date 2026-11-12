@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.Exercise
import com.gymtracker.data.ProgramWithExercises
import com.gymtracker.data.SessionExerciseWithSets
import com.gymtracker.data.SessionSummary
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.effort
import com.gymtracker.data.efforts
import com.gymtracker.data.volume
import com.gymtracker.ui.components.LocalCalories
import com.gymtracker.ui.components.formatKcal
import androidx.compose.material.icons.filled.Settings
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.EditSetDialog
import com.gymtracker.ui.components.ExerciseAvatar
import com.gymtracker.ui.components.ImageViewerDialog
import com.gymtracker.ui.components.MuscleTag
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.ui.components.RestPickerDialog
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.SectionHeader
import com.gymtracker.ui.components.TextInputDialog
import com.gymtracker.util.formatDuration
import com.gymtracker.util.formatDurationHuman
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(
    onOpenSession: (Long) -> Unit,
    onOpenProgram: (Long?) -> Unit,
    onOpenSettings: () -> Unit,
    vm: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        WorkoutUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is WorkoutUiState.NoSession -> HomeScreen(
            state = s,
            onStartFree = { vm.startSession() },
            onStartProgram = { vm.startProgram(it) },
            onOpenProgram = onOpenProgram,
            onOpenSession = onOpenSession,
            onOpenSettings = onOpenSettings,
        )
        is WorkoutUiState.Active -> ActiveSessionScreen(s.session, vm, onOpenSettings)
    }
}

// =====================================================================================  Accueil

@Composable
private fun HomeScreen(
    state: WorkoutUiState.NoSession,
    onStartFree: () -> Unit,
    onStartProgram: (Long) -> Unit,
    onOpenProgram: (Long?) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ScreenScaffold(
        title = "GymTracker",
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Réglages") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(state, onStartFree) }

            item {
                SectionHeader("Mes programmes") {
                    TextButton(onClick = { onOpenProgram(null) }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nouveau")
                    }
                }
            }
            if (state.programs.isEmpty()) {
                item {
                    Card(
                        onClick = { onOpenProgram(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Crée tes programmes (ex. Push, Pull, Legs) pour lancer une séance en un clic avec tes exercices déjà prêts.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(state.programs, key = { "p" + it.program.id }) { p ->
                ProgramCard(p, onStart = { onStartProgram(p.program.id) }, onEdit = { onOpenProgram(p.program.id) })
            }

            if (state.recent.isNotEmpty()) {
                item { SectionHeader("Séances récentes") }
                items(state.recent, key = { "s" + it.id }) { s ->
                    RecentSessionCard(s, onClick = { onOpenSession(s.id) })
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: WorkoutUiState.NoSession, onStart: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(primary, Color(0xFFFF4D6D))))
            .padding(20.dp)
    ) {
        Column {
            Text("Prêt à t'entraîner ?", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.sessionsThisWeek} séance(s) cette semaine · ${formatVolume(state.weekVolume)} soulevés",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Text(
                "${formatKcal(LocalCalories.current.kcal(state.weekEfforts))} brûlées cette semaine · ${state.totalSessions} séance(s) au total",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1A1A1A)),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Séance libre")
            }
        }
    }
}

@Composable
private fun ProgramCard(p: ProgramWithExercises, onStart: () -> Unit, onEdit: () -> Unit) {
    val items = p.orderedItems
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarStack(items.map { it.exercise })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.program.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${items.size} exercices · ${items.sumOf { it.item.targetSets }} séries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    items.joinToString(" · ") { it.exercise.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(onClick = onStart, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.PlayArrow, "Démarrer ${p.program.name}")
            }
        }
    }
}

@Composable
private fun AvatarStack(exercises: List<Exercise>) {
    val shown = exercises.take(3)
    if (shown.isEmpty()) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    Box(Modifier.width(44.dp + 14.dp * (shown.size - 1)).height(44.dp)) {
        shown.forEachIndexed { i, ex ->
            ExerciseAvatar(ex, size = 44.dp, modifier = Modifier.offset(x = 14.dp * i))
        }
    }
}

@Composable
private fun RecentSessionCard(s: SessionSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatLongDate(s.startedAt), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatDurationHuman(s.durationSeconds)} · ${s.setCount} séries · ${formatVolume(s.volume)} · ${formatKcal(LocalCalories.current.kcal(s.efforts))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (s.exerciseNames.isNotEmpty()) {
                    Text(
                        s.exerciseNames.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Voir la séance")
        }
    }
}

// =====================================================================================  Séance en cours

@Composable
private fun ActiveSessionScreen(session: SessionWithExercises, vm: WorkoutViewModel, onOpenSettings: () -> Unit) {
    val previous by vm.previous.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showSaveProgram by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var restEdit by remember { mutableStateOf<SessionExerciseWithSets?>(null) }
    var removeTarget by remember { mutableStateOf<SessionExerciseWithSets?>(null) }
    var editSet by remember { mutableStateOf<Pair<WorkoutSet, Int>?>(null) }
    var viewImage by remember { mutableStateOf<Exercise?>(null) }

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
    val elapsed = (now - session.session.startedAt) / 1000
    val calories = LocalCalories.current
    val sessionKcal = calories.kcal(session.efforts())
    val subtitle = "${formatDuration(elapsed)} · ${allSets.size} séries · ${formatVolume(allSets.sumOf { it.volume })} · ${formatKcal(sessionKcal)}"

    ScreenScaffold(
        title = "Séance en cours",
        subtitle = subtitle,
        actions = {
            FilledTonalButton(onClick = { showFinish = true }) { Text("Terminer") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Plus d'options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Enregistrer comme programme") },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                        enabled = session.exercises.isNotEmpty(),
                        onClick = { menuOpen = false; showSaveProgram = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Réglages du minuteur") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                    onEditSet = { set, index -> editSet = set to index },
                    onEditRest = { restEdit = item },
                    onShowImage = { viewImage = item.exercise },
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
    editSet?.let { (set, index) ->
        EditSetDialog(
            title = "Modifier la série ${index + 1}",
            initialWeight = formatWeight(set.weightKg),
            initialReps = set.reps.toString(),
            onDismiss = { editSet = null },
            onSave = { w, r -> vm.updateSet(set, w, r); editSet = null },
            onDelete = { vm.deleteSet(set); editSet = null },
        )
    }
    viewImage?.let { ImageViewerDialog(it, onDismiss = { viewImage = null }) }
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
    if (showSaveProgram) {
        TextInputDialog(
            title = "Enregistrer comme programme",
            label = "Nom du programme",
            confirmLabel = "Enregistrer",
            onDismiss = { showSaveProgram = false },
            onConfirm = { vm.saveAsProgram(session, it); showSaveProgram = false },
        )
    }
    if (showFinish) {
        val empty = allSets.isEmpty()
        ConfirmDialog(
            title = "Terminer la séance ?",
            message = if (empty) "Aucune série n'a été enregistrée : la séance sera supprimée."
            else "${allSets.size} séries · ${formatVolume(allSets.sumOf { it.volume })} en ${formatDurationHuman(elapsed)}.\n" +
                "Dépense estimée : ${formatKcal(sessionKcal)}. Bravo !",
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
private fun ExerciseCard(
    item: SessionExerciseWithSets,
    previousSets: List<WorkoutSet>,
    input: SetInput,
    onInputChange: (SetInput) -> Unit,
    onLogSet: (SetInput) -> Boolean,
    onEditSet: (WorkoutSet, Int) -> Unit,
    onEditRest: () -> Unit,
    onShowImage: () -> Unit,
    onRemove: () -> Unit,
) {
    var error by remember { mutableStateOf(false) }
    val sets = item.orderedSets
    val target = item.sessionExercise.targetSets
    val targetReached = target != null && sets.size >= target

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseAvatar(
                    item.exercise,
                    size = 52.dp,
                    onClick = if (item.exercise.imageUri != null) onShowImage else null,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MuscleTag(item.exercise.muscleGroup)
                        if (sets.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                formatKcal(LocalCalories.current.kcal(item.effort())),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (target != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${sets.size}/$target séries",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (targetReached) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Retirer l'exercice") }
            }
            if (target != null) {
                LinearProgressIndicator(
                    progress = { (sets.size.toFloat() / target).coerceAtMost(1f) },
                    color = if (targetReached) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(CircleShape),
                )
            }

            val rows = maxOf(sets.size, previousSets.size, target ?: 0)
            if (rows > 0) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    HeaderText("Série", Modifier.width(44.dp))
                    HeaderText("Précédent", Modifier.weight(1f))
                    HeaderText("Réalisé", Modifier.weight(1.2f))
                    HeaderText("Repos", Modifier.width(44.dp))
                }
            }
            for (i in 0 until rows) {
                val set = sets.getOrNull(i)
                SetRow(
                    index = i,
                    previous = previousSets.getOrNull(i),
                    set = set,
                    onClick = set?.let { { onEditSet(it, i) } },
                )
            }
            if (sets.isNotEmpty()) {
                Text(
                    "Touche une série pour la modifier ou la supprimer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { error = !onLogSet(input) },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Valider la série ${sets.size + 1}")
                }
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onEditRest,
                    label = { Text(formatDuration(item.exercise.defaultRestSeconds.toLong())) },
                    leadingIcon = { Icon(Icons.Filled.Timer, "Temps de repos", Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun SetRow(index: Int, previous: WorkoutSet?, set: WorkoutSet?, onClick: (() -> Unit)?) {
    val done = set != null
    Surface(
        color = if (done) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .let { if (onClick != null) it.clip(MaterialTheme.shapes.small).clickable(onClick = onClick) else it },
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondary)
                    } else {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                previous?.let { "${formatWeight(it.weightKg)} × ${it.reps}" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                set?.let { "${formatWeight(it.weightKg)} kg × ${it.reps}" } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                set?.restSeconds?.let { formatDuration(it.toLong()) } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
        }
    }
}
