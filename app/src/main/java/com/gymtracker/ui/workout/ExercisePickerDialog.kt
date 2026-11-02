package com.gymtracker.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtracker.data.Exercise
import com.gymtracker.ui.components.ExerciseAvatar

@Composable
fun ExercisePickerDialog(
    exercises: List<Exercise>,
    alreadyAdded: Set<Long>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
    onCreate: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = exercises.filter {
        q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.muscleGroup.contains(q, ignoreCase = true)
    }
    val exactMatch = exercises.any { it.name.equals(q, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un exercice") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Rechercher ou créer…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                    if (q.isNotEmpty() && !exactMatch) {
                        item {
                            ListItem(
                                headlineContent = { Text("Créer « $q »") },
                                leadingContent = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { onCreate(q) },
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { ex ->
                        ListItem(
                            headlineContent = { Text(ex.name) },
                            supportingContent = { Text(ex.muscleGroup) },
                            leadingContent = { ExerciseAvatar(ex, size = 40.dp) },
                            trailingContent = if (ex.id in alreadyAdded) {
                                { Icon(Icons.Filled.Check, "Déjà dans la séance") }
                            } else null,
                            modifier = Modifier.clickable { onPick(ex) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
