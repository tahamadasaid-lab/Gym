package com.gymtracker.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Catégories créées à l'installation (et lors de la mise à jour v2 → v3). */
object DefaultCategories {
    const val OTHER = "Autre"
    const val DEFAULT_COLOR = 0xFF9AA8B5.toInt()
    const val DEFAULT_ICON = "bolt"
    const val DEFAULT_MET = 4.5

    val all: List<MuscleGroup> = listOf(
        MuscleGroup(name = "Pectoraux", colorArgb = 0xFFFF7A45.toInt(), icon = "dumbbell", met = 5.0, position = 0),
        MuscleGroup(name = "Dos", colorArgb = 0xFF4FC3F7.toInt(), icon = "rowing", met = 5.0, position = 1),
        MuscleGroup(name = "Jambes", colorArgb = 0xFF7CD992.toInt(), icon = "run", met = 6.0, position = 2),
        MuscleGroup(name = "Épaules", colorArgb = 0xFFC792EA.toInt(), icon = "gym", met = 4.5, position = 3),
        MuscleGroup(name = "Bras", colorArgb = 0xFFFFC857.toInt(), icon = "mma", met = 3.5, position = 4),
        MuscleGroup(name = "Abdos", colorArgb = 0xFF4DD0E1.toInt(), icon = "yoga", met = 3.8, position = 5),
        MuscleGroup(name = "Cardio", colorArgb = 0xFFFF5C7A.toInt(), icon = "heart", met = 8.0, position = 6),
        MuscleGroup(name = OTHER, colorArgb = DEFAULT_COLOR, icon = DEFAULT_ICON, met = DEFAULT_MET, position = 7),
    )

    /** Insère les catégories par défaut, puis une catégorie pour chaque groupe déjà utilisé par un exercice. */
    fun seed(db: SupportSQLiteDatabase) {
        all.forEach {
            db.execSQL(
                "INSERT OR IGNORE INTO muscle_groups (name, colorArgb, icon, met, position) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(it.name, it.colorArgb, it.icon, it.met, it.position),
            )
        }
        db.execSQL(
            "INSERT OR IGNORE INTO muscle_groups (name, colorArgb, icon, met, position) " +
                "SELECT DISTINCT muscleGroup, ?, ?, ?, 100 FROM exercises WHERE muscleGroup != ''",
            arrayOf<Any>(DEFAULT_COLOR, DEFAULT_ICON, DEFAULT_MET),
        )
    }
}
