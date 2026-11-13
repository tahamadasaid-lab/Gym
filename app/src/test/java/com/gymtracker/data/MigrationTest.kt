package com.gymtracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vérifie que la mise à jour v1 → v2 conserve les séances déjà enregistrées. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Schéma v1 tel que généré par Room pour la première version de l'application. */
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, `defaultRestSeconds` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)",
        "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER)",
        "CREATE TABLE IF NOT EXISTS `session_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionExerciseId` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `completedAt` INTEGER NOT NULL, `restSeconds` INTEGER, FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_sets_sessionExerciseId` ON `sets` (`sessionExerciseId`)",
    )

    @Test
    fun migrate1To4_keepsData() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO exercises (id, name, muscleGroup, defaultRestSeconds) VALUES (1, 'Squat', 'Jambes', 150)")
            db.execSQL("INSERT INTO exercises (id, name, muscleGroup, defaultRestSeconds) VALUES (2, 'Hip thrust', 'Fessiers', 90)")
            db.execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, 1000, 5000)")
            db.execSQL("INSERT INTO session_exercises (id, sessionId, exerciseId, position) VALUES (1, 1, 1, 0)")
            db.execSQL("INSERT INTO sets (sessionExerciseId, reps, weightKg, completedAt, restSeconds) VALUES (1, 8, 100.0, 2000, NULL)")
            db.execSQL("INSERT INTO sets (sessionExerciseId, reps, weightKg, completedAt, restSeconds) VALUES (1, 6, 105.0, 3000, 120)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val sessions = room.workoutDao().observeFinishedSessions().first()
            assertEquals(1, sessions.size)
            val ex = sessions.single().exercises.single()
            assertEquals("Squat", ex.exercise.name)
            assertNull(ex.exercise.imageUri)
            assertNull(ex.sessionExercise.targetSets)
            assertEquals(listOf(100.0, 105.0), ex.orderedSets.map { it.weightKg })
            // v4 : anciennes séries = séries normales, pas de supersérie
            assertTrue(ex.orderedSets.none { it.isDropSet })
            assertNull(ex.sessionExercise.supersetGroup)

            // v3 : catégories par défaut + groupes déjà utilisés par les exercices.
            val categories = room.categoryDao().observeAll().first().map { it.name }
            assertTrue(categories.containsAll(listOf("Pectoraux", "Jambes", "Autre", "Fessiers")))
            assertEquals(categories.size, categories.toSet().size)

            // Les nouvelles tables fonctionnent.
            val repo = WorkoutRepository(room)
            val programId = repo.saveProgram(null, "Legs", listOf(1L to 4))
            assertEquals(4, repo.getProgram(programId)?.items?.single()?.item?.targetSets)
            repo.addBodyWeight(80.5, 10_000)
            assertEquals(80.5, room.bodyWeightDao().getAll().single().weightKg, 0.0)

            val sessionId = repo.startSessionFromProgram(programId)
            val started = room.workoutDao().observeSessionWithExercises(sessionId).first()
            assertNotNull(started)
            assertEquals(4, started!!.exercises.single().sessionExercise.targetSets)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
