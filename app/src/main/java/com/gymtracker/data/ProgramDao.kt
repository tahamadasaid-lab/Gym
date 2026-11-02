package com.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Transaction
    @Query("SELECT * FROM programs ORDER BY name COLLATE NOCASE")
    fun observePrograms(): Flow<List<ProgramWithExercises>>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getProgram(id: Long): ProgramWithExercises?

    @Insert
    suspend fun insertProgram(program: Program): Long

    @Update
    suspend fun updateProgram(program: Program)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("DELETE FROM program_exercises WHERE programId = :programId")
    suspend fun clearProgramExercises(programId: Long)

    @Insert
    suspend fun insertProgramExercises(items: List<ProgramExercise>)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weights ORDER BY date")
    fun observeAll(): Flow<List<BodyWeight>>

    @Query("SELECT * FROM body_weights ORDER BY date")
    suspend fun getAll(): List<BodyWeight>

    @Query("SELECT COUNT(*) FROM body_weights WHERE date BETWEEN :date AND :date + 999")
    suspend fun countAt(date: Long): Int

    @Insert
    suspend fun insert(entry: BodyWeight): Long

    @Delete
    suspend fun delete(entry: BodyWeight)
}
