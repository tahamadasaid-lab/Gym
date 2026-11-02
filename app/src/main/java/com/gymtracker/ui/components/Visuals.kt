package com.gymtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.gymtracker.data.Exercise
import java.io.File
import java.util.Locale

data class MuscleStyle(val color: Color, val icon: ImageVector)

/** Couleur et pictogramme associés à un groupe musculaire. */
fun muscleStyle(group: String): MuscleStyle {
    val g = group.lowercase(Locale.FRENCH).trim()
    return when {
        g.startsWith("pec") -> MuscleStyle(Color(0xFFFF7A45), Icons.Filled.FitnessCenter)
        g.startsWith("dos") -> MuscleStyle(Color(0xFF4FC3F7), Icons.Filled.Rowing)
        g.startsWith("jamb") || "cuisse" in g || "fess" in g || "mollet" in g ->
            MuscleStyle(Color(0xFF7CD992), Icons.AutoMirrored.Filled.DirectionsRun)
        g.startsWith("épaul") || g.startsWith("epaul") -> MuscleStyle(Color(0xFFC792EA), Icons.Filled.SportsGymnastics)
        g.startsWith("bras") || "biceps" in g || "triceps" in g -> MuscleStyle(Color(0xFFFFC857), Icons.Filled.SportsMma)
        g.startsWith("abdo") || "gainage" in g -> MuscleStyle(Color(0xFF4DD0E1), Icons.Filled.SelfImprovement)
        g.startsWith("cardio") -> MuscleStyle(Color(0xFFFF5C7A), Icons.Filled.MonitorHeart)
        else -> MuscleStyle(Color(0xFF9AA8B5), Icons.Filled.Bolt)
    }
}

/** Visuel d'un exercice : la photo choisie, sinon le pictogramme coloré de son groupe musculaire. */
@Composable
fun ExerciseAvatar(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val style = muscleStyle(exercise.muscleGroup)
    val shape = RoundedCornerShape(size / 3.2f)
    val base = modifier
        .size(size)
        .clip(shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    val path = exercise.imageUri
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = exercise.name,
            contentScale = ContentScale.Crop,
            modifier = base.background(style.color.copy(alpha = 0.2f)),
        )
    } else {
        Box(
            base.background(
                Brush.linearGradient(listOf(style.color.copy(alpha = 0.38f), style.color.copy(alpha = 0.14f)))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Petite pastille colorée avec le nom du groupe musculaire. */
@Composable
fun MuscleTag(group: String, modifier: Modifier = Modifier) {
    val style = muscleStyle(group)
    Surface(
        color = style.color.copy(alpha = 0.16f),
        contentColor = style.color,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(group, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun ImageViewerDialog(exercise: Exercise, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
                AsyncImage(
                    model = exercise.imageUri?.let { File(it) },
                    contentDescription = exercise.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onDismiss),
                )
            }
        }
    }
}
