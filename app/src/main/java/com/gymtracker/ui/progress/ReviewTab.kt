@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.data.startMillis
import com.gymtracker.ui.components.LocalCalories
import com.gymtracker.ui.components.LocalMuscleGroups
import com.gymtracker.ui.components.StatTile
import com.gymtracker.ui.components.formatKcal
import com.gymtracker.ui.components.rememberMuscleStyle
import com.gymtracker.util.formatDurationHuman
import com.gymtracker.util.formatShortDate
import com.gymtracker.util.formatVolume
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReviewTab(vm: InsightsViewModel) {
    val state by vm.review.collectAsStateWithLifecycle()
    val r = state ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    val calories = LocalCalories.current
    val modes = ReviewMode.entries

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = r.mode == m,
                        onClick = { vm.setReviewMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(if (m == ReviewMode.WEEK) "Semaine" else "Mois") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftReview(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Période précédente") }
                Text(
                    if (r.mode == ReviewMode.WEEK) "Semaine du ${formatShortDate(r.start.startMillis())} au ${formatShortDate(r.end.startMillis())}"
                    else r.start.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) } + " ${r.start.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.shiftReview(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Période suivante") }
            }
        }
        item {
            val s = r.stats
            val p = r.previous
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Séances", "${s.sessions}", Modifier.weight(1f), detail = delta(s.sessions.toDouble(), p.sessions.toDouble(), ""))
                    StatTile("Durée", formatDurationHuman(s.durationSeconds), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Volume", formatVolume(s.volume), Modifier.weight(1f), detail = delta(s.volume, p.volume, " kg"))
                    StatTile("Calories", formatKcal(calories.kcal(s.efforts)), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Séries", "${s.sets}", Modifier.weight(1f), detail = delta(s.sets.toDouble(), p.sets.toDouble(), ""))
                    StatTile(
                        "Régularité",
                        "🔥 ${r.streakWeeks} sem.",
                        Modifier.weight(1f),
                        detail = if (r.streakWeeks > 0) "semaines d'affilée" else "lance une série !",
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Muscles travaillés", style = MaterialTheme.typography.titleSmall)
                    val load = regionLoad(r.stats.setsByGroup)
                    BodyMap(load, Modifier.padding(top = 8.dp))
                    Text(
                        if (load.isEmpty()) "Aucune série sur cette période."
                        else "Plus la couleur est intense, plus tu as fait de séries sur la zone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (r.stats.setsByGroup.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Séries par catégorie", style = MaterialTheme.typography.titleSmall)
                        val max = r.stats.setsByGroup.values.max()
                        r.stats.setsByGroup.entries.sortedByDescending { it.value }.forEach { (group, sets) ->
                            CategoryBar(group, sets, max, r.stats.volumeByGroup[group] ?: 0.0)
                        }
                        val known = LocalMuscleGroups.current.keys
                        val missing = known.filter { k -> r.stats.setsByGroup.keys.none { it.lowercase() == k } }
                        if (missing.isNotEmpty() && r.stats.sessions > 0) {
                            Text(
                                "Pas travaillé : " + missing.joinToString(", ") { k -> k.replaceFirstChar { it.titlecase(Locale.FRENCH) } },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Séances par semaine (8 dernières)", style = MaterialTheme.typography.titleSmall)
                    WeekBars(r.perWeek.map { formatShortDate(it.first.startMillis()) to it.second })
                }
            }
        }
    }
}

private fun delta(current: Double, previous: Double, unit: String): String? {
    if (previous == 0.0 && current == 0.0) return null
    val d = current - previous
    val txt = if (unit == " kg") formatVolume(kotlin.math.abs(d)) else "${kotlin.math.abs(d).toLong()}$unit"
    return when {
        d > 0 -> "▲ +$txt vs période préc."
        d < 0 -> "▼ −$txt vs période préc."
        else -> "= période préc."
    }
}

@Composable
private fun CategoryBar(group: String, sets: Int, max: Int, volume: Double) {
    val color = rememberMuscleStyle(group).color
    Column {
        Row {
            Text(group, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$sets séries · ${formatVolume(volume)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(sets.toFloat() / max).fillMaxHeight().clip(MaterialTheme.shapes.small).background(color))
        }
    }
}

@Composable
private fun WeekBars(data: List<Pair<String, Int>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { i, (label, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((80f * count / max).dp.coerceAtLeast(3.dp))
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (count == 0) MaterialTheme.colorScheme.surfaceContainerHighest else if (i == data.lastIndex) bar else bar.copy(alpha = 0.6f))
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
