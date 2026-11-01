package com.gymtracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val longDate = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH)
private val shortDate = SimpleDateFormat("dd/MM", Locale.FRENCH)
private val time = SimpleDateFormat("HH:mm", Locale.FRENCH)

fun formatLongDate(ms: Long): String = longDate.format(Date(ms)).replaceFirstChar { it.titlecase(Locale.FRENCH) }
fun formatShortDate(ms: Long): String = shortDate.format(Date(ms))
fun formatTime(ms: Long): String = time.format(Date(ms))

/** 80.0 -> "80", 82.5 -> "82,5" */
fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString()
    else String.format(Locale.FRENCH, "%.2f", kg).trimEnd('0').trimEnd(',')

fun formatVolume(kg: Double): String = String.format(Locale.FRENCH, "%,d kg", kg.toLong())

/** 95 -> "1:35", 3725 -> "1:02:05" */
fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.FRENCH, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.FRENCH, "%d:%02d", m, sec)
}

fun formatDurationHuman(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
}

fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
