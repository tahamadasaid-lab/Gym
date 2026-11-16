package com.gymtracker.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymtracker.GymApp
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.supersetBlocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prochaine série probable de la séance en cours (pour la valider depuis la notification / la montre). */
data class NextSet(
    val sessionId: Long,
    val sessionExerciseId: Long,
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int,
    val restSeconds: Int,
    val startRest: Boolean,
)

object NextSetFinder {
    suspend fun find(repo: WorkoutRepository): NextSet? {
        val active = repo.observeActiveSession().first() ?: return null
        val session = repo.getSession(active.id) ?: return null
        val ordered = session.orderedExercises
        if (ordered.isEmpty()) return null
        val lastSet = ordered.flatMap { e -> e.sets.map { e to it } }.maxByOrNull { it.second.completedAt }
        var current = lastSet?.first ?: ordered.first()
        val work = current.sets.filter { it.isWork }
        val target = current.sessionExercise.targetSets
        // Objectif de séries atteint : on passe à l'exercice suivant.
        if (target != null && work.size >= target) {
            ordered.getOrNull(ordered.indexOf(current) + 1)?.let { current = it }
        }
        val ref = current.sets.filter { it.isWork }.maxByOrNull { it.completedAt }
        val weight = ref?.weightKg ?: current.sessionExercise.targetWeight
            ?: repo.previousSets(current.exercise.id, session.session.id).firstOrNull { it.isWork }?.weightKg
        val reps = ref?.reps ?: current.sessionExercise.targetReps
            ?: repo.previousSets(current.exercise.id, session.session.id).firstOrNull { it.isWork }?.reps
            ?: return null
        val block = ordered.supersetBlocks { it.sessionExercise.supersetGroup }
            .firstOrNull { b -> b.any { it.sessionExercise.id == current.sessionExercise.id } }.orEmpty()
        val chain = block.size > 1 && block.last().sessionExercise.id != current.sessionExercise.id
        return NextSet(
            sessionId = session.session.id,
            sessionExerciseId = current.sessionExercise.id,
            exerciseName = current.exercise.name,
            weightKg = weight ?: 0.0,
            reps = reps,
            restSeconds = current.exercise.defaultRestSeconds,
            startRest = !chain,
        )
    }
}

/** Bouton « ✓ Série faite » : enregistre la série et relance le minuteur, sans ouvrir l'appli. */
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_NEXT) return
        val app = context.applicationContext as GymApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val next = NextSetFinder.find(app.repository)
                if (next != null) {
                    app.repository.logSet(next.sessionId, next.sessionExerciseId, next.reps, next.weightKg)
                    withContext(Dispatchers.Main) {
                        Notifications.cancelFinished(app)
                        if (next.startRest) app.restTimer.start(next.restSeconds, next.exerciseName)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG_NEXT = "com.gymtracker.session.LOG_NEXT"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 50,
            Intent(context, SessionActionReceiver::class.java).setAction(ACTION_LOG_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
