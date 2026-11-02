package com.gymtracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Calendrier mensuel : les jours d'entraînement sont mis en évidence. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    trainingDays: Set<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onChangeMonth: (Long) -> Unit,
) {
    val today = LocalDate.now()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChangeMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent") }
                Text(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) } +
                        " ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChangeMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant") }
            }
            Row {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1 // lundi = 0
            val days = month.lengthOfMonth()
            val cells = offset + days
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..days) {
                                val date = month.atDay(dayNum)
                                val trained = date in trainingDays
                                val isSelected = date == selected
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (trained) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = if (isSelected || date == today) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary
                                            else if (date == today) MaterialTheme.colorScheme.outline else Color.Transparent,
                                            shape = CircleShape,
                                        )
                                        .clickable { onSelect(date) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (trained) FontWeight.Bold else null,
                                        color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
