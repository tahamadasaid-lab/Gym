package com.gymtracker.settings

import android.content.Context
import com.gymtracker.data.StandardPlates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

data class AppSettings(
    /** Thème de l'application : suit le téléphone, ou forcé clair / sombre. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** En thème sombre : fond noir intégral (économise la batterie sur écran OLED). */
    val pureBlack: Boolean = false,
    /** Bips sonores à la fin du repos (joués dans les écouteurs s'ils sont branchés). */
    val soundEnabled: Boolean = true,
    /** Annonce vocale « Repos terminé, série suivante : … ». */
    val voiceEnabled: Boolean = true,
    /** Signal 10 secondes avant la fin. */
    val warningEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Utiliser le canal « alarme » : sonne même en mode silencieux, mais aussi sur le haut-parleur. */
    val useAlarmChannel: Boolean = false,
    /** Poids utilisé pour les calories quand aucune pesée n'est enregistrée. */
    val profileWeightKg: Double = 75.0,
    /** Poids de la barre pour le calcul des disques. */
    val barWeightKg: Double = 20.0,
    /** Disques disponibles dans ta salle (kg). */
    val availablePlates: List<Double> = StandardPlates,
    /** Sauvegarde complète automatique chaque semaine dans [backupFolderUri]. */
    val autoBackupEnabled: Boolean = false,
    val backupFolderUri: String? = null,
    val lastBackupAt: Long? = null,
    /** Planning : jour (1 = lundi … 7 = dimanche) → programme (0 = séance libre). */
    val weekPlan: Map<Int, Long> = emptyMap(),
    val reminderEnabled: Boolean = false,
    /** Heure du rappel, en minutes depuis minuit. */
    val reminderMinutes: Int = 18 * 60,
    /** Health Connect : envoi automatique des séances. */
    val healthSyncEnabled: Boolean = false,
    /** Âge et sexe (« H » / « F ») : calcul des calories à partir de la fréquence cardiaque. */
    val age: Int? = null,
    val sex: String? = null,
)

/** Réglages stockés dans les SharedPreferences, exposés en StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        pureBlack = prefs.getBoolean("pure_black", false),
        soundEnabled = prefs.getBoolean("sound", true),
        voiceEnabled = prefs.getBoolean("voice", true),
        warningEnabled = prefs.getBoolean("warning", true),
        vibrationEnabled = prefs.getBoolean("vibration", true),
        useAlarmChannel = prefs.getBoolean("alarm_channel", false),
        profileWeightKg = prefs.getFloat("profile_weight", 75f).toDouble(),
        barWeightKg = prefs.getFloat("bar_weight", 20f).toDouble(),
        availablePlates = prefs.getString("plates", null)
            ?.split(';')?.mapNotNull { it.toDoubleOrNull() }
            ?: StandardPlates,
        autoBackupEnabled = prefs.getBoolean("auto_backup", false),
        backupFolderUri = prefs.getString("backup_folder", null),
        lastBackupAt = prefs.getLong("last_backup", 0L).takeIf { it > 0 },
        weekPlan = prefs.getString("week_plan", null).orEmpty().split(';').mapNotNull { e ->
            val (d, p) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            d.toIntOrNull()?.let { day -> p.toLongOrNull()?.let { day to it } }
        }.toMap(),
        reminderEnabled = prefs.getBoolean("reminder", false),
        reminderMinutes = prefs.getInt("reminder_minutes", 18 * 60),
        healthSyncEnabled = prefs.getBoolean("health_sync", false),
        age = prefs.getInt("age", 0).takeIf { it > 0 },
        sex = prefs.getString("sex", null),
    )

    /** Réglages à inclure dans une sauvegarde (hors dossier de sauvegarde, propre à l'appareil). */
    fun exportJson(): org.json.JSONObject = org.json.JSONObject().apply {
        prefs.all.forEach { (k, v) -> if (k !in DEVICE_KEYS && v != null) put(k, v) }
    }

    fun importJson(json: org.json.JSONObject) {
        val e = prefs.edit()
        json.keys().forEach { k ->
            if (k in DEVICE_KEYS) return@forEach
            when (val v = json.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is String -> e.putString(k, v)
                is Int -> if (k in FLOAT_KEYS) e.putFloat(k, v.toFloat()) else e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> e.putFloat(k, v.toFloat())
            }
        }
        e.apply()
        _settings.value = load()
    }

    private companion object {
        val DEVICE_KEYS = setOf("backup_folder", "auto_backup", "last_backup")
        val FLOAT_KEYS = setOf("profile_weight", "bar_weight")
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("theme", s.themeMode.name)
            .putBoolean("pure_black", s.pureBlack)
            .putBoolean("sound", s.soundEnabled)
            .putBoolean("voice", s.voiceEnabled)
            .putBoolean("warning", s.warningEnabled)
            .putBoolean("vibration", s.vibrationEnabled)
            .putBoolean("alarm_channel", s.useAlarmChannel)
            .putFloat("profile_weight", s.profileWeightKg.toFloat())
            .putFloat("bar_weight", s.barWeightKg.toFloat())
            .putString("plates", s.availablePlates.joinToString(";"))
            .putBoolean("auto_backup", s.autoBackupEnabled)
            .putString("backup_folder", s.backupFolderUri)
            .putLong("last_backup", s.lastBackupAt ?: 0L)
            .putString("week_plan", s.weekPlan.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putBoolean("reminder", s.reminderEnabled)
            .putInt("reminder_minutes", s.reminderMinutes)
            .putBoolean("health_sync", s.healthSyncEnabled)
            .putInt("age", s.age ?: 0)
            .putString("sex", s.sex)
            .apply()
        _settings.value = s
    }
}
