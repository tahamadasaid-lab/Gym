package com.gymtracker.data

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises()
    fun observeTrainedExerciseIds(): Flow<List<Long>> = dao.observeTrainedExerciseIds()

    /** Crée ou met à jour un exercice. Retourne false si le nom existe déjà. */
    suspend fun saveExercise(exercise: Exercise): Boolean {
        val existing = dao.findExerciseByName(exercise.name.trim())
        if (existing != null && existing.id != exercise.id) return false
        val clean = exercise.copy(name = exercise.name.trim(), muscleGroup = exercise.muscleGroup.trim().ifEmpty { "Autre" })
        if (clean.id == 0L) dao.insertExercise(clean) else dao.updateExercise(clean)
        return true
    }

    suspend fun deleteExercise(exercise: Exercise) = dao.deleteExercise(exercise)
    suspend fun updateRest(exerciseId: Long, seconds: Int) = dao.updateRest(exerciseId, seconds)

    suspend fun findOrCreateExercise(name: String): Long {
        dao.findExerciseByName(name.trim())?.let { return it.id }
        return dao.insertExercise(Exercise(name = name.trim(), muscleGroup = "Autre"))
    }

    fun observeActiveSession(): Flow<WorkoutSession?> = dao.observeActiveSession()
    fun observeSession(id: Long): Flow<SessionWithExercises?> = dao.observeSessionWithExercises(id)
    fun observeFinishedSessions(): Flow<List<SessionWithExercises>> = dao.observeFinishedSessions()

    suspend fun startSession(): Long = dao.insertSession(WorkoutSession(startedAt = System.currentTimeMillis()))
    suspend fun finishSession(id: Long) = dao.finishSession(id, System.currentTimeMillis())
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long): Long {
        val position = dao.maxPosition(sessionId) + 1
        return dao.insertSessionExercise(SessionExercise(sessionId = sessionId, exerciseId = exerciseId, position = position))
    }

    suspend fun removeSessionExercise(sessionExerciseId: Long) = dao.deleteSessionExercise(sessionExerciseId)

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

    suspend fun deleteSet(set: WorkoutSet) = dao.deleteSet(set)

    suspend fun previousSets(exerciseId: Long, excludeSessionId: Long): List<WorkoutSet> =
        dao.previousSets(exerciseId, excludeSessionId)

    fun observeExerciseHistory(exerciseId: Long): Flow<List<SetHistoryRow>> =
        dao.observeExerciseHistory(exerciseId)

    private companion object {
        /** Au-delà de 30 min, on considère que ce n'est plus un temps de repos. */
        const val MAX_TRACKED_REST_SECONDS = 30 * 60
    }
}
