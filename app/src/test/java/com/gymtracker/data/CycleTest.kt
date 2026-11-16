package com.gymtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.health.HealthSync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CycleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Test
    fun plannedWeight_progressionAndDeload() {
        assertEquals(80.0, plannedWeight(80.0, 2.5, 1, 6, false), 0.0)
        assertEquals(92.5, plannedWeight(80.0, 2.5, 6, 6, false), 0.0)
        // Décharge : 80 % de la semaine 5 (90 kg) = 72 → arrondi à 72,5
        assertEquals(72.5, plannedWeight(80.0, 2.5, 6, 6, true), 0.0)
    }

    @Test
    fun currentWeek_fromCycleStart() {
        val now = System.currentTimeMillis()
        val p = Program(name = "Push", weeks = 4, cycleStartedAt = now - TimeUnit.DAYS.toMillis(15))
        assertEquals(3, p.currentWeek(now))
        assertEquals(1, p.copy(cycleStartedAt = null).currentWeek(now))
        assertNull(p.copy(weeks = null).currentWeek(now))
        assertTrue(p.copy(cycleStartedAt = now - TimeUnit.DAYS.toMillis(29)).cycleFinished(now))
    }

    @Test
    fun keytel_isPlausible() {
        val perMin = HealthSync.keytelKcalPerMinute(130, 80.0, 30, female = false)
        assertTrue(perMin in 8.0..14.0)
    }

    @Test
    fun programFromSessions_thenCycleTargets() = runBlocking {
        val db = newDb()
        val repo = WorkoutRepository(db)
        val squat = repo.findOrCreateExercise("Squat", "Jambes")
        val bench = repo.findOrCreateExercise("Développé couché", "Pectoraux")
        val s1 = repo.startSession()
        val se1 = repo.addExerciseToSession(s1, squat)
        repo.logSet(s1, se1, 10, 40.0, isWarmup = true)
        repo.logSet(s1, se1, 5, 100.0)
        repo.logSet(s1, se1, 5, 100.0)
        val se2 = repo.addExerciseToSession(s1, bench)
        repo.logSet(s1, se2, 8, 70.0)
        repo.finishSession(s1)

        val programId = repo.createProgramFromSessions(listOf(s1), "Full body")
        val program = repo.getProgram(programId)!!
        val items = program.orderedItems
        assertEquals(listOf("Squat", "Développé couché"), items.map { it.exercise.name })
        assertEquals(2, items[0].item.targetSets) // échauffement ignoré
        assertEquals(100.0, items[0].item.startWeight!!, 0.0)
        assertEquals(5, items[0].item.targetReps)

        // Passage en cycle de 4 semaines, +2,5 kg / semaine sur le squat.
        repo.saveProgramEntries(
            programId, "Full body",
            items.map { WorkoutRepository.ProgramEntry(it.exercise.id, it.item.targetSets, null, it.item.targetReps, it.item.startWeight, 2.5) },
            weeks = 4,
        )
        val sessionId = repo.startSessionFromProgram(programId)
        val started = repo.getSession(sessionId)!!
        assertEquals("Full body · semaine 1/4", started.session.programLabel)
        val first = started.orderedExercises.first().sessionExercise
        assertEquals(100.0, first.targetWeight!!, 0.0)
        assertEquals(5, first.targetReps)
        db.close()
    }
}
