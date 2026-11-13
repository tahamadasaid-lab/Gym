package com.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // --- Exercices ---
    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name COLLATE NOCASE")
    fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findExerciseByName(name: String): Exercise?

    @Insert
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<Exercise>

    @Query("UPDATE exercises SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImage(id: Long, imageUri: String?)

    @Query("UPDATE exercises SET notes = :notes WHERE id = :id")
    suspend fun updateExerciseNotes(id: Long, notes: String)

    @Query("UPDATE sessions SET notes = :notes WHERE id = :id")
    suspend fun updateSessionNotes(id: Long, notes: String)

    @Query("UPDATE sets SET rpe = :rpe WHERE id = :id")
    suspend fun setRpe(id: Long, rpe: Int?)

    /** Séries de travail d'un exercice hors séance donnée (pour détecter un nouveau record). */
    @Query(
        """SELECT ws.weightKg AS weightKg, ws.reps AS reps FROM sets ws
           JOIN session_exercises se ON ws.sessionExerciseId = se.id
           WHERE se.exerciseId = :exerciseId AND se.sessionId != :excludeSessionId AND ws.isWarmup = 0"""
    )
    suspend fun workSetsExcluding(exerciseId: Long, excludeSessionId: Long): List<WeightReps>

    /** Toutes les séries de travail (records, bilans, objectifs). */
    @Query(
        """SELECT se.exerciseId AS exerciseId, s.id AS sessionId, s.startedAt AS date, ws.reps AS reps, ws.weightKg AS weightKg
           FROM sets ws
           JOIN session_exercises se ON ws.sessionExerciseId = se.id
           JOIN sessions s ON se.sessionId = s.id
           WHERE ws.isWarmup = 0"""
    )
    fun observeWorkSets(): Flow<List<WorkSetRow>>

    @Query("UPDATE exercises SET defaultRestSeconds = :seconds WHERE id = :id")
    suspend fun updateRest(id: Long, seconds: Int)

    @Query(
        """SELECT DISTINCT se.exerciseId FROM session_exercises se
           JOIN sets ws ON ws.sessionExerciseId = se.id"""
    )
    fun observeTrainedExerciseIds(): Flow<List<Long>>

    // --- Séances ---
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun finishSession(id: Long, endedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSession?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSessionWithExercises(id: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinishedSessions(): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun getAllSessions(): List<SessionWithExercises>

    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt BETWEEN :startedAt AND :startedAt + 999")
    suspend fun countSessionsStartedAt(startedAt: Long): Int

    // --- Exercices d'une séance ---
    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun maxPosition(sessionId: Long): Int

    @Insert
    suspend fun insertSessionExercise(sessionExercise: SessionExercise): Long

    @Query("UPDATE session_exercises SET supersetGroup = :group WHERE id IN (:ids)")
    suspend fun setSupersetGroup(ids: List<Long>, group: Long?)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    // --- Séries ---
    @Insert
    suspend fun insertSet(set: WorkoutSet): Long

    @Delete
    suspend fun deleteSet(set: WorkoutSet)

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Insert
    suspend fun insertSets(sets: List<WorkoutSet>)

    @Query("SELECT MAX(completedAt) FROM sets WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun lastCompletedAt(sessionExerciseId: Long): Long?

    @Query(
        """SELECT s.startedAt FROM sessions s
           JOIN session_exercises se ON se.sessionId = s.id
           WHERE se.id = :sessionExerciseId"""
    )
    suspend fun sessionStartFor(sessionExerciseId: Long): Long?

    @Query(
        """SELECT ws.* FROM sets ws
           JOIN session_exercises se ON ws.sessionExerciseId = se.id
           WHERE se.sessionId = :sessionId
           ORDER BY ws.completedAt DESC LIMIT 1"""
    )
    suspend fun lastSetInSession(sessionId: Long): WorkoutSet?

    /** Séries réalisées sur cet exercice lors de la séance précédente. */
    @Query(
        """SELECT * FROM sets WHERE sessionExerciseId = (
               SELECT se.id FROM session_exercises se
               JOIN sessions s ON s.id = se.sessionId
               WHERE se.exerciseId = :exerciseId AND se.sessionId != :excludeSessionId
                 AND EXISTS (SELECT 1 FROM sets x WHERE x.sessionExerciseId = se.id)
               ORDER BY s.startedAt DESC LIMIT 1
           ) ORDER BY completedAt"""
    )
    suspend fun previousSets(exerciseId: Long, excludeSessionId: Long): List<WorkoutSet>

    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS date, ws.reps AS reps, ws.weightKg AS weightKg
           FROM sets ws
           JOIN session_exercises se ON ws.sessionExerciseId = se.id
           JOIN sessions s ON se.sessionId = s.id
           WHERE se.exerciseId = :exerciseId AND ws.isWarmup = 0
           ORDER BY s.startedAt, ws.completedAt"""
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<SetHistoryRow>>
}
