package com.gymtracker.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    /** Temps de repos par défaut entre deux séries, en secondes. */
    val defaultRestSeconds: Int = 90,
    /** Chemin local d'une photo choisie par l'utilisateur (null = visuel par défaut). */
    val imageUri: String? = null,
)

@Entity(tableName = "sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
)

/** Un exercice réalisé pendant une séance. */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    /** Nombre de séries prévues (quand la séance vient d'un programme). */
    val targetSets: Int? = null,
    /** Exercices consécutifs ayant la même valeur = une supersérie (enchaînés sans repos). */
    val supersetGroup: Long? = null,
)

/** Une série : poids × répétitions, avec le temps de repos pris avant elle. */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val completedAt: Long,
    /** Repos réel écoulé depuis la série précédente de la séance (null pour la première). */
    val restSeconds: Int?,
    /** Série dégressive : charge réduite enchaînée sans repos après la série précédente. */
    @ColumnInfo(defaultValue = "0")
    val isDropSet: Boolean = false,
)

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<WorkoutSet>,
) {
    val orderedSets: List<WorkoutSet> get() = sets.sortedBy { it.completedAt }
}

data class SessionWithExercises(
    @Embedded val session: WorkoutSession,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
) {
    val orderedExercises: List<SessionExerciseWithSets>
        get() = exercises.sortedBy { it.sessionExercise.position }
}

/** Ligne brute utilisée pour calculer la progression d'un exercice. */
data class SetHistoryRow(
    val sessionId: Long,
    val date: Long,
    val reps: Int,
    val weightKg: Double,
)

/** Programme (modèle de séance), ex. « Push », « Pull », « Legs ». */
@Entity(tableName = "programs")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "program_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Program::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programId"), Index("exerciseId")],
)
data class ProgramExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val supersetGroup: Long? = null,
)

data class ProgramExerciseWithExercise(
    @Embedded val item: ProgramExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class ProgramWithExercises(
    @Embedded val program: Program,
    @Relation(entity = ProgramExercise::class, parentColumn = "id", entityColumn = "programId")
    val items: List<ProgramExerciseWithExercise>,
) {
    val orderedItems: List<ProgramExerciseWithExercise> get() = items.sortedBy { it.item.position }
}

/** Pesée (poids de corps). */
@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val weightKg: Double,
)

/**
 * Catégorie (groupe musculaire) personnalisable. Les exercices y sont rattachés par leur champ `muscleGroup`
 * (= nom de la catégorie). `met` = intensité utilisée pour estimer les calories.
 */
@Entity(tableName = "muscle_groups", indices = [Index(value = ["name"], unique = true)])
data class MuscleGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val icon: String,
    val met: Double,
    val position: Int,
)

/** Blocs d'exercices consécutifs : un exercice seul, ou plusieurs liés en supersérie. */
fun <T> List<T>.supersetBlocks(group: (T) -> Long?): List<List<T>> {
    val blocks = mutableListOf<MutableList<T>>()
    forEach { item ->
        val g = group(item)
        val last = blocks.lastOrNull()
        if (g != null && last != null && group(last.last()) == g) last += item else blocks += mutableListOf(item)
    }
    return blocks
}
