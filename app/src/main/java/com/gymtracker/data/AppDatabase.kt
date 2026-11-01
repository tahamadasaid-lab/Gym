package com.gymtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Exercise::class, WorkoutSession::class, SessionExercise::class, WorkoutSet::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gymtracker.db")
                .addCallback(SeedCallback)
                .build()
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
