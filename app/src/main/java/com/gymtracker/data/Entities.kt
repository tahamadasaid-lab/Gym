package com.gymtracker.data

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
