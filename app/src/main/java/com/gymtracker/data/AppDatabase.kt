package com.gymtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        SessionExercise::class,
        WorkoutSet::class,
        Program::class,
        ProgramExercise::class,
        BodyWeight::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun programDao(): ProgramDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gymtracker.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** v2 : photos d'exercices, programmes, objectifs de séries, poids de corps. Conserve toutes les données. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `imageUri` TEXT")
        db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `targetSets` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program_exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER NOT NULL, " +
                "`exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, " +
                "FOREIGN KEY(`programId`) REFERENCES `programs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_exercises_programId` ON `program_exercises` (`programId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_exercises_exerciseId` ON `program_exercises` (`exerciseId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `weightKg` REAL NOT NULL)"
        )
    }
}

/** Pré-remplit la base avec des exercices courants au premier lancement. */
private object SeedCallback : RoomDatabase.Callback() {
    private val defaults = listOf(
        Triple("Développé couché", "Pectoraux", 120),
        Triple("Développé incliné haltères", "Pectoraux", 90),
        Triple("Écarté à la poulie", "Pectoraux", 60),
        Triple("Pompes", "Pectoraux", 60),
        Triple("Tractions", "Dos", 120),
        Triple("Rowing barre", "Dos", 90),
        Triple("Tirage vertical", "Dos", 90),
        Triple("Soulevé de terre", "Dos", 180),
        Triple("Squat", "Jambes", 150),
        Triple("Presse à cuisses", "Jambes", 120),
        Triple("Fentes", "Jambes", 90),
        Triple("Leg curl", "Jambes", 60),
        Triple("Leg extension", "Jambes", 60),
        Triple("Mollets debout", "Jambes", 60),
        Triple("Développé militaire", "Épaules", 120),
        Triple("Élévations latérales", "Épaules", 60),
        Triple("Curl biceps", "Bras", 60),
        Triple("Curl marteau", "Bras", 60),
        Triple("Extension triceps poulie", "Bras", 60),
        Triple("Dips", "Bras", 90),
        Triple("Crunch", "Abdos", 45),
        Triple("Gainage (planche)", "Abdos", 45),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        defaults.forEach { (name, group, rest) ->
            db.execSQL(
                "INSERT INTO exercises (name, muscleGroup, defaultRestSeconds) VALUES (?, ?, ?)",
                arrayOf<Any>(name, group, rest),
            )
        }
    }
}
