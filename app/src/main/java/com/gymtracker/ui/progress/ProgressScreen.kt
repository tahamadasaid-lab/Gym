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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
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
import com.gymtracker.data.Exercise
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ChartPoint
import com.gymtracker.ui.components.EmptyState
import com.gymtracker.ui.components.LineChart
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.StatTile
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight

@Composable
fun ProgressScreen(vm: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Progression") { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.trainedExercises.isEmpty() -> EmptyState(
                Icons.AutoMirrored.Filled.ShowChart,
                "Pas encore de données",
                "Enregistre quelques séries pendant une séance pour voir ta progression ici.",
                Modifier.padding(padding),
            )
            else -> {
                val stats = state.stats
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
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
                color = if (positive) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}
