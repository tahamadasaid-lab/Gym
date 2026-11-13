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
        MuscleGroup::class,
        Goal::class,
        Measurement::class,
        ProgressPhoto::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun programDao(): ProgramDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun categoryDao(): CategoryDao
    abstract fun bodyDao(): BodyDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gymtracker.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

/** v3 : catégories (groupes musculaires) personnalisables. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `muscle_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `icon` TEXT NOT NULL, `met` REAL NOT NULL, " +
                "`position` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_muscle_groups_name` ON `muscle_groups` (`name`)")
        DefaultCategories.seed(db)
    }
}

/** v4 : séries dégressives et superséries. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `isDropSet` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `supersetGroup` INTEGER")
        db.execSQL("ALTER TABLE `program_exercises` ADD COLUMN `supersetGroup` INTEGER")
    }
}

/** v5 : échauffement, RPE, notes, objectifs, mensurations, photos de progression. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `isWarmup` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `rpe` INTEGER")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`exerciseId` INTEGER, `targetValue` REAL NOT NULL, `startValue` REAL NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`deadline` INTEGER, `achievedAt` INTEGER, " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_exerciseId` ON `goals` (`exerciseId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` INTEGER NOT NULL, `type` TEXT NOT NULL, `valueCm` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurements_type` ON `measurements` (`type`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `progress_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` INTEGER NOT NULL, `path` TEXT NOT NULL, `note` TEXT NOT NULL)"
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
        DefaultCategories.seed(db)
    }
}
