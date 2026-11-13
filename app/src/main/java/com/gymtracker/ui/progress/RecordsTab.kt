@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.gymtracker.ui.components.EmptyState
import com.gymtracker.ui.components.ExerciseAvatar
import com.gymtracker.util.formatShortDate
import com.gymtracker.util.formatVolume
import com.gymtracker.util.formatWeight

@Composable
fun RecordsTab(vm: InsightsViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val list = records
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> EmptyState(Icons.Filled.EmojiEvents, "Pas encore de records", "Tes records apparaîtront ici après tes premières séances.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Rechercher un exercice") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(list.filter { query.isBlank() || it.exercise.name.contains(query.trim(), true) }, key = { it.exercise.id }) { item ->
                RecordCard(item)
            }
        }
    }
}

@Composable
private fun RecordCard(item: RecordItem) {
    val r = item.records
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseAvatar(item.exercise, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${r.sessionCount} séance(s) · dernière le ${formatShortDate(r.lastDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordTile("🏋️ Poids max", "${formatWeight(r.bestWeight)} kg", formatShortDate(r.bestWeightDate), Modifier.weight(1f))
                RecordTile("🎯 1RM estimé", "${formatWeight(Math.round(r.bestOneRepMax * 2) / 2.0)} kg", formatShortDate(r.bestOneRepMaxDate), Modifier.weight(1f))
                RecordTile("📦 Volume", formatVolume(r.bestVolume), formatShortDate(r.bestVolumeDate), Modifier.weight(1f))
            }
            if (r.repMaxes.isNotEmpty()) {
                Text(
                    "Meilleure charge par nombre de répétitions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.repMaxes.forEach { m ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${m.reps} rep${if (m.reps > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall)
                                Text("${formatWeight(m.weightKg)} kg", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordTile(label: String, value: String, date: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
