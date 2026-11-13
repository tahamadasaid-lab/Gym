package com.gymtracker.data

import java.util.Locale
import kotlin.math.roundToInt

/** Conseil pour la prochaine séance d'un exercice. */
data class Suggestion(
    val weightKg: Double,
    val reps: Int,
    val increase: Boolean,
    val reason: String,
)

/** Pas d'augmentation de charge selon le poids et la catégorie. */
fun weightIncrement(weightKg: Double, muscleGroup: String): Double {
    val g = muscleGroup.lowercase(Locale.FRENCH)
    return when {
        weightKg < 10 -> 1.0
        g.startsWith("bras") || g.startsWith("épaul") || g.startsWith("epaul") || g.startsWith("abdo") -> 1.25
        (g.startsWith("jamb") || g.startsWith("dos")) && weightKg >= 100 -> 5.0
        else -> 2.5
    }
}

private fun fw(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.FRENCH, "%.2f", v).trimEnd('0').trimEnd(',')

/**
 * Progression conseillée à partir de la séance précédente :
 * - toutes les répétitions réussies à la charge de travail (et pas d'échec signalé) → + un palier ;
 * - sinon → même charge, en visant les répétitions manquées.
 * Les échauffements et les dégressives sont ignorés ; le RPE affine la décision s'il est renseigné.
 */
fun suggestNext(previous: List<WorkoutSet>, muscleGroup: String): Suggestion? {
    val work = previous.filter { it.isWork && !it.isDropSet }
    if (work.isEmpty()) return null
    val top = work.maxOf { it.weightKg }
    val topSets = work.filter { it.weightKg == top }
    val targetReps = topSets.first().reps
    val minReps = topSets.minOf { it.reps }
    val rpes = topSets.mapNotNull { it.rpe }
    val failure = rpes.any { it >= 10 }
    val easy = rpes.isNotEmpty() && rpes.all { it <= 8 }

    if (top == 0.0) {
        return Suggestion(0.0, targetReps + 1, true, "Au poids du corps : vise une répétition de plus que la dernière fois.")
    }
    val success = (minReps >= targetReps && !failure) || (easy && minReps >= targetReps - 1)
    return if (success) {
        val inc = weightIncrement(top, muscleGroup)
        val why = if (easy) "effort modéré (RPE ≤ 8)" else "toutes tes reps réussies"
        Suggestion(top + inc, targetReps, true, "Dernière fois : ${topSets.size} × $targetReps à ${fw(top)} kg, $why → +${fw(inc)} kg.")
    } else {
        Suggestion(
            top, targetReps, false,
            "Dernière fois : $minReps reps au lieu de $targetReps à ${fw(top)} kg. Garde ce poids et vise $targetReps reps partout.",
        )
    }
}

/** Une série d'échauffement proposée. */
data class WarmupStep(val weightKg: Double, val reps: Int, val label: String)

/**
 * Échauffement progressif vers une charge de travail : barre à vide, puis ~40 %, 60 %, 80 %.
 * Les charges sont arrondies à ce qui est réellement chargeable avec les disques disponibles.
 */
fun warmupPlan(workWeight: Double, bar: Double, plates: List<Double>): List<WarmupStep> {
    if (workWeight <= 0) return emptyList()
    fun loadable(target: Double) = computePlates(target, bar, plates).let { if (it.belowBar) bar else it.achieved }
    val steps = mutableListOf<WarmupStep>()
    if (bar > 0 && workWeight > bar * 1.5) steps += WarmupStep(bar, 10, "Barre à vide")
    listOf(0.4 to 8, 0.6 to 5, 0.8 to 3).forEach { (pct, reps) ->
        val w = loadable(workWeight * pct)
        if (w > (steps.lastOrNull()?.weightKg ?: 0.0) && w < workWeight) {
            steps += WarmupStep(w, reps, "${(pct * 100).roundToInt()} %")
        }
    }
    return steps
}
