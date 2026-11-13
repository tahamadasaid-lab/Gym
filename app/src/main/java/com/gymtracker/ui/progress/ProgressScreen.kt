@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.BodyWeight
import com.gymtracker.data.Exercise
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ChartPoint
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.EmptyState
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.ui.components.LineChart
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.StatTile
import com.gymtracker.ui.theme.PositiveGreen
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatTime
import com.gymtracker.util.parseWeight
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight

@Composable
fun ProgressScreen(
    vm: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory),
    insights: InsightsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val tabs = listOf("Exercices", "Records", "Bilan", "Objectifs", "Corps")
    ScreenScaffold(title = "Progression") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { vm.tab.value = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> ExerciseProgress(vm)
                1 -> RecordsTab(insights)
                2 -> ReviewTab(insights)
                3 -> GoalsTab(insights)
                else -> BodyTab(vm, insights)
            }
        }
    }
}

@Composable
private fun ExerciseProgress(vm: ProgressViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.trainedExercises.isEmpty() -> EmptyState(
            Icons.AutoMirrored.Filled.ShowChart,
            "Pas encore de données",
            "Enregistre quelques séries pendant une séance pour voir ta progression ici.",
        )
        else -> {
            val stats = state.stats
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ExerciseSelector(state.trainedExercises, state.selected, vm::select)
                }
                if (stats.isNotEmpty()) {
                    item { RecordsSection(stats) }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Metric.entries.forEach { m ->
                                FilterChip(
                                    selected = metric == m,
                                    onClick = { vm.selectMetric(m) },
                                    label = { Text(m.label) },
                                )
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${metric.label} par séance", style = MaterialTheme.typography.titleSmall)
                                LineChart(
                                    points = stats.map { ChartPoint(it.date, it.valueOf(metric)) },
                                    formatValue = { v ->
                                        if (metric == Metric.MaxReps) v.toInt().toString()
                                        else formatWeight(Math.round(v * 2) / 2.0)
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    item {
                        Text("Détail des séances", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    val reversed = stats.asReversed()
                    items(reversed.size, key = { reversed[it].sessionId }) { i ->
                        val st = reversed[i]
                        val before = reversed.getOrNull(i + 1)
                        SessionStatRow(
                            date = st.date,
                            best = "${formatWeight(st.bestSetWeight)} kg × ${st.bestSetReps}",
                            detail = "${st.setCount} séries · ${st.totalReps} reps · ${formatVolume(st.volume)}",
                            delta = before?.let { st.valueOf(metric) - it.valueOf(metric) },
                            unit = metric.unit,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun BodyWeightContent(vm: ProgressViewModel) {
    val entries by vm.bodyWeights.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BodyWeight?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.MonitorWeight, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter une pesée")
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Note ton poids régulièrement (par exemple chaque semaine, le matin à jeun) pour suivre son évolution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val first = entries.first()
            val last = entries.last()
            val diff = last.weightKg - first.weightKg
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Actuel", "${formatWeight(last.weightKg)} kg", Modifier.weight(1f))
                    StatTile(
                        "Évolution",
                        (if (diff > 0) "+" else "") + "${formatWeight(Math.round(diff * 10) / 10.0)} kg",
                        Modifier.weight(1f),
                        detail = "depuis le ${formatLongDate(first.date)}".takeIf { entries.size > 1 },
                    )
                }
            }
            if (entries.size > 1) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Poids de corps", style = MaterialTheme.typography.titleSmall)
                            LineChart(
                                points = entries.map { ChartPoint(it.date, it.weightKg) },
                                formatValue = { formatWeight(Math.round(it * 10) / 10.0) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            item { Text("Historique des pesées", style = MaterialTheme.typography.titleMedium) }
            val reversed = entries.asReversed()
            items(reversed.size, key = { reversed[it].id }) { i ->
                val e = reversed[i]
                val before = reversed.getOrNull(i + 1)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatWeight(e.weightKg)} kg", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${formatLongDate(e.date)} · ${formatTime(e.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (before != null) {
                        val d = Math.round((e.weightKg - before.weightKg) * 10) / 10.0
                        if (d != 0.0) {
                            Text(
                                (if (d > 0) "+" else "") + formatWeight(d) + " kg",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Outlined.Delete, "Supprimer la pesée") }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        var value by remember { mutableStateOf(entries.lastOrNull()?.let { formatWeight(it.weightKg) } ?: "") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nouvelle pesée") },
            text = { NumberStepper("Poids (kg)", value, { value = it }, step = 0.1, decimal = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        parseWeight(value)?.takeIf { it > 0 }?.let { vm.addBodyWeight(it) }
                        showAdd = false
                    },
                    enabled = (parseWeight(value) ?: 0.0) > 0,
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annuler") } },
        )
    }
    toDelete?.let { e ->
        ConfirmDialog(
            title = "Supprimer cette pesée ?",
            message = "${formatWeight(e.weightKg)} kg le ${formatLongDate(e.date)}",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = { vm.deleteBodyWeight(e); toDelete = null },
        )
    }
}

@Composable
private fun ExerciseSelector(exercises: List<Exercise>, selected: Exercise?, onSelect: (Exercise) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Exercice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            exercises.forEach { ex ->
                DropdownMenuItem(
                    text = { Text(ex.name) },
                    onClick = { onSelect(ex); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun RecordsSection(stats: List<com.gymtracker.data.ExerciseSessionStat>) {
    val first = stats.first()
    val bestWeight = stats.maxOf { it.maxWeight }
    val best1rm = stats.maxOf { it.bestOneRepMax }
    val bestVolume = stats.maxOf { it.volume }
    val gain = bestWeight - first.maxWeight
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Record poids",
                "${formatWeight(bestWeight)} kg",
                Modifier.weight(1f),
                detail = if (gain > 0) "+${formatWeight(gain)} kg depuis le début" else null,
            )
            StatTile("1RM estimé", "${formatWeight(Math.round(best1rm * 2) / 2.0)} kg", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Meilleur volume", formatVolume(bestVolume), Modifier.weight(1f))
            StatTile("Séances", "${stats.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SessionStatRow(date: Long, best: String, detail: String, delta: Double?, unit: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatLongDate(date), style = MaterialTheme.typography.bodyMedium)
            Text(best, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (delta != null && kotlin.math.abs(delta) > 1e-6) {
            val positive = delta > 0
            Text(
                (if (positive) "▲ +" else "▼ ") + formatWeight(Math.round(delta * 2) / 2.0) + " $unit",
                color = if (positive) PositiveGreen else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}
