@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.ExerciseAvatar
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.workout.ExercisePickerDialog

@Composable
fun ProgramEditorScreen(
    onBack: () -> Unit,
    vm: ProgramEditorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val byId = exercises.associateBy { it.id }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canSave = vm.name.isNotBlank() && vm.items.isNotEmpty()

    ScreenScaffold(
        title = if (vm.isNew) "Nouveau programme" else "Modifier le programme",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        },
        actions = {
            if (!vm.isNew) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer le programme") }
            }
            TextButton(onClick = { vm.save(onBack) }, enabled = canSave) { Text("Enregistrer") }
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    label = { Text("Nom du programme (ex. Push, Haut du corps…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "${vm.items.size} exercice(s) · ${vm.items.sumOf { it.targetSets }} séries",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(vm.items.size) { index ->
                val item = vm.items[index]
                val ex = byId[item.exerciseId]
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Monter")
                            }
                            IconButton(onClick = { vm.move(index, 1) }, enabled = index < vm.items.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Descendre")
                            }
                        }
                        if (ex != null) ExerciseAvatar(ex, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex?.name ?: "…", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.targetSets} séries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        FilledTonalIconButton(onClick = { vm.setSets(index, item.targetSets - 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, "Moins de séries", Modifier.size(18.dp))
                        }
                        Text("${item.targetSets}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 6.dp))
                        FilledTonalIconButton(onClick = { vm.setSets(index, item.targetSets + 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, "Plus de séries", Modifier.size(18.dp))
                        }
                        IconButton(onClick = { vm.remove(index) }) { Icon(Icons.Outlined.Delete, "Retirer") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter un exercice")
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            exercises = exercises,
            alreadyAdded = vm.items.map { it.exerciseId }.toSet(),
            onDismiss = { showPicker = false },
            onPick = { vm.add(it.id); showPicker = false },
            onCreate = { vm.createAndAdd(it); showPicker = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer ce programme ?",
            message = "Le programme sera supprimé. Tes séances et ton historique sont conservés.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
        )
    }
}
