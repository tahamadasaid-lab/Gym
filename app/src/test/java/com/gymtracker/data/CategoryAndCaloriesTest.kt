package com.gymtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryAndCaloriesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun renameAndDeleteCategory_movesExercises() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db)
        val id = repo.saveExercise(Exercise(name = "Hip thrust", muscleGroup = "Fessiers"))!!
        val fessiers = db.categoryDao().findByName("Fessiers")!!

        // Doublon refusé
        assertNull(repo.saveCategory(fessiers.copy(id = 0), null))

        // Renommage répercuté sur les exercices
        repo.saveCategory(fessiers.copy(name = "Fessiers & ischios", met = 6.0), "Fessiers")
        assertEquals("Fessiers & ischios", db.workoutDao().getAllExercises().single { it.id == id }.muscleGroup)

        // Suppression : l'exercice passe dans « Autre »
        repo.deleteCategory(db.categoryDao().findByName("Fessiers & ischios")!!)
        assertEquals("Autre", db.workoutDao().getAllExercises().single { it.id == id }.muscleGroup)
        val names = db.categoryDao().observeAll().first().map { it.name }
        assertTrue("Autre" in names && "Fessiers & ischios" !in names)
        db.close()
    }

    @Test
    fun calories_useCategoryIntensityWeightAndTime() {
        val calc = CalorieCalculator(80.0, mapOf("jambes" to 6.0))
        // 30 min de jambes à MET 6 pour 80 kg = 6 × 80 × 0,5 = 240 kcal
        assertEquals(240.0, calc.kcal(Effort("Jambes", 1800)), 0.001)
        // Catégorie inconnue : intensité par défaut (4,5)
        assertEquals(4.5 * 80 * 0.5, calc.kcal(Effort("Inconnue", 1800)), 0.001)
    }

    @Test
    fun attributedSeconds_usesMeasuredRestOrEstimate() {
        val set = WorkoutSet(sessionExerciseId = 1, reps = 10, weightKg = 50.0, completedAt = 0, restSeconds = null)
        assertEquals(100L, set.attributedSeconds()) // 10 reps × 4 s + 60 s
        assertEquals(120L, set.copy(restSeconds = 120).attributedSeconds())
        assertEquals(600L, set.copy(restSeconds = 3000).attributedSeconds()) // plafonné à 10 min
    }
}
