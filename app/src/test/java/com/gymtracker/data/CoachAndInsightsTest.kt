package com.gymtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachAndInsightsTest {
    private fun set(w: Double, r: Int, rpe: Int? = null, warm: Boolean = false, drop: Boolean = false) =
        WorkoutSet(sessionExerciseId = 1, reps = r, weightKg = w, completedAt = 0, restSeconds = null, isDropSet = drop, isWarmup = warm, rpe = rpe)

    @Test
    fun suggestion_increasesWhenAllRepsDone() {
        val s = suggestNext(listOf(set(20.0, 10, warm = true), set(80.0, 8), set(80.0, 8), set(80.0, 8)), "Pectoraux")!!
        assertTrue(s.increase)
        assertEquals(82.5, s.weightKg, 0.0)
        assertEquals(8, s.reps)
    }

    @Test
    fun suggestion_keepsWeightWhenRepsMissed() {
        val s = suggestNext(listOf(set(80.0, 8), set(80.0, 7), set(80.0, 6)), "Pectoraux")!!
        assertFalse(s.increase)
        assertEquals(80.0, s.weightKg, 0.0)
        assertEquals(8, s.reps)
    }

    @Test
    fun suggestion_failureRpeBlocksIncrease_andIsolationUsesSmallStep() {
        assertFalse(suggestNext(listOf(set(80.0, 8, rpe = 10), set(80.0, 8, rpe = 10)), "Dos")!!.increase)
        assertEquals(31.25, suggestNext(listOf(set(30.0, 10), set(30.0, 10)), "Bras")!!.weightKg, 0.0)
        assertNull(suggestNext(listOf(set(20.0, 10, warm = true)), "Bras"))
    }

    @Test
    fun warmupPlan_isProgressiveAndLoadable() {
        val plan = warmupPlan(100.0, 20.0, StandardPlates)
        assertEquals(listOf(20.0, 40.0, 60.0, 80.0), plan.map { it.weightKg })
        assertEquals(listOf(10, 8, 5, 3), plan.map { it.reps })
        assertTrue(warmupPlan(0.0, 20.0, StandardPlates).isEmpty())
    }

    @Test
    fun records_bestWeightOneRepMaxAndRepMaxes() {
        val rows = listOf(
            WorkSetRow(1, 10, 1_000, 5, 100.0),
            WorkSetRow(1, 10, 1_000, 8, 90.0),
            WorkSetRow(1, 11, 2_000, 1, 110.0),
            WorkSetRow(1, 11, 2_000, 10, 80.0),
        )
        val r = computeRecords(rows).getValue(1)
        assertEquals(110.0, r.bestWeight, 0.0)
        assertEquals(2, r.sessionCount)
        val byReps = r.repMaxes.associate { it.reps to it.weightKg }
        assertEquals(110.0, byReps[1]!!, 0.0)
        assertEquals(100.0, byReps[5]!!, 0.0)
        assertEquals(90.0, byReps[8]!!, 0.0)
        assertEquals(80.0, byReps[10]!!, 0.0)
        assertNull(byReps[12])
    }

    @Test
    fun detectRecord_weightThenOneRepMax() {
        val history = listOf(WeightReps(100.0, 5))
        assertTrue(detectRecord("Squat", history, 102.5, 3) is RecordEvent.Weight)
        assertTrue(detectRecord("Squat", history, 100.0, 7) is RecordEvent.OneRepMax)
        assertNull(detectRecord("Squat", history, 95.0, 5))
        assertNull(detectRecord("Squat", emptyList(), 200.0, 5))
    }

    @Test
    fun weekStreak_countsConsecutiveWeeks() {
        val today = LocalDate.of(2026, 11, 18) // mercredi
        fun d(date: LocalDate) = date.startMillis() + 3_600_000
        val dates = listOf(d(LocalDate.of(2026, 11, 16)), d(LocalDate.of(2026, 11, 10)), d(LocalDate.of(2026, 11, 3)), d(LocalDate.of(2026, 10, 20)))
        assertEquals(3, weekStreak(dates, today))
        // Rien encore cette semaine : on compte à partir de la semaine précédente.
        assertEquals(2, weekStreak(dates.drop(1), today))
        val perWeek = sessionsPerWeek(dates, 4, today)
        assertEquals(listOf(0, 1, 1, 1), perWeek.map { it.second })
    }

    @Test
    fun goalProgress_exerciseAndBodyWeight() {
        val g = Goal(type = GoalType.EXERCISE_WEIGHT.name, exerciseId = 1, targetValue = 100.0, startValue = 80.0, createdAt = 0, deadline = null)
        val p = goalProgress(g, "Développé couché", listOf(WorkSetRow(1, 1, 0, 5, 90.0)), emptyList())
        assertEquals(0.5f, p.fraction, 0.001f)
        assertFalse(p.achieved)
        // Perte de poids : objectif inférieur au départ.
        val b = Goal(type = GoalType.BODY_WEIGHT.name, exerciseId = null, targetValue = 75.0, startValue = 80.0, createdAt = 0, deadline = null)
        val pb = goalProgress(b, null, emptyList(), listOf(BodyWeight(date = 1, weightKg = 74.8)))
        assertTrue(pb.achieved)
        assertNotNull(pb.current)
    }
}
