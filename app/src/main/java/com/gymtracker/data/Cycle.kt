package com.gymtracker.data

import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/** Semaine en cours d'un programme sur plusieurs semaines (1..weeks), ou null pour un programme classique. */
fun Program.currentWeek(now: Long = System.currentTimeMillis()): Int? {
    val total = weeks ?: return null
    val start = cycleStartedAt ?: return 1
    val elapsed = TimeUnit.MILLISECONDS.toDays(now - start).coerceAtLeast(0)
    return (elapsed / 7 + 1).toInt().coerceIn(1, total)
}

/** Le cycle est-il terminé (au-delà de la dernière semaine) ? */
fun Program.cycleFinished(now: Long = System.currentTimeMillis()): Boolean {
    val total = weeks ?: return false
    val start = cycleStartedAt ?: return false
    return TimeUnit.MILLISECONDS.toDays(now - start) >= total * 7L
}

private fun roundTo(value: Double, step: Double): Double = (value / step).roundToLong() * step

/**
 * Charge prévue pour une semaine : départ + (semaine − 1) × augmentation ;
 * semaine de décharge (dernière, si activée) : 80 % de la semaine précédente.
 */
fun plannedWeight(startWeight: Double, increment: Double, week: Int, weeks: Int, deloadLastWeek: Boolean): Double {
    if (deloadLastWeek && week == weeks && weeks > 1) {
        val previous = startWeight + (week - 2) * increment
        return roundTo(previous * 0.8, 2.5).coerceAtLeast(0.0)
    }
    return startWeight + (week - 1) * increment
}

fun ProgramWithExercises.label(week: Int?): String =
    if (week == null || program.weeks == null) program.name
    else "${program.name} · semaine $week/${program.weeks}" + if (program.deloadLastWeek && week == program.weeks) " (décharge)" else ""
