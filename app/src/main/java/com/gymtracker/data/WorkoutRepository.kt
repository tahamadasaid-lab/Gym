package com.gymtracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()
    private val programDao = db.programDao()
    private val bodyDao = db.bodyWeightDao()
    private val categoryDao = db.categoryDao()
    private val extrasDao = db.bodyDao()

    // ---------------------------------------------------------------- Notes, RPE, records
    suspend fun updateSessionNotes(sessionId: Long, notes: String) = dao.updateSessionNotes(sessionId, notes)
    suspend fun updateExerciseNotes(exerciseId: Long, notes: String) = dao.updateExerciseNotes(exerciseId, notes)
    suspend fun setRpe(setId: Long, rpe: Int?) = dao.setRpe(setId, rpe)
    suspend fun workSetsExcluding(exerciseId: Long, sessionId: Long): List<WeightReps> = dao.workSetsExcluding(exerciseId, sessionId)
    fun observeWorkSets(): Flow<List<WorkSetRow>> = dao.observeWorkSets()

    // ---------------------------------------------------------------- Objectifs, mensurations, photos
    fun observeGoals(): Flow<List<Goal>> = extrasDao.observeGoals()
    suspend fun addGoal(goal: Goal) = extrasDao.insertGoal(goal)
    suspend fun updateGoal(goal: Goal) = extrasDao.updateGoal(goal)
    suspend fun deleteGoal(goal: Goal) = extrasDao.deleteGoal(goal)
    fun observeMeasurements(): Flow<List<Measurement>> = extrasDao.observeMeasurements()
    suspend fun addMeasurement(type: String, valueCm: Double, date: Long = System.currentTimeMillis()) =
        extrasDao.insertMeasurement(Measurement(date = date, type = type, valueCm = valueCm))
    suspend fun deleteMeasurement(m: Measurement) = extrasDao.deleteMeasurement(m)
    fun observePhotos(): Flow<List<ProgressPhoto>> = extrasDao.observePhotos()
    suspend fun addPhoto(path: String, date: Long = System.currentTimeMillis()) = extrasDao.insertPhoto(ProgressPhoto(date = date, path = path))
    suspend fun deletePhoto(p: ProgressPhoto) = extrasDao.deletePhoto(p)

    // ---------------------------------------------------------------- Catégories
    fun observeCategories(): Flow<List<MuscleGroup>> = categoryDao.observeAll()

    /** Crée la catégorie si elle n'existe pas encore. Retourne son nom tel qu'enregistré. */
    suspend fun ensureCategory(name: String): String {
        val clean = name.trim().ifEmpty { DefaultCategories.OTHER }
        categoryDao.findByName(clean)?.let { return it.name }
        categoryDao.insert(
            MuscleGroup(
                name = clean,
                colorArgb = DefaultCategories.DEFAULT_COLOR,
                icon = DefaultCategories.DEFAULT_ICON,
                met = DefaultCategories.DEFAULT_MET,
                position = categoryDao.maxPosition() + 1,
            )
        )
        return clean
    }

    /** Crée ou modifie une catégorie (le renommage est répercuté sur les exercices). Null si le nom est déjà pris. */
    suspend fun saveCategory(group: MuscleGroup, previousName: String?): Long? = db.withTransaction {
        val clean = group.copy(name = group.name.trim())
        val existing = categoryDao.findByName(clean.name)
        if (existing != null && existing.id != clean.id) return@withTransaction null
        if (clean.id == 0L) {
            categoryDao.insert(clean.copy(position = categoryDao.maxPosition() + 1))
        } else {
            categoryDao.update(clean)
            if (previousName != null && previousName != clean.name) categoryDao.moveExercises(previousName, clean.name)
            clean.id
        }
    }

    /** Supprime une catégorie ; ses exercices passent dans « Autre ». */
    suspend fun deleteCategory(group: MuscleGroup) = db.withTransaction {
        val other = ensureCategory(DefaultCategories.OTHER)
        categoryDao.moveExercises(group.name, other)
        if (!group.name.equals(other, ignoreCase = true)) categoryDao.delete(group)
    }

    // ---------------------------------------------------------------- Exercices
    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises()
    fun observeTrainedExerciseIds(): Flow<List<Long>> = dao.observeTrainedExerciseIds()

    /** Crée ou met à jour un exercice. Retourne son id, ou null si le nom existe déjà. */
    suspend fun saveExercise(exercise: Exercise): Long? {
        val existing = dao.findExerciseByName(exercise.name.trim())
        if (existing != null && existing.id != exercise.id) return null
        val clean = exercise.copy(
            name = exercise.name.trim(),
            muscleGroup = ensureCategory(exercise.muscleGroup),
        )
        return if (clean.id == 0L) dao.insertExercise(clean) else { dao.updateExercise(clean); clean.id }
    }

    suspend fun deleteExercise(exercise: Exercise) = dao.deleteExercise(exercise)
    suspend fun updateRest(exerciseId: Long, seconds: Int) = dao.updateRest(exerciseId, seconds)
    suspend fun updateExerciseImage(exerciseId: Long, path: String?) = dao.updateImage(exerciseId, path)

    suspend fun findOrCreateExercise(name: String, muscleGroup: String = "Autre"): Long {
        dao.findExerciseByName(name.trim())?.let { return it.id }
        return dao.insertExercise(Exercise(name = name.trim(), muscleGroup = ensureCategory(muscleGroup)))
    }

    // ---------------------------------------------------------------- Séances
    fun observeActiveSession(): Flow<WorkoutSession?> = dao.observeActiveSession()
    fun observeSession(id: Long): Flow<SessionWithExercises?> = dao.observeSessionWithExercises(id)
    fun observeFinishedSessions(): Flow<List<SessionWithExercises>> = dao.observeFinishedSessions()

    /** Démarre une séance libre s'il n'y en a pas déjà une en cours (bouton du widget). */
    suspend fun startSessionIfNone() {
        if (dao.observeActiveSession().first() == null) startSession()
    }

    suspend fun startSession(): Long = dao.insertSession(WorkoutSession(startedAt = System.currentTimeMillis()))

    suspend fun startSessionFromProgram(programId: Long): Long = db.withTransaction {
        val program = programDao.getProgram(programId)
        val sessionId = dao.insertSession(WorkoutSession(startedAt = System.currentTimeMillis()))
        val groups = mutableMapOf<Long, MutableList<Long>>()
        program?.orderedItems?.forEachIndexed { i, it ->
            val seId = dao.insertSessionExercise(
                SessionExercise(
                    sessionId = sessionId,
                    exerciseId = it.exercise.id,
                    position = i,
                    targetSets = it.item.targetSets,
                    supersetGroup = null,
                )
            )
            it.item.supersetGroup?.let { g -> groups.getOrPut(g) { mutableListOf() } += seId }
        }
        // Les superséries du programme reçoivent un identifiant propre à la séance.
        groups.values.filter { it.size > 1 }.forEach { ids -> dao.setSupersetGroup(ids, ids.first()) }
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
    /** Lie (group non nul) ou délie (null) des exercices de la séance en supersérie. */
    suspend fun setSuperset(sessionExerciseIds: List<Long>, group: Long?) = dao.setSupersetGroup(sessionExerciseIds, group)

    suspend fun logSet(
        sessionId: Long,
        sessionExerciseId: Long,
        reps: Int,
        weightKg: Double,
        isDropSet: Boolean = false,
        isWarmup: Boolean = false,
    ) {
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
                isDropSet = isDropSet,
                isWarmup = isWarmup,
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

    data class ProgramEntry(val exerciseId: Long, val targetSets: Int, val supersetGroup: Long? = null)

    /** items = (exerciseId, séries visées), dans l'ordre. Retourne l'id du programme. */
    suspend fun saveProgram(id: Long?, name: String, items: List<Pair<Long, Int>>): Long =
        saveProgramEntries(id, name, items.map { ProgramEntry(it.first, it.second) })

    suspend fun saveProgramEntries(id: Long?, name: String, items: List<ProgramEntry>): Long = db.withTransaction {
        val programId = if (id == null) {
            programDao.insertProgram(Program(name = name.trim()))
        } else {
            programDao.updateProgram(Program(id = id, name = name.trim()))
            programDao.clearProgramExercises(id)
            id
        }
        programDao.insertProgramExercises(
            items.mapIndexed { i, e ->
                ProgramExercise(
                    programId = programId,
                    exerciseId = e.exerciseId,
                    position = i,
                    targetSets = e.targetSets,
                    supersetGroup = e.supersetGroup,
                )
            }
        )
        programId
    }

    suspend fun saveSessionAsProgram(session: SessionWithExercises, name: String): Long =
        saveProgramEntries(
            id = null,
            name = name,
            items = session.orderedExercises.map {
                ProgramEntry(it.exercise.id, it.targetSets(), it.sessionExercise.supersetGroup)
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
                        SessionExercise(sessionId = sessionId, exerciseId = exerciseId, position = pos, supersetGroup = ex.supersetGroup)
                    )
                    dao.insertSets(
                        ex.sets.map {
                            WorkoutSet(
                                sessionExerciseId = seId,
                                reps = it.reps,
                                weightKg = it.weightKg,
                                completedAt = it.completedAt,
                                restSeconds = it.restSeconds,
                                isDropSet = it.isDropSet,
                                isWarmup = it.isWarmup,
                                rpe = it.rpe,
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
