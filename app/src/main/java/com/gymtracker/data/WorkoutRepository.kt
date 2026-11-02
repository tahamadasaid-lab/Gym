package com.gymtracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()
    private val programDao = db.programDao()
    private val bodyDao = db.bodyWeightDao()

    // ---------------------------------------------------------------- Exercices
    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises()
    fun observeTrainedExerciseIds(): Flow<List<Long>> = dao.observeTrainedExerciseIds()

    /** Crée ou met à jour un exercice. Retourne son id, ou null si le nom existe déjà. */
    suspend fun saveExercise(exercise: Exercise): Long? {
        val existing = dao.findExerciseByName(exercise.name.trim())
        if (existing != null && existing.id != exercise.id) return null
        val clean = exercise.copy(
            name = exercise.name.trim(),
            muscleGroup = exercise.muscleGroup.trim().ifEmpty { "Autre" },
        )
        return if (clean.id == 0L) dao.insertExercise(clean) else { dao.updateExercise(clean); clean.id }
    }

    suspend fun deleteExercise(exercise: Exercise) = dao.deleteExercise(exercise)
    suspend fun updateRest(exerciseId: Long, seconds: Int) = dao.updateRest(exerciseId, seconds)
    suspend fun updateExerciseImage(exerciseId: Long, path: String?) = dao.updateImage(exerciseId, path)

    suspend fun findOrCreateExercise(name: String, muscleGroup: String = "Autre"): Long {
        dao.findExerciseByName(name.trim())?.let { return it.id }
        return dao.insertExercise(Exercise(name = name.trim(), muscleGroup = muscleGroup.ifBlank { "Autre" }))
    }

    // ---------------------------------------------------------------- Séances
    fun observeActiveSession(): Flow<WorkoutSession?> = dao.observeActiveSession()
    fun observeSession(id: Long): Flow<SessionWithExercises?> = dao.observeSessionWithExercises(id)
    fun observeFinishedSessions(): Flow<List<SessionWithExercises>> = dao.observeFinishedSessions()

    suspend fun startSession(): Long = dao.insertSession(WorkoutSession(startedAt = System.currentTimeMillis()))

    suspend fun startSessionFromProgram(programId: Long): Long = db.withTransaction {
        val program = programDao.getProgram(programId)
        val sessionId = dao.insertSession(WorkoutSession(startedAt = System.currentTimeMillis()))
        program?.orderedItems?.forEachIndexed { i, it ->
            dao.insertSessionExercise(
                SessionExercise(
                    sessionId = sessionId,
                    exerciseId = it.exercise.id,
                    position = i,
                    targetSets = it.item.targetSets,
                )
            )
        }
        sessionId
    }

    suspend fun finishSession(id: Long) = dao.finishSession(id, System.currentTimeMillis())
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long): Long {
        val position = dao.maxPosition(sessionId) + 1
        return dao.insertSessionExercise(SessionExercise(sessionId = sessionId, exerciseId = exerciseId, position = position))
    }

    suspend fun removeSessionExercise(sessionExerciseId: Long) = dao.deleteSessionExercise(sessionExerciseId)

    // ---------------------------------------------------------------- Séries
    suspend fun logSet(sessionId: Long, sessionExerciseId: Long, reps: Int, weightKg: Double) {
        val now = System.currentTimeMillis()
        val rest = dao.lastSetInSession(sessionId)
            ?.let { ((now - it.completedAt) / 1000).toInt() }
            ?.takeIf { it in 0..MAX_TRACKED_REST_SECONDS }
        dao.insertSet(
            WorkoutSet(
                sessionExerciseId = sessionExerciseId,
                reps = reps,
                weightKg = weightKg,
                completedAt = now,
                restSeconds = rest,
            )
        )
    }

    /** Ajoute une série a posteriori (séance déjà terminée). */
    suspend fun addSetAfterwards(sessionExerciseId: Long, reps: Int, weightKg: Double) {
        val base = dao.lastCompletedAt(sessionExerciseId) ?: dao.sessionStartFor(sessionExerciseId) ?: System.currentTimeMillis()
        dao.insertSet(
            WorkoutSet(
                sessionExerciseId = sessionExerciseId,
                reps = reps,
                weightKg = weightKg,
                completedAt = base + 1000,
                restSeconds = null,
            )
        )
    }

    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set)
    suspend fun deleteSet(set: WorkoutSet) = dao.deleteSet(set)

    suspend fun previousSets(exerciseId: Long, excludeSessionId: Long): List<WorkoutSet> =
        dao.previousSets(exerciseId, excludeSessionId)

    fun observeExerciseHistory(exerciseId: Long): Flow<List<SetHistoryRow>> =
        dao.observeExerciseHistory(exerciseId)

    // ---------------------------------------------------------------- Programmes
    fun observePrograms(): Flow<List<ProgramWithExercises>> = programDao.observePrograms()
    suspend fun getProgram(id: Long): ProgramWithExercises? = programDao.getProgram(id)
    suspend fun deleteProgram(id: Long) = programDao.deleteProgram(id)

    /** items = (exerciseId, séries visées), dans l'ordre. Retourne l'id du programme. */
    suspend fun saveProgram(id: Long?, name: String, items: List<Pair<Long, Int>>): Long = db.withTransaction {
        val programId = if (id == null) {
            programDao.insertProgram(Program(name = name.trim()))
        } else {
            programDao.updateProgram(Program(id = id, name = name.trim()))
            programDao.clearProgramExercises(id)
            id
        }
        programDao.insertProgramExercises(
            items.mapIndexed { i, (exerciseId, sets) ->
                ProgramExercise(programId = programId, exerciseId = exerciseId, position = i, targetSets = sets)
            }
        )
        programId
    }

    suspend fun saveSessionAsProgram(session: SessionWithExercises, name: String): Long =
        saveProgram(
            id = null,
            name = name,
            items = session.orderedExercises.map {
                it.exercise.id to (it.targetSets())
            },
        )

    private fun SessionExerciseWithSets.targetSets(): Int =
        sets.size.takeIf { it > 0 } ?: sessionExercise.targetSets ?: 3

    // ---------------------------------------------------------------- Poids de corps
    fun observeBodyWeights(): Flow<List<BodyWeight>> = bodyDao.observeAll()
    suspend fun addBodyWeight(weightKg: Double, date: Long = System.currentTimeMillis()) =
        bodyDao.insert(BodyWeight(date = date, weightKg = weightKg))
    suspend fun deleteBodyWeight(entry: BodyWeight) = bodyDao.delete(entry)

    // ---------------------------------------------------------------- Export / import CSV
    suspend fun exportCsv(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val sessions = dao.getAllSessions()
        val weights = bodyDao.getAll()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // BOM : accents corrects dans Excel
            w.write(CsvCodec.header)
            w.write("\n")
            CsvCodec.encode(sessions, weights).forEach { w.write(it); w.write("\n") }
        }
        sessions.size
    }

    data class ImportResult(val sessions: Int, val skipped: Int, val bodyWeights: Int)

    suspend fun importCsv(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val lines = input.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        val parsed = CsvCodec.decode(lines)
        db.withTransaction {
            var imported = 0
            var skipped = 0
            parsed.sessions.forEach { s ->
                if (dao.countSessionsStartedAt(s.startedAt) > 0) {
                    skipped++
                    return@forEach
                }
                val sessionId = dao.insertSession(WorkoutSession(startedAt = s.startedAt, endedAt = s.endedAt))
                s.exercises.forEachIndexed { pos, ex ->
                    val exerciseId = findOrCreateExercise(ex.name, ex.muscleGroup)
                    val seId = dao.insertSessionExercise(
                        SessionExercise(sessionId = sessionId, exerciseId = exerciseId, position = pos)
                    )
                    dao.insertSets(
                        ex.sets.map {
                            WorkoutSet(
                                sessionExerciseId = seId,
                                reps = it.reps,
                                weightKg = it.weightKg,
                                completedAt = it.completedAt,
                                restSeconds = it.restSeconds,
                            )
                        }
                    )
                }
                imported++
            }
            var weights = 0
            parsed.bodyWeights.forEach {
                if (bodyDao.countAt(it.date) == 0) {
                    bodyDao.insert(it)
                    weights++
                }
            }
            ImportResult(imported, skipped, weights)
        }
    }

    private companion object {
        /** Au-delà de 30 min, on considère que ce n'est plus un temps de repos. */
        const val MAX_TRACKED_REST_SECONDS = 30 * 60
    }
}
