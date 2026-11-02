@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gymtracker.ui.exercises

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gymtracker.data.Exercise
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.ExerciseAvatar
import com.gymtracker.ui.components.RestPickerDialog
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.muscleStyle
import com.gymtracker.util.formatDuration

private val defaultGroups = listOf("Pectoraux", "Dos", "Jambes", "Épaules", "Bras", "Abdos", "Cardio", "Autre")

@Composable
fun ExercisesScreen(vm: ExercisesViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var query by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Exercices",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Exercise(name = "", muscleGroup = "") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nouvel exercice") },
            )
        },
    ) { padding ->
        val list = exercises
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val q = query.trim()
        val filtered = list.filter { q.isEmpty() || it.name.contains(q, true) || it.muscleGroup.contains(q, true) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Rechercher un exercice") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            filtered.groupBy { it.muscleGroup }.forEach { (group, items) ->
                item(key = "header-$group") {
                    Text(
                        "$group · ${items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = muscleStyle(group).color,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.id }) { ex ->
                    ListItem(
                        leadingContent = { ExerciseAvatar(ex, size = 48.dp) },
                        headlineContent = { Text(ex.name, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = { Text("Repos : ${formatDuration(ex.defaultRestSeconds.toLong())}") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { editing = ex },
                    )
                }
            }
        }
    }

    editing?.let { ex ->
        val groups = (defaultGroups + exercises.orEmpty().map { it.muscleGroup }).distinct()
        ExerciseEditDialog(
            initial = ex,
            groups = groups,
            onDismiss = { editing = null },
            onSave = { updated, image, onResult ->
                vm.save(updated, image) { ok -> onResult(ok); if (ok) editing = null }
            },
            onDelete = { vm.delete(ex); editing = null },
        )
    }
}

@Composable
private fun ExerciseEditDialog(
    initial: Exercise,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSave: (Exercise, ImageChange, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var group by remember { mutableStateOf(initial.muscleGroup) }
    var rest by remember { mutableIntStateOf(initial.defaultRestSeconds) }
    var imageChange by remember { mutableStateOf<ImageChange>(ImageChange.None) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickRest by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) imageChange = ImageChange.Replace(uri)
    }

    // Aperçu : nouvelle photo choisie, sinon photo actuelle (sauf si retirée), sinon pictogramme.
    val preview: Any? = when (val c = imageChange) {
        is ImageChange.Replace -> c.uri
        ImageChange.Remove -> null
        ImageChange.None -> initial.imageUri?.let { java.io.File(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nouvel exercice" else "Modifier l'exercice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview != null) {
                        AsyncImage(
                            model = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium),
                        )
                    } else {
                        ExerciseAvatar(Exercise(name = name, muscleGroup = group), size = 72.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (preview != null) "Changer" else "Photo")
                        }
                        if (preview != null) {
                            TextButton(onClick = { imageChange = ImageChange.Remove }) { Text("Retirer la photo") }
                        }
                    }
                }
                Text(
                    "Astuce : photographie la machine ou le réglage pour t'en souvenir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nom") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Groupe musculaire") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g -> SuggestionChip(onClick = { group = g }, label = { Text(g) }) }
                }
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { pickRest = true },
                    label = { Text("Repos par défaut : ${formatDuration(rest.toLong())}") },
                    leadingIcon = { Icon(Icons.Filled.Timer, null) },
                )
                if (!isNew) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Supprimer l'exercice", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Le nom est obligatoire"
                } else {
                    onSave(initial.copy(name = name, muscleGroup = group, defaultRestSeconds = rest), imageChange) { ok ->
                        if (!ok) error = "Un exercice porte déjà ce nom"
                    }
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (pickRest) {
        RestPickerDialog(
            exerciseName = name.ifBlank { "Nouvel exercice" },
            currentSeconds = rest,
            onDismiss = { pickRest = false },
            onConfirm = { rest = it; pickRest = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer « ${initial.name} » ?",
            message = "Tout l'historique de cet exercice (séries et progression) sera également supprimé.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete() },
        )
    }
}
