package com.gymtracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.gymtracker.data.CalorieCalculator
import com.gymtracker.data.WorkoutRepository
import com.gymtracker.data.efforts
import com.gymtracker.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Synchronisation avec Health Connect (Google Fit, Samsung Health, montres…) :
 * - envoie chaque séance terminée (séance de musculation + calories actives) ;
 * - lit la fréquence cardiaque enregistrée par une montre pendant la séance pour calculer les calories
 *   (formule de Keytel, si l'âge et le sexe sont renseignés).
 */
class HealthSync(
    private val context: Context,
    private val repo: WorkoutRepository,
    private val settings: SettingsStore,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect est-il installable (Android < 14 sans l'appli) ? */
    val needsInstall: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun grantedAll(): Boolean =
        isAvailable && client().permissionController.getGrantedPermissions().containsAll(permissions)

    /** Envoie une séance terminée ; retourne false si rien n'a été envoyé. */
    suspend fun syncSession(sessionId: Long): Boolean {
        if (!grantedAll()) return false
        val s = repo.getSession(sessionId) ?: return false
        val end = s.session.endedAt ?: return false
        val start = Instant.ofEpochMilli(s.session.startedAt)
        val stop = Instant.ofEpochMilli(end)
        if (!stop.isAfter(start)) return false
        val zone = ZoneId.systemDefault().rules
        val client = client()

        // Fréquence cardiaque de la montre pendant la séance.
        val bpm = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(start, stop)))
                .records.flatMap { r -> r.samples.map { it.beatsPerMinute } }
        }.getOrDefault(emptyList())
        val avgHr = bpm.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val prefs = settings.settings.value
        val weight = repo.observeBodyWeights().first().lastOrNull()?.weightKg ?: prefs.profileWeightKg
        val minutes = (end - s.session.startedAt) / 60_000.0
        val hrKcal = if (avgHr != null && prefs.age != null && prefs.sex != null) {
            keytelKcalPerMinute(avgHr, weight, prefs.age, prefs.sex == "F") * minutes
        } else null
        repo.updateSessionHealth(sessionId, avgHr, hrKcal)
        val kcal = hrKcal ?: CalorieCalculator.of(weight, repo.observeCategories().first()).kcal(s.efforts())

        val title = s.session.programLabel ?: "Musculation"
        val notes = s.orderedExercises.filter { it.sets.isNotEmpty() }.joinToString(", ") { it.exercise.name }
        client.insertRecords(
            listOf(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone.getOffset(start),
                    endTime = stop,
                    endZoneOffset = zone.getOffset(stop),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = title,
                    notes = notes.take(1000),
                    metadata = Metadata.manualEntry(clientRecordId = "gymtracker-session-$sessionId"),
                ),
                ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = zone.getOffset(start),
                    endTime = stop,
                    endZoneOffset = zone.getOffset(stop),
                    energy = Energy.kilocalories(kcal.coerceAtLeast(0.0)),
                    metadata = Metadata.manualEntry(clientRecordId = "gymtracker-kcal-$sessionId"),
                ),
            )
        )
        return true
    }

    /** Envoi automatique à la fin d'une séance, si activé dans les réglages. */
    suspend fun syncIfEnabled(sessionId: Long) {
        if (settings.settings.value.healthSyncEnabled) runCatching { syncSession(sessionId) }
    }

    /** Envoie toutes les séances terminées (les doublons sont évités grâce aux identifiants). */
    suspend fun syncAll(): Int {
        if (!grantedAll()) return 0
        return repo.observeFinishedSessions().first().count { runCatching { syncSession(it.session.id) }.getOrDefault(false) }
    }

    companion object {
        /** Formule de Keytel (2005) : kcal par minute selon la fréquence cardiaque moyenne. */
        fun keytelKcalPerMinute(hr: Int, weightKg: Double, age: Int, female: Boolean): Double {
            val kj = if (female) -20.4022 + 0.4472 * hr - 0.1263 * weightKg + 0.074 * age
            else -55.0969 + 0.6309 * hr + 0.1988 * weightKg + 0.2017 * age
            return (kj / 4.184).coerceAtLeast(0.0)
        }
    }
}
