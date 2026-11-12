package com.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM muscle_groups ORDER BY position, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MuscleGroup>>

    @Query("SELECT * FROM muscle_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): MuscleGroup?

    @Query("SELECT COALESCE(MAX(position), -1) FROM muscle_groups")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(group: MuscleGroup): Long

    @Update
    suspend fun update(group: MuscleGroup)

    @Delete
    suspend fun delete(group: MuscleGroup)

    @Query("UPDATE exercises SET muscleGroup = :newName WHERE muscleGroup = :oldName")
    suspend fun moveExercises(oldName: String, newName: String)
}
