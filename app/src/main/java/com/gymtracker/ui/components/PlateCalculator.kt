@file:OptIn(ExperimentalLayoutApi::class)

package com.gymtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.GymApp
import com.gymtracker.data.PlateLoad
import com.gymtracker.data.StandardPlates
import com.gymtracker.data.computePlates
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight

private val BarOptions = listOf(20.0, 15.0, 10.0, 7.0, 0.0)
private val AllPlates = StandardPlates + 0.5

/** Couleurs des disques (codes couleurs olympiques). */
fun plateColor(kg: Double): Color = when {
    kg >= 25 -> Color(0xFFE53935)
    kg >= 20 -> Color(0xFF1E88E5)
    kg >= 15 -> Color(0xFFFDD835)
    kg >= 10 -> Color(0xFF43A047)
    kg >= 5 -> Color(0xFFECEFF1)
    kg >= 2.5 -> Color(0xFFC62828)
    kg >= 1.25 -> Color(0xFFB0BEC5)
    else -> Color(0xFF78909C)
}

private fun plateHeight(kg: Double): Float = when {
    kg >= 20 -> 1f
    kg >= 15 -> 0.86f
    kg >= 10 -> 0.72f
    kg >= 5 -> 0.56f
    kg >= 2.5 -> 0.46f
    else -> 0.38f
}

private fun plateWidth(kg: Double): Float = when {
    kg >= 25 -> 15f
    kg >= 20 -> 13f
    kg >= 15 -> 11f
    kg >= 10 -> 9f
    kg >= 5 -> 7f
    else -> 5.5f
}

/** Dessin de la barre chargée (vue de face). */
@Composable
fun BarbellView(load: PlateLoad, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val cy = size.height / 2
        val maxH = size.height * 0.95f
        val unit = density
        // Barre
        drawRoundRect(
            barColor,
            topLeft = Offset(0f, cy - 3 * unit),
            size = Size(size.width, 6 * unit),
            cornerRadius = CornerRadius(3 * unit),
        )
        val gap = size.width * 0.34f
        val cx = size.width / 2
        // Butées
        listOf(cx - gap / 2 - 6 * unit, cx + gap / 2).forEach { x ->
            drawRect(barColor, topLeft = Offset(x, cy - 12 * unit), size = Size(6 * unit, 24 * unit))
        }
        var right = cx + gap / 2 + 6 * unit + unit
        var left = cx - gap / 2 - 6 * unit - unit
        load.perSide.forEach { kg ->
            val w = plateWidth(kg) * unit
            val h = maxH * plateHeight(kg)
            val color = plateColor(kg)
            drawRoundRect(color, topLeft = Offset(right, cy - h / 2), size = Size(w, h), cornerRadius = CornerRadius(2 * unit))
            drawRoundRect(color, topLeft = Offset(left - w, cy - h / 2), size = Size(w, h), cornerRadius = CornerRadius(2 * unit))
            right += w + unit
            left -= w + unit
        }
    }
}

fun describeLoad(load: PlateLoad): String = when {
    load.belowBar -> "Poids inférieur à la barre (${formatWeight(load.bar)} kg)"
    load.perSide.isEmpty() -> if (load.bar > 0) "Barre seule" else "Aucun disque"
    else -> "Par côté : " + load.grouped().joinToString(" + ") { (kg, n) -> if (n > 1) "$n × ${formatWeight(kg)}" else formatWeight(kg) } + " kg"
}

/**
 * Calculateur de disques : quels disques mettre de chaque côté de la barre pour un poids donné.
 * La barre et les disques disponibles sont mémorisés dans les réglages.
 */
@Composable
fun PlateCalculatorDialog(
    initialWeight: String,
    onDismiss: () -> Unit,
    onUseWeight: ((Double) -> Unit)? = null,
) {
    val store = (LocalContext.current.applicationContext as GymApp).settings
    val settings by store.settings.collectAsStateWithLifecycle()
    var weightText by remember { mutableStateOf(initialWeight.ifBlank { formatWeight(settings.barWeightKg) }) }
    val target = parseWeight(weightText) ?: 0.0
    val load = computePlates(target, settings.barWeightKg, settings.availablePlates)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disques à charger") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberStepper("Poids total (kg)", weightText, { weightText = it }, step = 2.5, decimal = true)
                BarbellView(load)
                Text(describeLoad(load), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!load.exact && !load.belowBar) {
                    Text(
                        "⚠ ${formatWeight(Math.round(load.remainder * 100) / 100.0)} kg impossibles à charger avec tes disques : " +
                            "la barre fera ${formatWeight(load.achieved)} kg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { weightText = formatWeight(load.achieved) }) {
                        Text("Arrondir à ${formatWeight(load.achieved)} kg")
                    }
                }
                Text("Barre", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BarOptions.forEach { b ->
                        FilterChip(
                            selected = settings.barWeightKg == b,
                            onClick = { store.update { it.copy(barWeightKg = b) } },
                            label = { Text(if (b == 0.0) "Sans barre" else "${formatWeight(b)} kg") },
                        )
                    }
                }
                Text("Disques disponibles", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AllPlates.forEach { p ->
                        val on = p in settings.availablePlates
                        FilterChip(
                            selected = on,
                            onClick = {
                                store.update {
                                    it.copy(availablePlates = if (on) it.availablePlates - p else (it.availablePlates + p).sortedDescending())
                                }
                            },
                            label = { Text(formatWeight(p)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (onUseWeight != null) {
                TextButton(onClick = { onUseWeight(target) }, enabled = target > 0) { Text("Utiliser ce poids") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
        dismissButton = if (onUseWeight != null) {
            { TextButton(onClick = onDismiss) { Text("Fermer") } }
        } else null,
    )
}
