package com.gymtracker.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
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
)

/** Réglages stockés dans les SharedPreferences, exposés en StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load() = AppSettings(
        soundEnabled = prefs.getBoolean("sound", true),
        voiceEnabled = prefs.getBoolean("voice", true),
        warningEnabled = prefs.getBoolean("warning", true),
        vibrationEnabled = prefs.getBoolean("vibration", true),
        useAlarmChannel = prefs.getBoolean("alarm_channel", false),
        profileWeightKg = prefs.getFloat("profile_weight", 75f).toDouble(),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putBoolean("sound", s.soundEnabled)
            .putBoolean("voice", s.voiceEnabled)
            .putBoolean("warning", s.warningEnabled)
            .putBoolean("vibration", s.vibrationEnabled)
            .putBoolean("alarm_channel", s.useAlarmChannel)
            .putFloat("profile_weight", s.profileWeightKg.toFloat())
            .apply()
        _settings.value = s
    }
}
