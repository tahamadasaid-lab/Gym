package com.gymtracker.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.GymApp
import com.gymtracker.data.RecordEvent
import com.gymtracker.data.WarmupStep
import com.gymtracker.data.WorkoutSet
import com.gymtracker.data.computePlates
import com.gymtracker.data.warmupPlan
import com.gymtracker.ui.components.describeLoad
import com.gymtracker.util.formatWeight
import kotlinx.coroutines.delay

/** Note libre de la séance, enregistrée automatiquement pendant la saisie. */
@Composable
fun SessionNotesField(sessionId: Long, saved: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(sessionId) { mutableStateOf(saved) }
    LaunchedEffect(text) {
        if (text != saved) {
            delay(800)
            onSave(text)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("📝 Note de séance (forme, douleur, sensations…)") },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Bandeau de célébration d'un nouveau record. */
@Composable
fun RecordBanner(event: RecordEvent?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(event) }
    if (event != null) last = event
    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.padding(12.dp),
    ) {
        val e = last ?: return@AnimatedVisibility
        Card(
            onClick = onDismiss,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("🏆 Nouveau record !", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when (e) {
                            is RecordEvent.Weight ->
                                "${e.exerciseName} : ${formatWeight(e.weightKg)} kg (avant ${formatWeight(e.previous)} kg)"
                            is RecordEvent.OneRepMax ->
                                "${e.exerciseName} : 1RM estimé ${formatWeight(Math.round(e.oneRepMax * 2) / 2.0)} kg " +
                                    "(avant ${formatWeight(Math.round(e.previous * 2) / 2.0)} kg)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Échauffement automatique vers la charge de travail saisie. */
@Composable
fun WarmupDialog(workWeight: Double, done: List<WorkoutSet>, onLog: (WarmupStep) -> Unit, onDismiss: () -> Unit) {
    val settings by (LocalContext.current.applicationContext as GymApp).settings.settings.collectAsStateWithLifecycle()
    val steps = remember(workWeight, settings.barWeightKg, settings.availablePlates) {
        warmupPlan(workWeight, settings.barWeightKg, settings.availablePlates)
    }
    var logged by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Échauffement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (steps.isEmpty()) {
                    Text("Saisis d'abord ta charge de travail (poids) sur l'exercice pour calculer l'échauffement.")
                } else {
                    Text(
                        "Vers ${formatWeight(workWeight)} kg. Pas de repos imposé ; les échauffements ne comptent pas dans tes stats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    steps.forEachIndexed { i, step ->
                        val isDone = i in logged ||
                            done.any { it.weightKg == step.weightKg && it.reps == step.reps } && i < done.size
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${step.label} · ${formatWeight(step.weightKg)} kg × ${step.reps}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    describeLoad(computePlates(step.weightKg, settings.barWeightKg, settings.availablePlates)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isDone) {
                                Icon(Icons.Filled.Check, "Faite", tint = MaterialTheme.colorScheme.secondary)
                            } else {
                                FilledTonalButton(onClick = { onLog(step); logged = logged + i }) { Text("Fait") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
