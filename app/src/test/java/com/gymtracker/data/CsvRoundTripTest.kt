package com.gymtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Test
    fun exportThenImport_restoresSessionsAndBodyWeight() = runBlocking {
        val source = newDb()
        val repo = WorkoutRepository(source)
        val benchId = repo.findOrCreateExercise("Développé; couché \"barre\"", "Pectoraux")
        val sessionId = repo.startSession()
        val seId = repo.addExerciseToSession(sessionId, benchId)
        repo.logSet(sessionId, seId, 10, 60.0)
        repo.logSet(sessionId, seId, 8, 62.5)
        repo.logSet(sessionId, seId, 6, 50.0, isDropSet = true)
        repo.finishSession(sessionId)
        repo.addBodyWeight(78.4, 1_700_000_000_000)

        val out = ByteArrayOutputStream()
        assertEquals(1, repo.exportCsv(out))
        source.close()

        val target = newDb()
        val repo2 = WorkoutRepository(target)
        val result = repo2.importCsv(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, result.sessions)
        assertEquals(1, result.bodyWeights)

        val sessions = target.workoutDao().getAllSessions()
        val ex = sessions.single().exercises.single()
        assertEquals("Développé; couché \"barre\"", ex.exercise.name)
        assertEquals("Pectoraux", ex.exercise.muscleGroup)
        assertEquals(listOf(60.0 to 10, 62.5 to 8, 50.0 to 6), ex.orderedSets.map { it.weightKg to it.reps })
        assertEquals(listOf(false, false, true), ex.orderedSets.map { it.isDropSet })
        assertEquals(78.4, target.bodyWeightDao().getAll().single().weightKg, 0.0)

        // Ré-importer le même fichier ne crée pas de doublons.
        val again = repo2.importCsv(ByteArrayInputStream(out.toByteArray()))
        assertEquals(0, again.sessions)
        assertEquals(1, again.skipped)
        target.close()
    }

    @Test
    fun parseLine_handlesQuotes() {
        assertEquals(listOf("a", "b;c", "d\"e", ""), CsvCodec.parseLine("a;\"b;c\";\"d\"\"e\";"))
    }
}
