package com.gymtracker.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.GymApp
import com.gymtracker.data.ProgramWithExercises
import com.gymtracker.planning.Reminders
import java.time.LocalDate

private val DayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
private val DayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Planning de la semaine : un programme (ou séance libre / repos) par jour. */
@Composable
fun PlanningCard(programs: List<ProgramWithExercises>, onStart: (Long) -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as GymApp).settings
    val settings by store.settings.collectAsStateWithLifecycle()
    val today = LocalDate.now().dayOfWeek.value
    var editDay by remember { mutableStateOf<Int?>(null) }

    fun nameOf(id: Long?): String? = when (id) {
        null -> null
        0L -> "Libre"
        else -> programs.firstOrNull { it.program.id == id }?.program?.name
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Planning de la semaine", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val todayPlan = settings.weekPlan[today]
                val todayName = nameOf(todayPlan)
                if (todayPlan != null && todayName != null) {
                    FilledTonalButton(onClick = { onStart(todayPlan) }) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text(todayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { d ->
                    val planned = nameOf(settings.weekPlan[d])
                    val isToday = d == today
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (planned != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(2.dp, if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.small)
                            .clickable { editDay = d }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(DayLetters[d - 1], style = MaterialTheme.typography.labelMedium)
                        Text(
                            planned ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                if (settings.weekPlan.isEmpty()) "Touche un jour pour planifier tes séances (rappels dans ⚙ Réglages)."
                else if (settings.reminderEnabled) "🔔 Rappel à ${settings.reminderMinutes / 60}h${(settings.reminderMinutes % 60).toString().padStart(2, '0')} les jours prévus"
                else "Active les rappels dans ⚙ Réglages pour ne rien oublier.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    editDay?.let { d ->
        val current = settings.weekPlan[d]
        val options = listOf<Pair<Long?, String>>(null to "Repos", 0L to "Séance libre") + programs.map { it.program.id to it.program.name }
        AlertDialog(
            onDismissRequest = { editDay = null },
            title = { Text(DayNames[d - 1]) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                store.update { s -> s.copy(weekPlan = if (id == null) s.weekPlan - d else s.weekPlan + (d to id)) }
                                Reminders.schedule(context, store.settings.value)
                                editDay = null
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editDay = null }) { Text("Fermer") } },
        )
    }
}
