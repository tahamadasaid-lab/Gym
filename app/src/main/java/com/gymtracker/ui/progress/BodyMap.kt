package com.gymtracker.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class BodyRegion { SHOULDERS, CHEST, ABS, ARMS, BACK, GLUTES, LEGS, CALVES }

/** Zones du corps correspondant à une catégorie (selon son nom). */
fun regionsFor(group: String): Set<BodyRegion> {
    val g = group.lowercase(Locale.FRENCH)
    return when {
        g.startsWith("pec") || "poitrine" in g -> setOf(BodyRegion.CHEST)
        g.startsWith("dos") || "lomb" in g || "trap" in g || "dorsa" in g -> setOf(BodyRegion.BACK)
        g.startsWith("épaul") || g.startsWith("epaul") || "delto" in g -> setOf(BodyRegion.SHOULDERS)
        g.startsWith("bras") || "biceps" in g || "triceps" in g || "avant-bras" in g -> setOf(BodyRegion.ARMS)
        g.startsWith("abdo") || "gainage" in g || "core" in g -> setOf(BodyRegion.ABS)
        "fess" in g || "glute" in g -> setOf(BodyRegion.GLUTES)
        "mollet" in g -> setOf(BodyRegion.CALVES)
        g.startsWith("jamb") -> setOf(BodyRegion.LEGS, BodyRegion.GLUTES, BodyRegion.CALVES)
        "quadri" in g || "cuisse" in g || "ischio" in g -> setOf(BodyRegion.LEGS)
        else -> emptySet()
    }
}

/** Nombre de séries par zone du corps. */
fun regionLoad(setsByGroup: Map<String, Int>): Map<BodyRegion, Int> {
    val out = mutableMapOf<BodyRegion, Int>()
    setsByGroup.forEach { (group, sets) -> regionsFor(group).forEach { out[it] = (out[it] ?: 0) + sets } }
    return out
}

private class Part(val region: BodyRegion?, val x: Float, val y: Float, val w: Float, val h: Float, val round: Float = 6f)

// Coordonnées dans un repère 100 × 210.
private val front = listOf(
    Part(BodyRegion.SHOULDERS, 17f, 32f, 18f, 18f, 9f), Part(BodyRegion.SHOULDERS, 65f, 32f, 18f, 18f, 9f),
    Part(BodyRegion.CHEST, 32f, 34f, 17f, 22f), Part(BodyRegion.CHEST, 51f, 34f, 17f, 22f),
    Part(BodyRegion.ABS, 36f, 58f, 28f, 34f),
    Part(BodyRegion.ARMS, 15f, 50f, 11f, 30f), Part(BodyRegion.ARMS, 74f, 50f, 11f, 30f),
    Part(BodyRegion.ARMS, 12f, 82f, 10f, 30f), Part(BodyRegion.ARMS, 78f, 82f, 10f, 30f),
    Part(null, 34f, 92f, 32f, 12f),
    Part(BodyRegion.LEGS, 33f, 104f, 16f, 48f), Part(BodyRegion.LEGS, 51f, 104f, 16f, 48f),
    Part(BodyRegion.CALVES, 35f, 155f, 12f, 44f), Part(BodyRegion.CALVES, 53f, 155f, 12f, 44f),
)
private val back = listOf(
    Part(BodyRegion.SHOULDERS, 17f, 32f, 18f, 18f, 9f), Part(BodyRegion.SHOULDERS, 65f, 32f, 18f, 18f, 9f),
    Part(BodyRegion.BACK, 32f, 34f, 36f, 36f), Part(BodyRegion.BACK, 37f, 72f, 26f, 20f),
    Part(BodyRegion.ARMS, 15f, 50f, 11f, 30f), Part(BodyRegion.ARMS, 74f, 50f, 11f, 30f),
    Part(BodyRegion.ARMS, 12f, 82f, 10f, 30f), Part(BodyRegion.ARMS, 78f, 82f, 10f, 30f),
    Part(BodyRegion.GLUTES, 34f, 92f, 32f, 20f, 10f),
    Part(BodyRegion.LEGS, 33f, 114f, 16f, 38f), Part(BodyRegion.LEGS, 51f, 114f, 16f, 38f),
    Part(BodyRegion.CALVES, 35f, 155f, 12f, 44f), Part(BodyRegion.CALVES, 53f, 155f, 12f, 44f),
)

/** Silhouettes face / dos colorées selon le nombre de séries de la période. */
@Composable
fun BodyMap(load: Map<BodyRegion, Int>, modifier: Modifier = Modifier) {
    val hot = MaterialTheme.colorScheme.primary
    val cold = MaterialTheme.colorScheme.surfaceContainerHighest
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val label = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val max = (load.values.maxOrNull() ?: 0).coerceAtLeast(1)

    fun colorFor(region: BodyRegion?): Color = when {
        region == null -> neutral
        (load[region] ?: 0) == 0 -> cold
        else -> hot.copy(alpha = 0.3f + 0.7f * (load[region]!!.toFloat() / max))
    }

    Canvas(modifier.fillMaxWidth().height(260.dp)) {
        val figW = size.width / 2
        val scale = minOf(figW / 110f, (size.height - 24.dp.toPx()) / 210f)
        fun DrawScope.figure(parts: List<Part>, originX: Float, title: String) {
            val ox = originX + (figW - 100f * scale) / 2
            val oy = 20.dp.toPx()
            drawCircle(neutral, radius = 11f * scale, center = Offset(ox + 50f * scale, oy + 16f * scale))
            parts.forEach { p ->
                drawRoundRect(
                    colorFor(p.region),
                    topLeft = Offset(ox + p.x * scale, oy + p.y * scale),
                    size = Size(p.w * scale, p.h * scale),
                    cornerRadius = CornerRadius(p.round * scale),
                )
            }
            val t = measurer.measure(title, label)
            drawText(t, topLeft = Offset(originX + (figW - t.size.width) / 2, 0f))
        }
        figure(front, 0f, "Face")
        figure(back, figW, "Dos")
    }
}
