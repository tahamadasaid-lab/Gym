package com.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {
    // Objectifs
    @Query("SELECT * FROM goals ORDER BY achievedAt IS NOT NULL, createdAt DESC")
    fun observeGoals(): Flow<List<Goal>>

    @Insert
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    // Mensurations
    @Query("SELECT * FROM measurements ORDER BY date")
    fun observeMeasurements(): Flow<List<Measurement>>

    @Insert
    suspend fun insertMeasurement(m: Measurement): Long

    @Delete
    suspend fun deleteMeasurement(m: Measurement)

    // Photos
    @Query("SELECT * FROM progress_photos ORDER BY date DESC")
    fun observePhotos(): Flow<List<ProgressPhoto>>

    @Insert
    suspend fun insertPhoto(p: ProgressPhoto): Long

    @Delete
    suspend fun deletePhoto(p: ProgressPhoto)
}
