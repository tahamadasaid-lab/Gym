package com.gymtracker.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Format CSV (séparateur « ; », décimales « , ») lisible dans Excel / LibreOffice / Google Sheets.
 * Une ligne par série, plus une ligne par pesée.
 */
object CsvCodec {
    private const val SEP = ';'
    const val header = "type;debut_seance;fin_seance;exercice;groupe;serie;poids_kg;reps;repos_s;heure_serie;degressive;superserie;echauffement;rpe"

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    data class ParsedSet(
        val weightKg: Double,
        val reps: Int,
        val restSeconds: Int?,
        val completedAt: Long,
        val isDropSet: Boolean = false,
        val isWarmup: Boolean = false,
        val rpe: Int? = null,
    )
    data class ParsedExercise(
        val name: String,
        val muscleGroup: String,
        val sets: MutableList<ParsedSet> = mutableListOf(),
        val supersetGroup: Long? = null,
    )
    data class ParsedSession(val startedAt: Long, val endedAt: Long?, val exercises: MutableList<ParsedExercise> = mutableListOf())
    data class Parsed(val sessions: List<ParsedSession>, val bodyWeights: List<BodyWeight>)

    fun encode(sessions: List<SessionWithExercises>, weights: List<BodyWeight>): List<String> {
        val df = dateFormat()
        fun d(ms: Long?) = ms?.let { df.format(Date(it)) } ?: ""
        val rows = mutableListOf<String>()
        sessions.forEach { s ->
            s.orderedExercises.forEach { ex ->
                ex.orderedSets.forEachIndexed { i, set ->
                    rows += row(
                        "SERIE", d(s.session.startedAt), d(s.session.endedAt), ex.exercise.name, ex.exercise.muscleGroup,
                        "${i + 1}", num(set.weightKg), "${set.reps}", set.restSeconds?.toString() ?: "", d(set.completedAt),
                        if (set.isDropSet) "1" else "0", ex.sessionExercise.supersetGroup?.toString() ?: "",
                        if (set.isWarmup) "1" else "0", set.rpe?.toString() ?: "",
                    )
                }
            }
        }
        weights.forEach { rows += row("POIDS_CORPS", d(it.date), "", "", "", "", num(it.weightKg), "", "", "", "", "", "", "") }
        return rows
    }

    fun decode(lines: List<String>): Parsed {
        val df = dateFormat()
        fun date(s: String): Long? = s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { df.parse(it)?.time }.getOrNull() }
        val sessions = linkedMapOf<Long, ParsedSession>()
        val weights = mutableListOf<BodyWeight>()
        lines.forEach { raw ->
            val line = raw.removePrefix("﻿")
            if (line.isBlank()) return@forEach
            val f = parseLine(line)
            when (f.getOrNull(0)?.trim()?.uppercase()) {
                "SERIE" -> {
                    val start = date(f.getOrElse(1) { "" }) ?: return@forEach
                    val name = f.getOrElse(3) { "" }.trim().ifEmpty { return@forEach }
                    val reps = f.getOrElse(7) { "" }.trim().toIntOrNull() ?: return@forEach
                    val weight = f.getOrElse(6) { "" }.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                    val session = sessions.getOrPut(start) { ParsedSession(start, date(f.getOrElse(2) { "" })) }
                    val ex = session.exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: ParsedExercise(
                            name = name,
                            muscleGroup = f.getOrElse(4) { "" }.trim(),
                            supersetGroup = f.getOrElse(11) { "" }.trim().toLongOrNull(),
                        ).also { session.exercises += it }
                    ex.sets += ParsedSet(
                        weightKg = weight,
                        reps = reps,
                        restSeconds = f.getOrElse(8) { "" }.trim().toIntOrNull(),
                        completedAt = date(f.getOrElse(9) { "" }) ?: (start + ex.sets.size * 1000L),
                        isDropSet = f.getOrElse(10) { "" }.trim() == "1",
                        isWarmup = f.getOrElse(12) { "" }.trim() == "1",
                        rpe = f.getOrElse(13) { "" }.trim().toIntOrNull(),
                    )
                }
                "POIDS_CORPS" -> {
                    val d = date(f.getOrElse(1) { "" }) ?: return@forEach
                    val w = f.getOrElse(6) { "" }.trim().replace(',', '.').toDoubleOrNull() ?: return@forEach
                    weights += BodyWeight(date = d, weightKg = w)
                }
            }
        }
        // Séance non terminée dans le fichier : on la considère finie à sa dernière série.
        val fixed = sessions.values.map { s ->
            if (s.endedAt != null) s
            else s.copy(endedAt = s.exercises.flatMap { it.sets }.maxOfOrNull { it.completedAt } ?: s.startedAt)
        }
        return Parsed(fixed, weights)
    }

    private fun num(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

    private fun row(vararg fields: String): String = fields.joinToString(SEP.toString()) { escape(it) }

    private fun escape(s: String): String =
        if (s.any { it == SEP || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == SEP -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
