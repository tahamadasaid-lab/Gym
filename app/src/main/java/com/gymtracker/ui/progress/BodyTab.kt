@file:OptIn(ExperimentalLayoutApi::class)

package com.gymtracker.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gymtracker.data.Measurement
import com.gymtracker.data.ProgressPhoto
import com.gymtracker.ui.components.ChartPoint
import com.gymtracker.ui.components.ConfirmDialog
import com.gymtracker.ui.components.LineChart
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.ui.components.StatTile
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatShortDate
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight
import java.io.File

val MeasurementTypes = listOf("Tour de bras", "Poitrine", "Épaules", "Taille", "Hanches", "Cuisse", "Mollet", "Avant-bras", "Cou")

/** Onglet « Corps » : poids, mensurations et photos de progression. */
@Composable
fun BodyTab(progressVm: ProgressViewModel, vm: InsightsViewModel) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Poids", "Mensurations", "Photos").forEachIndexed { i, label ->
                FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label) })
            }
        }
        when (section) {
            0 -> BodyWeightContent(progressVm)
            1 -> MeasurementsContent(vm)
            else -> PhotosContent(vm)
        }
    }
}

@Composable
private fun MeasurementsContent(vm: InsightsViewModel) {
    val all by vm.measurements.collectAsStateWithLifecycle()
    val types = (MeasurementTypes + all.map { it.type }).distinct()
    var type by remember { mutableStateOf(all.lastOrNull()?.type ?: MeasurementTypes.first()) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Measurement?>(null) }
    val entries = all.filter { it.type == type }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.Straighten, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter une mensuration")
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { t ->
                    val count = all.count { it.type == t }
                    FilterChip(selected = t == type, onClick = { type = t }, label = { Text(if (count > 0) "$t ($count)" else t) })
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Mesure-toi au même moment (ex. le matin), au même endroit, ruban bien à plat, toutes les 2 à 4 semaines.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                val diff = entries.last().valueCm - entries.first().valueCm
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Actuel", "${formatWeight(entries.last().valueCm)} cm", Modifier.weight(1f))
                    StatTile(
                        "Évolution",
                        (if (diff > 0) "+" else "") + "${formatWeight(Math.round(diff * 10) / 10.0)} cm",
                        Modifier.weight(1f),
                        detail = "depuis le ${formatShortDate(entries.first().date)}".takeIf { entries.size > 1 },
                    )
                }
            }
            if (entries.size > 1) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(type, style = MaterialTheme.typography.titleSmall)
                            LineChart(
                                points = entries.map { ChartPoint(it.date, it.valueCm) },
                                formatValue = { formatWeight(Math.round(it * 10) / 10.0) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            entries.asReversed().forEach { e ->
                item(key = e.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatWeight(e.valueCm)} cm", style = MaterialTheme.typography.titleSmall)
                            Text(formatLongDate(e.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        var addType by remember { mutableStateOf(type) }
        var value by remember {
            mutableStateOf(all.lastOrNull { it.type == type }?.let { formatWeight(it.valueCm) } ?: "")
        }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nouvelle mensuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { t ->
                            FilterChip(selected = t == addType, onClick = {
                                addType = t
                                value = all.lastOrNull { it.type == t }?.let { formatWeight(it.valueCm) } ?: ""
                            }, label = { Text(t) })
                        }
                    }
                    NumberStepper("$addType (cm)", value, { value = it }, step = 0.5, decimal = true)
                }
            },
            confirmButton = {
                TextButton(enabled = (parseWeight(value) ?: 0.0) > 0, onClick = {
                    parseWeight(value)?.let { vm.addMeasurement(addType, it) }
                    type = addType
                    showAdd = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annuler") } },
        )
    }
    toDelete?.let { e ->
        ConfirmDialog(
            title = "Supprimer cette mesure ?",
            message = "${e.type} : ${formatWeight(e.valueCm)} cm le ${formatLongDate(e.date)}",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = { vm.deleteMeasurement(e); toDelete = null },
        )
    }
}

@Composable
private fun PhotosContent(vm: InsightsViewModel) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<ProgressPhoto?>(null) }
    var toDelete by remember { mutableStateOf<ProgressPhoto?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(uri)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Filled.AddAPhoto, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter une photo")
            }
        }
        if (photos.size >= 2) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Avant / après", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(photos.last(), photos.first()).forEach { p ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(
                                        model = File(p.path),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium),
                                    )
                                    Text(formatShortDate(p.date), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Prends une photo toutes les 2 à 4 semaines, même lumière et même pose : c'est le meilleur moyen de voir tes progrès.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(photos, key = { it.id }) { p ->
            Column {
                AsyncImage(
                    model = File(p.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small).clickable { viewing = p },
                )
                Text(formatShortDate(p.date), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    viewing?.let { p ->
        Dialog(onDismissRequest = { viewing = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatLongDate(p.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { toDelete = p; viewing = null }) { Icon(Icons.Outlined.Delete, "Supprimer la photo") }
                    }
                    AsyncImage(
                        model = File(p.path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(MaterialTheme.shapes.medium).clickable { viewing = null },
                    )
                }
            }
        }
    }
    toDelete?.let { p ->
        ConfirmDialog(
            title = "Supprimer cette photo ?",
            message = "Photo du ${formatLongDate(p.date)}",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = { vm.deletePhoto(p); toDelete = null },
        )
    }
}
