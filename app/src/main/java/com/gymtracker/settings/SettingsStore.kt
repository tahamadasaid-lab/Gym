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
    )

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
            .apply()
        _settings.value = s
    }
}
