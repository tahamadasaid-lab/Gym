@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gymtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymtracker.util.formatDuration
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight

/** Scaffold d'un écran : la barre de navigation du bas est gérée par le Scaffold parent. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Champ numérique avec boutons − / +. */
@Composable
fun NumberStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    fun adjust(delta: Double) {
        val current = parseWeight(value) ?: 0.0
        val next = (current + delta).coerceAtLeast(0.0)
        onValueChange(if (decimal) formatWeight(next) else next.toInt().toString())
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { adjust(-step) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Diminuer $label")
            }
            OutlinedTextField(
                value = value,
                onValueChange = { new ->
                    val allowed = if (decimal) new.filter { it.isDigit() || it == ',' || it == '.' } else new.filter { it.isDigit() }
                    onValueChange(allowed.take(6))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            FilledTonalIconButton(onClick = { adjust(step) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Augmenter $label")
            }
        }
    }
}

private val restPresets = listOf(30, 45, 60, 75, 90, 120, 150, 180, 240, 300)

@Composable
fun RestPickerDialog(
    exerciseName: String,
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selected by remember { mutableIntStateOf(currentSeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temps de repos") },
        text = {
            Column {
                Text(exerciseName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    restPresets.forEach { s ->
                        FilterChip(
                            selected = selected == s,
                            onClick = { selected = s },
                            label = { Text(formatDuration(s.toLong())) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { selected = (selected - 15).coerceAtLeast(15) }) { Text("−15 s") }
                    Text(
                        formatDuration(selected.toLong()),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { selected += 15 }) { Text("+15 s") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
