package com.gymtracker.data

/** Estimation du 1RM (formule d'Epley). */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
    reps <= 0 -> 0.0
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}

val WorkoutSet.volume: Double get() = weightKg * reps

/** Temps actif estimé passé sur un exercice (exécution + repos), utilisé pour les calories. */
data class Effort(val muscleGroup: String, val activeSeconds: Long)

/**
 * Durée attribuée à une série : le repos réel mesuré avant elle (il inclut l'exécution),
 * plafonné à 10 min ; sinon (première série, série ajoutée après coup) une estimation.
 */
fun WorkoutSet.attributedSeconds(): Long =
    restSeconds?.toLong()?.coerceIn(0, 600) ?: (reps * 4L + 60)

fun SessionExerciseWithSets.effort(): Effort =
    Effort(exercise.muscleGroup, sets.sumOf { it.attributedSeconds() })

fun SessionWithExercises.efforts(): List<Effort> =
    exercises.filter { it.sets.isNotEmpty() }.map { it.effort() }

/**
 * Estimation des calories : kcal = MET × poids (kg) × durée (h).
 * Le MET dépend de la catégorie de l'exercice (réglable dans les catégories).
 */
class CalorieCalculator(
    val bodyWeightKg: Double,
    private val metByGroup: Map<String, Double>,
) {
    fun met(group: String): Double = metByGroup[group.lowercase()] ?: DefaultCategories.DEFAULT_MET
    fun kcal(effort: Effort): Double = met(effort.muscleGroup) * bodyWeightKg * effort.activeSeconds / 3600.0
    fun kcal(efforts: List<Effort>): Double = efforts.sumOf { kcal(it) }

    companion object {
        val Default = CalorieCalculator(75.0, emptyMap())
        fun of(bodyWeightKg: Double, groups: List<MuscleGroup>) =
            CalorieCalculator(bodyWeightKg, groups.associate { it.name.lowercase() to it.met })
    }
}

data class SessionSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseNames: List<String>,
    val setCount: Int,
    val volume: Double,
    val averageRestSeconds: Int?,
    val efforts: List<Effort> = emptyList(),
) {
    val durationSeconds: Long get() = ((endedAt ?: System.currentTimeMillis()) - startedAt) / 1000
}

fun SessionWithExercises.summary(): SessionSummary {
    val allSets = exercises.flatMap { it.sets }
    val rests = allSets.mapNotNull { it.restSeconds }
    return SessionSummary(
        id = session.id,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        exerciseNames = orderedExercises.filter { it.sets.isNotEmpty() }.map { it.exercise.name },
        setCount = allSets.size,
        volume = allSets.sumOf { it.volume },
        averageRestSeconds = if (rests.isEmpty()) null else rests.average().toInt(),
        efforts = efforts(),
    )
}

/** Performance d'un exercice sur une séance. */
data class ExerciseSessionStat(
    val sessionId: Long,
    val date: Long,
    val maxWeight: Double,
    val bestOneRepMax: Double,
    val volume: Double,
    val maxReps: Int,
    val totalReps: Int,
    val setCount: Int,
    val bestSetWeight: Double,
    val bestSetReps: Int,
)

fun List<SetHistoryRow>.toSessionStats(): List<ExerciseSessionStat> =
    groupBy { it.sessionId }.map { (sessionId, rows) ->
        val best = rows.maxWith(compareBy<SetHistoryRow> { it.weightKg }.thenBy { it.reps })
        ExerciseSessionStat(
            sessionId = sessionId,
            date = rows.first().date,
            maxWeight = best.weightKg,
            bestOneRepMax = rows.maxOf { estimatedOneRepMax(it.weightKg, it.reps) },
            volume = rows.sumOf { it.weightKg * it.reps },
            maxReps = rows.maxOf { it.reps },
            totalReps = rows.sumOf { it.reps },
            setCount = rows.size,
            bestSetWeight = best.weightKg,
            bestSetReps = best.reps,
        )
    }.sortedBy { it.date }
