package com.gymtracker.data

import kotlin.math.abs
import kotlin.math.roundToLong

/** Disques standard (kg) proposés par défaut. */
val StandardPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/**
 * Résultat du calcul des disques : liste des disques à mettre **de chaque côté** (du plus lourd au plus léger),
 * poids réellement obtenu et reste non chargeable.
 */
data class PlateLoad(
    val target: Double,
    val bar: Double,
    val perSide: List<Double>,
) {
    val achieved: Double get() = bar + 2 * perSide.sum()
    val remainder: Double get() = target - achieved
    val exact: Boolean get() = abs(remainder) < 1e-6
    val belowBar: Boolean get() = target < bar - 1e-6

    /** Regroupe : 20 kg × 2, 5 kg × 1… */
    fun grouped(): List<Pair<Double, Int>> =
        perSide.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
}

/**
 * Calcule les disques à charger par côté (algorithme glouton, disques en quantité illimitée).
 * Travaille en centièmes de kg pour éviter les erreurs d'arrondi.
 */
fun computePlates(target: Double, bar: Double, available: List<Double>): PlateLoad {
    if (target <= bar) return PlateLoad(target, bar, emptyList())
    var remaining = ((target - bar) / 2 * 100).roundToLong()
    val plates = available.filter { it > 0 }.sortedDescending()
    val result = mutableListOf<Double>()
    for (p in plates) {
        val c = (p * 100).roundToLong()
        while (remaining >= c) {
            result += p
            remaining -= c
        }
    }
    return PlateLoad(target, bar, result)
}
