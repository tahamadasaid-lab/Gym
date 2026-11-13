package com.gymtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.backup.BackupManager
import com.gymtracker.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Test
    fun backupThenRestore_restoresEverything() = runBlocking {
        val settings = SettingsStore(context)
        settings.update { it.copy(barWeightKg = 15.0) }

        val source = newDb()
        val repo = WorkoutRepository(source)
        val photoDir = File(context.filesDir, ImageStore.EXERCISE_DIR).apply { mkdirs() }
        val photo = File(photoDir, "test.jpg").apply { writeText("image") }
        val exId = repo.saveExercise(Exercise(name = "Squat", muscleGroup = "Jambes", imageUri = photo.absolutePath, notes = "barre haute"))!!
        val sessionId = repo.startSession()
        val seId = repo.addExerciseToSession(sessionId, exId)
        repo.logSet(sessionId, seId, 10, 40.0, isWarmup = true)
        repo.logSet(sessionId, seId, 5, 100.0)
        repo.updateSessionNotes(sessionId, "Bonne forme")
        repo.finishSession(sessionId)
        repo.saveProgram(null, "Legs", listOf(exId to 4))
        repo.addMeasurement("Tour de bras", 38.5)
        repo.addGoal(Goal(type = GoalType.EXERCISE_WEIGHT.name, exerciseId = exId, targetValue = 120.0, startValue = 100.0, createdAt = 1, deadline = null))

        val out = ByteArrayOutputStream()
        val written = BackupManager(context, source, settings).write(out)
        assertEquals(1, written.sessions)
        assertEquals(1, written.photos)
        source.close()

        // Nouvel appareil : base vide, photo et réglage absents.
        photo.delete()
        settings.update { it.copy(barWeightKg = 20.0) }
        val target = newDb()
        BackupManager(context, target, settings).restore(ByteArrayInputStream(out.toByteArray()))

        val sessions = target.workoutDao().getAllSessions()
        assertEquals("Bonne forme", sessions.single().session.notes)
        val ex = sessions.single().exercises.single()
        assertEquals("barre haute", ex.exercise.notes)
        assertEquals(listOf(true, false), ex.orderedSets.map { it.isWarmup })
        assertTrue(File(ex.exercise.imageUri!!).exists())
        assertEquals(4, target.programDao().observePrograms().first().single().items.single().item.targetSets)
        assertEquals(38.5, target.bodyDao().observeMeasurements().first().single().valueCm, 0.0)
        assertEquals(120.0, target.bodyDao().observeGoals().first().single().targetValue, 0.0)
        assertEquals(15.0, settings.settings.value.barWeightKg, 0.0)
        target.close()
    }
}
