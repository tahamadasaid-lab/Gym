@file:OptIn(ExperimentalLayoutApi::class)

package com.gymtracker.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymtracker.data.SessionWithExercises
import com.gymtracker.data.plannedWeight
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight

private val Increments = listOf(0.0, 1.25, 2.5, 5.0)

fun targetSummary(item: ProgramItem, weeks: Int?): String {
    val w = item.startWeight
    val r = item.targetReps
    if (w == null && r == null) return "🎯 Définir charge et répétitions"
    val base = "🎯 " + (w?.let { "${formatWeight(it)} kg" } ?: "—") + (r?.let { " × $it" } ?: "")
    val inc = item.weeklyIncrement
    return if (weeks != null && inc != null && inc > 0) "$base · +${formatWeight(inc)} kg/sem." else base
}

/** Réglages du cycle sur plusieurs semaines. */
@Composable
fun CycleCard(vm: ProgramEditorViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Progression sur plusieurs semaines", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Les charges augmentent chaque semaine selon ton plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.weeks != null, onCheckedChange = { vm.weeks = if (it) 6 else null })
            }
            val weeks = vm.weeks
            if (weeks != null) {
                NumberStepper(
                    "Durée du cycle (semaines)",
                    "$weeks",
                    { t -> t.toIntOrNull()?.let { vm.weeks = it.coerceIn(2, 16) } },
                    step = 1.0,
                    decimal = false,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dernière semaine allégée (décharge à −20 %)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = vm.deloadLastWeek, onCheckedChange = { vm.deloadLastWeek = it })
                }
                vm.currentWeek?.let { w ->
                    Text("Cycle en cours : semaine $w / $weeks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.restartCycle, onCheckedChange = { vm.restartCycle = it })
                        Text("Recommencer à la semaine 1", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Le cycle démarre à ta première séance de ce programme. Touche 🎯 sur chaque exercice pour régler la charge de départ et la progression.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Charge de départ, répétitions visées et progression d'un exercice du programme. */
@Composable
fun TargetDialog(
    name: String,
    item: ProgramItem,
    weeks: Int?,
    deload: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProgramItem) -> Unit,
) {
    var weight by remember { mutableStateOf(item.startWeight?.let { formatWeight(it) } ?: "") }
    var reps by remember { mutableStateOf(item.targetReps?.toString() ?: "") }
    var inc by remember { mutableStateOf(item.weeklyIncrement ?: 2.5) }
    val w = parseWeight(weight)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberStepper(if (weeks != null) "Charge semaine 1 (kg)" else "Charge (kg)", weight, { weight = it }, step = 2.5, decimal = true)
                NumberStepper("Répétitions visées", reps, { reps = it }, step = 1.0, decimal = false)
                if (weeks != null) {
                    Text("Augmentation par semaine", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Increments.forEach { v ->
                            FilterChip(selected = inc == v, onClick = { inc = v }, label = { Text(if (v == 0.0) "Aucune" else "+${formatWeight(v)} kg") })
                        }
                    }
                    if (w != null && w > 0) {
                        Text(
                            (1..weeks).joinToString("  ·  ") { wk -> "S$wk : ${formatWeight(plannedWeight(w, inc, wk, weeks, deload))}" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        startWeight = w?.takeIf { it > 0 },
                        targetReps = reps.toIntOrNull()?.takeIf { it > 0 },
                        weeklyIncrement = if (weeks != null) inc else item.weeklyIncrement,
                    )
                )
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Choix d'une ou plusieurs séances passées dont on reprend les exercices. */
@Composable
fun ImportSessionsDialog(
    sessions: List<SessionWithExercises>,
    onDismiss: () -> Unit,
    onImport: (List<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer des séances") },
        text = {
            Column {
                Text(
                    "Exercices, nombre de séries, répétitions et charge de ta meilleure série sont repris.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sessions.isEmpty()) {
                    Text("Aucune séance terminée pour l'instant.", modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(sessions, key = { it.session.id }) { s ->
                        val id = s.session.id
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (id in selected) selected - id else selected + id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = id in selected, onCheckedChange = { selected = if (it) selected + id else selected - id })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (s.session.programLabel?.let { "$it · " } ?: "") + formatLongDate(s.session.startedAt),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    s.orderedExercises.filter { it.sets.isNotEmpty() }.joinToString(" · ") { it.exercise.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onImport(selected.toList()) }) {
                Text("Importer (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
