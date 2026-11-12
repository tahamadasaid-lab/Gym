@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.gymtracker.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.data.DefaultCategories
import com.gymtracker.data.MuscleGroup
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.CategoryAvatar
import com.gymtracker.ui.components.CategoryColors
import com.gymtracker.ui.components.CategoryIcons
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.ScreenScaffold

/** Niveaux d'intensité proposés (valeur MET utilisée pour estimer les calories). */
val IntensityLevels = listOf(
    "Légère" to 3.5,
    "Modérée" to 5.0,
    "Intense" to 6.0,
    "Très intense" to 8.0,
)

fun intensityLabel(met: Double): String =
    IntensityLevels.minByOrNull { kotlin.math.abs(it.second - met) }?.first ?: "Modérée"

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    vm: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MuscleGroup?>(null) }

    ScreenScaffold(
        title = "Catégories",
        subtitle = "Groupes musculaires pour classer tes exercices",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = MuscleGroup(
                        name = "",
                        colorArgb = CategoryColors.first(),
                        icon = CategoryIcons.first().first,
                        met = 5.0,
                        position = 0,
                    )
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nouvelle catégorie") },
            )
        },
    ) { padding ->
        val list = rows
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(list, key = { it.group.id }) { row ->
                ListItem(
                    leadingContent = { CategoryAvatar(row.group) },
                    headlineContent = { Text(row.group.name, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text("${row.exerciseCount} exercice(s) · intensité ${intensityLabel(row.group.met).lowercase()}")
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { editing = row.group },
                )
            }
        }
    }

    editing?.let { group ->
        CategoryEditDialog(
            initial = group,
            exerciseCount = rows?.firstOrNull { it.group.id == group.id }?.exerciseCount ?: 0,
            onDismiss = { editing = null },
            onSave = { updated, onResult ->
                vm.save(updated, group.name.takeIf { group.id != 0L }) { ok -> onResult(ok); if (ok) editing = null }
            },
            onDelete = { vm.delete(group); editing = null },
        )
    }
}

@Composable
fun CategoryEditDialog(
    initial: MuscleGroup,
    exerciseCount: Int,
    onDismiss: () -> Unit,
    onSave: (MuscleGroup, (Boolean) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableIntStateOf(initial.colorArgb) }
    var icon by remember { mutableStateOf(initial.icon) }
    var met by remember { mutableDoubleStateOf(initial.met) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isOther = initial.name.equals(DefaultCategories.OTHER, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nouvelle catégorie" else "Modifier la catégorie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CategoryAvatar(initial.copy(colorArgb = color, icon = icon), size = 64.dp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nom (ex. Fessiers, Mollets, Avant-bras…)") },
                    singleLine = true,
                    enabled = !isOther,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Couleur", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(3.dp, if (c == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == color) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.Black)
                        }
                    }
                }
                Text("Pictogramme", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (selected) Color(color).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, key, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Intensité (pour les calories)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IntensityLevels.forEach { (label, value) ->
                        FilterChip(selected = met == value, onClick = { met = value }, label = { Text(label) })
                    }
                }
                if (onDelete != null && !isNew && !isOther) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Supprimer la catégorie", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Le nom est obligatoire"
                } else {
                    onSave(initial.copy(name = name.trim(), colorArgb = color, icon = icon, met = met)) { ok ->
                        if (!ok) error = "Cette catégorie existe déjà"
                    }
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Supprimer « ${initial.name} » ?",
            message = if (exerciseCount > 0) "Ses $exerciseCount exercice(s) seront déplacés dans « ${DefaultCategories.OTHER} ». Leur historique est conservé."
            else "La catégorie sera supprimée.",
            confirmLabel = "Supprimer",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete() },
        )
    }
}
