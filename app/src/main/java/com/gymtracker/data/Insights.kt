package com.gymtracker.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ------------------------------------------------------------------------------------------ Records

data class RepMax(val reps: Int, val weightKg: Double, val actualReps: Int, val date: Long)

data class ExerciseRecords(
    val exerciseId: Long,
    val bestWeight: Double,
    val bestWeightDate: Long,
    val bestOneRepMax: Double,
    val bestOneRepMaxDate: Long,
    val bestVolume: Double,
    val bestVolumeDate: Long,
    val repMaxes: List<RepMax>,
    val sessionCount: Int,
    val lastDate: Long,
)

val RepMaxTargets = listOf(1, 3, 5, 8, 10, 12)

fun computeRecords(rows: List<WorkSetRow>): Map<Long, ExerciseRecords> =
    rows.groupBy { it.exerciseId }.mapValues { (exerciseId, list) ->
        val bestW = list.maxWith(compareBy<WorkSetRow> { it.weightKg }.thenBy { it.reps }.thenBy { -it.date })
        val best1 = list.maxBy { estimatedOneRepMax(it.weightKg, it.reps) }
        val volumes = list.groupBy { it.sessionId }.map { (_, s) -> s.first().date to s.sumOf { it.weightKg * it.reps } }
        val bestVol = volumes.maxBy { it.second }
        val repMaxes = RepMaxTargets.mapNotNull { r ->
            list.filter { it.reps >= r }
                .maxWithOrNull(compareBy<WorkSetRow> { it.weightKg }.thenBy { -it.date })
                ?.let { RepMax(r, it.weightKg, it.reps, it.date) }
        }
        ExerciseRecords(
            exerciseId = exerciseId,
            bestWeight = bestW.weightKg,
            bestWeightDate = bestW.date,
            bestOneRepMax = estimatedOneRepMax(best1.weightKg, best1.reps),
            bestOneRepMaxDate = best1.date,
            bestVolume = bestVol.second,
            bestVolumeDate = bestVol.first,
            repMaxes = repMaxes,
            sessionCount = list.map { it.sessionId }.distinct().size,
            lastDate = list.maxOf { it.date },
        )
    }

/** Nouveau record battu par une série ? */
sealed interface RecordEvent {
    val exerciseName: String
    data class Weight(override val exerciseName: String, val weightKg: Double, val previous: Double) : RecordEvent
    data class OneRepMax(override val exerciseName: String, val oneRepMax: Double, val previous: Double) : RecordEvent
}

fun detectRecord(exerciseName: String, history: List<WeightReps>, weightKg: Double, reps: Int): RecordEvent? {
    if (history.isEmpty() || weightKg <= 0 || reps <= 0) return null
    val maxW = history.maxOf { it.weightKg }
    if (weightKg > maxW + 1e-6) return RecordEvent.Weight(exerciseName, weightKg, maxW)
    val max1 = history.maxOf { estimatedOneRepMax(it.weightKg, it.reps) }
    val e1 = estimatedOneRepMax(weightKg, reps)
    if (e1 > max1 + 0.05) return RecordEvent.OneRepMax(exerciseName, e1, max1)
    return null
}

// ------------------------------------------------------------------------------------------ Bilan

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class PeriodStats(
    val sessions: Int,
    val durationSeconds: Long,
    val sets: Int,
    val volume: Double,
    val efforts: List<Effort>,
    /** Catégorie → nombre de séries de travail. */
    val setsByGroup: Map<String, Int>,
    val volumeByGroup: Map<String, Double>,
)

fun periodStats(sessions: List<SessionWithExercises>): PeriodStats {
    val exercises = sessions.flatMap { it.exercises }
    return PeriodStats(
        sessions = sessions.size,
        durationSeconds = sessions.sumOf { ((it.session.endedAt ?: it.session.startedAt) - it.session.startedAt) / 1000 },
        sets = exercises.sumOf { e -> e.sets.count { it.isWork } },
        volume = exercises.sumOf { e -> e.sets.sumOf { it.volume } },
        efforts = sessions.flatMap { it.efforts() },
        setsByGroup = exercises.groupBy { it.exercise.muscleGroup }
            .mapValues { (_, l) -> l.sumOf { e -> e.sets.count { it.isWork } } }
            .filterValues { it > 0 },
        volumeByGroup = exercises.groupBy { it.exercise.muscleGroup }
            .mapValues { (_, l) -> l.sumOf { e -> e.sets.sumOf { it.volume } } }
            .filterValues { it > 0 },
    )
}

/** Nombre de semaines consécutives (jusqu'à cette semaine, ou la précédente si rien encore cette semaine) avec au moins une séance. */
fun weekStreak(sessionDates: List<Long>, today: LocalDate = LocalDate.now()): Int {
    val weeks = sessionDates.map { it.toLocalDate().weekStart() }.toSet()
    var w = today.weekStart()
    if (w !in weeks) w = w.minusWeeks(1)
    var n = 0
    while (w in weeks) {
        n++
        w = w.minusWeeks(1)
    }
    return n
}

/** Séances par semaine sur les [weeks] dernières semaines (la plus ancienne d'abord). */
fun sessionsPerWeek(sessionDates: List<Long>, weeks: Int = 8, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
    val counts = sessionDates.groupingBy { it.toLocalDate().weekStart() }.eachCount()
    val current = today.weekStart()
    return (weeks - 1 downTo 0).map { i -> current.minusWeeks(i.toLong()).let { it to (counts[it] ?: 0) } }
}

// ------------------------------------------------------------------------------------------ Objectifs

data class GoalProgress(
    val goal: Goal,
    val title: String,
    val current: Double?,
    /** 0..1 */
    val fraction: Float,
    val achieved: Boolean,
)

fun goalProgress(goal: Goal, exerciseName: String?, workSets: List<WorkSetRow>, bodyWeights: List<BodyWeight>): GoalProgress {
    val type = runCatching { GoalType.valueOf(goal.type) }.getOrDefault(GoalType.EXERCISE_WEIGHT)
    val current = when (type) {
        GoalType.EXERCISE_WEIGHT -> workSets.filter { it.exerciseId == goal.exerciseId }.maxOfOrNull { it.weightKg }
        GoalType.BODY_WEIGHT -> bodyWeights.lastOrNull()?.weightKg
    }
    val span = goal.targetValue - goal.startValue
    val fraction = when {
        current == null -> 0f
        span == 0.0 -> 1f
        else -> ((current - goal.startValue) / span).toFloat().coerceIn(0f, 1f)
    }
    val achieved = goal.achievedAt != null || (current != null && (if (span >= 0) current >= goal.targetValue else current <= goal.targetValue))
    val title = when (type) {
        GoalType.EXERCISE_WEIGHT -> exerciseName ?: "Exercice"
        GoalType.BODY_WEIGHT -> "Poids de corps"
    }
    return GoalProgress(goal, title, current, fraction, achieved)
}
