package com.gymtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gymtracker.util.formatShortDate

data class ChartPoint(val date: Long, val value: Double)

/** Graphique linéaire simple dessiné à la main (pas de dépendance externe). */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas
        val leftPad = 48.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 12.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad

        var minV = points.minOf { it.value }
        var maxV = points.maxOf { it.value }
        if (maxV - minV < 1e-6) { minV -= 1.0; maxV += 1.0 }
        val margin = (maxV - minV) * 0.1
        minV = (minV - margin).coerceAtLeast(0.0)
        maxV += margin

        // Grille horizontale + étiquettes
        val steps = 4
        for (i in 0..steps) {
            val v = minV + (maxV - minV) * i / steps
            val y = topPad + h - (h * i / steps)
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
            val layout = measurer.measure(formatValue(v), labelStyle)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }

        fun x(i: Int) = if (points.size == 1) leftPad + w / 2 else leftPad + w * i / (points.size - 1)
        fun y(v: Double) = topPad + h - ((v - minV) / (maxV - minV) * h).toFloat()

        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value))
        }
        if (points.size > 1) {
            val fill = Path().apply {
                addPath(path)
                lineTo(x(points.lastIndex), topPad + h)
                lineTo(x(0), topPad + h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f)), startY = topPad, endY = topPad + h))
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEachIndexed { i, p ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(i), y(p.value)))
        }

        // Dates : première, dernière (et milieu si assez de points)
        val labelIdx = buildSet {
            add(0); add(points.lastIndex)
            if (points.size >= 5) add(points.lastIndex / 2)
        }
        labelIdx.forEach { i ->
            val layout = measurer.measure(formatShortDate(points[i].date), labelStyle)
            val lx = (x(i) - layout.size.width / 2f).coerceIn(leftPad - layout.size.width / 2f, size.width - layout.size.width.toFloat())
            drawText(layout, topLeft = Offset(lx, topPad + h + 4.dp.toPx()))
        }
    }
}
