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
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.gymtracker.data.CalorieCalculator
import com.gymtracker.data.Exercise
import com.gymtracker.data.MuscleGroup
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

/** Pictogrammes proposés pour les catégories (clé enregistrée en base → icône). */
val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "dumbbell" to Icons.Filled.FitnessCenter,
    "rowing" to Icons.Filled.Rowing,
    "run" to Icons.AutoMirrored.Filled.DirectionsRun,
    "gym" to Icons.Filled.SportsGymnastics,
    "mma" to Icons.Filled.SportsMma,
    "yoga" to Icons.Filled.SelfImprovement,
    "heart" to Icons.Filled.MonitorHeart,
    "bolt" to Icons.Filled.Bolt,
    "fire" to Icons.Filled.LocalFireDepartment,
    "body" to Icons.Filled.AccessibilityNew,
    "hiking" to Icons.Filled.Hiking,
    "bike" to Icons.AutoMirrored.Filled.DirectionsBike,
    "pool" to Icons.Filled.Pool,
    "handball" to Icons.Filled.SportsHandball,
    "star" to Icons.Filled.Star,
)

fun categoryIcon(key: String): ImageVector = CategoryIcons.firstOrNull { it.first == key }?.second ?: Icons.Filled.Bolt

/** Couleurs proposées pour les catégories. */
val CategoryColors: List<Int> = listOf(
    0xFFFF7A45, 0xFFFF5C7A, 0xFFFFC857, 0xFF7CD992, 0xFF3DCB7F, 0xFF4DD0E1,
    0xFF4FC3F7, 0xFF5C8DFF, 0xFFC792EA, 0xFFFF8AD8, 0xFFB0855A, 0xFF9AA8B5,
).map { it.toInt() }

/** Catégories connues (nom → catégorie), fournies à la racine de l'appli. */
val LocalMuscleGroups = staticCompositionLocalOf<Map<String, MuscleGroup>> { emptyMap() }

/** Calcul des calories (poids de l'utilisateur + intensité des catégories), fourni à la racine de l'appli. */
val LocalCalories = staticCompositionLocalOf { CalorieCalculator.Default }

/** Style d'un groupe : celui de la catégorie personnalisée si elle existe, sinon le style par défaut. */
@Composable
fun rememberMuscleStyle(group: String): MuscleStyle {
    val cat = LocalMuscleGroups.current[group.lowercase()]
    return if (cat != null) MuscleStyle(Color(cat.colorArgb), categoryIcon(cat.icon)) else muscleStyle(group)
}

/** Visuel d'un exercice : la photo choisie, sinon le pictogramme coloré de son groupe musculaire. */
@Composable
fun ExerciseAvatar(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val style = rememberMuscleStyle(exercise.muscleGroup)
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
    val style = rememberMuscleStyle(group)
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

/** Affiche une estimation de calories, ex. « ≈ 245 kcal ». */
fun formatKcal(kcal: Double): String = "≈ ${kcal.toInt()} kcal"

/** Avatar coloré d'une catégorie. */
@Composable
fun CategoryAvatar(group: MuscleGroup, size: Dp = 44.dp) {
    val color = Color(group.colorArgb)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.14f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(group.icon), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
