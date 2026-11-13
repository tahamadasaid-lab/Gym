@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gymtracker.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.data.Exercise
import com.gymtracker.data.GoalProgress
import com.gymtracker.data.GoalType
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight
import java.util.concurrent.TimeUnit

@Composable
fun GoalsTab(vm: InsightsViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val exercises by vm.allExercises.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<GoalProgress?>(null) }
    val list = goals
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.Flag, null)
                Spacer(Modifier.width(8.dp))
                Text("Nouvel objectif")
            }
        }
        if (list.isEmpty()) {
            item {
                Text(
                    "Fixe-toi un cap : « 100 kg au développé couché d'ici juin » ou « 75 kg de poids de corps ». " +
                        "La progression se met à jour toute seule avec tes séances et tes pesées.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(list, key = { it.goal.id }) { g -> GoalCard(g, onDelete = { toDelete = g }) }
    }

    if (showAdd) {
        AddGoalDialog(
            exercises = exercises,
            currentValue = vm::currentValue,
            onDismiss = { showAdd = false },
            onAdd = { type, exId, target, deadline -> vm.addGoal(type, exId, target, deadline); showAdd = false },
        )
    }
    toDelete?.let { g ->
        ConfirmDialog(
            title = "Supprimer l'objectif ?",
            message = "${g.title} : ${formatWeight(g.goal.targetValue)} kg",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = { vm.deleteGoal(g.goal); toDelete = null },
        )
    }
}

@Composable
fun GoalCard(g: GoalProgress, onDelete: (() -> Unit)? = null) {
    val achieved = g.achieved
    Card(
        Modifier.fillMaxWidth(),
        colors = if (achieved) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (achieved) "✅ " else "🎯 ") + g.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatWeight(g.goal.startValue)} → ${formatWeight(g.goal.targetValue)} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    g.current?.let { "${formatWeight(it)} kg" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Supprimer l'objectif") }
                }
            }
            LinearProgressIndicator(
                progress = { g.fraction },
                color = if (achieved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(CircleShape),
            )
            val status = when {
                g.goal.achievedAt != null -> "Atteint le ${formatLongDate(g.goal.achievedAt)} 🎉"
                achieved -> "Atteint ! 🎉"
                g.goal.deadline != null -> {
                    val days = TimeUnit.MILLISECONDS.toDays(g.goal.deadline - System.currentTimeMillis())
                    if (days >= 0) "${(g.fraction * 100).toInt()} % · encore $days jour(s) (échéance ${formatLongDate(g.goal.deadline)})"
                    else "${(g.fraction * 100).toInt()} % · échéance dépassée"
                }
                else -> "${(g.fraction * 100).toInt()} % du chemin"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private val Deadlines = listOf("Aucune" to null, "1 mois" to 30L, "3 mois" to 91L, "6 mois" to 182L, "1 an" to 365L)

@Composable
private fun AddGoalDialog(
    exercises: List<Exercise>,
    currentValue: (GoalType, Long?) -> Double?,
    onDismiss: () -> Unit,
    onAdd: (GoalType, Long?, Double, Long?) -> Unit,
) {
    var type by remember { mutableStateOf(GoalType.EXERCISE_WEIGHT) }
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var target by remember { mutableStateOf("") }
    var deadlineDays by remember { mutableStateOf<Long?>(91L) }
    var expanded by remember { mutableStateOf(false) }
    val current = currentValue(type, exercise?.id)
    val targetValue = parseWeight(target)
    val valid = targetValue != null && targetValue > 0 && (type == GoalType.BODY_WEIGHT || exercise != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel objectif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == GoalType.EXERCISE_WEIGHT, { type = GoalType.EXERCISE_WEIGHT }, label = { Text("Charge sur un exercice") })
                    FilterChip(type == GoalType.BODY_WEIGHT, { type = GoalType.BODY_WEIGHT }, label = { Text("Poids de corps") })
                }
                if (type == GoalType.EXERCISE_WEIGHT) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = exercise?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Exercice") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            exercises.forEach { ex ->
                                DropdownMenuItem(text = { Text(ex.name) }, onClick = { exercise = ex; expanded = false })
                            }
                        }
                    }
                }
                Text(
                    current?.let { "Actuellement : ${formatWeight(it)} kg" + if (type == GoalType.EXERCISE_WEIGHT) " (record)" else " (dernière pesée)" }
                        ?: if (type == GoalType.BODY_WEIGHT) "Aucune pesée : ajoute-en une dans l'onglet Corps." else "Pas encore de série sur cet exercice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberStepper("Objectif (kg)", target, { target = it }, step = if (type == GoalType.BODY_WEIGHT) 0.5 else 2.5, decimal = true)
                Text("Échéance", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Deadlines.forEach { (label, days) ->
                        FilterChip(deadlineDays == days, { deadlineDays = days }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val deadline = deadlineDays?.let { System.currentTimeMillis() + TimeUnit.DAYS.toMillis(it) }
                onAdd(type, exercise?.id, targetValue!!, deadline)
            }) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
